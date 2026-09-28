package techguns.modern.world.structure;

import com.mojang.serialization.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class NetherAcidStructure extends SmallNetherStructure {
    public static final MapCodec<NetherAcidStructure> CODEC=simpleCodec(NetherAcidStructure::new);
    public NetherAcidStructure(StructureSettings settings) { super(settings,3,9,-2,16); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int direction) {
        return new NetherAcidPiece(context.structureTemplateManager(),origin,direction,context.random().nextLong());
    }
    @Override public StructureType<?> type() { return LocationContent.ACID.get(); }
}
