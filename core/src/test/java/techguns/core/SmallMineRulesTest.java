package techguns.core;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmallMineRulesTest {
    @Test void allFortySmallTicketsRemainDisjoint() {
        int[] counts=new int[4]; for(int roll=0;roll<40;roll++) counts[SmallOverworldRules.candidate(roll)]++;
        assertArrayEquals(new int[]{10,10,10,10},counts);
        for(int r=0;r<40;r++) assertEquals(r>=20 && r<=29,SmallOverworldRules.candidate(r)==2);
    }
    @Test void clusterInclusiveZeroBelongsToCoal() {
        int[] counts=new int[7]; for(int r=0;r<SmallMineRules.TYPE_BOUND;r++) counts[SmallMineRules.type(r)]++;
        assertArrayEquals(new int[]{11,10,10,5,2,2,2},counts);
        assertThrows(IllegalArgumentException.class,()->SmallMineRules.type(-1)); assertThrows(IllegalArgumentException.class,()->SmallMineRules.type(42));
    }
    @Test void coldCoverTakesPriorityOverDesertAndNether() {
        assertEquals(0,SmallMineRules.cover(false,false,false,false,false,false));
        assertEquals(1,SmallMineRules.cover(true,false,true,true,true,true));
        assertEquals(1,SmallMineRules.cover(false,true,false,true,false,false));
        assertEquals(2,SmallMineRules.cover(false,false,true,false,false,true));
        assertEquals(2,SmallMineRules.cover(false,false,false,true,false,false));
        assertEquals(2,SmallMineRules.cover(false,false,false,false,true,false));
        assertEquals(3,SmallMineRules.cover(false,false,false,false,false,true));
    }
    @Test void fifteenSamplesUseFullSeventeenByElevenAreaAndFiveBlockBurial() {
        int[] heights=new int[15]; Arrays.fill(heights,64); heights[14]=67;
        assertEquals(58,SmallOverworldRules.surface(15,heights)-5);
        heights[14]=68; assertEquals(Integer.MIN_VALUE,SmallOverworldRules.surface(15,heights));
        heights[14]=Integer.MIN_VALUE; assertEquals(Integer.MIN_VALUE,SmallOverworldRules.surface(15,heights));
        Arrays.fill(heights,-15); heights[0]=-16; assertEquals(-22,SmallOverworldRules.surface(15,heights)-5);
        assertThrows(IllegalArgumentException.class,()->SmallOverworldRules.surface(15,new int[12]));
    }
    @Test void rotationsPreserveRectangularPivotAndOriginShift() {
        int[][] shifts={{0,0},{3,-4},{-1,-1},{2,-3}};
        for(int t=0;t<4;t++) { assertArrayEquals(shifts[t],StructureRules.originShift(t,17,11));
            assertArrayEquals(new int[]{8,5},StructureRules.rotate(8,5,t,8,5));
        }
    }
}
