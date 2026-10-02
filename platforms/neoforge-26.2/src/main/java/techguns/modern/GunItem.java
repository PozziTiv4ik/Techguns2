package techguns.modern;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import techguns.core.Magazine;
import techguns.core.WeaponDefinition;

public class GunItem extends Item {
    private final WeaponDefinition definition;
    public GunItem(Properties properties, WeaponDefinition definition) { super(properties); this.definition = definition; }
    public WeaponDefinition definition() { return definition; }
    public boolean trigger(ServerLevel server, Player player, ItemStack stack) { return fire(server, player, stack); }
    protected boolean consumesLoadedAmmo(Player player) {
        return !(this instanceof ChainsawItem || definition.projectile() == techguns.core.ProjectileKind.GAUSS
                || definition.projectile() == techguns.core.ProjectileKind.BLASTER
                || definition.projectile() == techguns.core.ProjectileKind.ALIEN_BLASTER)
                || !player.getAbilities().instabuild;
    }
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
        float accuracyMultiplier = techguns.modern.armor.TGArmorSystem.gunAccuracyMultiplier(player);
        if (aiming) accuracyMultiplier *= gun.aim().accuracyMultiplier();
        for (int pellet = 0; pellet < gun.projectileCount(); pellet++) {
            if (gun.projectile() == techguns.core.ProjectileKind.ALIEN_BLASTER) {
                var blast = new AlienBlasterProjectile(TGContent.ALIEN_BLAST.get(), server);
                blast.configure(gun, !SafeMode.enabled(player)); blast.setOwner(player);
                blast.shootLegacy(player, gun.stats().spread() * accuracyMultiplier, LegacyShot.muzzleSide(player, player.getOffhandItem() == stack));
                if (!server.addFreshEntity(blast)) return false;
                continue;
            }
            if (gun.projectile() == techguns.core.ProjectileKind.BLASTER) {
                var blast = new BlasterProjectile(TGContent.BLASTER.get(), server);
                blast.configure(gun); blast.setOwner(player);
                blast.shootLegacy(player, (pellet == 0 ? gun.stats().spread() : gun.pelletSpread()) * accuracyMultiplier,
                        aiming && gun.aim().centered() ? 0 : LegacyShot.muzzleSide(player, player.getOffhandItem() == stack));
                if (!server.addFreshEntity(blast) && pellet == 0) return false;
                continue;
            }
            if (gun.projectile() == techguns.core.ProjectileKind.GAUSS) {
                var slug = new GaussProjectile(TGContent.GAUSS.get(), server);
                slug.configure(gun); slug.setOwner(player);
                slug.shootLegacy(player, gun.stats().spread() * accuracyMultiplier,
                        aiming && gun.aim().centered() ? 0 : LegacyShot.muzzleSide(player, player.getOffhandItem() == stack));
                if (!server.addFreshEntity(slug)) return false;
                continue;
            }
            if (gun.projectile() == techguns.core.ProjectileKind.FLAME) {
                var flame = new FlameProjectile(TGContent.FLAME.get(), server);
                flame.configure(gun, !SafeMode.enabled(player)); flame.setOwner(player);
                flame.shootLegacy(player, gun.stats().spread() * accuracyMultiplier, LegacyShot.muzzleSide(player, player.getOffhandItem() == stack));
                flame.setPos(flame.position().add(flame.getDeltaMovement().scale(techguns.core.NpcWeapons.forWeapon(gun.id()).forwardOffset() / gun.stats().projectileSpeed())));
                if (!server.addFreshEntity(flame)) return false;
                continue;
            }
            if(gun.projectile()==techguns.core.ProjectileKind.GRENADE_40MM) {
                var grenade=new Grenade40mmProjectile(TGContent.GRENADE_40MM.get(),server);
                grenade.configure(gun);grenade.setOwner(player);grenade.shootLegacy(player,gun.stats().spread()*accuracyMultiplier);
                if(!server.addFreshEntity(grenade)) return false;
                stack.set(TGContent.LAUNCHER_SHOT_TIME.get(),server.getGameTime());
                continue;
            }
            if (gun.projectile() == techguns.core.ProjectileKind.CHAINSAW) {
                var attack = new ChainsawAttack(TGContent.CHAINSAW_ATTACK.get(), server);
                attack.configure(gun); attack.setOwner(player); attack.shootLegacy(player, gun.stats().spread() * accuracyMultiplier);
                if (!server.addFreshEntity(attack)) return false;
                continue;
            }
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
                rocket.shootLegacy(player, gun.stats().spread() * accuracyMultiplier);
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
            if (BallisticAmmo.variant(stack) == techguns.core.BallisticVariant.INCENDIARY) {
                var incendiary = new IncendiaryBullet(TGContent.INCENDIARY_BULLET.get(), server);
                incendiary.configure(gun, !SafeMode.enabled(player)); incendiary.setOwner(player);
                incendiary.shootLegacy(player, (pellet == 0 ? gun.stats().spread() : gun.pelletSpread()) * accuracyMultiplier,
                        aiming && gun.aim().centered());
                if (!server.addFreshEntity(incendiary) && pellet == 0) return false;
                continue;
            }
            if (BallisticAmmo.variant(stack) == techguns.core.BallisticVariant.EXPLOSIVE) {
                var explosive = new ExplosiveBullet(TGContent.EXPLOSIVE_BULLET.get(), server);
                explosive.configure(gun, !SafeMode.enabled(player)); explosive.setOwner(player);
                explosive.shootLegacy(player, gun.stats().spread() * accuracyMultiplier, aiming && gun.aim().centered());
                if (!server.addFreshEntity(explosive)) return false;
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
        if (item.consumesLoadedAmmo(player))
            stack.set(TGContent.ROUNDS.get(), Magazine.afterShot(gun.stats(), rounds(stack)));
        player.getCooldowns().addCooldown(stack, gun.stats().firingInterval());
        item.shotEffects(server, player, stack);
        return true;
    }

    protected void shotEffects(ServerLevel server, Player player, ItemStack stack) {
        if (this instanceof ChainsawItem) ChainsawItem.playChain(server,player);
        else if (definition.projectile() == techguns.core.ProjectileKind.FLAME) FlameFiring.playerShot(server, player, stack);
        else server.playSound(null, player.getX(), player.getY(), player.getZ(),
                TGContent.SOUND_EVENTS.get(definition.fireSound()).get(), SoundSource.PLAYERS, 2, 1);
        if (definition.projectile() == techguns.core.ProjectileKind.GAUSS)
            server.playSound(null, player.getX(), player.getY(), player.getZ(), TGContent.GAUSS_RECHAMBER.get(), SoundSource.PLAYERS, 1, 1);
    }

    public static int availableAmmo(Player player, WeaponDefinition gun) {
        return availableAmmo(player, gun.ammo());
    }
    public static String ammoId(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gun)) return "";
        return gun.definition.projectile() == techguns.core.ProjectileKind.ROCKET ? RocketAmmo.variant(stack).ammo() : BallisticAmmo.ammo(stack).item();
    }
    public static int availableAmmo(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gun)) return 0;
        return gun.definition.projectile() == techguns.core.ProjectileKind.ROCKET
                ? availableAmmo(player, ammoId(stack)) : availableAmmo(player, BallisticAmmo.ammo(stack));
    }
    private static int availableAmmo(Player player, techguns.core.AmmoSpec ammo) {
        return ammo.components().stream().mapToInt(part -> availableAmmo(player, part.item())).min().orElse(0);
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
        var selectedAmmo = BallisticAmmo.ammo(stack);
        var inputs = gun.projectile() == techguns.core.ProjectileKind.ROCKET
                ? java.util.List.of(ammoId(stack)) : selectedAmmo.components().stream().map(techguns.core.AmmoSpec.Component::item).toList();
        // Check every input before mutating any stack. No hooks run between this check and consumption.
        if (inputs.stream().anyMatch(id -> availableAmmo(player, id) < plan.consumedItems())) return;
        for (String id : inputs) {
            int pending = plan.consumedItems();
            Item ammo = TGContent.AMMO.get(id).get();
            for (int slot = 0; slot < player.getInventory().getContainerSize() && pending > 0; slot++) {
                ItemStack candidate = player.getInventory().getItem(slot);
                if (candidate.is(ammo)) {
                    int count = Math.min(candidate.getCount(), pending);
                    candidate.shrink(count);
                    pending -= count;
                }
            }
        }
        stack.set(TGContent.ROUNDS.get(), plan.rounds());
        for (var part : selectedAmmo.components()) {
            giveRemainder(player, part.emptyItem(), plan.emptyMagazines());
            giveRemainder(player, part.looseItem(), plan.looseBundles());
        }
    }

    private static void giveRemainder(Player player, String id, int count) {
        if (count <= 0 || id.isEmpty()) return;
        ItemStack remainder = TGContent.AMMO.get(id).toStack(count);
        player.getInventory().add(remainder);
        if (!remainder.isEmpty()) player.drop(remainder, false);
    }

    public static void playReload(Player player, WeaponDefinition gun) {
        var pos = gun.projectile() == techguns.core.ProjectileKind.FLAME ? FlameFiring.soundPosition(player) : player.position();
        player.level().playSound(null, pos.x, pos.y, pos.z,
                TGContent.SOUND_EVENTS.get(gun.reloadSound()).get(), SoundSource.PLAYERS, 1, 1);
    }
    @Override public boolean isBarVisible(ItemStack stack) { return rounds(stack) < definition.stats().capacity(); }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13f * rounds(stack) / definition.stats().capacity()); }
    @Override public int getBarColor(ItemStack stack) { return 0xE9A63B; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                         java.util.function.Consumer<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        if (definition.ammo().components().size() == 2) lines.accept(net.minecraft.network.chat.Component.translatable("tooltip.techguns.compound_ammo",
                TGContent.AMMO.get(definition.ammo().components().get(0).item()).toStack().getHoverName(),
                TGContent.AMMO.get(definition.ammo().components().get(1).item()).toStack().getHoverName()));
        if (techguns.core.IncendiaryAmmo.supported(definition)) lines.accept(net.minecraft.network.chat.Component.translatable("tooltip.techguns.loaded_ammo",
                net.minecraft.network.chat.Component.translatable("hud.techguns.ballistic." + BallisticAmmo.variant(stack).id())));
    }
}
