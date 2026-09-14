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

/** Source ChainsawProjectile: physical damage, two movement ticks, no water drag or bullet tracer. */
public final class ChainsawAttack extends Projectile {
    static final ResourceKey<DamageType> DAMAGE_TYPE = ChainsawItem.DAMAGE;
    private WeaponDefinition weapon = Weapons.definition("chainsaw");
    private double distance;
    private int age;
    private ShotDamage shotDamage = ShotDamage.PLAYER;
    public void npcDamage(float scale) { shotDamage = new ShotDamage(true, scale); }
    public ShotDamage shotDamage() { return shotDamage; }

    public ChainsawAttack(EntityType<? extends ChainsawAttack> type, Level level) { super(type, level); }

    public void configure(WeaponDefinition weapon) {
        if (weapon.projectile() != techguns.core.ProjectileKind.CHAINSAW) throw new IllegalArgumentException("Expected chainsaw");
        this.weapon = weapon;
    }
    public WeaponDefinition weapon() { return weapon; }

    public void shootLegacy(LivingEntity source, double accuracy) {
        shootLegacy(source, accuracy, false);
    }

    public void shootLegacy(LivingEntity source, double accuracy, boolean centered) {
        LegacyShot.shoot(this, source, random, accuracy, centered, weapon.stats().projectileSpeed());
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
        if (impacted && hit instanceof EntityHitResult entityHit) {
            boolean friendlyPlayer = getOwner() instanceof Player owner && entityHit.getEntity() instanceof Player target
                    && !owner.canHarmPlayer(target);
            if (!friendlyPlayer) {
                DamageSource source = shotDamage.source(server, DAMAGE_TYPE, this);
                entityHit.getEntity().hurtServer(server, source, shotDamage.againstEntity(weapon.stats().damageAt(distance)));
            }
        }
        setPos(end);
        if (impacted) {
            server.playSound(null, end.x, end.y, end.z, TGContent.CHAINSAW_IMPACT.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1, 1);
            super.onHit(hit);
            discard();
        } else {
            setDeltaMovement(getDeltaMovement().add(0, -weapon.gravity(), 0));
            if (age >= weapon.stats().projectileLifetime()) discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("distance", distance);
        output.putInt("age", age);
        output.putString("weapon", weapon.id());
        shotDamage.save(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        double stored = input.getDoubleOr("distance", 0);
        distance = Double.isFinite(stored) ? Math.max(0, stored) : 0;
        try {
            shotDamage = ShotDamage.load(input);
            configure(Weapons.definition(input.getStringOr("weapon", "chainsaw")));
        } catch (IllegalArgumentException unknownWeapon) {
            discard();
            return;
        }
        age = Math.clamp(input.getIntOr("age", 0), 0, weapon.stats().projectileLifetime());
    }
}
