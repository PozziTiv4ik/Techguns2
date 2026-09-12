package techguns.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class T1ScoutTest {
    @Test void fractionalProtectionIsIndependentOfHudAndWear() {
        assertEquals(13,Armors.T1_SCOUT.stream().mapToDouble(ArmorSpec::physical).sum(),.00001);
        assertEquals(13,Armors.T1_SCOUT.stream().mapToInt(a -> a.displayedArmor(0)).sum());
        for(var kind:List.of(DamageKind.FIRE,DamageKind.ENERGY,DamageKind.EXPLOSION,DamageKind.POISON,DamageKind.DARK))
            assertEquals(9.75,Armors.T1_SCOUT.stream().mapToDouble(a -> a.armor(kind)).sum(),.00001);
        assertEquals(.44,Armors.T1_SCOUT.stream().mapToDouble(a -> a.absorption(DamageKind.PROJECTILE,.5f)).sum(),.00001);
        for(var a:Armors.T1_SCOUT) {
            assertEquals(825,a.durability()); assertEquals(0,a.toughness()); assertEquals(0,a.armor(DamageKind.RADIATION));
            assertTrue(a.bonusesActive(823)); assertFalse(a.bonusesActive(824)); assertEquals(0,a.displayedArmor(824));
            assertEquals(1,a.specialWearLimit(823,100)); assertEquals(0,a.specialWearLimit(824,100));
        }
    }
    @Test void scoutJumpComesFromEveryPartWhileFallProtectionComesFromBoots() {
        assertEquals(.5,Armors.T1_SCOUT.stream().mapToDouble(ArmorSpec::speed).sum(),.00001);
        assertEquals(.16,Armors.T1_SCOUT.stream().mapToDouble(ArmorSpec::jump).sum(),.00001);
        for(var a:Armors.T1_SCOUT) {
            assertEquals(.125,a.speed()); assertEquals(0,a.mining()); assertEquals(0,a.knockback()); assertEquals(0,a.radiationResistance());
            assertEquals(a.slot()==ArmorSlot.FEET?.1:.02,a.jump());
            assertEquals(a.slot()==ArmorSlot.FEET?.2:0,a.fallReduction()); assertEquals(a.slot()==ArmorSlot.FEET?1:0,a.freeFallHeight());
        }
    }
    @Test void clothOnlyRepairKeepsSourceRoundingBoundaries() {
        int[] costs={2,4,3,2};
        for(int i=0;i<4;i++) {
            var a=Armors.T1_SCOUT.get(i); assertEquals("",a.repairMetal()); assertEquals("heavycloth",a.repairCloth());
            assertArrayEquals(new int[]{0,0},a.repairBenchCosts(0)); assertArrayEquals(new int[]{0,1},a.repairBenchCosts(1));
            assertArrayEquals(new int[]{0,costs[i]},a.repairBenchCosts(824));
        }
        var chest=Armors.forSlot("t1_scout",ArmorSlot.CHEST);
        assertArrayEquals(new int[]{0,1},chest.repairBenchCosts(206)); assertArrayEquals(new int[]{0,2},chest.repairBenchCosts(207));
    }
    @Test void salvagePreservesExtraVirginPartWithoutExceedingCraftCost() {
        int[] healthy={3,5,4,3},used={2,4,3,2},craft={5,8,7,4};
        for(int i=0;i<4;i++) {
            var a=Armors.T1_SCOUT.get(i);
            assertArrayEquals(new int[]{0,healthy[i]},GrinderRules.armorSalvage(a,0));
            assertArrayEquals(new int[]{0,used[i]},GrinderRules.armorSalvage(a,1));
            assertArrayEquals(new int[]{0,1},GrinderRules.armorSalvage(a,824)); assertTrue(healthy[i]<craft[i]);
        }
    }
    @Test void allPiecesShareFourOriginalSkinsAndNames() {
        for(var a:Armors.T1_SCOUT) {
            assertEquals(List.of("t1_scout","t1_scout_forest","t1_scout_snow","t1_scout_black"),a.camos());
            assertTrue(a.canChangeCamo()); assertEquals("",a.camoNameSuffix());
            assertEquals("tooltip.techguns.armor.t1_scout.camo.3",a.camoTranslationKey(3));
        }
    }
}
