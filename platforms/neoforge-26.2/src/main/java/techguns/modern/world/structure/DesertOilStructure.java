package techguns.modern.world.structure;

import java.util.Optional;
import com.mojang.serialization.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.neoforged.neoforge.common.Tags;
import techguns.core.*;
import techguns.modern.machine.drill.ClusterOutputs;

public final class DesertOilStructure extends Structure {
    public static final MapCodec<DesertOilStructure> CODEC=simpleCodec(DesertOilStructure::new);
    public DesertOilStructure(StructureSettings settings) { super(settings); }
    @Override public StructureStart generate(Holder<Structure> selected,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            RegistryAccess registries,net.minecraft.world.level.chunk.ChunkGenerator generator,net.minecraft.world.level.biome.BiomeSource biomes,
            net.minecraft.world.level.levelgen.RandomState randomState,net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
            long seed,net.minecraft.world.level.ChunkPos chunk,int references,net.minecraft.world.level.LevelHeightAccessor height,
            java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>> validBiome) {
        if(!dimension.equals(net.minecraft.world.level.Level.OVERWORLD)) return StructureStart.INVALID_START;
        return super.generate(selected,dimension,registries,generator,biomes,randomState,templates,seed,chunk,references,height,validBiome);
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var chunk=context.chunkPos(); if(!LocationConfig.accepts(StructureGrid.Size.MEDIUM,chunk.x(),chunk.z()) || !LocationConfig.ORE_CLUSTERS.get()) return Optional.empty();
        var oilFluid=ClusterOutputs.worldOil(); if(oilFluid==Fluids.EMPTY) return Optional.empty();
        int x=chunk.getMinBlockX(),z=chunk.getMinBlockZ();
        var biome=context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(64),QuartPos.fromBlock(z),context.randomState().sampler());
        if(biome.is(BiomeTags.IS_OCEAN)) return Optional.empty();
        boolean sandy=biome.is(Tags.Biomes.IS_SANDY) || biome.is(Tags.Biomes.IS_WASTELAND); if(!sandy) return Optional.empty();
        if(!DesertOilRules.selected(context.random().nextInt(BugNestLayout.total(sandy,true,true)),sandy,true,true)) return Optional.empty();
        int turns=context.random().nextInt(4); int[] shift=StructureRules.originShift(turns,11,11); x+=shift[0]; z+=shift[1];
        int[] heights=new int[9]; int i=0;
        for(int dx:new int[]{0,4,8}) for(int dz:new int[]{0,4,8}) {
            int h=context.chunkGenerator().getBaseHeight(x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            var column=context.chunkGenerator().getBaseColumn(x+dx,z+dz,context.heightAccessor(),context.randomState());
            heights[i++]=column.getBlock(h).getFluidState().isEmpty()?h:Integer.MIN_VALUE;
        }
        int floor=DesertOilRules.surface(heights); if(floor==Integer.MIN_VALUE) return Optional.empty(); int y=floor-4;
        if(y<context.heightAccessor().getMinY() || y+9>=context.heightAccessor().getMaxY()) return Optional.empty();
        var origin=new BlockPos(x,y,z);
        context.random().nextInt(11); // Original single-type, inclusive cluster-weight roll.
        long mixture=context.random().nextLong(); var oil=BuiltInRegistries.FLUID.getKey(oilFluid);
        return Optional.of(new GenerationStub(origin.offset(5,4,5),pieces->pieces.addPiece(new DesertOilPiece(context.structureTemplateManager(),origin,turns,oil,mixture))));
    }
    @Override public StructureType<?> type() { return LocationContent.DESERT_OIL.get(); }
}
