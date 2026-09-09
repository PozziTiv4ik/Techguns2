package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArsenalTest {
    @Test void lasersKeepConstantDamageAtTheirEqualDropEndpoints() {
        for (String id : new String[]{"lasergun", "laserpistol"}) {
            var weapon = Weapons.definition(id);
            assertEquals(ProjectileKind.LASER, weapon.projectile());
            assertEquals(DamageKind.ENERGY, weapon.projectile().damageKind());
            assertEquals(7, weapon.stats().projectileLifetime());
            for (double distance : new double[]{0, 89, 90, 91, 150, 10000})
                assertEquals(weapon.stats().damage(), weapon.stats().damageAt(distance));
        }
        assertThrows(IllegalArgumentException.class, () -> new WeaponSpec("invalid", 1, 1, 1, 12, 6, 90, 90, 100, 7, 0));
    }
    @Test void usedCellsReturnEmptyContainersWithoutInventingResidualEnergyItems() {
        assertEquals(new Magazine.Plan(45, 1, 1, 0), Magazine.plan(Weapons.definition("lasergun"), 44, 1, false));
        assertEquals(new Magazine.Plan(20, 1, 1, 0), Magazine.plan(Weapons.definition("laserpistol"), 0, 1, false));
        assertEquals(new Magazine.Plan(20, 0, 0, 0), Magazine.plan(Weapons.definition("laserpistol"), 20, 1, false));
    }
    @Test void shotgunLoadsOnlyAvailableShellsAndKeepsExistingShells() {
        assertEquals(new Magazine.Plan(5, 2, 0, 0), Magazine.plan(Weapons.definition("combatshotgun"), 3, 2, false));
        assertEquals(new Magazine.Plan(2, 1, 0, 0), Magazine.plan(Weapons.definition("sawedoff"), 1, 20, false));
    }
    @Test void magazineSwapReturnsAnEmptyMagazineAndWholeRemainingBundles() {
        assertEquals(new Magazine.Plan(20, 1, 1, 1), Magazine.plan(Weapons.definition("thompson"), 15, 1, false));
        assertEquals(new Magazine.Plan(20, 1, 1, 0), Magazine.plan(Weapons.definition("thompson"), 9, 1, false));
    }
    @Test void revolverRoundItemRemainsASixShotBundle() {
        assertEquals(new Magazine.Plan(6, 1, 0, 0), Magazine.plan(Weapons.definition("revolver"), 2, 2, false));
    }
    @Test void creativeReloadDoesNotCreateRemainders() {
        for (WeaponDefinition gun : Weapons.ALL)
            assertEquals(new Magazine.Plan(gun.stats().capacity(), 0, 0, 0), Magazine.plan(gun, 0, 0, true));
    }
    @Test void allWeaponsRespectInventoryAndCapacityBounds() {
        for (WeaponDefinition gun : Weapons.ALL) {
            for (int rounds = 0; rounds <= gun.stats().capacity(); rounds++) {
                for (int available = 0; available <= 12; available++) {
                    Magazine.Plan plan = Magazine.plan(gun, rounds, available, false);
                    assertTrue(plan.rounds() >= rounds && plan.rounds() <= gun.stats().capacity(), gun.id());
                    assertTrue(plan.consumedItems() >= 0 && plan.consumedItems() <= available, gun.id());
                    if (available == 0) assertEquals(rounds, plan.rounds(), gun.id());
                    if (rounds == gun.stats().capacity()) assertEquals(0, plan.consumedItems(), gun.id());
                }
            }
        }
    }
    @Test void eightShotgunProjectilesAndAutomaticThompsonMatchLegacy() {
        assertEquals(8, Weapons.definition("sawedoff").projectileCount());
        assertEquals(8, Weapons.definition("combatshotgun").projectileCount());
        assertEquals(1, Weapons.definition("boltaction").projectileCount());
        assertTrue(Weapons.definition("thompson").automatic());
        assertFalse(Weapons.definition("revolver").automatic());
    }
}
