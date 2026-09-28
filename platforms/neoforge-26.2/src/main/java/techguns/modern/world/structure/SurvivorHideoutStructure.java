package techguns.modern.world.structure;

import java.util.Optional;
import com.mojang.serialization.*;
import net.minecraft.core.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.neoforged.neoforge.common.Tags;
import techguns.core.*;
import techguns.modern.machine.drill.ClusterOutputs;

public final class SurvivorHideoutStructure extends Structure {
    public static final MapCodec<SurvivorHideoutStructure> CODEC=simpleCodec(SurvivorHideoutStructure::new);
    public SurvivorHideoutStructure(StructureSettings settings) { super(settings); }
    @Override public StructureStart generate(Holder<Structure> selected,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            RegistryAccess registries,net.minecraft.world.level.chunk.ChunkGenerator generator,net.minecraft.world.level.biome.BiomeSource biomes,
            net.minecraft.world.level.levelgen.RandomState randomState,net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
            long seed,net.minecraft.world.level.ChunkPos chunk,int references,net.minecraft.world.level.LevelHeightAccessor height,
            java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>> validBiome) {
        if(!dimension.equals(net.minecraft.world.level.Level.OVERWORLD)) return StructureStart.INVALID_START;
        return super.generate(selected,dimension,registries,generator,biomes,randomState,templates,seed,chunk,references,height,validBiome);
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var chunk=context.chunkPos(); if(!LocationConfig.accepts(StructureGrid.Size.MEDIUM,chunk.x(),chunk.z())) return Optional.empty();
        int x=chunk.getMinBlockX(),z=chunk.getMinBlockZ();
        var biome=context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(64),QuartPos.fromBlock(z),context.randomState().sampler());
        if(biome.is(BiomeTags.IS_OCEAN)) return Optional.empty();
        boolean sandy=biome.is(Tags.Biomes.IS_SANDY) || biome.is(Tags.Biomes.IS_WASTELAND),ores=LocationConfig.ORE_CLUSTERS.get(),oil=ClusterOutputs.hasWorldOil();
        if(!SurvivorHideoutRules.selected(context.random().nextInt(BugNestLayout.total(sandy,ores,oil)),sandy,ores,oil)) return Optional.empty();
        int turns=context.random().nextInt(4); int[] shift=StructureRules.originShift(turns,11,19); x+=shift[0]; z+=shift[1];
        int[] heights=new int[15]; int i=0;
        for(int dx=0;dx<((turns&1)==0?11:19);dx+=4) for(int dz=0;dz<((turns&1)==0?19:11);dz+=4) {
            int h=context.chunkGenerator().getBaseHeight(x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            var column=context.chunkGenerator().getBaseColumn(x+dx,z+dz,context.heightAccessor(),context.randomState());
            heights[i++]=column.getBlock(h).getFluidState().isEmpty()?h:Integer.MIN_VALUE;
        }
        int y=SurvivorHideoutRules.surface(heights);
        if(y==Integer.MIN_VALUE || y-3<context.heightAccessor().getMinY() || y+10>=context.heightAccessor().getMaxY()) return Optional.empty();
        var origin=new BlockPos(x,y,z); int panel=context.random().nextInt(4),canopy=canopy(biome);
        return Optional.of(new GenerationStub(origin.offset(5,5,9),pieces->pieces.addPiece(new SurvivorHideoutPiece(context.structureTemplateManager(),origin,turns,panel,canopy))));
    }
    public static int canopy(Holder<net.minecraft.world.level.biome.Biome> biome) {
        return SurvivorHideoutRules.canopy(biome.is(Tags.Biomes.IS_COLD),biome.is(Tags.Biomes.IS_SNOWY),
                biome.is(Tags.Biomes.IS_SANDY),biome.is(Tags.Biomes.IS_BEACH),biome.is(Tags.Biomes.IS_BADLANDS));
    }
    @Override public StructureType<?> type() { return LocationContent.SURVIVOR.get(); }
}
