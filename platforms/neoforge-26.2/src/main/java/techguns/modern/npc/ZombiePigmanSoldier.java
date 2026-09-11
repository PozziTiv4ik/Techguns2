package techguns.modern.npc;

import net.minecraft.sounds.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import techguns.core.*;
import techguns.modern.armor.*;

public final class ZombiePigmanSoldier extends ArmedNpc {
    public ZombiePigmanSoldier(EntityType<? extends ZombiePigmanSoldier> type,Level level) { super(type,level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,25).add(Attributes.MOVEMENT_SPEED,.3)
                .add(Attributes.ATTACK_DAMAGE,5).add(Attributes.FOLLOW_RANGE,50).add(Attributes.ARMOR,10);
    }
    @Override public float armorAgainst(DamageKind kind) { return ArmorMath.defaultArmor(kind,10,true); }
    public void equipRoll(int weapon,double chest,double legs,double boots) {
        equip(PigmanRules.weapon(weapon));
        for(var slot:ArmorSlot.values()) {
            double roll=switch(slot) { case HEAD -> 0; case CHEST -> chest; case LEGS -> legs; case FEET -> boots; };
            var stack=PigmanRules.armor(slot,roll)?ArmorContent.ITEMS.get(slot).toStack():net.minecraft.world.item.ItemStack.EMPTY;
            if(!stack.isEmpty()) T2ArmorItem.setCamo(stack,3);
            setItemSlot(EquipmentSlot.valueOf(slot.name()),stack);
        }
    }
    public void equipForSpawn() { equipRoll(random.nextInt(9),random.nextDouble(),random.nextDouble(),random.nextDouble()); }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,EntitySpawnReason reason,SpawnGroupData data) {
        var result=super.finalizeSpawn(level,difficulty,reason,data); equipForSpawn(); setCanPickUpLoot(false); return result;
    }
    @Override public SoundEvent getAmbientSound() { return SoundEvents.ZOMBIFIED_PIGLIN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ZOMBIFIED_PIGLIN_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ZOMBIFIED_PIGLIN_DEATH; }
    @Override protected void playStepSound(BlockPos pos,BlockState state) { playSound(SoundEvents.ZOMBIE_STEP,.15f,1); }
}
