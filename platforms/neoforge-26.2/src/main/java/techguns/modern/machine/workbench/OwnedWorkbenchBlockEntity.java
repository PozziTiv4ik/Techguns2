package techguns.modern.machine.workbench;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.*;

/** Shared persistence and permissions, including existing Repair Bench saves. */
public abstract class OwnedWorkbenchBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    private final int size;
    private NonNullList<ItemStack> items;
    private final ResourceHandler<ItemResource> automation;
    private UUID owner;
    private boolean ownerOnly;
    protected OwnedWorkbenchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
        super(type, pos, state); this.size = size;
        items = NonNullList.withSize(size, ItemStack.EMPTY);
        automation = new WorldlyContainerWrapper(this, Direction.DOWN);
    }
    @Override public int getContainerSize() { return size; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override protected Component getDefaultName() { return getBlockState().getBlock().getName(); }
    public void setOwner(Player player) { owner = player.getUUID(); ownerOnly = false; setChanged(); }
    public boolean isOwner(Player player) { return player.getUUID().equals(owner); }
    public boolean ownerOnly() { return ownerOnly; }
    @Override public boolean canOpen(Player player) { return super.canOpen(player) && (!ownerOnly || owner == null || isOwner(player)); }
    @Override public boolean stillValid(Player player) { return !isRemoved() && player.level() == level && super.stillValid(player) && canOpen(player); }
    @Override protected final AbstractContainerMenu createMenu(int id, Inventory inventory) {
        if (owner == null && !inventory.player.isSpectator()) setOwner(inventory.player);
        return makeMenu(id, inventory);
    }
    protected abstract WorkbenchMenu makeMenu(int id, Inventory inventory);
    protected abstract boolean performAction(Player player, int button);
    public final boolean button(Player player, int button) {
        if (!(level instanceof ServerLevel) || player.isSpectator() || !stillValid(player)
                || !(player.containerMenu instanceof WorkbenchMenu menu) || !menu.owns(this) || !menu.stillValid(player)) return false;
        if (button == 0) {
            if (!isOwner(player)) return false;
            ownerOnly = !ownerOnly; setChanged(); return true;
        }
        return performAction(player, button);
    }
    protected final void publishChange(Player player) {
        setChanged(); player.getInventory().setChanged();
        player.containerMenu.broadcastChanges(); player.inventoryMenu.broadcastChanges();
    }
    public ResourceHandler<ItemResource> automation() { return automation; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return !isRemoved() && !stack.isEmpty() && slot >= 0 && slot < size; }
    @Override public int[] getSlotsForFace(Direction side) { return java.util.stream.IntStream.range(0, size).toArray(); }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return !isRemoved() && slot >= 0 && slot < size; }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); ContainerHelper.saveAllItems(output, items);
        if (owner != null) output.putString("owner", owner.toString());
        output.putBoolean("owner_only", ownerOnly);
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); items = NonNullList.withSize(size, ItemStack.EMPTY); ContainerHelper.loadAllItems(input, items);
        try { owner = UUID.fromString(input.getStringOr("owner", "")); } catch (IllegalArgumentException ignored) { owner = null; }
        ownerOnly = input.getBooleanOr("owner_only", false);
    }
}
