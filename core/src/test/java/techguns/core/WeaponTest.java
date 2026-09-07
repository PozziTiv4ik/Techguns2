package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponTest {
    private final WeaponSpec revolver = Weapons.REVOLVER;

    @Test void cylinderAllowsSixShotsAndNeverUnderflows() {
        int rounds = revolver.capacity();
        for (int i = 0; i < 6; i++) {
            assertTrue(Magazine.canFire(revolver, rounds, 0, false));
            rounds = Magazine.afterShot(revolver, rounds);
        }
        assertEquals(0, rounds);
        assertFalse(Magazine.canFire(revolver, rounds, 0, false));
        assertThrows(IllegalStateException.class, () -> Magazine.afterShot(revolver, 0));
    }

    @Test void cooldownAndReloadBlockFire() {
        assertFalse(Magazine.canFire(revolver, 6, 1, false));
        assertFalse(Magazine.canFire(revolver, 6, 0, true));
        assertTrue(Magazine.canFire(revolver, 6, 0, false));
    }

    @Test void oneOriginalAmmoBundleFillsCylinder() {
        assertEquals(new Magazine.Reload(6, 1), Magazine.reloadBundle(revolver, 0, 1, false));
        assertEquals(new Magazine.Reload(6, 1), Magazine.reloadBundle(revolver, 2, 8, false));
    }

    @Test void fullCylinderConsumesNothing() {
        assertEquals(new Magazine.Reload(6, 0), Magazine.reloadBundle(revolver, 6, 8, false));
    }

    @Test void missingAmmoPreservesRemainingRounds() {
        assertEquals(new Magazine.Reload(2, 0), Magazine.reloadBundle(revolver, 2, 0, false));
    }

    @Test void creativeReloadRequiresNoInventoryItems() {
        assertEquals(new Magazine.Reload(6, 0), Magazine.reloadBundle(revolver, 0, 0, true));
    }

    @Test void corruptAmmoCannotExceedCapacityOrBecomeNegative() {
        assertEquals(5, Magazine.afterShot(revolver, Integer.MAX_VALUE));
        assertEquals(new Magazine.Reload(0, 0), Magazine.reloadBundle(revolver, -100, 0, false));
    }

    @Test void damageMatchesOriginalDistanceCurve() {
        assertEquals(8, revolver.damageAt(0));
        assertEquals(8, revolver.damageAt(12));
        assertEquals(7, revolver.damageAt(16));
        assertEquals(6, revolver.damageAt(20));
        assertEquals(6, revolver.damageAt(1000));
        assertThrows(IllegalArgumentException.class, () -> revolver.damageAt(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> revolver.damageAt(-1));
    }

    @Test void invalidWeaponParametersFailEarly() {
        assertThrows(IllegalArgumentException.class, () -> new WeaponSpec(
                "broken", 0, 1, 1, 8, 6, 12, 20, 2, 40, 0.025));
        assertThrows(IllegalArgumentException.class, () -> new WeaponSpec(
                "broken", 6, 1, 1, Float.NaN, 6, 12, 20, 2, 40, 0.025));
    }
}
