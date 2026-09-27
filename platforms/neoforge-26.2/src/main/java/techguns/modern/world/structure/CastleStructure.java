package techguns.modern.world.structure;

import java.util.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import techguns.core.castle.CastleLayout;

/** Second big LAND ticket, sharing the native candidate RNG with MilitaryCamp. */
public final class CastleStructure extends Structure {
    public static final MapCodec<CastleStructure> CODEC=simpleCodec(CastleStructure::new);
    public CastleStructure(StructureSettings settings) { super(settings); }
    @Override public StructureStart generate(Holder<Structure> selected,net.minecraft.resources.ResourceKey<Level> dimension,
            RegistryAccess registries,net.minecraft.world.level.chunk.ChunkGenerator generator,net.minecraft.world.level.biome.BiomeSource biomes,
            RandomState randomState,net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
            long seed,ChunkPos chunk,int references,LevelHeightAccessor height,java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>> validBiome) {
        if(!dimension.equals(Level.OVERWORLD)) return StructureStart.INVALID_START;
        return super.generate(selected,dimension,registries,generator,biomes,randomState,templates,seed,chunk,references,height,validBiome);
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if(!LocationConfig.ENABLED.get()) return Optional.empty();
        int x=context.chunkPos().getMinBlockX(),z=context.chunkPos().getMinBlockZ();
        var biome=context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(64),QuartPos.fromBlock(z),context.randomState().sampler());
        if(biome.is(BiomeTags.IS_OCEAN)||!CastleLayout.selected(context.random().nextInt(2))) return Optional.empty();
        int width=32+context.random().nextInt(16),depth=32+context.random().nextInt(16),height=24+context.random().nextInt(16),direction=context.random().nextInt(4);
        var site=CastleLayout.surface(width,depth,direction,(dx,dz)->{
            int px=x+dx,pz=z+dz,h=context.chunkGenerator().getBaseHeight(px,pz,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            return context.chunkGenerator().getBaseColumn(px,pz,context.heightAccessor(),context.randomState()).getBlock(h).getFluidState().isEmpty()?h:-1;
        });
        if(site.isEmpty()) return Optional.empty(); var chosen=site.get();
        var origin=new BlockPos(x+chosen.offsetX(),chosen.surface()-5,z+chosen.offsetZ());
        if(origin.getY()<1||origin.getY()+height>=context.heightAccessor().getMaxY()) return Optional.empty();
        // CastleStructure ignores the inherited direction for geometry; it only shifts/samples the site.
        var plan=CastlePlan.create(origin,width,height,depth,context.random().nextLong());
        return Optional.of(new GenerationStub(origin.offset(width/2,5,depth/2),pieces->pieces.addPiece(new CastlePiece(plan))));
    }
    @Override public StructureType<?> type() { return LocationContent.CASTLE.get(); }
}
