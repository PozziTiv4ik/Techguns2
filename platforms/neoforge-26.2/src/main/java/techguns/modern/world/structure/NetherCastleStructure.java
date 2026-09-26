package techguns.modern.world.structure;

import java.util.Optional;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.world.level.levelgen.structure.*;
import techguns.core.*;

/** First medium Nether location; unported altar/ghast candidates retain their original tickets. */
public final class NetherCastleStructure extends Structure {
    public static final MapCodec<NetherCastleStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,NetherCastleStructure::new));
    private final int big;
    public NetherCastleStructure(StructureSettings settings,int big) { super(settings); this.big=big; }
    public int bigGrid() { return big; }
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
        if(!LocationConfig.ENABLED.get() || !LocationConfig.ORE_CLUSTERS.get() || chunk.x()%big==0 && chunk.z()%big==0) return Optional.empty();
        // Native random_spread supplies spacing 32 with zero offset; the first RNG roll is shared
        // with future medium candidates, so a rejected cave never rerolls as a different location.
        if(NetherCastleRules.candidate(context.random().nextInt(NetherCastleRules.total(true)),true)!=2) return Optional.empty();
        int turns=context.random().nextInt(4),index=0; int[] heights=new int[4];
        for(int dx:new int[]{0,15}) for(int dz:new int[]{0,15}) {
            var column=context.chunkGenerator().getBaseColumn(chunk.getMinBlockX()+dx,chunk.getMinBlockZ()+dz,context.heightAccessor(),context.randomState());
            heights[index++]=StructureRules.airFloor(y->column.getBlock(y).isAir());
        }
        int floor=StructureRules.caveHeight(heights); if(floor<0) return Optional.empty();
        int[] shift=StructureRules.originShift(turns,11,11);
        // spawnStructureCaveWorldgen passes floor-1; this source class adds hoffset=-1.
        var origin=new BlockPos(chunk.getMinBlockX()+shift[0],floor-2,chunk.getMinBlockZ()+shift[1]);
        if(origin.getY()-16<context.heightAccessor().getMinY() || origin.getY()+8>=context.heightAccessor().getMaxY()) return Optional.empty();
        long mixtureSeed=context.random().nextLong();
        return Optional.of(new GenerationStub(origin.offset(5,4,5),pieces->pieces.addPiece(new NetherCastlePiece(context.structureTemplateManager(),origin,turns,mixtureSeed))));
    }
    @Override public StructureType<?> type() { return LocationContent.NETHER_CASTLE.get(); }
}
