package techguns.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static techguns.core.OverworldSpawnRules.Choice.*;

class ZombieSoldierTest {
    private final OverworldSpawnRules.Weights defaults = new OverworldSpawnRules.Weights(200,200,100,100,3,50);
    @Test void allFourWeaponCasesIncludeBothVanillaShovels() {
        assertEquals(List.of("techguns:revolver","techguns:thompson","minecraft:iron_shovel","minecraft:stone_shovel"),
                java.util.stream.IntStream.range(0,4).mapToObj(ZombieSoldierRules::weapon).toList());
        assertThrows(IllegalArgumentException.class,() -> ZombieSoldierRules.weapon(4));
    }
    @Test void helmetUsesTheSameInclusiveHalfChanceAsOtherParts() {
        assertTrue(ZombieSoldierRules.armor(0)); assertTrue(ZombieSoldierRules.armor(.5));
        assertFalse(ZombieSoldierRules.armor(Math.nextUp(.5)));
        for(double bad : new double[]{-1,1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,() -> ZombieSoldierRules.armor(bad));
    }
    @Test void distanceUsesHorizontalRadiusAndStrictBoundaries() {
        for(var entry : Map.of(499.999,0,500.,1,999.999,1,1000.,2,2499.999,2,2500.,3).entrySet())
            assertEquals(entry.getValue(),OverworldSpawnRules.distanceDanger(entry.getKey()+17,23,17,23,500,1000,2500));
        assertEquals(1,OverworldSpawnRules.distanceDanger(300,400,0,0,500,1000,2500));
        assertEquals(3,OverworldSpawnRules.distanceDanger(0,0,0,0,0,0,0));
    }
    @Test void sourceBucketsPreserveReservedTicketsAndInclusiveBoundary() {
        assertEquals(400,defaults.total(0)); assertEquals(603,defaults.total(1)); assertEquals(653,defaults.total(5));
        assertEquals(ZOMBIE_FARMER,defaults.choose(1,200)); assertEquals(ZOMBIE_MINER,defaults.choose(1,201));
        assertEquals(ZOMBIE_MINER,defaults.choose(1,400)); assertEquals(ZOMBIE_SOLDIER,defaults.choose(1,401));
        assertEquals(ZOMBIE_SOLDIER,defaults.choose(1,500)); assertEquals(SKELETON_SOLDIER,defaults.choose(1,501));
        assertEquals(PSYCHO_STEVE,defaults.choose(1,602)); assertEquals(BANDIT,defaults.choose(2,652));
    }
    @Test void exactSoldierShareIsNotRenormalizedOntoUnportedMobs() {
        for(int danger : List.of(0,1,2,5)) {
            int count=0;
            for(int roll=0;roll<defaults.total(danger);roll++) if(defaults.choose(danger,roll)==ZOMBIE_SOLDIER) count++;
            assertEquals(danger==0?0:100,count);
        }
    }
    @Test void disabledEntriesNeverReceiveZeroTicket() {
        var only=new OverworldSpawnRules.Weights(0,0,100,0,0,0);
        assertEquals(NONE,only.choose(0,0));
        for(int roll=0;roll<100;roll++) assertEquals(ZOMBIE_SOLDIER,only.choose(1,roll));
        var noSoldier=new OverworldSpawnRules.Weights(1,1,0,1,1,1);
        for(int roll=0;roll<noSoldier.total(5);roll++) assertNotEquals(ZOMBIE_SOLDIER,noSoldier.choose(5,roll));
    }
    @Test void configBoundsAndInvalidRollsAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> new OverworldSpawnRules.Weights(-1,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,() -> new OverworldSpawnRules.Weights(0,0,10001,0,0,0));
        assertThrows(IllegalArgumentException.class,() -> defaults.choose(1,-1));
        assertThrows(IllegalArgumentException.class,() -> defaults.choose(1,603));
    }
}
