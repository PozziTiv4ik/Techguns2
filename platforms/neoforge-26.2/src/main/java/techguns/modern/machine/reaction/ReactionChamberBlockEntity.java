package techguns.modern.machine.reaction;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.core.ReactionCycle;
import techguns.modern.TGContent;
import techguns.modern.machine.TGMachineConfig;

public final class ReactionChamberBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int CAPACITY=1000000, DATA_COUNT=15;
    private NonNullList<ItemStack> items=NonNullList.withSize(6,ItemStack.EMPTY);
    private final SimpleEnergyHandler energy=new SimpleEnergyHandler(CAPACITY) {
        @Override protected void onEnergyChanged(int previous) { setChanged(); }
    };
    private final ResourceHandler<ItemResource> automation=new WorldlyContainerWrapper(this,Direction.DOWN);
    private int intensity,liquidLevel,redstone;
    private final ReactionTank tank=new ReactionTank(() -> liquidLevel*1000,this::setChanged);
    private UUID owner,formation;
    private boolean ownerOnly,unforming;
    private ReactionChamberRecipe recipe;
    private ReactionCycle.State operation;
    private ItemStack reserved=ItemStack.EMPTY;
    private FluidStack consumedFluid=FluidStack.EMPTY;
    public final ContainerData data=new ContainerData() {
        @Override public int get(int i) { return switch(i) {
            case 0 -> energy.getAmountAsInt(); case 1 -> operation==null ? 0 : operation.elapsed(); case 2 -> recipe==null ? 0 : recipe.rules().deadline();
            case 3 -> liquidLevel; case 4 -> intensity; case 5 -> operation==null ? intensity : operation.requiredIntensity();
            case 6 -> operation==null ? 0 : operation.completion(); case 7 -> recipe==null ? 0 : recipe.requiredCompletion();
            case 8 -> recipe==null ? 0 : recipe.energyPerCheck(); case 9 -> redstone; case 10 -> ownerOnly ? 1 : 0;
            case 11 -> formed() ? 1 : 0; case 12 -> recipe==null ? 0 : ReactionChamberRecipe.RISKS.indexOf(recipe.risk());
            case 13 -> working() ? 1 : 0; case 14 -> operation==null ? 0 : operation.nextCheck(); default -> 0;
        }; }
        @Override public void set(int i,int value) {}
        @Override public int getCount() { return DATA_COUNT; }
    };
    public ReactionChamberBlockEntity(BlockPos pos,BlockState state) { super(ReactionContent.CONTROLLER_ENTITY.get(),pos,state); }
    @Override protected Component getDefaultName() { return Component.translatable("container.techguns.reaction_chamber"); }
    @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory) { return new ReactionChamberMenu(id,inventory,this,data); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items=items; }
    @Override public int getContainerSize() { return 6; }
    public SimpleEnergyHandler energy() { return energy; }
    public ResourceHandler<ItemResource> automation() { return automation; }
    public ReactionTank tank() { return tank; }
    public boolean working() { return operation!=null && recipe!=null; }
    public boolean formed() { return formation!=null && getBlockState().getValue(ReactionChamberBlock.FORMED); }
    public Direction inward() { return getBlockState().getValue(ReactionChamberBlock.FACING); }
    public BlockPos center() { return worldPosition.relative(inward()); }
    public ReactionCycle.State operation() { return operation; }
    public ReactionChamberRecipe recipe() { return recipe; }
    public void setOwner(Player player) { owner=player.getUUID(); setChanged(); }
    @Override public boolean canOpen(Player player) { return super.canOpen(player) && (!ownerOnly || owner!=null && owner.equals(player.getUUID())); }
    @Override public boolean stillValid(Player player) { return super.stillValid(player) && canOpen(player); }
    public boolean button(Player player,int button) {
        if (!(level instanceof ServerLevel) || !stillValid(player)) return false;
        switch(button) {
            case 0 -> intensity=Math.min(10,intensity+1); case 1 -> intensity=Math.max(0,intensity-1);
            case 2 -> redstone=(redstone+1)%3;
            case 3 -> { if (owner==null || !owner.equals(player.getUUID())) return false; ownerOnly=!ownerOnly; }
            case 4 -> liquidLevel=Math.min(10,liquidLevel+1); case 5 -> liquidLevel=Math.max(0,liquidLevel-1);
            case 6 -> tank.set(0,FluidResource.EMPTY,0); default -> { return false; }
        }
        setChanged(); return true;
    }
    public static boolean isFocus(ItemStack item) {
        return item.is(TGContent.MATERIALS.get("rcheatray").get()) || item.is(TGContent.MATERIALS.get("rcuvemitter").get());
    }
    @Override public boolean canPlaceItem(int slot,ItemStack stack) {
        if (stack.isEmpty()) return false;
        return slot==1 ? isFocus(stack) : slot==0 && level instanceof ServerLevel server
                && server.getServer().getRecipeManager().recipeMap().byType(ReactionContent.RECIPE.get()).stream().anyMatch(r -> r.value().input().test(stack));
    }
    @Override public int[] getSlotsForFace(Direction side) { return new int[]{0,1,2,3,4,5}; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack item,Direction side) { return canPlaceItem(slot,item); }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack item,Direction side) { return slot>=2 && slot<6; }

    public boolean form(Direction outward,Player player) {
        if (!(level instanceof ServerLevel) || !stillValid(player) || outward.getAxis().isVertical() || formed()) return false;
        Direction direction=outward.getOpposite();
        var parts=ReactionStructure.parts(worldPosition,direction);
        for (var part:parts) {
            if (!level.hasChunkAt(part.pos()) || !level.mayInteract(player,part.pos())) return false;
            BlockState state=level.getBlockState(part.pos());
            if (!state.is(ReactionContent.block(part.kind())) || state.getValue(ReactionChamberBlock.FORMED)) return false;
            if (part.kind()!=ReactionChamberBlock.Part.CONTROLLER && !(level.getBlockEntity(part.pos()) instanceof ReactionPartBlockEntity)) return false;
        }
        formation=UUID.randomUUID();
        if (owner==null) owner=player.getUUID();
        for (var part:parts) {
            if (level.getBlockEntity(part.pos()) instanceof ReactionPartBlockEntity slave) slave.link(worldPosition,formation,part.connector());
            level.setBlock(part.pos(),level.getBlockState(part.pos()).setValue(ReactionChamberBlock.FORMED,true).setValue(ReactionChamberBlock.FACING,direction),3);
            level.invalidateCapabilities(part.pos());
        }
        setChanged(); return true;
    }
    /** 0: incomplete chunk availability, -1: broken structure, 1: complete and owned by this formation. */
    private int structureStatus() {
        if (level==null || isRemoved() || !formed()) return -1;
        var parts=ReactionStructure.parts(worldPosition,inward());
        for(var part:parts) if(!level.hasChunkAt(part.pos())) return 0;
        for(var part:parts) {
            var state=level.getBlockState(part.pos());
            if (!state.is(ReactionContent.block(part.kind())) || !state.getValue(ReactionChamberBlock.FORMED)
                    || state.getValue(ReactionChamberBlock.FACING)!=inward()) return -1;
            if (part.kind()!=ReactionChamberBlock.Part.CONTROLLER && (!(level.getBlockEntity(part.pos()) instanceof ReactionPartBlockEntity slave)
                    || !slave.linkedTo(worldPosition,formation) || slave.connector()!=part.connector())) return -1;
        }
        return 1;
    }
    public boolean linked(UUID generation) { return generation!=null && generation.equals(formation) && !isRemoved() && formed(); }
    public boolean portsAvailable(UUID generation) { return linked(generation) && structureStatus()==1; }
    public void unform() {
        if (unforming || formation==null || level==null) return;
        unforming=true;
        UUID old=formation; formation=null;
        for(var part:ReactionStructure.parts(worldPosition,inward())) {
            if (!level.hasChunkAt(part.pos())) continue;
            if (level.getBlockEntity(part.pos()) instanceof ReactionPartBlockEntity slave && slave.linkedTo(worldPosition,old)) slave.unlink();
        }
        if (level.getBlockState(worldPosition).is(ReactionContent.CONTROLLER.get()))
            level.setBlock(worldPosition,level.getBlockState(worldPosition).setValue(ReactionChamberBlock.FORMED,false),3);
        level.invalidateCapabilities(worldPosition);
        setChanged(); unforming=false;
    }
    private boolean enabled() { return redstone==0 || (redstone==1)==level.hasNeighborSignal(worldPosition); }
    private boolean pay(int amount) {
        if (TGMachineConfig.MACHINES_NEED_NO_POWER.get()) return true;
        try(Transaction tx=Transaction.openRoot()) { if(energy.extract(amount,tx)!=amount) return false; tx.commit(); return true; }
    }
    private void start(ServerLevel server) {
        if(!reserved.isEmpty()) {
            // Recover an input if a saved recipe was invalidated by a registry/data-pack change.
            ItemStack current=getItem(0);
            if(current.isEmpty()) setItem(0,reserved);
            else if(ItemStack.isSameItemSameComponents(current,reserved) && current.getCount()+reserved.getCount()<=current.getMaxStackSize()) current.grow(reserved.getCount());
            else Containers.dropItemStack(server,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,reserved);
            reserved=ItemStack.EMPTY; setChanged();
        }
        var input=new ReactionChamberRecipe.Input(getItem(0),getItem(1),tank.stack(),intensity,liquidLevel);
        var found=server.getServer().getRecipeManager().getRecipeFor(ReactionContent.RECIPE.get(),input,server);
        if(found.isEmpty()) return;
        var selected=found.get().value();
        if(!TGMachineConfig.MACHINES_NEED_NO_POWER.get() && energy.getAmountAsInt()<selected.energyPerCheck()) return;
        recipe=selected; reserved=removeItem(0,1);
        consumedFluid=tank.stack().copyWithAmount(recipe.fluidConsumption());
        operation=ReactionCycle.State.start(recipe.rules(),server.getRandom().nextLong()); setChanged();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,ReactionChamberBlockEntity machine) {
        if (!(level instanceof ServerLevel server)) return;
        int structure=machine.structureStatus();
        if(structure<0) machine.unform();
        if(structure!=1 || !machine.enabled()) return;
        if(!machine.working()) { machine.start(server); return; }
        var rules=machine.recipe.rules();
        boolean powered=machine.operation.checkDue(rules) && machine.pay(rules.energyPerCheck());
        var step=ReactionCycle.advance(rules,machine.operation,machine.intensity,machine.liquidLevel,machine.recipe.focus().test(machine.getItem(1)),powered);
        machine.operation=step.state();
        if(step.checked()) machine.onCheck(server,step.good());
        if(step.outcome()!=ReactionCycle.Outcome.RUNNING) machine.finish(server,step.outcome()==ReactionCycle.Outcome.SUCCESS);
        machine.setChanged();
    }
    private void onCheck(ServerLevel server,boolean good) {
        if(isFocus(getItem(1))) server.playSound(null,worldPosition,getItem(1).is(TGContent.MATERIALS.get("rcheatray").get())
                ? ReactionContent.HEAT_WORK.get() : techguns.modern.machine.TGMachineContent.CHEM_WORK.get(),SoundSource.BLOCKS,1,1);
        server.playSound(null,worldPosition,operation.requiredIntensity()==intensity ? ReactionContent.BEEP.get() : ReactionContent.WARNING.get(),SoundSource.BLOCKS,1,1);
        server.sendParticles(ParticleTypes.ENCHANT,center().getX()+.5,center().getY()+1.5,center().getZ()+.5,4,.2,.3,.2,0);
        if(!good && isFocus(getItem(1))) techguns.modern.radiation.RadiationSystem.reactionFailure(server,worldPosition,intensity);
    }
    private void finish(ServerLevel server,boolean success) {
        var finished=recipe;
        // Snapshot fluid components at start. Dumping/replacing liquid cannot create a free output.
        if(success && !consumedFluid.isEmpty()) try(Transaction tx=Transaction.openRoot()) {
            if(tank.consume(FluidResource.of(consumedFluid),consumedFluid.getAmount(),tx)!=consumedFluid.getAmount()) success=false;
            else tx.commit();
        }
        recipe=null; operation=null; reserved=ItemStack.EMPTY; consumedFluid=FluidStack.EMPTY;
        if(success) for(var output:finished.results()) output(output.create());
        else if(!finished.risk().equals("break_item")) rupture(server,finished.risk().equals("explosion_medium"));
    }
    private void output(ItemStack output) {
        for(int pass=0;pass<2 && !output.isEmpty();pass++) for(int slot=2;slot<6 && !output.isEmpty();slot++) {
            ItemStack current=getItem(slot);
            if(pass==0 && !current.isEmpty() && ItemStack.isSameItemSameComponents(current,output)) {
                int n=Math.min(output.getCount(),current.getMaxStackSize()-current.getCount()); current.grow(n); output.shrink(n);
            } else if(pass==1 && current.isEmpty()) { int n=Math.min(output.getCount(),output.getMaxStackSize()); setItem(slot,output.copyWithCount(n)); output.shrink(n); }
        }
        if(!output.isEmpty()) Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,output);
    }
    private void rupture(ServerLevel server,boolean medium) {
        BlockPos center=center().above();
        var fluid=tank.stack();
        // Clear before explosion callbacks can destroy this controller and drop its inventory.
        ItemStack waiting=removeItemNoUpdate(0);
        energy.set(0); tank.set(0,FluidResource.EMPTY,0); unform();
        if(medium) for(int y=0;y<2;y++) for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) server.removeBlock(center.offset(x,y,z),false);
        else { server.removeBlock(center,false); for(Direction side:Direction.Plane.HORIZONTAL) server.removeBlock(center.relative(side),false); }
        if(medium) server.explode(null,center.getX()+.5,center.getY()+.5,center.getZ()+.5,4,Level.ExplosionInteraction.BLOCK);
        else { server.playSound(null,center,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.BLOCKS,4,.7f); server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,center.getX()+.5,center.getY()+.5,center.getZ()+.5,1,0,0,0,0); }
        if(!fluid.isEmpty()) {
            BlockState fluidBlock=fluid.getFluid().defaultFluidState().createLegacyBlock();
            if(!fluidBlock.isAir()) { server.setBlock(center,fluidBlock,3); if(medium) { server.setBlock(center.above(),Blocks.AIR.defaultBlockState(),3); server.setBlock(center.below(),Blocks.GRAVEL.defaultBlockState(),3); } }
        }
        if(!waiting.isEmpty()) Containers.dropItemStack(server,center.getX()+.5,center.getY()+.5,center.getZ()+.5,waiting);
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state) {
        if(level instanceof ServerLevel) {
            unform();
            if(!reserved.isEmpty()) Containers.dropItemStack(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,reserved);
            reserved=ItemStack.EMPTY; recipe=null; operation=null;
        }
        super.preRemoveSideEffects(pos,state);
    }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out); ContainerHelper.saveAllItems(out,items);
        out.putInt("energy",energy.getAmountAsInt()); out.putInt("intensity",intensity); out.putInt("liquid_level",liquidLevel); out.putInt("redstone",redstone);
        out.putBoolean("owner_only",ownerOnly);
        if(owner!=null) out.putString("owner",owner.toString()); if(formation!=null) out.putString("formation",formation.toString());
        out.store("tank",FluidStack.OPTIONAL_CODEC,tank.stack());
        out.store("reserved",ItemStack.OPTIONAL_CODEC,reserved);
        if(working()) {
            out.store("reaction",ReactionChamberRecipe.CODEC.codec(),recipe); out.store("consumed_fluid",FluidStack.OPTIONAL_CODEC,consumedFluid);
            out.putInt("elapsed",operation.elapsed()); out.putInt("completion",operation.completion()); out.putInt("next_check",operation.nextCheck());
            out.putInt("required_intensity",operation.requiredIntensity()); out.putLong("random_state",operation.randomState());
        }
    }
    static UUID uuid(ValueInput in,String key) { try { return UUID.fromString(in.getStringOr(key,"")); } catch(IllegalArgumentException ignored) { return null; } }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in); items=NonNullList.withSize(6,ItemStack.EMPTY); ContainerHelper.loadAllItems(in,items);
        energy.set(Math.clamp(in.getIntOr("energy",0),0,CAPACITY)); intensity=Math.clamp(in.getIntOr("intensity",0),0,10); liquidLevel=Math.clamp(in.getIntOr("liquid_level",0),0,10);
        redstone=Math.clamp(in.getIntOr("redstone",0),0,2); ownerOnly=in.getBooleanOr("owner_only",false); owner=uuid(in,"owner"); formation=uuid(in,"formation");
        var fluid=in.read("tank",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY); tank.set(0,FluidResource.of(fluid),Math.min(10000,fluid.getAmount()));
        reserved=in.read("reserved",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        recipe=in.read("reaction",ReactionChamberRecipe.CODEC.codec()).orElse(null); operation=null;
        consumedFluid=in.read("consumed_fluid",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        if(recipe!=null && !reserved.isEmpty() && (recipe.fluidConsumption()==0 || !consumedFluid.isEmpty() && consumedFluid.getAmount()==recipe.fluidConsumption())) operation=new ReactionCycle.State(Math.clamp(in.getIntOr("elapsed",0),0,recipe.rules().deadline()),
                Math.clamp(in.getIntOr("completion",0),0,recipe.requiredCompletion()),Math.clamp(in.getIntOr("next_check",60),1,60),
                Math.clamp(in.getIntOr("required_intensity",recipe.intensity()),0,10),in.getLongOr("random_state",0));
        else recipe=null;
    }
}
