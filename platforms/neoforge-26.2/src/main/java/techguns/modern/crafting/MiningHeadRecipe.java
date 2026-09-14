package techguns.modern.crafting;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;
import techguns.core.ChainsawRules;
import techguns.modern.ChainsawItem;
import techguns.modern.TGContent;

/** Sequential source miningtool_upgrade, copying fuel and user metadata without ongoing player actions. */
public record MiningHeadRecipe(ShapelessRecipe base) implements CraftingRecipe {
    public static final MapCodec<MiningHeadRecipe> CODEC = ShapelessRecipe.MAP_CODEC.xmap(MiningHeadRecipe::new,MiningHeadRecipe::base);
    public static final StreamCodec<RegistryFriendlyByteBuf,MiningHeadRecipe> STREAM_CODEC = ShapelessRecipe.STREAM_CODEC.map(MiningHeadRecipe::new,MiningHeadRecipe::base);
    @Override public boolean matches(CraftingInput input,Level level) { return base.matches(input,level) && !assemble(input).isEmpty(); }
    @Override public ItemStack assemble(CraftingInput input) {
        if(input.ingredientCount()!=2) return ItemStack.EMPTY;
        var output=base.assemble(input);
        if(!(output.getItem() instanceof ChainsawItem)) return ItemStack.EMPTY;
        ItemStack source=ItemStack.EMPTY; int target=0;
        for(var item:input.items()) {
            if(item.is(output.getItem())) source=item;
            if(item.is(TGContent.MATERIALS.get("chainsawblades_obsidian").get())) target=1;
            if(item.is(TGContent.MATERIALS.get("chainsawblades_carbon").get())) target=2;
        }
        if(source.isEmpty() || !ChainsawRules.canUpgrade(ChainsawItem.head(source),target)) return ItemStack.EMPTY;
        var copy=source.copyWithCount(1); copy.set(TGContent.MINING_HEAD.get(),target);
        copy.remove(TGContent.AIMING.get()); copy.remove(TGContent.RELOAD_TICKS.get()); return copy;
    }
    @Override public RecipeSerializer<MiningHeadRecipe> getSerializer() { return TGCrafting.MINING_HEAD.get(); }
    @Override public CraftingBookCategory category() { return base.category(); }
    @Override public String group() { return base.group(); }
    @Override public boolean showNotification() { return base.showNotification(); }
    @Override public PlacementInfo placementInfo() { return base.placementInfo(); }
    @Override public List<RecipeDisplay> display() { return base.display(); }
}
