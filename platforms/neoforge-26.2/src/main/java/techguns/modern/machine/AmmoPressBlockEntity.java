package techguns.modern.machine;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import techguns.modern.TGContent;

public final class AmmoPressBlockEntity extends ProcessingMachineBlockEntity {
    public static final int SLOT_COUNT = 5;
    private static final List<TagKey<Item>> INPUT_TAGS = List.of(
            tag("ammo_press/metal1"), tag("ammo_press/metal2"), tag("ammo_press/powder"));
    private static TagKey<Item> tag(String path) { return TagKey.create(Registries.ITEM, TGContent.id(path)); }
    public AmmoPressBlockEntity(BlockPos pos, BlockState state) { super(TGMachineContent.AMMO_PRESS_ENTITY.get(), pos, state, 3); }
    @Override protected Component getDefaultName() { return Component.translatable("block.techguns.ammo_press"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new AmmoPressMenu(id, inventory, this, data); }
    @Override protected int modeCount() { return 4; }
    public static boolean accepts(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return slot >= 0 && slot < 3 ? stack.is(INPUT_TAGS.get(slot)) : slot == 4 && isUpgrade(stack);
    }
    @Override protected boolean acceptsInput(int slot, ItemStack stack) { return accepts(slot, stack); }
    @Override protected Optional<Job> findJob(ServerLevel level) {
        var input = new AmmoPressRecipe.Input(mode, List.of(getItem(0), getItem(1), getItem(2)));
        return level.getServer().getRecipeManager().getRecipeFor(TGMachineContent.AMMO_PRESS_RECIPE.get(), input, level)
                .map(holder -> new Job(holder.value().assemble(input), List.of(1, 2, 1), holder.value().duration(), holder.value().powerPerTick()));
    }
    @Override protected void playWorkSound(Level level, BlockPos pos, int progress, int duration) {
        int first = Math.round(duration * .05f), second = Math.round(duration * .30f), half = Math.round(duration * .5f);
        if (progress == first || progress == first + half) level.playSound(null, pos, TGMachineContent.PRESS_WORK1.get(), SoundSource.BLOCKS, .5f, 1);
        else if (progress == second || progress == second + half) level.playSound(null, pos, TGMachineContent.PRESS_WORK2.get(), SoundSource.BLOCKS, .5f, 1);
    }
}
