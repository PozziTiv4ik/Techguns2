package techguns.core;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrainStationRulesTest {
    @Test void originalInclusiveWeightsPreserveExtraFirstTicket() {
        int[][] weights={{2,1},{4,1},{1,1},{3,3,3,2}}; int[][] expected={{3,1},{5,1},{2,1},{4,3,3,2}};
        for(int i=0;i<weights.length;i++) { int[] counts=new int[weights[i].length];
            for(int r=0;r<TrainStationRules.bound(weights[i]);r++) counts[TrainStationRules.choice(r,weights[i])]++;
            assertArrayEquals(expected[i],counts);
        }
        assertThrows(IllegalArgumentException.class,()->TrainStationRules.choice(-1,2,1)); assertThrows(IllegalArgumentException.class,()->TrainStationRules.choice(4,2,1));
        assertThrows(IllegalArgumentException.class,()->TrainStationRules.bound()); assertThrows(IllegalArgumentException.class,()->TrainStationRules.bound(0,1));
    }
    @Test void trainAndGasUseDisjointOriginalTickets() {
        int[] counts=new int[4];
        for(int r=0;r<40;r++) { int candidate=SmallOverworldRules.candidate(r); counts[candidate]++; assertEquals(r/10,candidate);
            if(candidate==1) assertFalse(GasStationRules.selected(r));
        }
        assertArrayEquals(new int[]{10,10,10,10},counts);
        assertThrows(IllegalArgumentException.class,()->SmallOverworldRules.candidate(-1)); assertThrows(IllegalArgumentException.class,()->SmallOverworldRules.candidate(40));
    }
    @Test void rectangularSurfaceChecksIncludeBoundaryTwelve() {
        int[] h=new int[12]; Arrays.fill(h,64); h[11]=67; assertEquals(63,SmallOverworldRules.surface(12,h));
        h[11]=68; assertEquals(Integer.MIN_VALUE,SmallOverworldRules.surface(12,h)); h[11]=Integer.MIN_VALUE; assertEquals(Integer.MIN_VALUE,SmallOverworldRules.surface(12,h));
        assertThrows(IllegalArgumentException.class,()->SmallOverworldRules.surface(12,new int[9])); assertThrows(IllegalArgumentException.class,()->SmallOverworldRules.surface(0,new int[0]));
    }
    @Test void actualElevenSquareScanUsesRegisteredRectangularPivot() {
        int[][] shifts={{0,0},{-1,0},{-1,0},{-1,1}};
        int[][][] corners={{{0,0},{10,0},{0,10},{10,10}},{{-1,11},{-1,1},{9,11},{9,1}},{{10,12},{0,12},{10,2},{0,2}},{{11,1},{11,11},{1,1},{1,11}}};
        for(int t=0;t<4;t++) { assertArrayEquals(shifts[t],StructureRules.originShift(t,11,12)); int i=0;
            for(int[] p:new int[][]{{0,0},{10,0},{0,10},{10,10}}) assertArrayEquals(corners[t][i++],StructureRules.rotate(p[0],p[1],t,5,6));
        }
    }
}
