package techguns.modern.machine.camo;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import techguns.core.*;
import techguns.modern.armor.TGArmorItem;

public final class CamoCycling {
    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static Optional<CamoPalette> palette(String item) { return CamoPalettes.forItem(item).or(()->NetherMetal.PALETTE.index(item)>=0?Optional.of(NetherMetal.PALETTE):Optional.empty()).or(()->BuildingBlocks.palette(item)).or(()->CamouflageNets.palette(item)); }
    public static int count(ItemStack stack) { return stack.isEmpty() ? 0 : stack.getItem() instanceof TGArmorItem item ? (item.spec().canChangeCamo() ? item.spec().camos().size() : 0) : palette(id(stack)).map(p -> p.items().size()).orElse(0); }
    public static int index(ItemStack stack) { return count(stack)==0 ? -1 : stack.getItem() instanceof TGArmorItem ? TGArmorItem.camo(stack) : palette(id(stack)).map(p -> p.index(id(stack))).orElse(-1); }
    public static Component variantName(ItemStack stack) {
        if (count(stack)==0) return Component.translatable("gui.techguns.camo.unsupported");
        if (stack.getItem() instanceof TGArmorItem) return TGArmorItem.camoName(stack);
        if (NetherMetal.PALETTE.index(id(stack))>=0 || CamouflageNets.palette(id(stack)).isPresent()) return Component.translatable("block.techguns."+BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        var building=BuildingBlocks.variant(id(stack));
        if (building.isPresent()) return Component.translatable(building.get().camoKey());
        return CamoPalettes.forItem(id(stack)).<Component>map(p -> Component.translatable("gui.techguns.camo.color." + id(stack).substring("minecraft:".length(), id(stack).length() - p.id().length() - 1)))
                .orElseGet(() -> Component.translatable("gui.techguns.camo.unsupported"));
    }
    public static Optional<ItemStack> change(ItemStack stack, boolean back) {
        if (count(stack)==0) return Optional.empty();
        if (stack.getItem() instanceof TGArmorItem) {
            var result = stack.copy(); TGArmorItem.setCamo(result, CamoPalette.cycle(TGArmorItem.camo(stack), count(stack), back)); return Optional.of(result);
        }
        return palette(id(stack)).map(p -> stack.transmuteCopy(BuiltInRegistries.ITEM.getValue(Identifier.parse(p.next(id(stack), back)))));
    }
    private CamoCycling() {}
}
