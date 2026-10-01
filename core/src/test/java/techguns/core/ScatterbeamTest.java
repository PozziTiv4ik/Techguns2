package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScatterbeamTest {
    private final WeaponDefinition gun = Weapons.definition("scatterbeamrifle");
    @Test void fiveEnergyPelletsUseOneChargeAndTheFinalSpeedSetter() {
        assertEquals(ProjectileKind.BLASTER, gun.projectile()); assertEquals(DamageKind.ENERGY, gun.projectile().damageKind());
        assertEquals(5, gun.projectileCount()); assertEquals(.1, gun.stats().spread()); assertEquals(.15, gun.pelletSpread());
        assertEquals(2, gun.stats().projectileSpeed()); assertEquals(15, gun.stats().projectileLifetime());
        assertEquals(7, gun.stats().firingInterval()); assertTrue(gun.automatic()); assertEquals(39, Magazine.afterShot(gun.stats(), 40));
        assertEquals(new AimSpec(.75f, true, .75f, false), gun.aim());
        for (double distance : new double[]{0, 29, 30, 31, 100}) assertEquals(6, gun.stats().damageAt(distance));
        assertEquals(0, gun.penetration()); assertEquals(0, gun.gravity()); assertEquals(.99f, BlasterRules.DRAG);
    }
    @Test void everyPartialCellReloadReplacesTheCellWithoutResidualEnergyItems() {
        assertEquals(new AmmoSpec("energycell", "energycellempty", "", 0, false), gun.ammo());
        for (int rounds = 0; rounds < 40; rounds++) {
            assertEquals(new Magazine.Plan(40, 1, 1, 0), Magazine.plan(gun, rounds, 1, false));
            assertEquals(new Magazine.Plan(rounds, 0, 0, 0), Magazine.plan(gun, rounds, 0, false));
            assertEquals(new Magazine.Plan(40, 0, 0, 0), Magazine.plan(gun, rounds, 0, true));
        }
        assertEquals(new Magazine.Plan(40, 0, 0, 0), Magazine.plan(gun, 40, 64, false));
        assertEquals(45, gun.stats().reloadTicks());
    }
    @Test void energyFamilyDoesNotAcceptBallisticVariantsAndNpcKeepsSourceCadence() {
        assertFalse(BallisticVariant.INCENDIARY.supported(gun)); assertFalse(BallisticVariant.EXPLOSIVE.supported(gun));
        var npc = NpcWeapons.forWeapon(gun.id()); assertEquals(30, npc.interval()); assertEquals(0, npc.burst());
    }
}
