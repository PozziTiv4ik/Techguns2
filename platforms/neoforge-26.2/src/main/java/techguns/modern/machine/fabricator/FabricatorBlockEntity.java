package techguns.modern.machine.fabricator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import techguns.modern.machine.ProcessingMachineBlockEntity;
import techguns.modern.machine.multiblock.MachineFormation;
import techguns.modern.machine.multiblock.MultiblockController;

public final class FabricatorBlockEntity extends ProcessingMachineBlockEntity implements MultiblockController {
    public static final int CAPACITY=100000;
    private final MachineFormation assembly=new MachineFormation(this,direction -> parts(worldPosition,direction));
    public final ContainerData displayData=new ContainerData() {
        @Override public int get(int index) { return index==3 ? (assembly.status()==1 ? 1 : 0) : data.get(index); }
        @Override public void set(int index,int value) {}
        @Override public int getCount() { return DATA_COUNT; }
    };
    public FabricatorBlockEntity(BlockPos pos,BlockState state) { super(FabricatorContent.CONTROLLER_ENTITY.get(),pos,state,4,CAPACITY); }
    public static List<MachineFormation.Part> parts(BlockPos controller,Direction outward) {
        if(outward.getAxis().isVertical()) throw new IllegalArgumentException("Horizontal Fabricator required");
        List<MachineFormation.Part> parts=new ArrayList<>(8);
        for(int back=0;back<2;back++) for(int left=0;left<2;left++) {
            BlockPos pos=controller.relative(outward.getOpposite(),back).relative(outward.getClockWise(),left);
            parts.add(new MachineFormation.Part(pos,pos.equals(controller) ? FabricatorContent.CONTROLLER.get() : FabricatorContent.HOUSING.get(),pos.equals(controller) ? 0 : 2));
            parts.add(new MachineFormation.Part(pos.above(),FabricatorContent.GLASS.get(),1));
        }
        return List.copyOf(parts);
    }
    @Override public MachineFormation formation() { return assembly; }
    public boolean formed() { return assembly.formed(); }
    public boolean form(Direction outward,Player player) {
        if(!(level instanceof ServerLevel) || !stillValid(player) || !assembly.form(outward,player)) return false;
        claimIfUnowned(player); return true;
    }
    @Override protected boolean operational(ServerLevel level) { int state=assembly.status(); if(state<0) assembly.unform(); return state==1; }
    @Override protected Optional<Job> findJob(ServerLevel level) {
        var input=new FabricatorRecipe.Input(List.of(getItem(0),getItem(1),getItem(2),getItem(3)));
        return level.getServer().getRecipeManager().getRecipeFor(FabricatorContent.RECIPE.get(),input,level)
                .map(holder -> { var r=holder.value(); return new Job(r.assemble(input),r.counts(),r.duration(),r.powerPerTick()); });
    }
    @Override protected boolean acceptsInput(int slot,ItemStack stack) { return FabricatorRecipe.slotFor(stack)==slot; }
    @Override protected int modeCount() { return 1; }
    @Override protected boolean adjustMode(int button) { return false; }
    @Override protected void playWorkSound(Level level,BlockPos pos,int progress,int duration) {
        if(progress==1) level.playSound(null,pos,FabricatorContent.WORK.get(),SoundSource.BLOCKS,.5f,1);
    }
    @Override protected Component getDefaultName() { return Component.translatable("container.techguns.fabricator"); }
    @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory) { return new FabricatorMenu(id,inventory,this,displayData); }
    @Override protected void saveAdditional(ValueOutput output) { super.saveAdditional(output); assembly.save(output); }
    @Override protected void loadAdditional(ValueInput input) { super.loadAdditional(input); assembly.load(input); }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state) { if(level instanceof ServerLevel) assembly.unform(); super.preRemoveSideEffects(pos,state); }
}
