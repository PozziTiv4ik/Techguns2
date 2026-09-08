package techguns.modern.machine;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

public final class BlastFurnaceMenu extends ProcessingMachineMenu {
    public BlastFurnaceMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(4), clientData(40000)); }
    public BlastFurnaceMenu(int id, Inventory inventory, Container machine, ContainerData data) {
        super(TGMachineContent.BLAST_FURNACE_MENU.get(), id, inventory, machine, data, 2,
                (slot, stack) -> slot < 2 ? !stack.isEmpty() : slot == 3 && ProcessingMachineBlockEntity.isUpgrade(stack),
                new int[][]{{19,17},{47,17},{116,50},{150,50}});
    }
}
