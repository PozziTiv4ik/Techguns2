package techguns.modern.crafting;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;
import techguns.modern.GunItem;
import techguns.modern.TGContent;

/** Original copy_nbt upgrade, expressed through modern item components. */
public record CopyGunRecipe(ShapedRecipe base) implements CraftingRecipe {
    public static final MapCodec<CopyGunRecipe> CODEC = ShapedRecipe.MAP_CODEC.xmap(CopyGunRecipe::new, CopyGunRecipe::base);
    public static final StreamCodec<RegistryFriendlyByteBuf, CopyGunRecipe> STREAM_CODEC = ShapedRecipe.STREAM_CODEC.map(CopyGunRecipe::new, CopyGunRecipe::base);

    @Override public boolean matches(CraftingInput input, Level level) { return base.matches(input, level); }
    @Override public ItemStack assemble(CraftingInput input) {
        ItemStack output = base.assemble(input);
        if (!(output.getItem() instanceof GunItem gun)) return ItemStack.EMPTY;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack source = input.getItem(slot);
            if (source.getItem() instanceof GunItem) {
                ItemStack copy = source.transmuteCopy(output.getItem(), output.getCount());
                copy.set(TGContent.ROUNDS.get(), gun.definition().stats().clampRounds(GunItem.rounds(source)));
                // Player actions belong to the original held stack, not to the crafted item.
                copy.remove(TGContent.AIMING.get());
                copy.remove(TGContent.RELOAD_TICKS.get());
                return copy;
            }
        }
        return ItemStack.EMPTY;
    }
    @Override public RecipeSerializer<CopyGunRecipe> getSerializer() { return TGCrafting.COPY_GUN.get(); }
    @Override public CraftingBookCategory category() { return base.category(); }
    @Override public String group() { return base.group(); }
    @Override public boolean showNotification() { return base.showNotification(); }
    @Override public PlacementInfo placementInfo() { return base.placementInfo(); }
    @Override public List<RecipeDisplay> display() { return base.display(); }
}
