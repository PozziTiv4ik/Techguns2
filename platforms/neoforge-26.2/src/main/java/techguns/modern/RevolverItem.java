package techguns.modern;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import techguns.core.Magazine;
import techguns.core.Weapons;

/** First weapon being migrated. Vanilla use packets leave all inventory and damage decisions on the server. */
public final class RevolverItem extends Item {
    public RevolverItem(Properties properties) { super(properties); }

    public static int rounds(ItemStack stack) {
        return Weapons.REVOLVER.clampRounds(stack.getOrDefault(TGContent.ROUNDS.get(), 0));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack) || player.isUsingItem()) return InteractionResult.FAIL;
        if (player.isShiftKeyDown() || rounds(stack) == 0) {
            if (rounds(stack) == Weapons.REVOLVER.capacity()
                    || (!player.getAbilities().instabuild && findAmmo(player).isEmpty())) return InteractionResult.FAIL;
            player.startUsingItem(hand);
            if (level instanceof ServerLevel) level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    TGContent.REVOLVER_RELOAD.get(), SoundSource.PLAYERS, 1, 1);
            return InteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel server) fire(server, player, stack);
        return InteractionResult.SUCCESS;
    }

    /** Returns false without side effects if server state disallows a shot. Also used by integration tests. */
    public static boolean fire(ServerLevel server, Player player, ItemStack stack) {
        if (!stack.is(TGContent.REVOLVER.get()) || player.isSpectator()
                || (player.getMainHandItem() != stack && player.getOffhandItem() != stack)
                || player.getCooldowns().isOnCooldown(stack)
                || !Magazine.canFire(Weapons.REVOLVER, rounds(stack), 0, player.isUsingItem())) return false;
        Bullet bullet = new Bullet(TGContent.BULLET.get(), server);
        bullet.setOwner(player);
        bullet.setPos(player.getEyePosition());
        bullet.shootFromRotation(player, player.getXRot(), player.getYRot(), 0,
                (float) Weapons.REVOLVER.projectileSpeed(), (float) (Weapons.REVOLVER.spread() / 0.0075));
        if (!server.addFreshEntity(bullet)) return false;
        stack.set(TGContent.ROUNDS.get(), Magazine.afterShot(Weapons.REVOLVER, rounds(stack)));
        player.getCooldowns().addCooldown(stack, Weapons.REVOLVER.fireDelay());
        server.playSound(null, player.getX(), player.getY(), player.getZ(),
                TGContent.REVOLVER_FIRE.get(), SoundSource.PLAYERS, 2, 1);
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return Weapons.REVOLVER.reloadTicks(); }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BOW; }

    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        return oldStack == newStack;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (level instanceof ServerLevel && user instanceof Player player) {
            ItemStack ammo = findAmmo(player);
            Magazine.Reload reload = Magazine.reloadBundle(Weapons.REVOLVER, rounds(stack), ammo.getCount(),
                    player.getAbilities().instabuild);
            // No ammo is reserved at start: cancelling use cannot lose or duplicate ammunition.
            ammo.shrink(reload.consumedItems());
            stack.set(TGContent.ROUNDS.get(), reload.rounds());
        }
        return stack;
    }

    private static ItemStack findAmmo(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.is(TGContent.PISTOL_ROUNDS.get())) return candidate;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return rounds(stack) < Weapons.REVOLVER.capacity(); }
    @Override
    public int getBarWidth(ItemStack stack) { return Math.round(13f * rounds(stack) / Weapons.REVOLVER.capacity()); }
    @Override
    public int getBarColor(ItemStack stack) { return 0xE9A63B; }
}
