package techguns.modern.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import techguns.core.*;
import techguns.modern.*;

public final class NpcCombat {
    static boolean validTarget(ArmedNpc npc, LivingEntity target) {
        return target != null && target != npc && target.isAlive() && target.level() == npc.level() && !target.isSpectator()
                && !(target instanceof net.minecraft.world.entity.player.Player player && player.isCreative()) && npc.canAttack(target);
    }
    public static boolean fire(ArmedNpc npc, LivingEntity target) {
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
                case ADVANCED_BULLET -> {
                    var bullet = new AdvancedBulletProjectile(TGContent.ADVANCED_BULLET.get(), level); bullet.configure(gun); bullet.npcDamage(damage);
                    bullet.setOwner(npc); bullet.shootLegacy(npc, spread, -1); projectile = bullet;
                }
                case ALIEN_BLASTER -> {
                    var blast = new AlienBlasterProjectile(TGContent.ALIEN_BLAST.get(), level); blast.configure(gun, false); blast.npcDamage(damage);
                    blast.setOwner(npc); blast.shootLegacy(npc, spread, -1); projectile = blast;
                }
                case BLASTER -> {
                    var blast = new BlasterProjectile(TGContent.BLASTER.get(), level); blast.configure(gun); blast.npcDamage(damage);
                    blast.setOwner(npc); blast.shootLegacy(npc, spread, -1); projectile = blast;
                }
                case GAUSS -> {
                    var slug = new GaussProjectile(TGContent.GAUSS.get(), level); slug.configure(gun); slug.npcDamage(damage);
                    slug.setOwner(npc); slug.shootLegacy(npc, spread, -1); projectile = slug;
                }
                case FLAME -> {
                    var flame = new FlameProjectile(TGContent.FLAME.get(), level); flame.configure(gun, false); flame.npcDamage(damage);
                    flame.setOwner(npc); flame.shootLegacy(npc, spread, false); projectile = flame;
                }
                case BALLISTIC -> {
                    if (BallisticAmmo.variant(stack) == BallisticVariant.EXPLOSIVE) {
                        var bullet = new ExplosiveBullet(TGContent.EXPLOSIVE_BULLET.get(), level); bullet.configure(gun, false); bullet.npcDamage(damage);
                        bullet.setOwner(npc); bullet.shootLegacy(npc, spread, false); projectile = bullet;
                    } else if (BallisticAmmo.variant(stack) == BallisticVariant.INCENDIARY) {
                        var bullet = new IncendiaryBullet(TGContent.INCENDIARY_BULLET.get(), level); bullet.configure(gun, false); bullet.npcDamage(damage);
                        bullet.setOwner(npc); bullet.shootLegacy(npc, spread, false); projectile = bullet;
                    } else {
                        var bullet = new Bullet(TGContent.BULLET.get(), level); bullet.configure(gun); bullet.npcDamage(damage);
                        bullet.setOwner(npc); bullet.shootLegacy(npc, spread, false); projectile = bullet;
                    }
                }
                case LASER -> {
                    var beam = new LaserBeam(TGContent.LASER_BEAM.get(), level); beam.configure(gun); beam.npcDamage(damage);
                    beam.setOwner(npc); beam.shootLegacy(npc, spread, false); projectile = beam;
                }
                case ROCKET -> {
                    var rocket = new RocketProjectile(TGContent.ROCKET.get(), level); rocket.configure(gun, RocketAmmo.variant(stack), false); rocket.npcDamage(damage);
                    rocket.setOwner(npc); rocket.shootLegacy(npc, spread); projectile = rocket;
                }
                case GRENADE_40MM -> {
                    var grenade=new Grenade40mmProjectile(TGContent.GRENADE_40MM.get(),level);grenade.configure(gun);grenade.npcDamage(damage);
                    grenade.setOwner(npc);grenade.shootLegacy(npc,spread);projectile=grenade;
                }
                case NETHER_BLASTER -> {
                    var blast = new NetherBlasterProjectile(TGContent.NETHER_BLAST.get(), level); blast.configure(gun); blast.npcDamage(damage);
                    blast.setOwner(npc); blast.shootLegacy(npc, spread, false); projectile = blast;
                }
                case CHAINSAW -> {
                    var attack = new ChainsawAttack(TGContent.CHAINSAW_ATTACK.get(),level); attack.configure(gun); attack.npcDamage(damage);
                    attack.setOwner(npc); attack.shootLegacy(npc,spread); projectile=attack;
                }
                default -> throw new IllegalStateException("Unported NPC projectile family");
            }
            projectile.setPos(projectile.position().add(projectile.getDeltaMovement().scale(ai.forwardOffset() / gun.stats().projectileSpeed())));
            if (!level.addFreshEntity(projectile)) { if (n == 0) return false; else continue; }
            if(projectile instanceof Grenade40mmProjectile) stack.set(TGContent.LAUNCHER_SHOT_TIME.get(),level.getGameTime());
            if (projectile instanceof FlameProjectile) { FlameFiring.recoil(level, stack); FlameFiring.muzzle(level, npc, -1); }
            if (projectile instanceof LaserBeam beam) beam.trace();
        }
        var soundPos = gun.projectile() == ProjectileKind.FLAME ? FlameFiring.soundPosition(npc) : npc.position();
        level.playSound(null, soundPos.x, soundPos.y, soundPos.z, TGContent.SOUND_EVENTS.get(gun.fireSound()).get(), SoundSource.HOSTILE, 4, 1);
        return true; // Source NPC fire bypasses player ammo consumption, reload and cooldown.
    }
    private NpcCombat() {}
}
