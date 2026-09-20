package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeretTest {
    @Test void singleHeadPieceRetainsFractionalProtectionAndWearBoundary() {
        assertEquals(1,Armors.T2_BERET.size()); var a=Armors.T2_BERET.getFirst();
        assertEquals(ArmorSlot.HEAD,a.slot()); assertEquals(825,a.durability());
        assertEquals(2,a.physical()); assertEquals(1.5,a.elemental()); assertEquals(0,a.toughness());
        assertEquals(.06,a.absorption(DamageKind.PROJECTILE,.5f),.00001);
        assertEquals(0,a.absorption(DamageKind.RADIATION,0));
        assertTrue(a.bonusesActive(823)); assertFalse(a.bonusesActive(824));
        assertEquals(1,a.specialWearLimit(823,10)); assertEquals(0,a.specialWearLimit(824,10));
        assertEquals(.1,a.speed()); assertEquals(0,a.jump()); assertEquals(3,a.camos().size());
    }
    @Test void repairSlotsBothUseHeavyClothWithOriginalRounding() {
        var a=Armors.T2_BERET.getFirst(); assertEquals("heavycloth",a.repairMetal()); assertEquals("heavycloth",a.repairCloth());
        assertArrayEquals(new int[]{0,0},a.repairBenchCosts(0));
        assertArrayEquals(new int[]{1,0},a.repairBenchCosts(412));
        assertArrayEquals(new int[]{2,0},a.repairBenchCosts(413));
        assertArrayEquals(new int[]{2,0},a.repairBenchCosts(824));
    }
}
