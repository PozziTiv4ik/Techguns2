package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RocketTest {
    private final WeaponSpec gun = Weapons.definition("rocketlauncher").stats();
    @Test void factoriesKeepIndependentRadiusVelocityAndLifetimeModifiers() {
        assertEquals(ProjectileKind.ROCKET, Weapons.definition("rocketlauncher").projectile());
        assertEquals(1, gun.capacity()); assertEquals(40, gun.reloadTicks()); assertEquals(10, gun.fireDelay());
        assertFalse(Weapons.definition("rocketlauncher").automatic());
        assertEquals(2, RocketVariant.HIGH_VELOCITY.speed(gun));
        assertEquals(150, RocketVariant.HIGH_VELOCITY.lifetime(gun));
        assertEquals(2.25, RocketVariant.HIGH_VELOCITY.innerRadius(gun));
        assertEquals(3.75, RocketVariant.HIGH_VELOCITY.outerRadius(gun));
        assertEquals(1, RocketVariant.NUKE.speed(gun)); assertEquals(200, RocketVariant.NUKE.lifetime(gun));
        assertEquals(15, RocketVariant.NUKE.innerRadius(gun)); assertEquals(25, RocketVariant.NUKE.outerRadius(gun));
        assertEquals(250, RocketVariant.NUKE.damage(gun)); assertEquals(50, RocketVariant.NUKE.minimumDamage(gun));
        assertThrows(IllegalArgumentException.class, () -> RocketVariant.fromId("unknown"));
    }
    @Test void directFalloffAndExplosionOuterBandAreDifferentSourceExpressions() {
        var rocket = RocketVariant.DEFAULT;
        assertEquals(50, rocket.blastDamage(gun, 3));
        assertEquals(15, rocket.blastDamage(gun, 3.25)); assertEquals(45, rocket.directDamage(gun, 3.25));
        assertEquals(45, rocket.blastDamage(gun, 4.75)); assertEquals(15, rocket.directDamage(gun, 4.75));
        assertEquals(50, rocket.blastDamage(gun, 5)); assertEquals(10, rocket.directDamage(gun, 5));
        assertEquals(0, rocket.blastDamage(gun, Math.nextUp(5.0)));
    }
    @Test void falloutDecaysOnlyInSecondHalfAndRetainsRoundedAmplifierZero() {
        assertEquals(9, ExplosionMath.falloutStrength(9, 599, 1200));
        assertEquals(9, ExplosionMath.falloutStrength(9, 600, 1200));
        assertEquals(5, ExplosionMath.falloutStrength(9, 900, 1200));
        assertEquals(1, ExplosionMath.falloutStrength(2, 900, 1200));
        assertEquals(0, ExplosionMath.falloutStrength(9, 1200, 1200));
        assertEquals(2.7, ExplosionMath.band(16, 15, 25, 9, 2), 1e-8);
        assertEquals(8.3, ExplosionMath.band(24, 15, 25, 9, 2), 1e-8);
    }
    @Test void rocketPartAppearsStrictlyAfterHalfOfReload() {
        assertTrue(ExplosionMath.rocketVisible(1, 0, 40)); assertFalse(ExplosionMath.rocketVisible(0, 0, 40));
        assertFalse(ExplosionMath.rocketVisible(1, 40, 40)); assertFalse(ExplosionMath.rocketVisible(0, 20, 40));
        assertTrue(ExplosionMath.rocketVisible(0, 19, 40)); assertTrue(ExplosionMath.rocketVisible(0, 1, 40));
    }
}
