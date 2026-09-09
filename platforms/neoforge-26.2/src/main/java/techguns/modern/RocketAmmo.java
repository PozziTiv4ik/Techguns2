package techguns.modern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.item.ItemStack;
import techguns.core.RocketVariant;

public final class RocketAmmo {
    public static final Codec<RocketVariant> CODEC = Codec.STRING.comapFlatMap(id -> {
        try { return DataResult.success(RocketVariant.fromId(id)); }
        catch (IllegalArgumentException invalid) { return DataResult.error(invalid::getMessage); }
    }, RocketVariant::id);
    public static RocketVariant variant(ItemStack stack) { return stack.getOrDefault(TGContent.ROCKET_VARIANT.get(), RocketVariant.DEFAULT); }
    private RocketAmmo() {}
}
