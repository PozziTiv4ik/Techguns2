package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetherTest {
    @Test void originalTableRetainsUnportedEntryAndInclusiveBoundary() {
        int cyber = 0;
        for (int roll = 0; roll < 130; roll++) if (NetherSpawnRules.choose(100, 30, roll) == NetherSpawnRules.Choice.CYBER_DEMON) cyber++;
        assertEquals(29, cyber);
        assertEquals(NetherSpawnRules.Choice.ZOMBIE_PIGMAN_SOLDIER, NetherSpawnRules.choose(100, 30, 100));
        assertEquals(NetherSpawnRules.Choice.CYBER_DEMON, NetherSpawnRules.choose(100, 30, 101));
    }
    @Test void disabledEntriesAndInvalidRolls() {
        assertEquals(NetherSpawnRules.Choice.NONE, NetherSpawnRules.choose(0, 0, 0));
        for (int roll = 0; roll < 30; roll++) assertEquals(NetherSpawnRules.Choice.CYBER_DEMON, NetherSpawnRules.choose(0, 30, roll));
        for (int roll = 0; roll < 100; roll++) assertEquals(NetherSpawnRules.Choice.ZOMBIE_PIGMAN_SOLDIER, NetherSpawnRules.choose(100, 0, roll));
        assertThrows(IllegalArgumentException.class, () -> NetherSpawnRules.choose(-1, 30, 0));
        assertThrows(IllegalArgumentException.class, () -> NetherSpawnRules.choose(100, 30, 130));
    }
    @Test void netherBlasterHasIndependentPlayerAndNpcTiming() {
        var gun = Weapons.definition("netherblaster");
        assertEquals(ProjectileKind.NETHER_BLASTER, gun.projectile()); assertEquals(DamageKind.FIRE, gun.projectile().damageKind());
        assertEquals(10, gun.stats().capacity()); assertEquals(8, gun.stats().fireDelay()); assertEquals(35, gun.stats().reloadTicks());
        assertEquals(60, gun.stats().projectileLifetime()); assertEquals(1.5, gun.stats().projectileSpeed());
        assertEquals(14, gun.stats().damageAt(15)); assertEquals(11, gun.stats().damageAt(22.5)); assertEquals(8, gun.stats().damageAt(40));
        assertEquals("nethercharge", gun.ammo().item()); assertFalse(gun.ammo().individual());
        var ai = new NpcAttackCycle(NpcWeapons.forWeapon(gun.id()));
        var times = new java.util.ArrayList<Integer>();
        for(int i=1;i<=85;i++) if(ai.tick(24,true)) times.add(i);
        assertEquals(java.util.List.of(41,81), times);
    }
    @Test void fireImmuneArmorResistsBlasterWithoutGrantingImmunity() {
        assertEquals(10, ArmorMath.defaultArmor(DamageKind.PROJECTILE, 10, true));
        assertEquals(5, ArmorMath.defaultArmor(DamageKind.ENERGY, 10, true));
        assertEquals(20, ArmorMath.defaultArmor(DamageKind.FIRE, 10, true));
        assertEquals(0, ArmorMath.defaultArmor(DamageKind.POISON, 10, true));
        assertEquals(3.36f, ArmorMath.afterArmor(14, 20, 1, .5f), .0001);
    }
}
