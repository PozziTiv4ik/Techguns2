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
    public abstract float armorAgainst(DamageKind kind);
    public double bulletSideOffset() { return 0; }
    public double bulletHeightOffset() { return 0; }
    @Override public SoundEvent getAmbientSound() { return NpcContent.IDLE.get(); }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return NpcContent.HURT.get(); }
    @Override protected SoundEvent getDeathSound() { return NpcContent.DEATH.get(); }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(NpcContent.STEP.get(), .15f, 1); }
}
