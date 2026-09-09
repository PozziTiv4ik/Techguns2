package techguns.modern.machine;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

public final class ChemLabBlockEntity extends ProcessingMachineBlockEntity {
    private final ResourceHandler<FluidResource> fluids;
    public ChemLabBlockEntity(BlockPos pos,BlockState state) {
        super(TGMachineContent.CHEM_LAB_ENTITY.get(),pos,state,3,20000,8000,16000);
        fluids=tanks().automation(() -> mode==1);
    }
    public ResourceHandler<FluidResource> fluids() { return fluids; }
    private ChemLabRecipe.Input input() { return new ChemLabRecipe.Input(getItem(0),getItem(1),getItem(2),tanks().stack(0)); }
    @Override protected Optional<Job> findJob(ServerLevel level) {
        var input=input();
        return level.getServer().getRecipeManager().getRecipeFor(TGMachineContent.CHEM_LAB_RECIPE.get(),input,level).map(holder -> {
            var recipe=holder.value();
            return new Job(recipe.assemble(input),recipe.counts(input),recipe.duration(),recipe.powerPerTick(),
                    recipe.fluidInput().map(value -> input.fluid().copyWithAmount(value.amount())).orElse(FluidStack.EMPTY),
                    recipe.fluidOutput().map(FluidStackTemplate::create).orElse(FluidStack.EMPTY));
        });
    }
    @Override protected boolean acceptsInput(int slot,ItemStack stack) {
        if (!(level instanceof ServerLevel server)) return false;
        var recipes=server.getServer().getRecipeManager().recipeMap().byType(TGMachineContent.CHEM_LAB_RECIPE.get()).stream()
                .map(holder -> holder.value()).filter(recipe -> ChemicalRules.active(recipe.activation())).toList();
        if (slot==2) return recipes.stream().anyMatch(recipe -> recipe.bottleUses(stack));
        // Some original optional recipes explicitly use a container in a material slot.
        // Validate that recipe pair instead of globally excluding every item also used as a flask.
        ItemStack other=getItem(slot==0 ? 1 : 0);
        return recipes.stream().anyMatch(recipe -> other.isEmpty() ? recipe.uses(stack) : recipe.pairUses(slot,stack,other));
    }
    @Override protected int modeCount() { return 2; }
    @Override protected boolean adjustMode(int button) {
        if (button==0) mode=1-mode;
        else if (button==4 || button==5) tanks().set(button-4,FluidResource.EMPTY,0);
        else return false;
        return true;
    }
    @Override protected void playWorkSound(Level level,BlockPos pos,int progress,int duration) {
        if (progress==1 || progress==1+Math.round(duration*.5f)) level.playSound(null,pos,TGMachineContent.CHEM_WORK.get(),SoundSource.BLOCKS,.65f,1f);
    }
    @Override protected Component getDefaultName() { return Component.translatable("container.techguns.chem_lab"); }
    @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory) { return new ChemLabMenu(id,inventory,this,data); }
}
