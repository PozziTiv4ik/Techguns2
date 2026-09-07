package techguns.modern.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class AmmoPressBlock extends ProcessingMachineBlock {
    private static final MapCodec<AmmoPressBlock> CODEC = simpleCodec(AmmoPressBlock::new);
    public AmmoPressBlock(Properties properties) { super(properties); }
    @Override public MapCodec<AmmoPressBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<AmmoPressBlockEntity> machineType() { return TGMachineContent.AMMO_PRESS_ENTITY.get(); }
}
