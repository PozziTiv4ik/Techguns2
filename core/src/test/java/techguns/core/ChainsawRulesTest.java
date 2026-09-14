package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChainsawRulesTest {
    @Test void sourceWeapon() {
        var gun=Weapons.definition("chainsaw"); var s=gun.stats();
        assertEquals(300,s.capacity()); assertEquals(45,s.reloadTicks()); assertEquals(3,s.fireDelay());
        assertEquals(10,s.damage()); assertEquals(2,s.projectileLifetime()); assertEquals(2,s.projectileSpeed());
        assertEquals(DamageKind.PHYSICAL,gun.projectile().damageKind()); assertEquals(1,gun.penetration());
        assertEquals(new AmmoSpec("fueltank","fueltankempty","",0,false),gun.ammo());
    }
    @Test void fuelIsReplacedWithoutInventingPartialTanks() {
        var gun=Weapons.definition("chainsaw");
        assertEquals(new Magazine.Plan(300,1,1,0),Magazine.plan(gun,173,1,false));
        assertEquals(new Magazine.Plan(0,0,0,0),Magazine.plan(gun,0,0,false));
        assertEquals(new Magazine.Plan(300,0,0,0),Magazine.plan(gun,0,0,true));
    }
    @Test void upgradesAreSequential() {
        for(int current=0;current<=2;current++) for(int target=0;target<=2;target++)
            assertEquals(target==current+1,ChainsawRules.canUpgrade(current,target));
        assertFalse(ChainsawRules.canUpgrade(-1,0));
    }
    @Test void miningUpgradeDoesNotRaiseMeleeDamage() {
        for(int head=0;head<=2;head++) {
            assertEquals(14+3*head,ChainsawRules.digSpeed(1,head,true));
            assertEquals(3+head,ChainsawRules.harvestLevel(head));
            assertEquals(1,ChainsawRules.digSpeed(0,head,true));
            assertEquals(1,ChainsawRules.digSpeed(300,head,false));
        }
        assertEquals(12,ChainsawRules.meleeModifier(1)); assertEquals(2,ChainsawRules.meleeModifier(0));
    }
    @Test void lastFuelAndCreative() {
        assertEquals(0,ChainsawRules.afterUse(1,false)); assertEquals(0,ChainsawRules.afterUse(0,false));
        assertEquals(1,ChainsawRules.afterUse(1,true)); assertEquals(0,ChainsawRules.head(-1)); assertEquals(2,ChainsawRules.head(100));
    }
}
