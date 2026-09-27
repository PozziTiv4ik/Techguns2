package techguns.modern;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import techguns.core.FlameRules;

/** Accepted shots only: source per-hand restart delay, finite audio samples, and non-resetting recoil. */
public final class FlameFiring {
    private record SoundStamp(ResourceKey<Level> dimension, long time) {}
    private static final Map<Player, EnumMap<InteractionHand, SoundStamp>> SOUNDS = new WeakHashMap<>();
    public static void playerShot(ServerLevel level, Player player, ItemStack stack) {
        var hand = player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        var times = SOUNDS.computeIfAbsent(player, p -> new EnumMap<>(InteractionHand.class));
        var last = times.get(hand); long now = level.getGameTime();
        boolean start = last == null || !last.dimension.equals(level.dimension()) || FlameRules.startsSound(last.time, now);
        times.put(hand, new SoundStamp(level.dimension(), now));
        var sound = start ? TGContent.FLAME_START.get() : TGContent.SOUND_EVENTS.get("guns.flamethrowerfire").get();
        Vec3 pos = soundPosition(player);
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, 4, 1);
        recoil(level, stack);
        muzzle(level, player, LegacyShot.muzzleSide(player, hand == InteractionHand.OFF_HAND));
    }
    public static Vec3 soundPosition(LivingEntity source) {
        double yaw = Math.toRadians(source.getYHeadRot());
        return source.position().add(-Math.sin(yaw), source.getBbHeight() * .5, Math.cos(yaw));
    }
    public static void recoil(ServerLevel level, ItemStack stack) {
        long now = level.getGameTime();
        if (FlameRules.startsRecoil(stack.getOrDefault(TGContent.FLAME_RECOIL_TIME.get(), -1L), now))
            stack.set(TGContent.FLAME_RECOIL_TIME.get(), now);
    }
    public static void muzzle(ServerLevel level, LivingEntity source, int side) {
        double yaw = Math.toRadians(source.getYHeadRot());
        Vec3 forward = Vec3.directionFromRotation(source.getXRot(), source.getYHeadRot());
        Vec3 pos = source.getEyePosition().add(Math.cos(yaw) * side * .15, -.05, Math.sin(yaw) * side * .15).add(forward.scale(.5));
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 2, .015, .015, .015, .01);
    }
    private FlameFiring() {}
}
