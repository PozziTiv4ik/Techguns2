package techguns.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlasterRifleTest {
    private final WeaponDefinition gun = Weapons.definition("blasterrifle");
    @Test void rifleHasOneMovingEnergyShotAndBothFalloffBoundaries() {
        assertEquals(ProjectileKind.BLASTER, gun.projectile()); assertEquals(DamageKind.ENERGY, gun.projectile().damageKind());
        assertEquals(1, gun.projectileCount()); assertTrue(gun.automatic()); assertEquals(5, gun.stats().firingInterval());
        assertEquals(2, gun.stats().projectileSpeed()); assertEquals(30, gun.stats().projectileLifetime()); assertEquals(1, gun.penetration());
        assertEquals(new AimSpec(.5f, true, .75f, true), gun.aim());
        double[] distances = {0, 24.99, 25, 25.01, 30, 34.99, 35, 35.01, 1000};
        float[] damage = {10, 10, 10, 9.998f, 9, 8.002f, 8, 8, 8};
        for (int n = 0; n < distances.length; n++) {
            assertEquals(damage[n], gun.stats().damageAt(distances[n]), .00001f);
            assertEquals(6, Weapons.definition("scatterbeamrifle").stats().damageAt(distances[n]));
        }
    }
    @Test void fiftyChargesReloadFromOneCellWithoutInventingResidualEnergy() {
        assertEquals(50, gun.stats().capacity()); assertEquals(45, gun.stats().reloadTicks());
        assertEquals(new AmmoSpec("energycell", "energycellempty", "", 0, false), gun.ammo());
        for (int rounds = 0; rounds < 50; rounds++) {
            assertEquals(new Magazine.Plan(50, 1, 1, 0), Magazine.plan(gun, rounds, 1, false));
            assertEquals(new Magazine.Plan(rounds, 0, 0, 0), Magazine.plan(gun, rounds, 0, false));
        }
        assertEquals(new Magazine.Plan(50, 0, 0, 0), Magazine.plan(gun, 50, 2, false));
        assertFalse(BallisticVariant.INCENDIARY.supported(gun)); assertFalse(BallisticVariant.EXPLOSIVE.supported(gun));
    }
    @Test void npcFiresFiveSeparateShotsThreeTicksApart() {
        var spec = NpcWeapons.forWeapon(gun.id()); assertEquals(new NpcAttackSpec(24, 30, 5, 3, 0), spec);
        var clock = new NpcAttackCycle(spec); var ticks = new ArrayList<Integer>();
        for (int tick = 0; tick <= 52; tick++) if (clock.tick(12, true)) ticks.add(tick);
        assertEquals(List.of(20, 23, 26, 29, 32, 52), ticks);
    }
}
