package techguns.core;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurvivorHideoutRulesTest {
    @Test void tenTicketsNeverOverlapExistingMediumCandidates() {
        for(boolean sandy:new boolean[]{false,true}) for(boolean ores:new boolean[]{false,true}) for(boolean oil:new boolean[]{false,true}) {
            int count=0;
            for(int r=0;r<BugNestLayout.total(sandy,ores,oil);r++) if(SurvivorHideoutRules.selected(r,sandy,ores,oil)) {
                count++; assertFalse(PoliceStationRules.selected(r,sandy,ores,oil)); assertFalse(BugNestLayout.selected(r,sandy,ores,oil));
                if(ores) { assertFalse(SpikeRules.selected(r,sandy,oil)); assertFalse(MeteorRules.selected(r,sandy,oil)); }
            }
            assertEquals(10,count);
            assertThrows(IllegalArgumentException.class,()->SurvivorHideoutRules.selected(-1,sandy,ores,oil));
            assertThrows(IllegalArgumentException.class,()->SurvivorHideoutRules.selected(BugNestLayout.total(sandy,ores,oil),sandy,ores,oil));
        }
    }
    @Test void surfaceUsesAllFifteenSamplesWithFloorAndSpread() {
        int[] h=new int[15]; Arrays.fill(h,64); assertEquals(63,SurvivorHideoutRules.surface(h)); h[14]=67; assertEquals(63,SurvivorHideoutRules.surface(h));
        h[14]=68; assertEquals(Integer.MIN_VALUE,SurvivorHideoutRules.surface(h)); Arrays.fill(h,-15); h[0]=-16; assertEquals(-17,SurvivorHideoutRules.surface(h));
        h[8]=Integer.MIN_VALUE; assertEquals(Integer.MIN_VALUE,SurvivorHideoutRules.surface(h)); assertThrows(IllegalArgumentException.class,()->SurvivorHideoutRules.surface(new int[16]));
    }
    @Test void coldAndSnowOverrideSandyBeachAndMesa() {
        for(int mask=0;mask<32;mask++) assertEquals((mask&3)!=0?2:(mask&28)!=0?1:0,
                SurvivorHideoutRules.canopy((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,(mask&16)!=0));
    }
    @Test void rectangularSourceOriginKeepsInclusiveCornerQuirk() {
        int[][] shift={{0,0},{-4,3},{-1,-1},{-5,4}};
        int[][][] corners={{{0,0},{10,0},{0,18},{10,18}},{{-4,14},{-4,4},{14,14},{14,4}},{{10,18},{0,18},{10,0},{0,0}},{{14,4},{14,14},{-4,4},{-4,14}}};
        for(int t=0;t<4;t++) { assertArrayEquals(shift[t],StructureRules.originShift(t,11,19)); int i=0;
            for(int[] c:new int[][]{{0,0},{10,0},{0,18},{10,18}}) assertArrayEquals(corners[t][i++],StructureRules.rotate(c[0],c[1],t,5,9));
        }
    }
}
