package techguns.modern.npc;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import techguns.core.GhastlingAttack;
import techguns.modern.*;

public final class GhastlingAttackGoal extends Goal {
    private final Ghastling mob;
    private final GhastlingAttack cycle=new GhastlingAttack();
    public GhastlingAttackGoal(Ghastling mob) { this.mob=mob; setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
    @Override public boolean canUse() { return mob.getTarget()!=null && mob.getTarget().isAlive(); }
    @Override public void start() { cycle.start(); }
    @Override public void stop() { cycle.stop(); mob.setAttacking(false); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() {
        var target=mob.getTarget(); if(target==null || !target.isAlive() || !(mob.level() instanceof ServerLevel level)) return;
        double distance=mob.distanceToSqr(target),follow=mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        var action=cycle.tick(distance,follow); mob.setAttacking(cycle.attacking());
        if(action==GhastlingAttack.Action.MELEE) mob.doHurtTarget(level,target);
        else if(action==GhastlingAttack.Action.SHOT) {
            level.levelEvent(null,1018,mob.blockPosition(),0);
            var projectile=new AlienBlasterProjectile(TGContent.ALIEN_BLAST.get(),level);
            projectile.setOwner(mob); projectile.shootLegacy(mob); level.addFreshEntity(projectile);
        }
        if(distance<4) mob.getMoveControl().setWantedPosition(target.getX(),target.getY(),target.getZ(),1);
        else if(distance<follow*follow) mob.getLookControl().setLookAt(target,10,10);
        else { mob.getNavigation().stop(); mob.getMoveControl().setWantedPosition(target.getX(),target.getY(),target.getZ(),1); }
        // The registered source goal has no line-of-sight check here; walls stop the real projectile.
    }
}
