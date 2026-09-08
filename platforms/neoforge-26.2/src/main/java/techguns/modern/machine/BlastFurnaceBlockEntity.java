package techguns.modern.machine;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class BlastFurnaceBlockEntity extends ProcessingMachineBlockEntity {
    public BlastFurnaceBlockEntity(BlockPos pos, BlockState state) { super(TGMachineContent.BLAST_FURNACE_ENTITY.get(), pos, state, 2, 40000); }
    @Override protected Component getDefaultName() { return Component.translatable("block.techguns.blast_furnace"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new BlastFurnaceMenu(id, inventory, this, data); }
    @Override protected int modeCount() { return 1; }
    @Override protected boolean adjustMode(int button) { return false; }
    // MachineOperation.getPowerPerTick and BasicMachineTileEnt each multiply by batch size.
    @Override protected int batchPower(int base, int batch) { return base * batch * batch; }
    @Override protected boolean acceptsInput(int slot, ItemStack stack) {
        return level instanceof ServerLevel server && server.getServer().getRecipeManager().recipeMap().byType(TGMachineContent.BLAST_FURNACE_RECIPE.get())
                .stream().anyMatch(holder -> (slot == 0 ? holder.value().first() : holder.value().second()).test(stack));
    }
    @Override protected Optional<Job> findJob(ServerLevel level) {
        var input = new MetalPressRecipe.Input(getItem(0), getItem(1));
        return level.getServer().getRecipeManager().getRecipeFor(TGMachineContent.BLAST_FURNACE_RECIPE.get(), input, level)
                .map(holder -> new Job(holder.value().assemble(input), List.of(holder.value().firstCount(), holder.value().secondCount()),
                        holder.value().duration(), holder.value().powerPerTick()));
    }
    @Override protected void playWorkSound(Level level, BlockPos pos, int progress, int duration) {
        if (progress % 35 == 0) level.playSound(null, pos, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, .75f, 1);
        if (progress % 20 == 0 && level instanceof ServerLevel server) {
            level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, .75f, .5f + level.getRandom().nextFloat() * .15f);
            var facing = getBlockState().getValue(ProcessingMachineBlock.FACING);
            double offset = level.getRandom().nextDouble() * .6 - .3;
            double x = pos.getX() + .5 + facing.getStepX() * .52 + (facing.getStepX() == 0 ? offset : 0);
            double z = pos.getZ() + .5 + facing.getStepZ() * .52 + (facing.getStepZ() == 0 ? offset : 0);
            double y = pos.getY() + 3.0 / 16 + level.getRandom().nextDouble() * 6.0 / 16;
            server.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0, 0, 0, 0);
            server.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0, 0, 0);
        }
    }
}
