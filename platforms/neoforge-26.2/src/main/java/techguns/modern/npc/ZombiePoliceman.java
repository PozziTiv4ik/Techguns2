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
import techguns.modern.armor.*;

/** Source GenericNPCUndead: two handguns, independent T2 armor rolls and police camo five. */
public final class ZombiePoliceman extends BurningUndeadNpc {
    public ZombiePoliceman(EntityType<? extends ZombiePoliceman> type,Level level) { super(type,level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,25).add(Attributes.MOVEMENT_SPEED,.25)
                .add(Attributes.ATTACK_DAMAGE,4).add(Attributes.FOLLOW_RANGE,50).add(Attributes.ARMOR,5).add(Attributes.ARMOR_TOUGHNESS,0);
    }
    @Override public float armorAgainst(DamageKind kind) { return ArmorMath.defaultArmor(kind,5,false); }
    public void equipRoll(int weapon,double helmet,double chest,double legs,double boots) {
        if(weapon<0 || weapon>1) throw new IllegalArgumentException("Police weapon roll outside nextInt(2)");
        equip(weapon==0?"revolver":"pistol");
        for(var slot:ArmorSlot.values()) {
            double roll=switch(slot) { case HEAD->helmet; case CHEST->chest; case LEGS->legs; case FEET->boots; };
            var stack=roll<=.5?ArmorContent.ITEMS.get(slot).toStack():ItemStack.EMPTY;
            if(!stack.isEmpty()) TGArmorItem.setCamo(stack,5);
            setItemSlot(EquipmentSlot.valueOf(slot.name()),stack);
        }
    }
    public void equipForSpawn() { equipRoll(random.nextInt(2),random.nextDouble(),random.nextDouble(),random.nextDouble(),random.nextDouble()); }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,EntitySpawnReason reason,SpawnGroupData data) {
        var result=super.finalizeSpawn(level,difficulty,reason,data); equipForSpawn(); setCanPickUpLoot(false); return result;
    }
    @Override public SoundEvent getAmbientSound() { return SoundEvents.ZOMBIE_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ZOMBIE_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ZOMBIE_DEATH; }
    @Override protected void playStepSound(BlockPos pos,BlockState state) { playSound(SoundEvents.ZOMBIE_STEP,.15f,1); }
}
