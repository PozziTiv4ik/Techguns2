package techguns.modern.world.structure;

import java.util.Optional;
import net.minecraft.core.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import techguns.core.*;

/** Shared candidate roll keeps the four native small LAND structure IDs disjoint. */
public abstract class SmallOverworldStructure extends Structure {
    private final int candidate,width,depth,foundationDepth,top;
    protected SmallOverworldStructure(StructureSettings settings,int candidate,int width,int depth,int foundationDepth,int top) {
        super(settings); this.candidate=candidate; this.width=width; this.depth=depth; this.foundationDepth=foundationDepth; this.top=top;
    }
    protected abstract TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns);
    @Override public StructureStart generate(Holder<Structure> selected,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            RegistryAccess registries,net.minecraft.world.level.chunk.ChunkGenerator generator,net.minecraft.world.level.biome.BiomeSource biomes,
            net.minecraft.world.level.levelgen.RandomState randomState,net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
            long seed,net.minecraft.world.level.ChunkPos chunk,int references,net.minecraft.world.level.LevelHeightAccessor height,
            java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>> validBiome) {
        if(!dimension.equals(net.minecraft.world.level.Level.OVERWORLD)) return StructureStart.INVALID_START;
        return super.generate(selected,dimension,registries,generator,biomes,randomState,templates,seed,chunk,references,height,validBiome);
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var chunk=context.chunkPos(); if(!LocationConfig.accepts(StructureGrid.Size.SMALL,chunk.x(),chunk.z())) return Optional.empty();
        int x=chunk.getMinBlockX(),z=chunk.getMinBlockZ();
        var biome=context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(64),QuartPos.fromBlock(z),context.randomState().sampler());
        if(biome.is(BiomeTags.IS_OCEAN)) return Optional.empty();
        if(SmallOverworldRules.candidate(context.random().nextInt(SmallOverworldRules.TOTAL))!=candidate) return Optional.empty();
        int turns=context.random().nextInt(4); int[] shift=StructureRules.originShift(turns,width,depth); x+=shift[0]; z+=shift[1];
        int[] heights=new int[(width/4+1)*(depth/4+1)]; int i=0;
        for(int dx=0;dx<=((turns&1)==0?width:depth);dx+=4) for(int dz=0;dz<=((turns&1)==0?depth:width);dz+=4) {
            int h=context.chunkGenerator().getBaseHeight(x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            var column=context.chunkGenerator().getBaseColumn(x+dx,z+dz,context.heightAccessor(),context.randomState());
            heights[i++]=column.getBlock(h).getFluidState().isEmpty()?h:Integer.MIN_VALUE;
        }
        int y=SmallOverworldRules.surface((width/4+1)*(depth/4+1),heights);
        if(y==Integer.MIN_VALUE || y-foundationDepth<context.heightAccessor().getMinY() || y+top>=context.heightAccessor().getMaxY()) return Optional.empty();
        var origin=new BlockPos(x,y,z);
        return Optional.of(new GenerationStub(origin.offset(width/2,top/2,depth/2),pieces->pieces.addPiece(createPiece(context,origin,turns))));
    }
}
