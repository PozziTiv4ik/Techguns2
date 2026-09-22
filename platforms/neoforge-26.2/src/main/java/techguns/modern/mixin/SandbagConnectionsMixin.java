package techguns.modern.mixin;

import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import techguns.modern.world.SandbagBlock;

/** Old actual-state corners depended on diagonal blocks, beyond native six-neighbor updates. */
@Mixin(Level.class)
public abstract class SandbagConnectionsMixin {
    @Inject(method="markAndNotifyBlock",at=@At("TAIL"))
    private void techguns$refreshCorners(BlockPos pos,LevelChunk chunk,BlockState old,BlockState state,int flags,int limit,CallbackInfo ci) {
        if(old!=state && (flags & Block.UPDATE_KNOWN_SHAPE)==0 && limit>0) SandbagBlock.refreshAround((Level)(Object)this,pos);
    }
}
