package techguns.modern.machine.reaction;

import java.util.function.IntSupplier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** External transfer fills to the target and drains only excess. Reducing the target never deletes fluid. */
public final class ReactionTank extends FluidStacksResourceHandler {
    private final IntSupplier target;
    private final Runnable changed;
    public ReactionTank(IntSupplier target,Runnable changed) { super(1,10000); this.target=target; this.changed=changed; }
    @Override protected void onContentsChanged(int slot,FluidStack previous) { changed.run(); }
    @Override public int insert(int slot,FluidResource resource,int amount,TransactionContext tx) {
        TransferPreconditions.checkNonNegative(amount);
        int room = Math.max(0,target.getAsInt()-getAmountAsInt(slot));
        return super.insert(slot,resource,Math.min(amount,room),tx);
    }
    @Override public int extract(int slot,FluidResource resource,int amount,TransactionContext tx) {
        TransferPreconditions.checkNonNegative(amount);
        int excess = Math.max(0,getAmountAsInt(slot)-target.getAsInt());
        return super.extract(slot,resource,Math.min(amount,excess),tx);
    }
    public int consume(FluidResource resource,int amount,TransactionContext tx) { return super.extract(0,resource,amount,tx); }
    public FluidStack stack() { return FluidUtil.getStack(this,0); }
}
