package techguns.modern.mixin;

import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import techguns.modern.world.CamouflageNetBlock;

/** Preserve BlockPane's original MIDDLE_POLE_THIN connection to vertical nets. */
@Mixin(IronBarsBlock.class)
public abstract class CamouflagePaneMixin {
    @Inject(method = "attachsTo", at = @At("HEAD"), cancellable = true)
    private void techguns$connectToNet(BlockState state, boolean solid, CallbackInfoReturnable<Boolean> ci) {
        if (state.getBlock() instanceof CamouflageNetBlock net && !net.canopy()) ci.setReturnValue(true);
    }
}
