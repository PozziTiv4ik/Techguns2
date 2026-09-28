package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import techguns.core.StructureGrid.Size;

class StructureGridTest {
    @Test void defaultsMatchBothOriginalAxesAndPriority() {
        for(int x=-128;x<=128;x++) for(int z=-128;z<=128;z++) {
            var expected=x%64==0&&z%64==0?Size.BIG:x%32==0&&z%32==0?Size.MEDIUM:x%16==0&&z%16==0?Size.SMALL:null;
            for(var size:Size.values()) assertEquals(expected==size,StructureGrid.DEFAULT.accepts(size,x,z));
        }
    }
    @Test void nonDivisibleIntervalsPreserveSourceElseIfSemantics() {
        var grid=new StructureGrid(7,11,19);
        for(int x=-210;x<=210;x++) for(int z=-210;z<=210;z++) {
            var expected=x%19==0&&z%19==0?Size.BIG:x%11==0&&z%11==0?Size.MEDIUM:x%7==0&&z%7==0?Size.SMALL:null;
            int selected=0;
            for(var size:Size.values()) {assertEquals(expected==size,grid.accepts(size,x,z));if(grid.accepts(size,x,z)) selected++;}
            assertEquals(expected==null?0:1,selected);
        }
    }
    @Test void reservationsRequireTheIntersectionOfBothCoordinates() {
        var grid=StructureGrid.DEFAULT;
        assertTrue(grid.accepts(Size.SMALL,32,16));assertTrue(grid.accepts(Size.MEDIUM,64,32));
        assertFalse(grid.accepts(Size.SMALL,32,32));assertFalse(grid.accepts(Size.MEDIUM,64,64));
        assertTrue(grid.accepts(Size.BIG,0,0));
    }
    @Test void equalOrReversedIntervalsDoNotInventFallbackCandidates() {
        var same=new StructureGrid(16,16,16);
        assertTrue(same.accepts(Size.BIG,-16,32));assertFalse(same.accepts(Size.MEDIUM,-16,32));assertFalse(same.accepts(Size.SMALL,-16,32));
        var reverse=new StructureGrid(64,8,16);
        assertTrue(reverse.accepts(Size.MEDIUM,8,8));assertTrue(reverse.accepts(Size.BIG,64,64));assertFalse(reverse.accepts(Size.SMALL,64,64));
    }
    @Test void negativeSectorsRemainAnchoredToZero() {
        assertEquals(-7,StructureGrid.sectorOrigin(-1,7));assertEquals(-7,StructureGrid.sectorOrigin(-7,7));
        assertEquals(-14,StructureGrid.sectorOrigin(-8,7));assertEquals(0,StructureGrid.sectorOrigin(6,7));assertEquals(7,StructureGrid.sectorOrigin(7,7));
        for(int interval:new int[]{4,7,11,19,4097,100000}) for(int p=-1875000;p<=1875000;p+=17011) {
            int start=StructureGrid.sectorOrigin(p,interval);assertTrue(start<=p&&p<(long)start+interval);assertEquals(0,start%interval);
        }
    }
    @Test void fullOriginalConfigRangesAreAvailableWithoutVanillaCodecTruncation() {
        assertEquals(100000,new StructureGrid(100000,100000,100000).interval(Size.BIG));
        assertDoesNotThrow(()->new StructureGrid(4,8,16));assertDoesNotThrow(()->new StructureGrid(4097,50000,100000));
        for(int[] invalid:new int[][]{{3,32,64},{16,7,64},{16,32,15},{100001,32,64},{16,100001,64},{16,32,100001}})
            assertThrows(IllegalArgumentException.class,()->new StructureGrid(invalid[0],invalid[1],invalid[2]));
    }
}
