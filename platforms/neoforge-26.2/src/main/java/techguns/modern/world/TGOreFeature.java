package techguns.modern.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import techguns.core.OreDefinition;
import techguns.core.Ores;

/** One invocation per chunk, preserving the legacy generator's order and random draw boundaries. */
public final class TGOreFeature extends Feature<NoneFeatureConfiguration> {
    public TGOreFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // Biome tags alone do not prevent an Overworld biome from being used in another dimension.
        if (!context.level().getLevel().dimension().equals(Level.OVERWORLD)) return false;
        int chunkX = Math.floorDiv(context.origin().getX(), 16) * 16;
        int chunkZ = Math.floorDiv(context.origin().getZ(), 16) * 16;
        var random = context.random();
        boolean placed = false;
        for (OreDefinition ore : Ores.ALL) {
            if (!TGOreConfig.enabled(ore)) continue;
            int size = ore.minSize() + random.nextInt(ore.maxSize() - ore.minSize() + 1);
            var config = new OreConfiguration(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES),
                    TGOreContent.ORES.get(ore.id()).get().defaultBlockState(), size);
            for (int attempt = 0; attempt < ore.attempts(); attempt++) {
                BlockPos pos = new BlockPos(chunkX + random.nextInt(16), ore.minY() + random.nextInt(ore.maxY() - ore.minY()),
                        chunkZ + random.nextInt(16));
                // Use the pinned game's vein implementation while retaining the mod's distribution rules.
                placed |= Feature.ORE.place(config, context.level(), context.chunkGenerator(), random, pos);
            }
        }
        return placed;
    }
}
