package techguns.core;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesertOilRulesTest {
    @Test void allSixCandidatesCoverEachSourceTicketExactlyOnce() {
        for(boolean sandy:new boolean[]{false,true}) for(boolean ores:new boolean[]{false,true}) for(boolean oil:new boolean[]{false,true}) {
            int[] counts=new int[6]; int total=BugNestLayout.total(sandy,ores,oil);
            for(int roll=0;roll<total;roll++) {
                boolean[] selected={BugNestLayout.selected(roll,sandy,ores,oil),PoliceStationRules.selected(roll,sandy,ores,oil),
                        SurvivorHideoutRules.selected(roll,sandy,ores,oil),ores && SpikeRules.selected(roll,sandy,oil),
                        ores && MeteorRules.selected(roll,sandy,oil),DesertOilRules.selected(roll,sandy,ores,oil)};
                int matches=0; for(int i=0;i<selected.length;i++) if(selected[i]) { counts[i]++; matches++; }
                assertEquals(1,matches,"No lost or overlapping medium tickets");
            }
            assertArrayEquals(new int[]{sandy?20:0,10,10,ores?10:0,ores?5:0,sandy&&ores&&oil?15:0},counts);
            assertThrows(IllegalArgumentException.class,()->DesertOilRules.selected(-1,sandy,ores,oil));
            assertThrows(IllegalArgumentException.class,()->DesertOilRules.selected(total,sandy,ores,oil));
        }
    }
    @Test void nineHeightSamplesPreserveThreeBlockSpreadAndFloor() {
        int[] h=new int[9]; Arrays.fill(h,64); assertEquals(63,DesertOilRules.surface(h)); h[8]=67; assertEquals(63,DesertOilRules.surface(h));
        h[8]=68; assertEquals(Integer.MIN_VALUE,DesertOilRules.surface(h)); Arrays.fill(h,-15); h[0]=-16; assertEquals(-17,DesertOilRules.surface(h));
        h[0]=Integer.MIN_VALUE; assertEquals(Integer.MIN_VALUE,DesertOilRules.surface(h));
        assertThrows(IllegalArgumentException.class,()->DesertOilRules.surface(new int[8]));
    }
    @Test void rimRetainsInclusiveTwoOilTicketsToOneSand() {
        assertTrue(DesertOilRules.rimOil(0)); assertTrue(DesertOilRules.rimOil(1)); assertFalse(DesertOilRules.rimOil(2));
        assertThrows(IllegalArgumentException.class,()->DesertOilRules.rimOil(-1)); assertThrows(IllegalArgumentException.class,()->DesertOilRules.rimOil(3));
    }
}
