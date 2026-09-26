package techguns.core;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GasStationRulesTest {
    @Test void keepsAllFourEqualSmallLandCandidates() {
        int count=0;
        for(int r=0;r<40;r++) { assertEquals(r>=30,GasStationRules.selected(r)); if(GasStationRules.selected(r)) count++; }
        assertEquals(10,count); assertEquals(40,GasStationRules.TOTAL);
        assertThrows(IllegalArgumentException.class,()->GasStationRules.selected(-1)); assertThrows(IllegalArgumentException.class,()->GasStationRules.selected(40));
        for(int x=-128;x<=128;x+=16) for(int z=-128;z<=128;z+=16)
            assertEquals(!(x%32==0 && z%32==0),StructureRules.smallSite(x,z,16,32,64));
        assertFalse(StructureRules.smallSite(1,16,16,32,64));
    }
    @Test void allTwelveSamplesIncludeTheTwelveBlockBoundary() {
        int[] h=new int[12]; Arrays.fill(h,64); assertEquals(63,GasStationRules.surface(h)); h[11]=67; assertEquals(63,GasStationRules.surface(h));
        h[11]=68; assertEquals(Integer.MIN_VALUE,GasStationRules.surface(h)); Arrays.fill(h,-15); h[0]=-16; assertEquals(-17,GasStationRules.surface(h));
        h[5]=Integer.MIN_VALUE; assertEquals(Integer.MIN_VALUE,GasStationRules.surface(h)); assertThrows(IllegalArgumentException.class,()->GasStationRules.surface(new int[9]));
    }
    @Test void rectangularOriginAndEveryCornerMatchSourceRotation() {
        int[][] shift={{0,0},{-2,1},{-1,0},{-2,2}};
        int[][][] corners={{{0,0},{8,0},{0,11},{8,11}},{{-2,10},{-2,2},{9,10},{9,2}},{{8,12},{0,12},{8,1},{0,1}},{{10,2},{10,10},{-1,2},{-1,10}}};
        for(int t=0;t<4;t++) { assertArrayEquals(shift[t],StructureRules.originShift(t,9,12)); int i=0;
            for(int[] c:new int[][]{{0,0},{8,0},{0,11},{8,11}}) assertArrayEquals(corners[t][i++],StructureRules.rotate(c[0],c[1],t,4,6));
        }
    }
}
