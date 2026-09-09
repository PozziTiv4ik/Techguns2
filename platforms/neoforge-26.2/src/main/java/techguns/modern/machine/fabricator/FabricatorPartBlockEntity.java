package techguns.modern.machine.fabricator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import techguns.modern.machine.multiblock.LinkedMachinePartBlockEntity;

public final class FabricatorPartBlockEntity extends LinkedMachinePartBlockEntity {
    public FabricatorPartBlockEntity(BlockPos pos,BlockState state) { super(FabricatorContent.PART_ENTITY.get(),pos,state); }
    @Override protected boolean supports(Port port) { return connector()==2 && port!=Port.FLUIDS; }
    @Override public FabricatorBlockEntity master() { return super.master() instanceof FabricatorBlockEntity machine ? machine : null; }
}
