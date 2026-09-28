package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IncendiaryAmmoTest {
    @Test void onlySourceWeaponsSupportTheVariant() {
        assertEquals(17, Weapons.ALL.stream().filter(IncendiaryAmmo::supported).count());
        for (var gun : Weapons.ALL) {
            assertEquals(gun.ammo(), IncendiaryAmmo.ammo(gun, BallisticVariant.DEFAULT));
            if (IncendiaryAmmo.supported(gun)) {
                var ammo = IncendiaryAmmo.ammo(gun, BallisticVariant.INCENDIARY);
                assertEquals(gun.ammo().emptyItem(), ammo.emptyItem());
                assertEquals(gun.ammo().bundlesPerMagazine(), ammo.bundlesPerMagazine());
                assertEquals(gun.ammo().individual(), ammo.individual());
                assertNotEquals(gun.ammo().item(), ammo.item());
            } else assertThrows(IllegalArgumentException.class, () -> IncendiaryAmmo.ammo(gun, BallisticVariant.INCENDIARY));
        }
    }
    @Test void cylinderMagazineAndIndividualShellContractsStayDifferent() {
        var cylinder = IncendiaryAmmo.ammo(Weapons.definition("revolver"), BallisticVariant.INCENDIARY);
        assertFalse(cylinder.magazine()); assertFalse(cylinder.individual()); assertEquals("", cylinder.looseItem());
        var smg = IncendiaryAmmo.ammo(Weapons.definition("thompson"), BallisticVariant.INCENDIARY);
        assertEquals(new AmmoSpec("smgmagazine_incendiary", "smgmagazineempty", "pistolrounds_incendiary", 2, false), smg);
        var shells = IncendiaryAmmo.ammo(Weapons.definition("combatshotgun"), BallisticVariant.INCENDIARY);
        assertTrue(shells.individual()); assertFalse(shells.magazine());
    }
    @Test void bothEndsOfDamageFalloffReceiveTheSourceMultiplier() {
        for (var gun : Weapons.ALL) if (IncendiaryAmmo.supported(gun)) {
            var s = gun.stats();
            assertEquals(s.damage()*1.1f, IncendiaryAmmo.damageAt(s, 0));
            assertEquals(s.damage()*1.1f, IncendiaryAmmo.damageAt(s, s.dropStart()));
            assertEquals(s.minimumDamage()*1.1f, IncendiaryAmmo.damageAt(s, s.dropEnd()));
            assertEquals(s.minimumDamage()*1.1f, IncendiaryAmmo.damageAt(s, s.dropEnd()+100));
            assertEquals((s.damage()*1.1f+s.minimumDamage()*1.1f)/2, IncendiaryAmmo.damageAt(s,(s.dropStart()+s.dropEnd())/2), 1e-5);
        }
    }
    @Test void ignitionUsesInitialDamageAndInclusiveBoundary() {
        var s = Weapons.definition("revolver").stats();
        double chance = 8f*1.1f/40f;
        assertTrue(IncendiaryAmmo.ignites(s, 0)); assertTrue(IncendiaryAmmo.ignites(s, chance));
        assertFalse(IncendiaryAmmo.ignites(s, Math.nextUp(chance)));
        assertFalse(IncendiaryAmmo.ignites(s, .999));
        assertEquals(3, IncendiaryAmmo.BURN_SECONDS);
    }
    @Test void malformedVariantAndArithmeticInputsAreRejected() {
        for (String id : new String[]{"unknown", "INCENDIARY", "", "nuke"}) assertThrows(IllegalArgumentException.class, () -> BallisticVariant.fromId(id));
        for (var v : BallisticVariant.values()) assertEquals(v, BallisticVariant.fromId(v.id()));
        var gun = Weapons.definition("pistol").stats();
        for (double d : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) assertThrows(IllegalArgumentException.class, () -> IncendiaryAmmo.damageAt(gun, d));
        for (double roll : new double[]{-1, 1, Double.NaN}) assertThrows(IllegalArgumentException.class, () -> IncendiaryAmmo.ignites(gun, roll));
    }
}
