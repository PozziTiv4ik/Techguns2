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

/** Server-only R-key reloads. Sessions are transient and never follow an item to another player. */
public final class ReloadSessions {
    private record Pending(ItemStack stack, ServerLevel level, int remaining) {}
    private static final Map<Player, Pending> PENDING = new IdentityHashMap<>();

    public static boolean active(Player player) { return PENDING.containsKey(player); }

    public static boolean begin(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()
                || active(player) || player.isUsingItem()) return false;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun) || player.getCooldowns().isOnCooldown(stack)
                || !GunItem.canReload(player, stack)) return false;
        PENDING.put(player, new Pending(stack, level, gun.definition().stats().reloadTicks()));
        GunItem.playReload(player, gun.definition());
        return true;
    }

    public static void tick(Player player) {
        Pending pending = PENDING.get(player);
        if (pending == null) return;
        if (!player.isAlive() || player.isSpectator() || player.level() != pending.level()
                || player.getMainHandItem() != pending.stack() || player.isUsingItem()) {
            cancel(player);
        } else if (pending.remaining() > 1) {
            PENDING.put(player, new Pending(pending.stack(), pending.level(), pending.remaining() - 1));
        } else {
            GunItem.completeReload(player, pending.stack());
            cancel(player);
        }
    }

    public static void cancel(Player player) { PENDING.remove(player); }

    @SubscribeEvent public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel) tick(event.getEntity());
    }
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { cancel(event.getEntity()); }
    @SubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { cancel(event.getEntity()); }
    @SubscribeEvent public static void onStop(ServerStoppedEvent event) { PENDING.clear(); }

    private ReloadSessions() {}
}
