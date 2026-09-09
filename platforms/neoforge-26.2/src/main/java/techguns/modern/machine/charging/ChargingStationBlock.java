package techguns.modern.machine.charging;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import techguns.modern.machine.ProcessingMachineBlock;

public final class ChargingStationBlock extends ProcessingMachineBlock {
    private static final MapCodec<ChargingStationBlock> CODEC = simpleCodec(ChargingStationBlock::new);
    public ChargingStationBlock(Properties properties) { super(properties); }
    @Override public MapCodec<ChargingStationBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<ChargingStationBlockEntity> machineType() { return ChargingStationContent.ENTITY.get(); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, machineType(), ChargingStationBlockEntity::tick);
    }
}
