package techguns.modern.world.structure;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class TrainStationStructure extends SmallOverworldStructure {
    public static final MapCodec<TrainStationStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(settingsCodec(i),
            Codec.intRange(1,100000).fieldOf("reserved_medium_grid").forGetter(s->s.medium),
            Codec.intRange(1,100000).fieldOf("reserved_big_grid").forGetter(s->s.big)).apply(i,TrainStationStructure::new));
    public TrainStationStructure(StructureSettings settings,int medium,int big) { super(settings,medium,big,1,11,12,1,7); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns) { return new TrainStationPiece(context.structureTemplateManager(),origin,turns,context.random().nextLong()); }
    @Override public StructureType<?> type() { return LocationContent.TRAIN.get(); }
}
