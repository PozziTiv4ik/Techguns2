package techguns.modern.machine.grinder;

import java.util.*;
import java.util.function.DoubleSupplier;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import techguns.core.GrinderRules;
import techguns.modern.TGContent;
import techguns.modern.armor.TGArmorItem;

public record GrinderRecipe(Ingredient input, List<Output> outputs, boolean random, boolean armor) implements Recipe<SingleRecipeInput> {
    public record Output(ItemStackTemplate result, double factor, Optional<TagKey<Item>> preferredTag) {
        public Output { if (!Double.isFinite(factor) || factor < 0 || factor > 64) throw new IllegalArgumentException("Invalid Grinder factor"); }
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStackTemplate.CODEC.fieldOf("result").forGetter(Output::result),
                Codec.doubleRange(0,64).optionalFieldOf("factor",1d).forGetter(Output::factor),
                TagKey.codec(Registries.ITEM).optionalFieldOf("preferred_tag").forGetter(Output::preferredTag)
        ).apply(i,Output::new));
        public ItemStack resolve() {
            if (preferredTag.isPresent()) {
                var candidates = BuiltInRegistries.ITEM.getTagOrEmpty(preferredTag.get()).iterator();
                if (candidates.hasNext()) return result.create().transmuteCopy(candidates.next().value());
            }
            return result.create();
        }
    }
    public GrinderRecipe {
        outputs = List.copyOf(outputs);
        if ((!armor && outputs.isEmpty()) || outputs.size() > 9 || armor && (!outputs.isEmpty() || random)) throw new IllegalArgumentException("Invalid Grinder outputs");
    }
    public static final MapCodec<GrinderRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("input").forGetter(GrinderRecipe::input),
            Output.CODEC.listOf().fieldOf("outputs").forGetter(GrinderRecipe::outputs),
            Codec.BOOL.optionalFieldOf("random",false).forGetter(GrinderRecipe::random),
            Codec.BOOL.optionalFieldOf("armor",false).forGetter(GrinderRecipe::armor)
    ).apply(i,GrinderRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,GrinderRecipe> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
    @Override public boolean matches(SingleRecipeInput items, Level level) { return input.test(items.item()) && (!armor || items.item().getItem() instanceof TGArmorItem); }
    public List<ItemStack> results(ItemStack source, int batch, boolean maximum, DoubleSupplier rng) {
        List<ItemStack> values = new ArrayList<>();
        if (armor) {
            if (!(source.getItem() instanceof TGArmorItem item)) return List.of();
            int[] parts = GrinderRules.armorSalvage(item.spec(),source.getDamageValue());
            if (parts[0] > 0) values.add(item.repairMaterial(true,parts[0] * batch));
            if (parts[1] > 0) values.add(item.repairMaterial(false,parts[1] * batch));
        } else for (var output : outputs) {
            var stack = output.resolve();
            int count = random ? (maximum ? GrinderRules.maximumCount(stack.getCount(),output.factor(),batch)
                    : GrinderRules.rolledCount(stack.getCount(),output.factor(),batch,rng.getAsDouble())) : stack.getCount() * batch;
            while (count > 0) { int size = Math.min(count, stack.getMaxStackSize()); values.add(stack.copyWithCount(size)); count -= size; }
        }
        return values;
    }
    @Override public ItemStack assemble(SingleRecipeInput source) { return results(source.item(),1,true,() -> 0).stream().findFirst().orElse(ItemStack.EMPTY); }
    @Override public RecipeType<GrinderRecipe> getType() { return GrinderContent.RECIPE.get(); }
    @Override public RecipeSerializer<GrinderRecipe> getSerializer() { return GrinderContent.SERIALIZER.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}
