package techguns.modern.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.*;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import techguns.core.*;
import techguns.modern.armor.ArmorContent;

public final class SkeletonSoldier extends BurningUndeadNpc {
    public SkeletonSoldier(EntityType<? extends SkeletonSoldier> type,Level level) { super(type,level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,25).add(Attributes.MOVEMENT_SPEED,.25)
                .add(Attributes.ATTACK_DAMAGE,4).add(Attributes.FOLLOW_RANGE,50).add(Attributes.ARMOR,0);
    }
    @Override public float armorAgainst(DamageKind kind) { return 0; }
    public void equipRoll(int weapon,double helmet,double boots) {
        equip(SkeletonSoldierRules.weapon(weapon));
        setItemSlot(EquipmentSlot.HEAD,(SkeletonSoldierRules.scout(helmet)?ArmorContent.T1_SCOUT:ArmorContent.T1_COMBAT).get(ArmorSlot.HEAD).toStack());
        setItemSlot(EquipmentSlot.FEET,(SkeletonSoldierRules.scout(boots)?ArmorContent.T1_SCOUT:ArmorContent.T1_COMBAT).get(ArmorSlot.FEET).toStack());
        setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY); setItemSlot(EquipmentSlot.LEGS,ItemStack.EMPTY);
    }
    public void equipForSpawn() { equipRoll(random.nextInt(3),random.nextDouble(),random.nextDouble()); }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,EntitySpawnReason reason,SpawnGroupData data) {
        var result=super.finalizeSpawn(level,difficulty,reason,data); equipForSpawn(); setCanPickUpLoot(false); return result;
    }
    @Override public SoundEvent getAmbientSound() { return SoundEvents.SKELETON_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.SKELETON_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.SKELETON_DEATH; }
    @Override protected void playStepSound(BlockPos pos,BlockState state) { playSound(SoundEvents.SKELETON_STEP,.15f,1); }
}
