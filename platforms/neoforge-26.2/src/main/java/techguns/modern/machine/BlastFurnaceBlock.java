package techguns.modern.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class BlastFurnaceBlock extends ProcessingMachineBlock {
    private static final MapCodec<BlastFurnaceBlock> CODEC = simpleCodec(BlastFurnaceBlock::new);
    public BlastFurnaceBlock(Properties properties) { super(properties); }
    @Override public MapCodec<BlastFurnaceBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<BlastFurnaceBlockEntity> machineType() { return TGMachineContent.BLAST_FURNACE_ENTITY.get(); }
}
