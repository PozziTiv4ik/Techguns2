package techguns.modern.world.structure;

import com.mojang.serialization.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class NetherMediumAltarStructure extends MediumNetherStructure {
    public static final MapCodec<NetherMediumAltarStructure> CODEC=simpleCodec(NetherMediumAltarStructure::new);
    public NetherMediumAltarStructure(StructureSettings settings) { super(settings,0,16,-2,7); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int turns) {
        return new NetherMediumAltarPiece(context.structureTemplateManager(),origin,turns);
    }
    @Override public StructureType<?> type() { return LocationContent.MEDIUM_ALTAR.get(); }
}
