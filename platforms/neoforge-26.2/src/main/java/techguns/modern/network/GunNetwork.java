package techguns.modern.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import techguns.modern.ReloadSessions;
import techguns.modern.RevolverItem;
import techguns.modern.TGContent;

public final class GunNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        // PayloadRegistrar defaults to the main game thread.
        event.registrar("1").playToServer(GunActionPayload.TYPE, GunActionPayload.CODEC,
                (payload, context) -> handle(context.player(), payload));
    }

    public static boolean handle(Player player, GunActionPayload payload) {
        if (!(player.level() instanceof ServerLevel server) || !player.isAlive() || player.isSpectator()
                || !player.getMainHandItem().is(TGContent.REVOLVER.get())) return false;
        return payload.reload() ? ReloadSessions.begin(player)
                : RevolverItem.fire(server, player, player.getMainHandItem());
    }

    private GunNetwork() {}
}
