package techguns.modern.world.structure;

import com.mojang.serialization.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class NetherSoulStructure extends SmallNetherStructure {
    public static final MapCodec<NetherSoulStructure> CODEC=simpleCodec(NetherSoulStructure::new);
    // Source registration uses 11x11 despite the class/scan's 13x13 dimensions. Preserve its centre and shifts.
    public NetherSoulStructure(StructureSettings settings) { super(settings,1,11,-3,16); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int direction) {
        return new NetherSoulPiece(context.structureTemplateManager(),origin,direction);
    }
    @Override public StructureType<?> type() { return LocationContent.SOUL.get(); }
}
