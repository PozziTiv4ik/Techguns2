package techguns.modern.machine.fabricator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import techguns.modern.TGContent;
import techguns.modern.machine.ProcessingMachineBlockEntity;

public record FabricatorRecipe(SizedIngredient input,SizedIngredient wire,SizedIngredient powder,SizedIngredient plate,
        ItemStackTemplate result,int duration,int powerPerTick) implements Recipe<FabricatorRecipe.Input> {
    public static final MapCodec<FabricatorRecipe> CODEC=RecordCodecBuilder.mapCodec(i -> i.group(
            SizedIngredient.NESTED_CODEC.fieldOf("input").forGetter(FabricatorRecipe::input),
            SizedIngredient.NESTED_CODEC.fieldOf("wire").forGetter(FabricatorRecipe::wire),
            SizedIngredient.NESTED_CODEC.fieldOf("powder").forGetter(FabricatorRecipe::powder),
            SizedIngredient.NESTED_CODEC.fieldOf("plate").forGetter(FabricatorRecipe::plate),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(FabricatorRecipe::result),
            Codec.intRange(1,72000).fieldOf("duration").forGetter(FabricatorRecipe::duration),
            Codec.intRange(1,100000).fieldOf("power_per_tick").forGetter(FabricatorRecipe::powerPerTick)
    ).apply(i,FabricatorRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf,FabricatorRecipe> STREAM_CODEC=ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
    public static final TagKey<Item> WIRES=tag("wire"), POWDERS=tag("powder"), PLATES=tag("plate");
    private static TagKey<Item> tag(String name) { return TagKey.create(Registries.ITEM,TGContent.id("fabricator/"+name)); }
    public record Input(List<ItemStack> stacks) implements RecipeInput {
        public Input { if(stacks.size()!=4) throw new IllegalArgumentException("Fabricator has four fixed inputs"); }
        @Override public int size() { return 4; }
        @Override public ItemStack getItem(int slot) { return stacks.get(slot); }
    }
    public static int slotFor(ItemStack item) {
        if(item.isEmpty()) return -1;
        if(item.is(WIRES)) return 1; if(item.is(POWDERS)) return 2; if(item.is(PLATES)) return 3;
        return ProcessingMachineBlockEntity.isUpgrade(item) ? 5 : 0;
    }
    public List<Integer> counts() { return List.of(input.count(),wire.count(),powder.count(),plate.count()); }
    @Override public boolean matches(Input items,Level level) { return input.test(items.getItem(0)) && wire.test(items.getItem(1)) && powder.test(items.getItem(2)) && plate.test(items.getItem(3)); }
    @Override public ItemStack assemble(Input input) { return result.create(); }
    @Override public RecipeType<FabricatorRecipe> getType() { return FabricatorContent.RECIPE.get(); }
    @Override public RecipeSerializer<FabricatorRecipe> getSerializer() { return FabricatorContent.SERIALIZER.get(); }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}
