package techguns.modern;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;
import techguns.core.*;

/** GenericProjectileExplosive: accepted direct EXPLOSION hit, then a separate vanilla blast. */
public final class ExplosiveBullet extends Projectile {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("explosive_bullet"));
    private WeaponDefinition weapon = Weapons.definition("as50");
    private ShotDamage damage = ShotDamage.PLAYER;
    private Vec3 origin;
    private int age;
    private boolean blockDamage;

    public ExplosiveBullet(EntityType<? extends ExplosiveBullet> type, Level level) { super(type, level); }
    public void configure(WeaponDefinition gun, boolean blockDamage) {
        if (!ExplosiveAmmo.supported(gun)) throw new IllegalArgumentException("Weapon has no explosive bullet");
        weapon = gun; this.blockDamage = blockDamage;
    }
    public WeaponDefinition weapon() { return weapon; }
    public int age() { return age; }
    public boolean damagesBlocks() { return blockDamage; }
    public ShotDamage shotDamage() { return damage; }
    public void npcDamage(float scale) { damage = new ShotDamage(true, scale); }
    public void shootLegacy(LivingEntity source, double spread, boolean centered) {
        LegacyShot.shoot(this, source, random, spread, centered, weapon.stats().projectileSpeed());
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected boolean canHitEntity(Entity target) { return target != getOwner() && super.canHitEntity(target); }

    @Override public void tick() {
        if (isRemoved()) return;
        if (origin == null) origin = position();
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 start = position();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        boolean friendly = hit instanceof EntityHitResult entityHit && getOwner() instanceof Player owner
                && entityHit.getEntity() instanceof Player target && !owner.canHarmPlayer(target);
        boolean impact = !friendly && hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit);
        Vec3 end = impact ? hit.getLocation() : start.add(getDeltaMovement());
        boolean detonate = impact && hit instanceof BlockHitResult;
        if (impact && hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            float amount = ExplosiveAmmo.damageAt(weapon.stats(), origin.distanceTo(start));
            amount = target instanceof LivingEntity ? damage.againstEntity(amount) : amount * damage.scale();
            boolean accepted = RocketDamage.hurt(server, getOwner(), damage.source(server, DAMAGE_TYPE, this), target,
                    amount, ExplosiveAmmo.DIRECT_KNOCKBACK);
            // Source calls onHitEffect only for successfully hurt living entities.
            detonate = accepted && target instanceof LivingEntity;
        }
        setPos(end);
        if (impact) {
            if (detonate) explode();
            super.onHit(hit); discard(); return;
        }
        server.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        setDeltaMovement(getDeltaMovement().scale(isInWater() ? (double).85f : (double).99f));
        // The explosive factory does not forward gravity; expiry never detonates.
        if (++age >= weapon.stats().projectileLifetime()) discard();
    }
    public boolean explode() {
        if (isRemoved() || !(level() instanceof ServerLevel server)) return false;
        boolean accepted = ExplosiveBulletExplosion.detonate(server, this);
        discard(); return accepted;
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("weapon", weapon.id()); output.putInt("age", age); output.putBoolean("block_damage", blockDamage); damage.save(output);
        if (origin != null) { output.putDouble("origin_x", origin.x); output.putDouble("origin_y", origin.y); output.putDouble("origin_z", origin.z); }
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            configure(Weapons.definition(input.getStringOr("weapon", "as50")), input.getBooleanOr("block_damage", false));
            damage = ShotDamage.load(input); age = input.getIntOr("age", 0);
            if (age < 0 || age >= weapon.stats().projectileLifetime()) throw new IllegalArgumentException("Expired explosive bullet");
            var x = input.read("origin_x", Codec.DOUBLE); var y = input.read("origin_y", Codec.DOUBLE); var z = input.read("origin_z", Codec.DOUBLE);
            origin = null;
            if (x.isPresent() || y.isPresent() || z.isPresent()) {
                if (x.isEmpty() || y.isEmpty() || z.isEmpty() || !Double.isFinite(x.get()) || !Double.isFinite(y.get()) || !Double.isFinite(z.get()))
                    throw new IllegalArgumentException("Invalid projectile origin");
                origin = new Vec3(x.get(), y.get(), z.get());
            }
        } catch (IllegalArgumentException invalid) { discard(); }
    }
}
