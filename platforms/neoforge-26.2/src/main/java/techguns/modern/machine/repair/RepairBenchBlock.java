package techguns.modern.machine.repair;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.entity.BlockEntityType;
import techguns.modern.machine.workbench.OwnedWorkbenchBlock;

public final class RepairBenchBlock extends OwnedWorkbenchBlock {
    private static final MapCodec<RepairBenchBlock> CODEC = simpleCodec(RepairBenchBlock::new);
    public RepairBenchBlock(Properties properties) { super(properties); }
    @Override public MapCodec<RepairBenchBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<RepairBenchBlockEntity> workbenchType() { return RepairBenchContent.ENTITY.get(); }
}
