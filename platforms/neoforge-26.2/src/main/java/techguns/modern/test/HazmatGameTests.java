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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.storage.*;
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

final class HazmatGameTests {
    private static final BlockPos POS=new BlockPos(4,2,4);
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var slot:ArmorSlot.values()) {
            for(int damage:new int[]{0,1,1099}) r.register("grinder_hazmat_"+slot.name().toLowerCase(Locale.ROOT)+"_"+damage, () -> h -> grinder(h,slot,damage));
            r.register("hazmat_repair_"+slot.name().toLowerCase(Locale.ROOT), () -> h -> repair(h,slot));
            r.register("hazmat_camo_"+slot.name().toLowerCase(Locale.ROOT), () -> h -> camo(h,slot));
        }
        r.register("hazmat_equip_properties", () -> HazmatGameTests::equip);
        r.register("hazmat_crafting_and_fiber", () -> HazmatGameTests::crafting);
        r.register("hazmat_physical", () -> h -> damage(h,"physical",10,6));
        r.register("hazmat_fire", () -> h -> damage(h,"fire",14,5.04f));
        r.register("hazmat_acid", () -> h -> damage(h,"acid",10,2));
        r.register("hazmat_explosion", () -> h -> damage(h,"explosion",20,12));
        r.register("hazmat_radiation_damage", () -> h -> damage(h,"radiation",10,2));
        r.register("hazmat_projectile_penetration", () -> h -> damage(h,"bullet",9,6.12f));
        r.register("hazmat_exposure_partial_sets", () -> h -> exposure(h,0));
        r.register("hazmat_exposure_worn_sets", () -> h -> exposure(h,1099));
        r.register("hazmat_exposure_with_pills", () -> HazmatGameTests::pills);
        r.register("hazmat_radioactive_inventory", () -> HazmatGameTests::inventory);
        r.register("hazmat_existing_poisoning", () -> HazmatGameTests::poisoning);
        r.register("hazmat_worn_retains_protection", () -> HazmatGameTests::wear);
        r.register("hazmat_mixed_second_wear_can_break", () -> HazmatGameTests::mixedWear);
        r.register("hazmat_mixed_radiation_second_stage", () -> HazmatGameTests::mixedRadiation);
        r.register("hazmat_cancelled_radiation_no_wear", () -> HazmatGameTests::cancelled);
        r.register("hazmat_cancelled_armor_wear", () -> HazmatGameTests::cancelWear);
        r.register("hazmat_player_save_and_attributes", () -> HazmatGameTests::savePlayer);
        r.register("hazmat_slot_modifiers_do_not_duplicate", () -> HazmatGameTests::modifiers);
        r.register("hazmat_fall_distance", () -> HazmatGameTests::fallDistance);
        r.register("hazmat_real_fall_damage", () -> HazmatGameTests::fallDamage);
        r.register("hazmat_mob_radiation_attribute", () -> HazmatGameTests::mob);
        r.register("hazmat_anvil_and_pair_repair", () -> HazmatGameTests::anvil);
        r.register("hazmat_repair_shortage", () -> HazmatGameTests::shortage);
        r.register("hazmat_workbench_cycle", () -> HazmatGameTests::cycle);
    }
    private static ItemStack armor(ArmorSlot slot,int damage,int camo) {
        var stack=ArmorContent.HAZMAT.get(slot).toStack(); stack.setDamageValue(damage); TGArmorItem.setCamo(stack,camo); return stack;
    }
    private static ItemStack material(String id,int count) { return TGContent.MATERIALS.get(id).toStack(count); }
    private static Player player(GameTestHelper h) { var p=ArmorGameTests.player(h); p.getInventory().clearContent(); return p; }
    private static void suit(Player player,int damage) { for(var slot:ArmorSlot.values()) player.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,damage,0)); player.tick(); }
    private static void near(GameTestHelper h,double actual,double expected,String message) { ArmorGameTests.near(h,actual,expected,message); }
    private static DamageSource source(GameTestHelper h,String kind) {
        var level=h.getLevel();
        if(kind.equals("physical")) return level.damageSources().playerAttack(WeaponGameTests.player(h));
        if(kind.equals("bullet")) { var bullet=new Bullet(TGContent.BULLET.get(),level); bullet.configure(Weapons.definition("ak47")); bullet.setOwner(WeaponGameTests.player(h)); return ShotDamage.PLAYER.source(level,net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet); }
        var key=switch(kind) {
            case "fire" -> NetherBlasterProjectile.DAMAGE_TYPE;
            case "acid" -> techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE;
            case "radiation" -> RadiationSystem.DAMAGE;
            case "poisoning" -> RadiationSystem.POISONING;
            case "explosion" -> DamageTypes.EXPLOSION;
            default -> throw new IllegalArgumentException(kind);
        };
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key));
    }
    private static void enabled(Runnable action) { boolean previous=RadiationSystem.DISABLED.get(); try { RadiationSystem.DISABLED.set(false); action.run(); } finally { RadiationSystem.DISABLED.set(previous); } }
    private static void equip(GameTestHelper h) {
        var p=player(h);
        for(var slot:ArmorSlot.values()) {
            var stack=armor(slot,0,0); h.assertValueEqual(stack.getMaxDamage(),1100,"Original durability"); h.assertTrue(!stack.isEnchantable(),"Zero enchanting-table value");
            p.setItemInHand(InteractionHand.MAIN_HAND,stack); stack.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
            h.assertTrue(p.getItemBySlot(EquipmentSlot.valueOf(slot.name())).is(ArmorContent.HAZMAT.get(slot).get()),"Normal use equips the matching part");
        }
        p.tick(); near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),4,"One exposure resistance per piece"); near(h,p.getArmorValue(),11,"Original per-piece rounded armor display");
        near(h,p.getAttributeValue(Attributes.ARMOR_TOUGHNESS),0,"Hazmat has no toughness"); near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.1,"No combat movement bonus"); near(h,p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,"No combat knockback bonus"); h.succeed();
    }
    private static ItemStack craft(GameTestHelper h,int width,int height,List<ItemStack> grid) {
        var input=CraftingInput.of(width,height,grid); return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input);
    }
    private static void crafting(GameTestHelper h) {
        var fiber=craft(h,3,1,List.of(material("heavycloth",1),material("rubberbar",1),material("platelead",1)));
        h.assertTrue(fiber.is(TGContent.MATERIALS.get("protectivefiber").get()) && fiber.getCount()==3,"Three fibers from original cloth/rubber/lead recipe");
        String[] patterns={"ffff f   ","f fffffff","ffff ff f","f ff f   "}; int i=0;
        for(var slot:ArmorSlot.values()) {
            var grid=new ArrayList<ItemStack>(); for(char c:patterns[i++].toCharArray()) grid.add(c=='f'?fiber.copyWithCount(1):ItemStack.EMPTY);
            var result=craft(h,3,3,grid); h.assertTrue(result.is(ArmorContent.HAZMAT.get(slot).get()) && result.getCount()==1,"Original fiber-only armor recipe");
        }
        h.succeed();
    }
    private static void damage(GameTestHelper h,String kind,float amount,float expected) {
        var p=player(h); suit(p,0); p.hurtServer(h.getLevel(),source(h,kind),amount); near(h,1000-p.getHealth(),expected,"Hazmat typed protection for "+kind); h.succeed();
    }
    private static void exposure(GameTestHelper h,int wear) {
        enabled(() -> {
            for(int pieces=0;pieces<=4;pieces++) {
                var p=player(h); for(int i=0;i<pieces;i++) { var slot=ArmorSlot.values()[i]; p.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,wear,0)); } p.tick();
                near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),pieces,"Resistance counts worn pieces even at one remaining durability");
                RadiationSystem.EXPOSURE.get().applyEffectTick(h.getLevel(),p,3);
                h.assertValueEqual(RadiationSystem.dose(p),4-pieces,"Four exposure units minus gear resistance");
                near(h,p.getHealth(),1000,"Exposure changes dose rather than immediate health");
            }
        }); h.succeed();
    }
    private static void pills(GameTestHelper h) {
        enabled(() -> { var p=player(h); suit(p,1099); p.addEffect(new MobEffectInstance(RadiationSystem.PROTECTION,3600,1));
            near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),6,"Two medicine resistance plus four suit pieces");
            RadiationSystem.EXPOSURE.get().applyEffectTick(h.getLevel(),p,5); h.assertValueEqual(RadiationSystem.dose(p),0,"Combined resistance blocks six units");
            RadiationSystem.EXPOSURE.get().applyEffectTick(h.getLevel(),p,6); h.assertValueEqual(RadiationSystem.dose(p),1,"Stronger exposure still adds residual dose");
        }); h.succeed();
    }
    private static void inventory(GameTestHelper h) {
        var p=player(h); suit(p,1099); p.getInventory().setItem(35,material("antigravcore",1));
        h.onEachTick(() -> { if(h.getLevel().getGameTime()%60!=0) return; enabled(() -> {
            RadiationSystem.tick(p); var effect=p.getEffect(RadiationSystem.EXPOSURE); h.assertTrue(effect!=null && effect.getAmplifier()==3,"Inventory still supplies actual four-unit exposure");
            effect.getEffect().value().applyEffectTick(h.getLevel(),p,effect.getAmplifier()); h.assertValueEqual(RadiationSystem.dose(p),0,"Worn full suit blocks inventory exposure");
        }); h.succeed(); });
    }
    private static void poisoning(GameTestHelper h) {
        var p=player(h); suit(p,0); enabled(() -> RadiationSystem.add(p,1000));
        p.hurtServer(h.getLevel(),source(h,"poisoning"),2); near(h,p.getHealth(),998,"Suit does not block already accumulated poisoning damage");
        h.assertValueEqual(RadiationSystem.dose(p),1000,"Suit does not cure stored dose");
        for(var slot:TGArmorSystem.SLOTS) h.assertValueEqual(p.getItemBySlot(slot).getDamageValue(),0,"Poisoning does not wear armor"); h.succeed();
    }
    private static void wear(GameTestHelper h) {
        var p=player(h); suit(p,1098); p.hurtServer(h.getLevel(),source(h,"physical"),10);
        for(var slot:TGArmorSystem.SLOTS) h.assertValueEqual(p.getItemBySlot(slot).getDamageValue(),1099,"Special wear stops one point before break");
        p.invulnerableTime=0; p.hurtServer(h.getLevel(),source(h,"physical"),10); near(h,p.getHealth(),988,"Both attacks retain original typed protection");
        for(var slot:TGArmorSystem.SLOTS) h.assertTrue(!p.getItemBySlot(slot).isEmpty() && p.getItemBySlot(slot).getDamageValue()==1099,"Zero-toughness suit has no ordinary second wear pass");
        near(h,p.getArmorValue(),0,"Worn HUD disabled"); near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),4,"Radiation attribute remains"); h.succeed();
    }
    private static void mixedWear(GameTestHelper h) {
        var p=player(h); p.setItemSlot(EquipmentSlot.HEAD,armor(ArmorSlot.HEAD,1098,0)); p.setItemSlot(EquipmentSlot.CHEST,ArmorGameTests.armor(ArmorSlot.CHEST,0)); p.tick();
        p.hurtServer(h.getLevel(),source(h,"physical"),10); near(h,1000-p.getHealth(),6.84,"Mixed source absorption adds each material's contribution");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"T2 toughness enables ordinary wear that can break worn Hazmat"); p.tick(); near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),0,"Broken piece removes resistance"); h.succeed();
    }
    private static void cancelled(GameTestHelper h) {
        var p=player(h); suit(p,0); Consumer<LivingIncomingDamageEvent> cancel=e -> { if(e.getEntity()==p) e.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(cancel);
        try { p.hurtServer(h.getLevel(),source(h,"radiation"),10); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        near(h,p.getHealth(),1000,"Cancelled typed radiation does no damage"); for(var slot:TGArmorSystem.SLOTS) h.assertValueEqual(p.getItemBySlot(slot).getDamageValue(),0,"No eager wear before damage acceptance"); h.succeed();
    }
    private static void mixedRadiation(GameTestHelper h) {
        var p=player(h); p.setItemSlot(EquipmentSlot.HEAD,armor(ArmorSlot.HEAD,0,0)); p.setItemSlot(EquipmentSlot.CHEST,ArmorGameTests.armor(ArmorSlot.CHEST,0)); p.tick();
        p.hurtServer(h.getLevel(),source(h,"radiation"),10); near(h,p.getHealth(),992,"Only Hazmat supplies typed radiation absorption");
        h.assertValueEqual(p.getItemBySlot(EquipmentSlot.HEAD).getDamageValue(),4,"Two special plus two ordinary wear"); h.assertValueEqual(p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue(),2,"Mixed T2 toughness retains Forge ordinary wear stage"); h.succeed();
    }
    private static void cancelWear(GameTestHelper h) {
        var p=player(h); suit(p,0); Consumer<ArmorHurtEvent> cancel=e -> { if(e.getEntity()==p) e.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(cancel);
        try { p.hurtServer(h.getLevel(),source(h,"radiation"),10); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        near(h,p.getHealth(),998,"Cancelling wear does not cancel armor protection"); for(var slot:TGArmorSystem.SLOTS) h.assertValueEqual(p.getItemBySlot(slot).getDamageValue(),0,"Native ArmorHurtEvent cancellation"); h.succeed();
    }
    private static void savePlayer(GameTestHelper h) {
        var p=player(h); suit(p,1099); TGArmorItem.setCamo(p.getItemBySlot(EquipmentSlot.HEAD),3); enabled(() -> RadiationSystem.add(p,400));
        var problems=new ProblemReporter.Collector(); var out=TagValueOutput.createWithContext(problems,h.getLevel().registryAccess()); p.saveWithoutId(out);
        var restored=player(h); restored.load(TagValueInput.create(problems,h.getLevel().registryAccess(),out.buildResult())); restored.tick();
        h.assertTrue(problems.isEmpty(),"Native player save has no codec errors"); near(h,restored.getAttributeValue(RadiationSystem.RESISTANCE),4,"Reload neither loses nor doubles equipment resistance");
        h.assertValueEqual(RadiationSystem.dose(restored),400,"Dose survives player save"); h.assertValueEqual(TGArmorItem.camo(restored.getItemBySlot(EquipmentSlot.HEAD)),3,"Blue skin persists");
        enabled(() -> RadiationSystem.EXPOSURE.get().applyEffectTick(h.getLevel(),restored,4)); h.assertValueEqual(RadiationSystem.dose(restored),401,"Saved worn equipment still resists exposure"); h.succeed();
    }
    private static void modifiers(GameTestHelper h) {
        var p=player(h); var helmet=armor(ArmorSlot.HEAD,1099,0); p.setItemSlot(EquipmentSlot.HEAD,helmet);
        for(int i=0;i<8;i++) p.tick(); near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),1,"Repeated updates do not duplicate modifiers");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY); p.setItemInHand(InteractionHand.MAIN_HAND,helmet); p.tick(); near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),0,"Held armor grants no worn attribute");
        p.setItemSlot(EquipmentSlot.CHEST,helmet); p.tick(); near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),0,"Wrong equipment slot does not grant resistance"); h.succeed();
    }
    private static void fallDistance(GameTestHelper h) {
        var p=player(h); p.setItemSlot(EquipmentSlot.FEET,armor(ArmorSlot.FEET,0,0));
        for(double distance:new double[]{.25,.5,3,10}) { var event=new LivingFallEvent(p,distance,1); NeoForge.EVENT_BUS.post(event); near(h,event.getDistance(),Math.max(0,distance-.5)*.9,"Source modifies distance before vanilla free fall"); }
        p.getItemBySlot(EquipmentSlot.FEET).setDamageValue(1099); var event=new LivingFallEvent(p,10,1); NeoForge.EVENT_BUS.post(event); near(h,event.getDistance(),10,"Worn boots lose fall bonus"); h.succeed();
    }
    private static void fallDamage(GameTestHelper h) {
        var p=player(h); p.setItemSlot(EquipmentSlot.FEET,armor(ArmorSlot.FEET,0,0)); p.tick();
        p.causeFallDamage(10,1,h.getLevel().damageSources().fall()); near(h,p.getHealth(),995,"26.2 fall calculation uses floor(8.55 - 3 + epsilon)");
        h.assertValueEqual(p.getItemBySlot(EquipmentSlot.FEET).getDamageValue(),0,"Fall does not consume armor durability");
        p.invulnerableTime=0; p.getItemBySlot(EquipmentSlot.FEET).setDamageValue(1099); p.causeFallDamage(10,1,h.getLevel().damageSources().fall()); near(h,p.getHealth(),988,"Worn boots receive full seven damage"); h.succeed();
    }
    private static void mob(GameTestHelper h) {
        var mob=h.spawn(EntityTypes.HUSK,new net.minecraft.world.phys.Vec3(4,2,4)); mob.setNoAi(true);
        for(var slot:ArmorSlot.values()) mob.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,1099,0)); mob.tick();
        near(h,mob.getAttributeValue(RadiationSystem.RESISTANCE),4,"Original equipment attribute also applies to mobs"); float health=mob.getHealth();
        enabled(() -> RadiationSystem.EXPOSURE.get().applyEffectTick(h.getLevel(),mob,3)); near(h,mob.getHealth(),health,"Mob resistance blocks exposure before health damage");
        var event=new LivingFallEvent(mob,10,1); NeoForge.EVENT_BUS.post(event); near(h,event.getDistance(),10,"Player-only movement bonuses do not leak onto NPCs"); h.succeed();
    }
    private static void repair(GameTestHelper h,ArmorSlot slot) {
        h.setBlock(POS,RepairBenchContent.BLOCK.get()); var bench=h.getBlockEntity(POS,RepairBenchBlockEntity.class); var p=player(h); bench.setOwner(p);
        var menu=new RepairBenchMenu(61,p.getInventory(),bench); p.containerMenu=menu; var stack=armor(slot,1099,3); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Salvage suit")); p.setItemSlot(EquipmentSlot.valueOf(slot.name()),stack);
        int cost=new int[]{2,4,3,2}[slot.ordinal()]; bench.setItem(0,material("protectivefiber",cost));
        h.assertTrue(menu.clickMenuButton(p,slot.ordinal()+1),"Repair Bench accepts equipped Hazmat"); h.assertValueEqual(stack.getDamageValue(),0,"Full durability restored");
        h.assertTrue(bench.isEmpty(),"Only exact protective fiber cost consumed"); h.assertTrue(TGArmorItem.camo(stack)==3 && stack.getHoverName().getString().equals("Salvage suit"),"Name and blue camouflage preserved"); h.succeed();
    }
    private static void camo(GameTestHelper h,ArmorSlot slot) {
        var p=player(h); var stack=armor(slot,500,0); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Test suit")); p.setItemInHand(InteractionHand.MAIN_HAND,stack); p.setShiftKeyDown(true);
        for(int i=1;i<=4;i++) { stack.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND); h.assertValueEqual(TGArmorItem.camo(stack),i%4,"Four Hazmat variants wrap independently from six T2 variants"); }
        var changed=CamoCycling.change(stack,true).orElseThrow(); h.assertValueEqual(TGArmorItem.camo(changed),3,"Reverse from yellow selects blue");
        h.assertValueEqual(changed.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier(),TGContent.id("hazmatsuit_blue"),"Correct Hazmat equipment asset");
        var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE); var restored=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,changed).getOrThrow()).getOrThrow();
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess()); try { ItemStack.STREAM_CODEC.encode(buffer,restored); var decoded=ItemStack.STREAM_CODEC.decode(buffer); h.assertTrue(ItemStack.matches(decoded,changed),"Camo, damage and name survive item save and network"); } finally { buffer.release(); }
        h.succeed();
    }
    private static GrinderBlockEntity grind(GameTestHelper h,ItemStack stack) {
        h.setBlock(POS,GrinderContent.BLOCK.get()); var m=h.getBlockEntity(POS,GrinderBlockEntity.class); m.setItem(0,stack);
        try(var tx=Transaction.openRoot()) { m.energy().insert(500,tx); tx.commit(); } return m;
    }
    private static void grinder(GameTestHelper h,ArmorSlot slot,int wear) {
        var m=grind(h,armor(slot,wear,3)); int count=wear==1099?1:new int[]{2,4,3,2}[slot.ordinal()]+(wear==0?1:0);
        h.succeedWhen(() -> { h.assertTrue(!m.working() && m.getItem(0).isEmpty(),"Actual server finishes Hazmat recycling"); h.assertTrue(m.getItem(2).is(TGContent.MATERIALS.get("protectivefiber").get()) && m.getItem(2).getCount()==count,"Original inverse wear returns only fiber");
            for(int i=3;i<11;i++) h.assertTrue(m.getItem(i).isEmpty(),"No invented obsidian steel or heavy cloth"); h.assertValueEqual(m.energy().getAmountAsInt(),0,"500 FE paid"); });
    }
    private static AnvilMenu anvilMenu(GameTestHelper h,ItemStack stack,ItemStack material) { var menu=new AnvilMenu(1,player(h).getInventory()); menu.getSlot(0).set(stack); menu.getSlot(1).set(material); if(stack.has(DataComponents.CUSTOM_NAME)) menu.setItemName(stack.getHoverName().getString()); menu.createResult(); return menu; }
    private static void anvil(GameTestHelper h) {
        for(var slot:ArmorSlot.values()) {
            var input=armor(slot,1099,3); input.set(DataComponents.CUSTOM_NAME,Component.literal("Keep skin")); var result=anvilMenu(h,input,material("protectivefiber",1)).getSlot(2).getItem();
            h.assertTrue(!result.isEmpty() && result.getDamageValue()==824,"One fiber repairs floor(1100/4) durability"); h.assertTrue(TGArmorItem.camo(result)==3 && result.getHoverName().getString().equals("Keep skin"),"Anvil retains skin/name");
            h.assertTrue(anvilMenu(h,input,material("heavycloth",1)).getSlot(2).getItem().isEmpty(),"Repair does not accept T2 cloth");
        }
        var result=craft(h,2,1,List.of(armor(ArmorSlot.HEAD,600,2),armor(ArmorSlot.HEAD,600,3))); h.assertValueEqual(result.getDamageValue(),45,"Native pair repair bonus"); h.assertValueEqual(TGArmorItem.camo(result),0,"Pair-repaired suit uses default yellow");
        var book=new ItemStack(Items.ENCHANTED_BOOK); var mending=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING); var enchant=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY); enchant.set(mending,1); book.set(DataComponents.STORED_ENCHANTMENTS,enchant.toImmutable());
        h.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(mending,anvilMenu(h,armor(ArmorSlot.HEAD,0,0),book).getSlot(2).getItem()),1,"Books apply through native armor tags"); h.succeed();
    }
    private static void shortage(GameTestHelper h) {
        h.setBlock(POS,RepairBenchContent.BLOCK.get()); var bench=h.getBlockEntity(POS,RepairBenchBlockEntity.class); var p=player(h); bench.setOwner(p); var menu=new RepairBenchMenu(62,p.getInventory(),bench); p.containerMenu=menu;
        var input=armor(ArmorSlot.CHEST,1099,2); bench.setItem(9,input); bench.setItem(0,material("protectivefiber",3)); bench.setItem(1,material("heavycloth",64));
        h.assertTrue(!menu.clickMenuButton(p,6),"One missing fiber prevents entire repair"); h.assertValueEqual(input.getDamageValue(),1099,"No partial/free repair"); h.assertValueEqual(bench.getItem(0).getCount(),3,"Attempt rolls back material extraction"); h.assertValueEqual(bench.getItem(1).getCount(),64,"Wrong material not consumed"); h.succeed();
    }
    private static void cycle(GameTestHelper h) {
        var p=player(h); var worn=armor(ArmorSlot.HEAD,1099,0); p.setItemSlot(EquipmentSlot.HEAD,worn);
        h.setBlock(POS,RepairBenchContent.BLOCK.get()); var repair=h.getBlockEntity(POS,RepairBenchBlockEntity.class); repair.setOwner(p); var repairMenu=new RepairBenchMenu(63,p.getInventory(),repair); p.containerMenu=repairMenu; repair.setItem(0,material("protectivefiber",2));
        h.assertTrue(repairMenu.clickMenuButton(p,1),"Repair worn helmet for two fibers");
        h.setBlock(POS,CamoBenchContent.BLOCK.get()); var camo=h.getBlockEntity(POS,CamoBenchBlockEntity.class); camo.setOwner(p); var camoMenu=new CamoBenchMenu(64,p.getInventory(),camo); p.containerMenu=camoMenu;
        h.assertTrue(camoMenu.clickMenuButton(p,4),"Camo Bench reverses equipped helmet to blue"); var processed=p.getItemBySlot(EquipmentSlot.HEAD); h.assertValueEqual(TGArmorItem.camo(processed),3,"Correct set-specific camo"); p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
        var grinder=grind(h,processed); for(int i=0;i<101;i++) GrinderBlockEntity.tick(h.getLevel(),grinder.getBlockPos(),grinder.getBlockState(),grinder);
        h.assertTrue(grinder.getItem(2).is(TGContent.MATERIALS.get("protectivefiber").get()) && grinder.getItem(2).getCount()==3,"Repaired healthy helmet returns original three-fiber salvage"); h.succeed();
    }
}
