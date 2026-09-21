package techguns.core;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OreDrillRulesTest {
    @Test void originalLengthAndEngineRodConstraints() {
        assertTrue(OreDrillRules.valid(0,1,0)); assertFalse(OreDrillRules.valid(0,2,0)); assertFalse(OreDrillRules.valid(0,1,1));
        assertTrue(OreDrillRules.valid(1,1,0)); assertFalse(OreDrillRules.valid(1,3,1)); assertFalse(OreDrillRules.valid(2,2,1));
        assertTrue(OreDrillRules.valid(4,7,3)); assertTrue(OreDrillRules.valid(4,12,3)); assertFalse(OreDrillRules.valid(4,13,3));
        assertFalse(OreDrillRules.valid(5,4,1)); assertFalse(OreDrillRules.valid(5,9,4));
    }
    @Test void exactMinimumFootprintsDoNotOverlapAirOrTarget() {
        int[] expected={2,19,102,273,556};
        for(int i=0;i<5;i++) {
            var s=i==0?new OreDrillRules.Size(0,1,0):new OreDrillRules.Size(i,2*i-1,i-1);
            var parts=OreDrillRules.parts(s); assertEquals(expected[i],parts.size());
            var positions=new HashSet<String>(); for(var c:parts) { assertTrue(positions.add(c.axial()+","+c.side1()+","+c.side2())); assertTrue(c.axial()<=s.length()); }
            for(var c:OreDrillRules.air(s)) assertFalse(positions.contains(c.axial()+","+c.side1()+","+c.side2()));
            for(var c:OreDrillRules.endCap(s)) assertFalse(positions.contains(c.axial()+","+c.side1()+","+c.side2()));
        }
    }
    @Test void headSizeAndOptionalFinalRow() {
        int[] caps={0,0,8,24,48},heads={0,1,1,2,2};
        for(int i=0;i<5;i++) { var s=i==0?new OreDrillRules.Size(0,1,0):new OreDrillRules.Size(i,2*i-1,i-1); assertEquals(i,s.miningRadius()); assertEquals(heads[i],s.headSize()); assertEquals(caps[i],OreDrillRules.endCap(s).size()); }
    }
    @Test void tinyCoalAndNetherDefaultRates() {
        var s=new OreDrillRules.Size(0,1,0);
        var coal=OreDrillRules.rate(s,1,1,0,10,.1,1,1); assertEquals(35,coal.perHour()); assertEquals(2057,coal.ticks()); assertEquals(28,coal.power());
        var crystal=OreDrillRules.rate(s,7,2,2,4,.5,1,1); assertEquals(12,crystal.perHour()); assertEquals(6000,crystal.ticks()); assertEquals(48,crystal.power());
    }
    @Test void connectedClusterIsCappedByLengthAndRadiusAffectsBothRates() {
        var s=new OreDrillRules.Size(4,7,3);
        var all=OreDrillRules.rate(s,100,3,3,.2,1,1,1); var capped=OreDrillRules.rate(s,11,3,3,.2,1,1,1);
        assertEquals(all,capped); assertEquals(11,all.perHour(),.000001); assertEquals(6545,all.ticks()); assertEquals(140,all.power());
        assertEquals(1,OreDrillRules.rate(s,1,3,3,.2,1,1,1).perHour(),.000001);
    }
    @Test void insufficientHeadUsesTwentySecondCobbleAndTwentyFourPower() {
        var rate=OreDrillRules.rate(new OreDrillRules.Size(0,1,0),1,1,2,4,.5,1,2);
        assertEquals(400,rate.ticks()); assertEquals(48,rate.power());
    }
    @Test void sourceTruncationAndZeroPowerArePreserved() {
        var s=new OreDrillRules.Size(0,1,0);
        assertEquals(0,OreDrillRules.rate(s,1,3,0,1000,1000,1000,0).ticks());
        assertEquals(0,OreDrillRules.rate(s,1,3,0,1000,1000,1000,0).power());
        assertEquals(119999,OreDrillRules.rate(s,1,3,3,.2,1,1,1).ticks());
    }
    @Test void taperUsesFourSlicesPerRodAndSourceRadiusFactors() {
        for(int radius=0;radius<=4;radius++) for(int rods:new int[]{1,7,12}) {
            float previous=Float.MAX_VALUE;
            for(int i=0;i<rods*4;i++) { float width=OreDrillRules.headHalfWidth(rods,radius,i); assertTrue(width>0 && width<previous); previous=width; }
        }
        assertEquals(.35f,OreDrillRules.headHalfWidth(1,0,0),.00001f);
        assertEquals(2.4f,OreDrillRules.headHalfWidth(7,4,0),.00001f);
    }
}
