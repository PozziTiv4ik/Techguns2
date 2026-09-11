package techguns.modern;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import techguns.core.ProjectileKind;
import techguns.core.WeaponDefinition;
import techguns.core.Weapons;

/** One server trace followed by a stationary, synchronized seven-tick visual. */
public final class LaserBeam extends Projectile {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("laser"));
    private static final EntityDataAccessor<Vector3fc> END = SynchedEntityData.defineId(LaserBeam.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(LaserBeam.class, EntityDataSerializers.INT);
    private WeaponDefinition weapon = Weapons.definition("lasergun");
    private boolean traced;
    private ShotDamage shotDamage = ShotDamage.PLAYER;
    public void npcDamage(float scale) { shotDamage = new ShotDamage(true, scale); }
    public ShotDamage shotDamage() { return shotDamage; }

    public LaserBeam(EntityType<? extends LaserBeam> type, Level level) { super(type, level); }
    public WeaponDefinition weapon() { return weapon; }
    public int age() { return entityData.get(AGE); }
    public Vec3 endOffset() { return new Vec3(entityData.get(END)); }
    public AABB beamBounds() { return new AABB(position(), position().add(endOffset())).inflate(.1); }
    public void configure(WeaponDefinition weapon) {
        if (traced || weapon.projectile() != ProjectileKind.LASER) throw new IllegalArgumentException("Expected an unfired laser");
        this.weapon = weapon;
    }
    public void shootLegacy(LivingEntity source, double accuracy, boolean centered) {
        if (traced) throw new IllegalStateException("Beam already fired");
        LegacyShot.shoot(this, source, random, accuracy, centered, weapon.stats().projectileSpeed());
    }

    /** Call only after addFreshEntity succeeds, so a cancelled spawn cannot cause damage. */
    public void trace() {
        if (traced || isRemoved() || !(level() instanceof ServerLevel server)) return;
        traced = true;
        Vec3 from = position(), motion = getDeltaMovement();
        HitResult hit = server.clip(new ClipContext(from, from.add(motion), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        var entityHit = ProjectileUtil.getEntityHitResult(server, this, from, hit.getLocation(),
                getBoundingBox().expandTowards(motion).inflate(1), this::canHitEntity, .3f);
        if (entityHit != null) hit = entityHit;
        boolean impacted = hit.getType() != HitResult.Type.MISS;
        // Original trace uses the 1.5x noisy velocity; a miss is displayed at the nominal speed (100 blocks).
        Vec3 end = impacted ? hit.getLocation().subtract(from) : motion.normalize().scale(weapon.stats().projectileSpeed());
        entityData.set(END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
        setDeltaMovement(Vec3.ZERO);
        if (!impacted || EventHooks.onProjectileImpact(this, hit)) return;
        if (hit instanceof EntityHitResult targetHit) {
            boolean friendly = getOwner() instanceof Player owner && targetHit.getEntity() instanceof Player target && !owner.canHarmPlayer(target);
            if (friendly) return;
            DamageSource source = shotDamage.source(server, DAMAGE_TYPE, this);
            targetHit.getEntity().hurtServer(server, source, shotDamage.againstEntity(weapon.stats().damageAt(end.length())));
        } else {
            Vec3 at = hit.getLocation();
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, .05, .05, .05, .1);
        }
        super.onHit(hit);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(END, new Vector3f());
        builder.define(AGE, 0);
    }
    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel) {
            if (!traced || age() + 1 >= weapon.stats().projectileLifetime()) discard();
            else entityData.set(AGE, age() + 1);
        }
    }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("weapon", weapon.id());
        shotDamage.save(output);
        output.putBoolean("traced", traced);
        output.putInt("age", age());
        Vec3 end = endOffset();
        output.putDouble("end_x", end.x); output.putDouble("end_y", end.y); output.putDouble("end_z", end.z);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try { shotDamage = ShotDamage.load(input); configure(Weapons.definition(input.getStringOr("weapon", "lasergun"))); }
        catch (IllegalArgumentException invalid) { discard(); return; }
        Vec3 end = new Vec3(input.getDoubleOr("end_x", 0), input.getDoubleOr("end_y", 0), input.getDoubleOr("end_z", 0));
        int age = input.getIntOr("age", 0);
        if (!input.getBooleanOr("traced", false) || !Double.isFinite(end.lengthSqr()) || end.lengthSqr() > 256 * 256
                || age < 0 || age >= weapon.stats().projectileLifetime()) { discard(); return; }
        traced = true; // Loading a visual must never repeat its already-applied hit.
        entityData.set(END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
        entityData.set(AGE, age);
        setDeltaMovement(Vec3.ZERO);
    }
}
