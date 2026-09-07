package techguns.modern.machine;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class TGMachineConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue MACHINES_NEED_NO_POWER = BUILDER
            .comment("Original Techguns option: allow machines to work without an external energy generator.")
            .define("machinesNeedNoPower", false);
    private static final ModConfigSpec SPEC = BUILDER.build();
    public static void register(ModContainer container) { container.registerConfig(ModConfig.Type.SERVER, SPEC); }
    private TGMachineConfig() {}
}
