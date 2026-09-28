package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinigunTest {
    private final WeaponDefinition gun = Weapons.definition("minigun");

    @Test void zeroSourceDelayUsesOnePlayerTickWithoutChangingOtherWeapons() {
        assertTrue(gun.automatic());
        assertEquals(0, gun.stats().fireDelay());
        assertEquals(1, gun.stats().firingInterval());
        for (var other : Weapons.ALL) if (!other.id().equals("minigun"))
            assertEquals(other.stats().fireDelay(), other.stats().firingInterval());
        assertThrows(IllegalArgumentException.class, () -> new WeaponSpec("bad", 200, -1, 100, 5, 3, 30, 50, 3, 60, .025));
    }
    @Test void allTwoHundredRoundsIncludingTheLastOneAreUsable() {
        int rounds = gun.stats().capacity();
        assertEquals(200, rounds);
        for (int i = 0; i < 200; i++) {
            assertTrue(Magazine.canFire(gun.stats(), rounds, 0, false));
            assertFalse(Magazine.canFire(gun.stats(), rounds, 1, false));
            rounds = Magazine.afterShot(gun.stats(), rounds);
        }
        assertFalse(Magazine.canFire(gun.stats(), rounds, 0, false));
        assertThrows(IllegalStateException.class, () -> Magazine.afterShot(gun.stats(), 0));
    }
    @Test void partialDrumReturnsOnlyWholeSixteenthBundles() {
        assertEquals(100, gun.stats().reloadTicks());
        for (int rounds : new int[]{0, 1, 12, 13, 99, 100, 199})
            assertEquals(new Magazine.Plan(200, 1, 1, rounds * 16 / 200), Magazine.plan(gun, rounds, 1, false));
        assertEquals(new Magazine.Plan(200, 0, 0, 0), Magazine.plan(gun, 200, 1, false));
        assertEquals(new Magazine.Plan(99, 0, 0, 0), Magazine.plan(gun, 99, 0, false));
    }
    @Test void incendiaryDrumUsesRifleBundlesAndTheSameEmptyShell() {
        assertEquals(new AmmoSpec("minigundrum_incendiary", "minigundrumempty", "riflerounds_incendiary", 16, false),
                IncendiaryAmmo.ammo(gun, BallisticVariant.INCENDIARY));
        assertEquals(5, gun.stats().damageAt(30));
        assertEquals(4, gun.stats().damageAt(40));
        assertEquals(3, gun.stats().damageAt(50));
        assertEquals(4.4f, IncendiaryAmmo.damageAt(gun.stats(), 40), .00001);
    }
    @Test void sourceSpinCompletesOneTurnBeforeRestarting() {
        assertEquals(5, MinigunAnimation.SPIN_TICKS);
        assertTrue(MinigunAnimation.startsSpin(-1, 10));
        assertFalse(MinigunAnimation.startsSpin(10, 14));
        assertTrue(MinigunAnimation.startsSpin(10, 15));
        assertTrue(MinigunAnimation.startsSpin(10, 9));
        assertEquals(-180, MinigunAnimation.degrees(10, 12, .5f));
        assertEquals(-324, MinigunAnimation.degrees(10, 14, .5f));
        assertEquals(0, MinigunAnimation.degrees(10, 15, 0));
        assertEquals(0, MinigunAnimation.degrees(10, 9, 0));
        assertEquals(0, MinigunAnimation.degrees(10, 10, Float.NaN));
    }
}
