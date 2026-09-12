package techguns.modern.machine.grinder;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import techguns.modern.machine.SplitIntContainerData;
import techguns.modern.machine.workbench.WorkbenchMenu;

public final class GrinderMenu extends WorkbenchMenu {
    public static final int PLAYER_START = 11, PLAYER_END = 47;
    private final ContainerData metrics;
    public GrinderMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(11), new SimpleContainerData(GrinderBlockEntity.METRICS)); }
    public GrinderMenu(int id, Inventory inventory, Container machine, ContainerData metrics) {
        super(GrinderContent.MENU.get(), id, inventory, machine);
        checkContainerSize(machine, 11); checkContainerDataCount(metrics, GrinderBlockEntity.METRICS); this.metrics = metrics;
        addSlot(new Slot(machine, 0, 18, 17));
        addSlot(new Slot(machine, 1, 152, 60) {
            @Override public boolean mayPlace(ItemStack stack) { return GrinderBlockEntity.isUpgrade(stack); }
        });
        for (int index = 0; index < 9; index++) addSlot(new Slot(machine, index + 2, 80 + index % 3 * 18, 17 + index / 3 * 18) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(new SplitIntContainerData(metrics));
    }
    public int metric(int index) { return metrics.get(index); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!canInteract(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) return ItemStack.EMPTY;
        } else {
            int target = GrinderBlockEntity.isUpgrade(stack) ? 1 : bench instanceof GrinderBlockEntity machine && machine.acceptsRecipe(stack) ? 0 : -1;
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY, original); else slot.setChanged();
        slot.onTake(player, stack); return original;
    }
}
