package techguns.modern.machine.reaction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import techguns.modern.machine.multiblock.LinkedMachinePartBlockEntity;

public final class ReactionPartBlockEntity extends LinkedMachinePartBlockEntity {
    public ReactionPartBlockEntity(BlockPos pos,BlockState state) { super(ReactionContent.PART_ENTITY.get(),pos,state); }
    @Override protected boolean supports(Port port) { return connector()==(port==Port.ENERGY ? 3 : 2); }
    @Override public ReactionChamberBlockEntity master() { return super.master() instanceof ReactionChamberBlockEntity chamber ? chamber : null; }
}
