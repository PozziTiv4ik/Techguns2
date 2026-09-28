package techguns.modern.world.structure;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import techguns.core.StructureGrid;

public final class LocationConfig {
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED=BUILDER.comment("Generate Techguns locations in new chunks. Existing structures are never removed.").define("SpawnStructures",true);
    public static final ModConfigSpec.BooleanValue ORE_CLUSTERS=BUILDER.comment("Include original ore-cluster tickets when selecting small Nether and medium Overworld locations. Disabling affects new chunks only.").define("SpawnOreClusterStructures",true);
    public static final ModConfigSpec.IntValue SMALL=interval("StructureSpawnWeightSmall",StructureGrid.Size.SMALL);
    public static final ModConfigSpec.IntValue MEDIUM=interval("StructureSpawnWeightMedium",StructureGrid.Size.MEDIUM);
    public static final ModConfigSpec.IntValue BIG=interval("StructureSpawnWeightBig",StructureGrid.Size.BIG);
    private static final ModConfigSpec SPEC=BUILDER.build();
    private static ModConfigSpec.IntValue interval(String name,StructureGrid.Size size) {
        return BUILDER.comment("Try this structure size where both chunk coordinates are multiples of this interval. BIG reserves before MEDIUM, then SMALL. Applies to new chunks after reopening the world/server.")
            .worldRestart().defineInRange(name,size.defaultInterval(),size.minimum(),StructureGrid.MAX_INTERVAL);
    }
    public static StructureGrid grid() {return SPEC.isLoaded()?new StructureGrid(SMALL.get(),MEDIUM.get(),BIG.get()):StructureGrid.DEFAULT;}
    public static boolean accepts(StructureGrid.Size size,int x,int z) {return (!SPEC.isLoaded()||ENABLED.get())&&grid().accepts(size,x,z);}
    public static void register(ModContainer container) { container.registerConfig(ModConfig.Type.SERVER,SPEC,"techguns-structures-server.toml"); }
    private LocationConfig() {}
}
