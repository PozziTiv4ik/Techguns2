package techguns.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterAttackTest {
    @Test void originalFiveBulletBurstRocketAndRest() {
        var c=new HelicopterAttack(); var shots=new ArrayList<Integer>(); var rockets=new ArrayList<Integer>();
        for(int tick=1;tick<=102;tick++) { var a=c.tick(16,true); if(a==HelicopterAttack.Action.BULLET) shots.add(tick); if(a==HelicopterAttack.Action.ROCKET) rockets.add(tick); }
        assertEquals(List.of(14,16,18,20,22,80,82,84,86,88),shots); assertEquals(List.of(35,101),rockets); assertEquals(-30,c.timer());
    }
    @Test void ExactRangeAndLostSightDoNotSpendNegativeRest() {
        var c=new HelicopterAttack(); for(int i=0;i<14;i++) c.tick(1,true); assertTrue(c.attacking());
        c.tick(4096,true); assertEquals(13,c.timer()); c.tick(1,false); assertEquals(12,c.timer());
        for(int i=0;i<20;i++) c.tick(1,false); assertEquals(0,c.timer()); assertFalse(c.attacking());
        c.restore(-30); for(int i=0;i<100;i++) c.tick(4096,false); assertEquals(-30,c.timer());
        for(int i=0;i<29;i++) assertEquals(HelicopterAttack.Action.NONE,c.tick(4095,true)); assertEquals(-1,c.timer());
    }
    @Test void savedTimerResumesWithoutExtraShots() {
        var a=new HelicopterAttack(); for(int i=0;i<17;i++) a.tick(1,true); var b=new HelicopterAttack(); b.restore(a.timer());
        for(int i=0;i<80;i++) { assertEquals(a.tick(1,true),b.tick(1,true)); assertEquals(a.timer(),b.timer()); }
    }
    @Test void typedArmorAndUnusedRadiusRemainOriginal() {
        assertEquals(20,HelicopterAttack.armor(DamageKind.PROJECTILE)); assertEquals(10,HelicopterAttack.armor(DamageKind.ENERGY));
        assertEquals(0,HelicopterAttack.armor(DamageKind.EXPLOSION)); assertEquals(0,HelicopterAttack.armor(DamageKind.DARK));
        var gun=HelicopterWeapons.ROCKET.stats(); assertEquals(30,RocketVariant.DEFAULT.innerRadius(gun)); assertEquals(40,RocketVariant.DEFAULT.outerRadius(gun));
        assertEquals(12,RocketVariant.DEFAULT.blastDamage(gun,29)); assertEquals(10,RocketVariant.DEFAULT.blastDamage(gun,35)); assertEquals(12,RocketVariant.DEFAULT.blastDamage(gun,40));
        assertEquals(0,RocketVariant.DEFAULT.blastDamage(gun,Math.nextUp(40d)));
        assertEquals(12,HelicopterWeapons.BULLET.stats().damageAt(30)); assertEquals(8,HelicopterWeapons.BULLET.stats().damageAt(40));
    }
    @Test void npcProfilesDoNotAddObtainableGunsAndResolveSavedShots() {
        assertFalse(Weapons.ALL.contains(HelicopterWeapons.BULLET)); assertFalse(Weapons.ALL.contains(HelicopterWeapons.ROCKET));
        assertSame(HelicopterWeapons.ROCKET,HelicopterWeapons.resolve("attackhelicopter_rocket"));
        assertSame(HelicopterWeapons.BULLET,HelicopterWeapons.resolve("attackhelicopter_bullet"));
        assertSame(Weapons.definition("revolver"),HelicopterWeapons.resolve("revolver")); assertThrows(IllegalArgumentException.class,()->HelicopterWeapons.resolve("missing"));
    }
    @Test void sourceDeathAnimationUsesBothRotationsAndModelScale() {
        assertEquals(-1,HelicopterAttack.deathOffset(50)); assertEquals(360,HelicopterAttack.deathTurn(50));
        assertEquals(-4,HelicopterAttack.deathOffset(100)); assertEquals(1440,HelicopterAttack.deathTurn(100));
    }
}
