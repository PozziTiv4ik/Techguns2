package techguns.modern.machine.repair;

import java.util.List;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class RepairBenchMenu extends techguns.modern.machine.workbench.WorkbenchMenu {
    public static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    public static final int PLAYER_START = 10, PLAYER_END = 46, ARMOR_START = 46, OFFHAND = 50;
    public RepairBenchMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(10)); }
    public RepairBenchMenu(int id, Inventory inventory, RepairBenchBlockEntity bench) { this(id, inventory, (Container)bench); }
    private RepairBenchMenu(int id, Inventory inventory, Container bench) {
        super(RepairBenchContent.MENU.get(), id, inventory, bench);
        checkContainerSize(bench, 10);
        for (int slot = 0; slot < 9; slot++) addSlot(new Slot(bench, slot, 8 + slot * 18, 57));
        addSlot(new Slot(bench, 9, 8, 18) {
            @Override public boolean mayPlace(ItemStack stack) { return RepairBenchBlockEntity.supports(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addStandardInventorySlots(inventory, 8, 84);
        var icons = List.of(InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
        for (int i = 0; i < 4; i++) addSlot(new ArmorSlot(inventory, inventory.player, ARMOR.get(i), 39 - i, 89 + 20 * i, 18, icons.get(i)));
        addSlot(new Slot(inventory, 40, 64, 18) {
            @Override public void setByPlayer(ItemStack stack, ItemStack previous) { inventory.player.onEquipItem(EquipmentSlot.OFFHAND, previous, stack); super.setByPlayer(stack, previous); }
            @Override public net.minecraft.resources.Identifier getNoItemIcon() { return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD; }
        });
    }
    public ItemStack target(int button) {
        if (button >= 1 && button <= 4) return inventory.player.getItemBySlot(ARMOR.get(button - 1));
        return button == 5 ? inventory.player.getOffhandItem() : button == 6 ? bench.getItem(9) : ItemStack.EMPTY;
    }
    public int available(ItemStack wanted) { return RepairBenchBlockEntity.available(bench, wanted); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!canInteract(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < PLAYER_START || index >= PLAYER_END) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else {
            int armor = ARMOR.indexOf(player.getEquipmentSlotForItem(stack));
            boolean moved = armor >= 0 && !slots.get(ARMOR_START + armor).hasItem()
                    && moveItemStackTo(stack, ARMOR_START + armor, ARMOR_START + armor + 1, false);
            if (!moved && RepairBenchBlockEntity.supports(stack)) moved = moveItemStackTo(stack, 9, 10, false);
            if (!moved && !RepairBenchBlockEntity.supports(stack)) moved = moveItemStackTo(stack, 0, 9, false);
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY, original); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
