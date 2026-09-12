package techguns.modern.test;

import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.armor.*;
import techguns.modern.machine.camo.*;
import techguns.modern.machine.grinder.*;
import techguns.modern.machine.repair.*;
import techguns.modern.radiation.RadiationSystem;

final class T1CombatGameTests {
    private static final BlockPos POS=new BlockPos(4,2,4);
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var slot:ArmorSlot.values()) {
            String name=slot.name().toLowerCase(Locale.ROOT);
            r.register("t1_crafting_"+name, () -> h -> crafting(h,slot));
            r.register("t1_repair_"+name, () -> h -> repair(h,slot));
            for(int damage:new int[]{0,1,824}) r.register("grinder_t1_"+name+"_"+damage, () -> h -> grinder(h,slot,damage));
        }
        r.register("t1_equip_properties", () -> T1CombatGameTests::equip);
        r.register("t1_fixed_appearance_no_recolor", () -> T1CombatGameTests::noCamo);
        r.register("t1_item_save_and_packet", () -> T1CombatGameTests::save);
        r.register("t1_knockback_without_speed", () -> T1CombatGameTests::bonuses);
        r.register("t1_tooltip_has_no_false_speed_or_camo", () -> T1CombatGameTests::tooltip);
        r.register("t1_physical_absorption", () -> h -> damage(h,"physical",10,4));
        r.register("t1_fire_absorption", () -> h -> damage(h,"fire",14,7.7f));
        r.register("t1_acid_absorption", () -> h -> damage(h,"acid",10,5.5f));
        r.register("t1_raw_bullet_penetration", () -> h -> damage(h,"bullet",9,3.6f));
        r.register("t1_worn_absorption_and_breakage", () -> T1CombatGameTests::wear);
        r.register("t1_mixed_t2_hazmat", () -> T1CombatGameTests::mixed);
        r.register("t1_anvil_pair_and_books", () -> T1CombatGameTests::anvil);
        r.register("t1_cancelled_damage_no_wear", () -> T1CombatGameTests::cancel);
        r.register("t1_npc_has_no_player_bonus", () -> T1CombatGameTests::mob);
        r.register("t1_repair_shortage_and_wrong_metal", () -> T1CombatGameTests::shortage);
    }
    private static ItemStack armor(ArmorSlot slot,int damage) { var stack=ArmorContent.T1_COMBAT.get(slot).toStack(); stack.setDamageValue(damage); return stack; }
    private static ItemStack cloth(int n) { return TGContent.MATERIALS.get("heavycloth").toStack(n); }
    private static Player player(GameTestHelper h) { var p=ArmorGameTests.player(h); p.getInventory().clearContent(); return p; }
    private static void suit(Player p,int damage) { for(var slot:ArmorSlot.values()) p.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,damage)); p.tick(); }
    private static void near(GameTestHelper h,double actual,double expected,String text) { ArmorGameTests.near(h,actual,expected,text); }
    private static DamageSource source(GameTestHelper h,String kind) {
        if(kind.equals("physical")) return h.getLevel().damageSources().playerAttack(WeaponGameTests.player(h));
        var key=kind.equals("fire")?NetherBlasterProjectile.DAMAGE_TYPE:techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE;
        if(kind.equals("bullet")) {
            var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47")); bullet.setOwner(WeaponGameTests.player(h));
            return ShotDamage.PLAYER.source(h.getLevel(),net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet);
        }
        return new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key));
    }
    private static ItemStack craft(GameTestHelper h,int width,int height,List<ItemStack> items) {
        var input=CraftingInput.of(width,height,items); return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input);
    }
    private static void crafting(GameTestHelper h,ArmorSlot slot) {
        String[] patterns={"iiic c   ","c ciiiccc","iiic cc c","c ci i   "}; var items=new ArrayList<ItemStack>();
        for(char c:patterns[slot.ordinal()].toCharArray()) items.add(c=='i'?new ItemStack(Items.IRON_INGOT):c=='c'?cloth(1):ItemStack.EMPTY);
        var output=craft(h,3,3,items); h.assertTrue(output.is(ArmorContent.T1_COMBAT.get(slot).get()) && output.getCount()==1,"Original iron/cloth shaped recipe"); h.succeed();
    }
    private static void equip(GameTestHelper h) {
        var p=player(h); p.setShiftKeyDown(true);
        for(var slot:ArmorSlot.values()) {
            var stack=armor(slot,0); h.assertTrue(stack.getMaxDamage()==825 && !stack.isEnchantable(),"Original durability and zero enchanting-table value");
            h.assertValueEqual(stack.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier(),TGContent.id("t1_combat"),"Fixed original equipment asset");
            p.setItemInHand(InteractionHand.MAIN_HAND,stack); stack.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
            h.assertTrue(p.getItemBySlot(EquipmentSlot.valueOf(slot.name())).is(ArmorContent.T1_COMBAT.get(slot).get()),"Sneak-use equips single-appearance armor rather than pretending to recolor it");
        }
        p.tick(); near(h,p.getArmorValue(),16,"Per-piece rounded HUD values 4+5+4+3"); near(h,p.getAttributeValue(Attributes.ARMOR_TOUGHNESS),2,"Four times half a toughness"); h.succeed();
    }
    private static void noCamo(GameTestHelper h) {
        var p=player(h); suit(p,200); h.setBlock(POS,CamoBenchContent.BLOCK.get()); var bench=h.getBlockEntity(POS,CamoBenchBlockEntity.class); bench.setOwner(p);
        var menu=new CamoBenchMenu(71,p.getInventory(),bench); p.containerMenu=menu;
        for(var slot:ArmorSlot.values()) {
            var stack=armor(slot,200); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Fixed skin")); bench.setItem(0,stack);
            h.assertTrue(CamoCycling.count(stack)==0 && CamoCycling.index(stack)==-1 && CamoCycling.change(stack,false).isEmpty(),"T1 is not ICamoChangeable");
            h.assertTrue(!menu.clickMenuButton(p,1) && !menu.clickMenuButton(p,2),"Input recolor requests rejected");
            h.assertTrue(!menu.clickMenuButton(p,3+2*slot.ordinal()) && !menu.clickMenuButton(p,4+2*slot.ordinal()),"Equipped recolor requests rejected");
            h.assertTrue(ItemStack.matches(stack,bench.getItem(0)),"Rejected action preserves the stack and components");
        }
        h.succeed();
    }
    private static void save(GameTestHelper h) {
        for(var slot:ArmorSlot.values()) {
            var stack=armor(slot,700); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Old uniform"));
            var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE); var saved=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,stack).getOrThrow()).getOrThrow();
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try { ItemStack.STREAM_CODEC.encode(buffer,saved); h.assertTrue(ItemStack.matches(ItemStack.STREAM_CODEC.decode(buffer),stack),"Fixed asset, name, wear and attributes survive save/network"); } finally { buffer.release(); }
        }
        h.succeed();
    }
    private static void bonuses(GameTestHelper h) {
        var p=player(h); suit(p,0); for(int i=0;i<5;i++) TGArmorSystem.refresh(p);
        near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.1,"No extra movement speed"); near(h,p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),.4,"Original total knockback bonus");
        near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),0,"T1 does not inherit Hazmat radiation resistance");
        p.setDeltaMovement(0,0,0); p.knockback(1,1,0,source(h,"physical"),10); near(h,p.getDeltaMovement().x,-.6,"Native knockback respects forty percent resistance");
        for(var slot:TGArmorSystem.SLOTS) p.getItemBySlot(slot).setDamageValue(824); TGArmorSystem.refresh(p);
        near(h,p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,"Worn gear loses movement bonuses"); near(h,p.getArmorValue(),0,"Worn display disabled");
        p.setDeltaMovement(0,0,0); p.knockback(1,1,0,source(h,"physical"),10); near(h,p.getDeltaMovement().x,-1,"Worn gear has no hidden knockback reduction"); h.succeed();
    }
    private static List<TranslatableContents> tooltips(ItemStack stack) {
        var lines=new ArrayList<Component>(); stack.getItem().appendHoverText(stack,Item.TooltipContext.EMPTY,TooltipDisplay.DEFAULT,lines::add,TooltipFlag.NORMAL);
        return lines.stream().map(Component::getContents).filter(TranslatableContents.class::isInstance).map(TranslatableContents.class::cast).toList();
    }
    private static void tooltip(GameTestHelper h) {
        for(var slot:ArmorSlot.values()) {
            var lines=tooltips(armor(slot,0));
            h.assertTrue(lines.stream().noneMatch(t -> t.getKey().equals("tooltip.techguns.armor.speed") || t.getKey().equals("tooltip.techguns.armor.camo")),"Single-skin T1 does not advertise speed or camouflage controls");
            var kb=lines.stream().filter(t -> t.getKey().equals("tooltip.techguns.armor.knockback")).findFirst().orElseThrow();
            h.assertValueEqual(((Number)kb.getArgs()[0]).intValue(),new int[]{5,20,10,5}[slot.ordinal()],"Tooltip uses this piece's real bonus");
        }
        h.assertTrue(tooltips(ArmorGameTests.armor(ArmorSlot.HEAD,0)).stream().anyMatch(t -> t.getKey().equals("tooltip.techguns.armor.speed")),"T2 still advertises its actual speed bonus"); h.succeed();
    }
    private static void damage(GameTestHelper h,String kind,float amount,float expected) { var p=player(h); suit(p,0); p.hurtServer(h.getLevel(),source(h,kind),amount); near(h,1000-p.getHealth(),expected,"T1 typed armor for "+kind); h.succeed(); }
    private static void wear(GameTestHelper h) {
        var p=player(h); suit(p,0); p.hurtServer(h.getLevel(),source(h,"physical"),10);
        for(var slot:TGArmorSystem.SLOTS) h.assertValueEqual(p.getItemBySlot(slot).getDamageValue(),2,"One special plus one ordinary wear");
        for(var slot:TGArmorSystem.SLOTS) p.getItemBySlot(slot).setDamageValue(824); p.invulnerableTime=0; p.hurtServer(h.getLevel(),source(h,"physical"),10);
        near(h,p.getHealth(),992,"Worn source protection applies to the breaking attack");
        for(var slot:TGArmorSystem.SLOTS) h.assertTrue(p.getItemBySlot(slot).isEmpty(),"Ordinary second wear pass can break T1"); h.succeed();
    }
    private static void mixed(GameTestHelper h) {
        var p=player(h); p.setItemSlot(EquipmentSlot.HEAD,armor(ArmorSlot.HEAD,0)); p.setItemSlot(EquipmentSlot.CHEST,ArmorGameTests.armor(ArmorSlot.CHEST,0)); p.setItemSlot(EquipmentSlot.FEET,ArmorContent.HAZMAT.get(ArmorSlot.FEET).toStack()); p.tick();
        near(h,p.getAttributeValue(Attributes.ARMOR_TOUGHNESS),1.5,"Fractional toughness from mixed tiers"); near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),1,"Only Hazmat supplies radiation resistance");
        near(h,p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),.3,"Tier-specific knockback adds without duplication");
        p.hurtServer(h.getLevel(),source(h,"physical"),20); near(h,1000-p.getHealth(),11.08,"Mixed source physical protections add correctly"); h.succeed();
    }
    private static AnvilMenu anvilMenu(GameTestHelper h,ItemStack stack,ItemStack material) {
        var menu=new AnvilMenu(1,player(h).getInventory()); menu.getSlot(0).set(stack); menu.getSlot(1).set(material); if(stack.has(DataComponents.CUSTOM_NAME)) menu.setItemName(stack.getHoverName().getString()); menu.createResult(); return menu;
    }
    private static void anvil(GameTestHelper h) {
        for(var slot:ArmorSlot.values()) {
            var stack=armor(slot,824); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Repaired uniform")); var result=anvilMenu(h,stack,new ItemStack(Items.IRON_INGOT)).getSlot(2).getItem();
            h.assertTrue(!result.isEmpty() && result.getDamageValue()==618 && result.getHoverName().getString().equals("Repaired uniform"),"One vanilla iron ingot restores floor(825/4), retaining name");
            h.assertTrue(anvilMenu(h,stack,cloth(1)).getSlot(2).getItem().isEmpty(),"Anvil uses metal, not bench cloth");
        }
        var pair=craft(h,2,1,List.of(armor(ArmorSlot.HEAD,600),armor(ArmorSlot.HEAD,600))); h.assertValueEqual(pair.getDamageValue(),334,"Native pair repair includes 41-point bonus");
        var book=new ItemStack(Items.ENCHANTED_BOOK); var mending=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING); var enchants=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY); enchants.set(mending,1); book.set(DataComponents.STORED_ENCHANTMENTS,enchants.toImmutable());
        h.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(mending,anvilMenu(h,armor(ArmorSlot.HEAD,0),book).getSlot(2).getItem()),1,"Armor tags allow Mending despite zero table enchantability"); h.succeed();
    }
    private static void cancel(GameTestHelper h) {
        var p=player(h); suit(p,0); Consumer<LivingIncomingDamageEvent> cancel=e -> { if(e.getEntity()==p) e.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(cancel);
        try { p.hurtServer(h.getLevel(),source(h,"physical"),10); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        near(h,p.getHealth(),1000,"Cancelled attack does no damage"); for(var slot:TGArmorSystem.SLOTS) h.assertValueEqual(p.getItemBySlot(slot).getDamageValue(),0,"Cancelled attack does not wear T1"); h.succeed();
    }
    private static void mob(GameTestHelper h) {
        var mob=h.spawn(EntityTypes.HUSK,new net.minecraft.world.phys.Vec3(4,2,4)); mob.setNoAi(true); double kb=mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        for(var slot:ArmorSlot.values()) mob.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,0)); mob.tick();
        near(h,mob.getAttributeValue(Attributes.ARMOR_TOUGHNESS),2,"Native equipment toughness applies to NPC wearer"); near(h,mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),kb,"Player-only bonus does not leak onto mob"); h.succeed();
    }
    private static RepairBenchBlockEntity bench(GameTestHelper h,Player p) {
        h.setBlock(POS,RepairBenchContent.BLOCK.get()); var bench=h.getBlockEntity(POS,RepairBenchBlockEntity.class); bench.setOwner(p); p.containerMenu=new RepairBenchMenu(72,p.getInventory(),bench); return bench;
    }
    private static void repair(GameTestHelper h,ArmorSlot slot) {
        var p=player(h); var bench=bench(h,p); var stack=armor(slot,824); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Field uniform")); p.setItemSlot(EquipmentSlot.valueOf(slot.name()),stack);
        int[][] counts={{1,1},{2,2},{1,2},{1,1}}; bench.setItem(0,new ItemStack(Items.IRON_INGOT,counts[slot.ordinal()][0])); bench.setItem(1,cloth(counts[slot.ordinal()][1]));
        h.assertTrue(p.containerMenu.clickMenuButton(p,slot.ordinal()+1),"Original per-piece iron/cloth repair cost"); h.assertValueEqual(stack.getDamageValue(),0,"Full repair"); h.assertTrue(bench.isEmpty() && stack.getHoverName().getString().equals("Field uniform"),"Exact material consumption and retained name"); h.succeed();
    }
    private static void shortage(GameTestHelper h) {
        var p=player(h); var bench=bench(h,p); var stack=armor(ArmorSlot.CHEST,824); bench.setItem(9,stack); bench.setItem(0,TGContent.MATERIALS.get("ingotobsidiansteel").toStack(2)); bench.setItem(1,cloth(2));
        h.assertTrue(!p.containerMenu.clickMenuButton(p,6),"T2 metal is not a replacement for T1 iron"); bench.setItem(0,new ItemStack(Items.IRON_INGOT,2)); bench.setItem(1,cloth(1));
        h.assertTrue(!p.containerMenu.clickMenuButton(p,6),"One missing cloth cancels repair"); h.assertValueEqual(bench.getItem(0).getCount(),2,"Iron extraction rolled back"); h.assertValueEqual(stack.getDamageValue(),824,"No partial repair"); h.succeed();
    }
    private static void grinder(GameTestHelper h,ArmorSlot slot,int damage) {
        h.setBlock(POS,GrinderContent.BLOCK.get()); var m=h.getBlockEntity(POS,GrinderBlockEntity.class); m.setItem(0,armor(slot,damage)); try(var tx=Transaction.openRoot()) { m.energy().insert(500,tx); tx.commit(); }
        int[][] healthy={{2,1},{3,2},{2,2},{2,1}}, worn={{1,1},{2,2},{1,2},{1,1}}; int[] expected=damage==824?new int[]{1,0}:damage==0?healthy[slot.ordinal()]:worn[slot.ordinal()];
        h.succeedWhen(() -> { h.assertTrue(!m.working() && m.getItem(0).isEmpty(),"Real server finishes T1 recycling"); h.assertTrue(m.getItem(2).is(Items.IRON_INGOT) && m.getItem(2).getCount()==expected[0],"Source vanilla iron output");
            h.assertTrue(expected[1]==0?m.getItem(3).isEmpty():m.getItem(3).is(cloth(1).getItem()) && m.getItem(3).getCount()==expected[1],"Source cloth output and healthy rounding");
            for(int i=4;i<11;i++) h.assertTrue(m.getItem(i).isEmpty(),"No foreign armor material output"); h.assertValueEqual(m.energy().getAmountAsInt(),0,"One 500 FE cycle"); });
    }
}
