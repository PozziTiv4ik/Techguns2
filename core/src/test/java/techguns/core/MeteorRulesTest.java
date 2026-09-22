package techguns.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MeteorRulesTest {
    @Test void ticketsNeverOverlapSpikeAndRetainAllUnportedWeights() {
        for(boolean sandy:new boolean[]{false,true}) for(boolean oil:new boolean[]{false,true}) {
            int count=0,spikes=0;
            for(int roll=0;roll<SpikeRules.total(sandy,oil);roll++) {
                if(MeteorRules.selected(roll,sandy,oil)) count++;
                if(SpikeRules.selected(roll,sandy,oil)) spikes++;
                assertFalse(MeteorRules.selected(roll,sandy,oil) && SpikeRules.selected(roll,sandy,oil));
            }
            assertEquals(5,count); assertEquals(10,spikes);
        }
    }
    @Test void inclusiveClusterRollAndSizeSeventeenSurface() {
        int[] frequency=new int[8]; for(int roll=0;roll<=50;roll++) frequency[MeteorRules.type(roll)]++;
        assertArrayEquals(new int[]{6,5,5,5,5,5,5,15},frequency);
        assertEquals(64,MeteorRules.surface(Collections.nCopies(25,65).stream().mapToInt(Integer::intValue).toArray()));
        int[] heights=Collections.nCopies(25,65).stream().mapToInt(Integer::intValue).toArray(); heights[24]=69;
        assertEquals(Integer.MIN_VALUE,MeteorRules.surface(heights));
    }
}
