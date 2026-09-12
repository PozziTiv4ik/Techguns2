package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrinderTest {
    @Test void chanceIsAnExpectedQuantityWithInclusiveRemainder() {
        assertEquals(2, GrinderRules.rolledCount(1, 1.5, 1, .5));
        assertEquals(1, GrinderRules.rolledCount(1, 1.5, 1, Math.nextUp(.5)));
        assertEquals(1, GrinderRules.rolledCount(1, .75, 1, .75));
        assertEquals(0, GrinderRules.rolledCount(1, .75, 1, Math.nextUp(.75)));
        assertEquals(1, GrinderRules.rolledCount(1, 0, 1, 0));
        assertEquals(0, GrinderRules.rolledCount(1, 0, 1, .1));
    }
    @Test void batchRoundsCombinedQuantityOnceAndUsesLinearPower() {
        for (double roll : new double[]{0, .125, .75, Math.nextDown(1d)}) {
            assertEquals(12, GrinderRules.rolledCount(1, 1.5, 8, roll));
            assertEquals(6, GrinderRules.rolledCount(1, .75, 8, roll));
            assertEquals(1, GrinderRules.rolledCount(1, .125, 8, roll));
            assertEquals(32, GrinderRules.rolledCount(4, 1, 8, roll));
        }
        assertEquals(0, GrinderRules.rolledCount(1, 1d / 12, 8, .9));
        assertEquals(1, GrinderRules.rolledCount(1, 1d / 12, 8, .5));
        for (int batch = 1; batch <= 8; batch++) assertEquals(500 * batch, GrinderRules.power(batch) * GrinderRules.DURATION);
    }
    @Test void healthyArmorRetainsOriginalExtraRounding() {
        assertArrayEquals(new int[]{2,1}, GrinderRules.armorSalvage(Armors.forSlot(ArmorSlot.HEAD), 0));
        assertArrayEquals(new int[]{3,2}, GrinderRules.armorSalvage(Armors.forSlot(ArmorSlot.CHEST), 0));
        assertArrayEquals(new int[]{2,2}, GrinderRules.armorSalvage(Armors.forSlot(ArmorSlot.LEGS), 0));
        assertArrayEquals(new int[]{2,1}, GrinderRules.armorSalvage(Armors.forSlot(ArmorSlot.FEET), 0));
    }
    @Test void wornArmorRoundsAtInverseDamageBoundaries() {
        int[][] cases = {{1,2,2},{248,2,2},{249,2,1},{495,2,1},{496,1,1},{742,1,1},{743,1,0},{989,1,0}};
        for (var c : cases) assertArrayEquals(new int[]{c[1], c[2]}, GrinderRules.armorSalvage(Armors.forSlot(ArmorSlot.CHEST), c[0]), "damage=" + c[0]);
        for (var armor : Armors.T2_COMBAT) assertArrayEquals(new int[]{1,0}, GrinderRules.armorSalvage(armor,989));
        assertArrayEquals(new int[]{1,2}, GrinderRules.armorSalvage(Armors.forSlot(ArmorSlot.LEGS),1));
    }
    @Test void maximumSpaceNeverUnderestimatesARealRoll() {
        for (double factor : new double[]{0, 1d/12, .0625, .125, .5, .75, 1, 1.5, 64}) for (int batch = 1; batch <= 8; batch++)
            for (double roll : new double[]{0, .125, .5, .75, Math.nextDown(1d)})
                assertTrue(GrinderRules.maximumCount(4, factor, batch) >= GrinderRules.rolledCount(4, factor, batch, roll));
        assertThrows(IllegalArgumentException.class, () -> GrinderRules.rolledCount(1, Double.NaN, 1, .5));
        assertThrows(IllegalArgumentException.class, () -> GrinderRules.rolledCount(1, -1, 1, .5));
        assertThrows(IllegalArgumentException.class, () -> GrinderRules.power(9));
    }
    @Test void sourceRendererMakesThreeTurnsAndLowersInput() {
        assertEquals(45, GrinderRules.rollerAngle(0)); assertEquals(225, GrinderRules.rollerAngle(.5f)); assertEquals(45, GrinderRules.rollerAngle(1));
        assertEquals(.75, GrinderRules.itemHeight(0,true), .00001); assertEquals(.35, GrinderRules.itemHeight(1,true), .00001);
        assertEquals(.2, GrinderRules.itemHeight(1,false), .00001);
    }
}
