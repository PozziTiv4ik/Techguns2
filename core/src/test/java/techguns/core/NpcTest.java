package techguns.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcTest {
    @Test void originalWeaponRollHasThreeSinglesAndTwoLaserCases() {
        assertEquals(List.of("rocketlauncher","ak47","combatshotgun","lasergun","lasergun"),
                java.util.stream.IntStream.range(0,5).mapToObj(SuperMutantRules::weapon).toList());
        assertThrows(IllegalArgumentException.class, () -> SuperMutantRules.weapon(5));
    }
    @Test void burstsHaveTheirOwnDelayInsteadOfPlayerFireCooldown() {
        var clock = new NpcAttackCycle(NpcWeapons.forWeapon("ak47")); var ticks = new ArrayList<Integer>();
        for (int tick=1;tick<=70;tick++) if(clock.tick(24,true)) ticks.add(tick);
        assertEquals(List.of(31,34,37,67,70),ticks);
        var rocket=NpcWeapons.forWeapon("rocketlauncher");
        assertEquals(26,rocket.intervalAt(0)); assertEquals(53,rocket.intervalAt(12)); assertEquals(80,rocket.intervalAt(24));
        assertEquals(20,NpcWeapons.forWeapon("combatshotgun").intervalAt(6));
    }
    @Test void lostSightAtScheduledShotDoesNotProduceAnImmediateRetry() {
        var clock=new NpcAttackCycle(NpcWeapons.forWeapon("lasergun"));
        for(int i=0;i<10;i++) assertFalse(clock.tick(0,true));
        assertFalse(clock.tick(0,false)); assertFalse(clock.tick(0,true));
        for(int i=0;i<9;i++) assertFalse(clock.tick(0,true));
        assertTrue(clock.tick(0,true));
    }
    @Test void approachStopsOnlyAfterTwentyConsecutiveVisibleTicks() {
        var clock=new NpcAttackCycle(NpcWeapons.forWeapon("ak47"));
        for(int i=0;i<19;i++) { clock.tick(10,true); assertTrue(clock.pursue(10)); }
        clock.tick(10,true); assertFalse(clock.pursue(10)); assertTrue(clock.pursue(25));
        clock.tick(10,false); assertTrue(clock.pursue(10));
        clock.resetTarget(); assertTrue(clock.pursue(10));
    }
    @Test void intrinsicArmorIsDifferentFromOrdinaryArmorScaling() {
        assertEquals(7,SuperMutantRules.armor(DamageKind.PROJECTILE));
        assertEquals(10,SuperMutantRules.armor(DamageKind.ENERGY));
        assertEquals(15,SuperMutantRules.armor(DamageKind.RADIATION));
        assertEquals(0,SuperMutantRules.armor(DamageKind.UNRESISTABLE));
        assertEquals(6.84f,ArmorMath.afterArmor(9,7,1,.5f),.0001);
    }
    @Test void difficultyPenaltiesAndAllCurrentAiProfilesExist() {
        assertEquals(.6f,SuperMutantRules.damage(1)); assertEquals(.8f,SuperMutantRules.damage(2)); assertEquals(1,SuperMutantRules.damage(3));
        assertEquals(1.3,SuperMutantRules.accuracy(1)); assertEquals(1.15,SuperMutantRules.accuracy(2)); assertEquals(1,SuperMutantRules.accuracy(3));
        for(var gun:Weapons.ALL) assertTrue(NpcWeapons.forWeapon(gun.id()).range()>0);
    }
}
