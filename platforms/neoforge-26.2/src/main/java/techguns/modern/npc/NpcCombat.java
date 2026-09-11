package techguns.modern.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import techguns.core.*;
import techguns.modern.*;

public final class NpcCombat {
    static boolean validTarget(SuperMutant npc, LivingEntity target) {
        return target != null && target != npc && target.isAlive() && target.level() == npc.level() && !target.isSpectator()
                && !(target instanceof net.minecraft.world.entity.player.Player player && player.isCreative()) && npc.canAttack(target);
    }
    public static boolean fire(SuperMutant npc, LivingEntity target) {
        if (!(npc.level() instanceof ServerLevel level) || !npc.isAlive() || !npc.armed() || !validTarget(npc, target)
                || !npc.getSensing().hasLineOfSight(target)) return false;
        var stack = npc.getMainHandItem(); var gun = ((GunItem) stack.getItem()).definition(); var ai = NpcWeapons.forWeapon(gun.id());
        if (npc.distanceTo(target) > ai.range()) return false;
        int difficulty = level.getDifficulty().getId();
        float damage = SuperMutantRules.damage(difficulty);
        double accuracy = SuperMutantRules.accuracy(difficulty) * gun.aim().accuracyMultiplier();
        for (int n = 0; n < gun.projectileCount(); n++) {
            double spread = (n == 0 ? gun.stats().spread() : gun.pelletSpread()) * accuracy;
            Projectile projectile;
            switch (gun.projectile()) {
                case BALLISTIC -> {
                    var bullet = new Bullet(TGContent.BULLET.get(), level); bullet.configure(gun); bullet.npcDamage(damage);
                    bullet.setOwner(npc); bullet.shootLegacy(npc, spread, false); projectile = bullet;
                }
                case LASER -> {
                    var beam = new LaserBeam(TGContent.LASER_BEAM.get(), level); beam.configure(gun); beam.npcDamage(damage);
                    beam.setOwner(npc); beam.shootLegacy(npc, spread, false); projectile = beam;
                }
                case ROCKET -> {
                    var rocket = new RocketProjectile(TGContent.ROCKET.get(), level); rocket.configure(gun, RocketAmmo.variant(stack), false); rocket.npcDamage(damage);
                    rocket.setOwner(npc); rocket.shootLegacy(npc, spread); projectile = rocket;
                }
                default -> throw new IllegalStateException("Unported NPC projectile family");
            }
            projectile.setPos(projectile.position().add(projectile.getDeltaMovement().scale(ai.forwardOffset() / gun.stats().projectileSpeed())));
            if (!level.addFreshEntity(projectile)) { if (n == 0) return false; else continue; }
            if (projectile instanceof LaserBeam beam) beam.trace();
        }
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), TGContent.SOUND_EVENTS.get(gun.fireSound()).get(), SoundSource.HOSTILE, 4, 1);
        return true; // Source NPC fire bypasses player ammo consumption, reload and cooldown.
    }
    private NpcCombat() {}
}
