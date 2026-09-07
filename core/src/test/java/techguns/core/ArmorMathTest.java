package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmorMathTest {
    @Test void legacyArmorIsLinearAndCappedAtNinetySixPercent() {
        assertEquals(1.6f, ArmorMath.afterArmor(8, 20, 0, 0), .00001f);
        assertEquals(.32f, ArmorMath.afterArmor(8, 100, 0, 0), .00001f);
        assertEquals(8, ArmorMath.afterArmor(8, 0, 0, 0));
    }
    @Test void penetrationUsesFourPointsPerRatingAndToughnessOffsetsIt() {
        assertEquals(10.88f, ArmorMath.afterArmor(16, 10, 0, .5f), .00001f);
        assertEquals(9.6f, ArmorMath.afterArmor(16, 10, 2, .5f), .00001f);
        assertEquals(16, ArmorMath.afterArmor(16, 1, 0, 2));
    }
    @Test void proportionalArmorDoesNotChangeWithBulletDamage() {
        assertEquals(ArmorMath.afterArmor(8, 10, 0, .5f) * 5, ArmorMath.afterArmor(40, 10, 0, .5f), .00001f);
    }
    @Test void categoryDefaultsMatchTheSource() {
        assertEquals(10, ArmorMath.defaultArmor(DamageKind.PROJECTILE, 10, false));
        assertEquals(5, ArmorMath.defaultArmor(DamageKind.ENERGY, 10, false));
        assertEquals(20, ArmorMath.defaultArmor(DamageKind.FIRE, 10, true));
        assertEquals(0, ArmorMath.defaultArmor(DamageKind.RADIATION, 10, false));
    }
    @Test void invalidNumericDamageCannotPropagate() {
        assertThrows(IllegalArgumentException.class, () -> ArmorMath.afterArmor(Float.NaN, 10, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ArmorMath.afterArmor(8, -1, 0, 0));
    }
}
