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
import techguns.modern.armor.*;

/** Living source GenericNPC; always a fueled Chainsaw and one matching T1 Miner camouflage. */
public final class PsychoSteve extends ArmedNpc {
    public PsychoSteve(EntityType<? extends PsychoSteve> type,Level level) { super(type,level); xpReward=25; }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,75).add(Attributes.MOVEMENT_SPEED,.6)
                .add(Attributes.ATTACK_DAMAGE,7).add(Attributes.FOLLOW_RANGE,60).add(Attributes.ARMOR,5).add(Attributes.ARMOR_TOUGHNESS,1);
    }
    @Override public float armorAgainst(DamageKind kind) { return ArmorMath.defaultArmor(kind,5,false); }
    public void equipCamo(int camo) {
        if(camo<0 || camo>=Armors.T1_MINER.getFirst().camos().size()) throw new IllegalArgumentException("Invalid source miner camo");
        equip("chainsaw");
        for(var slot:ArmorSlot.values()) {
            var part=ArmorContent.T1_MINER.get(slot).toStack(); TGArmorItem.setCamo(part,camo);
            setItemSlot(EquipmentSlot.valueOf(slot.name()),part);
        }
    }
    public void equipForSpawn() { equipCamo(random.nextInt(Armors.T1_MINER.getFirst().camos().size())); }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,DifficultyInstance difficulty,EntitySpawnReason reason,SpawnGroupData data) {
        var result=super.finalizeSpawn(level,difficulty,reason,data); equipForSpawn(); setCanPickUpLoot(false); return result;
    }
    @Override public SoundEvent getAmbientSound() { return SoundEvents.VILLAGER_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.VILLAGER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }
    @Override protected void playStepSound(BlockPos pos,BlockState state) { playSound(SoundEvents.ZOMBIE_STEP,.15f,1); }
}
