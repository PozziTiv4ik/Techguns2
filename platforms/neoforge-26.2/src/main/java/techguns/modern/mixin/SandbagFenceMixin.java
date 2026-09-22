package techguns.modern.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import techguns.modern.world.SandbagBlock;

/** Replaces only BlockSandbags.canBeConnectedTo, removed with the old Forge hook. */
@Mixin(FenceBlock.class)
public abstract class SandbagFenceMixin {
    @Inject(method="connectsTo",at=@At("HEAD"),cancellable=true)
    private void techguns$connectToSandbags(BlockState state,boolean solid,Direction direction,CallbackInfoReturnable<Boolean> ci) {
        if(state.getBlock() instanceof SandbagBlock) ci.setReturnValue(true);
    }
}
