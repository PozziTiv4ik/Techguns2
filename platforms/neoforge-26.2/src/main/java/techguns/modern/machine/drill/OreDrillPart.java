package techguns.modern.machine.drill;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import techguns.modern.machine.multiblock.LinkedMachinePartBlockEntity;
public final class OreDrillPart extends LinkedMachinePartBlockEntity {
    public OreDrillPart(BlockPos pos,BlockState state) { super(OreDrillContent.PART.get(),pos,state); }
    @Override protected boolean supports(Port port) { return false; }
    @Override public OreDrillBlockEntity master() { return super.master() instanceof OreDrillBlockEntity drill?drill:null; }
}
