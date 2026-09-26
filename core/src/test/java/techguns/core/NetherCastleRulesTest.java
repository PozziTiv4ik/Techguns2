package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetherCastleRulesTest {
    @Test void keepsAllOriginalMediumTickets() {
        int[] counts=new int[3]; for(int roll=0;roll<1020;roll++) counts[NetherCastleRules.candidate(roll,true)]++;
        assertArrayEquals(new int[]{10,10,1000},counts);
        counts=new int[3]; for(int roll=0;roll<20;roll++) counts[NetherCastleRules.candidate(roll,false)]++;
        assertArrayEquals(new int[]{10,10,0},counts);
    }
    @Test void preservesInclusiveFortySixtyMixture() {
        int clusters=0; for(int roll=0;roll<=100;roll++) if(NetherCastleRules.cluster(roll)) clusters++;
        assertEquals(41,clusters); assertTrue(NetherCastleRules.cluster(40)); assertFalse(NetherCastleRules.cluster(41));
    }
    @Test void refusesRollsOutsideOriginalBounds() {
        assertThrows(IllegalArgumentException.class,()->NetherCastleRules.candidate(-1,true));
        assertThrows(IllegalArgumentException.class,()->NetherCastleRules.candidate(1020,true));
        assertThrows(IllegalArgumentException.class,()->NetherCastleRules.candidate(20,false));
        assertThrows(IllegalArgumentException.class,()->NetherCastleRules.cluster(-1));
        assertThrows(IllegalArgumentException.class,()->NetherCastleRules.cluster(101));
    }
}
