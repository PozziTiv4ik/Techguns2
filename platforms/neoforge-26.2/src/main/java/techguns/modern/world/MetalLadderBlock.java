package techguns.modern.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.*;

/** Original BlockTGLadder: free-standing, solid 1/8 slab and vertical alignment. */
public final class MetalLadderBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MetalLadderBlock> CODEC=simpleCodec(MetalLadderBlock::new);
    private static final VoxelShape NORTH=Block.box(0,0,14,16,16,16), SOUTH=Block.box(0,0,0,16,16,2),
            WEST=Block.box(14,0,0,16,16,16), EAST=Block.box(0,0,0,2,16,16);
    public MetalLadderBlock(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH)); }
    @Override public MapCodec<MetalLadderBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var face=context.getClickedFace(); var pos=context.getClickedPos(); var level=context.getLevel();
        if(face.getAxis().isHorizontal()) return defaultBlockState().setValue(FACING,face);
        for(var neighbour:new BlockPos[]{pos.below(),pos.above()}) {
            var state=level.getBlockState(neighbour);
            if(state.getBlock() instanceof MetalLadderBlock) return defaultBlockState().setValue(FACING,state.getValue(FACING));
        }
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return switch(state.getValue(FACING)) { case NORTH->NORTH; case SOUTH->SOUTH; case WEST->WEST; default->EAST; };
    }
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos) { return Shapes.empty(); }
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type) { return false; }
}
