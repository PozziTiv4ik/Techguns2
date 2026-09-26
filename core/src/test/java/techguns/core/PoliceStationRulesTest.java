package techguns.core;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoliceStationRulesTest {
    @Test void preservesTenPoliceTicketsInEverySourcePool() {
        for(boolean sandy:new boolean[]{false,true}) for(boolean ores:new boolean[]{false,true}) for(boolean oil:new boolean[]{false,true}) {
            int count=0;
            for(int r=0;r<BugNestLayout.total(sandy,ores,oil);r++) if(PoliceStationRules.selected(r,sandy,ores,oil)) {
                count++; assertFalse(BugNestLayout.selected(r,sandy,ores,oil));
                if(ores) { assertFalse(SpikeRules.selected(r,sandy,oil)); assertFalse(MeteorRules.selected(r,sandy,oil)); }
            }
            assertEquals(10,count);
            assertThrows(IllegalArgumentException.class,()->PoliceStationRules.selected(-1,sandy,ores,oil));
            assertThrows(IllegalArgumentException.class,()->PoliceStationRules.selected(BugNestLayout.total(sandy,ores,oil),sandy,ores,oil));
        }
    }
    @Test void keepsAverageFloorAndThreeBlockSpread() {
        int[] h=new int[16]; Arrays.fill(h,64); assertEquals(63,PoliceStationRules.surface(h));
        h[0]=67; assertEquals(63,PoliceStationRules.surface(h)); h[0]=68; assertEquals(Integer.MIN_VALUE,PoliceStationRules.surface(h));
        Arrays.fill(h,-15); h[0]=-16; assertEquals(-17,PoliceStationRules.surface(h));
    }
    @Test void rejectsLiquidSampleAndWrongSampleCount() {
        int[] h=new int[16]; h[8]=Integer.MIN_VALUE; assertEquals(Integer.MIN_VALUE,PoliceStationRules.surface(h));
        assertThrows(IllegalArgumentException.class,()->PoliceStationRules.surface(new int[15]));
    }
}
