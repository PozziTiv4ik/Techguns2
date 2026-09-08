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
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Counted, ordered inputs; coal is a recipe ingredient, not a vanilla furnace fuel slot. */
public record BlastFurnaceRecipe(Ingredient first, int firstCount, Ingredient second, int secondCount,
                                  ItemStackTemplate result, int duration, int powerPerTick) implements Recipe<MetalPressRecipe.Input> {
    public static final MapCodec<BlastFurnaceRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.fieldOf("first").forGetter(BlastFurnaceRecipe::first),
            Codec.intRange(1, 64).fieldOf("first_count").forGetter(BlastFurnaceRecipe::firstCount),
            Ingredient.CODEC.fieldOf("second").forGetter(BlastFurnaceRecipe::second),
            Codec.intRange(1, 64).fieldOf("second_count").forGetter(BlastFurnaceRecipe::secondCount),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(BlastFurnaceRecipe::result),
            Codec.intRange(1, 72000).fieldOf("duration").forGetter(BlastFurnaceRecipe::duration),
            Codec.intRange(1, 40000).fieldOf("power_per_tick").forGetter(BlastFurnaceRecipe::powerPerTick)
    ).apply(instance, BlastFurnaceRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlastFurnaceRecipe> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
    @Override public boolean matches(MetalPressRecipe.Input input, Level level) {
        return input.first().getCount() >= firstCount && input.second().getCount() >= secondCount
                && first.test(input.first()) && second.test(input.second());
    }
    @Override public ItemStack assemble(MetalPressRecipe.Input input) { return result.create(); }
    @Override public RecipeType<BlastFurnaceRecipe> getType() { return TGMachineContent.BLAST_FURNACE_RECIPE.get(); }
    @Override public RecipeSerializer<BlastFurnaceRecipe> getSerializer() { return TGMachineContent.BLAST_FURNACE_SERIALIZER.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}
