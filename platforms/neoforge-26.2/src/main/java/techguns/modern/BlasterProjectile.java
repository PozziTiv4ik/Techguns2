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

/** The moving ENERGY factory used by Scatterbeam; GenericProjectile with no water slowdown. */
public final class BlasterProjectile extends Projectile {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("blaster"));
    private static final DustParticleOptions IMPACT = new DustParticleOptions(0xFF6030, .65f);
    private WeaponDefinition weapon = Weapons.definition("scatterbeamrifle");
    private ShotDamage damage = ShotDamage.PLAYER;
    private int age;

    public BlasterProjectile(EntityType<? extends BlasterProjectile> type, Level level) { super(type, level); setNoGravity(true); }
    public void configure(WeaponDefinition gun) {
        if (gun.projectile() != ProjectileKind.BLASTER) throw new IllegalArgumentException("Expected Blaster weapon");
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
            // Scatterbeam has constant damage; every pellet applies its own accepted ENERGY hit.
            float amount = target instanceof LivingEntity ? damage.againstEntity(weapon.stats().damage()) : weapon.stats().damage() * damage.scale();
            if (amount > 0) {
                if (target instanceof LivingEntity) target.hurtServer(server, damage.source(server, BurningProjectile.KNOCKBACK_TYPE, this), .01f);
                target.hurtServer(server, damage.source(server, DAMAGE_TYPE, this), amount);
            }
        }
        setPos(end);
        if (impact) {
            // Native visual substitute for LaserGunImpact; the original branch never modifies blocks.
            if (hit instanceof BlockHitResult) server.sendParticles(IMPACT, end.x, end.y, end.z, 6, .04, .04, .04, 0);
            super.onHit(hit); discard(); return;
        }
        setDeltaMovement(getDeltaMovement().scale((double)BlasterRules.DRAG));
        if (isInWaterOrRain()) clearFire();
        if (++age >= weapon.stats().projectileLifetime()) discard();
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("weapon", weapon.id()); output.putInt("age", age); damage.save(output);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            configure(Weapons.definition(input.getStringOr("weapon", "scatterbeamrifle")));
            damage = ShotDamage.load(input); age = input.getIntOr("age", 0);
            if (age < 0 || age >= weapon.stats().projectileLifetime()) throw new IllegalArgumentException("Expired Blaster projectile");
        } catch (IllegalArgumentException invalid) { discard(); }
    }
}
