package techguns.modern.machine.charging;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStackResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.machine.ProcessingMachineBlockEntity;

public final class ChargingStationBlockEntity extends ProcessingMachineBlockEntity {
    public static final int CAPACITY = 100000, CHARGE_RATE = 800, ITEM_CHARGE_RATE = 1600;
    private final ItemStackResourceHandler inputAccess = internalSlot(0), outputAccess = internalSlot(1);
    private final ItemAccess itemAccess = ItemAccess.forHandlerIndexStrict(inputAccess, 0);
    private ItemStack finishedDirect = ItemStack.EMPTY, renderedItem = ItemStack.EMPTY;
    private boolean charging;
    private int transferredThisTick, soundTicks;

    public final ContainerData displayData = new ContainerData() {
        @Override public int get(int index) {
            if (index == 3) return working() ? 1 : !finishedDirect.isEmpty() ? 3 : charging ? 2 : 0;
            if (index == 7 && !working()) return transferredThisTick;
            return data.get(index);
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return DATA_COUNT; }
    };
    public ChargingStationBlockEntity(BlockPos pos, BlockState state) { super(ChargingStationContent.ENTITY.get(), pos, state, 1, CAPACITY); }
    @Override protected Component getDefaultName() { return Component.translatable("block.techguns.charging_station"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new ChargingStationMenu(id, inventory, this, displayData); }
    @Override protected int modeCount() { return 1; }
    @Override protected boolean adjustMode(int button) { return false; }
    // The source MachineOperation already multiplies 800 FE by the batch size once.
    @Override protected int batchPower(int base, int batch) { return base * batch; }
    @Override protected boolean acceptsInput(int slot, ItemStack stack) {
        if (slot != 0 || stack.isEmpty() || isUpgrade(stack)) return false;
        if (ItemAccess.forStack(stack.copy()).getCapability(Capabilities.Energy.ITEM) != null) return true;
        return level instanceof ServerLevel server && server.getServer().getRecipeManager().recipeMap().byType(ChargingStationContent.RECIPE.get())
                .stream().anyMatch(holder -> holder.value().input().ingredient().test(stack));
    }
    @Override protected Optional<Job> findJob(ServerLevel level) {
        var input = new SingleRecipeInput(getItem(0));
        return level.getServer().getRecipeManager().getRecipeFor(ChargingStationContent.RECIPE.get(), input, level)
                .map(holder -> new Job(holder.value().assemble(input), List.of(holder.value().input().count()), holder.value().duration(), CHARGE_RATE));
    }
    private ItemStackResourceHandler internalSlot(int slot) {
        // Capability-driven item replacements may change item type or lose their energy capability.
        // This internal access is separate from the restricted ports exposed to hoppers and pipes.
        return new ItemStackResourceHandler() {
            @Override protected ItemStack getStack() { return getItem(slot); }
            @Override protected void setStack(ItemStack stack) { getItems().set(slot, stack); }
            @Override protected int getCapacity(ItemResource resource) { return resource.isEmpty() ? 64 : Math.min(64, resource.getMaxStackSize()); }
            @Override protected void onRootCommit(ItemStack original) { setChanged(); }
        };
    }
    @Override protected boolean processIdle(ServerLevel server) {
        charging = false;
        if (!finishedDirect.isEmpty()) {
            if (sameStack(finishedDirect, getItem(0))) { moveFinished(); return true; }
            finishedDirect = ItemStack.EMPTY;
            setChanged();
        }
        EnergyHandler handler = itemAccess.getCapability(Capabilities.Energy.ITEM);
        if (handler == null) return false;
        int accepted;
        try (Transaction probe = Transaction.openRoot()) { accepted = handler.insert(ITEM_CHARGE_RATE, probe); }
        if (accepted == 0) { moveFinished(); return true; }
        charging = true;
        // The original no-power machine setting does not grant energy to external items.
        try (Transaction tx = Transaction.openRoot()) {
            int offered = Math.min(ITEM_CHARGE_RATE, energy().getAmountAsInt());
            int received = handler.insert(offered, tx);
            if (received > 0 && energy().extract(received, tx) == received) {
                tx.commit();
                transferredThisTick = received;
            }
        }
        if (transferredThisTick > 0) {
            playWorkSound(server, worldPosition, 0, 0);
            if (!getItem(0).isEmpty() && itemAccess.getCapability(Capabilities.Energy.ITEM) == null) {
                finishedDirect = getItem(0).copy();
                setChanged();
            }
        }
        return true;
    }
    private void moveFinished() {
        if (getItem(0).isEmpty() || !getItem(1).isEmpty()) return;
        ItemResource item = ItemResource.of(getItem(0)); int amount = getItem(0).getCount();
        try (Transaction tx = Transaction.openRoot()) {
            if (inputAccess.extract(0, item, amount, tx) == amount && outputAccess.insert(0, item, amount, tx) == amount) {
                tx.commit();
                finishedDirect = ItemStack.EMPTY;
                setChanged();
            }
        }
    }
    public static void tick(Level level, BlockPos pos, BlockState state, ChargingStationBlockEntity machine) {
        if (!(level instanceof ServerLevel server)) return;
        machine.transferredThisTick = 0;
        if (machine.soundTicks > 0) machine.soundTicks--;
        ProcessingMachineBlockEntity.tick(level, pos, state, machine);
        ItemStack shown = machine.currentDisplay();
        if (!sameStack(shown, machine.renderedItem)) {
            machine.renderedItem = shown;
            server.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }
    @Override protected void playWorkSound(Level level, BlockPos pos, int progress, int duration) {
        if (soundTicks > 0 || !(level instanceof ServerLevel server)) return;
        soundTicks = 20;
        server.playSound(null, pos, ChargingStationContent.WORK.get(), SoundSource.BLOCKS, .35f, 1);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 4, .2, .1, .2, .02);
    }
    private static boolean sameStack(ItemStack a, ItemStack b) {
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }
    private ItemStack currentDisplay() {
        ItemStack stack = working() ? reservedInput(0) : charging || !finishedDirect.isEmpty() ? getItem(0) : ItemStack.EMPTY;
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }
    public ItemStack displayItem() { return level instanceof ServerLevel ? currentDisplay() : renderedItem.copy(); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.store("display_item", ItemStack.OPTIONAL_CODEC, displayItem());
        return output.buildResult();
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(ValueInput input) { renderedItem = input.read("display_item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY); }
    @Override public void onDataPacket(Connection connection, ValueInput input) { handleUpdateTag(input); }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("finished_direct", ItemStack.OPTIONAL_CODEC, finishedDirect);
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        finishedDirect = input.read("finished_direct", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        charging = false; transferredThisTick = soundTicks = 0;
    }
}
