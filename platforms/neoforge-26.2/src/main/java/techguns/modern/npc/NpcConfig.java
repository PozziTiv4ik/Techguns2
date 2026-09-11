package techguns.modern.npc;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class NpcConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.DoubleValue DAMAGE_FACTOR = BUILDER.comment("Original DamageFactorNPC. Applied to NPC projectile damage in addition to the difficulty penalty.")
            .defineInRange("DamageFactorNPC", 1.0, 0.0, 100.0);
    private static final ModConfigSpec SPEC = BUILDER.build();
    public static void register(ModContainer container) { container.registerConfig(ModConfig.Type.SERVER, SPEC, "techguns-npc-server.toml"); }
    private NpcConfig() {}
}
