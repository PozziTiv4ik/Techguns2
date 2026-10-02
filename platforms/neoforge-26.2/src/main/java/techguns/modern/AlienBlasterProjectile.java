package techguns.modern;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
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

/** Shared Alien factory. An unconfigured entity retains the original Ghastling save/profile. */
public final class AlienBlasterProjectile extends Projectile {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("alien_blast"));
    private WeaponDefinition weapon;
    private ShotDamage damage = new ShotDamage(true, 1);
    private boolean blockDamage;
    private int age;

    public AlienBlasterProjectile(EntityType<? extends AlienBlasterProjectile> type, Level level) { super(type, level); setNoGravity(true); }
    public void configure(WeaponDefinition gun, boolean blockDamage) {
        if (gun.projectile() != ProjectileKind.ALIEN_BLASTER || gun.stats().damage() != gun.stats().minimumDamage())
            throw new IllegalArgumentException("Expected constant-damage Alien Blaster");
        weapon = gun; this.blockDamage = blockDamage; damage = ShotDamage.PLAYER;
    }
    public WeaponDefinition weapon() { return weapon; }
    public boolean ghastlingProfile() { return weapon == null; }
    public int lifetime() { return ghastlingProfile() ? AlienBlasterRules.GHASTLING_LIFETIME : weapon.stats().projectileLifetime(); }
    public int age() { return age; }
    public boolean damagesBlocks() { return blockDamage; }
    public ShotDamage shotDamage() { return damage; }
    public void npcDamage(float scale) { damage = new ShotDamage(true, scale); }
    public void shootLegacy(LivingEntity source) {
        if (!ghastlingProfile()) throw new IllegalStateException("Centered Ghastling launch requires its own profile");
        LegacyShot.shoot(this, source, random, AlienBlasterRules.GHASTLING_SPREAD, true, AlienBlasterRules.GHASTLING_SPEED);
    }
    public void shootLegacy(LivingEntity source, double spread, int side) {
        if (ghastlingProfile()) throw new IllegalStateException("Weapon launch requires configuration");
        LegacyShot.shoot(this, source, random, spread, side, weapon.stats().projectileSpeed());
    }
    @Override protected boolean canHitEntity(Entity entity) { return entity != getOwner() && super.canHitEntity(entity); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override public void tick() {
        if (isRemoved()) return;
        super.tick(); if (!(level() instanceof ServerLevel level)) return;
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        boolean friendly = hit instanceof EntityHitResult entityHit && getOwner() instanceof Player owner
                && entityHit.getEntity() instanceof Player target && !owner.canHarmPlayer(target);
        boolean impact = !friendly && hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit);
        Vec3 end = impact ? hit.getLocation() : position().add(getDeltaMovement());
        if (impact && hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            float amount = ghastlingProfile() ? AlienBlasterRules.GHASTLING_DAMAGE : weapon.stats().damage();
            amount = target instanceof LivingEntity ? damage.againstEntity(amount) : amount * damage.scale();
            // Source causeFireDamage retains ordinary knockback: no extra 0.01 PHYSICAL hit.
            if (amount > 0 && target.hurtServer(level, damage.source(level, DAMAGE_TYPE, this), amount) && target instanceof LivingEntity living)
                living.igniteForSeconds(AlienBlasterRules.IGNITE_SECONDS);
        } else if (impact && hit instanceof BlockHitResult blockHit) igniteBlock(blockHit);
        setPos(end);
        // Native substitute for AlienBlasterTrail/AlienExplosion. The original has no explosion.
        level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1), end.x, end.y, end.z, impact ? 8 : 2, .04, .04, .04, .01);
        if (impact) { super.onHit(hit); discard(); return; }
        if (isInWater()) level.sendParticles(ParticleTypes.BUBBLE, end.x, end.y, end.z, 4, .02, .02, .02, 0);
        setDeltaMovement(getDeltaMovement().scale(isInWater() ? (double)AlienBlasterRules.WATER_DRAG : (double)AlienBlasterRules.AIR_DRAG));
        if (isInWaterOrRain()) clearFire();
        if (++age >= lifetime()) discard();
    }
    public boolean igniteBlock(BlockHitResult hit) {
        if (!(level() instanceof ServerLevel level) || !blockDamage || !AlienBlasterRules.ignites(random.nextDouble())) return false;
        var pos = hit.getBlockPos().relative(hit.getDirection());
        if (!level.hasChunkAt(pos) || !level.isInWorldBounds(pos) || !level.getBlockState(pos).isAir()) return false;
        return level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("profile", ghastlingProfile() ? "ghastling" : weapon.id());
        output.putInt("age", age); output.putBoolean("block_damage", blockDamage); damage.save(output);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        try {
            String profile = input.getStringOr("profile", "ghastling");
            if (profile.equals("ghastling")) {
                weapon = null; blockDamage = false; damage = new ShotDamage(true, 1);
                if (input.getBooleanOr("block_damage", false) || !input.getBooleanOr("npc_shot", true) || input.getFloatOr("damage_scale", 1) != 1)
                    throw new IllegalArgumentException("Invalid Ghastling profile");
            } else {
                configure(Weapons.definition(profile), input.getBooleanOr("block_damage", false));
                damage = ShotDamage.load(input);
            }
            age = input.getIntOr("age", 0);
            if (age < 0 || age >= lifetime()) throw new IllegalArgumentException("Expired Alien projectile");
        } catch (IllegalArgumentException invalid) { discard(); }
    }
}
