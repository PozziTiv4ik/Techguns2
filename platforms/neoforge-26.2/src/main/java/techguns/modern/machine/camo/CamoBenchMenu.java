package techguns.modern.machine.camo;

import java.util.List;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import techguns.modern.machine.workbench.WorkbenchMenu;

public final class CamoBenchMenu extends WorkbenchMenu {
    public static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    public static final int PLAYER_START = 1, PLAYER_END = 37, ARMOR_START = 37, OFFHAND = 41;
    public CamoBenchMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(1)); }
    public CamoBenchMenu(int id, Inventory inventory, CamoBenchBlockEntity bench) { this(id, inventory, (Container)bench); }
    private CamoBenchMenu(int id, Inventory inventory, Container bench) {
        super(CamoBenchContent.MENU.get(), id, inventory, bench); checkContainerSize(bench, 1);
        addSlot(new Slot(bench, 0, 17, 18));
        addStandardInventorySlots(inventory, 8, 84);
        var icons = List.of(InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
        for (int i = 0; i < 4; i++) addSlot(new ArmorSlot(inventory, inventory.player, ARMOR.get(i), 39 - i, 99 + i * 18, 18, icons.get(i)));
        addSlot(new Slot(inventory, 40, -17, 94) {
            @Override public void setByPlayer(ItemStack stack, ItemStack previous) { inventory.player.onEquipItem(EquipmentSlot.OFFHAND, previous, stack); super.setByPlayer(stack, previous); }
            @Override public net.minecraft.resources.Identifier getNoItemIcon() { return InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD; }
        });
    }
    public ItemStack target(int index) { return index == 0 ? bench.getItem(0) : index >= 1 && index <= 4 ? inventory.player.getItemBySlot(ARMOR.get(index - 1)) : ItemStack.EMPTY; }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!canInteract(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index == 0) {
            int armor = ARMOR.indexOf(player.getEquipmentSlotForItem(stack));
            boolean moved = armor >= 0 && !slots.get(ARMOR_START + armor).hasItem() && moveItemStackTo(stack, ARMOR_START + armor, ARMOR_START + armor + 1, false);
            if (!moved && !moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY, original); else slot.setChanged();
        slot.onTake(player, stack); return original;
    }
}
