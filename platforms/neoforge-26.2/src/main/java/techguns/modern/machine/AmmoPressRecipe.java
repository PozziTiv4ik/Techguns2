package techguns.modern.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
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

public record AmmoPressRecipe(int plan, Ingredient metal1, Ingredient metal2, Ingredient powder,
                              ItemStackTemplate result, int duration, int powerPerTick) implements Recipe<AmmoPressRecipe.Input> {
    public static final MapCodec<AmmoPressRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(0, 3).fieldOf("plan").forGetter(AmmoPressRecipe::plan),
            Ingredient.CODEC.fieldOf("metal1").forGetter(AmmoPressRecipe::metal1),
            Ingredient.CODEC.fieldOf("metal2").forGetter(AmmoPressRecipe::metal2),
            Ingredient.CODEC.fieldOf("powder").forGetter(AmmoPressRecipe::powder),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(AmmoPressRecipe::result),
            Codec.intRange(1, 72000).fieldOf("duration").forGetter(AmmoPressRecipe::duration),
            Codec.intRange(1, 20000).fieldOf("power_per_tick").forGetter(AmmoPressRecipe::powerPerTick)
    ).apply(instance, AmmoPressRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AmmoPressRecipe> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
    public record Input(int plan, List<ItemStack> items) implements RecipeInput {
        @Override public ItemStack getItem(int index) { return items.get(index); }
        @Override public int size() { return items.size(); }
    }
    @Override public boolean matches(Input input, Level level) {
        return input.plan() == plan && input.size() == 3 && input.getItem(0).getCount() >= 1
                && input.getItem(1).getCount() >= 2 && input.getItem(2).getCount() >= 1
                && metal1.test(input.getItem(0)) && metal2.test(input.getItem(1)) && powder.test(input.getItem(2));
    }
    @Override public ItemStack assemble(Input input) { return result.create(); }
    @Override public RecipeSerializer<AmmoPressRecipe> getSerializer() { return TGMachineContent.AMMO_PRESS_SERIALIZER.get(); }
    @Override public RecipeType<AmmoPressRecipe> getType() { return TGMachineContent.AMMO_PRESS_RECIPE.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}
