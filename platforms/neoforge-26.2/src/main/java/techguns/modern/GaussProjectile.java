package techguns.modern;

import net.minecraft.core.particles.DustParticleOptions;
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

/** Gauss/AdvancedBullet inherit GenericProjectile flight and bullet damage without piercing entities or blocks. */
public final class GaussProjectile extends Projectile {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("gauss"));
    private static final DustParticleOptions TRAIL = new DustParticleOptions(0x80BFFF, .65f);
    private WeaponDefinition weapon = Weapons.definition("gaussrifle");
    private ShotDamage damage = ShotDamage.PLAYER;
    private int age;

    public GaussProjectile(EntityType<? extends GaussProjectile> type, Level level) { super(type, level); }
    public void configure(WeaponDefinition gun) {
        if (gun.projectile() != ProjectileKind.GAUSS) throw new IllegalArgumentException("Expected Gauss weapon");
        weapon = gun;
    }
    public WeaponDefinition weapon() { return weapon; }
    public ShotDamage shotDamage() { return damage; }
    public int age() { return age; }
    public void npcDamage(float scale) { damage = new ShotDamage(true, scale); }
    public void shootLegacy(LivingEntity source, double spread, int side) {
        LegacyShot.shoot(this, source, random, spread, side, weapon.stats().projectileSpeed());
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected boolean canHitEntity(Entity target) { return target != getOwner() && super.canHitEntity(target); }

    @Override public void tick() {
        if (isRemoved()) return;
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 start = position();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        boolean friendly = hit instanceof EntityHitResult entityHit && getOwner() instanceof Player owner
                && entityHit.getEntity() instanceof Player target && !owner.canHarmPlayer(target);
        boolean impact = !friendly && hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit);
        Vec3 end = impact ? hit.getLocation() : start.add(getDeltaMovement());
        if (impact && hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            // TGuns has no Gauss damage drop. The factory does not override either damage or gravity.
            float amount = target instanceof LivingEntity ? damage.againstEntity(weapon.stats().damage()) : weapon.stats().damage() * damage.scale();
            if (amount > 0) {
                if (target instanceof LivingEntity) target.hurtServer(server, damage.source(server, BurningProjectile.KNOCKBACK_TYPE, this), .01f);
                target.hurtServer(server, damage.source(server, DAMAGE_TYPE, this), amount);
            }
        }
        setPos(end);
        // A native blue trace keeps the shot visible pending the original entity-bound Gauss FX port.
        int points = Math.clamp((int)Math.ceil(start.distanceTo(end) * 2), 1, 32);
        for (int i = 1; i <= points; i++) {
            Vec3 point = start.lerp(end, (double)i / points);
            server.sendParticles(TRAIL, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        if (impact) { super.onHit(hit); discard(); return; }
        setDeltaMovement(getDeltaMovement().scale(isInWater() ? (double)GaussRules.WATER_DRAG : (double)GaussRules.AIR_DRAG));
        if (++age >= weapon.stats().projectileLifetime()) discard();
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("weapon", weapon.id()); output.putInt("age", age); damage.save(output);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            configure(Weapons.definition(input.getStringOr("weapon", "gaussrifle")));
            damage = ShotDamage.load(input); age = input.getIntOr("age", 0);
            if (age < 0 || age >= weapon.stats().projectileLifetime()) throw new IllegalArgumentException("Expired Gauss projectile");
        } catch (IllegalArgumentException invalid) { discard(); }
    }
}
