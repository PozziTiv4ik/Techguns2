package techguns.modern.world;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import techguns.core.OreDefinition;
import techguns.core.Ores;

public final class TGOreConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final Map<String, ModConfigSpec.BooleanValue> ENABLED = new LinkedHashMap<>();
    public static final Map<String, ModConfigSpec.BooleanValue> ORE_GENERATION = Collections.unmodifiableMap(ENABLED);
    static {
        for (OreDefinition ore : Ores.ALL) ENABLED.put(ore.id(), BUILDER
                .comment("Generate Techguns "+ore.id()+" in new Overworld chunks. Does not remove existing ore or other mods' generation.")
                .define(ore.config(), ore.enabledByDefault()));
    }
    private static final ModConfigSpec SPEC = BUILDER.build();
    public static boolean enabled(OreDefinition ore) { return ENABLED.get(ore.id()).get(); }
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "techguns-worldgen-server.toml");
    }
    private TGOreConfig() {}
}
