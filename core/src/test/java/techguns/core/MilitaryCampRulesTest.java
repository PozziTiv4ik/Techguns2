package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MilitaryCampRulesTest {
    @Test void twoEqualLandTicketsKeepTheUnavailableCastle() {
        assertTrue(MilitaryCampRules.selected(0)); assertFalse(MilitaryCampRules.selected(1));
        assertThrows(IllegalArgumentException.class,()->MilitaryCampRules.selected(2));
    }
    @Test void flatMaximumFootprintSurvives() {
        assertEquals(new MilitaryCampRules.Site(79,79,80),MilitaryCampRules.surface(79,79,(x,z)->80).orElseThrow());
    }
    @Test void fiveBlocksOfReliefAreAllowed() {
        assertEquals(32,MilitaryCampRules.surface(32,32,(x,z)->x==0?80:85).orElseThrow().width());
        assertTrue(MilitaryCampRules.surface(32,32,(x,z)->x==0?80:86).isEmpty());
    }
    @Test void retriesShrinkBothAxesAtTheSameOriginIncludingTheFinalSmallAttempt() {
        assertEquals(new MilitaryCampRules.Site(16,31,70),MilitaryCampRules.surface(64,79,(x,z)->x<=16&&z<=31?70:95).orElseThrow());
    }
    @Test void waterAndNegativeHeightsRejectAllAttempts() {
        assertTrue(MilitaryCampRules.surface(79,32,(x,z)->-1).isEmpty());
        assertTrue(MilitaryCampRules.surface(32,32,(x,z)->0).isEmpty());
    }
    @Test void nonMultipleEndpointsAreNotInventedByTheSampler() {
        assertEquals(79,MilitaryCampRules.surface(79,79,(x,z)->x>72||z>72?-1:63).orElseThrow().width());
    }
    @Test void sourceIntegerAverageAnd255SentinelAreRetained() {
        assertEquals(64,MilitaryCampRules.surface(32,32,(x,z)->x==0?65:64).orElseThrow().height());
        assertTrue(MilitaryCampRules.surface(32,32,(x,z)->261).isEmpty());
        assertEquals(260,MilitaryCampRules.surface(32,32,(x,z)->260).orElseThrow().height());
    }
    @Test void callerMustRollTheSourceDimensions() {
        assertThrows(IllegalArgumentException.class,()->MilitaryCampRules.surface(31,32,(x,z)->70));
        assertThrows(IllegalArgumentException.class,()->MilitaryCampRules.surface(80,32,(x,z)->70));
    }
}
