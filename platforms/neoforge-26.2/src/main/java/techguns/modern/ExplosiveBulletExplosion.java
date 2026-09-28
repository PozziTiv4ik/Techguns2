package techguns.modern;

import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.*;
import net.neoforged.neoforge.event.EventHooks;
import techguns.core.ExplosiveAmmo;

/** The active source invokes vanilla Explosion(1.5, false, blockdamage), not TGExplosion. */
final class ExplosiveBulletExplosion {
    static boolean detonate(ServerLevel level, ExplosiveBullet bullet) {
        var calculator = new EntityBasedExplosionDamageCalculator(bullet) {
            @Override public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
                return ExplosiveAmmo.blastDamage(entity.position().distanceTo(explosion.center()), exposure);
            }
        };
        // In 1.12 getExplosivePlacedBy recognizes TNT/living sources, not this projectile.
        // Splash therefore has no shooter, weapon penetration or NPC multiplier.
        var explosion = new ServerExplosion(level, bullet, level.damageSources().explosion(null, null), calculator,
                bullet.position(), ExplosiveAmmo.EXPLOSION_POWER, false,
                bullet.damagesBlocks() ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.KEEP) {
            @Override public LivingEntity getIndirectSourceEntity() { return null; }
        };
        if (EventHooks.onExplosionStart(level, explosion)) return false;
        int blocks = explosion.explode();
        // Native packet carries both sound/particles and the affected player's blast velocity.
        // The original doExplosionB(false) omits per-block debris particles.
        for (var player : level.players()) if (player.distanceToSqr(bullet.position()) < 4096) {
            player.connection.send(new ClientboundExplodePacket(bullet.position(), explosion.radius(), blocks,
                    Optional.ofNullable(explosion.getHitPlayers().get(player)), ParticleTypes.EXPLOSION,
                    SoundEvents.GENERIC_EXPLODE, WeightedList.of()));
        }
        return true;
    }
    private ExplosiveBulletExplosion() {}
}
