package techguns.modern.world.structure;

import com.mojang.serialization.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class NetherAltarStructure extends SmallNetherStructure {
    public static final MapCodec<NetherAltarStructure> CODEC=simpleCodec(NetherAltarStructure::new);
    public NetherAltarStructure(StructureSettings settings) { super(settings,0,11,-3,16); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int direction) { return new NetherAltarPiece(context.structureTemplateManager(),origin,direction); }
    @Override public StructureType<?> type() { return LocationContent.ALTAR.get(); }
}
