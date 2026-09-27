package techguns.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import techguns.core.castle.*;
import techguns.core.castle.CastleSegments.SegmentType;

class CastleLayoutTest {
    private static final class World extends CastleWorld {
        record Placement(int family,SegmentType type,int x,int y,int z,int rotation) {}
        final List<Placement> segments=new ArrayList<>(); final Set<CastlePos> posts=new HashSet<>();
        World(long seed) { super(seed); }
        public void segment(int family,SegmentType type,int x,int y,int z,int rotation) { segments.add(new Placement(family,type,x,y,z,rotation)); }
        public void post(int x,int y,int z) { assertTrue(posts.add(new CastlePos(x,y,z))); }
    }
    @Test void landTicketsAreMutuallyExclusiveAndComplete() {
        for(int roll=0;roll<2;roll++) assertNotEquals(MilitaryCampRules.selected(roll),CastleLayout.selected(roll));
        assertFalse(CastleLayout.selected(0)); assertTrue(CastleLayout.selected(1));
        assertThrows(IllegalArgumentException.class,()->CastleLayout.selected(2));
    }
    @Test void sourceRotationSamplesShiftedCornersAndOnlyStepMultiples() {
        for(int direction=0;direction<4;direction++) {
            var samples=new HashSet<CastlePos>();
            var site=CastleLayout.surface(33,46,direction,(x,z)->{samples.add(new CastlePos(x,0,z));return 72;}).orElseThrow();
            assertEquals(72,site.surface()); assertEquals(108,samples.size());
            assertEquals(direction%2==0?33:46,site.sampledWidth());
            int[][] offsets={{0,0},{-7,6},{-1,0},{-7,7}};
            assertEquals(offsets[direction][0],site.offsetX()); assertEquals(offsets[direction][1],site.offsetZ());
            for(var p:samples) { assertEquals(0,(p.x()-site.offsetX())%4); assertEquals(0,(p.z()-site.offsetZ())%4); }
        }
    }
    @Test void heightToleranceIsInclusiveMeanAndRejectsWaterWithoutRetry() {
        assertEquals(65,CastleLayout.surface(32,32,0,(x,z)->x==0?74:64).orElseThrow().surface());
        assertTrue(CastleLayout.surface(32,32,0,(x,z)->x==0?75:64).isEmpty());
        assertTrue(CastleLayout.surface(47,47,0,(x,z)->x==4&&z==8?-1:70).isEmpty());
        assertThrows(IllegalArgumentException.class,()->CastleLayout.surface(48,32,0,(x,z)->64));
    }
    @Test void fiveAttemptsSelectLargestAndRemainDeterministic() {
        for(int seed=0;seed<40;seed++) {
            var a=new World(seed); var b=new World(seed);
            var first=CastleLayout.generate(a,0,60,0,47,39,47); var second=CastleLayout.generate(b,0,60,0,47,39,47);
            assertEquals(first,second); assertEquals(a.segments,b.segments); assertEquals(a.posts,b.posts);
            assertEquals(5,first.attempts().size()); assertEquals(Collections.max(first.attempts()),first.nodes().size());
        }
    }
    @Test void roomsRampsBoundsAndFiniteGuardSitesSurviveAllSizeExtremes() {
        var seenTypes=EnumSet.noneOf(SegmentType.class); var families=new HashSet<Integer>(); var facings=EnumSet.noneOf(CastleFacing.class);
        for(int seed=0;seed<160;seed++) {
            int width=32+seed%16,height=24+seed%16,depth=47-seed%16;
            var w=new World(seed); var result=CastleLayout.generate(w,0,60,0,width,height,depth); facings.add(result.facing());
            var nodes=new HashMap<CastlePos,CastleMaze.Node>();
            for(var n:result.nodes()) {
                assertTrue(n.x()>=0&&n.x()<width/5&&n.y()>=0&&n.y()<height/5&&n.z()>=0&&n.z()<depth/5);
                assertNull(nodes.put(new CastlePos(n.x(),n.y(),n.z()),n));
            }
            assertEquals(1,result.nodes().stream().filter(CastleMaze.Node::entrance).count());
            assertEquals(1,result.entrance().y());
            for(var n:result.nodes()) if(n.ramp()) {
                var partner=nodes.get(new CastlePos(n.x(),n.y()+n.elevation(),n.z()));
                assertNotNull(partner); assertTrue(partner.ramp()); assertEquals(-n.elevation(),partner.elevation());
            }
            for(var p:w.posts) {
                var n=nodes.get(new CastlePos((p.x()-2)/5,(p.y()-61)/5,(p.z()-2)/5));
                assertNotNull(n); assertFalse(n.ramp()||n.entrance());
            }
            assertTrue(w.posts.size()<=(int)(result.nodes().size()*.1f));
            for(var s:w.segments) { seenTypes.add(s.type());families.add(s.family());assertTrue(s.rotation()>=0&&s.rotation()<4); }
        }
        assertEquals(EnumSet.allOf(SegmentType.class),seenTypes); assertEquals(Set.of(0,1,2,3,4,5),families); assertEquals(4,facings.size());
    }
    @Test void presetUsesAllSixSourceHeightBranches() {
        var preset=CastlePreset.INSTANCE;
        for(int y=-1;y<7;y++) {
            int expected=y==-1?5:y==0?0:y==1?1:y<5?2:y==5?3:4;
            assertEquals(expected,preset.getSegment(SegmentType.END,y,0,7,false,false,1).family());
        }
    }
    @Test void onlyOneTemplateMatchesEachRotatedConnectionPattern() {
        var patterns=new HashSet<Integer>();
        for(var segment:CastleSegments.templateSegments.values()) if(segment.match) for(int r=0;r<segment.rotations;r++) {
            int bits=0; for(int i=0;i<8;i++) if(segment.pattern[(i+r*2)%8]) bits|=1<<i;
            assertTrue(patterns.add(bits),"Template HashMap order must not affect matching");
        }
        assertEquals(40,patterns.size());
    }
}
