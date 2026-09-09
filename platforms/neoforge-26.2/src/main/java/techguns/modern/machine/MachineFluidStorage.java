package techguns.modern.machine;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Internal tank access is separate from the original fill-input / drain-selected-tank automation contract. */
public final class MachineFluidStorage extends FluidStacksResourceHandler {
    private final int inputCapacity, outputCapacity;
    private final Runnable changed;
    public MachineFluidStorage(int inputCapacity,int outputCapacity,Runnable changed) {
        super(2,0); this.inputCapacity=inputCapacity; this.outputCapacity=outputCapacity; this.changed=changed;
    }
    @Override protected int getCapacity(int slot,FluidResource resource) {
        Objects.checkIndex(slot,2); return slot==0 ? inputCapacity : outputCapacity;
    }
    @Override protected void onContentsChanged(int slot,FluidStack previous) { changed.run(); }
    public FluidStack stack(int slot) { return FluidUtil.getStack(this,slot); }
    public int capacity(int slot) { return getCapacity(slot,FluidResource.EMPTY); }
    public ResourceHandler<FluidResource> automation(BooleanSupplier drainInput) {
        return new ResourceHandler<>() {
            @Override public int size() { return 2; }
            @Override public FluidResource getResource(int slot) { return MachineFluidStorage.this.getResource(slot); }
            @Override public long getAmountAsLong(int slot) { return MachineFluidStorage.this.getAmountAsLong(slot); }
            @Override public long getCapacityAsLong(int slot,FluidResource resource) { return MachineFluidStorage.this.getCapacityAsLong(slot,resource); }
            @Override public boolean isValid(int slot,FluidResource resource) { Objects.checkIndex(slot,2); return slot==0; }
            @Override public int insert(int slot,FluidResource resource,int amount,TransactionContext tx) {
                Objects.checkIndex(slot,2); return slot==0 ? MachineFluidStorage.this.insert(slot,resource,amount,tx) : 0;
            }
            @Override public int extract(int slot,FluidResource resource,int amount,TransactionContext tx) {
                Objects.checkIndex(slot,2); return slot==(drainInput.getAsBoolean() ? 0 : 1) ? MachineFluidStorage.this.extract(slot,resource,amount,tx) : 0;
            }
        };
    }
}
