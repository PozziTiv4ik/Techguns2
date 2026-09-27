package techguns.modern.world.structure;

import java.util.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.neoforged.neoforge.common.Tags;
import techguns.core.MilitaryCampRules;
import techguns.modern.world.structure.camp.*;
import techguns.modern.world.structure.camp.CampPart.BiomeColorType;

/** First of the two original big LAND tickets; Castle's unported ticket remains empty. */
public final class MilitaryCampStructure extends Structure {
    public static final MapCodec<MilitaryCampStructure> CODEC=simpleCodec(MilitaryCampStructure::new);
    public MilitaryCampStructure(StructureSettings settings) { super(settings); }
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
        if(biome.is(BiomeTags.IS_OCEAN)||!MilitaryCampRules.selected(context.random().nextInt(2))) return Optional.empty();
        int width=32+context.random().nextInt(48),depth=32+context.random().nextInt(48);
        var columns=new HashMap<Long,NoiseColumn>(); var heights=new HashMap<Long,Integer>();
        var terrain=new CampWorld.Terrain() {
            private NoiseColumn column(int px,int pz) { return columns.computeIfAbsent(CampWorld.column(px,pz),ignored->context.chunkGenerator().getBaseColumn(px,pz,context.heightAccessor(),context.randomState())); }
            @Override public net.minecraft.world.level.block.state.BlockState state(BlockPos pos) { return column(pos.getX(),pos.getZ()).getBlock(pos.getY()); }
            @Override public int top(int px,int pz) {
                return heights.computeIfAbsent(CampWorld.column(px,pz),ignored->{
                    var c=column(px,pz); for(int y=context.heightAccessor().getMaxY()-1;y>=context.heightAccessor().getMinY();y--) if(!c.getBlock(y).isAir()) return y+1;
                    return context.heightAccessor().getMinY();
                });
            }
        };
        var site=MilitaryCampRules.surface(width,depth,(dx,dz)->{
            int px=x+dx,pz=z+dz;
            int h=context.chunkGenerator().getBaseHeight(px,pz,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            return terrain.state(new BlockPos(px,h,pz)).getFluidState().isEmpty()?h:-1;
        });
        if(site.isEmpty()) return Optional.empty(); var chosen=site.get();
        if(chosen.height()+32>=context.heightAccessor().getMaxY()||chosen.height()-16<context.heightAccessor().getMinY()) return Optional.empty();
        var center=context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x+chosen.width()/2),QuartPos.fromBlock(chosen.height()),QuartPos.fromBlock(z+chosen.depth()/2),context.randomState().sampler());
        var color=center.is(Tags.Biomes.IS_SANDY)||center.is(BiomeTags.IS_SAVANNA)||center.is(Tags.Biomes.IS_WASTELAND)?BiomeColorType.DESERT:center.is(Tags.Biomes.IS_SNOWY)?BiomeColorType.SNOW:BiomeColorType.WOODLAND;
        long layoutSeed=context.random().nextLong(),decorationSeed=context.random().nextLong();
        var origin=new BlockPos(x,chosen.height(),z);
        var plan=CampWorld.create(origin,chosen.width(),chosen.depth(),context.heightAccessor().getMinY(),context.heightAccessor().getMaxY(),color,layoutSeed,decorationSeed,terrain);
        var piece=new MilitaryCampPiece(plan,layoutSeed,decorationSeed);
        return Optional.of(new GenerationStub(origin.offset(chosen.width()/2,0,chosen.depth()/2),pieces->pieces.addPiece(piece)));
    }
    @Override public StructureType<?> type() { return LocationContent.MILITARY_CAMP.get(); }
}
