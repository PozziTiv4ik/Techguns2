package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlameRulesTest {
    @Test void tankAndCadenceMatchTheSourceNotTheChainsaw() {
        var gun = Weapons.definition("flamethrower");
        assertEquals(ProjectileKind.FLAME, gun.projectile()); assertEquals(DamageKind.FIRE, gun.projectile().damageKind());
        assertTrue(gun.automatic()); assertEquals(100, gun.stats().capacity()); assertEquals(2, gun.stats().fireDelay());
        assertEquals(45, gun.stats().reloadTicks()); assertEquals(.01, gun.gravity()); assertEquals(16, gun.stats().projectileLifetime());
        assertEquals(.5, gun.stats().projectileSpeed()); assertEquals(.35, NpcWeapons.forWeapon(gun.id()).forwardOffset());
        assertEquals(new AmmoSpec("fueltank", "fueltankempty", "", 0, false), gun.ammo());
        assertEquals(new Magazine.Plan(100, 1, 1, 0), Magazine.plan(gun, 99, 2, false));
        assertEquals(new Magazine.Plan(100, 0, 0, 0), Magazine.plan(gun, 0, 0, true));
    }
    @Test void fireFalloffAndChanceHaveSourceBoundaries() {
        var gun = Weapons.definition("flamethrower").stats();
        assertEquals(5, gun.damageAt(0)); assertEquals(5, gun.damageAt(4)); assertEquals(3.5, gun.damageAt(10));
        assertEquals(2, gun.damageAt(16)); assertEquals(2, gun.damageAt(100));
        assertTrue(FlameRules.ignites(0)); assertTrue(FlameRules.ignites(.5)); assertFalse(FlameRules.ignites(Math.nextUp(.5)));
        assertEquals(3, FlameRules.BURN_SECONDS);
        for (double x : new double[]{-1, 1, Double.NaN}) assertThrows(IllegalArgumentException.class, () -> FlameRules.ignites(x));
    }
    @Test void continuousShotsDoNotRestartStartSampleOrRecoil() {
        assertTrue(FlameRules.startsSound(-1, 40)); assertFalse(FlameRules.startsSound(40, 42));
        assertFalse(FlameRules.startsSound(40, 49)); assertTrue(FlameRules.startsSound(40, 50));
        assertTrue(FlameRules.startsSound(40, 0));
        assertFalse(FlameRules.startsRecoil(40, 48)); assertTrue(FlameRules.startsRecoil(40, 50));
        assertEquals(10, FlameRules.MUZZLE_TICKS);
    }
    @Test void sourceSineReturnsToRestWithoutPersistingAnimation() {
        assertEquals(0, FlameRules.recoil(-1, 40, .5f)); assertEquals(0, FlameRules.recoil(40, 39, 0));
        assertEquals(0, FlameRules.recoil(40, 40, 0)); assertEquals(1, FlameRules.recoil(40, 42, .5f), 1e-6);
        assertEquals(-1, FlameRules.recoil(40, 47, .5f), 1e-6); assertEquals(0, FlameRules.recoil(40, 50, 0));
        assertEquals(0, FlameRules.recoil(40, 40, Float.NaN));
    }
}
