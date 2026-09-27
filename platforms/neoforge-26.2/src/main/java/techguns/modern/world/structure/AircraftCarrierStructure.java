package techguns.modern.world.structure;

import java.util.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.tags.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import techguns.core.AircraftCarrierRules;

/** The sole big WATER candidate. Ocean eligibility never consumes either LAND ticket. */
public final class AircraftCarrierStructure extends Structure {
    public static final MapCodec<AircraftCarrierStructure> CODEC=simpleCodec(AircraftCarrierStructure::new);
    public AircraftCarrierStructure(StructureSettings settings) {super(settings);}
    @Override public StructureStart generate(Holder<Structure> selected,net.minecraft.resources.ResourceKey<Level> dimension,
            RegistryAccess registries,net.minecraft.world.level.chunk.ChunkGenerator generator,net.minecraft.world.level.biome.BiomeSource biomes,
            RandomState randomState,net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
            long seed,ChunkPos chunk,int references,LevelHeightAccessor height,java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>> validBiome) {
        if(!dimension.equals(Level.OVERWORLD)) return StructureStart.INVALID_START;
        return super.generate(selected,dimension,registries,generator,biomes,randomState,templates,seed,chunk,references,height,validBiome);
    }
    /** WORLD_SURFACE_WG includes water. Ice, waterlogged solids and dry terrain reject the site. */
    public static int waterHeight(NoiseColumn column,int top) {
        var state=column.getBlock(top-1);
        return state.getBlock() instanceof LiquidBlock && state.getFluidState().is(FluidTags.WATER)?top:-1;
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if(!LocationConfig.ENABLED.get()) return Optional.empty();
        int x=context.chunkPos().getMinBlockX(),z=context.chunkPos().getMinBlockZ();
        var biome=context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(64),QuartPos.fromBlock(z),context.randomState().sampler());
        if(!biome.is(BiomeTags.IS_OCEAN)) return Optional.empty();
        int y=AircraftCarrierRules.surface((dx,dz)->{
            int px=x+dx,pz=z+dz;var g=context.chunkGenerator();
            int top=g.getBaseHeight(px,pz,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
            return waterHeight(g.getBaseColumn(px,pz,context.heightAccessor(),context.randomState()),top);
        });
        if(y<0||y-3<context.heightAccessor().getMinY()||y+20>=context.heightAccessor().getMaxY()) return Optional.empty();
        int turns=context.random().nextInt(4);var origin=new BlockPos(x,y-3,z);
        var piece=new AircraftCarrierPiece(AircraftCarrierPlan.create(origin,turns,context.random().nextLong()));
        return Optional.of(new GenerationStub(origin.offset(27,3,10),pieces->pieces.addPiece(piece)));
    }
    @Override public StructureType<?> type() {return LocationContent.AIRCRAFT_CARRIER.get();}
}
