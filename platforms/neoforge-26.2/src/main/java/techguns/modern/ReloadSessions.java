package techguns.modern;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import techguns.core.Magazine;
import techguns.core.Weapons;

/** Server-only R-key reloads. Sessions are transient and never follow an item to another player. */
public final class ReloadSessions {
    private record Pending(ItemStack stack, ServerLevel level, int remaining) {}
    private static final Map<Player, Pending> PENDING = new IdentityHashMap<>();

    public static boolean active(Player player) { return PENDING.containsKey(player); }

    public static boolean begin(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()
                || active(player) || player.isUsingItem()) return false;
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(TGContent.REVOLVER.get()) || player.getCooldowns().isOnCooldown(stack)
                || RevolverItem.rounds(stack) == Weapons.REVOLVER.capacity()
                || (!player.getAbilities().instabuild && RevolverItem.findAmmo(player).isEmpty())) return false;
        PENDING.put(player, new Pending(stack, level, Weapons.REVOLVER.reloadTicks()));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                TGContent.REVOLVER_RELOAD.get(), SoundSource.PLAYERS, 1, 1);
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
            ItemStack ammo = RevolverItem.findAmmo(player);
            Magazine.Reload result = Magazine.reloadBundle(Weapons.REVOLVER, RevolverItem.rounds(pending.stack()),
                    ammo.getCount(), player.getAbilities().instabuild);
            ammo.shrink(result.consumedItems());
            pending.stack().set(TGContent.ROUNDS.get(), result.rounds());
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
