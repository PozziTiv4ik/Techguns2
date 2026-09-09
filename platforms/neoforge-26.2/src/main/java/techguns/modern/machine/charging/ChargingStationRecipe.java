package techguns.modern.machine.charging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/** Nominal charge amount retains the source's truncated operation duration. */
public record ChargingStationRecipe(SizedIngredient input, ItemStackTemplate result, int chargeAmount) implements Recipe<SingleRecipeInput> {
    public static final MapCodec<ChargingStationRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SizedIngredient.NESTED_CODEC.fieldOf("input").forGetter(ChargingStationRecipe::input),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(ChargingStationRecipe::result),
            Codec.intRange(1, 57600000).fieldOf("charge_amount").forGetter(ChargingStationRecipe::chargeAmount)
    ).apply(i, ChargingStationRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChargingStationRecipe> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
    public int duration() { return Math.max(1, chargeAmount / ChargingStationBlockEntity.CHARGE_RATE); }
    @Override public boolean matches(SingleRecipeInput items, Level level) { return input.test(items.item()); }
    @Override public ItemStack assemble(SingleRecipeInput input) { return result.create(); }
    @Override public RecipeType<ChargingStationRecipe> getType() { return ChargingStationContent.RECIPE.get(); }
    @Override public RecipeSerializer<ChargingStationRecipe> getSerializer() { return ChargingStationContent.SERIALIZER.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}
