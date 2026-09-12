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
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) { r.register("rural_sun_night_roof_and_helmet",() -> RuralDaylightGameTests::sunlight); }
    private static void sunlight(GameTestHelper h) {
        var level=h.getLevel(); var clock=level.dimensionType().defaultClock().orElseThrow(); long savedTime=level.clockManager().getTotalTicks(clock);
        var base=h.absolutePos(new BlockPos(4,0,4)); var pos=new BlockPos(base.getX(),181,base.getZ()); level.getChunk(pos);
        var npcs=new ArrayList<RuralZombie>(); long seed=1;
        while(seed<10000 && !RuralZombieRules.sunIgnites(1,RandomSource.create(seed).nextFloat())) seed++;
        h.assertTrue(seed<10000,"Passing source sunlight draw found");
        try {
            h.setTime(6000); OverworldSpawnGameTests.awaitLighting(h,pos,pos);
            for(var kind:RuralZombieRules.Kind.values()) {
                var npc=RuralZombieGameTests.create(h,kind); npc.removeFreeWill(); npc.setNoGravity(true); npc.snapTo(Vec3.atBottomCenterOf(pos)); npc.equipRoll(0,0,0,0,0); level.addFreshEntity(npc); npcs.add(npc);
                h.assertTrue(level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN,npc.position()),"Real Overworld day enables monster sunlight");
                h.assertTrue(npc.getLightLevelDependentMagicValue()>.99f && level.canSeeSky(BlockPos.containing(npc.getEyePosition())),"Real outdoor sunlight fixture");
                h.assertTrue(!npc.is(EntityTypeTags.BURN_IN_DAYLIGHT),"Custom source sunlight bypasses vanilla helmet protection");
                npc.getRandom().setSeed(seed); npc.aiStep(); h.assertValueEqual(npc.getRemainingFireTicks(),160,"Source eight-second body ignition");
                h.assertValueEqual(npc.getItemBySlot(EquipmentSlot.HEAD).getDamageValue(),0,"Sunlight does not wear the miner helmet");
                npc.clearFire(); h.setTime(18000); npc.getRandom().setSeed(seed); npc.aiStep(); h.assertTrue(!npc.isOnFire(),"Night prevents ignition"); h.setTime(6000);
                var roof=pos.above(3); level.setBlock(roof,Blocks.STONE.defaultBlockState(),3); OverworldSpawnGameTests.awaitLighting(h,roof,roof);
                npc.getRandom().setSeed(seed); npc.aiStep(); h.assertTrue(!npc.isOnFire(),"Opaque shelter prevents ignition");
                level.setBlock(roof,Blocks.AIR.defaultBlockState(),3); OverworldSpawnGameTests.awaitLighting(h,roof,roof);
                npc.getRandom().setSeed(seed); npc.aiStep(); h.assertTrue(npc.isOnFire(),"Removing shelter restores source sunlight behavior"); npc.discard();
            }
        } finally { h.setTime(savedTime); npcs.forEach(Entity::discard); level.setBlock(pos.above(3),Blocks.AIR.defaultBlockState(),3); }
        h.succeed();
    }
    private RuralDaylightGameTests() {}
}
