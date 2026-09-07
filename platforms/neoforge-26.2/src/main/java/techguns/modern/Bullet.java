package techguns.modern;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
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
import techguns.core.WeaponDefinition;
import net.neoforged.neoforge.event.EventHooks;

/** Server-simulated ballistic round, retaining its source weapon across saves. */
public final class Bullet extends Projectile {
    static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("bullet"));
    private WeaponDefinition weapon = Weapons.definition("revolver");
    private double distance;
    private int age;

    public Bullet(EntityType<? extends Bullet> type, Level level) { super(type, level); }

    public void configure(WeaponDefinition weapon) { this.weapon = weapon; }
    public WeaponDefinition weapon() { return weapon; }

    public void shootLegacy(LivingEntity source, double accuracy) {
        float yaw = source.getYRot() + (float) (accuracy - 2 * random.nextDouble() * accuracy) * 40;
        float pitch = source.getXRot() + (float) (accuracy - 2 * random.nextDouble() * accuracy) * 40;
        double side = source.getMainArm() == HumanoidArm.RIGHT ? -0.16 : 0.16;
        setPos(source.getEyePosition().add(Math.cos(Math.toRadians(yaw)) * side, -0.1,
                Math.sin(Math.toRadians(yaw)) * side));
        Vec3 direction = Vec3.directionFromRotation(pitch, yaw).normalize().add(
                random.nextGaussian() * 0.007499999832361937,
                random.nextGaussian() * 0.007499999832361937,
                random.nextGaussian() * 0.007499999832361937);
        // Legacy shoot(..., 1.5, 1) followed by *= speed. The discarded normalize()
        // return in GenericProjectile leaves this 1.5 multiplier in actual gameplay.
        setDeltaMovement(direction.scale(1.5 * weapon.stats().projectileSpeed()));
        setYRot(yaw);
        setXRot(pitch);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (++age > weapon.stats().projectileLifetime()) { discard(); return; }
        Vec3 start = position();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        boolean impacted = hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit);
        Vec3 end = impacted ? hit.getLocation() : start.add(getDeltaMovement());
        distance += start.distanceTo(end);
        server.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        if (impacted && hit instanceof EntityHitResult entityHit) {
            boolean friendlyPlayer = getOwner() instanceof Player owner && entityHit.getEntity() instanceof Player target
                    && !owner.canHarmPlayer(target);
            if (!friendlyPlayer) {
                DamageSource source = new DamageSource(server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                        .getOrThrow(DAMAGE_TYPE), this, getOwner());
                entityHit.getEntity().hurtServer(server, source, weapon.stats().damageAt(distance));
            }
        }
        setPos(end);
        if (impacted) {
            super.onHit(hit);
            discard();
        } else {
            setDeltaMovement(getDeltaMovement().add(0, -weapon.gravity(), 0));
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("distance", distance);
        output.putInt("age", age);
        output.putString("weapon", weapon.id());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        double stored = input.getDoubleOr("distance", 0);
        distance = Double.isFinite(stored) ? Math.max(0, stored) : 0;
        try {
            weapon = Weapons.definition(input.getStringOr("weapon", "revolver"));
        } catch (IllegalArgumentException unknownWeapon) {
            discard();
            return;
        }
        age = Math.clamp(input.getIntOr("age", 0), 0, weapon.stats().projectileLifetime());
    }
}
