package techguns.modern.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.*;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import techguns.core.*;
import techguns.modern.armor.ArmorContent;

/** Source Commando: living GenericNPC, fixed Infiltrator and complete T2 Commando suit. */
public final class Commando extends ArmedNpc {
    public Commando(EntityType<? extends Commando> type,Level level) { super(type,level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.MOVEMENT_SPEED,.35)
                .add(Attributes.ATTACK_DAMAGE,5).add(Attributes.FOLLOW_RANGE,75).add(Attributes.ARMOR,8).add(Attributes.ARMOR_TOUGHNESS,0);
    }
    @Override public float armorAgainst(DamageKind kind) { return ArmorMath.defaultArmor(kind,8,false); }
    public void equipForSpawn() {
        equip("m4_infiltrator");
        for(var slot:ArmorSlot.values()) setItemSlot(EquipmentSlot.valueOf(slot.name()),ArmorContent.COMMANDO.get(slot).toStack());
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,EntitySpawnReason reason,SpawnGroupData data) {
        var result=super.finalizeSpawn(level,difficulty,reason,data); equipForSpawn(); setCanPickUpLoot(false); return result;
    }
    @Override public SoundEvent getAmbientSound() { return SoundEvents.VILLAGER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.VILLAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }
    @Override protected void playStepSound(BlockPos pos,BlockState state) { playSound(SoundEvents.ZOMBIE_VILLAGER_STEP,.15f,1); }
}
