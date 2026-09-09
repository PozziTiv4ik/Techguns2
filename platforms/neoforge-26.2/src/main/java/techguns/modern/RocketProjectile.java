package techguns.modern;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;
import techguns.core.*;

/** Server-authoritative legacy rocket flight. A direct hit and blast retain normal hurt cooldowns. */
public final class RocketProjectile extends Projectile {
    private static final EntityDataAccessor<String> VARIANT = SynchedEntityData.defineId(RocketProjectile.class, EntityDataSerializers.STRING);
    private WeaponDefinition weapon = Weapons.definition("rocketlauncher");
    private Vec3 origin;
    private int age;
    private boolean blockDamage;
    public RocketProjectile(EntityType<? extends RocketProjectile> type, Level level) { super(type, level); }
    public void configure(WeaponDefinition weapon, RocketVariant variant, boolean blockDamage) {
        if (weapon.projectile() != ProjectileKind.ROCKET) throw new IllegalArgumentException("Expected rocket weapon");
        this.weapon = weapon;
        entityData.set(VARIANT, variant.id());
        this.blockDamage = blockDamage;
    }
    public WeaponDefinition weapon() { return weapon; }
    public RocketVariant variant() { return RocketVariant.fromId(entityData.get(VARIANT)); }
    public boolean damagesBlocks() { return blockDamage; }
    public int age() { return age; }
    public void shootLegacy(LivingEntity source, double accuracy) {
        LegacyShot.shoot(this, source, random, accuracy, false, variant().speed(weapon.stats()));
        orient(1);
        yRotO = getYRot(); xRotO = getXRot();
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(VARIANT, RocketVariant.DEFAULT.id()); }
    private boolean target(Entity entity) { return entity != getOwner() && !entity.isSpectator() && entity.isAlive() && entity.isPickable(); }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            // Predict only visual flight between the server's per-tick corrections.
            setPos(position().add(getDeltaMovement()));
            orient(.2f);
            setDeltaMovement(getDeltaMovement().scale(isInWater() ? (double) .85f : (double) .99f).add(0, -weapon.gravity(), 0));
            return;
        }
        if (age >= variant().lifetime(weapon.stats())) { discard(); return; }
        if (origin == null) origin = position();
        ++age;
        Vec3 start = position(), movement = getDeltaMovement(), end = start.add(movement);
        HitResult hit = level().clipIncludingBorder(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level(), this, start,
                hit.getType() == HitResult.Type.MISS ? end : hit.getLocation(), getBoundingBox().expandTowards(movement).inflate(1), this::target, .3f);
        if (entityHit != null) hit = entityHit;
        boolean friendly = hit instanceof EntityHitResult result && getOwner() instanceof Player owner
                && result.getEntity() instanceof Player player && !owner.canHarmPlayer(player);
        if (!friendly && hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
            if (hit instanceof EntityHitResult result) {
                boolean hurt = RocketDamage.hurt(server, this, result.getEntity(), variant().directDamage(weapon.stats(), origin.distanceTo(start)), 3);
                if (hurt && result.getEntity() instanceof LivingEntity) explode();
                else discard();
            } else explode();
            return;
        }
        setPos(end);
        orient(.2f);
        server.sendParticles(ParticleTypes.SMOKE, start.x, start.y, start.z, 2, .025, .025, .025, .01);
        server.sendParticles(ParticleTypes.FLAME, start.x, start.y, start.z, 1, 0, 0, 0, 0);
        setDeltaMovement(movement.scale(isInWater() ? (double) .85f : (double) .99f).add(0, -weapon.gravity(), 0));
        if (age >= variant().lifetime(weapon.stats())) discard();
    }
    private void orient(float fraction) {
        Vec3 velocity = getDeltaMovement();
        float yaw = (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
        float pitch = (float) Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance()));
        setYRot(Mth.rotLerp(fraction, getYRot(), yaw));
        setXRot(Mth.rotLerp(fraction, getXRot(), pitch));
    }
    public boolean explode() {
        if (isRemoved() || !(level() instanceof ServerLevel server)) return false;
        boolean exploded = RocketExplosion.detonate(server, this);
        if (exploded && variant() == RocketVariant.NUKE && !techguns.modern.radiation.RadiationSystem.DISABLED.get()) {
            var zone = new techguns.modern.radiation.RadiationZone(TGContent.RADIATION_ZONE.get(), server);
            zone.setPos(position());
            server.addFreshEntity(zone);
        }
        discard();
        return exploded;
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("weapon", weapon.id()); output.putString("variant", variant().id());
        output.putInt("age", age); output.putBoolean("block_damage", blockDamage);
        if (origin != null) output.store("origin", Vec3.CODEC, origin);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try { configure(Weapons.definition(input.getStringOr("weapon", "rocketlauncher")), RocketVariant.fromId(input.getStringOr("variant", "default")), input.getBooleanOr("block_damage", false)); }
        catch (IllegalArgumentException invalid) { discard(); return; }
        age = Math.clamp(input.getIntOr("age", 0), 0, variant().lifetime(weapon.stats()));
        origin = input.read("origin", Vec3.CODEC).orElse(null);
        if (origin != null && (!Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z))) discard();
    }
}
