package techguns.modern;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import techguns.core.MinigunAnimation;
import techguns.core.WeaponDefinition;

/** Source empty-trigger reload and Creative ammunition policy; shared server ballistics. */
public final class MinigunItem extends GunItem {
    public MinigunItem(Properties properties, WeaponDefinition definition) { super(properties, definition); }

    @Override public boolean trigger(ServerLevel server, Player player, ItemStack stack) {
        if (player.level() != server || player.getMainHandItem() != stack) return false;
        return rounds(stack) == 0 ? ReloadSessions.begin(player) : super.trigger(server, player, stack);
    }

    @Override protected boolean consumesLoadedAmmo(Player player) { return !player.getAbilities().instabuild; }

    @Override protected void shotEffects(ServerLevel server, Player player, ItemStack stack) {
        long now = server.getGameTime();
        if (MinigunAnimation.startsSpin(stack.getOrDefault(TGContent.MINIGUN_SPIN_TIME.get(), -1L), now))
            stack.set(TGContent.MINIGUN_SPIN_TIME.get(), now);
        super.shotEffects(server, player, stack);
    }
}
