package techguns.modern.machine;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import java.util.function.BiPredicate;

public abstract class ProcessingMachineMenu extends AbstractContainerMenu {
    private final Container machine;
    private final ContainerData data;
    private final int machineSlots;
    protected ProcessingMachineMenu(MenuType<?> type, int id, Inventory inventory, Container machine, ContainerData data, int inputs, BiPredicate<Integer, ItemStack> clientAccepts) {
        this(type, id, inventory, machine, data, inputs, clientAccepts, null);
    }
    protected ProcessingMachineMenu(MenuType<?> type, int id, Inventory inventory, Container machine, ContainerData data, int inputs, BiPredicate<Integer, ItemStack> clientAccepts, int[][] positions) {
        super(type, id);
        machineSlots = inputs + 2;
        checkContainerSize(machine, machineSlots); checkContainerDataCount(data, ProcessingMachineBlockEntity.DATA_COUNT);
        this.machine = machine; this.data = data;
        for (int index = 0; index < machineSlots; index++) {
            final int slot = index;
            int x = index < inputs ? (inputs == 3 ? 100 : 110) + index * 20 : index == inputs ? 120 : 152;
            int y = index < inputs ? 17 : 60;
            if (positions != null) { x = positions[index][0]; y = positions[index][1]; }
            addSlot(new Slot(machine, index, x, y) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return machine instanceof ProcessingMachineBlockEntity ? machine.canPlaceItem(slot, stack) : clientAccepts.test(slot, stack);
                }
            });
        }
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(new SplitIntContainerData(data));
    }
    protected static ContainerData clientData(int capacity) {
        var data = new SimpleContainerData(ProcessingMachineBlockEntity.DATA_COUNT);
        data.set(8, capacity);
        return data;
    }
    public int value(int index) { return data.get(index); }
    @Override public boolean stillValid(Player player) { return machine.stillValid(player); }
    @Override public boolean clickMenuButton(Player player, int button) {
        return player.containerMenu == this && machine instanceof ProcessingMachineBlockEntity blockEntity && blockEntity.button(player, button);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, machineSlots + 36, true)) return ItemStack.EMPTY;
        } else {
            int target = -1;
            for (int candidate = 0; candidate < machineSlots; candidate++) {
                if (slots.get(candidate).mayPlace(stack) && moveItemStackTo(stack, candidate, candidate + 1, false)) { target = candidate; break; }
            }
            if (target < 0) return ItemStack.EMPTY;
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
