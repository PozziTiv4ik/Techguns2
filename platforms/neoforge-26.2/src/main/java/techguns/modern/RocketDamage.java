package techguns.modern;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

public final class RocketDamage {
    public static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("rocket"));
    private record Hit(Entity target, Entity owner, float multiplier) {}
    // NeoForge's knockback event has no DamageSource. Keep context only for this synchronous hurt call.
    private static final ThreadLocal<Hit> ACTIVE = new ThreadLocal<>();
    public static DamageSource source(ServerLevel level, RocketProjectile rocket) {
        return rocket.shotDamage().source(level, TYPE, rocket);
    }
    public static boolean hurt(ServerLevel level, RocketProjectile rocket, Entity target, float amount, float knockback) {
        return hurt(level,rocket.getOwner(),source(level,rocket),target,rocket.shotDamage().againstEntity(amount),knockback);
    }
    public static boolean hurt(ServerLevel level, Entity ownerEntity, DamageSource source, Entity target, float amount, float knockback) {
        if (amount <= 0 || ownerEntity instanceof Player owner && target instanceof Player player && !owner.canHarmPlayer(player)) return false;
        Hit previous = ACTIVE.get();
        ACTIVE.set(new Hit(target, ownerEntity, knockback));
        try { return target.hurtServer(level, source, amount); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }
    public static void knockback(LivingKnockBackEvent event) {
        Hit hit = ACTIVE.get();
        if (hit == null || hit.target != event.getEntity()) return;
        event.setStrength(event.getStrength() * hit.multiplier);
        if (hit.owner != null) {
            event.setRatioX(hit.owner.getX() - event.getEntity().getX());
            event.setRatioZ(hit.owner.getZ() - event.getEntity().getZ());
        }
    }
    private RocketDamage() {}
}
