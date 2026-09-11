package techguns.modern;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Shared muzzle offset and dispersion from GenericProjectile.initProjectile/shoot. */
final class LegacyShot {
    static void shoot(Projectile projectile, LivingEntity source, RandomSource random,
                      double accuracy, boolean centered, double speed) {
        boolean npc = source instanceof net.minecraft.world.entity.Mob;
        float yaw = (npc ? source.getYHeadRot() : source.getYRot()) + (float) (accuracy - 2 * random.nextDouble() * accuracy) * 40;
        float pitch = source.getXRot() + (float) (accuracy - 2 * random.nextDouble() * accuracy) * 40;
        double offset = .16 + (source instanceof techguns.modern.npc.ArmedNpc armed ? armed.bulletSideOffset() : 0);
        double height = -.1 + (source instanceof techguns.modern.npc.ArmedNpc armed ? armed.bulletHeightOffset() : 0);
        double side = centered ? 0 : npc || source.getMainArm() == HumanoidArm.RIGHT ? -offset : offset;
        projectile.setPos(source.getEyePosition().add(Math.cos(Math.toRadians(yaw)) * side, height,
                Math.sin(Math.toRadians(yaw)) * side));
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw).normalize().add(
                random.nextGaussian() * 0.007499999832361937,
                random.nextGaussian() * 0.007499999832361937,
                random.nextGaussian() * 0.007499999832361937);
        // The original discards normalize() after shoot(..., 1.5, 1), retaining this multiplier.
        projectile.setDeltaMovement(direction.scale(1.5 * speed));
        projectile.setYRot(yaw);
        projectile.setXRot(pitch);
    }
    private LegacyShot() {}
}
