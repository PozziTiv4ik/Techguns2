package techguns.core;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryHouseRulesTest {
    @Test void firstTenTicketsRemainIndependentOfTrainGasAndMine() {
        for(int r=0;r<40;r++) {
            assertEquals(r<10,SmallOverworldRules.candidate(r)==0);
            if(SmallOverworldRules.candidate(r)==0) { assertFalse(GasStationRules.selected(r)); assertNotEquals(1,SmallOverworldRules.candidate(r)); }
        }
    }
    @Test void nineSamplesKeepSpreadAndNegativeFloorSemantics() {
        int[] h=new int[9]; Arrays.fill(h,64); h[8]=67; assertEquals(63,SmallOverworldRules.surface(9,h));
        h[8]=68; assertEquals(Integer.MIN_VALUE,SmallOverworldRules.surface(9,h)); Arrays.fill(h,-15); h[0]=-16; assertEquals(-17,SmallOverworldRules.surface(9,h));
        h[4]=Integer.MIN_VALUE; assertEquals(Integer.MIN_VALUE,SmallOverworldRules.surface(9,h)); assertThrows(IllegalArgumentException.class,()->SmallOverworldRules.surface(9,new int[12]));
    }
    @Test void registeredElevenByTenDimensionsRotateTheActualNineByElevenScan() {
        int[][] shifts={{0,0},{0,-1},{-1,0},{0,0}};
        int[][][] corners={{{0,0},{8,0},{0,10},{8,10}},{{0,10},{0,2},{10,10},{10,2}},{{10,10},{2,10},{10,0},{2,0}},{{10,0},{10,8},{0,0},{0,8}}};
        for(int t=0;t<4;t++) { assertArrayEquals(shifts[t],StructureRules.originShift(t,11,10)); int i=0;
            for(int[] p:new int[][]{{0,0},{8,0},{0,10},{8,10}}) assertArrayEquals(corners[t][i++],StructureRules.rotate(p[0],p[1],t,5,5));
        }
    }
}
