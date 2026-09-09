package techguns.modern.machine.fabricator;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import techguns.modern.machine.ProcessingMachineMenu;

public final class FabricatorMenu extends ProcessingMachineMenu {
    public FabricatorMenu(int id,Inventory inventory) { this(id,inventory,new SimpleContainer(6),clientData(100000)); }
    public FabricatorMenu(int id,Inventory inventory,Container machine,ContainerData data) {
        super(FabricatorContent.MENU.get(),id,inventory,machine,data,4,(slot,item) -> slot!=4 && FabricatorRecipe.slotFor(item)==slot,
                new int[][]{{19,17},{47,17},{68,17},{89,17},{116,50},{150,50}});
    }
}
