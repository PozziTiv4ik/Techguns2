package techguns.modern.world.structure;

import java.util.Optional;
import net.minecraft.core.*;
import net.minecraft.world.level.levelgen.structure.*;
import techguns.core.*;

/** Shared first RNG roll, cave search and dimension guard for the original medium Nether table. */
public abstract class MediumNetherStructure extends Structure {
    protected final int big;
    private final int candidate,width,floorOffset,top;
    protected MediumNetherStructure(StructureSettings settings,int big,int candidate,int width,int floorOffset,int top) {
        super(settings); this.big=big; this.candidate=candidate; this.width=width; this.floorOffset=floorOffset; this.top=top;
    }
    public int bigGrid() { return big; }
    protected abstract TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns);
    @Override public StructureStart generate(Holder<Structure> selected,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            RegistryAccess registries,net.minecraft.world.level.chunk.ChunkGenerator generator,net.minecraft.world.level.biome.BiomeSource biomes,
            net.minecraft.world.level.levelgen.RandomState randomState,net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
            long seed,net.minecraft.world.level.ChunkPos chunk,int references,net.minecraft.world.level.LevelHeightAccessor height,
            java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>> validBiome) {
        if(!dimension.equals(net.minecraft.world.level.Level.NETHER)) return StructureStart.INVALID_START;
        return super.generate(selected,dimension,registries,generator,biomes,randomState,templates,seed,chunk,references,height,validBiome);
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var chunk=context.chunkPos();
        if(!LocationConfig.ENABLED.get() || chunk.x()%big==0 && chunk.z()%big==0) return Optional.empty();
        boolean clusters=LocationConfig.ORE_CLUSTERS.get();
        // Native random_spread supplies spacing 32 with zero offset. Every candidate sees
        // the same first draw, including when the optional castle's 1000 tickets are disabled.
        if(NetherCastleRules.candidate(context.random().nextInt(NetherCastleRules.total(clusters)),clusters)!=candidate) return Optional.empty();
        int turns=context.random().nextInt(4),index=0; int[] heights=new int[4];
        for(int dx:new int[]{0,15}) for(int dz:new int[]{0,15}) {
            var column=context.chunkGenerator().getBaseColumn(chunk.getMinBlockX()+dx,chunk.getMinBlockZ()+dz,context.heightAccessor(),context.randomState());
            heights[index++]=StructureRules.airFloor(y->column.getBlock(y).isAir());
        }
        int floor=StructureRules.caveHeight(heights); if(floor<0) return Optional.empty();
        int[] shift=StructureRules.originShift(turns,width,width);
        var origin=new BlockPos(chunk.getMinBlockX()+shift[0],floor+floorOffset,chunk.getMinBlockZ()+shift[1]);
        if(origin.getY()-16<context.heightAccessor().getMinY() || origin.getY()+top>=context.heightAccessor().getMaxY()) return Optional.empty();
        return Optional.of(new GenerationStub(origin.offset(width/2,4,width/2),pieces->pieces.addPiece(createPiece(context,origin,turns))));
    }
}
