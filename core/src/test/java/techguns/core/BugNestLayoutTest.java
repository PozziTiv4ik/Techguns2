package techguns.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BugNestLayoutTest {
    @Test void originalMediumTicketsNeverCollideWithClusterStructures() {
        for(boolean sandy:List.of(false,true)) for(boolean oil:List.of(false,true)) {
            int count=0,total=BugNestLayout.total(sandy,true,oil);
            assertEquals(SpikeRules.total(sandy,oil),total);
            for(int roll=0;roll<total;roll++) if(BugNestLayout.selected(roll,sandy,true,oil)) {
                count++; assertFalse(SpikeRules.selected(roll,sandy,oil)); assertFalse(MeteorRules.selected(roll,sandy,oil));
            }
            assertEquals(sandy?20:0,count);
        }
        assertEquals(40,BugNestLayout.total(true,false,true));
        assertTrue(BugNestLayout.selected(19,true,false,true)); assertFalse(BugNestLayout.selected(20,true,false,true));
    }
    @Test void sourceSurfaceMedianAndLiquidRejectionIncludeModernNegativeHeights() {
        assertEquals(65,BugNestLayout.surface(new int[]{64,66,90,60}));
        assertEquals(-29,BugNestLayout.surface(new int[]{-30,-28,-20,-40}));
        assertEquals(Integer.MIN_VALUE,BugNestLayout.surface(new int[]{64,64,Integer.MIN_VALUE,64}));
        assertEquals(Integer.MIN_VALUE,BugNestLayout.surface(new int[]{0,256,0,0}));
        assertEquals(0,BugNestLayout.surface(new int[]{0,255,0,0}));
    }
    @Test void originalInclusivePaletteBoundaries() {
        int[] shell=new int[4],tunnel=new int[4];
        for(int roll=0;roll<20;roll++) shell[BugNestLayout.wall(roll,false)]++;
        for(int roll=0;roll<14;roll++) tunnel[BugNestLayout.wall(roll,true)]++;
        assertArrayEquals(new int[]{0,17,1,2},shell); assertArrayEquals(new int[]{0,13,0,1},tunnel);
        assertThrows(IllegalArgumentException.class,()->BugNestLayout.wall(20,false));
    }
    @Test void graphIsConnectedAndPreservesSourceRoomAndEncounterRanges() {
        for(long seed=0;seed<64;seed++) {
            var p=BugNestLayout.create(new BugNestLayout.Pos(-512,64,-512),16,31,seed,seed^17L,x->BugNestLayout.TERRAIN);
            assertTrue(p.rooms().size()>=7 && p.rooms().size()<=12); assertEquals(5,p.rooms().getFirst().radius());
            assertEquals(2,p.rooms().get(1).type()); assertEquals(2,p.rooms().get(1).radius());
            var seen=new HashSet<Integer>(); seen.add(0); boolean changed=true;
            while(changed) { changed=false; for(var edge:p.links()) if(seen.contains(edge.from())) changed|=seen.add(edge.to()); }
            assertEquals(p.rooms().size(),seen.size());
            long holes=p.cells().values().stream().filter(m->m==BugNestLayout.SPAWNER).count();
            assertTrue(holes>=5 && holes<=10,"Each ordinary room has its finite encounter");
            assertTrue(p.cells().size()<50000); assertTrue(p.cells().values().stream().anyMatch(v->v>=5 && v<=8));
        }
    }
    @Test void savedRandomStreamsAreDeterministicAndInputsAreBounded() {
        var origin=new BugNestLayout.Pos(0,80,0);
        var a=BugNestLayout.create(origin,16,31,0,7,p->BugNestLayout.TERRAIN);
        assertEquals(a,BugNestLayout.create(origin,16,31,0,7,p->BugNestLayout.TERRAIN));
        var b=BugNestLayout.create(origin,16,31,0,8,p->BugNestLayout.TERRAIN);
        assertEquals(a.rooms(),b.rooms()); assertNotEquals(a.cells(),b.cells());
        assertThrows(UnsupportedOperationException.class,()->a.cells().clear());
        assertThrows(IllegalArgumentException.class,()->BugNestLayout.create(origin,32,16,0,7,p->0));
    }
}
