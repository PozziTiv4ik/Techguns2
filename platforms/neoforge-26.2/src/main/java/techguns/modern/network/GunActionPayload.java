package techguns.modern.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import techguns.modern.TGContent;

/** Exactly one boolean: no client-supplied damage, ammo count, target, slot or coordinates. */
public record GunActionPayload(boolean reload) implements CustomPacketPayload {
    public static final Type<GunActionPayload> TYPE = new Type<>(TGContent.id("gun_action"));
    public static final StreamCodec<ByteBuf, GunActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GunActionPayload::reload, GunActionPayload::new);
    @Override public Type<GunActionPayload> type() { return TYPE; }
}
