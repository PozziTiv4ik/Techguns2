package techguns.modern.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import techguns.core.DamageKind;
import techguns.core.Weapons;
import techguns.modern.GunItem;
import techguns.modern.TGContent;

/** Shared GenericNPC behavior for the currently ported HOSTILE-faction NPCs. */
public abstract class ArmedNpc extends Monster {
    private techguns.modern.npc.spawner.SpawnerLink spawnerLink;
    private boolean spawnerNotified;
    protected ArmedNpc(EntityType<? extends ArmedNpc> type, Level level) { super(type, level); setCanPickUpLoot(false); }
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
            @Override public boolean canUse() { return !(getLastHurtByMob() instanceof ArmedNpc) && super.canUse(); }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
    public boolean armed() { return getMainHandItem().getItem() instanceof GunItem; }
    protected void equip(String id) {
        var weapon = TGContent.GUNS.get(id).toStack();
        weapon.set(TGContent.ROUNDS.get(), Weapons.definition(id).stats().capacity());
        setItemSlot(EquipmentSlot.MAINHAND, weapon);
    }
    public boolean fireAt(LivingEntity target) { return NpcCombat.fire(this, target); }
    public techguns.modern.npc.spawner.SpawnerLink spawnerLink() { return spawnerLink; }
    public boolean hasSpawnerOrigin() { return spawnerLink!=null; }
    public void bindSpawner(techguns.modern.npc.spawner.SpawnerLink link) { spawnerLink=link; spawnerNotified=false; }
    private void notifySpawner(boolean killed) {
        if(spawnerLink!=null && !spawnerNotified && !level().isClientSide()) { spawnerLink.removed(this,killed); spawnerNotified=true; }
    }
    @Override public void tick() {
        if(spawnerLink!=null && !spawnerNotified && !level().isClientSide()) {
            if(!level().dimension().equals(spawnerLink.origin().dimension())) { notifySpawner(false); clearHome(); }
            else if(tickCount%20==0) spawnerLink.relink(this);
        }
        super.tick();
    }
    @Override public void die(DamageSource source) { super.die(source); if(dead) notifySpawner(true); }
    @Override public void remove(RemovalReason reason) {
        if(reason==RemovalReason.KILLED || reason==RemovalReason.DISCARDED || reason==RemovalReason.CHANGED_DIMENSION) notifySpawner(reason==RemovalReason.KILLED);
        super.remove(reason);
    }
    @Override protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        super.addAdditionalSaveData(out);
        if(spawnerLink!=null) { out.store("tg_spawner",techguns.modern.npc.spawner.SpawnerLink.CODEC,spawnerLink); out.putBoolean("tg_spawner_released",spawnerNotified); }
    }
    @Override protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        spawnerLink=in.read("tg_spawner",techguns.modern.npc.spawner.SpawnerLink.CODEC).orElse(null); spawnerNotified=in.getBooleanOr("tg_spawner_released",false);
        // Dimension transfer copies NBT before the old entity receives CHANGED_DIMENSION.
        // Keep the origin marker, but the copied NPC must never re-adopt the old reservation.
        if(spawnerLink!=null && !level().dimension().equals(spawnerLink.origin().dimension())) { spawnerNotified=true; clearHome(); }
    }
    public abstract float armorAgainst(DamageKind kind);
    public double bulletSideOffset() { return 0; }
    public double bulletHeightOffset() { return 0; }
    @Override public SoundEvent getAmbientSound() { return NpcContent.IDLE.get(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return NpcContent.HURT.get(); }
    @Override protected SoundEvent getDeathSound() { return NpcContent.DEATH.get(); }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(NpcContent.STEP.get(), .15f, 1); }
}
