package techguns.modern.machine.drill;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class OreDrillConfig {
    private static final ModConfigSpec.Builder B=new ModConfigSpec.Builder();
    public static final ModConfigSpec.DoubleValue ORES=B.defineInRange("oreDrillMultiplierOre",1,0.001,1000);
    public static final ModConfigSpec.DoubleValue POWER=B.defineInRange("oreDrillMultiplierPower",1.0,0,1000);
    public static final ModConfigSpec.DoubleValue FUEL=B.defineInRange("oreDrillFuelMultiplier",1000.0,1,100000);
    public static final ModConfigSpec.DoubleValue LIQUID_FUEL=B.defineInRange("oreDrillFuelValueFuel",100.0,1,100000);
    private static final ModConfigSpec SPEC=B.build();
    public static void register(ModContainer c) { c.registerConfig(ModConfig.Type.SERVER,SPEC,"techguns-ore-drill-server.toml"); }
    private OreDrillConfig() {}
}
