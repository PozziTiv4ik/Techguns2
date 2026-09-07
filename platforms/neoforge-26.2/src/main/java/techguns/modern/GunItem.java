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
import techguns.core.WeaponDefinition;

public final class GunItem extends Item {
    private final WeaponDefinition definition;
    public GunItem(Properties properties, WeaponDefinition definition) { super(properties); this.definition = definition; }
    public WeaponDefinition definition() { return definition; }
    public static int rounds(ItemStack stack) {
        return stack.getItem() instanceof GunItem gun
                ? gun.definition.stats().clampRounds(stack.getOrDefault(TGContent.ROUNDS.get(), 0)) : 0;
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack) || player.isUsingItem()) return InteractionResult.FAIL;
        if (level instanceof ServerLevel && ReloadSessions.active(player)) return InteractionResult.FAIL;
        if (player.isShiftKeyDown() || rounds(stack) == 0) {
            if (!canReload(player, stack)) return InteractionResult.FAIL;
            player.startUsingItem(hand);
            if (level instanceof ServerLevel) playReload(player, definition);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    public static boolean fire(ServerLevel server, Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem item) || player.level() != server || !player.isAlive() || player.isSpectator()
                || (player.getMainHandItem() != stack && player.getOffhandItem() != stack)
                || player.getCooldowns().isOnCooldown(stack) || ReloadSessions.active(player)
                || !Magazine.canFire(item.definition.stats(), rounds(stack), 0, player.isUsingItem())) return false;
        WeaponDefinition gun = item.definition;
        for (int pellet = 0; pellet < gun.projectileCount(); pellet++) {
            Bullet bullet = new Bullet(TGContent.BULLET.get(), server);
            bullet.configure(gun);
            bullet.setOwner(player);
            bullet.shootLegacy(player, pellet == 0 ? gun.stats().spread() : gun.pelletSpread());
            // The first round must enter the world before consuming ammo. A later spawn
            // cancellation suppresses that pellet but does not refund an already fired shot.
            if (!server.addFreshEntity(bullet) && pellet == 0) return false;
        }
        stack.set(TGContent.ROUNDS.get(), Magazine.afterShot(gun.stats(), rounds(stack)));
        player.getCooldowns().addCooldown(stack, gun.stats().fireDelay());
        server.playSound(null, player.getX(), player.getY(), player.getZ(),
                TGContent.SOUND_EVENTS.get(gun.fireSound()).get(), SoundSource.PLAYERS, 2, 1);
        return true;
    }

    public static int availableAmmo(Player player, WeaponDefinition gun) {
        Item ammo = TGContent.AMMO.get(gun.ammo().item()).get();
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.is(ammo)) total += candidate.getCount();
        }
        return total;
    }

    public static boolean canReload(Player player, ItemStack stack) {
        return stack.getItem() instanceof GunItem gun && rounds(stack) < gun.definition.stats().capacity()
                && (player.getAbilities().instabuild || availableAmmo(player, gun.definition) > 0);
    }

    public static void completeReload(Player player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel) || !(stack.getItem() instanceof GunItem item)) return;
        WeaponDefinition gun = item.definition;
        Magazine.Plan plan = Magazine.plan(gun, rounds(stack), availableAmmo(player, gun), player.getAbilities().instabuild);
        int pending = plan.consumedItems();
        Item ammo = TGContent.AMMO.get(gun.ammo().item()).get();
        for (int slot = 0; slot < player.getInventory().getContainerSize() && pending > 0; slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.is(ammo)) {
                int count = Math.min(candidate.getCount(), pending);
                candidate.shrink(count);
                pending -= count;
            }
        }
        stack.set(TGContent.ROUNDS.get(), plan.rounds());
        giveRemainder(player, gun.ammo().emptyItem(), plan.emptyMagazines());
        giveRemainder(player, gun.ammo().looseItem(), plan.looseBundles());
    }

    private static void giveRemainder(Player player, String id, int count) {
        if (count <= 0) return;
        ItemStack remainder = TGContent.AMMO.get(id).toStack(count);
        player.getInventory().add(remainder);
        if (!remainder.isEmpty()) player.drop(remainder, false);
    }

    public static void playReload(Player player, WeaponDefinition gun) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                TGContent.SOUND_EVENTS.get(gun.reloadSound()).get(), SoundSource.PLAYERS, 1, 1);
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return definition.stats().reloadTicks(); }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BOW; }
    @Override public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) { return oldStack == newStack; }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (level instanceof ServerLevel && user instanceof Player player) completeReload(player, stack);
        return stack;
    }
    @Override public boolean isBarVisible(ItemStack stack) { return rounds(stack) < definition.stats().capacity(); }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13f * rounds(stack) / definition.stats().capacity()); }
    @Override public int getBarColor(ItemStack stack) { return 0xE9A63B; }
}
