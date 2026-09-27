package techguns.modern.npc;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import techguns.core.*;
import techguns.modern.npc.spawner.*;

/** Original GenericFlyingMob, not a ground GenericNPC and not a member of its HOSTILE faction. */
public final class AttackHelicopter extends Mob implements Enemy,NpcTypedArmor,NpcMuzzle,SpawnerLinked {
    private static final EntityDataAccessor<Boolean> ATTACKING=SynchedEntityData.defineId(AttackHelicopter.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DEATH_PROGRESS=SynchedEntityData.defineId(AttackHelicopter.class,EntityDataSerializers.INT);
    private final SpawnerLifecycle spawner=new SpawnerLifecycle();
    private HelicopterAttackGoal attack;
    private int rotorDelay;
    private boolean pendingExperience;
    private UUID experiencePlayer,savedTarget;
    public AttackHelicopter(EntityType<? extends AttackHelicopter> type,Level level) {
        super(type,level); xpReward=5; moveControl=new HelicopterFlight.Move(this); lookControl=new HelicopterFlight.LegacyLook(this);
    }
    public static AttributeSupplier.Builder attributes() { return createMobAttributes().add(Attributes.MAX_HEALTH,100).add(Attributes.FOLLOW_RANGE,128).add(Attributes.ARMOR,16).add(Attributes.ARMOR_TOUGHNESS,5); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(5,new HelicopterFlight.RandomFly(this)); goalSelector.addGoal(7,new HelicopterFlight.Look(this));
        attack=new HelicopterAttackGoal(this); goalSelector.addGoal(7,attack); targetSelector.addGoal(1,new HelicopterFlight.Target(this));
    }
    public HelicopterAttackGoal attackGoal() { return attack; }
    public boolean isAttacking() { return entityData.get(ATTACKING); }
    public void setAttacking(boolean value) { entityData.set(ATTACKING,value); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(ATTACKING,false); builder.define(DEATH_PROGRESS,0); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if(DEATH_PROGRESS.equals(key)) deathTime=Math.max(deathTime,entityData.get(DEATH_PROGRESS));
    }
    @Override public SpawnerLifecycle spawnerLifecycle() { return spawner; }
    @Override public void tick() {
        if(!level().isClientSide() && level().getDifficulty()==Difficulty.PEACEFUL) { discard(); return; }
        if(savedTarget!=null && level() instanceof ServerLevel server) {
            var entity=server.getEntity(savedTarget); if(entity instanceof LivingEntity living && living.isAlive() && distanceToSqr(living)<=128*128) setTarget(living); savedTarget=null;
        }
        spawner.tick(this); super.tick();
        if(!level().isClientSide() && isAlive() && --rotorDelay<=0) { playSound(NpcContent.HELI_ROTOR.get(),6,1); rotorDelay=61; }
    }
    @Override public void travel(Vec3 input) { travelFlying(input,.02f); }
    @Override protected void checkFallDamage(double ya,boolean onGround,BlockState state,BlockPos pos) {}
    @Override public boolean onClimbable() { return false; }
    @Override public int getMaxSpawnClusterSize() { return 1; }
    @Override public boolean checkSpawnRules(LevelAccessor level,EntitySpawnReason reason) { return level.getDifficulty()!=Difficulty.PEACEFUL && random.nextInt(20)==0 && super.checkSpawnRules(level,reason); }
    @Override public float armorAgainst(DamageKind kind) { return HelicopterAttack.armor(kind); }
    @Override public double bulletSideOffset() { return 2; }
    @Override public SoundSource getSoundSource() { return SoundSource.HOSTILE; }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return NpcContent.HELI_HIT.get(); }
    @Override protected SoundEvent getDeathSound() { return NpcContent.HELI_HIT.get(); }
    @Override public float getSoundVolume() { return 10; }
    @Override public void die(DamageSource source) { super.die(source); if(dead) { setAttacking(false); spawner.notifyOwner(this,true); } }
    @Override public void remove(RemovalReason reason) { spawner.remove(this,reason); super.remove(reason); }
    @Override protected void dropExperience(ServerLevel level,Entity killer) {
        pendingExperience=!wasExperienceConsumed() && lastHurtByPlayerMemoryTime>0 && shouldDropExperience();
        var player=getLastHurtByPlayer(); experiencePlayer=player==null?null:player.getUUID();
    }
    @Override protected void tickDeath() {
        if(deathTime==0 && !level().isClientSide()) playSound(NpcContent.HELI_DEATH.get(),6,1);
        ++deathTime;
        if(!level().isClientSide()) entityData.set(DEATH_PROGRESS,Math.min(deathTime,100));
        if(deathTime>=100 && level() instanceof ServerLevel server && !isRemoved()) {
            if(pendingExperience && lastHurtByPlayerMemoryTime>0 && shouldDropExperience() && !wasExperienceConsumed() && server.getGameRules().get(GameRules.MOB_DROPS)) {
                var player=experiencePlayer==null?null:server.getPlayerByUUID(experiencePlayer);
                ExperienceOrb.award(server,position(),EventHooks.getExperienceDrop(this,player,getExperienceReward(server,player))); pendingExperience=false;
            }
            playSound(NpcContent.HELI_EXPLODE.get(),6,1);
            // The original death explosion is FX only; unlike its combat rocket it never hurts terrain/entities.
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,getX(),getY()-8,getZ(),1,0,0,0,0);
            server.sendParticles(ParticleTypes.LARGE_SMOKE,getX(),getY()-8,getZ(),40,2,2,2,.08);
            remove(RemovalReason.KILLED);
        }
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out); spawner.save(out); attack.save(out); ((HelicopterFlight.Move)moveControl).save(out);
        out.putInt("rotor_delay",rotorDelay); out.putBoolean("pending_experience",pendingExperience);
        if(pendingExperience) out.putInt("experience_credit_ticks",lastHurtByPlayerMemoryTime);
        if(experiencePlayer!=null) out.store("experience_player",UUIDUtil.CODEC,experiencePlayer);
        if(getTarget()!=null) out.store("attack_target",UUIDUtil.CODEC,getTarget().getUUID());
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in); spawner.load(this,in); attack.load(in); ((HelicopterFlight.Move)moveControl).load(in);
        rotorDelay=Math.clamp(in.getIntOr("rotor_delay",0),0,61); pendingExperience=in.getBooleanOr("pending_experience",false);
        if(pendingExperience) lastHurtByPlayerMemoryTime=Math.clamp(in.getIntOr("experience_credit_ticks",0),0,100);
        experiencePlayer=in.read("experience_player",UUIDUtil.CODEC).orElse(null); savedTarget=in.read("attack_target",UUIDUtil.CODEC).orElse(null);
        deathTime=Math.clamp(deathTime,0,100); entityData.set(DEATH_PROGRESS,deathTime); setAttacking(isAlive() && attack.timer()>10);
    }
}
