package techguns.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpawnerRulesTest {
    @Test void sourceDefaults() {
        assertEquals(200,SpawnerRules.DEFAULT_DELAY); assertEquals(5,SpawnerRules.DEFAULT_REMAINING);
        assertEquals(3,SpawnerRules.DEFAULT_ACTIVE); assertEquals(2,SpawnerRules.DEFAULT_RANGE); assertEquals(10,SpawnerRules.HOME_RADIUS);
    }
    @Test void activeLimitIncludesRemainingDeathBudget() {
        assertTrue(SpawnerRules.hasRoom(2,3,5)); assertFalse(SpawnerRules.hasRoom(3,3,5));
        assertTrue(SpawnerRules.hasRoom(1,3,2)); assertFalse(SpawnerRules.hasRoom(2,3,2)); assertFalse(SpawnerRules.hasRoom(0,3,0));
    }
    @Test void strictWeightedTicketsDoNotUseTheOverworldBoundaryBug() {
        var weights=List.of(1,3,2); int[] counts=new int[3];
        for(int roll=0;roll<6;roll++) counts[SpawnerRules.choose(weights,roll)]++;
        assertArrayEquals(new int[]{1,3,2},counts); assertEquals(1,SpawnerRules.choose(List.of(1,1),1));
    }
    @Test void badWeightsAndRollsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->SpawnerRules.choose(List.of(1,0),0));
        assertThrows(IllegalArgumentException.class,()->SpawnerRules.choose(List.of(1),1));
        assertThrows(IllegalArgumentException.class,()->SpawnerRules.choose(List.of(),0));
    }
    @Test void sourceTriangularOffsetHasHalfBlockOrigin() {
        assertEquals(.5,SpawnerRules.offset(.25,.25,2)); assertEquals(-1.5,SpawnerRules.offset(0,1,2));
        assertEquals(2.5,SpawnerRules.offset(1,0,2)); assertEquals(.5,SpawnerRules.offset(.9,.1,0));
    }
}
