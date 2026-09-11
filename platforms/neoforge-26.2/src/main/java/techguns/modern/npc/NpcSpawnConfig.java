package techguns.modern.npc;

import java.util.List;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class NpcSpawnConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue NETHER_WEIGHT = BUILDER.comment("Original Techguns Nether pool weight; restart the server after changing biome spawn settings.")
            .defineInRange("TechgunsSpawnweightNether", 300, 0, 10000);
    public static final ModConfigSpec.IntValue CYBER_WEIGHT = BUILDER.comment("Original CyberDemon weight inside the Nether pool.")
            .defineInRange("SpawnWeightCyberDemon", 30, 0, 10000);
    public static final ModConfigSpec.IntValue PIGMAN_WEIGHT = BUILDER.comment("Reserved original ZombiePigmanSoldier weight. This NPC is not ported yet: its draws currently spawn nothing.")
            .defineInRange("SpawnWeightZombiePigmanSoldier", 100, 0, 10000);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_BLACKLIST = BUILDER.comment("Nether biome identifiers excluded from Techguns spawns; restart required.")
            .defineListAllowEmpty("BiomeBlacklist", List.<String>of(), () -> "minecraft:nether_wastes", value -> value instanceof String text && net.minecraft.resources.Identifier.tryParse(text) != null);
    private static final ModConfigSpec SPEC = BUILDER.build();
    public static void register(ModContainer container) { container.registerConfig(ModConfig.Type.COMMON, SPEC, "techguns-npc-spawns.toml"); }
    private NpcSpawnConfig() {}
}
