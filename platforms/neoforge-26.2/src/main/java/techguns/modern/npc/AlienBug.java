package techguns.modern.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import techguns.core.DamageKind;
import techguns.modern.npc.spawner.*;

/** Original Spider-derived AlienBug, including wall climbing and daylight target acquisition. */
public final class AlienBug extends Spider implements NpcTypedArmor,HostileNpc,SpawnerLinked {
    private final SpawnerLifecycle spawner=new SpawnerLifecycle();
    private int attackTimer;
    public AlienBug(EntityType<? extends AlienBug> type,Level level) { super(type,level); }
    public static AttributeSupplier.Builder attributes() {
        return Spider.createAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,1).add(Attributes.ATTACK_DAMAGE,4);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(1,new FloatGoal(this));
        goalSelector.addGoal(3,new LeapAtTargetGoal(this,.4f));
        goalSelector.addGoal(4,new AttackGoal(this));
        goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,.8));
        goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,8));
        goalSelector.addGoal(6,new RandomLookAroundGoal(this));
        // AlienBug uses vanilla retaliation, unlike GenericNPC's faction-aware goal.
        targetSelector.addGoal(1,new HurtByTargetGoal(this));
        targetSelector.addGoal(2,new TargetGoal<>(this,Player.class));
        targetSelector.addGoal(3,new TargetGoal<>(this,IronGolem.class));
    }
    public static final class AttackGoal extends MeleeAttackGoal {
        public AttackGoal(AlienBug mob) { super(mob,1,true); }
        @Override public boolean canContinueToUse() {
            if(mob.getLightLevelDependentMagicValue()>=.5f && mob.getRandom().nextInt(100)==0) { mob.setTarget(null); return false; }
            return super.canContinueToUse();
        }
        @Override protected boolean canPerformAttack(LivingEntity target) {
            // 1.12.2 measured distance to the feet and did not require line of sight for a bite.
            return isTimeToAttack() && mob.distanceToSqr(target.getX(),target.getBoundingBox().minY,target.getZ())<=4.0f+target.getBbWidth();
        }
    }
    public static final class TargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        public TargetGoal(AlienBug mob,Class<T> type) { super(mob,type,true); }
        @Override public void start() { super.start(); mob.playSound(NpcContent.BUG_AGGRO.get(),1,1); }
    }
    @Override public boolean doHurtTarget(ServerLevel level,Entity target) {
        boolean result=super.doHurtTarget(level,target); attackTimer=10;
        playSound(NpcContent.BUG_BITE.get(),2,1); level.broadcastEntityEvent(this,(byte)4); return result;
    }
    public int attackTimer() { return attackTimer; }
    @Override public boolean isWithinMeleeAttackRange(LivingEntity target) {
        return distanceToSqr(target.getX(),target.getBoundingBox().minY,target.getZ())<=4.0f+target.getBbWidth();
    }
    @Override public void handleEntityEvent(byte event) { if(event==4) attackTimer=10; else super.handleEntityEvent(event); }
    @Override public float armorAgainst(DamageKind kind) {
        return switch(kind) { case PHYSICAL,PROJECTILE->10; case ENERGY,EXPLOSION,ICE,LIGHTNING->5; case POISON,RADIATION->20; default->0; };
    }
    @Override public SoundEvent getAmbientSound() { return NpcContent.BUG_IDLE.get(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return NpcContent.BUG_HURT.get(); }
    @Override protected SoundEvent getDeathSound() { return NpcContent.BUG_DEATH.get(); }
    @Override protected void playStepSound(BlockPos pos,BlockState state) { playSound(NpcContent.BUG_STEP.get(),.15f,1); }
    @Override public SpawnerLifecycle spawnerLifecycle() { return spawner; }
    @Override public void tick() { spawner.tick(this); super.tick(); if(attackTimer>0) attackTimer--; }
    @Override public void die(DamageSource source) { super.die(source); if(dead) spawner.notifyOwner(this,true); }
    @Override public void remove(RemovalReason reason) { spawner.remove(this,reason); super.remove(reason); }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); spawner.save(out); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); spawner.load(this,in); }
}
