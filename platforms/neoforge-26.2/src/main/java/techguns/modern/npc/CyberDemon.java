package techguns.modern.npc;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.*;
import techguns.core.ArmorMath;
import techguns.core.DamageKind;

/** GenericNPCUndead: undead effects and fire immunity, without Zombie-specific AI or sunlight burning. */
public final class CyberDemon extends ArmedNpc {
    public CyberDemon(EntityType<? extends CyberDemon> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, .35)
                .add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.FOLLOW_RANGE, 50)
                .add(Attributes.ARMOR, 10).add(Attributes.ARMOR_TOUGHNESS, 1);
    }
    public void equipBlaster() { equip("netherblaster"); }
    @Override public float armorAgainst(DamageKind kind) { return ArmorMath.defaultArmor(kind, 10, true); }
    @Override public double bulletSideOffset() { return .3; }
    @Override public double bulletHeightOffset() { return -.59; }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        var result = super.finalizeSpawn(level, difficulty, reason, data);
        equipBlaster(); setCanPickUpLoot(false);
        return result;
    }
}
