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

public final class PoliceStationStructure extends Structure {
    public static final MapCodec<PoliceStationStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,PoliceStationStructure::new));
    private final int big;
    public PoliceStationStructure(StructureSettings settings,int big) { super(settings); this.big=big; }
    public int bigGrid() { return big; }
    @Override public StructureStart generate(Holder<Structure> selected,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            RegistryAccess registries,net.minecraft.world.level.chunk.ChunkGenerator generator,net.minecraft.world.level.biome.BiomeSource biomes,
            net.minecraft.world.level.levelgen.RandomState randomState,net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
            long seed,net.minecraft.world.level.ChunkPos chunk,int references,net.minecraft.world.level.LevelHeightAccessor height,
            java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>> validBiome) {
        if(!dimension.equals(net.minecraft.world.level.Level.OVERWORLD)) return StructureStart.INVALID_START;
        return super.generate(selected,dimension,registries,generator,biomes,randomState,templates,seed,chunk,references,height,validBiome);
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var chunk=context.chunkPos(); if(!LocationConfig.ENABLED.get() || chunk.x()%big==0 && chunk.z()%big==0) return Optional.empty();
        int x=chunk.getMinBlockX(),z=chunk.getMinBlockZ();
        var biome=context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(64),QuartPos.fromBlock(z),context.randomState().sampler());
        if(biome.is(BiomeTags.IS_OCEAN)) return Optional.empty();
        boolean sandy=biome.is(Tags.Biomes.IS_SANDY) || biome.is(Tags.Biomes.IS_WASTELAND),ores=LocationConfig.ORE_CLUSTERS.get(),oil=ClusterOutputs.hasWorldOil();
        if(!PoliceStationRules.selected(context.random().nextInt(BugNestLayout.total(sandy,ores,oil)),sandy,ores,oil)) return Optional.empty();
        int turns=context.random().nextInt(4); int[] shift=StructureRules.originShift(turns,13,13); x+=shift[0]; z+=shift[1];
        int[] heights=new int[16]; int i=0;
        for(int dx:new int[]{0,4,8,12}) for(int dz:new int[]{0,4,8,12}) {
            int h=context.chunkGenerator().getBaseHeight(x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            var column=context.chunkGenerator().getBaseColumn(x+dx,z+dz,context.heightAccessor(),context.randomState());
            heights[i++]=column.getBlock(h).getFluidState().isEmpty()?h:Integer.MIN_VALUE;
        }
        int y=PoliceStationRules.surface(heights);
        if(y==Integer.MIN_VALUE || y-3<context.heightAccessor().getMinY() || y+7>=context.heightAccessor().getMaxY()) return Optional.empty();
        var origin=new BlockPos(x,y,z);
        return Optional.of(new GenerationStub(origin.offset(6,3,6),pieces->pieces.addPiece(new PoliceStationPiece(context.structureTemplateManager(),origin,turns))));
    }
    @Override public StructureType<?> type() { return LocationContent.POLICE.get(); }
}
