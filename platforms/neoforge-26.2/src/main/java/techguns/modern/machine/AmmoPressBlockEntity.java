package techguns.modern.machine;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.TGContent;

public final class AmmoPressBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int ENERGY_CAPACITY = 20000, SLOT_COUNT = 5, DATA_COUNT = 7;
    private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4};
    private static final List<TagKey<Item>> INPUT_TAGS = List.of(
            tag("ammo_press/metal1"), tag("ammo_press/metal2"), tag("ammo_press/powder"));
    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY) {
        @Override protected void onEnergyChanged(int previousAmount) { setChanged(); }
    };
    // Every side uses the original input/output restrictions, including unsided queries.
    private final ResourceHandler<ItemResource> automation = new WorldlyContainerWrapper(this, Direction.DOWN);
    private List<ItemStack> reserved = List.of();
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private int progress, duration, powerPerTick, multiplier = 1, plan, redstoneMode;
    private UUID owner;
    private boolean ownerOnly;
    private boolean needsRecipeCheck = true;

    public final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> (int) energy.getAmountAsLong(); case 1 -> progress; case 2 -> duration;
                case 3 -> plan; case 4 -> redstoneMode; case 5 -> ownerOnly ? 1 : 0; case 6 -> multiplier;
                default -> 0;
            };
        }
        @Override public void set(int index, int value) { /* Server data is read-only to menus. */ }
        @Override public int getCount() { return DATA_COUNT; }
    };

    public AmmoPressBlockEntity(BlockPos pos, BlockState state) { super(TGMachineContent.AMMO_PRESS_ENTITY.get(), pos, state); }
    private static TagKey<Item> tag(String path) { return TagKey.create(Registries.ITEM, TGContent.id(path)); }
    public SimpleEnergyHandler energy() { return energy; }
    public ResourceHandler<ItemResource> automation() { return automation; }
    public boolean working() { return !pendingOutput.isEmpty(); }
    @Override public int getContainerSize() { return SLOT_COUNT; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override protected Component getDefaultName() { return Component.translatable("block.techguns.ammo_press"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new AmmoPressMenu(id, inventory, this, data); }
    @Override public boolean canOpen(Player player) { return super.canOpen(player) && (!ownerOnly || owner == null || owner.equals(player.getUUID())); }
    @Override public boolean stillValid(Player player) { return super.stillValid(player) && canOpen(player); }
    public void setOwner(Player player) { owner = player.getUUID(); ownerOnly = false; setChanged(); }

    public boolean button(Player player, int button) {
        if (!stillValid(player) || !(level instanceof ServerLevel)) return false;
        switch (button) {
            case 0 -> plan = (plan + 1) % 4;
            case 1 -> plan = (plan + 3) % 4;
            case 2 -> redstoneMode = (redstoneMode + 1) % 3;
            case 3 -> {
                if (owner == null || !owner.equals(player.getUUID())) return false;
                ownerOnly = !ownerOnly;
            }
            default -> { return false; }
        }
        setChanged();
        return true;
    }
    public static boolean accepts(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (slot >= 0 && slot < 3) return stack.is(INPUT_TAGS.get(slot));
        return slot == 4 && stack.is(TGContent.MATERIALS.get("machinestackupgrade").get());
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return accepts(slot, stack); }
    @Override public int[] getSlotsForFace(Direction side) { return ALL_SLOTS.clone(); }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == 3; }
    @Override public void setChanged() { super.setChanged(); needsRecipeCheck = true; }

    private boolean redstoneEnabled() {
        if (redstoneMode == 0) return true;
        boolean powered = level != null && level.hasNeighborSignal(worldPosition);
        return redstoneMode == 1 ? powered : !powered;
    }
    private boolean canOutput(ItemStack output) {
        ItemStack current = items.get(3);
        return (current.isEmpty() || ItemStack.isSameItemSameComponents(current, output))
                && current.getCount() + output.getCount() <= output.getMaxStackSize();
    }
    private void startOperation(ServerLevel level) {
        needsRecipeCheck = false;
        AmmoPressRecipe.Input input = new AmmoPressRecipe.Input(plan, List.of(items.get(0), items.get(1), items.get(2)));
        var match = level.getServer().getRecipeManager().getRecipeFor(TGMachineContent.AMMO_PRESS_RECIPE.get(), input, level);
        if (match.isEmpty()) return;
        AmmoPressRecipe recipe = match.get().value();
        ItemStack output = recipe.assemble(input);
        if (output.isEmpty() || !canOutput(output)) return;
        int batch = accepts(4, items.get(4)) ? Math.min(8, items.get(4).getCount() + 1) : 1;
        batch = Math.min(batch, Math.min(items.get(0).getCount(), Math.min(items.get(1).getCount() / 2, items.get(2).getCount())));
        batch = Math.min(batch, (output.getMaxStackSize() - items.get(3).getCount()) / output.getCount());
        if (!TGMachineConfig.MACHINES_NEED_NO_POWER.get()) batch = Math.min(batch, ENERGY_CAPACITY / recipe.powerPerTick());
        if (batch < 1) return;
        reserved = List.of(removeItem(0, batch), removeItem(1, batch * 2), removeItem(2, batch));
        pendingOutput = output.copyWithCount(output.getCount() * batch);
        progress = 0;
        duration = recipe.duration();
        powerPerTick = recipe.powerPerTick();
        multiplier = batch;
        setChanged();
    }
    public static void tick(Level level, BlockPos pos, BlockState state, AmmoPressBlockEntity machine) {
        if (!(level instanceof ServerLevel server) || !machine.redstoneEnabled()) return;
        if (!machine.working()) {
            if (machine.needsRecipeCheck || level.getGameTime() % 20 == 0) machine.startOperation(server);
            return;
        }
        if (machine.progress < machine.duration) {
            if (!TGMachineConfig.MACHINES_NEED_NO_POWER.get()) {
                int required = machine.powerPerTick * machine.multiplier;
                try (Transaction transaction = Transaction.openRoot()) {
                    if (machine.energy.extract(required, transaction) != required) return;
                    transaction.commit();
                }
            }
            machine.progress++;
            int first = Math.round(machine.duration * .05f), second = Math.round(machine.duration * .30f), half = Math.round(machine.duration * .5f);
            if (machine.progress == first || machine.progress == first + half)
                level.playSound(null, pos, TGMachineContent.PRESS_WORK1.get(), SoundSource.BLOCKS, .5f, 1);
            else if (machine.progress == second || machine.progress == second + half)
                level.playSound(null, pos, TGMachineContent.PRESS_WORK2.get(), SoundSource.BLOCKS, .5f, 1);
            machine.setChanged();
        }
        if (machine.progress >= machine.duration && machine.canOutput(machine.pendingOutput)) {
            ItemStack output = machine.items.get(3);
            machine.setItem(3, output.isEmpty() ? machine.pendingOutput : output.copyWithCount(output.getCount() + machine.pendingOutput.getCount()));
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
        output.putInt("plan", plan); output.putInt("redstone", redstoneMode);
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
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energy.set(Math.clamp(input.getIntOr("energy", 0), 0, ENERGY_CAPACITY));
        plan = Math.clamp(input.getIntOr("plan", 0), 0, 3);
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
