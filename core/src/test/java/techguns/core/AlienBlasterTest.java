package techguns.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlienBlasterTest {
    private final WeaponDefinition gun = Weapons.definition("alienblaster");
    @Test void gunAndGhastlingRetainDifferentFactoryProfiles() {
        assertEquals(ProjectileKind.ALIEN_BLASTER, gun.projectile()); assertEquals(DamageKind.FIRE, gun.projectile().damageKind());
        assertEquals(1, gun.projectileCount()); assertTrue(gun.automatic()); assertEquals(8, gun.stats().firingInterval());
        assertEquals(1, gun.stats().projectileSpeed()); assertEquals(40, gun.stats().projectileLifetime()); assertEquals(1, gun.penetration());
        for (double distance : new double[]{0, 39.99, 40, 40.01, 1000}) assertEquals(16, gun.stats().damageAt(distance));
        assertEquals(6, AlienBlasterRules.GHASTLING_DAMAGE); assertEquals(200, AlienBlasterRules.GHASTLING_LIFETIME);
        assertEquals(1.5, AlienBlasterRules.GHASTLING_SPEED); assertEquals(.05, AlienBlasterRules.GHASTLING_SPREAD);
    }
    @Test void partialEnergyReloadConsumesOneCellAndReturnsOneEmptyCell() {
        assertEquals(10, gun.stats().capacity()); assertEquals(35, gun.stats().reloadTicks());
        assertEquals(new AmmoSpec("energycell", "energycellempty", "", 0, false), gun.ammo());
        for (int rounds = 0; rounds < 10; rounds++) {
            assertEquals(new Magazine.Plan(10, 1, 1, 0), Magazine.plan(gun, rounds, 1, false));
            assertEquals(new Magazine.Plan(rounds, 0, 0, 0), Magazine.plan(gun, rounds, 0, false));
        }
        assertFalse(BallisticVariant.INCENDIARY.supported(gun)); assertFalse(BallisticVariant.EXPLOSIVE.supported(gun));
    }
    @Test void ignitionRetainsInclusiveSourceBoundary() {
        assertTrue(AlienBlasterRules.ignites(0)); assertTrue(AlienBlasterRules.ignites(.35));
        assertFalse(AlienBlasterRules.ignites(Math.nextUp(.35))); assertFalse(AlienBlasterRules.ignites(-.01));
        assertFalse(AlienBlasterRules.ignites(Double.NaN)); assertFalse(AlienBlasterRules.ignites(1));
        assertEquals(3, AlienBlasterRules.IGNITE_SECONDS);
    }
    @Test void npcSingleShotsFollowDistanceIntervalWithoutBurst() {
        var spec = NpcWeapons.forWeapon(gun.id()); assertEquals(new NpcAttackSpec(24, 40, 0, 0, 0), spec);
        var clock = new NpcAttackCycle(spec); var ticks = new ArrayList<Integer>();
        for (int tick = 0; tick <= 80; tick++) if (clock.tick(24, true)) ticks.add(tick);
        assertEquals(List.of(40, 80), ticks);
    }
}
