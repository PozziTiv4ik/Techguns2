package techguns.modern.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

public final class ChemLabBlock extends ProcessingMachineBlock {
    private static final MapCodec<ChemLabBlock> CODEC=simpleCodec(ChemLabBlock::new);
    public ChemLabBlock(Properties properties) { super(properties); }
    @Override public MapCodec<ChemLabBlock> codec() { return CODEC; }
    @Override protected BlockEntityType<ChemLabBlockEntity> machineType() { return TGMachineContent.CHEM_LAB_ENTITY.get(); }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ChemLabBlockEntity lab && lab.stillValid(player)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (FluidUtil.interactWithFluidHandler(player,hand,pos,lab.fluids(),null)) return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
}
