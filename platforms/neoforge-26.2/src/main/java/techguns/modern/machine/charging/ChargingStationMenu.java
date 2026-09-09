package techguns.modern.machine.charging;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import techguns.modern.machine.ProcessingMachineBlockEntity;
import techguns.modern.machine.ProcessingMachineMenu;

public final class ChargingStationMenu extends ProcessingMachineMenu {
    public ChargingStationMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(3), clientData(100000)); }
    public ChargingStationMenu(int id, Inventory inventory, Container machine, ContainerData data) {
        super(ChargingStationContent.MENU.get(), id, inventory, machine, data, 1,
                (slot, stack) -> slot == 0 ? !stack.isEmpty() : slot == 2 && ProcessingMachineBlockEntity.isUpgrade(stack),
                new int[][]{{19,17},{68,17},{152,60}});
    }
}
