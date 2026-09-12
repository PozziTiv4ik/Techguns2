package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HazmatTest {
    @Test void materialSettersDoNotChangeDarkProtection() {
        assertEquals(10,total(DamageKind.PHYSICAL),.00001);
        assertEquals(10,total(DamageKind.PROJECTILE),.00001);
        assertEquals(10,total(DamageKind.EXPLOSION),.00001);
        for(var kind:new DamageKind[]{DamageKind.ENERGY,DamageKind.FIRE,DamageKind.ICE,DamageKind.LIGHTNING}) assertEquals(16,total(kind),.00001);
        assertEquals(20,total(DamageKind.POISON),.00001); assertEquals(20,total(DamageKind.RADIATION),.00001);
        assertEquals(7.5,total(DamageKind.DARK),.00001); assertEquals(0,total(DamageKind.UNRESISTABLE));
    }
    private static double total(DamageKind kind) { return Armors.HAZMAT.stream().mapToDouble(a -> a.armor(kind)).sum(); }
    @Test void everyPartHasEqualDurabilityAndOneExposureResistance() {
        for(var spec:Armors.HAZMAT) {
            assertEquals(1100,spec.durability()); assertEquals(0,spec.toughness()); assertEquals(1,spec.radiationResistance());
            assertEquals(0,spec.speed()); assertEquals(0,spec.jump()); assertEquals(0,spec.knockback());
            assertEquals("",spec.repairMetal()); assertEquals("protectivefiber",spec.repairCloth());
            assertEquals(4,spec.camos().size()); assertEquals("hazmatsuit_blue",spec.camos().get(3));
        }
        assertEquals(11,Armors.HAZMAT.stream().mapToInt(a -> a.displayedArmor(0)).sum());
    }
    @Test void wornBonusesAndCappedSpecialWearDoNotEraseDefense() {
        var boots=Armors.forSlot("hazmat",ArmorSlot.FEET);
        assertTrue(boots.bonusesActive(1098)); assertFalse(boots.bonusesActive(1099));
        assertEquals(1,boots.specialWearLimit(1098,20)); assertEquals(0,boots.specialWearLimit(1099,20));
        assertEquals(0,boots.displayedArmor(1099)); assertEquals(.16,boots.absorption(DamageKind.RADIATION,0),.00001);
        assertEquals(.1,boots.fallReduction()); assertEquals(.5,boots.freeFallHeight());
    }
    @Test void repairAndHealthySalvageUseOnlyFiberWithOriginalRounding() {
        int[] costs={2,4,3,2};
        for(int i=0;i<4;i++) {
            var armor=Armors.HAZMAT.get(i);
            assertArrayEquals(new int[]{0,0},armor.repairBenchCosts(0));
            assertArrayEquals(new int[]{0,costs[i]},armor.repairBenchCosts(1099));
            assertArrayEquals(new int[]{0,costs[i]+1},GrinderRules.armorSalvage(armor,0));
            assertArrayEquals(new int[]{0,costs[i]},GrinderRules.armorSalvage(armor,1));
            assertArrayEquals(new int[]{0,1},GrinderRules.armorSalvage(armor,1099));
        }
        for(var c:new int[][]{{1,1},{274,1},{275,2},{549,2},{550,3},{824,3},{825,4},{1099,4}})
            assertArrayEquals(new int[]{0,c[1]},Armors.forSlot("hazmat",ArmorSlot.CHEST).repairBenchCosts(c[0]));
    }
    @Test void rawPenetrationIsAppliedPerPieceWithoutNpcMultiplier() {
        assertEquals(.32,Armors.HAZMAT.stream().mapToDouble(a -> a.absorption(DamageKind.PROJECTILE,.5f)).sum(),.00001);
        assertEquals(.08,Armors.HAZMAT.stream().mapToDouble(a -> a.absorption(DamageKind.PROJECTILE,2)).sum(),.00001);
    }
    @Test void mixedSetsKeepTheirSeparateProtectionAndMaterials() {
        var helmet=Armors.forSlot("hazmat",ArmorSlot.HEAD); var chest=Armors.forSlot(ArmorSlot.CHEST);
        assertEquals(.316,helmet.absorption(DamageKind.PHYSICAL,0)+chest.absorption(DamageKind.PHYSICAL,0),.00001);
        assertEquals(.2,helmet.absorption(DamageKind.RADIATION,0)+chest.absorption(DamageKind.RADIATION,0),.00001);
        assertEquals("heavycloth",chest.repairCloth()); assertEquals(6,chest.camos().size()); assertEquals(8,Armors.ALL.size());
    }
}
