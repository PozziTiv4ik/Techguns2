package techguns.modern.world.structure;

import java.util.Optional;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.neoforged.neoforge.common.Tags;
import techguns.core.*;
import techguns.modern.machine.drill.ClusterOutputs;

/** The original medium LAND slot after OreClusterSpike, with 17x17 surface sampling. */
public final class MeteorStructure extends Structure {
    public static final MapCodec<MeteorStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,MeteorStructure::new));
    private final int big;
    public MeteorStructure(StructureSettings settings,int big) { super(settings); this.big=big; }
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
        if(!MeteorRules.selected(context.random().nextInt(SpikeRules.total(sandy,oil)),sandy,oil)) return Optional.empty();
        int turns=context.random().nextInt(4),index=0; int[] heights=new int[25];
        for(int dx:new int[]{0,4,8,12,16}) for(int dz:new int[]{0,4,8,12,16}) {
            int h=context.chunkGenerator().getBaseHeight(x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            var column=context.chunkGenerator().getBaseColumn(x+dx,z+dz,context.heightAccessor(),context.randomState());
            heights[index++]=column.getBlock(h).getFluidState().isEmpty()?h:Integer.MIN_VALUE;
        }
        int floor=MeteorRules.surface(heights);
        if(floor==Integer.MIN_VALUE) return Optional.empty();
        int originY=floor-5;
        if(originY<context.heightAccessor().getMinY() || originY+35>=context.heightAccessor().getMaxY()) return Optional.empty();
        int[] shift=StructureRules.originShift(turns,17,17);
        var origin=new BlockPos(x+shift[0],originY,z+shift[1]);
        int type=MeteorRules.type(context.random().nextInt(51)); long mixture=context.random().nextLong();
        return Optional.of(new GenerationStub(origin.offset(8,10,8),pieces->pieces.addPiece(new MeteorPiece(context.structureTemplateManager(),origin,turns,type,mixture))));
    }
    @Override public StructureType<?> type() { return LocationContent.METEOR.get(); }
}
