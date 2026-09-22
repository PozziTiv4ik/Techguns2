package techguns.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpikeRulesTest {
    @Test void unportedMediumLocationsRetainTheirTicketsInEveryBiomeTable() {
        for(boolean sandy:new boolean[]{false,true}) for(boolean oil:new boolean[]{false,true}) {
            int total=SpikeRules.total(sandy,oil),hits=0;
            assertEquals(sandy?(oil?70:55):35,total);
            for(int roll=0;roll<total;roll++) if(SpikeRules.selected(roll,sandy,oil)) hits++;
            assertEquals(10,hits); assertTrue(SpikeRules.selected(sandy?40:20,sandy,oil));
            assertFalse(SpikeRules.selected(sandy?39:19,sandy,oil)); assertFalse(SpikeRules.selected(sandy?50:30,sandy,oil));
        }
    }
    @Test void allFortySixTypeRollsKeepOriginalInclusiveFirstBoundary() {
        int[] counts=new int[7]; for(int i=0;i<46;i++) counts[SpikeRules.type(i)]++;
        assertArrayEquals(new int[]{6,10,10,10,5,2,3},counts);
    }
    @Test void nestedOreMixturesKeepTheirInclusiveProbabilities() {
        int[] common=new int[3],gem=new int[3];
        for(int i=0;i<4;i++) common[SpikeRules.inclusive(i,new int[]{1,1,1})]++;
        for(int i=0;i<7;i++) gem[SpikeRules.inclusive(i,new int[]{2,1,3})]++;
        assertArrayEquals(new int[]{2,1,1},common); assertArrayEquals(new int[]{3,1,3},gem);
    }
    @Test void averageSurfaceAndSpreadUseAllNineSamples() {
        assertEquals(63,SpikeRules.surface(new int[]{64,64,64,64,64,64,64,64,64}));
        assertEquals(63,SpikeRules.surface(new int[]{64,64,64,64,64,64,64,64,67}));
        assertEquals(Integer.MIN_VALUE,SpikeRules.surface(new int[]{64,64,64,64,64,64,64,64,68}));
        assertEquals(Integer.MIN_VALUE,SpikeRules.surface(new int[]{64,64,64,64,Integer.MIN_VALUE,64,64,64,64}));
    }
    @Test void modernNegativeTerrainUsesFloorAverage() {
        assertEquals(-22,SpikeRules.surface(new int[]{-20,-20,-20,-20,-20,-20,-20,-20,-21}));
    }
    @Test void invalidRollsCannotSilentlySelectAnotherResource() {
        assertThrows(IllegalArgumentException.class,()->SpikeRules.type(-1)); assertThrows(IllegalArgumentException.class,()->SpikeRules.type(46));
        assertThrows(IllegalArgumentException.class,()->SpikeRules.selected(35,false,false)); assertThrows(IllegalArgumentException.class,()->SpikeRules.selected(-1,true,true));
        assertThrows(IllegalArgumentException.class,()->SpikeRules.surface(new int[]{64}));
    }
}
