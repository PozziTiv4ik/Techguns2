package techguns.modern.crafting;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;
import techguns.core.ProjectileKind;
import techguns.core.RocketVariant;
import techguns.core.BallisticVariant;
import techguns.core.IncendiaryAmmo;
import techguns.modern.GunItem;
import techguns.modern.TGContent;

/** Original shapeless ammo_change_crafting: replace the loaded round, retaining the gun's metadata. */
public record AmmoChangeRecipe(ShapelessRecipe base) implements CraftingRecipe {
    public static final MapCodec<AmmoChangeRecipe> CODEC = ShapelessRecipe.MAP_CODEC.xmap(AmmoChangeRecipe::new, AmmoChangeRecipe::base);
    public static final StreamCodec<RegistryFriendlyByteBuf, AmmoChangeRecipe> STREAM_CODEC = ShapelessRecipe.STREAM_CODEC.map(AmmoChangeRecipe::new, AmmoChangeRecipe::base);
    @Override public boolean matches(CraftingInput input, Level level) { return base.matches(input, level) && !assemble(input).isEmpty(); }
    @Override public ItemStack assemble(CraftingInput input) {
        ItemStack output = base.assemble(input), source = ItemStack.EMPTY;
        RocketVariant variant = null; BallisticVariant ballistic = null;
        if (input.ingredientCount() != 2 || !(output.getItem() instanceof GunItem gun)) return ItemStack.EMPTY;
        boolean rocket = gun.definition().projectile() == ProjectileKind.ROCKET;
        if (!rocket && !IncendiaryAmmo.supported(gun.definition())) return ItemStack.EMPTY;
        for (var item : input.items()) {
            if (item.is(output.getItem())) source = item;
            if (rocket) {
                for (var candidate : RocketVariant.values()) if (item.is(TGContent.AMMO.get(candidate.ammo()).get())) variant = candidate;
            } else {
                for (var candidate : BallisticVariant.values()) if (item.is(TGContent.AMMO.get(IncendiaryAmmo.ammo(gun.definition(), candidate).item()).get())) ballistic = candidate;
            }
        }
        if (source.isEmpty() || (rocket ? variant == null : ballistic == null)) return ItemStack.EMPTY;
        ItemStack copy = source.copyWithCount(1);
        copy.set(TGContent.ROUNDS.get(), gun.definition().ammo().individual() ? 1 : gun.definition().stats().capacity());
        if (rocket) copy.set(TGContent.ROCKET_VARIANT.get(), variant);
        else copy.set(TGContent.BALLISTIC_VARIANT.get(), ballistic);
        copy.remove(TGContent.AIMING.get());
        copy.remove(TGContent.RELOAD_TICKS.get());
        copy.remove(TGContent.MINIGUN_SPIN_TIME.get());
        return copy;
    }
    @Override public RecipeSerializer<AmmoChangeRecipe> getSerializer() { return TGCrafting.AMMO_CHANGE.get(); }
    @Override public CraftingBookCategory category() { return base.category(); }
    @Override public String group() { return base.group(); }
    @Override public boolean showNotification() { return base.showNotification(); }
    @Override public PlacementInfo placementInfo() { return base.placementInfo(); }
    @Override public List<RecipeDisplay> display() { return base.display(); }
}
