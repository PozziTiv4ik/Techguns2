package techguns.modern.machine.multiblock;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Resources belong to the controller; linked parts provide checked views of them. */
public interface MultiblockController {
    MachineFormation formation();
    EnergyHandler energy();
    ResourceHandler<ItemResource> automation();
    default ResourceHandler<FluidResource> fluidAutomation() { return null; }
}
