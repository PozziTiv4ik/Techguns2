package techguns.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureRulesTest {
    @Test void originalMixtureKeepsInclusiveThreeWayRollInsteadOfEqualOdds() {
        assertTrue(StructureRules.acidMixture(0)); assertTrue(StructureRules.acidMixture(1)); assertFalse(StructureRules.acidMixture(2));
        assertThrows(IllegalArgumentException.class,()->StructureRules.acidMixture(-1));
        assertThrows(IllegalArgumentException.class,()->StructureRules.acidMixture(3));
    }
    @Test void oddAcidPoolKeepsSourceNineWideCornerShift() {
        int[][] shifts={{0,0},{0,-1},{-1,-1},{-1,0}};
        for(int turn=0;turn<4;turn++) assertArrayEquals(shifts[turn],StructureRules.originShift(turn,9,9));
        assertArrayEquals(new int[]{3,6},StructureRules.rotate(2,3,1,4,4));
    }
    @Test void allCandidateTicketsRetainSourceOrderWithAndWithoutOres() {
        for(boolean ores:new boolean[]{false,true}) for(int roll=0;roll<StructureRules.smallNetherTotal(ores);roll++)
            assertEquals(roll/10,StructureRules.smallNetherCandidate(roll,ores));
        assertThrows(IllegalArgumentException.class,()->StructureRules.smallNetherCandidate(-1,true));
        assertThrows(IllegalArgumentException.class,()->StructureRules.smallNetherCandidate(40,false));
    }
    @Test void evenSizedLootScanRotatesAboutThreeWithoutTheOddAltarShift() {
        for(int turn=0;turn<4;turn++) assertArrayEquals(new int[]{0,0},StructureRules.originShift(turn,6,6));
        assertArrayEquals(new int[]{2,4},StructureRules.rotate(2,2,1,3,3));
        assertArrayEquals(new int[]{4,4},StructureRules.rotate(2,2,2,3,3));
        assertArrayEquals(new int[]{4,2},StructureRules.rotate(2,2,3,3,3));
    }
    @Test void positiveAndNegativeModuloGridKeepsLargeSitePriority() {
        for(int sign:new int[]{-1,1}) {
            assertTrue(StructureRules.smallSite(sign*16,sign*16,16,32,64));
            assertFalse(StructureRules.smallSite(sign*32,sign*32,16,32,64));
            assertFalse(StructureRules.smallSite(sign*64,sign*64,16,32,64));
            assertTrue(StructureRules.smallSite(sign*16,sign*32,16,32,64));
            assertFalse(StructureRules.smallSite(sign*15,sign*16,16,32,64));
        }
        assertFalse(StructureRules.smallSite(0,0,16,32,64));
    }
    @Test void choicesRetainTheirTickets() {
        for(boolean ores:new boolean[]{false,true}) { int hits=0;
            for(int roll=0;roll<StructureRules.smallNetherTotal(ores);roll++) if(StructureRules.altarSelected(roll,ores)) hits++;
            assertEquals(10,hits); assertFalse(StructureRules.altarSelected(10,ores));
        }
        assertEquals(40,StructureRules.smallNetherTotal(false)); assertEquals(50,StructureRules.smallNetherTotal(true));
        assertThrows(IllegalArgumentException.class,()->StructureRules.altarSelected(50,true));
    }
    @Test void clusterHalfWeightsHaveInclusiveFirstBoundary() {
        int count=0; for(int roll=0;roll<=100;roll++) if(StructureRules.clusterMixture(roll)) count++;
        assertEquals(51,count); assertTrue(StructureRules.clusterMixture(50)); assertFalse(StructureRules.clusterMixture(51));
        assertThrows(IllegalArgumentException.class,()->StructureRules.clusterMixture(-1));
        assertThrows(IllegalArgumentException.class,()->StructureRules.clusterMixture(101));
    }
    @Test void caveFloorUsesDescendingTenAirRunAndExcludesBottomEndpoint() {
        assertEquals(90,StructureRules.airFloor(y->y>90));
        assertEquals(-1,StructureRules.airFloor(y->y>91));
        assertEquals(-1,StructureRules.airFloor(y->true));
        assertEquals(-1,StructureRules.airFloor(y->y>20));
        assertEquals(21,StructureRules.airFloor(y->y>21));
        assertEquals(70,StructureRules.airFloor(y->y>95 || y>70 && y<90));
    }
    @Test void fourCornersMustExistAndHaveAtMostTenHeightDifference() {
        assertEquals(46,StructureRules.caveHeight(40,50,47,48));
        assertEquals(-1,StructureRules.caveHeight(40,51,47,48));
        assertEquals(-1,StructureRules.caveHeight(-1,40,40,40));
        assertEquals(-1,StructureRules.caveHeight(101,99,100,99));
        assertEquals(41,StructureRules.caveHeight(40,40,41,41));
        assertThrows(IllegalArgumentException.class,()->StructureRules.caveHeight(40,40,40));
    }
    @Test void cellRotationAndOriginalSizeCornerShiftRemainDistinct() {
        assertArrayEquals(new int[]{0,10},StructureRules.rotate(0,0,1,5,5));
        assertArrayEquals(new int[]{10,10},StructureRules.rotate(0,0,2,5,5));
        assertArrayEquals(new int[]{10,0},StructureRules.rotate(0,0,3,5,5));
        int[][] shifts={{0,0},{0,-1},{-1,-1},{-1,0}};
        for(int i=0;i<4;i++) assertArrayEquals(shifts[i],StructureRules.altarOriginShift(i));
    }
    @Test void metalPaletteRetainsAllMetadataAndOneLightSource() {
        assertEquals(10,NetherMetal.ALL.size());
        for(int i=0;i<10;i++) { assertEquals(i,NetherMetal.ALL.get(i).metadata()); assertEquals(i==9?15:0,NetherMetal.ALL.get(i).light()); }
        assertEquals("techguns:nethermetal_border_lava",NetherMetal.PALETTE.next("techguns:nethermetal_panel",true));
        String item="techguns:nethermetal_panel"; for(int i=0;i<10;i++) item=NetherMetal.PALETTE.next(item,false);
        assertEquals("techguns:nethermetal_panel",item);
    }
}
