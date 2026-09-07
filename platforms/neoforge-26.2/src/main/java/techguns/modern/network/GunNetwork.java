package techguns.modern.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import techguns.modern.ReloadSessions;
import techguns.modern.GunItem;

public final class GunNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        // PayloadRegistrar defaults to the main game thread.
        event.registrar("1").playToServer(GunActionPayload.TYPE, GunActionPayload.CODEC,
                (payload, context) -> handle(context.player(), payload));
    }

    public static boolean handle(Player player, GunActionPayload payload) {
        if (!(player.level() instanceof ServerLevel server) || !player.isAlive() || player.isSpectator()
                || !(player.getMainHandItem().getItem() instanceof GunItem)) return false;
        return payload.reload() ? ReloadSessions.begin(player)
                : GunItem.fire(server, player, player.getMainHandItem());
    }

    private GunNetwork() {}
}
