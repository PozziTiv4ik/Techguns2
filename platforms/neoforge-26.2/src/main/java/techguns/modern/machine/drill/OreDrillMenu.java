package techguns.modern.machine.drill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import techguns.modern.machine.SplitIntContainerData;
import techguns.modern.network.MachineTanksPayload;

public final class OreDrillMenu extends AbstractContainerMenu {
    private final Container machine;
    private final ContainerData data;
    private final Player viewer;
    private FluidStack input=FluidStack.EMPTY,output=FluidStack.EMPTY;
    public OreDrillMenu(int id,Inventory inventory) { this(id,inventory,new SimpleContainer(11),new SimpleContainerData(OreDrillBlockEntity.DATA_COUNT)); }
    public OreDrillMenu(int id,Inventory inventory,Container machine,ContainerData data) {
        super(OreDrillContent.MENU.get(),id); this.machine=machine; this.data=data; viewer=inventory.player;
        checkContainerSize(machine,11); checkContainerDataCount(data,OreDrillBlockEntity.DATA_COUNT);
        for(int index=0;index<11;index++) { final int slot=index;
            int x=slot<2?30:102+(slot-2)/3*18,y=slot==0?17:slot==1?51:17+(slot-2)%3*18;
            addSlot(new Slot(machine,slot,x,y) {
                @Override public boolean mayPlace(ItemStack stack) {
                    if(slot==0) return machine instanceof OreDrillBlockEntity d?d.acceptsHead(stack):value(8)==1 && stack.getItem() instanceof OreDrillHeadItem h && h.head.size()==(value(10)==0?0:value(10)<3?1:2);
                    return slot==1 && (machine instanceof OreDrillBlockEntity?machine.canPlaceItem(slot,stack):stack.getBurnTime(RecipeType.SMELTING,viewer.level().fuelValues())>0);
                }
                @Override public int getMaxStackSize(ItemStack stack) { return slot==0?1:super.getMaxStackSize(stack); }
            });
        }
        addStandardInventorySlots(inventory,8,84); addDataSlots(new SplitIntContainerData(data));
    }
    public int value(int i) { return data.get(i); }
    public boolean owns(OreDrillBlockEntity d) { return machine==d; }
    public FluidStack fluid(int slot) { return machine instanceof OreDrillBlockEntity d?d.tanks().stack(slot):(slot==0?input:output).copy(); }
    public void updateFluids(FluidStack in,FluidStack out) { if(!(machine instanceof OreDrillBlockEntity)) { input=in.copy(); output=out.copy(); } }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if(viewer instanceof ServerPlayer p && p.containerMenu==this && machine instanceof OreDrillBlockEntity d) {
            var in=d.tanks().stack(0); var out=d.tanks().stack(1);
            if(!FluidStack.matches(in,input) || !FluidStack.matches(out,output)) { input=in.copy(); output=out.copy(); PacketDistributor.sendToPlayer(p,new MachineTanksPayload(containerId,input,output)); }
        }
    }
    @Override public boolean stillValid(Player p) { return machine.stillValid(p); }
    @Override public boolean clickMenuButton(Player p,int button) { return p.containerMenu==this && machine instanceof OreDrillBlockEntity d && d.button(p,button); }
    @Override public ItemStack quickMoveStack(Player p,int index) {
        if(!stillValid(p) || index<=0 || index>=slots.size()) return ItemStack.EMPTY; // Source forbids shift-clicking the drill head out.
        var source=slots.get(index); if(!source.hasItem()) return ItemStack.EMPTY;
        var stack=source.getItem(); var original=stack.copy();
        if(index<11) { if(!moveItemStackTo(stack,11,47,false)) return ItemStack.EMPTY; }
        else { int target=stack.getItem() instanceof OreDrillHeadItem?0:1; if(!slots.get(target).mayPlace(stack) || !moveItemStackTo(stack,target,target+1,false)) return ItemStack.EMPTY; }
        if(stack.getCount()==original.getCount()) return ItemStack.EMPTY;
        if(stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY); else source.setChanged(); source.onTake(p,stack); return original;
    }
}
