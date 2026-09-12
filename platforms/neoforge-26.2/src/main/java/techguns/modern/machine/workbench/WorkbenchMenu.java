package techguns.modern.machine.workbench;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;

/** Vanilla menu synchronization with server-side checks shared by both workbenches. */
public abstract class WorkbenchMenu extends AbstractContainerMenu {
    protected final Container bench;
    protected final Inventory inventory;
    private final ContainerData data;
    protected WorkbenchMenu(MenuType<?> type, int id, Inventory inventory, Container bench) {
        super(type, id); this.bench = bench; this.inventory = inventory;
        data = bench instanceof OwnedWorkbenchBlockEntity blockEntity ? new ContainerData() {
            @Override public int get(int index) { return index == 0 ? (blockEntity.ownerOnly() ? 1 : 0) : index == 1 && blockEntity.isOwner(inventory.player) ? 1 : 0; }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 2; }
        } : new SimpleContainerData(2);
        addDataSlots(data);
    }
    public final boolean owns(OwnedWorkbenchBlockEntity blockEntity) { return bench == blockEntity; }
    public final int value(int index) { return data.get(index); }
    @Override public boolean stillValid(Player player) { return player == inventory.player && bench.stillValid(player); }
    protected final boolean canInteract(Player player) { return player.containerMenu == this && !player.isSpectator() && stillValid(player); }
    @Override public final boolean clickMenuButton(Player player, int button) {
        return canInteract(player) && bench instanceof OwnedWorkbenchBlockEntity blockEntity && blockEntity.button(player, button);
    }
    @Override public final void clicked(int slot, int button, ContainerInput type, Player player) {
        if (canInteract(player)) super.clicked(slot, button, type, player);
    }
}
