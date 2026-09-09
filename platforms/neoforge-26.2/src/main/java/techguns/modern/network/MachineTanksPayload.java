package techguns.modern.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import techguns.modern.TGContent;
import techguns.modern.machine.ChemLabMenu;

/** Full fluid stacks retain names and other data components across menu synchronization. */
public record MachineTanksPayload(int containerId,FluidStack input,FluidStack output) implements CustomPacketPayload {
    public static final Type<MachineTanksPayload> TYPE=new Type<>(TGContent.id("machine_tanks"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MachineTanksPayload> CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,MachineTanksPayload::containerId,
            FluidStack.OPTIONAL_STREAM_CODEC,MachineTanksPayload::input,
            FluidStack.OPTIONAL_STREAM_CODEC,MachineTanksPayload::output,MachineTanksPayload::new);
    @Override public Type<MachineTanksPayload> type() { return TYPE; }
    public void apply(ChemLabMenu menu) { if(menu.containerId==containerId) menu.updateFluids(input,output); }
    public void apply(techguns.modern.machine.reaction.ReactionChamberMenu menu) { if(menu.containerId==containerId) menu.updateFluid(input); }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE,CODEC,(payload,context) -> {
            if (context.player().containerMenu instanceof ChemLabMenu menu) payload.apply(menu);
            else if (context.player().containerMenu instanceof techguns.modern.machine.reaction.ReactionChamberMenu menu) payload.apply(menu);
        });
    }
}
