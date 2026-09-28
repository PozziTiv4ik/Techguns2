package techguns.modern.world.structure;

import java.util.*;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.*;
import techguns.core.StructureGrid;
import techguns.core.StructureGrid.Size;

/** Native locate uses spacing()/getPotentialStructureChunk(), so config changes move the actual search grid. */
public final class StructureGridPlacement extends RandomSpreadStructurePlacement {
    private static final Codec<Size> SIZE_CODEC=Codec.STRING.comapFlatMap(key->{
        for(var size:Size.values()) if(size.name().toLowerCase(Locale.ROOT).equals(key)) return DataResult.success(size);
        return DataResult.error(()->"Unknown Techguns structure size: "+key);
    },size->size.name().toLowerCase(Locale.ROOT));
    public static final MapCodec<StructureGridPlacement> CODEC=RecordCodecBuilder.mapCodec(i->placementCodec(i)
        .and(SIZE_CODEC.fieldOf("size").forGetter(StructureGridPlacement::size)).apply(i,StructureGridPlacement::new));
    private final Size size;
    public StructureGridPlacement(Vec3i offset,FrequencyReductionMethod reduction,float frequency,int salt,Optional<ExclusionZone> exclusion,Size size) {
        // These parent fields only initialize vanilla metadata; every grid query below uses the shared config.
        super(offset,reduction,frequency,salt,exclusion,size.defaultInterval(),size.defaultInterval()-1,RandomSpreadType.LINEAR);
        this.size=size;
    }
    public Size size() {return size;}
    @Override public int spacing() {return LocationConfig.grid().interval(size);}
    @Override public int separation() {return spacing()-1;}
    @Override public ChunkPos getPotentialStructureChunk(long seed,int sourceX,int sourceZ) {
        int interval=spacing();return new ChunkPos(StructureGrid.sectorOrigin(sourceX,interval),StructureGrid.sectorOrigin(sourceZ,interval));
    }
    @Override protected boolean isPlacementChunk(ChunkGeneratorStructureState state,int x,int z) {return LocationConfig.grid().accepts(size,x,z);}
    @Override public boolean applyAdditionalChunkRestrictions(int x,int z,long seed) {
        // StructureCheck calls this before its cached terrain/candidate probe. In particular a reserved
        // empty BIG Nether slot must never fall through to a medium/small locate or chunk generation.
        return LocationConfig.accepts(size,x,z)&&x>=-1875000&&x<1875000&&z>=-1875000&&z<1875000
            &&super.applyAdditionalChunkRestrictions(x,z,seed);
    }
    @Override public StructurePlacementType<?> type() {return LocationContent.GRID_PLACEMENT.get();}
}
