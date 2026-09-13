package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkeletonSoldierTest {
    @Test void allThreeDrawsAreGunsAndDefaultShovelIsUnreachable() {
        assertEquals("revolver",SkeletonSoldierRules.weapon(0)); assertEquals("thompson",SkeletonSoldierRules.weapon(1)); assertEquals("handcannon",SkeletonSoldierRules.weapon(2));
        assertThrows(IllegalArgumentException.class,() -> SkeletonSoldierRules.weapon(3)); assertThrows(IllegalArgumentException.class,() -> SkeletonSoldierRules.weapon(-1));
    }
    @Test void independentArmorDrawsKeepInclusiveBoundary() {
        assertTrue(SkeletonSoldierRules.scout(0)); assertTrue(SkeletonSoldierRules.scout(.5));
        assertFalse(SkeletonSoldierRules.scout(Math.nextUp(.5))); assertFalse(SkeletonSoldierRules.scout(Math.nextDown(1d)));
        for(double invalid:new double[]{-1,1,Double.NaN,Double.POSITIVE_INFINITY}) assertThrows(IllegalArgumentException.class,() -> SkeletonSoldierRules.scout(invalid));
    }
    @Test void defaultTicketsRemainOneHundredAboveDangerZero() {
        var weights=new OverworldSpawnRules.Weights(200,200,100,100,3,50);
        for(int danger=0;danger<=5;danger++) {
            int count=0; for(int roll=0;roll<weights.total(danger);roll++) if(weights.choose(danger,roll)==OverworldSpawnRules.Choice.SKELETON_SOLDIER) count++;
            assertEquals(danger==0?0:100,count);
        }
        assertEquals(OverworldSpawnRules.Choice.ZOMBIE_SOLDIER,weights.choose(1,500));
        assertEquals(OverworldSpawnRules.Choice.SKELETON_SOLDIER,weights.choose(1,501)); assertEquals(OverworldSpawnRules.Choice.SKELETON_SOLDIER,weights.choose(1,600));
    }
    @Test void heldItemOffsetMirrorsOnlyTheHorizontalAxis() {
        assertEquals(-.06f,SkeletonSoldierRules.heldX(false)); assertEquals(.06f,SkeletonSoldierRules.heldX(true)); assertEquals(-.06f,SkeletonSoldierRules.HELD_Y);
        assertEquals(.0625f,SkeletonSoldierRules.BODY_INFLATION);
    }
    @Test void undeadSunlightMatchesSharedOriginalRule() {
        assertFalse(UndeadRules.sunIgnites(.5f,0)); assertTrue(UndeadRules.sunIgnites(.51f,0));
        assertTrue(UndeadRules.sunIgnites(1,.039f)); assertFalse(UndeadRules.sunIgnites(1,.041f));
        assertEquals(RuralZombieRules.sunIgnites(1,.02f),UndeadRules.sunIgnites(1,.02f));
    }
}
