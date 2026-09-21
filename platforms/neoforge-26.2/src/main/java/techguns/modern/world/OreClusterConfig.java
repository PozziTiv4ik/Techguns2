package techguns.modern.world;

import java.util.*;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import techguns.core.OreClusters;

/** Synced original per-cluster settings, shared by tooltips and the future drill. */
public final class OreClusterConfig {
    public record Settings(ModConfigSpec.IntValue miningLevel,ModConfigSpec.DoubleValue ores,ModConfigSpec.DoubleValue power) {}
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    public static final Map<String,Settings> VALUES=create();
    private static Map<String,Settings> create() {
        var values=new LinkedHashMap<String,Settings>();
        for(var v:OreClusters.ALL) values.put(v.id(),new Settings(
                BUILDER.defineInRange("cluster_mininglevel_"+v.type(),v.miningLevel(),0,10),
                BUILDER.defineInRange("cluster_oremult_"+v.type(),v.oreMultiplier(),0.0001,1000),
                BUILDER.defineInRange("cluster_powermult_"+v.type(),v.powerMultiplier(),0.0001,1000)));
        return Collections.unmodifiableMap(values);
    }
    private static final ModConfigSpec SPEC=BUILDER.build();
    public static void register(ModContainer container) { container.registerConfig(ModConfig.Type.SERVER,SPEC,"techguns-ore-clusters-server.toml"); }
    private OreClusterConfig() {}
}
