package techguns.modern;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import techguns.modern.npc.NpcConfig;

/** Persist the launch-time NPC difficulty penalty; the server damage factor is evaluated on impact. */
public record ShotDamage(boolean npc, float scale) {
    public static final ShotDamage PLAYER = new ShotDamage(false, 1);
    public ShotDamage {
        if (!Float.isFinite(scale) || scale < 0 || scale > 1) throw new IllegalArgumentException("Invalid projectile damage scale");
    }
    public float againstEntity(float damage) { return damage * scale * (npc ? NpcConfig.DAMAGE_FACTOR.get().floatValue() : 1); }
    public DamageSource source(ServerLevel level, ResourceKey<DamageType> type, Projectile projectile) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), projectile, projectile.getOwner()) {
            // The source EntityDamageSource points at a non-living projectile. Do not apply a second difficulty multiplier.
            @Override public boolean scalesWithDifficulty() { return !npc && super.scalesWithDifficulty(); }
        };
    }
    public void save(ValueOutput output) { output.putBoolean("npc_shot", npc); output.putFloat("damage_scale", scale); }
    public static ShotDamage load(ValueInput input) { return new ShotDamage(input.getBooleanOr("npc_shot", false), input.getFloatOr("damage_scale", 1)); }
}
