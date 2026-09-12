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

final class T1MinerGameTests {
    private static final BlockPos POS=new BlockPos(4,2,4);
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var slot:ArmorSlot.values()) {
            String name=slot.name().toLowerCase(Locale.ROOT);
            r.register("miner_crafting_"+name,() -> h -> crafting(h,slot));
            r.register("miner_camo_save_and_menu_"+name,() -> h -> camo(h,slot));
            r.register("miner_repair_"+name,() -> h -> repair(h,slot));
            for(int damage:new int[]{0,1,824}) r.register("grinder_miner_"+name+"_"+damage,() -> h -> grinder(h,slot,damage));
        }
        r.register("miner_equip_and_properties",() -> T1MinerGameTests::equip);
        r.register("miner_tooltip_per_part_bonuses",() -> T1MinerGameTests::tooltip);
        r.register("miner_speed_sprint_jump_and_fall",() -> T1MinerGameTests::bonuses);
        r.register("miner_native_fall_damage",() -> T1MinerGameTests::fall);
        r.register("miner_physical_damage",() -> h -> damage(h,"physical",10,4.8f));
        r.register("miner_fire_damage",() -> h -> damage(h,"fire",10,6.1f));
        r.register("miner_acid_damage",() -> h -> damage(h,"acid",10,6.1f));
        r.register("miner_bullet_penetration",() -> h -> damage(h,"bullet",9,5.04f));
        r.register("miner_worn_defense_and_mixed_wear",() -> T1MinerGameTests::wear);
        r.register("miner_anvil_pair_and_books",() -> T1MinerGameTests::anvil);
        r.register("miner_repair_shortage_and_wrong_material",() -> T1MinerGameTests::shortage);
        r.register("grinder_miner_three_workbench_cycle",() -> T1MinerGameTests::cycle);
        r.register("miner_npc_has_no_player_bonuses",() -> T1MinerGameTests::mob);
        MiningArmorGameTests.register(r);
    }
    private static ItemStack armor(ArmorSlot slot,int damage,int camo) {
        var stack=ArmorContent.T1_MINER.get(slot).toStack(); stack.setDamageValue(damage); TGArmorItem.setCamo(stack,camo); return stack;
    }
    private static ItemStack cloth(int n) { return TGContent.MATERIALS.get("heavycloth").toStack(n); }
    private static Player player(GameTestHelper h) { var p=ArmorGameTests.player(h); p.getInventory().clearContent(); return p; }
    private static void suit(Player p,int wear) { for(var slot:ArmorSlot.values()) p.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,wear,0)); p.tick(); }
    private static void near(GameTestHelper h,double actual,double expected,String message) { ArmorGameTests.near(h,actual,expected,message); }
    private static ItemStack craft(GameTestHelper h,int width,int height,List<ItemStack> items) {
        var input=CraftingInput.of(width,height,items); return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input);
    }
    private static void crafting(GameTestHelper h,ArmorSlot slot) {
        String[] patterns={"iyic c   ","c ccccici","icic cc c","c ci i   "}; var items=new ArrayList<ItemStack>();
        for(char c:patterns[slot.ordinal()].toCharArray()) items.add(switch(c) { case 'i' -> new ItemStack(slot==ArmorSlot.FEET?Items.IRON_NUGGET:Items.IRON_INGOT); case 'y' -> new ItemStack(Items.DYE.pick(DyeColor.YELLOW)); case 'c' -> cloth(1); default -> ItemStack.EMPTY; });
        var output=craft(h,3,3,items); h.assertTrue(output.is(ArmorContent.T1_MINER.get(slot).get()) && output.getCount()==1,"Original shaped recipe uses cloth, ingots/nuggets and yellow dye");
        h.assertValueEqual(TGArmorItem.camo(output),0,"Crafted armor uses source default appearance");
        if(slot==ArmorSlot.HEAD) { items.set(1,new ItemStack(Items.DYE.pick(DyeColor.RED))); h.assertTrue(h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,CraftingInput.of(3,3,items),h.getLevel()).isEmpty(),"Wrong color cannot replace yellow dye"); }
        h.succeed();
    }
    private static void equip(GameTestHelper h) {
        var p=player(h);
        for(var slot:ArmorSlot.values()) {
            var stack=armor(slot,0,0); h.assertTrue(stack.getMaxDamage()==825 && !stack.isEnchantable(),"825 durability and no enchanting-table value");
            p.setItemInHand(InteractionHand.MAIN_HAND,stack); stack.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
            h.assertTrue(p.getItemBySlot(EquipmentSlot.valueOf(slot.name())).is(ArmorContent.T1_MINER.get(slot).get()),"Normal use equips armor");
        }
        p.tick(); near(h,p.getArmorValue(),13,"Per-part rounded HUD 3+4+3+3"); near(h,p.getAttributeValue(Attributes.ARMOR_TOUGHNESS),0,"No native toughness");
        near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),0,"No inherited Hazmat radiation bonus"); near(h,p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,"No invented knockback bonus"); h.succeed();
    }
    private static void camo(GameTestHelper h,ArmorSlot slot) {
        var p=player(h); var stack=armor(slot,700,0); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Miner test"));
        p.setShiftKeyDown(true); p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        for(int i=1;i<=4;i++) {
            stack.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND); h.assertValueEqual(TGArmorItem.camo(stack),i%4,"Sneak-use cycles and wraps four source skins");
            h.assertValueEqual(stack.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier(),TGContent.id(Armors.T1_MINER.getFirst().camos().get(i%4)),"Equipment layer follows current camo");
        }
        p.setShiftKeyDown(false); h.setBlock(POS,CamoBenchContent.BLOCK.get()); var bench=h.getBlockEntity(POS,CamoBenchBlockEntity.class); bench.setOwner(p);
        var menu=new CamoBenchMenu(91,p.getInventory(),bench); p.containerMenu=menu; p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); bench.setItem(0,stack);
        h.assertTrue(menu.clickMenuButton(p,2),"Reverse input recoloring works"); var changed=bench.getItem(0); h.assertValueEqual(TGArmorItem.camo(changed),3,"Input reverse wraps to index 3");
        bench.setItem(0,ItemStack.EMPTY); p.setItemSlot(EquipmentSlot.valueOf(slot.name()),changed);
        h.assertTrue(menu.clickMenuButton(p,3+2*slot.ordinal()),"Equipped armor can be recolored"); changed=p.getItemBySlot(EquipmentSlot.valueOf(slot.name()));
        h.assertValueEqual(TGArmorItem.camo(changed),0,"Equipped forward wraps to zero");
        h.assertValueEqual(((TranslatableContents)CamoCycling.variantName(changed).getContents()).getKey(),"tooltip.techguns.armor.t1_miner."+(slot==ArmorSlot.HEAD?"helmet.":"")+"camo.0","Helmet has distinct color naming");
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
            var mining=lines.stream().filter(t -> t.getKey().equals("tooltip.techguns.armor.mining")).findFirst().orElseThrow();
            h.assertValueEqual(((Number)mining.getArgs()[0]).intValue(),5,"Per-part mining percentage");
            var speed=lines.stream().filter(t -> t.getKey().equals("tooltip.techguns.armor.speed")).findFirst().orElseThrow();
            h.assertValueEqual(((Number)speed.getArgs()[0]).intValue(),8,"Per-part movement percentage");
            h.assertTrue(lines.stream().noneMatch(t -> t.getKey().equals("tooltip.techguns.armor.knockback") || t.getKey().equals("tooltip.techguns.armor.radiation_resistance")),"No invented combat or radiation bonuses");
            h.assertTrue(tooltips(armor(slot,824,0)).stream().noneMatch(t -> t.getKey().equals("tooltip.techguns.armor.mining") || t.getKey().equals("tooltip.techguns.armor.speed")),"Worn tooltips do not advertise inactive bonuses");
        }
        h.succeed();
    }
    private static void bonuses(GameTestHelper h) {
        var p=player(h); suit(p,0); for(int i=0;i<5;i++) TGArmorSystem.refresh(p);
        near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.132,"Four eight-percent bonuses do not accumulate on refresh");
        p.setSprinting(true); TGArmorSystem.refresh(p); near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.2132,"Doubled gear speed composes with vanilla sprint");
        p.setDeltaMovement(0,0,0); NeoForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(p)); near(h,p.getDeltaMovement().y,.1,"Boot jump bonus");
        for(double distance:new double[]{.25,1,3,10}) { var event=new LivingFallEvent(p,distance,1); NeoForge.EVENT_BUS.post(event); near(h,event.getDistance(),Math.max(0,distance-1)*.8,"Source fall distance offset then multiplier"); }
        for(var slot:TGArmorSystem.SLOTS) p.getItemBySlot(slot).setDamageValue(824); TGArmorSystem.refresh(p);
        near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.13,"Only vanilla sprint remains at max wear");
        p.setDeltaMovement(0,0,0); NeoForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(p)); near(h,p.getDeltaMovement().y,0,"Worn boots lose jump bonus");
        var event=new LivingFallEvent(p,10,1); NeoForge.EVENT_BUS.post(event); near(h,event.getDistance(),10,"Worn boots lose fall bonus"); h.succeed();
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
        h.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).isEmpty(),"Mixing ordinary armor enables second wear stage and can break worn miner armor"); h.succeed();
    }
    private static AnvilMenu anvilMenu(GameTestHelper h,ItemStack stack,ItemStack material) {
        var menu=new AnvilMenu(1,player(h).getInventory()); menu.getSlot(0).set(stack); menu.getSlot(1).set(material);
        if(stack.has(DataComponents.CUSTOM_NAME)) menu.setItemName(stack.getHoverName().getString()); menu.createResult(); return menu;
    }
    private static void anvil(GameTestHelper h) {
        for(var slot:ArmorSlot.values()) {
            var stack=armor(slot,824,3); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Keep camo"));
            var result=anvilMenu(h,stack,new ItemStack(Items.IRON_INGOT)).getSlot(2).getItem();
            h.assertTrue(!result.isEmpty() && result.getDamageValue()==618,"Iron anvil repair restores floor(825/4)");
            h.assertTrue(TGArmorItem.camo(result)==3 && result.getHoverName().getString().equals("Keep camo"),"Anvil retains camo and name");
            h.assertTrue(anvilMenu(h,stack,new ItemStack(Items.IRON_NUGGET)).getSlot(2).getItem().isEmpty(),"Boot crafting nuggets are not anvil repair material");
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
        var p=player(h); var bench=bench(h,p); var stack=armor(slot,824,3); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Miner repair")); p.setItemSlot(EquipmentSlot.valueOf(slot.name()),stack);
        int n=slot==ArmorSlot.CHEST?2:1; bench.setItem(0,new ItemStack(Items.IRON_INGOT,n)); bench.setItem(1,cloth(n));
        h.assertTrue(p.containerMenu.clickMenuButton(p,slot.ordinal()+1),"Correct iron/cloth repairs equipped piece");
        h.assertTrue(stack.getDamageValue()==0 && TGArmorItem.camo(stack)==3 && stack.getHoverName().getString().equals("Miner repair") && bench.isEmpty(),"Full repair retains components and consumes exact materials"); h.succeed();
    }
    private static void shortage(GameTestHelper h) {
        var p=player(h); var bench=bench(h,p); var stack=armor(ArmorSlot.CHEST,824,2); bench.setItem(9,stack); bench.setItem(0,new ItemStack(Items.IRON_NUGGET,64)); bench.setItem(1,cloth(2));
        h.assertTrue(!p.containerMenu.clickMenuButton(p,6),"Nuggets cannot replace repair ingots");
        bench.setItem(0,new ItemStack(Items.IRON_INGOT,2)); bench.setItem(1,cloth(1)); h.assertTrue(!p.containerMenu.clickMenuButton(p,6),"Missing cloth cancels the transaction");
        h.assertTrue(stack.getDamageValue()==824 && bench.getItem(0).getCount()==2 && bench.getItem(1).getCount()==1,"No partial repair or extraction"); h.succeed();
    }
    private static GrinderBlockEntity grind(GameTestHelper h,ItemStack stack) {
        h.setBlock(POS,GrinderContent.BLOCK.get()); var m=h.getBlockEntity(POS,GrinderBlockEntity.class); m.setItem(0,stack);
        try(var tx=Transaction.openRoot()) { m.energy().insert(500,tx); tx.commit(); } return m;
    }
    private static void grinder(GameTestHelper h,ArmorSlot slot,int damage) {
        var m=grind(h,armor(slot,damage,3)); int[][] healthy={{2,1},{3,2},{1,2},{2,1}}, used={{1,1},{2,2},{1,1},{1,1}};
        int[] expected=damage==824?new int[]{1,0}:damage==0?healthy[slot.ordinal()]:used[slot.ordinal()];
        h.succeedWhen(() -> { h.assertTrue(!m.working() && m.getItem(0).isEmpty(),"Native server finishes recycling");
            h.assertTrue(m.getItem(2).is(Items.IRON_INGOT) && m.getItem(2).getCount()==expected[0],"Source returns ingots, including for boots");
            h.assertTrue(expected[1]==0?m.getItem(3).isEmpty():m.getItem(3).is(cloth(1).getItem()) && m.getItem(3).getCount()==expected[1],"Source cloth and healthy rounding");
            for(int i=4;i<11;i++) h.assertTrue(m.getItem(i).isEmpty(),"No extra output"); h.assertValueEqual(m.energy().getAmountAsInt(),0,"Exactly 500 FE"); });
    }
    private static void cycle(GameTestHelper h) {
        var p=player(h); var worn=armor(ArmorSlot.HEAD,824,0); p.setItemSlot(EquipmentSlot.HEAD,worn); var repair=bench(h,p);
        repair.setItem(0,new ItemStack(Items.IRON_INGOT)); repair.setItem(1,cloth(1)); h.assertTrue(p.containerMenu.clickMenuButton(p,1),"Repair first");
        h.setBlock(POS,CamoBenchContent.BLOCK.get()); var camo=h.getBlockEntity(POS,CamoBenchBlockEntity.class); camo.setOwner(p); p.containerMenu=new CamoBenchMenu(93,p.getInventory(),camo);
        h.assertTrue(p.containerMenu.clickMenuButton(p,4),"Reverse equipped camo"); var changed=p.getItemBySlot(EquipmentSlot.HEAD); h.assertValueEqual(TGArmorItem.camo(changed),3,"Camo wraps to orange helmet");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY); var grinder=grind(h,changed);
        h.succeedWhen(() -> { h.assertTrue(!grinder.working() && grinder.getItem(0).isEmpty(),"Repaired/recolored item completes recycling");
            h.assertTrue(grinder.getItem(2).is(Items.IRON_INGOT) && grinder.getItem(2).getCount()==2 && grinder.getItem(3).is(cloth(1).getItem()) && grinder.getItem(3).getCount()==1,"Source healthy helmet salvage after all three benches"); });
    }
    private static void mob(GameTestHelper h) {
        var mob=h.spawnWithNoFreeWill(EntityTypes.HUSK,new net.minecraft.world.phys.Vec3(4,2,4)); double speed=mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        for(var slot:ArmorSlot.values()) mob.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,0,0)); mob.tick();
        near(h,mob.getAttributeValue(Attributes.MOVEMENT_SPEED),speed,"NPC wearer gets no player movement bonus"); near(h,mob.getAttributeValue(Attributes.ARMOR_TOUGHNESS),0,"No toughness added");
        var event=new LivingFallEvent(mob,10,1); NeoForge.EVENT_BUS.post(event); near(h,event.getDistance(),10,"NPC wearer gets no player fall bonus"); mob.discard(); h.succeed();
    }
    private T1MinerGameTests() {}
}
