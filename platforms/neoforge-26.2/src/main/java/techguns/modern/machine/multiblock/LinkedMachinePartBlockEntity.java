package techguns.modern.machine.multiblock;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Capability views resolve a live controller for every call, including calls through cached handles. */
public abstract class LinkedMachinePartBlockEntity extends BlockEntity {
    public enum Port { ITEMS, FLUIDS, ENERGY }
    private BlockPos controller;
    private UUID generation;
    private int connector;
    protected LinkedMachinePartBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state) { super(type,pos,state); }
    protected abstract boolean supports(Port port);
    protected int itemSlots() { return 6; }
    public int connector() { return connector; }
    public BlockPos controller() { return controller; }
    public boolean linkedTo(BlockPos pos,UUID id) { return Objects.equals(controller,pos) && id!=null && id.equals(generation); }
    public void link(BlockPos pos,UUID id,int connector) { controller=pos.immutable(); generation=id; this.connector=connector; setChanged(); }
    public void unlink() {
        controller=null; generation=null; connector=0; setChanged();
        if(level!=null && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition)==this && getBlockState().hasProperty(MachineFormation.FORMED))
            level.setBlock(worldPosition,getBlockState().setValue(MachineFormation.FORMED,false),3);
        if(level!=null) level.invalidateCapabilities(worldPosition);
    }
    public MultiblockController master() {
        if(isRemoved() || controller==null || level==null || !level.hasChunkAt(controller) || !getBlockState().getValue(MachineFormation.FORMED)) return null;
        return level.getBlockEntity(controller) instanceof MultiblockController master && master.formation().portsAvailable(generation) ? master : null;
    }
    public static void tick(Level level,BlockPos pos,BlockState state,LinkedMachinePartBlockEntity part) {
        if(level.isClientSide() || level.getGameTime()%20!=0 || !state.getValue(MachineFormation.FORMED)) return;
        if(part.controller!=null && !level.hasChunkAt(part.controller)) return;
        if(part.controller==null || !(level.getBlockEntity(part.controller) instanceof MultiblockController master) || !master.formation().linked(part.generation)) part.unlink();
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state) {
        if(level!=null && !level.isClientSide() && controller!=null && level.hasChunkAt(controller)
                && level.getBlockEntity(controller) instanceof MultiblockController master && master.formation().linked(generation)) master.formation().unform();
        super.preRemoveSideEffects(pos,state);
    }
    private <T> T withMaster(java.util.function.Function<MultiblockController,T> action) { var master=master(); return master==null ? null : action.apply(master); }
    public ResourceHandler<ItemResource> items() { return new LinkedHandler<>(itemSlots(),ItemResource.EMPTY,
            () -> supports(Port.ITEMS) ? withMaster(MultiblockController::automation) : null); }
    public ResourceHandler<FluidResource> fluids() { return new LinkedHandler<>(1,FluidResource.EMPTY,
            () -> supports(Port.FLUIDS) ? withMaster(MultiblockController::fluidAutomation) : null); }
    public EnergyHandler energy() { return new EnergyHandler() {
        private EnergyHandler target() { return supports(Port.ENERGY) ? withMaster(MultiblockController::energy) : null; }
        @Override public long getAmountAsLong() { var target=target(); return target==null ? 0 : target.getAmountAsLong(); }
        @Override public long getCapacityAsLong() { var target=target(); return target==null ? 0 : target.getCapacityAsLong(); }
        @Override public int insert(int amount,TransactionContext tx) { TransferPreconditions.checkNonNegative(amount); var target=target(); return target==null ? 0 : target.insert(amount,tx); }
        @Override public int extract(int amount,TransactionContext tx) { TransferPreconditions.checkNonNegative(amount); var target=target(); return target==null ? 0 : target.extract(amount,tx); }
    }; }
    private record LinkedHandler<T extends Resource>(int size,T empty,Supplier<ResourceHandler<T>> target) implements ResourceHandler<T> {
        private ResourceHandler<T> target(int slot) { Objects.checkIndex(slot,size); return target.get(); }
        @Override public T getResource(int slot) { var t=target(slot); return t==null ? empty : t.getResource(slot); }
        @Override public long getAmountAsLong(int slot) { var t=target(slot); return t==null ? 0 : t.getAmountAsLong(slot); }
        @Override public long getCapacityAsLong(int slot,T resource) { var t=target(slot); return t==null ? 0 : t.getCapacityAsLong(slot,resource); }
        @Override public boolean isValid(int slot,T resource) { var t=target(slot); return t!=null && t.isValid(slot,resource); }
        @Override public int insert(int slot,T resource,int amount,TransactionContext tx) { TransferPreconditions.checkNonEmptyNonNegative(resource,amount); var t=target(slot); return t==null ? 0 : t.insert(slot,resource,amount,tx); }
        @Override public int extract(int slot,T resource,int amount,TransactionContext tx) { TransferPreconditions.checkNonEmptyNonNegative(resource,amount); var t=target(slot); return t==null ? 0 : t.extract(slot,resource,amount,tx); }
    }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out); if(controller!=null) out.store("controller",BlockPos.CODEC,controller);
        if(generation!=null) out.putString("formation",generation.toString()); out.putInt("connector",connector);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in); controller=in.read("controller",BlockPos.CODEC).orElse(null);
        generation=MachineFormation.uuid(in,"formation"); connector=Math.clamp(in.getIntOr("connector",0),0,3);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
