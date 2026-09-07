package techguns.modern.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record MetalPressRecipe(Ingredient first, Ingredient second, boolean allowSwap, ItemStackTemplate result,
                               int duration, int powerPerTick) implements Recipe<MetalPressRecipe.Input> {
    public static final MapCodec<MetalPressRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.fieldOf("first").forGetter(MetalPressRecipe::first),
            Ingredient.CODEC.fieldOf("second").forGetter(MetalPressRecipe::second),
            Codec.BOOL.fieldOf("allow_swap").forGetter(MetalPressRecipe::allowSwap),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(MetalPressRecipe::result),
            Codec.intRange(1, 72000).fieldOf("duration").forGetter(MetalPressRecipe::duration),
            Codec.intRange(1, 20000).fieldOf("power_per_tick").forGetter(MetalPressRecipe::powerPerTick)
    ).apply(instance, MetalPressRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MetalPressRecipe> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
    public record Input(ItemStack first, ItemStack second) implements RecipeInput {
        @Override public int size() { return 2; }
        @Override public ItemStack getItem(int slot) { return switch (slot) { case 0 -> first; case 1 -> second; default -> throw new IndexOutOfBoundsException(slot); }; }
    }
    @Override public boolean matches(Input input, Level level) {
        if (input.first().isEmpty() || input.second().isEmpty()) return false;
        return first.test(input.first()) && second.test(input.second())
                || allowSwap && first.test(input.second()) && second.test(input.first());
    }
    public boolean uses(ItemStack stack) { return first.test(stack) || second.test(stack); }
    @Override public ItemStack assemble(Input input) { return result.create(); }
    @Override public RecipeType<MetalPressRecipe> getType() { return TGMachineContent.METAL_PRESS_RECIPE.get(); }
    @Override public RecipeSerializer<MetalPressRecipe> getSerializer() { return TGMachineContent.METAL_PRESS_SERIALIZER.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}
