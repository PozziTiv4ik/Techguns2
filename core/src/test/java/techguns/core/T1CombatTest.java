package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class T1CombatTest {
    @Test void originalMaterialAndSingleTexture() {
        assertEquals(15,Armors.T1_COMBAT.stream().mapToDouble(a -> a.physical()).sum());
        for (var kind : new DamageKind[]{DamageKind.ENERGY,DamageKind.EXPLOSION,DamageKind.FIRE,DamageKind.ICE,DamageKind.LIGHTNING,DamageKind.DARK,DamageKind.POISON})
            assertEquals(11.25,Armors.T1_COMBAT.stream().mapToDouble(a -> a.armor(kind)).sum(),.00001);
        for(var spec:Armors.T1_COMBAT) {
            assertEquals(825,spec.durability()); assertEquals(.5,spec.toughness()); assertFalse(spec.canChangeCamo());
            assertEquals(java.util.List.of("t1_combat"),spec.camos()); assertEquals("minecraft:iron_ingot",spec.repairMetal());
            assertEquals("heavycloth",spec.repairCloth()); assertEquals(0,spec.radiationResistance()); assertEquals(0,spec.armor(DamageKind.RADIATION));
        }
        assertEquals(16,Armors.T1_COMBAT.stream().mapToInt(a -> a.displayedArmor(0)).sum());
    }
    @Test void noInventedSpeedOrRadiationBonuses() {
        assertEquals(.4,Armors.T1_COMBAT.stream().mapToDouble(a -> a.knockback()).sum(),.00001);
        for(var spec:Armors.T1_COMBAT) { assertEquals(0,spec.speed()); assertEquals(0,spec.jump()); assertEquals(0,spec.fallReduction()); assertEquals(0,spec.freeFallHeight()); }
        assertTrue(Armors.T2_COMBAT.getFirst().canChangeCamo()); assertTrue(Armors.HAZMAT.getFirst().canChangeCamo());
    }
    @Test void rawPenetrationAndFractionalToughness() {
        assertEquals(.6,Armors.T1_COMBAT.stream().mapToDouble(a -> a.absorption(DamageKind.PROJECTILE,.5f)).sum(),.00001);
        assertEquals(.36,Armors.T1_COMBAT.stream().mapToDouble(a -> a.absorption(DamageKind.PROJECTILE,2)).sum(),.00001);
        assertEquals(.45,Armors.T1_COMBAT.stream().mapToDouble(a -> a.absorption(DamageKind.POISON,0)).sum(),.00001);
    }
    @Test void repairThresholdsUseEightHundredTwentyFourDenominator() {
        var chest=Armors.forSlot("t1_combat",ArmorSlot.CHEST);
        for(var c:new int[][]{{1,1,0},{206,1,0},{207,1,1},{412,1,1},{413,2,1},{618,2,1},{619,2,2},{824,2,2}})
            assertArrayEquals(new int[]{c[1],c[2]},chest.repairBenchCosts(c[0]));
        int[][] healthy={{2,1},{3,2},{2,2},{2,1}}, damaged={{1,1},{2,2},{1,2},{1,1}};
        for(int i=0;i<4;i++) {
            assertArrayEquals(healthy[i],GrinderRules.armorSalvage(Armors.T1_COMBAT.get(i),0));
            assertArrayEquals(damaged[i],GrinderRules.armorSalvage(Armors.T1_COMBAT.get(i),1));
            assertArrayEquals(new int[]{1,0},GrinderRules.armorSalvage(Armors.T1_COMBAT.get(i),824));
        }
    }
    @Test void specialWearStopsAtOneWithoutErasingAbsorption() {
        var head=Armors.forSlot("t1_combat",ArmorSlot.HEAD);
        assertTrue(head.bonusesActive(823)); assertFalse(head.bonusesActive(824));
        assertEquals(1,head.specialWearLimit(823,20)); assertEquals(0,head.specialWearLimit(824,20));
        assertEquals(0,head.displayedArmor(824)); assertEquals(.15,head.absorption(DamageKind.PHYSICAL,0),.00001);
    }
}
