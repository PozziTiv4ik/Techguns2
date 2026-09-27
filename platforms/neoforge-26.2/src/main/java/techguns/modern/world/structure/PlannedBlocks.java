package techguns.modern.world.structure;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.FluidState;
import techguns.modern.world.structure.camp.CampWorld;

/** Neighbour shapes query the entire authored plan before any neighbouring chunk is placed. */
public final class PlannedBlocks {
    public static BlockGetter view(Map<BlockPos,BlockState> cells,BlockGetter terrain) {
        return new BlockGetter() {
            @Override public BlockState getBlockState(BlockPos p) { var state=cells.get(p); return state==null?terrain.getBlockState(p):state; }
            @Override public BlockEntity getBlockEntity(BlockPos p) { return null; }
            @Override public FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
            @Override public int getHeight() { return terrain.getHeight(); }
            @Override public int getMinY() { return terrain.getMinY(); }
        };
    }
    public static BlockState connected(BlockState state,BlockGetter view,BlockPos p) {
        state=CampWorld.connected(state,view,p);
        if(!(state.getBlock() instanceof StairBlock)) return state;
        // StairBlock's shape query is private. Same neighbour rule, applied to the full saved plan.
        var facing=state.getValue(StairBlock.FACING); var half=state.getValue(StairBlock.HALF);
        var behind=view.getBlockState(p.relative(facing));
        if(behind.getBlock() instanceof StairBlock&&behind.getValue(StairBlock.HALF)==half) {
            var d=behind.getValue(StairBlock.FACING);
            if(d.getAxis()!=facing.getAxis()&&canShape(state,view,p.relative(d.getOpposite())))
                return state.setValue(StairBlock.SHAPE,d==facing.getCounterClockWise()?StairsShape.OUTER_LEFT:StairsShape.OUTER_RIGHT);
        }
        var front=view.getBlockState(p.relative(facing.getOpposite()));
        if(front.getBlock() instanceof StairBlock&&front.getValue(StairBlock.HALF)==half) {
            var d=front.getValue(StairBlock.FACING);
            if(d.getAxis()!=facing.getAxis()&&canShape(state,view,p.relative(d)))
                return state.setValue(StairBlock.SHAPE,d==facing.getCounterClockWise()?StairsShape.INNER_LEFT:StairsShape.INNER_RIGHT);
        }
        return state.setValue(StairBlock.SHAPE,StairsShape.STRAIGHT);
    }
    private static boolean canShape(BlockState state,BlockGetter view,BlockPos p) {
        var other=view.getBlockState(p);
        return !(other.getBlock() instanceof StairBlock)||other.getValue(StairBlock.FACING)!=state.getValue(StairBlock.FACING)||other.getValue(StairBlock.HALF)!=state.getValue(StairBlock.HALF);
    }
    private PlannedBlocks() {}
}
