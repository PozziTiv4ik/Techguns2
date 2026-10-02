package techguns.modern;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;
import techguns.core.*;

/** AdvancedBulletProjectile inherits GenericProjectile flight, displacement damage and ordinary bullet armor. */
public final class AdvancedBulletProjectile extends Projectile {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("advanced_bullet"));
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(AdvancedBulletProjectile.class, EntityDataSerializers.FLOAT);
    private static final DustParticleOptions IMPACT = new DustParticleOptions(0x66CCFF, .6f);
    private WeaponDefinition weapon = Weapons.definition("pdw");
    private ShotDamage damage = ShotDamage.PLAYER;
    private Vec3 origin;
    private int age;

    public AdvancedBulletProjectile(EntityType<? extends AdvancedBulletProjectile> type, Level level) { super(type, level); setNoGravity(true); }
    public void configure(WeaponDefinition gun) {
        if (gun.projectile() != ProjectileKind.ADVANCED_BULLET) throw new IllegalArgumentException("Expected Advanced bullet weapon");
        weapon = gun; entityData.set(SPEED, (float)gun.stats().projectileSpeed());
    }
    public WeaponDefinition weapon() { return weapon; }
    public ShotDamage shotDamage() { return damage; }
    public int age() { return age; }
    public float renderSpeed() { return entityData.get(SPEED); }
    public void npcDamage(float scale) { damage = new ShotDamage(true, scale); }
    public void shootLegacy(LivingEntity source, double spread, int side) { LegacyShot.shoot(this, source, random, spread, side, weapon.stats().projectileSpeed()); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(SPEED, (float)Weapons.definition("pdw").stats().projectileSpeed()); }
    @Override protected boolean canHitEntity(Entity target) { return target != getOwner() && super.canHitEntity(target); }

    @Override public void tick() {
        if (isRemoved()) return;
        super.tick(); if (!(level() instanceof ServerLevel server)) return;
        Vec3 start = position(); if (origin == null) origin = start;
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        boolean friendly = hit instanceof EntityHitResult entityHit && getOwner() instanceof Player owner
                && entityHit.getEntity() instanceof Player target && !owner.canHarmPlayer(target);
        boolean impact = !friendly && hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit);
        Vec3 end = impact ? hit.getLocation() : start.add(getDeltaMovement());
        if (impact && hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            float amount = weapon.stats().damageAt(origin.distanceTo(start));
            amount = target instanceof LivingEntity ? damage.againstEntity(amount) : amount * damage.scale();
            if (amount > 0) {
                if (target instanceof LivingEntity) target.hurtServer(server, damage.source(server, BurningProjectile.KNOCKBACK_TYPE, this), .01f);
                target.hurtServer(server, damage.source(server, DAMAGE_TYPE, this), amount);
            }
        } else if (impact && hit instanceof BlockHitResult block) {
            var type = server.getBlockState(block.getBlockPos()).getSoundType(server, block.getBlockPos(), this);
            String sound = type == SoundType.STONE ? AdvancedBulletRules.BULLET_IMPACT_STONE
                    : type == SoundType.WOOD || type == SoundType.LADDER ? AdvancedBulletRules.BULLET_IMPACT_WOOD
                    : type == SoundType.GLASS ? AdvancedBulletRules.BULLET_IMPACT_GLASS
                    : type == SoundType.METAL || type == SoundType.ANVIL ? AdvancedBulletRules.BULLET_IMPACT_METAL : AdvancedBulletRules.BULLET_IMPACT_DIRT;
            server.playSound(null, end.x, end.y, end.z, TGContent.SOUND_EVENTS.get(sound).get(), SoundSource.AMBIENT, 1, 1);
            server.sendParticles(IMPACT, end.x, end.y, end.z, 6, .04, .04, .04, 0);
        }
        setPos(end);
        if (impact) { super.onHit(hit); discard(); return; }
        if (isInWater()) server.sendParticles(ParticleTypes.BUBBLE, end.x, end.y, end.z, 4, .02, .02, .02, 0);
        setDeltaMovement(getDeltaMovement().scale(isInWater() ? (double)AdvancedBulletRules.WATER_DRAG : (double)AdvancedBulletRules.AIR_DRAG));
        if (isInWaterOrRain()) clearFire();
        if (++age >= weapon.stats().projectileLifetime()) discard();
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("weapon", weapon.id()); output.putInt("age", age); damage.save(output);
        if (origin != null) { output.putDouble("origin_x", origin.x); output.putDouble("origin_y", origin.y); output.putDouble("origin_z", origin.z); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            configure(Weapons.definition(input.getStringOr("weapon", "pdw"))); damage = ShotDamage.load(input); age = input.getIntOr("age", 0);
            if (age < 0 || age >= weapon.stats().projectileLifetime()) throw new IllegalArgumentException("Expired Advanced bullet");
            origin = null;
            var x = input.read("origin_x", com.mojang.serialization.Codec.DOUBLE);
            var y = input.read("origin_y", com.mojang.serialization.Codec.DOUBLE);
            var z = input.read("origin_z", com.mojang.serialization.Codec.DOUBLE);
            if (x.isPresent() || y.isPresent() || z.isPresent()) {
                if (x.isEmpty() || y.isEmpty() || z.isEmpty() || !Double.isFinite(x.get()) || !Double.isFinite(y.get()) || !Double.isFinite(z.get())
                        || Math.abs(x.get()) > 30_000_000 || Math.abs(y.get()) > 30_000_000 || Math.abs(z.get()) > 30_000_000)
                    throw new IllegalArgumentException("Invalid Advanced bullet origin");
                origin = new Vec3(x.get(), y.get(), z.get());
            } else if (age > 0) throw new IllegalArgumentException("Missing in-flight Advanced bullet origin");
        } catch (IllegalArgumentException invalid) { discard(); }
    }
}
