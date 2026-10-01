package techguns.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GaussTest {
    private final WeaponDefinition gun = Weapons.definition("gaussrifle");
    @Test void compoundInputsRetainTheirAlignedRemainders() {
        assertEquals(List.of(new AmmoSpec.Component("gaussrifleslugs", "", "gaussrifleslugs"),
                new AmmoSpec.Component("energycell", "energycellempty", "")), gun.ammo().components());
        assertEquals(1, gun.ammo().bundlesPerMagazine()); assertFalse(gun.ammo().individual());
        assertTrue(gun.ammo().magazine());
    }
    @Test void everyPartialLoadRequiresACompletePairAndReturnsOnlyTheCell() {
        for (int rounds = 0; rounds < 8; rounds++) {
            assertEquals(new Magazine.Plan(rounds, 0, 0, 0), Magazine.plan(gun, rounds, 0, false));
            assertEquals(new Magazine.Plan(8, 1, 1, 0), Magazine.plan(gun, rounds, 1, false));
            assertEquals(new Magazine.Plan(8, 0, 0, 0), Magazine.plan(gun, rounds, 0, true));
        }
        assertEquals(new Magazine.Plan(8, 0, 0, 0), Magazine.plan(gun, 8, 64, false));
    }
    @Test void impulseWeaponKeepsConstantProjectileDamageAndOriginalCadence() {
        assertEquals(ProjectileKind.GAUSS, gun.projectile()); assertEquals(DamageKind.PROJECTILE, gun.projectile().damageKind());
        assertEquals(2, gun.penetration()); assertEquals(8, gun.stats().capacity());
        assertEquals(30, gun.stats().fireDelay()); assertEquals(60, gun.stats().reloadTicks());
        assertEquals(18, gun.stats().projectileLifetime()); assertEquals(5, gun.stats().projectileSpeed());
        for (int distance : new int[]{0, 89, 90, 91, 1000}) assertEquals(40, gun.stats().damageAt(distance));
        assertFalse(gun.automatic()); assertTrue(gun.aim().centered()); assertEquals(0, gun.aim().accuracyMultiplier());
        assertThrows(IllegalArgumentException.class, () -> new AimSpec(.35f, true, -.01f, true));
    }
    @Test void componentsCannotChangeUnderAPendingReloadOrDoubleSpendASlot() {
        var mutable = new ArrayList<>(gun.ammo().components());
        var spec = new AmmoSpec(mutable, 1, false); mutable.clear();
        assertEquals(2, spec.components().size());
        assertThrows(UnsupportedOperationException.class, () -> spec.components().clear());
        assertThrows(IllegalArgumentException.class, () -> new AmmoSpec(List.of(), 1, false));
        assertThrows(IllegalArgumentException.class, () -> new AmmoSpec(List.of(spec.components().getFirst(), spec.components().getFirst()), 1, false));
        assertThrows(IllegalArgumentException.class, () -> new AmmoSpec(spec.components(), 1, true));
    }
    @Test void gaussNeverAcceptsInjectedBallisticVariantsAndHudUsesEightRounds() {
        assertFalse(BallisticVariant.INCENDIARY.supported(gun)); assertFalse(BallisticVariant.EXPLOSIVE.supported(gun));
        assertEquals(new WeaponHud(8, 8, 30, .5f), WeaponHud.of(gun, 100, 30));
        assertEquals(.99f, GaussRules.AIR_DRAG); assertEquals(.85f, GaussRules.WATER_DRAG);
    }
}
