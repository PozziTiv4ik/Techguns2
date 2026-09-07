package techguns.modern.machine;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

public final class AmmoPressMenu extends ProcessingMachineMenu {
    public AmmoPressMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(5), new SimpleContainerData(ProcessingMachineBlockEntity.DATA_COUNT)); }
    public AmmoPressMenu(int id, Inventory inventory, Container machine, ContainerData data) {
        super(TGMachineContent.AMMO_PRESS_MENU.get(), id, inventory, machine, data, 3, AmmoPressBlockEntity::accepts);
    }
}
