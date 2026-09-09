package techguns.modern.machine.reaction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import techguns.modern.machine.SplitIntContainerData;
import techguns.modern.network.MachineTanksPayload;

public final class ReactionChamberMenu extends AbstractContainerMenu {
    private final Container machine;
    private final ContainerData data;
    private final Player viewer;
    private FluidStack fluid=FluidStack.EMPTY;
    public ReactionChamberMenu(int id,Inventory inventory) { this(id,inventory,new SimpleContainer(6),new SimpleContainerData(ReactionChamberBlockEntity.DATA_COUNT)); }
    public ReactionChamberMenu(int id,Inventory inventory,Container machine,ContainerData data) {
        super(ReactionContent.MENU.get(),id); this.machine=machine; this.data=data; viewer=inventory.player;
        checkContainerSize(machine,6); checkContainerDataCount(data,ReactionChamberBlockEntity.DATA_COUNT);
        int[][] positions={{35,17},{93,17},{134,17},{152,17},{134,35},{152,35}};
        for(int i=0;i<6;i++) { final int slot=i;
            addSlot(new Slot(machine,i,positions[i][0],positions[i][1]) {
                @Override public boolean mayPlace(ItemStack item) { return machine instanceof ReactionChamberBlockEntity ? machine.canPlaceItem(slot,item)
                        : slot==0 ? !item.isEmpty() && !ReactionChamberBlockEntity.isFocus(item) : slot==1 && ReactionChamberBlockEntity.isFocus(item); }
                @Override public int getMaxStackSize(ItemStack item) { return slot==1 ? 1 : super.getMaxStackSize(item); }
            });
        }
        addStandardInventorySlots(inventory,8,84); addDataSlots(new SplitIntContainerData(data));
    }
    public int value(int index) { return data.get(index); }
    public FluidStack fluid() { return machine instanceof ReactionChamberBlockEntity chamber ? chamber.tank().stack() : fluid.copy(); }
    public void updateFluid(FluidStack value) { if(!(machine instanceof ReactionChamberBlockEntity)) fluid=value.copy(); }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if(viewer instanceof ServerPlayer player && player.containerMenu==this && machine instanceof ReactionChamberBlockEntity chamber) {
            var current=chamber.tank().stack();
            if(!FluidStack.matches(current,fluid)) { fluid=current.copy(); PacketDistributor.sendToPlayer(player,new MachineTanksPayload(containerId,fluid,FluidStack.EMPTY)); }
        }
    }
    @Override public boolean stillValid(Player player) { return machine.stillValid(player); }
    @Override public boolean clickMenuButton(Player player,int button) { return player.containerMenu==this && machine instanceof ReactionChamberBlockEntity chamber && chamber.button(player,button); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(!stillValid(player) || index<0 || index>=slots.size()) return ItemStack.EMPTY;
        Slot source=slots.get(index); if(!source.hasItem()) return ItemStack.EMPTY;
        ItemStack stack=source.getItem(), original=stack.copy();
        if(index<6) { if(!moveItemStackTo(stack,6,42,true)) return ItemStack.EMPTY; }
        else {
            int target=ReactionChamberBlockEntity.isFocus(stack) ? 1 : 0;
            if(!slots.get(target).mayPlace(stack) || !moveItemStackTo(stack,target,target+1,false)) return ItemStack.EMPTY;
        }
        if(stack.getCount()==original.getCount()) return ItemStack.EMPTY;
        if(stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY); else source.setChanged();
        source.onTake(player,stack); return original;
    }
}
