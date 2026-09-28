package techguns.modern.world.structure;

import com.mojang.serialization.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class NetherClusterStructure extends SmallNetherStructure {
    public static final MapCodec<NetherClusterStructure> CODEC=simpleCodec(NetherClusterStructure::new);
    public NetherClusterStructure(StructureSettings settings) { super(settings,4,3,0,2); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int direction) {
        return new NetherClusterPiece(context.structureTemplateManager(),origin,direction,context.random().nextLong());
    }
    @Override public StructureType<?> type() { return LocationContent.CLUSTER.get(); }
}
