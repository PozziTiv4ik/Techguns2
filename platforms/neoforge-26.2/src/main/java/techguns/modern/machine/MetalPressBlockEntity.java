package techguns.modern.machine;

import java.util.List;
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

public final class MetalPressBlockEntity extends ProcessingMachineBlockEntity {
    public MetalPressBlockEntity(BlockPos pos, BlockState state) { super(TGMachineContent.METAL_PRESS_ENTITY.get(), pos, state, 2); }
    @Override protected Component getDefaultName() { return Component.translatable("block.techguns.metal_press"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new MetalPressMenu(id, inventory, this, data); }
    @Override protected int modeCount() { return 2; }
    @Override protected boolean adjustMode(int button) { if (button != 0) return false; mode = 1 - mode; return true; }
    // MetalPressTileEnt and BasicMachineTileEnt each apply the batch multiplier in the original.
    @Override protected int batchPower(int base, int batch) { return base * batch * batch; }
    private Optional<MetalPressRecipe> recipe(ServerLevel level, ItemStack first, ItemStack second) {
        return level.getServer().getRecipeManager().getRecipeFor(TGMachineContent.METAL_PRESS_RECIPE.get(), new MetalPressRecipe.Input(first, second), level)
                .map(holder -> holder.value());
    }
    @Override protected boolean acceptsInput(int slot, ItemStack stack) {
        if (!(level instanceof ServerLevel server)) return false;
        ItemStack own = getItem(slot), other = getItem(1 - slot);
        if (!own.isEmpty()) return ItemStack.isSameItemSameComponents(own, stack);
        if (other.isEmpty()) return server.getServer().getRecipeManager().recipeMap().byType(TGMachineContent.METAL_PRESS_RECIPE.get())
                .stream().anyMatch(holder -> holder.value().uses(stack));
        return slot == 0 ? recipe(server, stack, other).isPresent() : recipe(server, other, stack).isPresent();
    }
    @Override protected void prepareInputs(ServerLevel level) {
        if (mode == 0) return;
        for (int slot = 0; slot < 2; slot++) {
            ItemStack source = getItem(slot);
            if (source.getCount() > 1 && getItem(1 - slot).isEmpty() && recipe(level, source, source).isPresent()) {
                setItem(1 - slot, removeItem(slot, source.getCount() / 2));
                return;
            }
        }
    }
    @Override protected Optional<Job> findJob(ServerLevel level) {
        var input = new MetalPressRecipe.Input(getItem(0), getItem(1));
        return recipe(level, input.first(), input.second()).map(recipe -> new Job(recipe.assemble(input), List.of(1, 1), recipe.duration(), recipe.powerPerTick()));
    }
    @Override protected void playWorkSound(Level level, BlockPos pos, int progress, int duration) {
        int first = Math.round(duration * .075f), half = Math.round(duration * .5f);
        if (progress == first || progress == first + half) level.playSound(null, pos, TGMachineContent.METAL_WORK.get(), SoundSource.BLOCKS, .5f, 1);
    }
}
