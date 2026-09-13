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
    public static final ModConfigSpec.IntValue PIGMAN_WEIGHT = BUILDER.comment("Original ZombiePigmanSoldier weight inside the Nether pool.")
            .defineInRange("SpawnWeightZombiePigmanSoldier", 100, 0, 10000);
    public static final ModConfigSpec.IntValue OVERWORLD_WEIGHT = BUILDER.comment("Original Overworld pool weight; unported NPC selections currently spawn nothing. Restart required.")
            .defineInRange("TechgunsSpawnweightOverworld", 600, 0, 10000);
    public static final ModConfigSpec.IntValue SOLDIER_WEIGHT = BUILDER.defineInRange("SpawnWeightZombieSoldier",100,0,10000);
    public static final ModConfigSpec.IntValue FARMER_WEIGHT = BUILDER.comment("Original ZombieFarmer weight, available from danger level 0.").defineInRange("SpawnWeightZombieFarmer",200,0,10000);
    public static final ModConfigSpec.IntValue MINER_WEIGHT = BUILDER.comment("Original ZombieMiner weight, available from danger level 0.").defineInRange("SpawnWeightZombieMiner",200,0,10000);
    public static final ModConfigSpec.IntValue SKELETON_WEIGHT = BUILDER.comment("Original SkeletonSoldier weight; available at danger level 1 or above.").defineInRange("SpawnWeightSkeletonSoldier",100,0,10000);
    public static final ModConfigSpec.IntValue PSYCHO_WEIGHT = BUILDER.comment("Reserved original weight: PsychoSteve is not ported yet.").defineInRange("SpawnWeightPsychoSteve",3,0,10000);
    public static final ModConfigSpec.IntValue BANDIT_WEIGHT = BUILDER.comment("Reserved original weight: Bandit is not ported yet.").defineInRange("SpawnWeightBandit",50,0,10000);
    public static final ModConfigSpec.IntValue DISTANCE_0 = BUILDER.defineInRange("DistanceSpawnLevel0",500,0,Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue DISTANCE_1 = BUILDER.defineInRange("DistanceSpawnLevel1",1000,0,Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue DISTANCE_2 = BUILDER.defineInRange("DistanceSpawnLevel2",2500,0,Integer.MAX_VALUE);
    public static techguns.core.OverworldSpawnRules.Weights overworldWeights() {
        return new techguns.core.OverworldSpawnRules.Weights(FARMER_WEIGHT.get(),MINER_WEIGHT.get(),SOLDIER_WEIGHT.get(),SKELETON_WEIGHT.get(),PSYCHO_WEIGHT.get(),BANDIT_WEIGHT.get());
    }
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_BLACKLIST = BUILDER.comment("Biome identifiers excluded from Techguns spawns; restart required.")
            .defineListAllowEmpty("BiomeBlacklist", List.<String>of(), () -> "minecraft:nether_wastes", value -> value instanceof String text && net.minecraft.resources.Identifier.tryParse(text) != null);
    private static final ModConfigSpec SPEC = BUILDER.build();
    public static void register(ModContainer container) { container.registerConfig(ModConfig.Type.COMMON, SPEC, "techguns-npc-spawns.toml"); }
    private NpcSpawnConfig() {}
}
