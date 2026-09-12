package techguns.modern.machine.camo;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.entity.BlockEntityType;
import techguns.modern.machine.workbench.OwnedWorkbenchBlock;

public final class CamoBenchBlock extends OwnedWorkbenchBlock {
    private static final MapCodec<CamoBenchBlock> CODEC = simpleCodec(CamoBenchBlock::new);
    public CamoBenchBlock(Properties properties) { super(properties); }
    @Override public MapCodec<CamoBenchBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<CamoBenchBlockEntity> workbenchType() { return CamoBenchContent.ENTITY.get(); }
}
