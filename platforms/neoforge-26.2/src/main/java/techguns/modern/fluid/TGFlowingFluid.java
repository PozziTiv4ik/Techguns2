package techguns.modern.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/** Keep the legacy density barrier when using the modern game's flow geometry. */
public abstract class TGFlowingFluid extends BaseFlowingFluid {
    protected TGFlowingFluid(Properties properties) { super(properties); }
    private boolean densityAllows(FluidState existing) {
        return existing.isEmpty() || isSame(existing.getType()) || getFluidType().getDensity()>existing.getFluidType().getDensity();
    }
    @Override protected boolean canBeReplacedWith(FluidState state,BlockGetter level,BlockPos pos,Fluid incoming,Direction direction) {
        return !isSame(incoming) && incoming.getFluidType().getDensity()>getFluidType().getDensity();
    }
    @Override protected void spreadTo(LevelAccessor level,BlockPos pos,BlockState state,Direction direction,FluidState target) {
        if (densityAllows(state.getFluidState())) super.spreadTo(level,pos,state,direction,target);
    }
    @Override protected void spread(ServerLevel level,BlockPos pos,BlockState state,FluidState fluid) {
        if (!densityAllows(level.getFluidState(pos.below()))) {
            // A denser liquid below is a floor, not a valid downward outlet.
            if (fluid.getAmount()>getDropOff(level) || fluid.getValue(FALLING))
                getSpread(level,pos,state).forEach((direction,target) -> {
                    BlockPos to=pos.relative(direction); spreadTo(level,to,level.getBlockState(to),direction,target);
                });
        } else super.spread(level,pos,state,fluid);
    }
    public static final class Source extends TGFlowingFluid {
        public Source(Properties properties) { super(properties); }
        @Override public int getAmount(FluidState state) { return 8; }
        @Override public boolean isSource(FluidState state) { return true; }
    }
    public static final class Flowing extends TGFlowingFluid {
        public Flowing(Properties properties) { super(properties); registerDefaultState(getStateDefinition().any().setValue(LEVEL,7)); }
        @Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid,FluidState> builder) { super.createFluidStateDefinition(builder); builder.add(LEVEL); }
        @Override public int getAmount(FluidState state) { return state.getValue(LEVEL); }
        @Override public boolean isSource(FluidState state) { return false; }
    }
}
