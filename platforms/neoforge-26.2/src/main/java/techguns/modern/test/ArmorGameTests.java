package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.armor.*;

final class ArmorGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("t2_armor_properties_and_equip", () -> ArmorGameTests::equip);
        r.register("t2_armor_camouflage_save_and_packet", () -> ArmorGameTests::camouflage);
        r.register("t2_armor_full_physical_absorption", () -> h -> damage(h,"physical",10,2.8f,true));
        r.register("t2_armor_helmet_raw_penetration", () -> h -> damage(h,"bullet",9,7.38f,false));
        r.register("t2_armor_full_fire_absorption", () -> h -> damage(h,"fire",14,6.44f,true));
        r.register("t2_armor_poison_protection", () -> h -> damage(h,"acid",10,4.6f,true));
        r.register("t2_armor_fall_bypasses", () -> h -> damage(h,"fall",10,10,true));
        r.register("t2_armor_mixed_vanilla_second_stage", () -> ArmorGameTests::mixed);
        r.register("t2_armor_two_wear_stages", () -> ArmorGameTests::wear);
        r.register("t2_armor_zero_damage_does_not_wear", () -> ArmorGameTests::zeroDamage);
        r.register("t2_armor_ordinary_wear_can_break", () -> ArmorGameTests::breakage);
        r.register("t2_armor_cancelled_attack_no_wear", () -> ArmorGameTests::cancelAttack);
        r.register("t2_armor_cancelled_wear_preserves_protection", () -> ArmorGameTests::cancelWear);
        r.register("t2_armor_direct_slot_wear_remains_native", () -> ArmorGameTests::directWear);
        r.register("t2_armor_anvil_repair_preserves_camo", () -> ArmorGameTests::repair);
        r.register("t2_armor_anvil_rejects_cloth", () -> ArmorGameTests::rejectCloth);
        r.register("t2_armor_pair_repair_default_camo", () -> ArmorGameTests::pairRepair);
        r.register("t2_armor_anvil_book_supported", () -> ArmorGameTests::enchant);
        r.register("t2_armor_player_speed_and_knockback", () -> ArmorGameTests::bonuses);
        r.register("t2_armor_jump_and_worn_boots", () -> ArmorGameTests::jump);
        r.register("t2_armor_unequip_removes_display_before_damage", () -> ArmorGameTests::unequip);
        PigmanGameTests.register(r);
    }
    static void near(GameTestHelper h,double actual,double expected,String text) { h.assertTrue(Math.abs(actual-expected)<.0006,text+": "+actual+" != "+expected); }
    static Player player(GameTestHelper h) { var p=WeaponGameTests.player(h); p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); p.setHealth(1000); return p; }
    static ItemStack armor(ArmorSlot slot,int camo) { var item=ArmorContent.ITEMS.get(slot).toStack(); T2ArmorItem.setCamo(item,camo); return item; }
    static void equipAll(Player p) { for(var slot:ArmorSlot.values()) p.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,0)); p.tick(); }
    private static DamageSource physical(GameTestHelper h) { return h.getLevel().damageSources().playerAttack(WeaponGameTests.player(h)); }
    private static DamageSource source(GameTestHelper h,String type) {
        if(type.equals("physical")) return physical(h);
        if(type.equals("fall")) return h.getLevel().damageSources().fall();
        if(type.equals("acid")) return new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE));
        if(type.equals("fire")) {
            var blast=new NetherBlasterProjectile(TGContent.NETHER_BLAST.get(),h.getLevel()); blast.setOwner(WeaponGameTests.player(h));
            return ShotDamage.PLAYER.source(h.getLevel(),NetherBlasterProjectile.DAMAGE_TYPE,blast);
        }
        var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47")); bullet.setOwner(WeaponGameTests.player(h));
        return ShotDamage.PLAYER.source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet);
    }
    private static void equip(GameTestHelper h) {
        var p=player(h);
        for(var spec:Armors.ALL) {
            var stack=armor(spec.slot(),0); var slot=EquipmentSlot.valueOf(spec.slot().name());
            h.assertValueEqual(stack.getMaxDamage(),990,"Original equal durability split"); h.assertTrue(!stack.isEnchantable(),"Original enchantability is zero");
            h.assertValueEqual(stack.get(DataComponents.EQUIPPABLE).slot(),slot,"Correct equipment slot");
            p.setItemInHand(InteractionHand.MAIN_HAND,stack); stack.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
            h.assertTrue(p.getItemBySlot(slot).is(ArmorContent.ITEMS.get(spec.slot()).get()),"Normal use equips armor");
        }
        p.tick(); near(h,p.getAttributeValue(Attributes.ARMOR_TOUGHNESS),4,"One toughness per piece"); near(h,p.getArmorValue(),19,"Source rounded HUD display"); h.succeed();
    }
    private static void camouflage(GameTestHelper h) {
        var p=player(h); var stack=armor(ArmorSlot.HEAD,0); stack.setDamageValue(300); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Camo test"));
        p.setItemInHand(InteractionHand.MAIN_HAND,stack); p.setShiftKeyDown(true);
        for(int n=1;n<=6;n++) {
            stack.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND); h.assertValueEqual(T2ArmorItem.camo(stack),n%6,"Six source camouflage variants wrap");
            h.assertValueEqual(stack.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier(),TGContent.id(Armors.CAMOS.get(n%6)),"Equipment asset follows selected camouflage");
            h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"Sneak-use changes skin without equipping");
        }
        T2ArmorItem.setCamo(stack,3);
        var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE); var saved=ItemStack.CODEC.encodeStart(ops,stack).getOrThrow(); var restored=ItemStack.CODEC.parse(ops,saved).getOrThrow();
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try { ItemStack.STREAM_CODEC.encode(buffer,restored); var decoded=ItemStack.STREAM_CODEC.decode(buffer);
            h.assertValueEqual(T2ArmorItem.camo(decoded),3,"Camouflage survives storage and networking"); h.assertValueEqual(decoded.getDamageValue(),300,"Damage retained");
            h.assertValueEqual(decoded.get(DataComponents.CUSTOM_NAME),stack.get(DataComponents.CUSTOM_NAME),"Name retained");
            h.assertValueEqual(decoded.get(DataComponents.EQUIPPABLE),stack.get(DataComponents.EQUIPPABLE),"Equipment rendering component survives packet");
        } finally { buffer.release(); }
        h.succeed();
    }
    private static void damage(GameTestHelper h,String kind,float amount,float expected,boolean full) {
        var p=player(h); if(full) equipAll(p); else { p.setItemSlot(EquipmentSlot.HEAD,armor(ArmorSlot.HEAD,0)); p.tick(); }
        p.hurtServer(h.getLevel(),source(h,kind),amount); near(h,1000-p.getHealth(),expected,"Source special armor damage");
        if(kind.equals("fall")) for(var slot:T2ArmorSystem.SLOTS) h.assertValueEqual(p.getItemBySlot(slot).getDamageValue(),0,"Unblockable damage does not wear armor");
        h.succeed();
    }
    private static void mixed(GameTestHelper h) {
        var p=player(h); p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET)); p.setItemSlot(EquipmentSlot.CHEST,armor(ArmorSlot.CHEST,0)); p.tick();
        p.hurtServer(h.getLevel(),physical(h),20); near(h,1000-p.getHealth(),15.30368,"Special absorption precedes ordinary vanilla armor"); h.succeed();
    }
    private static void wear(GameTestHelper h) {
        var p=player(h); var helmet=armor(ArmorSlot.HEAD,0); p.setItemSlot(EquipmentSlot.HEAD,helmet); p.tick();
        p.hurtServer(h.getLevel(),physical(h),10); h.assertValueEqual(helmet.getDamageValue(),3,"1 absorbed wear + 2 ordinary wear, matching Forge 2807"); h.succeed();
    }
    private static void zeroDamage(GameTestHelper h) {
        var p=player(h); equipAll(p); p.hurtServer(h.getLevel(),physical(h),0);
        for(var slot:T2ArmorSystem.SLOTS) h.assertValueEqual(p.getItemBySlot(slot).getDamageValue(),0,"Zero absorbed damage must not trigger minimum wear");
        near(h,p.getHealth(),1000,"Zero damage leaves health unchanged"); h.succeed();
    }
    private static void breakage(GameTestHelper h) {
        var p=player(h); var helmet=armor(ArmorSlot.HEAD,0); helmet.setDamageValue(988); p.setItemSlot(EquipmentSlot.HEAD,helmet); p.tick();
        p.hurtServer(h.getLevel(),physical(h),10); h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"Ordinary second wear pass can break the item after special wear stops at 989"); h.succeed();
    }
    private static void cancelAttack(GameTestHelper h) {
        var p=player(h); equipAll(p); Consumer<LivingIncomingDamageEvent> cancel=e -> { if(e.getEntity()==p) e.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(cancel);
        try { p.hurtServer(h.getLevel(),physical(h),10); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        near(h,p.getHealth(),1000,"Cancelled attack does no damage"); for(var slot:T2ArmorSystem.SLOTS) h.assertValueEqual(p.getItemBySlot(slot).getDamageValue(),0,"No wear before accepted damage stage"); h.succeed();
    }
    private static void cancelWear(GameTestHelper h) {
        var p=player(h); var helmet=armor(ArmorSlot.HEAD,0); helmet.setDamageValue(989); p.setItemSlot(EquipmentSlot.HEAD,helmet); p.tick();
        Consumer<ArmorHurtEvent> cancel=e -> { if(e.getEntity()==p) e.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(cancel);
        try { p.hurtServer(h.getLevel(),physical(h),10); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        near(h,1000-p.getHealth(),8.2,"Source protection remains even with worn bonus/display disabled"); h.assertValueEqual(helmet.getDamageValue(),989,"NeoForge armor wear cancellation respected");
        near(h,p.getArmorValue(),0,"Worn source display is zero"); h.succeed();
    }
    private static AnvilMenu anvil(GameTestHelper h,ItemStack armor,ItemStack material) {
        var menu=new AnvilMenu(1,player(h).getInventory()); menu.getSlot(0).set(armor); menu.getSlot(1).set(material);
        // The actual anvil screen sends its populated name field along with the inputs.
        if(armor.has(DataComponents.CUSTOM_NAME)) menu.setItemName(armor.getHoverName().getString());
        menu.createResult(); return menu;
    }
    private static void directWear(GameTestHelper h) {
        var p=player(h); var helmet=armor(ArmorSlot.HEAD,0); p.setItemSlot(EquipmentSlot.HEAD,helmet); p.tick();
        net.neoforged.neoforge.common.CommonHooks.onArmorHurt(physical(h),new EquipmentSlot[]{EquipmentSlot.HEAD},4,p);
        h.assertValueEqual(helmet.getDamageValue(),4,"Separate helmet/slot wear is not intercepted as regular body armor absorption"); h.succeed();
    }
    private static void repair(GameTestHelper h) {
        var helmet=armor(ArmorSlot.HEAD,3); helmet.setDamageValue(989); helmet.set(DataComponents.CUSTOM_NAME,Component.literal("Repair test"));
        var menu=anvil(h,helmet,TGContent.MATERIALS.get("ingotobsidiansteel").toStack()); var result=menu.getSlot(2).getItem();
        h.assertTrue(!result.isEmpty(),"Obsidian steel repairs armor on vanilla anvil"); h.assertValueEqual(result.getDamageValue(),742,"One ingot restores floor(990/4) durability");
        h.assertValueEqual(T2ArmorItem.camo(result),3,"Repair preserves camouflage"); h.assertValueEqual(result.get(DataComponents.CUSTOM_NAME),helmet.get(DataComponents.CUSTOM_NAME),"Repair preserves name"); h.succeed();
    }
    private static void rejectCloth(GameTestHelper h) {
        var helmet=armor(ArmorSlot.HEAD,0); helmet.setDamageValue(500); var menu=anvil(h,helmet,TGContent.MATERIALS.get("heavycloth").toStack());
        h.assertTrue(menu.getSlot(2).getItem().isEmpty(),"Cloth belongs to the future repair bench recipe, not vanilla anvil repair"); h.succeed();
    }
    private static void pairRepair(GameTestHelper h) {
        var first=armor(ArmorSlot.HEAD,3); var second=armor(ArmorSlot.HEAD,5); first.setDamageValue(600); second.setDamageValue(600);
        var input=CraftingInput.of(2,1,List.of(first,second)); var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow(); var result=recipe.value().assemble(input);
        h.assertValueEqual(result.getDamageValue(),161,"Ordinary pair-repair durability bonus"); h.assertValueEqual(T2ArmorItem.camo(result),0,"Crafted armor starts at original default camouflage"); h.succeed();
    }
    private static void enchant(GameTestHelper h) {
        var helmet=armor(ArmorSlot.HEAD,3); var book=new ItemStack(Items.ENCHANTED_BOOK); var mending=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
        var stored=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY); stored.set(mending,1); book.set(DataComponents.STORED_ENCHANTMENTS,stored.toImmutable());
        var result=anvil(h,helmet,book).getSlot(2).getItem(); h.assertTrue(!result.isEmpty(),"Supported book applies on anvil despite zero enchanting-table value");
        h.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(mending,result),1,"Mending retained"); h.succeed();
    }
    private static void bonuses(GameTestHelper h) {
        var p=player(h); equipAll(p); for(int n=0;n<10;n++) T2ArmorSystem.refresh(p);
        near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.14,"Four +10% bonuses do not accumulate on repeated refresh");
        near(h,p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),.6,"Original per-piece knockback bonuses");
        p.setSprinting(true); T2ArmorSystem.refresh(p); near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.234,"Sprint doubles gear bonus, then composes with vanilla sprint");
        for(var slot:T2ArmorSystem.SLOTS) p.getItemBySlot(slot).setDamageValue(989); T2ArmorSystem.refresh(p);
        near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.13,"Worn gear removes speed bonus"); near(h,p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,"Worn gear removes knockback bonus"); h.succeed();
    }
    private static void jump(GameTestHelper h) {
        var p=player(h); var boots=armor(ArmorSlot.FEET,0); p.setItemSlot(EquipmentSlot.FEET,boots); p.tick(); p.jumpFromGround(); near(h,p.getDeltaMovement().y,.52,"Boots add .1 after native jump");
        boots.setDamageValue(989); p.setDeltaMovement(0,0,0); p.jumpFromGround(); near(h,p.getDeltaMovement().y,.42,"Worn boots give no jump bonus"); h.succeed();
    }
    private static void unequip(GameTestHelper h) {
        var p=player(h); equipAll(p); for(var slot:T2ArmorSystem.SLOTS) p.setItemSlot(slot,ItemStack.EMPTY);
        p.hurtServer(h.getLevel(),source(h,"bullet"),9); near(h,1000-p.getHealth(),9,"Unequip removes HUD modifier before same-tick incoming damage"); near(h,p.getArmorValue(),0,"No ghost armor display"); h.succeed();
    }
    private ArmorGameTests() {}
}
