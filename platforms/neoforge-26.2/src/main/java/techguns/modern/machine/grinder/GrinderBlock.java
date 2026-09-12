package techguns.modern.machine.grinder;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import techguns.modern.machine.workbench.OwnedWorkbenchBlock;

public final class GrinderBlock extends OwnedWorkbenchBlock {
    private static final MapCodec<GrinderBlock> CODEC = simpleCodec(GrinderBlock::new);
    public GrinderBlock(Properties properties) { super(properties); }
    @Override public MapCodec<GrinderBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<GrinderBlockEntity> workbenchType() { return GrinderContent.ENTITY.get(); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type,workbenchType(),GrinderBlockEntity::tick);
    }
}
