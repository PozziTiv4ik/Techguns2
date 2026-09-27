package techguns.modern;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import techguns.core.HandGrenade;

/** GenericGrenade: hold to change gravity, then release one real projectile. */
public final class GrenadeItem extends Item {
    private final HandGrenade grenade;
    public GrenadeItem(Properties properties, HandGrenade grenade) { super(properties); this.grenade=grenade; }
    public HandGrenade grenade() { return grenade; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return grenade.useDuration; }
    @Override public boolean useOnRelease(ItemStack stack) { return true; }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isAlive() || player.isSpectator()) return InteractionResult.FAIL;
        player.startUsingItem(hand);
        if (player.isUsingItem() && level instanceof ServerLevel && grenade==HandGrenade.FRAGGRENADE)
            level.playSound(null, player.getX(), player.getY(), player.getZ(), TGContent.GRENADE_PIN.get(), SoundSource.PLAYERS, 1, 1);
        return InteractionResult.CONSUME;
    }
    @Override public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (!(level instanceof ServerLevel server) || stack.isEmpty() || stack.getItem()!=this
                || !user.isAlive() || user.isSpectator() || !user.isUsingItem() || user.getUseItem()!=stack
                || user.getItemInHand(user.getUsedItemHand())!=stack) return false;
        var projectile = new GrenadeProjectile(TGContent.HAND_GRENADE.get(), server);
        projectile.configure(grenade, Math.clamp((long)grenade.useDuration-remaining, 0, grenade.useDuration));
        projectile.setOwner(user);
        boolean left = (user.getUsedItemHand()==InteractionHand.OFF_HAND) != (user.getMainArm()==HumanoidArm.LEFT);
        projectile.shootLegacy(user, left ? 1 : -1);
        // A protection mod can reject the spawn without consuming the player's last grenade.
        if (!server.addFreshEntity(projectile)) return false;
        if (user instanceof Player player) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        }
        user.swing(user.getUsedItemHand());
        return true;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                          Consumer<Component> output, TooltipFlag flag) {
        output.accept(Component.translatable("tooltip.techguns.grenade", grenade.damage, grenade.innerRadius, grenade.outerRadius, grenade.bounces));
    }
}
