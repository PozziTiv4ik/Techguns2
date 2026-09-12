package techguns.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class T1MinerTest {
    @Test void sourceMaterialHasThirteenPhysicalAndNoToughness() {
        assertEquals(13,Armors.T1_MINER.stream().mapToDouble(ArmorSpec::physical).sum(),.00001);
        for(var kind:List.of(DamageKind.FIRE,DamageKind.ENERGY,DamageKind.EXPLOSION,DamageKind.POISON,DamageKind.DARK,DamageKind.ICE,DamageKind.LIGHTNING))
            assertEquals(9.75,Armors.T1_MINER.stream().mapToDouble(a -> a.armor(kind)).sum(),.00001);
        for(var spec:Armors.T1_MINER) {
            assertEquals(825,spec.durability()); assertEquals(0,spec.toughness());
            assertEquals(0,spec.armor(DamageKind.RADIATION)); assertEquals(0,spec.armor(DamageKind.UNRESISTABLE));
            assertEquals(0,spec.radiationResistance()); assertEquals(0,spec.knockback());
        }
    }
    @Test void originalMiningAndMovementBonusesRemainPerPart() {
        for(var spec:Armors.T1_MINER) { assertEquals(.05,spec.mining()); assertEquals(.08,spec.speed()); }
        assertEquals(.2,Armors.T1_MINER.stream().mapToDouble(ArmorSpec::mining).sum(),.00001);
        var boots=Armors.forSlot("t1_miner",ArmorSlot.FEET);
        assertEquals(.1,boots.jump()); assertEquals(.2,boots.fallReduction()); assertEquals(1,boots.freeFallHeight());
        for(var spec:Armors.T1_MINER) if(spec.slot()!=ArmorSlot.FEET) { assertEquals(0,spec.jump()); assertEquals(0,spec.fallReduction()); }
    }
    @Test void helmetAndSuitHaveDifferentColorNamesAtTheSameIndex() {
        for(var spec:Armors.T1_MINER) {
            assertEquals(List.of("t1_miner","t1_miner_red","t1_miner_green","t1_miner_black"),spec.camos()); assertTrue(spec.canChangeCamo());
            assertEquals("tooltip.techguns.armor.t1_miner."+(spec.slot()==ArmorSlot.HEAD?"helmet.":"")+"camo.2",spec.camoTranslationKey(2));
        }
        assertEquals("tooltip.techguns.armor.hazmat.camo.2",Armors.HAZMAT.getFirst().camoTranslationKey(2));
        assertEquals("tooltip.techguns.armor.t2_combat.camo.2",Armors.T2_COMBAT.getFirst().camoTranslationKey(2));
    }
    @Test void wornGearLosesBonusesAndDisplayButKeepsAbsorption() {
        assertEquals(13,Armors.T1_MINER.stream().mapToInt(a -> a.displayedArmor(0)).sum());
        for(var spec:Armors.T1_MINER) {
            assertTrue(spec.bonusesActive(823)); assertFalse(spec.bonusesActive(824));
            assertEquals(0,spec.displayedArmor(824)); assertEquals(1,spec.specialWearLimit(823,100)); assertEquals(0,spec.specialWearLimit(824,100));
        }
        assertEquals(.52,Armors.T1_MINER.stream().mapToDouble(a -> a.absorption(DamageKind.PHYSICAL,0)).sum(),.00001);
        assertEquals(.44,Armors.T1_MINER.stream().mapToDouble(a -> a.absorption(DamageKind.PROJECTILE,.5f)).sum(),.00001);
    }
    @Test void sourceRepairAndGrinderRoundingKeepIronIngots() {
        int[][] full={{1,1},{2,2},{1,1},{1,1}}, healthy={{2,1},{3,2},{1,2},{2,1}};
        for(int i=0;i<4;i++) {
            var spec=Armors.T1_MINER.get(i); assertEquals("minecraft:iron_ingot",spec.repairMetal()); assertEquals("heavycloth",spec.repairCloth());
            assertArrayEquals(full[i],spec.repairBenchCosts(824)); assertArrayEquals(new int[]{1,0},spec.repairBenchCosts(1));
            assertArrayEquals(healthy[i],GrinderRules.armorSalvage(spec,0)); assertArrayEquals(full[i],GrinderRules.armorSalvage(spec,1));
            assertArrayEquals(new int[]{1,0},GrinderRules.armorSalvage(spec,824));
        }
    }
    @Test void otherSetsDoNotAcquireMiningOrHelmetColorSuffixes() {
        for(var spec:Armors.ALL) if(!spec.set().equals("t1_miner")) { assertEquals(0,spec.mining()); assertEquals("",spec.camoNameSuffix()); }
    }
}
