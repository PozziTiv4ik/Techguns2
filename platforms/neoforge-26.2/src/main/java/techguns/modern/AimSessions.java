package techguns.modern;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Authoritative aiming state. The synchronized item flag is display-only; it is never trusted for damage. */
public final class AimSessions {
    private record Aimed(ItemStack stack, ServerLevel level) {}
    private static final Map<Player, Aimed> AIMED = new IdentityHashMap<>();

    public static boolean set(Player player, boolean enabled) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (!enabled) { cancel(player); return true; }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun) || !gun.definition().aim().supported()
                || !player.isAlive() || player.isSpectator() || player.isUsingItem() || ReloadSessions.active(player)) {
            cancel(player);
            return false;
        }
        cancel(player);
        AIMED.put(player, new Aimed(stack, level));
        stack.set(TGContent.AIMING.get(), true);
        return true;
    }

    public static boolean active(Player player, ItemStack stack) {
        Aimed state = AIMED.get(player);
        return state != null && state.stack() == stack && player.getMainHandItem() == stack
                && player.level() == state.level() && player.isAlive() && !player.isSpectator()
                && !player.isUsingItem() && !ReloadSessions.active(player);
    }

    public static void cancel(Player player) {
        Aimed old = AIMED.remove(player);
        if (old != null) old.stack().remove(TGContent.AIMING.get());
    }

    @SubscribeEvent public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel)) return;
        Aimed state = AIMED.get(player);
        if (state != null && !active(player, state.stack())) cancel(player);
    }
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { cancel(event.getEntity()); }
    @SubscribeEvent public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) { cancel(event.getEntity()); }
    @SubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { cancel(event.getEntity()); }
    @SubscribeEvent public static void onStop(ServerStoppedEvent event) {
        AIMED.values().forEach(state -> state.stack().remove(TGContent.AIMING.get()));
        AIMED.clear();
    }
    private AimSessions() {}
}
