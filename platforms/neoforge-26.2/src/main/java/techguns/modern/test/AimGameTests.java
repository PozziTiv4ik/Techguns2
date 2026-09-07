package techguns.modern.test;

import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.modern.AimSessions;
import techguns.modern.Bullet;
import techguns.modern.GunItem;
import techguns.modern.ReloadSessions;
import techguns.modern.TGContent;
import techguns.modern.network.AimPayload;

final class AimGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        registry.register("aim_supported_and_toggle", () -> AimGameTests::supported);
        registry.register("aim_reload_cancels", () -> AimGameTests::reloadCancels);
        registry.register("aim_stack_swap_cancels", () -> AimGameTests::swapCancels);
        registry.register("aim_centered_projectile", () -> AimGameTests::centeredProjectile);
        registry.register("aim_display_flag_untrusted", () -> AimGameTests::untrustedFlag);
        registry.register("aim_transient_state_not_saved", () -> AimGameTests::transientState);
        registry.register("aim_codec_roundtrip", () -> AimGameTests::codec);
        registry.register("aim_death_cancels", () -> AimGameTests::deathCancels);
    }

    private static Player player(GameTestHelper helper, String weapon) {
        Player player = WeaponGameTests.player(helper);
        ItemStack gun = TGContent.GUNS.get(weapon).toStack();
        gun.set(TGContent.ROUNDS.get(), ((GunItem) gun.getItem()).definition().stats().capacity() - 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        return player;
    }

    private static void supported(GameTestHelper helper) {
        Player player = player(helper, "m4");
        helper.assertTrue(AimSessions.set(player, true), "M4 supports its original sight");
        helper.assertTrue(AimSessions.active(player, player.getMainHandItem()), "Server state active");
        helper.assertTrue(player.getMainHandItem().getOrDefault(TGContent.AIMING.get(), false), "Aim flag synchronized on the weapon");
        helper.assertTrue(AimSessions.set(player, false), "Aim can be disabled");
        helper.assertTrue(!AimSessions.active(player, player.getMainHandItem()), "Server state cleared");
        player.setItemInHand(InteractionHand.MAIN_HAND, TGContent.GUNS.get("pistol").toStack());
        helper.assertTrue(!AimSessions.set(player, true), "Pistol has no unsupported zoom bonus");
        helper.succeed();
    }

    private static void reloadCancels(GameTestHelper helper) {
        Player player = player(helper, "m4");
        ItemStack gun = player.getMainHandItem();
        player.getInventory().setItem(1, TGContent.AMMO.get("assaultriflemagazine").toStack());
        helper.assertTrue(AimSessions.set(player, true), "Aim starts");
        helper.assertTrue(ReloadSessions.begin(player), "Reload starts");
        helper.assertTrue(!gun.getOrDefault(TGContent.AIMING.get(), false), "Reload clears visible aiming");
        helper.assertTrue(!AimSessions.set(player, true), "Cannot aim during reload");
        helper.assertValueEqual(gun.getOrDefault(TGContent.RELOAD_TICKS.get(), 0), 45, "Reload duration synchronized");
        player.tick();
        helper.assertValueEqual(gun.getOrDefault(TGContent.RELOAD_TICKS.get(), 0), 44, "HUD progress follows server timer");
        ReloadSessions.cancel(player);
        helper.assertValueEqual(gun.getOrDefault(TGContent.RELOAD_TICKS.get(), 0), 0, "Cancelled progress removed");
        helper.succeed();
    }

    private static void swapCancels(GameTestHelper helper) {
        Player player = player(helper, "m4");
        ItemStack original = player.getMainHandItem();
        helper.assertTrue(AimSessions.set(player, true), "Aim starts");
        player.setItemInHand(InteractionHand.MAIN_HAND, TGContent.GUNS.get("m4").toStack());
        player.tick();
        helper.assertTrue(!AimSessions.active(player, player.getMainHandItem()), "Aiming does not transfer to another copy");
        helper.assertTrue(!original.getOrDefault(TGContent.AIMING.get(), false), "Previous copy has no stale aim flag");
        helper.succeed();
    }

    private static void centeredProjectile(GameTestHelper helper) {
        Player player = player(helper, "m4_infiltrator");
        helper.assertTrue(AimSessions.set(player, true), "Infiltrator aims");
        helper.assertTrue(GunItem.fire(helper.getLevel(), player, player.getMainHandItem()), "Can fire while aiming");
        var bullets = helper.getLevel().getEntitiesOfClass(Bullet.class, player.getBoundingBox().inflate(3), b -> b.getOwner() == player);
        helper.assertValueEqual(bullets.size(), 1, "Aimed shot spawned");
        Bullet bullet = bullets.getFirst();
        helper.assertTrue(Math.abs(bullet.getX() - player.getX()) < .000001 && Math.abs(bullet.getZ() - player.getZ()) < .000001,
                "Original centered aiming removes lateral muzzle offset");
        AimSessions.cancel(player);
        helper.succeed();
    }

    private static void untrustedFlag(GameTestHelper helper) {
        Player player = player(helper, "m4_infiltrator");
        player.getMainHandItem().set(TGContent.AIMING.get(), true);
        helper.assertTrue(!AimSessions.active(player, player.getMainHandItem()), "Display component cannot authorize aim bonuses");
        helper.assertTrue(GunItem.fire(helper.getLevel(), player, player.getMainHandItem()), "Hip-fire still allowed");
        var bullet = helper.getLevel().getEntitiesOfClass(Bullet.class, player.getBoundingBox().inflate(3), b -> b.getOwner() == player).getFirst();
        helper.assertTrue(Math.abs(bullet.getX() - player.getX()) > .15, "Untrusted component does not center the muzzle");
        helper.succeed();
    }

    private static void transientState(GameTestHelper helper) {
        Player player = player(helper, "m4");
        ItemStack gun = player.getMainHandItem();
        helper.assertTrue(AimSessions.set(player, true), "Aim starts");
        gun.set(TGContent.RELOAD_TICKS.get(), 20);
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var saved = ItemStack.CODEC.encodeStart(ops, gun).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        helper.assertTrue(!loaded.getOrDefault(TGContent.AIMING.get(), false), "Aim cannot become stuck after save/reload");
        helper.assertValueEqual(loaded.getOrDefault(TGContent.RELOAD_TICKS.get(), 0), 0, "Reload HUD cannot become stuck after save/reload");
        helper.assertValueEqual(GunItem.rounds(loaded), 29, "Actual ammo still persists");
        AimSessions.cancel(player);
        helper.succeed();
    }

    private static void codec(GameTestHelper helper) {
        for (boolean value : new boolean[]{false,true}) {
            var buffer = Unpooled.buffer();
            try {
                AimPayload.CODEC.encode(buffer, new AimPayload(value));
                helper.assertValueEqual(buffer.readableBytes(), 1, "Aiming request is bounded to one byte");
                helper.assertValueEqual(AimPayload.CODEC.decode(buffer), new AimPayload(value), "Aim packet roundtrip");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    private static void deathCancels(GameTestHelper helper) {
        Player player = player(helper, "m4");
        ItemStack gun = player.getMainHandItem();
        helper.assertTrue(AimSessions.set(player, true), "Aim starts");
        player.setHealth(0);
        player.tick();
        helper.assertTrue(!AimSessions.active(player, gun), "Death ends authoritative aim");
        helper.assertTrue(!gun.getOrDefault(TGContent.AIMING.get(), false), "Death clears visible flag");
        helper.succeed();
    }
    private AimGameTests() {}
}
