package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandGrenadeTest {
    @Test void immediateReleaseIsFiniteAndChargeCaps() {
        for(var grenade:HandGrenade.values()) {
            assertEquals(.45,grenade.gravity(0),.000001);
            assertEquals(grenade.gravity(1),grenade.gravity(-100));
            assertEquals(.03,grenade.gravity(15),.000001);
            assertEquals(.015,grenade.gravity(30),.000001);
            assertEquals(grenade.gravity(30),grenade.gravity(Integer.MAX_VALUE));
        }
    }
    @Test void explosionRetainsRisingOuterBandAndInclusiveRadius() {
        for(var grenade:HandGrenade.values()) {
            assertEquals(grenade.damage,grenade.blastDamage(grenade.innerRadius));
            assertEquals(grenade.minimumDamage,grenade.blastDamage(grenade.innerRadius+.00001),.0001);
            assertEquals(grenade.damage,grenade.blastDamage(grenade.outerRadius));
            assertEquals(0,grenade.blastDamage(grenade.outerRadius+.00001));
        }
    }
    @Test void inheritedImpactFallsToMinimumBeyondOuterRadius() {
        for(var grenade:HandGrenade.values()) {
            assertEquals(grenade.damage,grenade.directDamage(0));
            assertEquals((grenade.damage+grenade.minimumDamage)/2,grenade.directDamage((grenade.innerRadius+grenade.outerRadius)/2));
            assertEquals(grenade.minimumDamage,grenade.directDamage(1000));
        }
    }
}
