package techguns.modern.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.storage.*;
import techguns.core.*;
import techguns.modern.*;

public final class HelicopterAttackGoal extends Goal {
    private final AttackHelicopter heli;
    private final HelicopterAttack cycle=new HelicopterAttack();
    private boolean resume;
    public HelicopterAttackGoal(AttackHelicopter heli) { this.heli=heli; }
    @Override public boolean canUse() { return heli.getTarget()!=null && heli.getTarget().isAlive(); }
    @Override public void start() { if(!resume) cycle.start(); resume=false; }
    @Override public void stop() { heli.setAttacking(false); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    public int timer() { return cycle.timer(); }
    public void save(ValueOutput out) { out.putInt("attack_timer",cycle.timer()); }
    public void load(ValueInput in) { cycle.restore(in.getIntOr("attack_timer",0)); resume=true; }
    @Override public void tick() {
        var target=heli.getTarget(); if(target==null || !target.isAlive() || !(heli.level() instanceof ServerLevel level)) return;
        var action=cycle.tick(heli.distanceToSqr(target),heli.hasLineOfSight(target)); heli.setAttacking(cycle.attacking());
        if(action==HelicopterAttack.Action.BULLET) {
            if(cycle.timer()==14) heli.playSound(NpcContent.HELI_BURST.get(),8,1);
            var bullet=new Bullet(TGContent.BULLET.get(),level); bullet.setOwner(heli); bullet.configure(HelicopterWeapons.BULLET); bullet.npcDamage(1);
            bullet.shootLegacy(heli,.05,true); level.addFreshEntity(bullet);
        } else if(action==HelicopterAttack.Action.ROCKET) {
            heli.playSound(TGContent.SOUND_EVENTS.get(Weapons.definition("rocketlauncher").fireSound()).get(),8,1);
            var rocket=new RocketProjectile(TGContent.ROCKET.get(),level); rocket.setOwner(heli); rocket.configure(HelicopterWeapons.ROCKET,RocketVariant.DEFAULT,false); rocket.npcDamage(1);
            rocket.shootLegacy(heli,.05,heli.getRandom().nextBoolean()?1:-1); level.addFreshEntity(rocket);
        }
    }
}
