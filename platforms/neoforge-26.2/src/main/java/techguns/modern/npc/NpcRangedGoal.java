package techguns.modern.npc;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import techguns.core.NpcAttackCycle;
import techguns.core.NpcWeapons;
import techguns.modern.GunItem;

/** Port of EntityAIRangedAttack: approach, track visibility, then use the source weapon's burst clock. */
public final class NpcRangedGoal extends Goal {
    private final ArmedNpc mob;
    private ItemStack weapon;
    private NpcAttackCycle cycle;
    public NpcRangedGoal(ArmedNpc mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    @Override public boolean canUse() { return mob.armed() && valid(mob.getTarget()); }
    @Override public boolean canContinueToUse() { return canUse(); }
    private boolean valid(LivingEntity target) { return NpcCombat.validTarget(mob, target); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void stop() { if (cycle != null) cycle.resetTarget(); mob.setAggressive(false); mob.getNavigation().stop(); }
    @Override public void tick() {
        if (!canUse()) return;
        if (weapon != mob.getMainHandItem()) {
            weapon = mob.getMainHandItem(); cycle = new NpcAttackCycle(NpcWeapons.forWeapon(((GunItem) weapon.getItem()).definition().id()));
        }
        var target = mob.getTarget();
        double distance = mob.distanceTo(target);
        boolean fire = cycle.tick(distance, mob.getSensing().hasLineOfSight(target));
        if (cycle.pursue(distance)) mob.getNavigation().moveTo(target, 1); else mob.getNavigation().stop();
        mob.getLookControl().setLookAt(target, 30, 55);
        mob.setAggressive(true);
        if (fire) mob.fireAt(target);
    }
}
