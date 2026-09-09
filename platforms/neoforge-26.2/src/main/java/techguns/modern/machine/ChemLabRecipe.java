package techguns.modern.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

public record ChemLabRecipe(Optional<SizedIngredient> first,Optional<SizedIngredient> second,Optional<SizedIngredient> bottle,
                            Optional<FluidInput> fluidInput,Optional<FluidStackTemplate> fluidOutput,Optional<ItemStackTemplate> result,
                            boolean allowSwap,int duration,int powerPerTick,String activation) implements Recipe<ChemLabRecipe.Input> {
    private static final List<String> RULES=List.of("always","coal_dust_present","coal_dust_absent","biofuel_present","oils_present","oils_absent","fuels_present","lava_fuel_fallback");
    public static final MapCodec<ChemLabRecipe> CODEC=RecordCodecBuilder.mapCodec(i -> i.group(
            SizedIngredient.NESTED_CODEC.optionalFieldOf("first").forGetter(ChemLabRecipe::first),
            SizedIngredient.NESTED_CODEC.optionalFieldOf("second").forGetter(ChemLabRecipe::second),
            SizedIngredient.NESTED_CODEC.optionalFieldOf("bottle").forGetter(ChemLabRecipe::bottle),
            FluidInput.CODEC.optionalFieldOf("fluid_input").forGetter(ChemLabRecipe::fluidInput),
            FluidStackTemplate.CODEC.optionalFieldOf("fluid_output").forGetter(ChemLabRecipe::fluidOutput),
            ItemStackTemplate.CODEC.optionalFieldOf("result").forGetter(ChemLabRecipe::result),
            Codec.BOOL.fieldOf("allow_swap").forGetter(ChemLabRecipe::allowSwap),
            Codec.intRange(1,72000).fieldOf("duration").forGetter(ChemLabRecipe::duration),
            Codec.intRange(1,20000).fieldOf("power_per_tick").forGetter(ChemLabRecipe::powerPerTick),
            Codec.STRING.validate(rule -> RULES.contains(rule) ? DataResult.success(rule) : DataResult.error(() -> "Unknown ChemLab activation: "+rule))
                    .optionalFieldOf("activation","always").forGetter(ChemLabRecipe::activation)
    ).apply(i,ChemLabRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,ChemLabRecipe> STREAM_CODEC=ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
    public record FluidInput(Optional<FluidIngredient> ingredient,Optional<String> group,int amount) {
        public static final Codec<FluidInput> CODEC=RecordCodecBuilder.<FluidInput>create(i -> i.group(
                FluidIngredient.CODEC.optionalFieldOf("ingredient").forGetter(FluidInput::ingredient),
                Codec.STRING.optionalFieldOf("group").forGetter(FluidInput::group),
                Codec.intRange(1,8000).fieldOf("amount").forGetter(FluidInput::amount)
        ).apply(i,FluidInput::new)).validate(value -> value.ingredient.isPresent()!=value.group.isPresent()
                && value.group.map(g -> g.equals("oils") || g.equals("fuels")).orElse(true)
                ? DataResult.success(value) : DataResult.error(() -> "Specify one fluid ingredient or a supported fluid group"));
        boolean test(FluidStack stack) { return !stack.isEmpty() && stack.getAmount()>=amount
                && ingredient.map(value -> value.test(stack)).orElseGet(() -> ChemicalRules.groupMatches(group.orElseThrow(),stack.getFluid())); }
    }
    public record Input(ItemStack first,ItemStack second,ItemStack bottle,FluidStack fluid) implements RecipeInput {
        @Override public int size() { return 3; }
        @Override public ItemStack getItem(int slot) { return switch (slot) { case 0 -> first; case 1 -> second; case 2 -> bottle; default -> throw new IndexOutOfBoundsException(slot); }; }
        @Override public boolean isEmpty() { return first.isEmpty() && second.isEmpty() && bottle.isEmpty() && fluid.isEmpty(); }
    }
    private static boolean matches(Optional<SizedIngredient> ingredient,ItemStack stack) { return ingredient.map(value -> value.test(stack)).orElseGet(stack::isEmpty); }
    public boolean direct(Input input) { return matches(first,input.first()) && matches(second,input.second()); }
    @Override public boolean matches(Input input,Level level) {
        return ChemicalRules.active(activation) && matches(bottle,input.bottle()) && fluidInput.map(value -> value.test(input.fluid())).orElse(true)
                && (direct(input) || allowSwap && matches(first,input.second()) && matches(second,input.first()));
    }
    public List<Integer> counts(Input input) {
        int a=first.map(SizedIngredient::count).orElse(0), b=second.map(SizedIngredient::count).orElse(0);
        return List.of(direct(input) ? a : b,direct(input) ? b : a,bottle.map(SizedIngredient::count).orElse(0));
    }
    public boolean bottleUses(ItemStack stack) { return bottle.map(value -> value.ingredient().test(stack)).orElse(false); }
    public boolean uses(ItemStack stack) { return first.map(value -> value.ingredient().test(stack)).orElse(false) || second.map(value -> value.ingredient().test(stack)).orElse(false); }
    public boolean pairUses(int slot,ItemStack candidate,ItemStack other) {
        Optional<SizedIngredient> direct=slot==0 ? first : second, reverse=slot==0 ? second : first;
        return direct.map(value -> value.ingredient().test(candidate)).orElse(false) && reverse.map(value -> value.ingredient().test(other)).orElseGet(other::isEmpty)
                || allowSwap && reverse.map(value -> value.ingredient().test(candidate)).orElse(false) && direct.map(value -> value.ingredient().test(other)).orElseGet(other::isEmpty);
    }
    @Override public ItemStack assemble(Input input) { return result.map(ItemStackTemplate::create).orElse(ItemStack.EMPTY); }
    @Override public RecipeType<ChemLabRecipe> getType() { return TGMachineContent.CHEM_LAB_RECIPE.get(); }
    @Override public RecipeSerializer<ChemLabRecipe> getSerializer() { return TGMachineContent.CHEM_LAB_SERIALIZER.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}
