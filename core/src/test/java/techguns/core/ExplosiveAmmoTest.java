package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExplosiveAmmoTest {
    @Test void onlyAs50AcceptsExplosiveMagazines() {
        for (var gun : Weapons.ALL) {
            assertEquals(gun.id().equals("as50"), BallisticVariant.EXPLOSIVE.supported(gun));
            if (!gun.id().equals("as50")) assertThrows(IllegalArgumentException.class, () -> BallisticVariant.EXPLOSIVE.ammo(gun));
        }
        assertEquals(new AmmoSpec("as50magazine_explosive", "as50magazineempty", "sniperrounds_explosive", 2, false),
                BallisticVariant.EXPLOSIVE.ammo(Weapons.definition("as50")));
        assertThrows(IllegalArgumentException.class, () -> IncendiaryAmmo.ammo(Weapons.definition("as50"), BallisticVariant.EXPLOSIVE));
    }
    @Test void directFalloffKeepsBothModifiedEndpoints() {
        var gun = Weapons.definition("as50").stats();
        for (double d : new double[]{0, 40}) assertEquals(36.8f, ExplosiveAmmo.damageAt(gun, d), .00001);
        assertEquals(32.2f, ExplosiveAmmo.damageAt(gun, 50), .00001);
        for (double d : new double[]{60, 100}) assertEquals(27.6f, ExplosiveAmmo.damageAt(gun, d), .00001);
        assertEquals(3, ExplosiveAmmo.DIRECT_KNOCKBACK);
    }
    @Test void blastUsesIntegerVanillaExposureAndThreeBlockRadius() {
        assertEquals(1.5f, ExplosiveAmmo.EXPLOSION_POWER);
        assertEquals(22, ExplosiveAmmo.blastDamage(0, 1));
        assertEquals(8, ExplosiveAmmo.blastDamage(1.5, 1));
        assertEquals(8, ExplosiveAmmo.blastDamage(0, .5));
        assertEquals(4, ExplosiveAmmo.blastDamage(1.5, .5));
        assertEquals(1, ExplosiveAmmo.blastDamage(3, 1));
        assertEquals(0, ExplosiveAmmo.blastDamage(Math.nextUp(3d), 1));
        assertEquals(1, ExplosiveAmmo.blastDamage(1, 0));
    }
    @Test void invalidGeometryCannotCreateDamage() {
        for (double d : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> ExplosiveAmmo.blastDamage(d, 1));
            assertThrows(IllegalArgumentException.class, () -> ExplosiveAmmo.damageAt(Weapons.definition("as50").stats(), d));
        }
        for (double e : new double[]{-1, 1.1, Double.NaN}) assertThrows(IllegalArgumentException.class, () -> ExplosiveAmmo.blastDamage(1, e));
    }
    @Test void addingThirdVariantDoesNotChangeExistingAmmo() {
        assertEquals(BallisticVariant.EXPLOSIVE, BallisticVariant.fromId("explosive"));
        for (var gun : Weapons.ALL) {
            assertEquals(gun.ammo(), BallisticVariant.DEFAULT.ammo(gun));
            assertEquals(IncendiaryAmmo.supported(gun), BallisticVariant.INCENDIARY.supported(gun));
            if (IncendiaryAmmo.supported(gun)) assertEquals(IncendiaryAmmo.ammo(gun, BallisticVariant.INCENDIARY), BallisticVariant.INCENDIARY.ammo(gun));
        }
    }
}
