package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.RuralZombieRules;
import techguns.modern.npc.*;

final class RuralDaylightGameTests {
    private enum Mode { RURAL, SKELETON, BANDIT, PSYCHO, ARMY, POLICE }
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) { r.register("rural_sun_night_roof_and_helmet",() -> RuralDaylightGameTests::sunlight); }
    private static void sunlight(GameTestHelper h) {
        sunlight(h,Mode.RURAL);
    }
    static void skeletonSunlight(GameTestHelper h) { sunlight(h,Mode.SKELETON); }
    static void banditSunlight(GameTestHelper h) { sunlight(h,Mode.BANDIT); }
    static void psychoSunlight(GameTestHelper h) { sunlight(h,Mode.PSYCHO); }
    static void policeSunlight(GameTestHelper h) { sunlight(h,Mode.POLICE); }
    static void armySunlight(GameTestHelper h) { sunlight(h,Mode.ARMY); }
    private static void sunlight(GameTestHelper h,Mode mode) {
        var level=h.getLevel(); var clock=level.dimensionType().defaultClock().orElseThrow(); long savedTime=level.clockManager().getTotalTicks(clock);
        var base=h.absolutePos(new BlockPos(4,0,4)); var pos=new BlockPos(base.getX(),181,base.getZ()); level.getChunk(pos);
        var npcs=new ArrayList<ArmedNpc>(); long seed=1;
        boolean living=mode==Mode.BANDIT || mode==Mode.PSYCHO || mode==Mode.ARMY;
        while(seed<10000 && !RuralZombieRules.sunIgnites(1,RandomSource.create(seed).nextFloat())) seed++;
        h.assertTrue(seed<10000,"Passing source sunlight draw found");
        try {
            // Reproduce a test starting with last tick's night brightness. GameTestHelper.setTime
            // changes the clock only; Level normally refreshes skyDarken on the next world tick.
            time(h,18000); h.assertTrue(level.getSkyDarken()>0,"Night brightness cache prepared");
            time(h,6000); OverworldSpawnGameTests.awaitLighting(h,pos,pos);
            for(int variant=0;variant<2;variant++) {
                ArmedNpc npc;
                if(mode==Mode.POLICE) {
                    var police=new ZombiePoliceman(NpcContent.POLICEMAN.get(),level); police.equipRoll(0,variant==0?0:.9,0,0,0); npc=police;
                } else if(mode==Mode.ARMY) {
                    var soldier=new ArmySoldier(NpcContent.ARMY.get(),level); soldier.equipLoadout(0,false,false,false,false);
                    if(variant==1) soldier.setItemSlot(EquipmentSlot.HEAD,net.minecraft.world.item.ItemStack.EMPTY); npc=soldier;
                } else if(mode==Mode.PSYCHO) {
                    var psycho=new PsychoSteve(NpcContent.PSYCHO.get(),level); psycho.equipCamo(variant==0?0:3);
                    if(variant==1) psycho.setItemSlot(EquipmentSlot.HEAD,net.minecraft.world.item.ItemStack.EMPTY); npc=psycho;
                } else if(mode==Mode.BANDIT) {
                    var bandit=new Bandit(NpcContent.BANDIT.get(),level); bandit.equipRoll(0,variant==0?0:.9); npc=bandit;
                } else if(mode==Mode.SKELETON) {
                    var soldier=new SkeletonSoldier(NpcContent.SKELETON.get(),level); soldier.equipRoll(0,variant==0?0:.9,0); npc=soldier;
                } else {
                    var rural=RuralZombieGameTests.create(h,RuralZombieRules.Kind.values()[variant]); rural.equipRoll(0,0,0,0,0); npc=rural;
                }
                npc.removeFreeWill(); npc.setNoGravity(true); npc.snapTo(Vec3.atBottomCenterOf(pos)); level.addFreshEntity(npc); npcs.add(npc);
                h.assertTrue(level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN,npc.position()),"Real Overworld day enables monster sunlight");
                h.assertTrue(npc.getLightLevelDependentMagicValue()>.99f && level.canSeeSky(BlockPos.containing(npc.getEyePosition())),"Real outdoor sunlight fixture");
                h.assertTrue(!npc.is(EntityTypeTags.BURN_IN_DAYLIGHT),"Custom source sunlight bypasses vanilla helmet protection");
                npc.getRandom().setSeed(seed); npc.aiStep();
                if(living) h.assertTrue(!npc.isOnFire(),"Living NPC does not ignite in daylight with or without headgear");
                else h.assertValueEqual(npc.getRemainingFireTicks(),160,"Source eight-second body ignition");
                h.assertValueEqual(npc.getItemBySlot(EquipmentSlot.HEAD).getDamageValue(),0,"Sunlight does not wear headgear");
                npc.clearFire(); time(h,18000); npc.getRandom().setSeed(seed); npc.aiStep(); h.assertTrue(!npc.isOnFire(),"Night prevents ignition"); time(h,6000);
                var roof=pos.above(3); level.setBlock(roof,Blocks.STONE.defaultBlockState(),3); OverworldSpawnGameTests.awaitLighting(h,roof,roof);
                npc.getRandom().setSeed(seed); npc.aiStep(); h.assertTrue(!npc.isOnFire(),"Opaque shelter prevents ignition");
                level.setBlock(roof,Blocks.AIR.defaultBlockState(),3); OverworldSpawnGameTests.awaitLighting(h,roof,roof);
                npc.getRandom().setSeed(seed); npc.aiStep(); h.assertValueEqual(npc.isOnFire(),!living,"Removing shelter preserves each NPC's source sunlight behavior");
                if(mode==Mode.POLICE) {
                    npc.clearFire(); npc.bindSpawner(new techguns.modern.npc.spawner.SpawnerLink(net.minecraft.core.GlobalPos.of(level.dimension(),pos),UUID.randomUUID()));
                    npc.getRandom().setSeed(seed); npc.aiStep(); h.assertTrue(!npc.isOnFire(),"Source spawner provenance prevents police daylight burning");
                }
                npc.discard();
            }
        } finally { time(h,savedTime); npcs.forEach(Entity::discard); level.setBlock(pos.above(3),Blocks.AIR.defaultBlockState(),3); }
        h.succeed();
    }
    private static void time(GameTestHelper h,long ticks) { h.setTime(ticks); h.getLevel().updateSkyBrightness(); }
    private RuralDaylightGameTests() {}
}
