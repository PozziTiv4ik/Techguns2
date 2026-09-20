package techguns.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhastlingAttackTest {
    @Test void originalThirtySixFiftyCadenceAndThreeShots() {
        var cycle=new GhastlingAttack(); cycle.start(); var shots=new ArrayList<Integer>();
        for(int tick=0;tick<150;tick++) { if(cycle.tick(100,64)==GhastlingAttack.Action.SHOT) shots.add(tick); }
        assertEquals(List.of(30,36,42,128,134,140),shots);
    }
    @Test void chargeFlagCoversWarmupAndWholeBurstThenClears() {
        var cycle=new GhastlingAttack(); cycle.start();
        for(int i=0;i<48;i++) { cycle.tick(100,64); assertTrue(cycle.attacking()); }
        cycle.tick(100,64); assertFalse(cycle.attacking()); assertEquals(50,cycle.time());
    }
    @Test void closeRangeUsesVanillaMeleeEveryTwentyTicks() {
        var cycle=new GhastlingAttack(); var melee=new ArrayList<Integer>();
        for(int i=0;i<=40;i++) if(cycle.tick(3.999,64)==GhastlingAttack.Action.MELEE) melee.add(i);
        assertEquals(List.of(0,20,40),melee); assertFalse(cycle.attacking());
        assertEquals(GhastlingAttack.Action.NONE,new GhastlingAttack().tick(4,64));
    }
    @Test void exactFollowBoundaryNeverFiresAndKeepsCooldownRunning() {
        var cycle=new GhastlingAttack(); cycle.tick(10,64);
        for(int i=0;i<35;i++) assertEquals(GhastlingAttack.Action.NONE,cycle.tick(4096,64));
        assertEquals(GhastlingAttack.Action.SHOT,cycle.tick(4095,64));
    }
    @Test void restartingGoalResetsBurstButRetainsSourceCooldown() {
        var cycle=new GhastlingAttack(); cycle.tick(10,64); for(int i=0;i<10;i++) cycle.tick(10,64);
        cycle.stop(); assertFalse(cycle.attacking()); cycle.start(); assertEquals(20,cycle.time());
        for(int i=0;i<19;i++) assertEquals(GhastlingAttack.Action.NONE,cycle.tick(10,64));
        assertEquals(GhastlingAttack.Action.NONE,cycle.tick(10,64)); assertTrue(cycle.attacking()); assertEquals(30,cycle.time());
    }
    @Test void soulPlatformUsesRegisteredElevenCentreForThirteenCellScan() {
        int[][] spawn={{6,6},{6,4},{4,4},{4,6}};
        for(int turn=0;turn<4;turn++) assertArrayEquals(spawn[turn],StructureRules.rotate(6,6,turn,5,5));
        assertArrayEquals(new int[]{12,-2},StructureRules.rotate(12,12,1,5,5));
        assertArrayEquals(new int[]{0,-1},StructureRules.originShift(1,11,11));
    }
}
