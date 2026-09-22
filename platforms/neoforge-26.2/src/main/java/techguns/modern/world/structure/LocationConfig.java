package techguns.modern.world.structure;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class LocationConfig {
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED=BUILDER.comment("Generate Techguns locations in new chunks. Existing structures are never removed.").define("SpawnStructures",true);
    public static final ModConfigSpec.BooleanValue ORE_CLUSTERS=BUILDER.comment("Include original ore-cluster tickets when selecting small Nether and medium Overworld locations. Disabling affects new chunks only.").define("SpawnOreClusterStructures",true);
    private static final ModConfigSpec SPEC=BUILDER.build();
    public static void register(ModContainer container) { container.registerConfig(ModConfig.Type.SERVER,SPEC,"techguns-structures-server.toml"); }
    private LocationConfig() {}
}
