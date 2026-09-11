package techguns.modern.npc;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.*;
import techguns.core.SuperMutantRules;

/** Original SuperMutantBasic. Spawn tables intentionally remain unchanged: the original has no natural entry. */
public final class SuperMutant extends ArmedNpc {
    public SuperMutant(EntityType<? extends SuperMutant> type, Level level) { super(type, level); setCanPickUpLoot(false); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 35).add(Attributes.MOVEMENT_SPEED, .3)
                .add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.FOLLOW_RANGE, 50)
                .add(Attributes.ARMOR, 7).add(Attributes.ARMOR_TOUGHNESS, 1);
    }
    public void equipRoll(int roll) { equip(SuperMutantRules.weapon(roll)); }
    @Override public float armorAgainst(techguns.core.DamageKind kind) { return SuperMutantRules.armor(kind); }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        var result = super.finalizeSpawn(level, difficulty, reason, data);
        equipRoll(random.nextInt(5)); setCanPickUpLoot(false);
        return result;
    }
}
