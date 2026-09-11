package techguns.modern.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import techguns.core.SuperMutantRules;
import techguns.core.Weapons;
import techguns.modern.GunItem;
import techguns.modern.TGContent;

/** Original SuperMutantBasic. Spawn tables intentionally remain unchanged: the original has no natural entry. */
public final class SuperMutant extends Monster {
    public SuperMutant(EntityType<? extends SuperMutant> type, Level level) { super(type, level); setCanPickUpLoot(false); }
    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 35).add(Attributes.MOVEMENT_SPEED, .3)
                .add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.FOLLOW_RANGE, 50)
                .add(Attributes.ARMOR, 7).add(Attributes.ARMOR_TOUGHNESS, 1);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(4, new NpcRangedGoal(this));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.2, false) {
            @Override public boolean canUse() { return !armed() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !armed() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(5, new RandomStrollGoal(this, 1));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override public boolean canUse() { return !(getLastHurtByMob() instanceof SuperMutant) && super.canUse(); }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    public boolean armed() { return getMainHandItem().getItem() instanceof GunItem; }
    public void equipRoll(int roll) {
        String id = SuperMutantRules.weapon(roll);
        var weapon = TGContent.GUNS.get(id).toStack();
        weapon.set(TGContent.ROUNDS.get(), Weapons.definition(id).stats().capacity());
        setItemSlot(EquipmentSlot.MAINHAND, weapon);
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data) {
        var result = super.finalizeSpawn(level, difficulty, reason, data);
        equipRoll(random.nextInt(5)); setCanPickUpLoot(false);
        return result;
    }
    public boolean fireAt(LivingEntity target) { return NpcCombat.fire(this, target); }
    @Override public SoundEvent getAmbientSound() { return NpcContent.IDLE.get(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return NpcContent.HURT.get(); }
    @Override protected SoundEvent getDeathSound() { return NpcContent.DEATH.get(); }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(NpcContent.STEP.get(), .15f, 1); }
}
