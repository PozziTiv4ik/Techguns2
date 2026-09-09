package techguns.modern.machine;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.neoforged.neoforge.fluids.FluidStack;

public final class ChemLabMenu extends ProcessingMachineMenu {
    private final Container machine;
    private final net.minecraft.world.entity.player.Player viewer;
    private FluidStack input=FluidStack.EMPTY, output=FluidStack.EMPTY;
    public ChemLabMenu(int id,Inventory inventory) { this(id,inventory,new SimpleContainer(5),clientData(20000)); }
    public ChemLabMenu(int id,Inventory inventory,Container machine,ContainerData data) {
        super(TGMachineContent.CHEM_LAB_MENU.get(),id,inventory,machine,data,3,
                (slot,stack) -> slot<3 ? !stack.isEmpty() : slot==4 && ProcessingMachineBlockEntity.isUpgrade(stack),
                new int[][]{{35,17},{57,17},{35,40},{135,17},{135,60}});
        this.machine=machine; viewer=inventory.player;
    }
    public FluidStack fluid(int slot) {
        java.util.Objects.checkIndex(slot,2);
        return machine instanceof ChemLabBlockEntity lab ? lab.tanks().stack(slot) : (slot==0 ? input : output).copy();
    }
    public void updateFluids(FluidStack input,FluidStack output) {
        if (!(machine instanceof ChemLabBlockEntity)) { this.input=input.copy(); this.output=output.copy(); }
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (viewer instanceof ServerPlayer player && viewer.containerMenu==this && machine instanceof ChemLabBlockEntity lab) {
            FluidStack currentIn=lab.tanks().stack(0), currentOut=lab.tanks().stack(1);
            if (!FluidStack.matches(input,currentIn) || !FluidStack.matches(output,currentOut)) {
                input=currentIn.copy(); output=currentOut.copy();
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new techguns.modern.network.MachineTanksPayload(containerId,input,output));
            }
        }
    }
    public int capacity(int slot) { return slot==0 ? 8000 : 16000; }
}
