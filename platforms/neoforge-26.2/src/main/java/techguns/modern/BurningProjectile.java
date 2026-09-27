package techguns.modern;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;
import techguns.core.*;

/** Shared GenericProjectile movement, fire damage, ignition and persistence. Profiles keep factory differences explicit. */
public abstract class BurningProjectile extends Projectile {
    public static final ResourceKey<DamageType> KNOCKBACK_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("incendiary_knockback"));
    private WeaponDefinition weapon;
    private ShotDamage damage = ShotDamage.PLAYER;
    private Vec3 origin;
    private int age;
    private boolean blockDamage;

    protected BurningProjectile(EntityType<? extends BurningProjectile> type, Level level, WeaponDefinition initial) { super(type, level); weapon = initial; }
    public abstract ResourceKey<DamageType> damageType();
    protected abstract boolean supports(WeaponDefinition gun);
    protected abstract float damageAt(double distance);
    protected abstract boolean ignites(double roll);
    protected abstract int burnSeconds();
    public abstract boolean fireTrail();
    protected double flightGravity() { return 0; }
    protected boolean extinguishesWhenWet() { return false; }
    public void configure(WeaponDefinition gun, boolean blockDamage) {
        if (!supports(gun)) throw new IllegalArgumentException("Weapon does not support this fire projectile");
        weapon = gun;
        this.blockDamage = blockDamage;
    }
    public WeaponDefinition weapon() { return weapon; }
    public int age() { return age; }
    public boolean damagesBlocks() { return blockDamage; }
    public void npcDamage(float scale) { damage = new ShotDamage(true, scale); }
    public ShotDamage shotDamage() { return damage; }
    public void shootLegacy(LivingEntity source, double spread, boolean centered) {
        LegacyShot.shoot(this, source, random, spread, centered, weapon.stats().projectileSpeed());
    }
    public void shootLegacy(LivingEntity source, double spread, int side) {
        LegacyShot.shoot(this, source, random, spread, side, weapon.stats().projectileSpeed());
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected boolean canHitEntity(Entity target) { return target != getOwner() && super.canHitEntity(target); }

    @Override public void tick() {
        if (origin == null) origin = position();
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 start = position();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        boolean friendly = hit instanceof EntityHitResult entityHit && getOwner() instanceof Player owner
                && entityHit.getEntity() instanceof Player target && !owner.canHarmPlayer(target);
        boolean impact = !friendly && hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit);
        Vec3 end = impact ? hit.getLocation() : start.add(getDeltaMovement());
        if (impact && hit instanceof EntityHitResult entityHit) {
            var target = entityHit.getEntity();
            float amount = damageAt(origin.distanceTo(start));
            amount = target instanceof LivingEntity ? damage.againstEntity(amount) : amount * damage.scale();
            if (amount > 0) {
                // Source GenericProjectile calls this ordinary-cooldown PHYSICAL hit first.
                // It supplies knockback once; the main FIRE hit bypasses cooldown without more knockback.
                if (target instanceof LivingEntity) target.hurtServer(server, damage.source(server, KNOCKBACK_TYPE, this), .01f);
                boolean accepted = target.hurtServer(server, damage.source(server, damageType(), this), amount);
                if (accepted && target instanceof LivingEntity living && !living.fireImmune()) living.igniteForSeconds(burnSeconds());
            }
        } else if (impact && hit instanceof BlockHitResult blockHit) igniteBlock(blockHit);
        setPos(end);
        // Native particles adapt the source entity-bound trail / impact FX without a client dependency.
        if (fireTrail() || impact) server.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, impact ? 4 : 1, .02, .02, .02, 0);
        if (impact) { super.onHit(hit); discard(); return; }
        setDeltaMovement(getDeltaMovement().scale(isInWater() ? (double).85f : (double).99f));
        setDeltaMovement(getDeltaMovement().add(0, -flightGravity(), 0));
        if (extinguishesWhenWet() && isInWaterOrRain()) {
            if (isInWater()) server.sendParticles(ParticleTypes.BUBBLE, end.x, end.y, end.z, 4, .02, .02, .02, 0);
            discard();
        }
        if (++age >= weapon.stats().projectileLifetime()) discard();
    }

    public boolean igniteBlock(BlockHitResult hit) {
        if (!(level() instanceof ServerLevel server) || !blockDamage || !ignites(random.nextDouble())) return false;
        var pos = hit.getBlockPos().relative(hit.getDirection());
        if (!server.hasChunkAt(pos) || !server.isInWorldBounds(pos) || !server.getBlockState(pos).isAir()) return false;
        // Fire's native onPlace handles support, extinguishing and portal behavior, as in 1.12.
        return server.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("weapon", weapon.id()); output.putInt("age", age); output.putBoolean("block_damage", blockDamage); damage.save(output);
        if (origin != null) { output.putDouble("origin_x", origin.x); output.putDouble("origin_y", origin.y); output.putDouble("origin_z", origin.z); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            configure(Weapons.definition(input.getStringOr("weapon", weapon.id())), input.getBooleanOr("block_damage", false));
            damage = ShotDamage.load(input); age = input.getIntOr("age", 0);
            if (age < 0 || age >= weapon.stats().projectileLifetime()) throw new IllegalArgumentException("Expired projectile");
            var x = input.read("origin_x", com.mojang.serialization.Codec.DOUBLE);
            var y = input.read("origin_y", com.mojang.serialization.Codec.DOUBLE);
            var z = input.read("origin_z", com.mojang.serialization.Codec.DOUBLE);
            origin = null;
            if (x.isPresent() || y.isPresent() || z.isPresent()) {
                if (x.isEmpty() || y.isEmpty() || z.isEmpty() || !Double.isFinite(x.get()) || !Double.isFinite(y.get()) || !Double.isFinite(z.get()))
                    throw new IllegalArgumentException("Invalid projectile origin");
                origin = new Vec3(x.get(), y.get(), z.get());
            }
        } catch (IllegalArgumentException invalid) { discard(); }
    }
}
