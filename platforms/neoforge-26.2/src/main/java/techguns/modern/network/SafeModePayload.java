package techguns.modern.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import techguns.modern.TGContent;

public record SafeModePayload() implements CustomPacketPayload {
    public static final SafeModePayload INSTANCE = new SafeModePayload();
    public static final Type<SafeModePayload> TYPE = new Type<>(TGContent.id("toggle_safe_mode"));
    public static final StreamCodec<ByteBuf, SafeModePayload> CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<SafeModePayload> type() { return TYPE; }
}
