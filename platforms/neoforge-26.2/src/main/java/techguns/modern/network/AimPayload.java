package techguns.modern.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import techguns.modern.TGContent;

public record AimPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<AimPayload> TYPE = new Type<>(TGContent.id("aim"));
    public static final StreamCodec<ByteBuf, AimPayload> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, AimPayload::enabled, AimPayload::new);
    @Override public Type<AimPayload> type() { return TYPE; }
}
