package techguns.modern.world.structure;

import java.util.*;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.neoforged.neoforge.common.Tags;
import techguns.core.BugNestLayout;
import techguns.modern.machine.drill.ClusterOutputs;

/** The first twenty sandy/wasteland medium tickets; independent of the ore-cluster toggle. */
public final class BugNestStructure extends Structure {
    public static final MapCodec<BugNestStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,BugNestStructure::new));
    private final int big;
    public BugNestStructure(StructureSettings settings,int big) { super(settings); this.big=big; }
    @Override public StructureStart generate(Holder<Structure> selected,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            RegistryAccess registries,net.minecraft.world.level.chunk.ChunkGenerator generator,net.minecraft.world.level.biome.BiomeSource biomes,
            RandomState randomState,net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
            long seed,net.minecraft.world.level.ChunkPos chunk,int references,net.minecraft.world.level.LevelHeightAccessor height,
            java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>> validBiome) {
        if(!dimension.equals(net.minecraft.world.level.Level.OVERWORLD)) return StructureStart.INVALID_START;
        return super.generate(selected,dimension,registries,generator,biomes,randomState,templates,seed,chunk,references,height,validBiome);
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var chunk=context.chunkPos(); if(!LocationConfig.ENABLED.get() || chunk.x()%big==0 && chunk.z()%big==0) return Optional.empty();
        int x=chunk.getMinBlockX(),z=chunk.getMinBlockZ();
        var biome=context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(64),QuartPos.fromBlock(z),context.randomState().sampler());
        boolean sandy=biome.is(Tags.Biomes.IS_SANDY) || biome.is(Tags.Biomes.IS_WASTELAND),clusters=LocationConfig.ORE_CLUSTERS.get(),oil=ClusterOutputs.hasWorldOil();
        if(biome.is(BiomeTags.IS_OCEAN) || !sandy || !BugNestLayout.selected(context.random().nextInt(BugNestLayout.total(sandy,clusters,oil)),sandy,clusters,oil)) return Optional.empty();
        int[] heights=new int[4]; int i=0;
        for(int dx:new int[]{0,4}) for(int dz:new int[]{0,4}) {
            int h=context.chunkGenerator().getBaseHeight(x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            var column=context.chunkGenerator().getBaseColumn(x+dx,z+dz,context.heightAccessor(),context.randomState());
            heights[i++]=column.getBlock(h).getFluidState().isEmpty()?h:Integer.MIN_VALUE;
        }
        int surface=BugNestLayout.surface(heights); if(surface==Integer.MIN_VALUE) return Optional.empty();
        var origin=new BugNestLayout.Pos(x,surface-2,z); int width=16+context.random().nextInt(16),depth=16+context.random().nextInt(16);
        long layoutSeed=context.random().nextLong(),decorSeed=context.random().nextLong();
        // Only cells outside the authored geometry ask for terrain (entrance attachments).
        var columns=new HashMap<Long,NoiseColumn>();
        var plan=BugNestLayout.create(origin,width,depth,layoutSeed,decorSeed,p->{
            long key=((long)p.x()<<32)^(p.z()&0xffffffffL);
            var column=columns.computeIfAbsent(key,ignored->context.chunkGenerator().getBaseColumn(p.x(),p.z(),context.heightAccessor(),context.randomState()));
            var state=column.getBlock(p.y()); return state.isAir()?BugNestLayout.AIR:state.canOcclude() && !state.isSignalSource()?BugNestLayout.TERRAIN:-1;
        });
        var piece=new BugNestPiece(plan,layoutSeed,decorSeed); var box=piece.getBoundingBox();
        if(box.minY()<context.heightAccessor().getMinY() || box.maxY()>=context.heightAccessor().getMaxY()) return Optional.empty();
        return Optional.of(new GenerationStub(BugNestPiece.pos(plan.rooms().get(1).center()),pieces->pieces.addPiece(piece)));
    }
    @Override public StructureType<?> type() { return LocationContent.BUGNEST.get(); }
}
