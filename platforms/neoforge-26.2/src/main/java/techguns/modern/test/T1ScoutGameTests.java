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
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.armor.*;
import techguns.modern.machine.camo.*;
import techguns.modern.machine.grinder.*;
import techguns.modern.machine.repair.*;
import techguns.modern.radiation.RadiationSystem;

final class T1ScoutGameTests {
    private static final BlockPos POS=new BlockPos(4,2,4);
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var slot:ArmorSlot.values()) {
            String name=slot.name().toLowerCase(Locale.ROOT);
            r.register("scout_crafting_"+name,() -> h -> crafting(h,slot));
            r.register("scout_camo_save_and_menu_"+name,() -> h -> camo(h,slot));
            r.register("scout_repair_"+name,() -> h -> repair(h,slot));
            for(int damage:new int[]{0,1,824}) r.register("grinder_scout_"+name+"_"+damage,() -> h -> grinder(h,slot,damage));
        }
        r.register("scout_equip_and_properties",() -> T1ScoutGameTests::equip);
        r.register("scout_tooltip_per_part_bonuses",() -> T1ScoutGameTests::tooltip);
        r.register("scout_speed_sprint_jump_and_fall",() -> T1ScoutGameTests::bonuses);
        r.register("scout_native_fall_damage",() -> T1ScoutGameTests::fall);
        r.register("scout_physical_damage",() -> h -> damage(h,"physical",10,4.8f));
        r.register("scout_fire_damage",() -> h -> damage(h,"fire",10,6.1f));
        r.register("scout_acid_damage",() -> h -> damage(h,"acid",10,6.1f));
        r.register("scout_bullet_penetration",() -> h -> damage(h,"bullet",9,5.04f));
        r.register("scout_worn_defense_and_mixed_wear",() -> T1ScoutGameTests::wear);
        r.register("scout_anvil_pair_and_books",() -> T1ScoutGameTests::anvil);
        r.register("scout_repair_shortage_and_wrong_material",() -> T1ScoutGameTests::shortage);
        r.register("grinder_scout_three_workbench_cycle",() -> T1ScoutGameTests::cycle);
        r.register("scout_npc_has_no_player_bonuses",() -> T1ScoutGameTests::mob);
        r.register("scout_mixed_bonuses_and_effects",() -> T1ScoutGameTests::mixedBonuses);
    }
    private static ItemStack armor(ArmorSlot slot,int damage,int camo) {
        var stack=ArmorContent.T1_SCOUT.get(slot).toStack(); stack.setDamageValue(damage); TGArmorItem.setCamo(stack,camo); return stack;
    }
    private static ItemStack cloth(int n) { return TGContent.MATERIALS.get("heavycloth").toStack(n); }
    private static Player player(GameTestHelper h) { var p=ArmorGameTests.player(h); p.getInventory().clearContent(); return p; }
    private static void suit(Player p,int wear) { for(var slot:ArmorSlot.values()) p.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,wear,0)); p.tick(); }
    private static void near(GameTestHelper h,double actual,double expected,String message) { ArmorGameTests.near(h,actual,expected,message); }
    private static ItemStack craft(GameTestHelper h,int width,int height,List<ItemStack> items) {
        var input=CraftingInput.of(width,height,items); return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input);
    }
    private static void crafting(GameTestHelper h,ArmorSlot slot) {
        String[] patterns={"cccc c   ","c ccccccc","cccc cc c","c cc c   "}; var items=new ArrayList<ItemStack>();
        for(char c:patterns[slot.ordinal()].toCharArray()) items.add(c=='c'?cloth(1):ItemStack.EMPTY);
        var output=craft(h,3,3,items); h.assertTrue(output.is(ArmorContent.T1_SCOUT.get(slot).get()) && output.getCount()==1,"Original shaped recipe uses only heavy cloth");
        h.assertValueEqual(TGArmorItem.camo(output),0,"Crafted armor starts with the default skin");
        items.set(0,TGContent.MATERIALS.get("protectivefiber").toStack());
        h.assertTrue(h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,CraftingInput.of(3,3,items),h.getLevel()).isEmpty(),"Hazmat fiber cannot substitute Scout cloth"); h.succeed();
    }

    private static void equip(GameTestHelper h) {
        var p=player(h);
        for(var slot:ArmorSlot.values()) {
            var stack=armor(slot,0,0); h.assertTrue(stack.getMaxDamage()==825 && !stack.isEnchantable(),"825 durability and no enchanting-table value");
            p.setItemInHand(InteractionHand.MAIN_HAND,stack); stack.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
            h.assertTrue(p.getItemBySlot(EquipmentSlot.valueOf(slot.name())).is(ArmorContent.T1_SCOUT.get(slot).get()),"Normal use equips armor");
        }
        p.tick(); near(h,p.getArmorValue(),13,"Per-part rounded HUD 3+4+3+3"); near(h,p.getAttributeValue(Attributes.ARMOR_TOUGHNESS),0,"No native toughness");
        near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),0,"No inherited Hazmat radiation bonus"); near(h,p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,"No invented knockback bonus"); h.succeed();
    }
    private static void camo(GameTestHelper h,ArmorSlot slot) {
        var p=player(h); var stack=armor(slot,700,0); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Scout test"));
        p.setShiftKeyDown(true); p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        for(int i=1;i<=4;i++) {
            stack.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND); h.assertValueEqual(TGArmorItem.camo(stack),i%4,"Sneak-use cycles and wraps four source skins");
            h.assertValueEqual(stack.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier(),TGContent.id(Armors.T1_SCOUT.getFirst().camos().get(i%4)),"Equipment layer follows current camo");
        }
        p.setShiftKeyDown(false); h.setBlock(POS,CamoBenchContent.BLOCK.get()); var bench=h.getBlockEntity(POS,CamoBenchBlockEntity.class); bench.setOwner(p);
        var menu=new CamoBenchMenu(91,p.getInventory(),bench); p.containerMenu=menu; p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); bench.setItem(0,stack);
        h.assertTrue(menu.clickMenuButton(p,2),"Reverse input recoloring works"); var changed=bench.getItem(0); h.assertValueEqual(TGArmorItem.camo(changed),3,"Input reverse wraps to index 3");
        bench.setItem(0,ItemStack.EMPTY); p.setItemSlot(EquipmentSlot.valueOf(slot.name()),changed);
        h.assertTrue(menu.clickMenuButton(p,3+2*slot.ordinal()),"Equipped armor can be recolored"); changed=p.getItemBySlot(EquipmentSlot.valueOf(slot.name()));
        h.assertValueEqual(TGArmorItem.camo(changed),0,"Equipped forward wraps to zero");
        h.assertValueEqual(((TranslatableContents)CamoCycling.variantName(changed).getContents()).getKey(),"tooltip.techguns.armor.t1_scout.camo.0","All Scout pieces share palette names");
        var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE); var restored=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,changed).getOrThrow()).getOrThrow();
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try { ItemStack.STREAM_CODEC.encode(buffer,restored); h.assertTrue(ItemStack.matches(ItemStack.STREAM_CODEC.decode(buffer),changed),"Wear, name, armor asset and camo survive save/network"); }
        finally { buffer.release(); }
        h.assertValueEqual(changed.getDamageValue(),700,"Recolor does not repair armor"); h.succeed();
    }
    private static List<TranslatableContents> tooltips(ItemStack stack) {
        var lines=new ArrayList<Component>(); stack.getItem().appendHoverText(stack,Item.TooltipContext.EMPTY,TooltipDisplay.DEFAULT,lines::add,TooltipFlag.NORMAL);
        return lines.stream().map(Component::getContents).filter(TranslatableContents.class::isInstance).map(TranslatableContents.class::cast).toList();
    }
    private static void tooltip(GameTestHelper h) {
        for(var slot:ArmorSlot.values()) {
            var lines=tooltips(armor(slot,0,0));
            var speed=lines.stream().filter(t -> t.getKey().equals("tooltip.techguns.armor.speed")).findFirst().orElseThrow();
            near(h,((Number)speed.getArgs()[0]).doubleValue(),12.5,"Fractional speed percentage is not rounded to 13");
            near(h,((Number)speed.getArgs()[1]).doubleValue(),25,"Doubled sprint bonus percentage");
            var jump=lines.stream().filter(t -> t.getKey().equals("tooltip.techguns.armor.jump")).findFirst().orElseThrow();
            near(h,((Number)jump.getArgs()[0]).doubleValue(),slot==ArmorSlot.FEET?.1:.02,"Tooltip uses the actual per-part jump bonus");
            h.assertTrue(lines.stream().noneMatch(t -> t.getKey().equals("tooltip.techguns.armor.mining") || t.getKey().equals("tooltip.techguns.armor.knockback") || t.getKey().equals("tooltip.techguns.armor.radiation_resistance")),"No invented mining, combat or radiation bonuses");
            h.assertTrue(tooltips(armor(slot,824,0)).stream().noneMatch(t -> t.getKey().equals("tooltip.techguns.armor.jump") || t.getKey().equals("tooltip.techguns.armor.speed")),"Worn tooltips omit inactive bonuses");
        }
        for(var item:List.of(ArmorContent.ITEMS.get(ArmorSlot.FEET),ArmorContent.T1_MINER.get(ArmorSlot.FEET))) {
            var jump=tooltips(item.toStack()).stream().filter(t -> t.getKey().equals("tooltip.techguns.armor.jump")).findFirst().orElseThrow();
            near(h,((Number)jump.getArgs()[0]).doubleValue(),.1,"Previously ported boots keep their tooltip value");
        }
        h.assertTrue(tooltips(ArmorContent.HAZMAT.get(ArmorSlot.FEET).toStack()).stream().noneMatch(t -> t.getKey().equals("tooltip.techguns.armor.jump")),"Hazmat boots do not acquire an invented jump tooltip");
        h.succeed();
    }

    private static void bonuses(GameTestHelper h) {
        var p=player(h); suit(p,0); for(int i=0;i<5;i++) TGArmorSystem.refresh(p);
        near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.15,"Full Scout gives fifty percent movement without stacking refreshes");
        p.setSprinting(true); TGArmorSystem.refresh(p); near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.26,"Doubled gear bonus composes with native sprint");
        p.jumpFromGround(); near(h,p.getDeltaMovement().y,.58,"Every part contributes after the native jump");
        p.getItemBySlot(EquipmentSlot.FEET).setDamageValue(824); p.setDeltaMovement(0,0,0); p.jumpFromGround(); near(h,p.getDeltaMovement().y,.48,"Three non-boot pieces retain their smaller jump bonuses");
        p.getItemBySlot(EquipmentSlot.FEET).setDamageValue(0);
        for(double distance:new double[]{.25,1,3,10}) { var event=new LivingFallEvent(p,distance,1); NeoForge.EVENT_BUS.post(event); near(h,event.getDistance(),Math.max(0,distance-1)*.8,"Boot fall distance offset then multiplier"); }
        for(var slot:TGArmorSystem.SLOTS) p.getItemBySlot(slot).setDamageValue(824); TGArmorSystem.refresh(p);
        near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.13,"Only native sprint remains when worn");
        p.setDeltaMovement(0,0,0); p.jumpFromGround(); near(h,p.getDeltaMovement().y,.42,"Worn armor leaves native jump unchanged");
        var event=new LivingFallEvent(p,10,1); NeoForge.EVENT_BUS.post(event); near(h,event.getDistance(),10,"Worn boots lose fall protection"); h.succeed();
    }

    private static void fall(GameTestHelper h) {
        var p=player(h); p.setItemSlot(EquipmentSlot.FEET,armor(ArmorSlot.FEET,0,0)); p.tick();
        p.causeFallDamage(10,1,h.getLevel().damageSources().fall()); near(h,p.getHealth(),996,"Native floor((10-1)*.8-3+epsilon) gives four damage");
        p.invulnerableTime=0; p.getItemBySlot(EquipmentSlot.FEET).setDamageValue(824); p.causeFallDamage(10,1,h.getLevel().damageSources().fall()); near(h,p.getHealth(),989,"Worn boots receive full seven damage"); h.succeed();
    }
    private static DamageSource source(GameTestHelper h,String kind) {
        if(kind.equals("physical")) return h.getLevel().damageSources().playerAttack(WeaponGameTests.player(h));
        if(kind.equals("bullet")) {
            var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47")); bullet.setOwner(WeaponGameTests.player(h));
            return ShotDamage.PLAYER.source(h.getLevel(),net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet);
        }
        var key=kind.equals("fire")?NetherBlasterProjectile.DAMAGE_TYPE:techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE;
        return new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key));
    }
    private static void damage(GameTestHelper h,String kind,float amount,float expected) {
        var p=player(h); suit(p,0); p.hurtServer(h.getLevel(),source(h,kind),amount); near(h,1000-p.getHealth(),expected,"Source fractional protection");
        for(var slot:TGArmorSystem.SLOTS) h.assertValueEqual(p.getItemBySlot(slot).getDamageValue(),1,"Special wear only: no ordinary armor or toughness"); h.succeed();
    }
    private static void wear(GameTestHelper h) {
        var p=player(h); suit(p,823); p.hurtServer(h.getLevel(),source(h,"physical"),10); near(h,p.getHealth(),995.2,"Healthy protection at wear boundary");
        for(var slot:TGArmorSystem.SLOTS) h.assertValueEqual(p.getItemBySlot(slot).getDamageValue(),824,"Special wear stops at one durability");
        p.invulnerableTime=0; p.hurtServer(h.getLevel(),source(h,"physical"),10); near(h,p.getHealth(),990.4,"Worn pieces retain typed protection");
        near(h,p.getArmorValue(),0,"Worn armor display disabled");
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET)); p.tick(); p.invulnerableTime=0; p.hurtServer(h.getLevel(),source(h,"physical"),10);
        h.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).isEmpty(),"Mixing ordinary armor enables second wear stage and can break worn scout armor"); h.succeed();
    }
    private static AnvilMenu anvilMenu(GameTestHelper h,ItemStack stack,ItemStack material) {
        var menu=new AnvilMenu(1,player(h).getInventory()); menu.getSlot(0).set(stack); menu.getSlot(1).set(material);
        if(stack.has(DataComponents.CUSTOM_NAME)) menu.setItemName(stack.getHoverName().getString()); menu.createResult(); return menu;
    }
    private static void anvil(GameTestHelper h) {
        for(var slot:ArmorSlot.values()) {
            var stack=armor(slot,824,3); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Keep camo"));
            var result=anvilMenu(h,stack,cloth(1)).getSlot(2).getItem();
            h.assertTrue(!result.isEmpty() && result.getDamageValue()==618,"Cloth anvil repair restores floor(825/4)");
            h.assertTrue(TGArmorItem.camo(result)==3 && result.getHoverName().getString().equals("Keep camo"),"Anvil retains camo and name");
            h.assertTrue(anvilMenu(h,stack,new ItemStack(Items.IRON_INGOT)).getSlot(2).getItem().isEmpty(),"Iron is not a Scout repair material");
        }
        var pair=craft(h,2,1,List.of(armor(ArmorSlot.HEAD,600,1),armor(ArmorSlot.HEAD,600,3)));
        h.assertValueEqual(pair.getDamageValue(),334,"Native pair repair bonus"); h.assertValueEqual(TGArmorItem.camo(pair),0,"Pair repair uses default appearance");
        var book=new ItemStack(Items.ENCHANTED_BOOK); var mending=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
        var enchant=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY); enchant.set(mending,1); book.set(DataComponents.STORED_ENCHANTMENTS,enchant.toImmutable());
        h.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(mending,anvilMenu(h,armor(ArmorSlot.HEAD,0,0),book).getSlot(2).getItem()),1,"Armor tags permit books"); h.succeed();
    }
    private static RepairBenchBlockEntity bench(GameTestHelper h,Player p) {
        h.setBlock(POS,RepairBenchContent.BLOCK.get()); var bench=h.getBlockEntity(POS,RepairBenchBlockEntity.class); bench.setOwner(p); p.containerMenu=new RepairBenchMenu(92,p.getInventory(),bench); return bench;
    }
    private static void repair(GameTestHelper h,ArmorSlot slot) {
        var p=player(h); var bench=bench(h,p); var stack=armor(slot,824,3); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Scout repair")); p.setItemSlot(EquipmentSlot.valueOf(slot.name()),stack);
        int n=new int[]{2,4,3,2}[slot.ordinal()]; bench.setItem(0,cloth(n));
        h.assertTrue(p.containerMenu.clickMenuButton(p,slot.ordinal()+1),"Cloth alone repairs each equipped Scout piece");
        h.assertTrue(stack.getDamageValue()==0 && TGArmorItem.camo(stack)==3 && stack.getHoverName().getString().equals("Scout repair") && bench.isEmpty(),"Exact cloth consumed; name and camouflage retained");
        p.tick(); near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.1125,"Repair restores movement bonus"); h.succeed();
    }

    private static void shortage(GameTestHelper h) {
        var p=player(h); var bench=bench(h,p); var stack=armor(ArmorSlot.CHEST,824,2); bench.setItem(9,stack); bench.setItem(0,new ItemStack(Items.IRON_INGOT,64));
        h.assertTrue(!p.containerMenu.clickMenuButton(p,6),"Metal cannot replace cloth");
        bench.setItem(1,cloth(3)); h.assertTrue(!p.containerMenu.clickMenuButton(p,6),"One missing cloth cancels the whole repair");
        h.assertTrue(stack.getDamageValue()==824 && bench.getItem(0).getCount()==64 && bench.getItem(1).getCount()==3,"No partial repair or material consumption");
        bench.setItem(1,cloth(4)); h.assertTrue(p.containerMenu.clickMenuButton(p,6),"Full cloth cost permits repair");
        h.assertTrue(stack.getDamageValue()==0 && bench.getItem(0).getCount()==64 && bench.getItem(1).isEmpty(),"Unrelated iron remains untouched"); h.succeed();
    }

    private static GrinderBlockEntity grind(GameTestHelper h,ItemStack stack) {
        h.setBlock(POS,GrinderContent.BLOCK.get()); var m=h.getBlockEntity(POS,GrinderBlockEntity.class); m.setItem(0,stack);
        try(var tx=Transaction.openRoot()) { m.energy().insert(500,tx); tx.commit(); } return m;
    }
    private static void grinder(GameTestHelper h,ArmorSlot slot,int damage) {
        var m=grind(h,armor(slot,damage,3)); int[] healthy={3,5,4,3},used={2,4,3,2};
        int expected=damage==824?1:damage==0?healthy[slot.ordinal()]:used[slot.ordinal()];
        h.succeedWhen(() -> { h.assertTrue(!m.working() && m.getItem(0).isEmpty(),"Native server completes recycling");
            h.assertTrue(m.getItem(2).is(cloth(1).getItem()) && m.getItem(2).getCount()==expected,"Source cloth salvage including extra healthy part");
            for(int i=3;i<11;i++) h.assertTrue(m.getItem(i).isEmpty(),"No metal or additional outputs"); h.assertValueEqual(m.energy().getAmountAsInt(),0,"Exactly 500 FE"); });
    }

    private static void cycle(GameTestHelper h) {
        var p=player(h); var worn=armor(ArmorSlot.HEAD,824,0); p.setItemSlot(EquipmentSlot.HEAD,worn); var repair=bench(h,p);
        repair.setItem(0,cloth(2)); h.assertTrue(p.containerMenu.clickMenuButton(p,1),"Repair with two cloth");
        h.setBlock(POS,CamoBenchContent.BLOCK.get()); var camo=h.getBlockEntity(POS,CamoBenchBlockEntity.class); camo.setOwner(p); p.containerMenu=new CamoBenchMenu(93,p.getInventory(),camo);
        h.assertTrue(p.containerMenu.clickMenuButton(p,4),"Reverse equipped camouflage"); var changed=p.getItemBySlot(EquipmentSlot.HEAD); h.assertValueEqual(TGArmorItem.camo(changed),3,"Reverse wraps to black");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY); var grinder=grind(h,changed);
        h.succeedWhen(() -> { h.assertTrue(!grinder.working() && grinder.getItem(0).isEmpty(),"Repaired and recolored piece completes recycling");
            h.assertTrue(grinder.getItem(2).is(cloth(1).getItem()) && grinder.getItem(2).getCount()==3,"Healthy mask salvage after all three benches"); });
    }

    private static void mob(GameTestHelper h) {
        var mob=h.spawnWithNoFreeWill(EntityTypes.HUSK,new net.minecraft.world.phys.Vec3(4,2,4)); double speed=mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        for(var slot:ArmorSlot.values()) mob.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,0,0)); mob.tick();
        near(h,mob.getAttributeValue(Attributes.MOVEMENT_SPEED),speed,"NPC wearer gets no player movement bonus"); near(h,mob.getAttributeValue(Attributes.ARMOR_TOUGHNESS),0,"No toughness added");
        var event=new LivingFallEvent(mob,10,1); NeoForge.EVENT_BUS.post(event); near(h,event.getDistance(),10,"NPC wearer gets no player fall bonus"); mob.discard(); h.succeed();
    }
    private static void mixedBonuses(GameTestHelper h) {
        var p=player(h); suit(p,0);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED,200,1));
        near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.21,"Scout composes with Speed II"); p.removeAllEffects();
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP_BOOST,200,0));
        p.jumpFromGround(); near(h,p.getDeltaMovement().y,.68,"Scout adds after native Jump Boost I"); p.removeAllEffects();
        p.setItemSlot(EquipmentSlot.CHEST,ArmorContent.T1_COMBAT.get(ArmorSlot.CHEST).toStack());
        p.setItemSlot(EquipmentSlot.FEET,ArmorContent.T1_MINER.get(ArmorSlot.FEET).toStack());
        p.getInventory().add(armor(ArmorSlot.CHEST,0,0)); p.setItemSlot(EquipmentSlot.OFFHAND,armor(ArmorSlot.HEAD,0,0)); TGArmorSystem.refresh(p);
        near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.133,"Mixed sets add bonuses only from equipped slots");
        p.setDeltaMovement(0,0,0); p.jumpFromGround(); near(h,p.getDeltaMovement().y,.56,"Scout non-boots compose with Miner boots");
        p.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(824); TGArmorSystem.refresh(p);
        near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.1205,"Only worn mask bonus is removed");
        var mining=new net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed(p,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),10,h.absolutePos(POS));
        NeoForge.EVENT_BUS.post(mining); near(h,mining.getNewSpeed(),10.5,"Only Miner boots add mining speed in a mixed set"); h.succeed();
    }
    private T1ScoutGameTests() {}
}
