package techguns.modern;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import techguns.core.Weapons;

/** Server-simulated revolver round with swept collision, range falloff and bounded lifetime. */
public final class Bullet extends Projectile {
    private double distance;
    private int age;

    public Bullet(EntityType<? extends Bullet> type, Level level) { super(type, level); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (++age > Weapons.REVOLVER.projectileLifetime()) { discard(); return; }
        Vec3 start = position();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        Vec3 end = hit.getType() == HitResult.Type.MISS ? start.add(getDeltaMovement()) : hit.getLocation();
        distance += start.distanceTo(end);
        server.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        if (hit instanceof EntityHitResult entityHit) {
            boolean friendlyPlayer = getOwner() instanceof Player owner && entityHit.getEntity() instanceof Player target
                    && !owner.canHarmPlayer(target);
            if (!friendlyPlayer && getOwner() instanceof LivingEntity owner) {
                entityHit.getEntity().hurtServer(server, damageSources().mobProjectile(this, owner),
                        Weapons.REVOLVER.damageAt(distance));
            }
        }
        setPos(end);
        if (hit.getType() != HitResult.Type.MISS) discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("distance", distance);
        output.putInt("age", age);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        double stored = input.getDoubleOr("distance", 0);
        distance = Double.isFinite(stored) ? Math.max(0, stored) : 0;
        age = Math.clamp(input.getIntOr("age", 0), 0, Weapons.REVOLVER.projectileLifetime());
    }
}
