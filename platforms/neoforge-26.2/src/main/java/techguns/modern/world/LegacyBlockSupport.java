package techguns.modern.world;

import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Original 1.12 attachment exceptions; modern support faces alone are more permissive. */
final class LegacyBlockSupport {
    static boolean attachingException(BlockState s) {
        var b=s.getBlock();
        return b instanceof LeavesBlock || b instanceof ShulkerBoxBlock || b instanceof TrapDoorBlock
                || b instanceof AbstractCauldronBlock || b instanceof StainedGlassBlock || s.is(Blocks.BEACON)
                || s.is(Blocks.GLASS) || s.is(Blocks.GLOWSTONE) || s.is(Blocks.ICE) || s.is(Blocks.SEA_LANTERN);
    }
    static boolean pistonException(BlockState s) {
        return attachingException(s) || s.is(Blocks.PISTON) || s.is(Blocks.STICKY_PISTON) || s.is(Blocks.PISTON_HEAD);
    }
    static boolean fenceException(BlockState s) {
        return pistonException(s) || s.is(Blocks.BARRIER) || s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN)
                || s.is(Blocks.CARVED_PUMPKIN) || s.is(Blocks.JACK_O_LANTERN);
    }
    static boolean solid(BlockGetter level,BlockPos pos,Direction face) {
        return level.getBlockState(pos).isFaceSturdy(level,pos,face);
    }
    static boolean torchTop(BlockGetter level,BlockPos pos) {
        var s=level.getBlockState(pos); var b=s.getBlock();
        if(s.is(Blocks.END_GATEWAY) || s.is(Blocks.JACK_O_LANTERN)) return false;
        if(b instanceof SandbagBlock || b instanceof FenceBlock || b instanceof WallBlock || s.is(Blocks.GLASS) || b instanceof StainedGlassBlock) return true;
        return !attachingException(s) && (solid(level,pos,Direction.UP) || b instanceof HopperBlock);
    }
    static boolean normalCube(BlockState s) { return s.isSolidRender() && !s.isSignalSource() && !s.is(Blocks.GLOWSTONE) && !s.is(Blocks.SEA_LANTERN); }
    private LegacyBlockSupport() {}
}
