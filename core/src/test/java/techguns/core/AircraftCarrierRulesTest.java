package techguns.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AircraftCarrierRulesTest {
    @Test void exactUnrotatedCornersAndMiddlePairAreUsed() {
        var samples=new ArrayList<String>();
        assertEquals(63,AircraftCarrierRules.surface((x,z)->{samples.add(x+","+z);return x==0?63:64;}));
        assertEquals(List.of("0,0","54,0","0,21","54,21"),samples);
    }
    @Test void differenceUsesFirstCornerRatherThanWholeSpread() {
        assertEquals(63,AircraftCarrierRules.surface((x,z)->x==0?(z==0?63:62):64));
        assertEquals(-1,AircraftCarrierRules.surface((x,z)->x==0?63:65));
    }
    @Test void dryCornerRejectsAndFlatWaterPasses() {
        for(int i=0;i<4;i++) { int dry=i;int[] index={0};assertEquals(-1,AircraftCarrierRules.surface((x,z)->index[0]++==dry?-1:63)); }
        assertEquals(63,AircraftCarrierRules.surface((x,z)->63));
    }
    @Test void pivotRetainsSourceShiftsInAllFourDirections() {
        int[][] bounds={{0,0,53,20},{17,-16,37,37},{1,0,54,20},{17,-17,37,36}};
        for(int r=0;r<4;r++) {
            var points=new HashSet<String>();int minX=100,minZ=100,maxX=-100,maxZ=-100;
            for(int x=0;x<54;x++) for(int z=0;z<21;z++) {var p=AircraftCarrierRules.rotated(x,z,r);points.add(p[0]+","+p[1]);minX=Math.min(minX,p[0]);minZ=Math.min(minZ,p[1]);maxX=Math.max(maxX,p[0]);maxZ=Math.max(maxZ,p[1]);}
            assertEquals(54*21,points.size());assertArrayEquals(bounds[r],new int[]{minX,minZ,maxX,maxZ});
            assertArrayEquals(new int[]{27,10},AircraftCarrierRules.rotated(27,10,r));
        }
    }
    @Test void inclusiveSourceSupplyWeightsArePreserved() {
        for(boolean chance:List.of(false,true)) {
            var weights=new HashMap<Integer,Integer>();for(int roll=0;roll<(chance?19:10);roll++) weights.merge(AircraftCarrierRules.supply(roll,chance),1,Integer::sum);
            assertEquals(2,weights.get(0));for(int i=1;i<9;i++) assertEquals(1,weights.get(i));
            assertEquals(chance?9:null,weights.get(-1));
            assertThrows(IllegalArgumentException.class,()->AircraftCarrierRules.supply(chance?19:10,chance));
        }
    }
}
