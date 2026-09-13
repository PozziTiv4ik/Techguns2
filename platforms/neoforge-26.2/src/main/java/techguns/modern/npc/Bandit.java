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

/** Living GenericNPC, with Bandit's original armor and all six reachable gun choices. */
public final class Bandit extends ArmedNpc {
    public Bandit(EntityType<? extends Bandit> type,Level level) { super(type,level); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.3)
                .add(Attributes.ATTACK_DAMAGE,5).add(Attributes.FOLLOW_RANGE,40).add(Attributes.ARMOR,5);
    }
    @Override public float armorAgainst(DamageKind kind) { return ArmorMath.defaultArmor(kind,5,false); }
    public void equipRoll(int weapon,double helmet) {
        equip(BanditRules.weapon(weapon));
        for(var slot:ArmorSlot.values()) setItemSlot(EquipmentSlot.valueOf(slot.name()),
                slot!=ArmorSlot.HEAD || BanditRules.helmet(helmet)?ArmorContent.T1_SCOUT.get(slot).toStack():ItemStack.EMPTY);
    }
    public void equipForSpawn() { equipRoll(random.nextInt(6),random.nextDouble()); }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,EntitySpawnReason reason,SpawnGroupData data) {
        var result=super.finalizeSpawn(level,difficulty,reason,data); equipForSpawn(); setCanPickUpLoot(false); return result;
    }
    @Override public SoundEvent getAmbientSound() { return SoundEvents.VILLAGER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.VILLAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }
    @Override protected void playStepSound(BlockPos pos,BlockState state) { state.playStepSound(level(),pos,this,.15f,1); }
}
