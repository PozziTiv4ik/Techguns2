package techguns.modern.machine.repair;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.TGContent;
import techguns.modern.armor.*;

public final class RepairBenchBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int MATERIAL_SLOTS = 9, REPAIR_SLOT = 9, SIZE = 10;
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final ResourceHandler<ItemResource> automation = new WorldlyContainerWrapper(this, Direction.DOWN);
    private UUID owner;
    private boolean ownerOnly;
    public RepairBenchBlockEntity(BlockPos pos, BlockState state) { super(RepairBenchContent.ENTITY.get(), pos, state); }
    @Override public int getContainerSize() { return SIZE; }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override protected Component getDefaultName() { return Component.translatable("block.techguns.repair_bench"); }
    public void setOwner(Player player) { owner = player.getUUID(); ownerOnly = false; setChanged(); }
    public boolean isOwner(Player player) { return player.getUUID().equals(owner); }
    public boolean ownerOnly() { return ownerOnly; }
    @Override public boolean canOpen(Player player) { return super.canOpen(player) && (!ownerOnly || owner == null || isOwner(player)); }
    @Override public boolean stillValid(Player player) { return !isRemoved() && player.level() == level && super.stillValid(player) && canOpen(player); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        if (owner == null && !inventory.player.isSpectator()) setOwner(inventory.player);
        return new RepairBenchMenu(id, inventory, this);
    }
    public static boolean supports(ItemStack stack) { return stack.getItem() instanceof T2ArmorItem; }
    public static List<ItemStack> costs(ItemStack stack) {
        if (!(stack.getItem() instanceof T2ArmorItem armor) || stack.getDamageValue() <= 0 || stack.getCount() != 1) return List.of();
        int[] amounts = armor.spec().repairBenchCosts(stack.getDamageValue());
        var result = new ArrayList<ItemStack>();
        if (amounts[0] > 0) result.add(TGContent.MATERIALS.get("ingotobsidiansteel").toStack(amounts[0]));
        if (amounts[1] > 0) result.add(TGContent.MATERIALS.get("heavycloth").toStack(amounts[1]));
        return List.copyOf(result);
    }
    public static int available(Container materialInventory, ItemStack wanted) {
        int count = 0;
        for (int slot = 0; slot < MATERIAL_SLOTS; slot++) if (materialInventory.getItem(slot).is(wanted.getItem())) count += materialInventory.getItem(slot).getCount();
        return count;
    }
    public boolean button(Player player, int button) {
        if (!(level instanceof ServerLevel) || player.isSpectator() || !stillValid(player)
                || !(player.containerMenu instanceof RepairBenchMenu menu) || !menu.owns(this) || !menu.stillValid(player)) return false;
        if (button == 0) {
            if (!isOwner(player)) return false;
            ownerOnly = !ownerOnly; setChanged(); return true;
        }
        if (button < 1 || button > 6) return false;
        ItemStack target = menu.target(button);
        List<ItemStack> costs = costs(target);
        if (costs.isEmpty()) return false;
        // A missing later material rolls back earlier extractions. Only the bench's material slots contribute.
        try (Transaction transaction = Transaction.openRoot()) {
            for (var cost : costs) {
                int remaining = cost.getCount();
                for (int slot = 0; slot < MATERIAL_SLOTS && remaining > 0; slot++) {
                    var stack = getItem(slot);
                    if (stack.is(cost.getItem())) remaining -= automation.extract(slot, ItemResource.of(stack), remaining, transaction);
                }
                if (remaining != 0) return false;
            }
            transaction.commit();
        }
        target.setDamageValue(0);
        setChanged(); player.getInventory().setChanged(); T2ArmorSystem.refresh(player);
        menu.broadcastChanges(); player.inventoryMenu.broadcastChanges();
        return true;
    }
    public ResourceHandler<ItemResource> automation() { return automation; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return !isRemoved() && !stack.isEmpty() && slot >= 0 && slot < SIZE && (slot < MATERIAL_SLOTS || supports(stack));
    }
    @Override public int[] getSlotsForFace(Direction side) { return java.util.stream.IntStream.range(0, SIZE).toArray(); }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return !isRemoved() && slot >= 0 && slot < SIZE; }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); ContainerHelper.saveAllItems(output, items);
        if (owner != null) output.putString("owner", owner.toString());
        output.putBoolean("owner_only", ownerOnly);
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input); items = NonNullList.withSize(SIZE, ItemStack.EMPTY); ContainerHelper.loadAllItems(input, items);
        try { owner = UUID.fromString(input.getStringOr("owner", "")); } catch (IllegalArgumentException ignored) { owner = null; }
        ownerOnly = input.getBooleanOr("owner_only", false);
    }
}
