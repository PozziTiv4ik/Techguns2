package techguns.modern.machine;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

public final class MetalPressMenu extends ProcessingMachineMenu {
    public MetalPressMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(4), clientData(20000)); }
    public MetalPressMenu(int id, Inventory inventory, Container machine, ContainerData data) {
        super(TGMachineContent.METAL_PRESS_MENU.get(), id, inventory, machine, data, 2,
                // The server validates the current data-pack recipe pair. The client must
                // not reject new data-pack inputs using a stale, generated material list.
                (slot, stack) -> slot < 2 ? !stack.isEmpty() : slot == 3 && ProcessingMachineBlockEntity.isUpgrade(stack));
    }
}
