package techguns.modern.world.structure;

import com.mojang.serialization.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class FactoryHouseStructure extends SmallOverworldStructure {
    public static final MapCodec<FactoryHouseStructure> CODEC=simpleCodec(FactoryHouseStructure::new);
    public FactoryHouseStructure(StructureSettings settings) { super(settings,0,11,10,3,7); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns) { return new FactoryHousePiece(context.structureTemplateManager(),origin,turns); }
    @Override public StructureType<?> type() { return LocationContent.FACTORY.get(); }
}
