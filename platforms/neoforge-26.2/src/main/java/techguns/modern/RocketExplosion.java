package techguns.modern;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.EventHooks;
import techguns.core.RocketVariant;

/** TGExplosion's resistance rays, sparse outer loot and single visibility ray, using modern explosion hooks. */
public final class RocketExplosion {
    public static boolean detonate(ServerLevel level, RocketProjectile rocket) {
        var variant = rocket.variant(); var gun = rocket.weapon().stats(); Vec3 center = rocket.position();
        double inner = variant.innerRadius(gun), outer = variant.outerRadius(gun);
        var context = new ServerExplosion(level, rocket, RocketDamage.source(level, rocket), null, center, (float) outer, false,
                rocket.damagesBlocks() ? Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.KEEP);
        if (EventHooks.onExplosionStart(level, context)) return false;
        List<BlockPos> blocks = rocket.damagesBlocks() ? affectedBlocks(level, rocket, context) : new ArrayList<>();
        List<Entity> entities = new ArrayList<>(level.getEntities(rocket, new AABB(center, center).inflate(outer + 1)));
        EventHooks.onExplosionDetonate(level, context, entities, blocks);
        // Legacy breaks walls before tracing visibility to entities. KEEP remains authoritative even if an event adds blocks.
        if (rocket.damagesBlocks()) for (BlockPos pos : new LinkedHashSet<>(blocks)) {
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.isAir()) continue;
            double distance = center.distanceTo(Vec3.atCenterOf(pos));
            double chance = (variant == RocketVariant.NUKE ? .05 : .25) * (distance - inner) / (outer - inner);
            if (distance > inner && level.getRandom().nextDouble() < chance && state.canDropFromExplosion(level, pos, context)) {
                var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                        .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                        .withOptionalParameter(LootContextParams.BLOCK_ENTITY, state.hasBlockEntity() ? level.getBlockEntity(pos) : null)
                        .withOptionalParameter(LootContextParams.THIS_ENTITY, context.getDirectSourceEntity());
                state.getDrops(params).forEach(stack -> Block.popResource(level, pos, stack));
            }
            state.onBlockExploded(level, pos, context);
        }
        for (Entity entity : new LinkedHashSet<>(entities)) {
            if (entity == rocket || entity.ignoreExplosion(context) || entity.isSpectator() || !entity.isAlive() || !entity.isPickable()) continue;
            float damage = variant.blastDamage(gun, center.distanceTo(entity.getEyePosition()));
            if (damage <= 0) continue;
            Vec3 target = entity.position().add(0, entity.getEyeHeight() * .5, 0);
            if (level.clip(new ClipContext(center, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rocket)).getType() != HitResult.Type.MISS) continue;
            RocketDamage.hurt(level, rocket, entity, damage, variant == RocketVariant.NUKE ? 3 : 1);
        }
        level.gameEvent(rocket.getOwner(), GameEvent.EXPLODE, center);
        if (variant == RocketVariant.NUKE) level.playSound(null, center.x, center.y, center.z, TGContent.NUKE_EXPLOSION.get(), SoundSource.BLOCKS, 4, 1);
        else level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4,
                (1 + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * .2f) * .7f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        if (variant == RocketVariant.NUKE) level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 120, inner * .4, inner * .4, inner * .4, .1);
        return true;
    }
    private static List<BlockPos> affectedBlocks(ServerLevel level, RocketProjectile rocket, ServerExplosion context) {
        Set<BlockPos> affected = new LinkedHashSet<>();
        int radius = (int) Math.ceil(context.radius());
        double step = 0.30000001192092896D;
        int steps = (int) Math.ceil(radius / step);
        Vec3 center = context.center();
        for (int j = -radius; j < radius; ++j) for (int k = -radius; k < radius; ++k) for (int l = -radius; l < radius; ++l) {
            if (j != -radius && j != radius - 1 && k != -radius && k != radius - 1 && l != -radius && l != radius - 1) continue;
            Vec3 direction = new Vec3((double) ((float) j / radius), (double) ((float) k / radius), (double) ((float) l / radius)).normalize().scale(step);
            Vec3 offset = Vec3.ZERO; double damping = 0;
            BlockPos previous = null;
            for (int i = 0; i < steps; i++, offset = offset.add(direction)) {
                BlockPos pos = BlockPos.containing(center.add(offset));
                // Do not generate chunks for a blast at the edge of the active simulation.
                if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) break;
                var state = level.getBlockState(pos);
                var variant = rocket.variant(); var gun = rocket.weapon().stats();
                double power = techguns.core.ExplosionMath.band(center.distanceTo(Vec3.atCenterOf(pos)),
                        variant.innerRadius(gun), variant.outerRadius(gun), variant.damage(gun), variant.minimumDamage(gun));
                if (power <= 0) break;
                if (!state.isAir()) {
                    float resistance = state.getExplosionResistance(level, pos, context);
                    Entity owner = rocket.getOwner();
                    if (power - damping > 0 && resistance < (power - damping) * .5
                            && (owner == null || owner.shouldBlockExplode(context, level, pos, state, (float) power))) {
                        affected.add(pos.immutable());
                        if (!pos.equals(previous)) damping += resistance;
                    } else break;
                }
                previous = pos;
            }
        }
        return new ArrayList<>(affected);
    }
    private RocketExplosion() {}
}
