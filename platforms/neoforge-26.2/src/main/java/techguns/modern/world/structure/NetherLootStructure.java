package techguns.modern.world.structure;

import com.mojang.serialization.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.*;

public final class NetherLootStructure extends SmallNetherStructure {
    public static final MapCodec<NetherLootStructure> CODEC=simpleCodec(NetherLootStructure::new);
    public NetherLootStructure(StructureSettings settings) { super(settings,2,6,0,16); }
    @Override protected TemplateStructurePiece createPiece(GenerationContext context,BlockPos origin,int direction) { return new NetherLootPiece(context.structureTemplateManager(),origin,direction); }
    @Override public StructureType<?> type() { return LocationContent.LOOT.get(); }
}
