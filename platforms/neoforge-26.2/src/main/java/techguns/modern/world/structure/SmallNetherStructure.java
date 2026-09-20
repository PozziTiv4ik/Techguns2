package techguns.modern.world.structure;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;
import techguns.core.StructureRules;

/** Shared original candidate roll and cave search; separate native IDs never reroll on a failed candidate. */
public abstract class SmallNetherStructure extends Structure {
    protected final int medium,big;
    private final int candidate,width,floorOffset,foundationDepth;
    protected SmallNetherStructure(StructureSettings settings,int medium,int big,int candidate,int width,int floorOffset,int foundationDepth) {
        super(settings); this.medium=medium; this.big=big; this.candidate=candidate; this.width=width;
        this.floorOffset=floorOffset; this.foundationDepth=foundationDepth;
    }
    public int mediumGrid() { return medium; }
    public int bigGrid() { return big; }
    protected abstract TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int direction);
    @Override public StructureStart generate(net.minecraft.core.Holder<Structure> selected,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            net.minecraft.core.RegistryAccess registries, net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.biome.BiomeSource biomes, net.minecraft.world.level.levelgen.RandomState randomState,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
            long seed, net.minecraft.world.level.ChunkPos chunk, int references,
            net.minecraft.world.level.LevelHeightAccessor height, java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> validBiome) {
        if(!dimension.equals(net.minecraft.world.level.Level.NETHER)) return StructureStart.INVALID_START;
        return super.generate(selected,dimension,registries,generator,biomes,randomState,templates,seed,chunk,references,height,validBiome);
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var chunk=context.chunkPos();
        // The native random_spread set supplies the small grid, with spacing-1 separation (zero offset).
        if(!LocationConfig.ENABLED.get() || !StructureRules.smallSite(chunk.x(),chunk.z(),1,medium,big)) return Optional.empty();
        boolean clusters=LocationConfig.ORE_CLUSTERS.get();
        if(StructureRules.smallNetherCandidate(context.random().nextInt(StructureRules.smallNetherTotal(clusters)),clusters)!=candidate) return Optional.empty();
        int direction=context.random().nextInt(4),index=0; int[] heights=new int[4];
        for(int x:new int[]{0,15}) for(int z:new int[]{0,15}) {
            var column=context.chunkGenerator().getBaseColumn(chunk.getMinBlockX()+x,chunk.getMinBlockZ()+z,context.heightAccessor(),context.randomState());
            heights[index++]=StructureRules.airFloor(y->column.getBlock(y).isAir());
        }
        int floor=StructureRules.caveHeight(heights); if(floor<0) return Optional.empty();
        var shift=StructureRules.originShift(direction,width,width);
        var origin=new BlockPos(chunk.getMinBlockX()+shift[0],floor+floorOffset,chunk.getMinBlockZ()+shift[1]);
        if(origin.getY()-foundationDepth<context.heightAccessor().getMinY() || origin.getY()+10>=context.heightAccessor().getMaxY()) return Optional.empty();
        return Optional.of(new GenerationStub(origin.offset(width/2,5,width/2),pieces->pieces.addPiece(createPiece(context,origin,direction))));
    }
}
