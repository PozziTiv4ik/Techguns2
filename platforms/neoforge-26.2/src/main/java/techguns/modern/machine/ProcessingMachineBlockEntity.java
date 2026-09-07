package techguns.modern.machine;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.TGContent;

public abstract class ProcessingMachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int ENERGY_CAPACITY = 20000, DATA_COUNT = 8;
    private final int inputSlots;
    private NonNullList<ItemStack> items;
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY) {
        @Override protected void onEnergyChanged(int previousAmount) { setChanged(); }
    };
    // Every side uses the original input/output restrictions, including unsided queries.
    private final ResourceHandler<ItemResource> automation;
    private List<ItemStack> reserved = List.of();
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private int progress, duration, powerPerTick, multiplier = 1, redstoneMode;
    protected int mode;
    private UUID owner;
    private boolean ownerOnly;
    private boolean needsRecipeCheck = true;

    public final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) energy.getAmountAsLong(); case 1 -> progress; case 2 -> duration;
                case 3 -> mode; case 4 -> redstoneMode; case 5 -> ownerOnly ? 1 : 0; case 6 -> multiplier;
                case 7 -> working() && progress < duration && !TGMachineConfig.MACHINES_NEED_NO_POWER.get() ? batchPower(powerPerTick, multiplier) : 0;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) { /* Server data is read-only to menus. */ }
        @Override public int getCount() { return DATA_COUNT; }
    };

    protected ProcessingMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int inputSlots) {
        super(type, pos, state);
        this.inputSlots = inputSlots;
        items = NonNullList.withSize(inputSlots + 2, ItemStack.EMPTY);
        // Vanilla's transactional wrapper captures the container size when constructed.
        automation = new WorldlyContainerWrapper(this, Direction.DOWN);
    }
    protected record Job(ItemStack output, List<Integer> inputCounts, int duration, int powerPerTick) {}
    protected abstract Optional<Job> findJob(ServerLevel level);
    protected abstract boolean acceptsInput(int slot, ItemStack stack);
    protected abstract int modeCount();
    protected abstract void playWorkSound(Level level, BlockPos pos, int progress, int duration);
    protected void prepareInputs(ServerLevel level) {}
    protected int batchPower(int base, int batch) { return base * batch; }
    protected boolean adjustMode(int button) {
        if (button != 0 && button != 1) return false;
        mode = Math.floorMod(mode + (button == 0 ? 1 : -1), modeCount());
        return true;
    }
    public int inputSlots() { return inputSlots; }
    public int outputSlot() { return inputSlots; }
    public int upgradeSlot() { return inputSlots + 1; }
    public SimpleEnergyHandler energy() { return energy; }
    public ResourceHandler<ItemResource> automation() { return automation; }
    public boolean working() { return !pendingOutput.isEmpty(); }
    @Override public int getContainerSize() { return inputSlots + 2; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public boolean canOpen(Player player) { return super.canOpen(player) && (!ownerOnly || owner == null || owner.equals(player.getUUID())); }
    @Override public boolean stillValid(Player player) { return super.stillValid(player) && canOpen(player); }
    public void setOwner(Player player) { owner = player.getUUID(); ownerOnly = false; setChanged(); }

    public boolean button(Player player, int button) {
        if (!stillValid(player) || !(level instanceof ServerLevel)) return false;
        switch (button) {
            case 2 -> redstoneMode = (redstoneMode + 1) % 3;
            case 3 -> {
                if (owner == null || !owner.equals(player.getUUID())) return false;
                ownerOnly = !ownerOnly;
            }
            default -> { if (!adjustMode(button)) return false; }
        }
        setChanged();
        return true;
    }
    public static boolean isUpgrade(ItemStack stack) { return stack.is(TGContent.MATERIALS.get("machinestackupgrade").get()); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return slot >= 0 && slot < inputSlots ? acceptsInput(slot, stack) : slot == upgradeSlot() && isUpgrade(stack);
    }
    @Override public int[] getSlotsForFace(Direction side) { return java.util.stream.IntStream.range(0, getContainerSize()).toArray(); }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == outputSlot(); }
    @Override public void setChanged() { super.setChanged(); needsRecipeCheck = true; }

    private boolean redstoneEnabled() {
        if (redstoneMode == 0) return true;
        boolean powered = level != null && level.hasNeighborSignal(worldPosition);
        return redstoneMode == 1 ? powered : !powered;
    }
    private boolean canOutput(ItemStack output) {
        ItemStack current = items.get(outputSlot());
        return (current.isEmpty() || ItemStack.isSameItemSameComponents(current, output))
                && current.getCount() + output.getCount() <= output.getMaxStackSize();
    }
    private void startOperation(ServerLevel level) {
        needsRecipeCheck = false;
        prepareInputs(level);
        var match = findJob(level);
        if (match.isEmpty()) return;
        Job recipe = match.get();
        ItemStack output = recipe.output();
        if (output.isEmpty() || !canOutput(output)) return;
        int batch = isUpgrade(items.get(upgradeSlot())) ? Math.min(8, items.get(upgradeSlot()).getCount() + 1) : 1;
        for (int slot = 0; slot < inputSlots; slot++) batch = Math.min(batch, items.get(slot).getCount() / recipe.inputCounts().get(slot));
        batch = Math.min(batch, (output.getMaxStackSize() - items.get(outputSlot()).getCount()) / output.getCount());
        if (!TGMachineConfig.MACHINES_NEED_NO_POWER.get())
            while (batch > 0 && batchPower(recipe.powerPerTick(), batch) > ENERGY_CAPACITY) batch--;
        if (batch < 1) return;
        reserved = new ArrayList<>();
        for (int slot = 0; slot < inputSlots; slot++) reserved.add(removeItem(slot, recipe.inputCounts().get(slot) * batch));
        pendingOutput = output.copyWithCount(output.getCount() * batch);
        progress = 0;
        duration = recipe.duration();
        powerPerTick = recipe.powerPerTick();
        multiplier = batch;
        setChanged();
    }
    public static void tick(Level level, BlockPos pos, BlockState state, ProcessingMachineBlockEntity machine) {
        if (!(level instanceof ServerLevel server) || !machine.redstoneEnabled()) return;
        if (!machine.working()) {
            if (machine.needsRecipeCheck || level.getGameTime() % 20 == 0) machine.startOperation(server);
            return;
        }
        if (machine.progress < machine.duration) {
            if (!TGMachineConfig.MACHINES_NEED_NO_POWER.get()) {
                int required = machine.batchPower(machine.powerPerTick, machine.multiplier);
                try (Transaction transaction = Transaction.openRoot()) {
                    if (machine.energy.extract(required, transaction) != required) return;
                    transaction.commit();
                }
            }
            machine.progress++;
            machine.playWorkSound(level, pos, machine.progress, machine.duration);
            machine.setChanged();
        }
        if (machine.progress >= machine.duration && machine.canOutput(machine.pendingOutput)) {
            ItemStack output = machine.items.get(machine.outputSlot());
            machine.setItem(machine.outputSlot(), output.isEmpty() ? machine.pendingOutput : output.copyWithCount(output.getCount() + machine.pendingOutput.getCount()));
            machine.pendingOutput = ItemStack.EMPTY;
            machine.reserved = List.of();
            machine.progress = machine.duration = 0;
            machine.multiplier = 1;
            machine.startOperation(server);
            machine.setChanged();
        }
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("energy", (int) energy.getAmountAsLong());
        output.putInt("mode", mode); output.putInt("redstone", redstoneMode);
        output.putBoolean("owner_only", ownerOnly);
        if (owner != null) output.putString("owner", owner.toString());
        if (working()) {
            output.store("reserved", ItemStack.OPTIONAL_CODEC.listOf(), reserved);
            output.store("pending_output", ItemStack.OPTIONAL_CODEC, pendingOutput);
            output.putInt("progress", progress); output.putInt("duration", duration);
            output.putInt("power_per_tick", powerPerTick); output.putInt("multiplier", multiplier);
        }
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energy.set(Math.clamp(input.getIntOr("energy", 0), 0, ENERGY_CAPACITY));
        mode = Math.clamp(input.getIntOr("mode", input.getIntOr("plan", 0)), 0, modeCount() - 1);
        redstoneMode = Math.clamp(input.getIntOr("redstone", 0), 0, 2);
        ownerOnly = input.getBooleanOr("owner_only", false);
        try { owner = UUID.fromString(input.getStringOr("owner", "")); } catch (IllegalArgumentException ignored) { owner = null; }
        reserved = input.read("reserved", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        pendingOutput = input.read("pending_output", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        duration = Math.clamp(input.getIntOr("duration", 100), 1, 72000);
        progress = Math.clamp(input.getIntOr("progress", 0), 0, duration);
        powerPerTick = Math.clamp(input.getIntOr("power_per_tick", 5), 1, 20000);
        multiplier = Math.clamp(input.getIntOr("multiplier", 1), 1, 8);
        if (!working()) { progress = duration = 0; multiplier = 1; }
        needsRecipeCheck = true;
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel) {
            // Return committed inputs of the unfinished batch; never drop its promised output as well.
            for (ItemStack input : reserved) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), input);
            reserved = List.of();
            pendingOutput = ItemStack.EMPTY;
        }
        super.preRemoveSideEffects(pos, state);
    }
}
