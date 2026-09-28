package techguns.modern.world.structure;

import com.mojang.serialization.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class NetherGhastStructure extends MediumNetherStructure {
    public static final MapCodec<NetherGhastStructure> CODEC=simpleCodec(NetherGhastStructure::new);
    public NetherGhastStructure(StructureSettings settings) { super(settings,1,10,-3,13); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns) {
        return new NetherGhastPiece(context.structureTemplateManager(),origin,turns);
    }
    @Override public StructureType<?> type() { return LocationContent.GHAST.get(); }
}
