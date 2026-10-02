package techguns.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PdwTest {
    private final WeaponDefinition gun = Weapons.definition("pdw");
    @Test void automaticPdwKeepsActiveSourceFactoryAndFalloff() {
        assertEquals(ProjectileKind.ADVANCED_BULLET, gun.projectile()); assertEquals(DamageKind.PROJECTILE, gun.projectile().damageKind());
        assertTrue(gun.automatic()); assertEquals(1, gun.projectileCount()); assertEquals(1, gun.stats().firingInterval());
        assertEquals(2, gun.stats().projectileSpeed()); assertEquals(20, gun.stats().projectileLifetime()); assertEquals(1, gun.penetration());
        assertEquals(.03, gun.stats().spread()); assertEquals(new AimSpec(1, false, 1, false), gun.aim());
        for (double d : new double[]{0, 17.99, 18}) assertEquals(5, gun.stats().damageAt(d));
        assertEquals(4, gun.stats().damageAt(21.5));
        for (double d : new double[]{25, 25.01, 1000}) assertEquals(3, gun.stats().damageAt(d));
        assertFalse(BallisticVariant.INCENDIARY.supported(gun)); assertFalse(BallisticVariant.EXPLOSIVE.supported(gun));
    }
    @Test void nonDivisibleMagazineReturnsOnlyWholeSourceBundles() {
        assertEquals(40, gun.stats().capacity()); assertEquals(40, gun.stats().reloadTicks());
        assertEquals(new AmmoSpec("advancedmagazine", "advancedmagazineempty", "advancedrounds", 3, false), gun.ammo());
        for (int rounds = 0; rounds < 40; rounds++) {
            int legacy = (int)Math.floor(rounds / (40f / 3));
            assertEquals(new Magazine.Plan(40, 1, 1, legacy), Magazine.plan(gun, rounds, 1, false));
            assertEquals(new Magazine.Plan(rounds, 0, 0, 0), Magazine.plan(gun, rounds, 0, false));
        }
        assertEquals(0, Magazine.plan(gun, 13, 1, false).looseBundles()); assertEquals(1, Magazine.plan(gun, 14, 1, false).looseBundles());
        assertEquals(1, Magazine.plan(gun, 26, 1, false).looseBundles()); assertEquals(2, Magazine.plan(gun, 27, 1, false).looseBundles());
        assertEquals(new Magazine.Plan(40, 0, 0, 0), Magazine.plan(gun, 40, 1, false));
        assertEquals(new Magazine.Plan(40, 0, 0, 0), Magazine.plan(gun, 1, 0, true));
    }
    @Test void npcHasFourSeparateShotsThenDistanceCooldown() {
        var spec = NpcWeapons.forWeapon("pdw"); assertEquals(new NpcAttackSpec(18, 30, 4, 2, 0), spec);
        var cycle = new NpcAttackCycle(spec); var shots = new ArrayList<Integer>();
        for (int tick = 0; tick <= 52; tick++) if (cycle.tick(Math.sqrt(145), true)) shots.add(tick);
        assertEquals(List.of(23, 25, 27, 29, 52), shots);
    }
    @Test void camosKeepSourceOrderAndBothWrapDirections() {
        assertEquals(3, GunCamos.count("pdw")); assertEquals(0, GunCamos.count("revolver"));
        assertEquals("techguns.item.defaultcamo", GunCamos.nameKey("pdw", 0));
        assertEquals("item.techguns.pdw.camoname.1", GunCamos.nameKey("pdw", 1));
        assertEquals("item.techguns.pdw.camoname.2", GunCamos.nameKey("pdw", 2));
        assertEquals(0, CamoPalette.cycle(2, 3, false)); assertEquals(2, CamoPalette.cycle(0, 3, true));
        assertEquals(.2, AdvancedBulletRules.HALF_LENGTH); assertEquals(.025, AdvancedBulletRules.HALF_WIDTH);
        assertEquals(2.5, AdvancedBulletRules.DELAY_FACTOR);
    }
}
