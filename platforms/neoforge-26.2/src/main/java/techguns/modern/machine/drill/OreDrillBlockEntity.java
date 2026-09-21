package techguns.modern.machine.drill;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.*;
import techguns.core.*;
import techguns.modern.machine.*;
import techguns.modern.machine.multiblock.*;
import techguns.modern.world.*;

/** Source production cycle: heads are not consumed, weighted outputs are chosen once per operation. */
public final class OreDrillBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer,MultiblockController {
    public static final int CAPACITY=500000,DATA_COUNT=13;
    private NonNullList<ItemStack> items=NonNullList.withSize(11,ItemStack.EMPTY);
    private final SimpleEnergyHandler energy=new SimpleEnergyHandler(CAPACITY) {
        @Override protected void onEnergyChanged(int before) { setChanged(); }
        @Override public int insert(int amount,TransactionContext tx) { return isRemoved()?0:super.insert(amount,tx); }
        @Override public int extract(int amount,TransactionContext tx) { return isRemoved()?0:super.extract(amount,tx); }
    };
    private final ResourceHandler<ItemResource> automation=new WorldlyContainerWrapper(this,Direction.DOWN);
    private final MachineFluidStorage tanks=new MachineFluidStorage(16000,32000,this::setChanged);
    private final ResourceHandler<FluidResource> fluidAutomation=tanks.automation(()->false,()->!isRemoved());
    private OreDrillRules.Size size=new OreDrillRules.Size(0,1,0);
    private Direction drillDirection=Direction.DOWN;
    private List<BlockPos> endCaps=List.of();
    private List<MachineFormation.Part> partsCache;
    private final MachineFormation assembly=new MachineFormation(this,ignored->parts());
    private List<MachineFormation.Part> parts() {
        if(partsCache==null) partsCache=OreDrillStructure.parts(worldPosition,drillDirection,size,endCaps);
        return partsCache;
    }
    private UUID owner;
    private boolean ownerOnly,powered;
    private int redstone,progress,duration,power,fuelBuffer,fuelMax,soundDelay;
    private ItemStack pending=ItemStack.EMPTY,jobHead=ItemStack.EMPTY;
    private FluidStack pendingFluid=FluidStack.EMPTY;
    public final ContainerData data=new ContainerData() {
        @Override public int get(int i) { return switch(i) {
            case 0->energy.getAmountAsInt(); case 1->progress; case 2->duration; case 3->power; case 4->fuelBuffer; case 5->fuelMax;
            case 6->redstone; case 7->ownerOnly?1:0; case 8->formed()?1:0; case 9->size.length(); case 10->size.miningRadius();
            case 11->powered?1:0; case 12->headLevel(); default->0; }; }
        @Override public void set(int i,int v) {}
        @Override public int getCount() { return DATA_COUNT; }
    };
    public OreDrillBlockEntity(BlockPos pos,BlockState state) { super(OreDrillContent.CONTROLLER.get(),pos,state); }
    @Override protected Component getDefaultName() { return Component.translatable("container.techguns.ore_drill"); }
    @Override protected AbstractContainerMenu createMenu(int id,Inventory inv) { if(owner==null && !inv.player.isSpectator()) setOwner(inv.player); return new OreDrillMenu(id,inv,this,data); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> list) { items=list; }
    @Override public int getContainerSize() { return 11; }
    @Override public SimpleEnergyHandler energy() { return energy; }
    @Override public ResourceHandler<ItemResource> automation() { return automation; }
    @Override public ResourceHandler<FluidResource> fluidAutomation() { return fluidAutomation; }
    public MachineFluidStorage tanks() { return tanks; }
    @Override public MachineFormation formation() { return assembly; }
    public OreDrillRules.Size size() { return size; }
    public Direction drillDirection() { return drillDirection; }
    public boolean formed() { return assembly.formed(); }
    public boolean working() { return !pending.isEmpty() || !pendingFluid.isEmpty(); }
    public boolean turning() { return formed() && powered && redstoneEnabled() && headLevel()>0; }
    public int headLevel() { return formed() && getItem(0).getItem() instanceof OreDrillHeadItem h && h.head.size()==size.headSize()?h.head.level():0; }
    public void setOwner(Player p) { owner=p.getUUID(); setChanged(); }
    @Override public boolean canOpen(Player p) { return super.canOpen(p) && (!ownerOnly || p.getUUID().equals(owner)); }
    @Override public boolean stillValid(Player p) { return !isRemoved() && p.level()==level && level.getBlockEntity(worldPosition)==this && p.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=144 && canOpen(p); }
    public boolean form(Player p) {
        if(!(level instanceof ServerLevel) || p.isSpectator() || !stillValid(p) || formed()) return false;
        var shape=OreDrillStructure.detect(level,worldPosition); if(shape==null) return false;
        size=shape.size(); drillDirection=shape.direction();
        endCaps=OreDrillRules.endCap(size).stream().map(c->OreDrillStructure.position(worldPosition,drillDirection,c))
                .filter(pos->level.hasChunkAt(pos) && level.getBlockState(pos).is(OreDrillContent.BLOCKS.get("scaffold").get())).toList();
        partsCache=null;
        if(!assembly.form(getBlockState().getValue(MachineFormation.FACING),p)) return false;
        for(var pos:endCaps) level.setBlock(pos,level.getBlockState(pos).setValue(OreDrillBlock.END_CAP,true),3);
        if(owner==null) setOwner(p); cancel(); publish(); return true;
    }
    public boolean button(Player p,int button) {
        if(!(level instanceof ServerLevel) || p.isSpectator() || !stillValid(p) || !(p.containerMenu instanceof OreDrillMenu menu) || !menu.owns(this)) return false;
        switch(button) {
            case 2->redstone=(redstone+1)%3;
            case 3->{ if(!p.getUUID().equals(owner)) return false; ownerOnly=!ownerOnly; }
            default->{ return false; }
        }
        publish(); return true;
    }
    private boolean redstoneEnabled() { return redstone==0 || (level!=null && level.hasNeighborSignal(worldPosition))==(redstone==1); }
    public boolean acceptsHead(ItemStack stack) { return formed() && stack.getItem() instanceof OreDrillHeadItem h && h.head.size()==size.headSize(); }
    public int burnTime(ItemStack stack) { return level==null?0:stack.getBurnTime(RecipeType.SMELTING,level.fuelValues()); }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) { return !isRemoved() && !stack.isEmpty() && (slot==0?stack.getItem() instanceof OreDrillHeadItem:slot==1 && burnTime(stack)>0); }
    @Override public int[] getSlotsForFace(Direction dir) { return new int[]{0,1,2,3,4,5,6,7,8,9,10}; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction dir) { return canPlaceItem(slot,stack); }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction dir) { return !isRemoved() && slot>=2 && slot<11; }
    private void cancel() { pending=jobHead=ItemStack.EMPTY; pendingFluid=FluidStack.EMPTY; progress=duration=power=0; powered=false; setChanged(); }
    private void publish() { setChanged(); if(level!=null && !level.isClientSide()) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3); }
    private void start(ServerLevel l) {
        if(headLevel()==0) return;
        var pos=worldPosition.relative(drillDirection,size.length()+1);
        if(!OreDrillStructure.cluster(l,pos)) return;
        var variant=OreClusters.ALL.stream().filter(v->l.getBlockState(pos).is(OreClusterContent.BLOCKS.get(v.id()).get())).findFirst().orElseThrow();
        var cfg=OreClusterConfig.VALUES.get(variant.id());
        int count=OreDrillStructure.connected(l,pos,size.length());
        var rate=OreDrillRules.rate(size,count,headLevel(),cfg.miningLevel().get(),cfg.ores().get(),cfg.power().get(),OreDrillConfig.ORES.get(),OreDrillConfig.POWER.get());
        boolean enough=headLevel()+size.miningRadius()>=cfg.miningLevel().get();
        var entries=enough?ClusterOutputs.entries(variant.type()):List.<ClusterOutputs.Output>of();
        if(entries.isEmpty()) { pending=new ItemStack(Items.COBBLESTONE); pendingFluid=FluidStack.EMPTY; power=(int)(24*OreDrillConfig.POWER.get().floatValue()); }
        else { var result=ClusterOutputs.select(entries,l.getRandom().nextInt(entries.stream().mapToInt(ClusterOutputs.Output::weight).sum())); pending=result.item(); pendingFluid=result.fluid(); power=rate.power(); }
        progress=0; duration=rate.ticks(); jobHead=getItem(0).copyWithCount(1); publish();
    }
    private void outputItem(ItemStack stack) {
        if(stack.isEmpty()) return; var remaining=stack.copy();
        for(int slot=2;slot<11 && !remaining.isEmpty();slot++) {
            var current=getItem(slot); if(!current.isEmpty() && !ItemStack.isSameItemSameComponents(current,remaining)) continue;
            int n=Math.min(remaining.getCount(),remaining.getMaxStackSize()-current.getCount());
            if(n>0) { setItem(slot,current.isEmpty()?remaining.copyWithCount(n):current.copyWithCount(current.getCount()+n)); remaining.shrink(n); }
        }
        if(!remaining.isEmpty()) Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,remaining);
    }
    private boolean consumePower() {
        if(TGMachineConfig.MACHINES_NEED_NO_POWER.get() || power==0) return true;
        float factor=OreDrillConfig.FUEL.get().floatValue(); int amount=power;
        if(fuelBuffer*factor>=amount) { fuelBuffer=(int)(fuelBuffer-Math.max(amount/factor,1)); return true; }
        amount=(int)(amount-fuelBuffer*factor); fuelBuffer=fuelMax=0;
        int burn=burnTime(getItem(1));
        if(burn>0) {
            var fuel=getItem(1); var remainder=fuel.getCraftingRemainder(); removeItem(1,1); if(remainder!=null) outputItem(remainder.create()); fuelBuffer=fuelMax=burn;
        } else {
            var fluid=tanks.stack(0); float value=fluid.getFluid()==Fluids.LAVA?20:ChemicalRules.groupMatches("fuels",fluid.getFluid())?OreDrillConfig.LIQUID_FUEL.get().floatValue():0;
            if(value>0 && !fluid.isEmpty()) try(Transaction tx=Transaction.openRoot()) { int n=tanks.extract(0,FluidResource.of(fluid),Math.min(1000,fluid.getAmount()),tx); tx.commit(); fuelBuffer=fuelMax=(int)(n*value); }
        }
        if(fuelBuffer*factor>=amount) { fuelBuffer=(int)(fuelBuffer-Math.max(amount/factor,1)); return true; }
        if(amount==0) return true;
        try(Transaction tx=Transaction.openRoot()) { if(energy.extract(amount,tx)!=amount) return false; tx.commit(); return true; }
    }
    public static void tick(Level level,BlockPos pos,BlockState state,OreDrillBlockEntity m) {
        if(!(level instanceof ServerLevel l)) return;
        int status=m.assembly.status();
        if(status!=1) { if(status<0) { m.assembly.unform(); if(m.working()) { m.cancel(); m.publish(); } } else if(m.powered) { m.powered=false; m.publish(); } return; }
        var target=pos.relative(m.drillDirection,m.size.length()+1); if(!l.hasChunkAt(target)) { if(m.powered) { m.powered=false; m.publish(); } return; }
        if(!OreDrillStructure.cluster(l,target)) { m.assembly.unform(); m.cancel(); m.publish(); return; }
        if(!m.redstoneEnabled()) { if(m.powered) { m.powered=false; m.publish(); } return; }
        if(!m.working()) { m.start(l); return; }
        if(l.getGameTime()%20==0 && (m.headLevel()==0 || !ItemStack.isSameItem(m.jobHead,m.getItem(0)))) { m.cancel(); m.publish(); return; }
        boolean powered=m.consumePower(); if(powered!=m.powered) { m.powered=powered; m.publish(); }
        m.setChanged(); if(!powered) return;
        m.progress++;
        if(m.soundDelay--<=0) {
            var sound=OreDrillContent.SOUNDS.get(m.size.headSize()).get();
            l.playSound(null,pos.relative(m.drillDirection,m.size.engines()+m.size.rods()/2),sound,SoundSource.BLOCKS,.5f,1); m.soundDelay=61;
        }
        if(m.progress>=m.duration) {
            m.outputItem(m.pending);
            if(!m.pendingFluid.isEmpty()) try(Transaction tx=Transaction.openRoot()) { m.tanks.insert(1,FluidResource.of(m.pendingFluid),m.pendingFluid.getAmount(),tx); tx.commit(); }
            m.cancel(); m.start(l);
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) { return saveWithoutMetadata(r); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out); ContainerHelper.saveAllItems(out,items); assembly.save(out);
        out.putInt("engines",size.engines()); out.putInt("rods",size.rods()); out.putInt("radius",size.radius()); out.putInt("drill_direction",drillDirection.get3DDataValue());
        out.store("end_caps",BlockPos.CODEC.listOf(),endCaps); out.putInt("energy",energy.getAmountAsInt());
        out.store("input_tank",FluidStack.OPTIONAL_CODEC,tanks.stack(0)); out.store("output_tank",FluidStack.OPTIONAL_CODEC,tanks.stack(1));
        out.putInt("fuel_buffer",fuelBuffer); out.putInt("fuel_max",fuelMax); out.putInt("redstone",redstone); out.putBoolean("owner_only",ownerOnly); out.putBoolean("powered",powered);
        if(owner!=null) out.putString("owner",owner.toString());
        if(working()) { out.store("pending",ItemStack.OPTIONAL_CODEC,pending); out.store("pending_fluid",FluidStack.OPTIONAL_CODEC,pendingFluid); out.store("job_head",ItemStack.OPTIONAL_CODEC,jobHead); out.putInt("progress",progress); out.putInt("duration",duration); out.putInt("power",power); }
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in); items=NonNullList.withSize(11,ItemStack.EMPTY); ContainerHelper.loadAllItems(in,items); assembly.load(in);
        int e=in.getIntOr("engines",0),r=in.getIntOr("rods",1),rad=in.getIntOr("radius",0);
        boolean valid=OreDrillRules.valid(e,r,rad); size=valid?new OreDrillRules.Size(e,r,rad):new OreDrillRules.Size(0,1,0);
        drillDirection=Direction.from3DDataValue(Math.clamp(in.getIntOr("drill_direction",0),0,5));
        var possible=OreDrillRules.endCap(size).stream().map(c->OreDrillStructure.position(worldPosition,drillDirection,c)).toList();
        endCaps=in.read("end_caps",BlockPos.CODEC.listOf()).orElse(List.of()).stream().filter(possible::contains).distinct().toList();
        partsCache=null;
        energy.set(Math.clamp(in.getIntOr("energy",0),0,CAPACITY));
        for(int slot=0;slot<2;slot++) { var f=in.read(slot==0?"input_tank":"output_tank",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY); tanks.set(slot,FluidResource.of(f),Math.min(f.getAmount(),tanks.capacity(slot))); }
        fuelBuffer=Math.clamp(in.getIntOr("fuel_buffer",0),0,100000000); fuelMax=Math.max(fuelBuffer,Math.clamp(in.getIntOr("fuel_max",0),0,100000000));
        redstone=Math.clamp(in.getIntOr("redstone",0),0,2); ownerOnly=in.getBooleanOr("owner_only",false); owner=MachineFormation.uuid(in,"owner"); powered=in.getBooleanOr("powered",false);
        pending=in.read("pending",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY); pendingFluid=in.read("pending_fluid",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY); jobHead=in.read("job_head",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        duration=Math.max(0,in.getIntOr("duration",0)); progress=Math.clamp(in.getIntOr("progress",0),0,duration); power=Math.max(0,in.getIntOr("power",0));
        if(!valid || !working() || !(jobHead.getItem() instanceof OreDrillHeadItem)) cancel();
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state) { if(level instanceof ServerLevel) { assembly.unform(); cancel(); } super.preRemoveSideEffects(pos,state); }
}
