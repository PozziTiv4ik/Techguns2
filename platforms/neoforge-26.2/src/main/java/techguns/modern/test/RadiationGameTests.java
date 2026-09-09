package techguns.modern.test;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.modern.TGContent;
import techguns.modern.radiation.RadiationSystem;

final class RadiationGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        registry.register("radiation_dose_resistance_and_clamping",() -> RadiationGameTests::dose);
        registry.register("radiation_save_and_death_persistence",() -> RadiationGameTests::persistence);
        registry.register("radiation_medicine_effects_and_containers",() -> RadiationGameTests::medicine);
        registry.register("radiation_milk_does_not_cure_exposure",() -> RadiationGameTests::milk);
        registry.register("radiation_reaction_radius_and_strength",() -> RadiationGameTests::radius);
        registry.register("radiation_mob_damage_bypasses_armor_and_cooldown",() -> RadiationGameTests::mobDamage);
        registry.register("radiation_poisoning_thresholds",() -> RadiationGameTests::thresholds);
        registry.register("radiation_inventory_uses_highest_strength",() -> RadiationGameTests::inventory);
        registry.register("radiation_original_disabled_default",() -> RadiationGameTests::disabled);
    }
    private static void enabled(Runnable action) { boolean old=RadiationSystem.DISABLED.get(); try { RadiationSystem.DISABLED.set(false); action.run(); } finally { RadiationSystem.DISABLED.set(old); } }
    private static void dose(GameTestHelper h) {
        enabled(() -> {
            var player=WeaponGameTests.player(h);
            RadiationSystem.EXPOSURE.get().applyEffectTick(h.getLevel(),player,4); h.assertValueEqual(RadiationSystem.dose(player),5,"Radiation adds amplifier plus one per second");
            player.addEffect(new MobEffectInstance(RadiationSystem.PROTECTION,3600,2));
            RadiationSystem.EXPOSURE.get().applyEffectTick(h.getLevel(),player,4); h.assertValueEqual(RadiationSystem.dose(player),7,"Pills resist three radiation units per second");
            RadiationSystem.add(player,10000); h.assertValueEqual(RadiationSystem.dose(player),1000,"Radiation caps at original lethal dose");
            RadiationSystem.add(player,-10000); h.assertValueEqual(RadiationSystem.dose(player),0,"Medicine cannot create negative dose");
            var creative=h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE); RadiationSystem.EXPOSURE.get().applyEffectTick(h.getLevel(),creative,24); h.assertValueEqual(RadiationSystem.dose(creative),0,"Creative player does not accumulate exposure");
            h.assertTrue(RadiationSystem.EXPOSURE.get().shouldApplyEffectTickThisTick(60,3) && !RadiationSystem.EXPOSURE.get().shouldApplyEffectTickThisTick(59,3),"Exposure ticks at original 20-tick duration boundaries");
        }); h.succeed();
    }
    private static void persistence(GameTestHelper h) {
        enabled(() -> {
            var player=WeaponGameTests.player(h); RadiationSystem.add(player,725);
            var problems=new ProblemReporter.Collector(); var out=TagValueOutput.createWithContext(problems,h.getLevel().registryAccess()); player.saveWithoutId(out);
            var restored=WeaponGameTests.player(h); restored.load(TagValueInput.create(problems,h.getLevel().registryAccess(),out.buildResult()));
            h.assertTrue(problems.isEmpty(),"Player radiation serializes without errors: "+problems.getReport()); h.assertValueEqual(RadiationSystem.dose(restored),725,"Dose survives full player NBT");
            var respawned=WeaponGameTests.player(h); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(respawned,restored,true));
            h.assertValueEqual(RadiationSystem.dose(respawned),525,"Death removes original 200 units");
            var changedDimension=WeaponGameTests.player(h); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(changedDimension,respawned,false));
            h.assertValueEqual(RadiationSystem.dose(changedDimension),525,"Dimension clone does not remove radiation");
        }); h.succeed();
    }
    private static void medicine(GameTestHelper h) {
        enabled(() -> {
            var player=WeaponGameTests.player(h); RadiationSystem.add(player,700);
            var pills=TGContent.MATERIALS.get("radpills").toStack(2); player.setItemInHand(InteractionHand.MAIN_HAND,pills);
            pills.getItem().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
            h.assertValueEqual(pills.getUseDuration(player),32,"Original pill consumption time");
            pills.getItem().finishUsingItem(pills,h.getLevel(),player); player.stopUsingItem();
            h.assertValueEqual(pills.getCount(),1,"Exactly one pill item consumed"); h.assertValueEqual(player.getInventory().countItem(Items.GLASS_BOTTLE),1,"Pills return one bottle");
            var regen=player.getEffect(RadiationSystem.REGENERATION); var protection=player.getEffect(RadiationSystem.PROTECTION);
            h.assertValueEqual(regen.getDuration(),500,"Original regeneration duration"); h.assertValueEqual(protection.getDuration(),3600,"Original three-minute protection");
            for(int tick=regen.getDuration();tick>0;tick--) if(regen.getEffect().value().shouldApplyEffectTickThisTick(tick,regen.getAmplifier())) regen.getEffect().value().applyEffectTick(h.getLevel(),player,regen.getAmplifier());
            h.assertValueEqual(RadiationSystem.dose(player),650,"Pills remove 50 total dose over time");
            var away=TGContent.MATERIALS.get("radaway").toStack(); away.getItem().finishUsingItem(away,h.getLevel(),player);
            h.assertTrue(away.isEmpty(),"RadAway is consumed"); h.assertValueEqual(player.getInventory().countItem(TGContent.MATERIALS.get("infusionbag").get()),1,"RadAway returns infusion bag");
            regen=player.getEffect(RadiationSystem.REGENERATION); h.assertValueEqual(regen.getDuration(),400,"Original RadAway duration"); h.assertValueEqual(regen.getAmplifier(),14,"Original 15-per-second regeneration");
            for(int tick=regen.getDuration();tick>0;tick--) if(tick%20==0) regen.getEffect().value().applyEffectTick(h.getLevel(),player,regen.getAmplifier());
            h.assertValueEqual(RadiationSystem.dose(player),350,"RadAway removes 300 total dose");
        }); h.succeed();
    }
    private static void milk(GameTestHelper h) {
        enabled(() -> {
            var player=WeaponGameTests.player(h); player.addEffect(new MobEffectInstance(RadiationSystem.EXPOSURE,62,3)); player.addEffect(new MobEffectInstance(MobEffects.POISON,100,0));
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.MILK_BUCKET)); player.startUsingItem(InteractionHand.MAIN_HAND);
            var milk=player.getMainHandItem(); milk.getItem().finishUsingItem(milk,h.getLevel(),player); player.stopUsingItem();
            h.assertTrue(player.hasEffect(RadiationSystem.EXPOSURE),"Milk cannot cure radiation exposure"); h.assertTrue(!player.hasEffect(MobEffects.POISON),"Milk still clears ordinary poison");
            player.removeEffect(RadiationSystem.EXPOSURE); h.assertTrue(!player.hasEffect(RadiationSystem.EXPOSURE),"Explicit effect removal remains available");
        }); h.succeed();
    }
    private static void radius(GameTestHelper h) {
        enabled(() -> {
            BlockPos controller=h.absolutePos(new BlockPos(4,2,4)); Vec3 center=Vec3.atCenterOf(controller);
            var inner=h.spawn(EntityTypes.PIG,new Vec3(4,2,4)); inner.setPos(center.add(1,0,0));
            var outer=h.spawn(EntityTypes.PIG,new Vec3(6,2,4)); outer.setPos(center.add(5,0,0));
            var outside=h.spawn(EntityTypes.PIG,new Vec3(7,2,4)); outside.setPos(center.add(6,0,0));
            RadiationSystem.reactionFailure(h.getLevel(),controller,8);
            h.assertValueEqual(inner.getEffect(RadiationSystem.EXPOSURE).getAmplifier(),3,"Inner radius uses ceil(intensity/2) radiation strength");
            h.assertValueEqual(inner.getEffect(RadiationSystem.EXPOSURE).getDuration(),62,"Source exposure lasts across the next reaction check");
            h.assertValueEqual(outer.getEffect(RadiationSystem.EXPOSURE).getAmplifier(),2,"Outer radius preserves source formula");
            h.assertTrue(!outside.hasEffect(RadiationSystem.EXPOSURE),"Radius boundary is excluded");
        }); h.succeed();
    }
    private static void mobDamage(GameTestHelper h) {
        enabled(() -> {
            var pig=h.spawn(EntityTypes.PIG,new Vec3(4,2,4)); pig.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.NETHERITE_CHESTPLATE));
            float before=pig.getHealth(); RadiationSystem.EXPOSURE.get().applyEffectTick(h.getLevel(),pig,3); RadiationSystem.EXPOSURE.get().applyEffectTick(h.getLevel(),pig,3);
            h.assertValueEqual(pig.getHealth(),before-4,"Radiation bypasses ordinary armor and damage cooldown");
            pig.getAttribute(RadiationSystem.RESISTANCE).setBaseValue(4); RadiationSystem.EXPOSURE.get().applyEffectTick(h.getLevel(),pig,3);
            h.assertValueEqual(pig.getHealth(),before-4,"Radiation resistance blocks exposure before damage");
        }); h.succeed();
    }
    private static void thresholds(GameTestHelper h) {
        h.onEachTick(() -> { if(h.getLevel().getGameTime()%20!=0) return;
            enabled(() -> {
                for(int dose:new int[]{499,500,750,1000}) {
                    var player=WeaponGameTests.player(h); RadiationSystem.add(player,dose); float before=player.getHealth(); RadiationSystem.tick(player);
                    h.assertTrue(player.hasEffect(MobEffects.HUNGER)==(dose>=500),"Minor poisoning begins at 500");
                    h.assertTrue(player.hasEffect(MobEffects.WEAKNESS)==(dose>=750),"Severe poisoning begins at 750");
                    h.assertTrue(player.hasEffect(MobEffects.NAUSEA)==(dose>=1000),"Lethal poisoning begins at 1000");
                    if(dose==1000) h.assertValueEqual(player.getHealth(),before-2,"Lethal poisoning deals two damage per second");
                }
            }); h.succeed();
        });
    }
    private static void inventory(GameTestHelper h) {
        h.onEachTick(() -> { if(h.getLevel().getGameTime()%60!=0) return;
            enabled(() -> {
                var player=WeaponGameTests.player(h); player.getInventory().setItem(0,TGContent.MATERIALS.get("yellowcake").toStack(64));
                player.getInventory().setItem(35,TGContent.MATERIALS.get("antigravcore").toStack()); RadiationSystem.tick(player);
                h.assertValueEqual(player.getEffect(RadiationSystem.EXPOSURE).getAmplifier(),3,"Inventory uses maximum source strength, not sum or stack count");
                h.assertValueEqual(player.getEffect(RadiationSystem.EXPOSURE).getDuration(),60,"Exposure refreshed every original 60 ticks");
            }); h.succeed();
        });
    }
    private static void disabled(GameTestHelper h) {
        boolean old=RadiationSystem.DISABLED.get(); try {
            RadiationSystem.DISABLED.set(true); var player=WeaponGameTests.player(h);
            RadiationSystem.add(player,800); RadiationSystem.EXPOSURE.get().applyEffectTick(h.getLevel(),player,24); RadiationSystem.tick(player);
            h.assertValueEqual(RadiationSystem.dose(player),0,"Disabled radiation does not accumulate or poison");
        } finally { RadiationSystem.DISABLED.set(old); } h.succeed();
    }
    private RadiationGameTests() {}
}
