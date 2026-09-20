package techguns.modern.npc;

import net.minecraft.network.syncher.*;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import techguns.core.DamageKind;

/** Original ground-based EntityMob, separate from GenericNPC gun AI and faction membership. */
public final class Ghastling extends SpawnerNpc implements NpcTypedArmor {
    private static final EntityDataAccessor<Boolean> ATTACKING=SynchedEntityData.defineId(Ghastling.class,EntityDataSerializers.BOOLEAN);
    public Ghastling(EntityType<? extends Ghastling> type,Level level) {
        super(type,level); xpReward=10;
        setPathfindingMalus(PathType.WATER,-1); setPathfindingMalus(PathType.LAVA,8);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR,0); setPathfindingMalus(PathType.FIRE,0);
    }
    public static AttributeSupplier.Builder attributes() {
        return createMonsterAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.7)
                .add(Attributes.ATTACK_DAMAGE,2).add(Attributes.FOLLOW_RANGE,64).add(Attributes.ARMOR,10).add(Attributes.ARMOR_TOUGHNESS,0);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(ATTACKING,false); }
    public boolean isAttacking() { return entityData.get(ATTACKING); }
    public void setAttacking(boolean value) { entityData.set(ATTACKING,value); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(4,new GhastlingAttackGoal(this));
        goalSelector.addGoal(5,new MoveTowardsRestrictionGoal(this,1));
        goalSelector.addGoal(7,new WaterAvoidingRandomStrollGoal(this,1,0));
        goalSelector.addGoal(8,new LookAtPlayerGoal(this,Player.class,8));
        goalSelector.addGoal(8,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));
    }
    // The original deliberately returns zero despite its ARMOR attribute of ten.
    @Override public float armorAgainst(DamageKind kind) { return 0; }
    @Override public SoundEvent getAmbientSound() { return SoundEvents.GHAST_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.GHAST_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.GHAST_DEATH; }
    @Override public float getSoundVolume() { return 10; }
}
