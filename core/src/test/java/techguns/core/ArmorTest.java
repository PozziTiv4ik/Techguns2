package techguns.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmorTest {
    @Test void fractionalMaterialStatsAndDurabilitySurvive() {
        assertEquals(18,Armors.ALL.stream().mapToDouble(a -> a.armor(DamageKind.PHYSICAL)).sum(),.0001);
        assertEquals(13.5,Armors.ALL.stream().mapToDouble(a -> a.armor(DamageKind.POISON)).sum(),.0001);
        assertEquals(0,Armors.ALL.stream().mapToDouble(a -> a.armor(DamageKind.RADIATION)).sum());
        for(var armor:Armors.ALL) { assertEquals(990,armor.durability()); assertEquals(1,armor.toughness()); }
        assertEquals(5.4f,Armors.forSlot(ArmorSlot.CHEST).physical());
    }
    @Test void specialArmorUsesRawPenetrationPerPiece() {
        var helmet=Armors.forSlot(ArmorSlot.HEAD);
        assertEquals(.18,helmet.absorption(DamageKind.PROJECTILE,.5f),.00001);
        assertEquals(.14,helmet.absorption(DamageKind.PROJECTILE,2),.00001);
        assertEquals(0,helmet.absorption(DamageKind.PROJECTILE,10));
        assertEquals(.54,Armors.ALL.stream().mapToDouble(a -> a.absorption(DamageKind.FIRE,0)).sum(),.00001);
    }
    @Test void wearDisablesBonusesAndDisplayButNotTheSourceAbsorbRatio() {
        var helmet=Armors.forSlot(ArmorSlot.HEAD);
        assertTrue(helmet.bonusesActive(988)); assertFalse(helmet.bonusesActive(989));
        assertEquals(5,helmet.displayedArmor(988)); assertEquals(0,helmet.displayedArmor(989));
        assertEquals(4,helmet.specialWearLimit(985,10)); assertEquals(0,helmet.specialWearLimit(989,10));
        assertEquals(.18,helmet.absorption(DamageKind.PHYSICAL,0),.00001);
    }
    @Test void repairBenchMetadataPreservesOriginalRatios() {
        assertArrayEquals(new int[]{1,1},Armors.forSlot(ArmorSlot.HEAD).repairBenchCosts(989));
        assertArrayEquals(new int[]{2,2},Armors.forSlot(ArmorSlot.CHEST).repairBenchCosts(989));
        assertArrayEquals(new int[]{1,2},Armors.forSlot(ArmorSlot.LEGS).repairBenchCosts(989));
        assertArrayEquals(new int[]{0,0},Armors.forSlot(ArmorSlot.FEET).repairBenchCosts(0));
        assertEquals("t2_combat_arctic",Armors.CAMOS.get(3)); assertEquals(6,Armors.CAMOS.size());
    }
    @Test void pigmanWeaponCasesAfterEightAreUnreachable() {
        assertEquals(List.of("thompson","thompson","thompson","revolver","revolver","ak47","ak47","pistol","pistol"),
                java.util.stream.IntStream.range(0,9).mapToObj(PigmanRules::weapon).toList());
        assertThrows(IllegalArgumentException.class,() -> PigmanRules.weapon(9));
    }
    @Test void helmetIsUnconditionalAndOtherPiecesHaveInclusiveHalfChance() {
        assertTrue(PigmanRules.armor(ArmorSlot.HEAD,.99));
        for(var slot:List.of(ArmorSlot.CHEST,ArmorSlot.LEGS,ArmorSlot.FEET)) {
            assertTrue(PigmanRules.armor(slot,.5)); assertFalse(PigmanRules.armor(slot,.5001));
        }
    }
}
