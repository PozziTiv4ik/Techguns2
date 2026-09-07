package techguns.modern.machine;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class AmmoPressMenu extends AbstractContainerMenu {
    private final Container machine;
    private final ContainerData data;
    public AmmoPressMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(5), new SimpleContainerData(AmmoPressBlockEntity.DATA_COUNT));
    }
    public AmmoPressMenu(int id, Inventory inventory, Container machine, ContainerData data) {
        super(TGMachineContent.AMMO_PRESS_MENU.get(), id);
        checkContainerSize(machine, 5); checkContainerDataCount(data, AmmoPressBlockEntity.DATA_COUNT);
        this.machine = machine; this.data = data;
        for (int index = 0; index < 5; index++) {
            final int slot = index;
            int x = index < 3 ? 100 + index * 20 : index == 3 ? 120 : 152;
            int y = index < 3 ? 17 : 60;
            addSlot(new Slot(machine, index, x, y) {
                @Override public boolean mayPlace(ItemStack stack) { return AmmoPressBlockEntity.accepts(slot, stack); }
            });
        }
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }
    public int value(int index) { return data.get(index); }
    @Override public boolean stillValid(Player player) { return machine.stillValid(player); }
    @Override public boolean clickMenuButton(Player player, int button) {
        return player.containerMenu == this && machine instanceof AmmoPressBlockEntity blockEntity && blockEntity.button(player, button);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < 5) {
            if (!moveItemStackTo(stack, 5, 41, true)) return ItemStack.EMPTY;
        } else {
            int target = -1;
            for (int candidate : new int[]{0, 1, 2, 4}) if (AmmoPressBlockEntity.accepts(candidate, stack)) { target = candidate; break; }
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
