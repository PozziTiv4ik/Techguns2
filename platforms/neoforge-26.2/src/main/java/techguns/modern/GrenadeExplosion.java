package techguns.modern;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;

/** TGExplosion called with blockDamageFactor=0, irrespective of the shooter's safe-mode setting. */
final class GrenadeExplosion {
    static boolean detonate(ServerLevel level, GrenadeProjectile grenade) {
        Vec3 center=grenade.position();
        var source=grenade.shotDamage().source(level,GrenadeProjectile.BLAST,grenade);
        var context=new ServerExplosion(level,grenade,source,null,center,(float)grenade.outerRadius(),false,Explosion.BlockInteraction.KEEP);
        if(EventHooks.onExplosionStart(level,context)) return false;
        List<Entity> entities=new ArrayList<>(level.getEntities(grenade,new AABB(center,center).inflate(grenade.outerRadius()+1)));
        List<BlockPos> blocks=new ArrayList<>();
        EventHooks.onExplosionDetonate(level,context,entities,blocks);
        // KEEP is authoritative even when a detonation listener adds blocks to its mutable list.
        for(var entity:new LinkedHashSet<>(entities)) {
            if(entity==grenade || entity.ignoreExplosion(context) || entity.isSpectator() || !entity.isAlive() || !entity.isPickable()) continue;
            float damage=grenade.blastDamage(center.distanceTo(entity.getEyePosition()));
            damage=entity instanceof net.minecraft.world.entity.LivingEntity?grenade.shotDamage().againstEntity(damage):damage*grenade.shotDamage().scale();
            if(damage<=0) continue;
            Vec3 target=entity.position().add(0,entity.getEyeHeight()*.5,0);
            if(level.clip(new ClipContext(center,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,grenade)).getType()!=HitResult.Type.MISS) continue;
            RocketDamage.hurt(level,grenade.getOwner(),source,entity,damage,1);
        }
        level.gameEvent(grenade.getOwner(),GameEvent.EXPLODE,center);
        level.playSound(null,center.x,center.y,center.z,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,4,
                (1+(level.getRandom().nextFloat()-level.getRandom().nextFloat())*.2f)*.7f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,center.x,center.y,center.z,1,0,0,0,0);
        return true;
    }
    private GrenadeExplosion() {}
}
