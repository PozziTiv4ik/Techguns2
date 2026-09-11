package techguns.modern;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    public static boolean fire(ServerLevel server, Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem item) || player.level() != server || !player.isAlive() || player.isSpectator()
                || (player.getMainHandItem() != stack && player.getOffhandItem() != stack)
                || player.getCooldowns().isOnCooldown(stack) || ReloadSessions.active(player)
                || !Magazine.canFire(item.definition.stats(), rounds(stack), 0, player.isUsingItem())) return false;
        WeaponDefinition gun = item.definition;
        boolean aiming = AimSessions.active(player, stack);
        double accuracyMultiplier = aiming ? gun.aim().accuracyMultiplier() : 1;
        for (int pellet = 0; pellet < gun.projectileCount(); pellet++) {
            if (gun.projectile() == techguns.core.ProjectileKind.NETHER_BLASTER) {
                var blast = new NetherBlasterProjectile(TGContent.NETHER_BLAST.get(), server);
                blast.configure(gun); blast.setOwner(player);
                blast.shootLegacy(player, gun.stats().spread() * accuracyMultiplier, aiming && gun.aim().centered());
                if (!server.addFreshEntity(blast)) return false;
                continue;
            }
            if (gun.projectile() == techguns.core.ProjectileKind.ROCKET) {
                RocketProjectile rocket = new RocketProjectile(TGContent.ROCKET.get(), server);
                rocket.configure(gun, RocketAmmo.variant(stack), !SafeMode.enabled(player));
                rocket.setOwner(player);
                rocket.shootLegacy(player, gun.stats().spread());
                if (!server.addFreshEntity(rocket)) return false;
                continue;
            }
            if (gun.projectile() == techguns.core.ProjectileKind.LASER) {
                LaserBeam beam = new LaserBeam(TGContent.LASER_BEAM.get(), server);
                beam.configure(gun);
                beam.setOwner(player);
                beam.shootLegacy(player, gun.stats().spread() * accuracyMultiplier, aiming && gun.aim().centered());
                if (!server.addFreshEntity(beam)) return false;
                beam.trace();
                continue;
            }
            Bullet bullet = new Bullet(TGContent.BULLET.get(), server);
            bullet.configure(gun);
            bullet.setOwner(player);
            bullet.shootLegacy(player, (pellet == 0 ? gun.stats().spread() : gun.pelletSpread()) * accuracyMultiplier,
                    aiming && gun.aim().centered());
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
        return availableAmmo(player, gun.ammo().item());
    }
    private static String ammoId(ItemStack stack, WeaponDefinition gun) {
        return gun.projectile() == techguns.core.ProjectileKind.ROCKET ? RocketAmmo.variant(stack).ammo() : gun.ammo().item();
    }
    public static int availableAmmo(Player player, ItemStack stack) {
        return stack.getItem() instanceof GunItem gun ? availableAmmo(player, ammoId(stack, gun.definition)) : 0;
    }
    private static int availableAmmo(Player player, String id) {
        Item ammo = TGContent.AMMO.get(id).get();
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.is(ammo)) total += candidate.getCount();
        }
        return total;
    }

    public static boolean canReload(Player player, ItemStack stack) {
        return stack.getItem() instanceof GunItem gun && rounds(stack) < gun.definition.stats().capacity()
                && (player.getAbilities().instabuild || availableAmmo(player, stack) > 0);
    }

    public static void completeReload(Player player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel) || !(stack.getItem() instanceof GunItem item)) return;
        WeaponDefinition gun = item.definition;
        Magazine.Plan plan = Magazine.plan(gun, rounds(stack), availableAmmo(player, stack), player.getAbilities().instabuild);
        int pending = plan.consumedItems();
        Item ammo = TGContent.AMMO.get(ammoId(stack, gun)).get();
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
    @Override public boolean isBarVisible(ItemStack stack) { return rounds(stack) < definition.stats().capacity(); }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13f * rounds(stack) / definition.stats().capacity()); }
    @Override public int getBarColor(ItemStack stack) { return 0xE9A63B; }
}
