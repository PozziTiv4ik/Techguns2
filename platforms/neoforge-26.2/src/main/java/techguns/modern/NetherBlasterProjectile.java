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
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;
import techguns.core.*;

/** CyberdemonBlasterProjectile: moving FIRE damage, with GenericProjectile drag and displacement falloff. */
public final class NetherBlasterProjectile extends Projectile {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("nether_blast"));
    private WeaponDefinition weapon = Weapons.definition("netherblaster");
    private ShotDamage damage = ShotDamage.PLAYER;
    private Vec3 origin;
    private int age;
    public NetherBlasterProjectile(EntityType<? extends NetherBlasterProjectile> type, Level level) { super(type, level); }
    public void configure(WeaponDefinition gun) {
        if (gun.projectile() != ProjectileKind.NETHER_BLASTER) throw new IllegalArgumentException("Expected Nether Blaster");
        weapon = gun;
    }
    public WeaponDefinition weapon() { return weapon; }
    public void npcDamage(float scale) { damage = new ShotDamage(true, scale); }
    public ShotDamage shotDamage() { return damage; }
    public void shootLegacy(LivingEntity source, double spread, boolean centered) {
        LegacyShot.shoot(this, source, random, spread, centered, weapon.stats().projectileSpeed());
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
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
        // The source calls getDamage at the start of the collision tick, before advancing the projectile.
        if (impact && hit instanceof EntityHitResult entityHit) {
            var target = entityHit.getEntity();
            float amount = weapon.stats().damageAt(origin.distanceTo(start));
            amount = target instanceof LivingEntity ? damage.againstEntity(amount) : amount * damage.scale();
            target.hurtServer(server, damage.source(server, DAMAGE_TYPE, this), amount);
        }
        setPos(end);
        // Temporary visible trail; the original CyberDemonBlasterTrail/Impact FX engine is a separate port.
        server.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, impact ? 8 : 2, .04, .04, .04, .01);
        if (impact) { super.onHit(hit); discard(); return; }
        setDeltaMovement(getDeltaMovement().scale(isInWater() ? (double).85f : (double).99f).add(0, -weapon.gravity(), 0));
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
            configure(Weapons.definition(input.getStringOr("weapon", "netherblaster"))); damage = ShotDamage.load(input);
            age = input.getIntOr("age", 0);
            if (age < 0 || age >= weapon.stats().projectileLifetime()) throw new IllegalArgumentException("Expired projectile");
            var x = input.read("origin_x", com.mojang.serialization.Codec.DOUBLE);
            var y = input.read("origin_y", com.mojang.serialization.Codec.DOUBLE);
            var z = input.read("origin_z", com.mojang.serialization.Codec.DOUBLE);
            if (x.isPresent() || y.isPresent() || z.isPresent()) {
                if (x.isEmpty() || y.isEmpty() || z.isEmpty() || !Double.isFinite(x.get()) || !Double.isFinite(y.get()) || !Double.isFinite(z.get()))
                    throw new IllegalArgumentException("Invalid projectile origin");
                if (Math.abs(x.get()) > 30_000_000 || Math.abs(y.get()) > 30_000_000 || Math.abs(z.get()) > 30_000_000)
                    throw new IllegalArgumentException("Projectile origin outside world bounds");
                origin = new Vec3(x.get(), y.get(), z.get());
            }
        } catch (IllegalArgumentException invalid) { discard(); }
    }
}
