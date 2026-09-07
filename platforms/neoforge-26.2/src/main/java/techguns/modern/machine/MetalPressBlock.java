package techguns.modern.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class MetalPressBlock extends ProcessingMachineBlock {
    private static final MapCodec<MetalPressBlock> CODEC = simpleCodec(MetalPressBlock::new);
    public MetalPressBlock(Properties properties) { super(properties); }
    @Override public MapCodec<MetalPressBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<MetalPressBlockEntity> machineType() { return TGMachineContent.METAL_PRESS_ENTITY.get(); }
}
