package techguns.modern.world.structure;

import com.mojang.serialization.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class GasStationStructure extends SmallOverworldStructure {
    public static final MapCodec<GasStationStructure> CODEC=simpleCodec(GasStationStructure::new);
    public GasStationStructure(StructureSettings settings) { super(settings,3,9,12,3,7); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns) { return new GasStationPiece(context.structureTemplateManager(),origin,turns); }
    @Override public StructureType<?> type() { return LocationContent.GAS.get(); }
}
