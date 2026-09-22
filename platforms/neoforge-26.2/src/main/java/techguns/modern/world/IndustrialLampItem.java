package techguns.modern.world;

import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Original ItemBlockLamp has an extra clicked-face filter in addition to block survival. */
public final class IndustrialLampItem extends BlockItem {
    public IndustrialLampItem(IndustrialLampBlock block,Properties p) { super(block,p); }
    @Override protected boolean canPlace(BlockPlaceContext c,BlockState state) {
        var face=c.getClickedFace(); var support=c.getClickedPos().relative(face.getOpposite()); var level=c.getLevel(); var s=level.getBlockState(support);
        boolean vertical=face.getAxis().isVertical(); boolean pole=s.getBlock() instanceof WallBlock || s.getBlock() instanceof SandbagBlock;
        boolean allowed=LegacyBlockSupport.solid(level,support,face) || pole
                || ((IndustrialLampBlock)getBlock()).lantern() && vertical && LegacyBlockSupport.torchTop(level,support);
        return allowed && super.canPlace(c,state);
    }
}
