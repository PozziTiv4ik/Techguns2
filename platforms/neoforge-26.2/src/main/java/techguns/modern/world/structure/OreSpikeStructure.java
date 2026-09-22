package techguns.modern.world.structure;

import java.util.Optional;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.neoforged.neoforge.common.Tags;
import techguns.core.SpikeRules;
import techguns.modern.machine.drill.ClusterOutputs;

/** First ported medium Overworld location. Native terrain heights/RNG replace the 1.12 population API. */
public final class OreSpikeStructure extends Structure {
    public static final MapCodec<OreSpikeStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,OreSpikeStructure::new));
    private final int big;
    public OreSpikeStructure(StructureSettings settings,int big) { super(settings); this.big=big; }
    @Override public StructureStart generate(Holder<Structure> selected,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            RegistryAccess registries,net.minecraft.world.level.chunk.ChunkGenerator generator,net.minecraft.world.level.biome.BiomeSource biomes,
            net.minecraft.world.level.levelgen.RandomState randomState,net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
            long seed,net.minecraft.world.level.ChunkPos chunk,int references,net.minecraft.world.level.LevelHeightAccessor height,
            java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>> validBiome) {
        if(!dimension.equals(net.minecraft.world.level.Level.OVERWORLD)) return StructureStart.INVALID_START;
        return super.generate(selected,dimension,registries,generator,biomes,randomState,templates,seed,chunk,references,height,validBiome);
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var chunk=context.chunkPos();
        if(!LocationConfig.ENABLED.get() || !LocationConfig.ORE_CLUSTERS.get() || (chunk.x()%big==0 && chunk.z()%big==0)) return Optional.empty();
        int x=chunk.getMinBlockX(),z=chunk.getMinBlockZ();
        var biome=context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(64),QuartPos.fromBlock(z),context.randomState().sampler());
        if(biome.is(BiomeTags.IS_OCEAN)) return Optional.empty();
        boolean sandy=biome.is(Tags.Biomes.IS_SANDY) || biome.is(Tags.Biomes.IS_WASTELAND), oil=ClusterOutputs.hasWorldOil();
        if(!SpikeRules.selected(context.random().nextInt(SpikeRules.total(sandy,oil)),sandy,oil)) return Optional.empty();
        int turns=context.random().nextInt(4),index=0; int[] heights=new int[9];
        for(int dx:new int[]{0,4,8}) for(int dz:new int[]{0,4,8}) {
            int h=context.chunkGenerator().getBaseHeight(x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            var column=context.chunkGenerator().getBaseColumn(x+dx,z+dz,context.heightAccessor(),context.randomState());
            heights[index++]=column.getBlock(h).getFluidState().isEmpty()?h:Integer.MIN_VALUE;
        }
        int y=SpikeRules.surface(heights);
        if(y<context.heightAccessor().getMinY() || y+7>=context.heightAccessor().getMaxY()) return Optional.empty();
        var origin=new BlockPos(x,y,z); int type=SpikeRules.type(context.random().nextInt(46)); long mixture=context.random().nextLong();
        return Optional.of(new GenerationStub(origin.offset(4,3,4),pieces->pieces.addPiece(new OreSpikePiece(context.structureTemplateManager(),origin,turns,type,mixture))));
    }
    @Override public StructureType<?> type() { return LocationContent.SPIKE.get(); }
}
