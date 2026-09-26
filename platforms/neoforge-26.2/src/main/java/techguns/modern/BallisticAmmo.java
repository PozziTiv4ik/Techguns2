package techguns.modern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.item.ItemStack;
import techguns.core.*;

public final class BallisticAmmo {
    public static final Codec<BallisticVariant> CODEC = Codec.STRING.comapFlatMap(id -> {
        try { return DataResult.success(BallisticVariant.fromId(id)); }
        catch (IllegalArgumentException invalid) { return DataResult.error(invalid::getMessage); }
    }, BallisticVariant::id);
    public static BallisticVariant variant(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gun) || !IncendiaryAmmo.supported(gun.definition())) return BallisticVariant.DEFAULT;
        return stack.getOrDefault(TGContent.BALLISTIC_VARIANT.get(), BallisticVariant.DEFAULT);
    }
    public static AmmoSpec ammo(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gun)) throw new IllegalArgumentException("Expected gun");
        return IncendiaryAmmo.ammo(gun.definition(), variant(stack));
    }
    private BallisticAmmo() {}
}
