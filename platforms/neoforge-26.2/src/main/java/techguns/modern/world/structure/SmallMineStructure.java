package techguns.modern.world.structure;

import com.mojang.serialization.*;
import net.minecraft.core.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.*;
import net.neoforged.neoforge.common.Tags;
import techguns.core.SmallMineRules;

public final class SmallMineStructure extends SmallOverworldStructure {
    public static final MapCodec<SmallMineStructure> CODEC=simpleCodec(SmallMineStructure::new);
    public SmallMineStructure(StructureSettings settings) { super(settings,2,17,11,5,5); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns) {
        var chunk=context.chunkPos(); var biome=context.biomeSource().getNoiseBiome(QuartPos.fromBlock(chunk.getMinBlockX()),QuartPos.fromBlock(64),QuartPos.fromBlock(chunk.getMinBlockZ()),context.randomState().sampler());
        return new SmallMinePiece(context.structureTemplateManager(),origin.below(5),turns,
                SmallMineRules.type(context.random().nextInt(SmallMineRules.TYPE_BOUND)),cover(biome),context.random().nextLong());
    }
    public static int cover(Holder<Biome> biome) {
        return SmallMineRules.cover(biome.is(Tags.Biomes.IS_COLD),biome.is(Tags.Biomes.IS_SNOWY),biome.is(Tags.Biomes.IS_SANDY),
                biome.is(Tags.Biomes.IS_BEACH),biome.is(Tags.Biomes.IS_BADLANDS),biome.is(Tags.Biomes.IS_NETHER));
    }
    @Override public StructureType<?> type() { return LocationContent.MINE.get(); }
}
