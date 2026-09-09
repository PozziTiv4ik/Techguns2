package techguns.modern.crafting;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import techguns.modern.Techguns;

public final class TGCrafting {
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Techguns.MOD_ID);
    private static final DeferredRegister<IngredientType<?>> INGREDIENTS = DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, Techguns.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CopyGunRecipe>> COPY_GUN = RECIPES.register("copy_gun",
            () -> new RecipeSerializer<>(CopyGunRecipe.CODEC, CopyGunRecipe.STREAM_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AmmoChangeRecipe>> AMMO_CHANGE = RECIPES.register("ammo_change_crafting",
            () -> new RecipeSerializer<>(AmmoChangeRecipe.CODEC, AmmoChangeRecipe.STREAM_CODEC));
    public static final DeferredHolder<IngredientType<?>, IngredientType<TagFallbackIngredient>> TAG_FALLBACK = INGREDIENTS.register("tag_fallback",
            () -> new IngredientType<>(TagFallbackIngredient.CODEC));
    public static void register(IEventBus bus) { RECIPES.register(bus); INGREDIENTS.register(bus); }
    private TGCrafting() {}
}
