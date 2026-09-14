package techguns.modern.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import techguns.modern.ChainsawItem;

public record MiningHeadProperty(int level) implements ConditionalItemModelProperty {
    public static final MapCodec<MiningHeadProperty> CODEC = Codec.intRange(0,2).fieldOf("level").xmap(MiningHeadProperty::new,MiningHeadProperty::level);
    @Override public boolean get(ItemStack stack, ClientLevel world, LivingEntity owner, int seed, ItemDisplayContext context) { return ChainsawItem.head(stack)==level; }
    @Override public MapCodec<MiningHeadProperty> type() { return CODEC; }
}
