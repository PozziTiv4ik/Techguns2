package techguns.modern;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import techguns.core.GunCamos;

/** The source gun's cosmetic index is independent of charges, ammo mode and player actions. */
public final class GunCamo {
    public static int count(ItemStack stack) { return stack.getItem() instanceof GunItem gun ? GunCamos.count(gun.definition().id()) : 0; }
    public static int index(ItemStack stack) {
        int index = stack.getOrDefault(TGContent.GUN_CAMO.get(), 0);
        return index >= 0 && index < count(stack) ? index : 0;
    }
    public static Component name(ItemStack stack) {
        return count(stack) > 0 ? Component.translatable(GunCamos.nameKey(((GunItem)stack.getItem()).definition().id(), index(stack))) : Component.empty();
    }
    public static void set(ItemStack stack, int index) {
        if (index < 0 || index >= count(stack)) throw new IllegalArgumentException("Invalid gun camouflage");
        stack.set(TGContent.GUN_CAMO.get(), index);
    }
    private GunCamo() {}
}
