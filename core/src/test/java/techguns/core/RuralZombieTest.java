package techguns.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static techguns.core.RuralZombieRules.Kind.*;
import static techguns.core.OverworldSpawnRules.Choice.*;

class RuralZombieTest {
    @Test void farmerHasThreeHoesAndOneHandCannonCase() {
        assertEquals(List.of("minecraft:wooden_hoe","minecraft:iron_hoe","minecraft:stone_hoe","techguns:handcannon"),
                java.util.stream.IntStream.range(0,FARMER.weaponCount()).mapToObj(FARMER::weapon).toList());
        assertEquals(18,FARMER.health()); assertEquals(3,FARMER.attack());
    }
    @Test void minerDefaultIsReachableForTwoDespiteSourceCaseThree() {
        assertEquals(List.of("minecraft:stone_pickaxe","minecraft:iron_pickaxe","techguns:handcannon"),
                java.util.stream.IntStream.range(0,MINER.weaponCount()).mapToObj(MINER::weapon).toList());
        assertEquals(20,MINER.health()); assertEquals(4,MINER.attack());
        assertThrows(IllegalArgumentException.class,() -> MINER.weapon(3)); assertThrows(IllegalArgumentException.class,() -> FARMER.weapon(-1));
    }
    @Test void guaranteedHeadAndTorsoAreDifferentForEachNpc() {
        assertFalse(RuralZombieRules.armor(FARMER,ArmorSlot.HEAD,0)); assertTrue(RuralZombieRules.armor(FARMER,ArmorSlot.CHEST,.99));
        assertTrue(RuralZombieRules.armor(MINER,ArmorSlot.HEAD,.99)); assertFalse(RuralZombieRules.armor(MINER,ArmorSlot.CHEST,.99));
        for(var kind:List.of(FARMER,MINER)) for(var slot:List.of(ArmorSlot.LEGS,ArmorSlot.FEET)) {
            assertTrue(RuralZombieRules.armor(kind,slot,.5)); assertFalse(RuralZombieRules.armor(kind,slot,Math.nextUp(.5)));
        }
    }
    @Test void sunlightUsesOriginalBrightnessThresholdAndRandomFactor() {
        assertFalse(RuralZombieRules.sunIgnites(.5f,0)); assertTrue(RuralZombieRules.sunIgnites(.51f,0));
        assertTrue(RuralZombieRules.sunIgnites(1,.039f)); assertFalse(RuralZombieRules.sunIgnites(1,.041f));
        assertFalse(RuralZombieRules.sunIgnites(.6f,.02f)); assertTrue(RuralZombieRules.sunIgnites(1,.02f));
    }
    @Test void dangerZeroKeepsInclusiveTwoHundredOneToOneHundredNinetyNineTickets() {
        var weights=new OverworldSpawnRules.Weights(200,200,100,100,3,50); var counts=new EnumMap<OverworldSpawnRules.Choice,Integer>(OverworldSpawnRules.Choice.class);
        for(int roll=0;roll<weights.total(0);roll++) counts.merge(weights.choose(0,roll),1,Integer::sum);
        assertEquals(Map.of(ZOMBIE_FARMER,201,ZOMBIE_MINER,199),counts);
        assertEquals(ZOMBIE_FARMER,weights.choose(0,200)); assertEquals(ZOMBIE_MINER,weights.choose(0,201));
    }
    @Test void zeroWeightIsRemovedWithoutRenormalizingOtherDangers() {
        var weights=new OverworldSpawnRules.Weights(0,200,100,100,3,50);
        for(int roll=0;roll<200;roll++) assertEquals(ZOMBIE_MINER,weights.choose(0,roll));
        assertEquals(ZOMBIE_MINER,weights.choose(1,200)); assertEquals(ZOMBIE_SOLDIER,weights.choose(1,201));
        assertEquals(SKELETON_SOLDIER,weights.choose(1,400)); assertEquals(PSYCHO_STEVE,weights.choose(1,402));
    }
}
