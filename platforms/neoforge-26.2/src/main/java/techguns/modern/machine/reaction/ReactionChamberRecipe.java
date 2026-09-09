package techguns.modern.machine.reaction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import techguns.core.ReactionCycle;
import techguns.modern.TGContent;

public record ReactionChamberRecipe(Ingredient input, Ingredient focus, String fluid, List<ItemStackTemplate> results,
        int cycles, int requiredCompletion, int intensity, int margin, int liquidLevel, int fluidConsumption,
        double instability, String risk, int energyPerCheck) implements Recipe<ReactionChamberRecipe.Input> {
    public static final List<String> RISKS = List.of("break_item", "explosion_low", "explosion_medium");
    public static final MapCodec<ReactionChamberRecipe> CODEC = RecordCodecBuilder.<ReactionChamberRecipe>mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("input").forGetter(ReactionChamberRecipe::input),
            Ingredient.CODEC.fieldOf("focus").forGetter(ReactionChamberRecipe::focus),
            Codec.STRING.fieldOf("fluid").forGetter(ReactionChamberRecipe::fluid),
            ItemStackTemplate.CODEC.listOf(1,4).fieldOf("results").forGetter(ReactionChamberRecipe::results),
            Codec.INT.fieldOf("cycles").forGetter(ReactionChamberRecipe::cycles),
            Codec.INT.fieldOf("required_completion").forGetter(ReactionChamberRecipe::requiredCompletion),
            Codec.INT.fieldOf("intensity").forGetter(ReactionChamberRecipe::intensity),
            Codec.INT.fieldOf("margin").forGetter(ReactionChamberRecipe::margin),
            Codec.INT.fieldOf("liquid_level").forGetter(ReactionChamberRecipe::liquidLevel),
            Codec.intRange(0,10000).fieldOf("fluid_consumption").forGetter(ReactionChamberRecipe::fluidConsumption),
            Codec.DOUBLE.fieldOf("instability").forGetter(ReactionChamberRecipe::instability),
            Codec.STRING.fieldOf("risk").forGetter(ReactionChamberRecipe::risk),
            Codec.INT.fieldOf("energy_per_check").forGetter(ReactionChamberRecipe::energyPerCheck)
    ).apply(i,ReactionChamberRecipe::new)).flatXmap(ReactionChamberRecipe::validate, DataResult::success);
    public static final StreamCodec<RegistryFriendlyByteBuf,ReactionChamberRecipe> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
    private static DataResult<ReactionChamberRecipe> validate(ReactionChamberRecipe recipe) {
        try {
            recipe.rules();
            if (!RISKS.contains(recipe.risk) || recipe.fluidConsumption > recipe.liquidLevel * 1000
                    || !(recipe.fluid.equals("redstone") || recipe.fluid.equals("ender") || recipe.fluid.contains(":") && Identifier.tryParse(recipe.fluid) != null))
                return DataResult.error(() -> "Invalid reaction fluid, risk or consumption");
            return DataResult.success(recipe);
        } catch (IllegalArgumentException invalid) { return DataResult.error(invalid::getMessage); }
    }
    public ReactionCycle.Rules rules() { return new ReactionCycle.Rules(cycles,requiredCompletion,intensity,margin,liquidLevel,instability,energyPerCheck); }
    public record Input(ItemStack reagent,ItemStack focus,FluidStack fluid,int intensity,int liquidLevel) implements RecipeInput {
        @Override public ItemStack getItem(int slot) { return switch(slot) { case 0 -> reagent; case 1 -> focus; default -> throw new IndexOutOfBoundsException(slot); }; }
        @Override public int size() { return 2; }
    }
    public boolean fluidMatches(Fluid candidate) {
        if (fluid.contains(":")) return candidate == BuiltInRegistries.FLUID.getValue(Identifier.parse(fluid));
        TagKey<Fluid> tag = TagKey.create(Registries.FLUID,TGContent.id("reaction_"+fluid));
        // Original integrations used the names redstone and ender; tags allow modern renamed fluids.
        boolean hasPreferred = BuiltInRegistries.FLUID.stream().anyMatch(f -> f != Fluids.EMPTY
                && (f.defaultFluidState().is(tag) || BuiltInRegistries.FLUID.getKey(f).getPath().equals(fluid)));
        return hasPreferred ? candidate.defaultFluidState().is(tag) || BuiltInRegistries.FLUID.getKey(candidate).getPath().equals(fluid) : candidate == Fluids.LAVA;
    }
    @Override public boolean matches(Input value,Level level) {
        return input.test(value.reagent) && focus.test(value.focus) && !value.fluid.isEmpty()
                && fluidMatches(value.fluid.getFluid()) && value.fluid.getAmount() == liquidLevel * 1000
                && value.liquidLevel == liquidLevel && value.intensity == intensity;
    }
    @Override public ItemStack assemble(Input input) { return results.getFirst().create(); }
    @Override public RecipeType<ReactionChamberRecipe> getType() { return ReactionContent.RECIPE.get(); }
    @Override public RecipeSerializer<ReactionChamberRecipe> getSerializer() { return ReactionContent.SERIALIZER.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}
