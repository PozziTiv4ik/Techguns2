package techguns.modern.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import techguns.core.ExplosionMath;
import techguns.modern.GunItem;
import techguns.modern.TGContent;

public record RocketLoadedProperty() implements ConditionalItemModelProperty {
    public static final MapCodec<RocketLoadedProperty> CODEC = MapCodec.unit(new RocketLoadedProperty());
    @Override public boolean get(ItemStack stack, ClientLevel level, LivingEntity owner, int seed, ItemDisplayContext context) {
        return stack.getItem() instanceof GunItem gun && ExplosionMath.rocketVisible(GunItem.rounds(stack),
                stack.getOrDefault(TGContent.RELOAD_TICKS.get(), 0), gun.definition().stats().reloadTicks());
    }
    @Override public MapCodec<RocketLoadedProperty> type() { return CODEC; }
}
