package techguns.modern.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/** Preserves the original hardened-glass and electrum preferences with standalone fallbacks. */
public record TagFallbackIngredient(TagKey<Item> preferred, TagKey<Item> fallback) implements ICustomIngredient {
    public static final MapCodec<TagFallbackIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TagKey.codec(Registries.ITEM).fieldOf("preferred").forGetter(TagFallbackIngredient::preferred),
            TagKey.codec(Registries.ITEM).fieldOf("fallback").forGetter(TagFallbackIngredient::fallback)
    ).apply(instance, TagFallbackIngredient::new));

    private TagKey<Item> activeTag() {
        return BuiltInRegistries.ITEM.getTagOrEmpty(preferred).iterator().hasNext() ? preferred : fallback;
    }
    @Override public Stream<Holder<Item>> items() {
        return StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(activeTag()).spliterator(), false);
    }
    @Override public boolean test(ItemStack stack) { return stack.is(activeTag()); }
    @Override public boolean isSimple() { return true; }
    @Override public IngredientType<?> getType() { return TGCrafting.TAG_FALLBACK.get(); }
    @Override public SlotDisplay display() { return new SlotDisplay.TagSlotDisplay(activeTag()); }
}
