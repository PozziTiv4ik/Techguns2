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
    public static int count(ItemStack stack) { return stack.isEmpty() ? 0 : stack.getItem() instanceof TGArmorItem item ? item.spec().camos().size() : CamoPalettes.forItem(id(stack)).map(p -> p.items().size()).orElse(0); }
    public static int index(ItemStack stack) { return stack.getItem() instanceof TGArmorItem ? TGArmorItem.camo(stack) : CamoPalettes.forItem(id(stack)).map(p -> p.index(id(stack))).orElse(-1); }
    public static Component variantName(ItemStack stack) {
        if (stack.getItem() instanceof TGArmorItem) return TGArmorItem.camoName(stack);
        return CamoPalettes.forItem(id(stack)).<Component>map(p -> Component.translatable("gui.techguns.camo.color." + id(stack).substring("minecraft:".length(), id(stack).length() - p.id().length() - 1)))
                .orElseGet(() -> Component.translatable("gui.techguns.camo.unsupported"));
    }
    public static Optional<ItemStack> change(ItemStack stack, boolean back) {
        if (stack.isEmpty()) return Optional.empty();
        if (stack.getItem() instanceof TGArmorItem) {
            var result = stack.copy(); TGArmorItem.setCamo(result, CamoPalette.cycle(TGArmorItem.camo(stack), count(stack), back)); return Optional.of(result);
        }
        return CamoPalettes.forItem(id(stack)).map(p -> stack.transmuteCopy(BuiltInRegistries.ITEM.getValue(Identifier.parse(p.next(id(stack), back)))));
    }
    private CamoCycling() {}
}
