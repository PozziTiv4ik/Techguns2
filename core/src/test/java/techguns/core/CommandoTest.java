package techguns.core;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandoTest {
    @Test void sourceMaterialKeepsSeparateDamageKindsAndFractionalPieces() {
        assertEquals(4,Armors.T2_COMMANDO.size());
        var kinds=List.of(DamageKind.PHYSICAL,DamageKind.ENERGY,DamageKind.EXPLOSION,DamageKind.POISON,DamageKind.DARK,DamageKind.RADIATION);
        double[] totals={18,16,16,10,13.5,5};
        for(int i=0;i<kinds.size();i++) { var kind=kinds.get(i); assertEquals(totals[i],Armors.T2_COMMANDO.stream().mapToDouble(a->a.armor(kind)).sum(),.00001); }
        assertEquals(5.4f,Armors.forSlot("t2_commando",ArmorSlot.CHEST).physical());
        for(var a:Armors.T2_COMMANDO) { assertEquals(990,a.durability()); assertEquals(1,a.toughness()); assertFalse(a.canChangeCamo()); assertEquals(List.of("t2_commando"),a.camos()); }
    }
    @Test void newBonusesAreSourceValuesAndOldSetsDoNotAcquireThem() {
        for(var a:Armors.ALL) {
            boolean commando=a.set().equals("t2_commando"); assertEquals(commando?1.25:0,a.waterMining()); assertEquals(commando?.05:0,a.gunAccuracy());
            assertEquals(commando && a.slot()==ArmorSlot.HEAD?1:0,a.oxygenGear());
        }
    }
    @Test void wornBoundaryRetainsProtectionButDisablesAllEquipmentBonuses() {
        for(var a:Armors.T2_COMMANDO) { assertTrue(a.bonusesActive(988)); assertFalse(a.bonusesActive(989)); assertEquals(0,a.displayedArmor(989)); assertEquals(1,a.specialWearLimit(988,10)); }
        assertEquals(.72,Armors.T2_COMMANDO.stream().mapToDouble(a->a.absorption(DamageKind.PROJECTILE,.5f)).sum(),.00001);
        assertEquals(1,Armors.T2_COMMANDO.stream().mapToDouble(ArmorSpec::radiationResistance).sum());
    }
    @Test void sourceRepairUsesObsidianSteelAndRubberWithIndependentRounding() {
        int[][] expected={{1,1},{2,2},{1,2},{1,1}};
        for(var slot:ArmorSlot.values()) { var a=Armors.forSlot("t2_commando",slot); assertArrayEquals(expected[slot.ordinal()],a.repairBenchCosts(989)); assertEquals("ingotobsidiansteel",a.repairMetal()); assertEquals("rubberbar",a.repairCloth()); }
        var legs=Armors.forSlot("t2_commando",ArmorSlot.LEGS);
        assertArrayEquals(new int[]{1,0},legs.repairBenchCosts(329)); assertArrayEquals(new int[]{1,1},legs.repairBenchCosts(330));
        assertArrayEquals(new int[]{1,1},legs.repairBenchCosts(659)); assertArrayEquals(new int[]{1,2},legs.repairBenchCosts(660));
    }
    @Test void healthyAndWornRecyclingKeepOriginalExtraHealthyPart() {
        int[][] healthy={{2,1},{3,2},{2,2},{2,1}},used={{1,1},{2,2},{1,2},{1,1}};
        for(var slot:ArmorSlot.values()) { var a=Armors.forSlot("t2_commando",slot); assertArrayEquals(healthy[slot.ordinal()],GrinderRules.armorSalvage(a,0)); assertArrayEquals(used[slot.ordinal()],GrinderRules.armorSalvage(a,1)); assertArrayEquals(new int[]{1,0},GrinderRules.armorSalvage(a,989)); }
    }
    @Test void movementAndBootEffectsAreIndependentFromWaterMining() {
        assertEquals(.4,Armors.T2_COMMANDO.stream().mapToDouble(ArmorSpec::speed).sum(),.00001);
        assertEquals(.4,Armors.T2_COMMANDO.stream().mapToDouble(ArmorSpec::knockback).sum(),.00001);
        var boots=Armors.forSlot("t2_commando",ArmorSlot.FEET); assertEquals(.1,boots.jump()); assertEquals(.2,boots.fallReduction()); assertEquals(1,boots.freeFallHeight());
        assertEquals(0,Armors.T2_COMMANDO.stream().mapToDouble(ArmorSpec::mining).sum());
    }
}
