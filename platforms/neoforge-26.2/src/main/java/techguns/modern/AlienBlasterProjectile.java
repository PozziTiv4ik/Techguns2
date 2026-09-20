package techguns.modern;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;

/** Ghastling's fixed AlienBlasterProjectile profile. The player Alien Blaster gun remains unported. */
public final class AlienBlasterProjectile extends Projectile {
    public static final ResourceKey<DamageType> DAMAGE_TYPE=ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("alien_blast"));
    public static final float DAMAGE=6;
    public static final int LIFETIME=200,IGNITE_SECONDS=3;
    private int age;
    private static final ShotDamage NPC_DAMAGE=new ShotDamage(true,1);
    public AlienBlasterProjectile(EntityType<? extends AlienBlasterProjectile> type,Level level) { super(type,level); }
    public void shootLegacy(LivingEntity source) { LegacyShot.shoot(this,source,random,.05,true,1.5); }
    @Override protected boolean canHitEntity(Entity entity) { return entity!=getOwner() && super.canHitEntity(entity); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override public void tick() {
        super.tick(); if(!(level() instanceof ServerLevel level)) return;
        HitResult hit=ProjectileUtil.getHitResultOnMoveVector(this,this::canHitEntity);
        boolean impact=hit.getType()!=HitResult.Type.MISS && !EventHooks.onProjectileImpact(this,hit);
        var end=impact?hit.getLocation():position().add(getDeltaMovement());
        if(impact && hit instanceof EntityHitResult entityHit) {
            var target=entityHit.getEntity(); float amount=target instanceof LivingEntity?NPC_DAMAGE.againstEntity(DAMAGE):DAMAGE;
            if(amount>0 && target.hurtServer(level,NPC_DAMAGE.source(level,DAMAGE_TYPE,this),amount) && target instanceof LivingEntity living)
                living.igniteForSeconds(IGNITE_SECONDS);
        }
        setPos(end);
        // Original AlienBlasterTrail/AlienExplosion FX and Albedo lights are still a separate rendering port.
        level.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH,1),end.x,end.y,end.z,impact?8:2,.04,.04,.04,.01);
        if(impact) { super.onHit(hit); discard(); return; }
        setDeltaMovement(getDeltaMovement().scale(isInWater()?(double).85f:(double).99f));
        if(++age>=LIFETIME) discard();
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) { super.addAdditionalSaveData(output); output.putInt("age",age); }
    @Override protected void readAdditionalSaveData(ValueInput input) { super.readAdditionalSaveData(input); age=input.getIntOr("age",0); if(age<0 || age>=LIFETIME) discard(); }
}
