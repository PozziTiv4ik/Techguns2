package techguns.modern.world.structure;

import com.mojang.serialization.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class TrainStationStructure extends SmallOverworldStructure {
    public static final MapCodec<TrainStationStructure> CODEC=simpleCodec(TrainStationStructure::new);
    public TrainStationStructure(StructureSettings settings) { super(settings,1,11,12,1,7); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns) { return new TrainStationPiece(context.structureTemplateManager(),origin,turns,context.random().nextLong()); }
    @Override public StructureType<?> type() { return LocationContent.TRAIN.get(); }
}
