package techguns.modern.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import techguns.modern.ReloadSessions;
import techguns.modern.GunItem;
import techguns.modern.AimSessions;

public final class GunNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        // PayloadRegistrar defaults to the main game thread.
        var registrar = event.registrar("2");
        registrar.playToServer(GunActionPayload.TYPE, GunActionPayload.CODEC, (payload, context) -> handle(context.player(), payload));
        registrar.playToServer(AimPayload.TYPE, AimPayload.CODEC, (payload, context) -> AimSessions.set(context.player(), payload.enabled()));
    }

    public static boolean handle(Player player, GunActionPayload payload) {
        if (!(player.level() instanceof ServerLevel server) || !player.isAlive() || player.isSpectator()
                || !(player.getMainHandItem().getItem() instanceof GunItem)) return false;
        return payload.reload() ? ReloadSessions.begin(player)
                : GunItem.fire(server, player, player.getMainHandItem());
    }

    private GunNetwork() {}
}
