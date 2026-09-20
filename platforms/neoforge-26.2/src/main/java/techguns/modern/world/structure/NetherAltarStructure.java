package techguns.modern.world.structure;

import java.util.Optional;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;
import techguns.core.StructureRules;

/** Original small Nether choice and four-corner cave test in the native saved structure pipeline. */
public final class NetherAltarStructure extends Structure {
    public static final MapCodec<NetherAltarStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_medium_grid").forGetter(s->s.medium),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,NetherAltarStructure::new));
    private final int medium,big;
    public NetherAltarStructure(StructureSettings settings,int medium,int big) { super(settings); this.medium=medium; this.big=big; }
    public int mediumGrid() { return medium; }
    public int bigGrid() { return big; }
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
        if(!StructureRules.altarSelected(context.random().nextInt(StructureRules.smallNetherTotal(clusters)),clusters)) return Optional.empty();
        int direction=context.random().nextInt(4),index=0; int[] heights=new int[4];
        for(int x:new int[]{0,15}) for(int z:new int[]{0,15}) {
            var column=context.chunkGenerator().getBaseColumn(chunk.getMinBlockX()+x,chunk.getMinBlockZ()+z,context.heightAccessor(),context.randomState());
            heights[index++]=StructureRules.airFloor(y->column.getBlock(y).isAir());
        }
        int floor=StructureRules.caveHeight(heights); if(floor<0) return Optional.empty();
        var shift=StructureRules.altarOriginShift(direction);
        var origin=new BlockPos(chunk.getMinBlockX()+shift[0],floor-3,chunk.getMinBlockZ()+shift[1]);
        if(origin.getY()-16<context.heightAccessor().getMinY() || origin.getY()+10>=context.heightAccessor().getMaxY()) return Optional.empty();
        return Optional.of(new GenerationStub(origin.offset(5,5,5),pieces->pieces.addPiece(new NetherAltarPiece(context.structureTemplateManager(),origin,direction))));
    }
    @Override public StructureType<?> type() { return LocationContent.ALTAR.get(); }
}
