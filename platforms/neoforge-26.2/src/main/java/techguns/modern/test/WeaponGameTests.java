package techguns.modern.test;

import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.Weapons;
import techguns.modern.Bullet;
import techguns.modern.RevolverItem;
import techguns.modern.TGContent;

/** Opt-in tests: activated only by the dedicated Gradle gameTestServer run. */
public final class WeaponGameTests {
    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, "techguns");

    static {
        FUNCTIONS.register("ammo_persists", () -> WeaponGameTests::ammoPersists);
        FUNCTIONS.register("reload_timing_and_consumption", () -> WeaponGameTests::reloadTiming);
        FUNCTIONS.register("reload_cancellation", () -> WeaponGameTests::reloadCancellation);
        FUNCTIONS.register("reload_same_item_swap", () -> WeaponGameTests::reloadSameItemSwap);
        FUNCTIONS.register("reload_rechecks_inventory", () -> WeaponGameTests::reloadRechecksInventory);
        FUNCTIONS.register("fire_cooldown", () -> WeaponGameTests::fireCooldown);
        FUNCTIONS.register("bullet_hits_target", () -> helper -> bulletCollision(helper, false));
        FUNCTIONS.register("wall_blocks_bullet", () -> helper -> bulletCollision(helper, true));
        FUNCTIONS.register("bullet_expires", () -> WeaponGameTests::bulletExpires);
    }

    public static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TGContent.id("weapons"));
        FUNCTIONS.getEntries().forEach(function -> event.registerTest(function.getId(),
                new FunctionGameTestInstance(function.getKey(),
                        new TestData<>(environment, TGContent.id("weapon_test"), 100, 0, true))));
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(2, 2, 2)));
        player.setNoGravity(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, TGContent.REVOLVER.toStack());
        return player;
    }

    private static void ammoPersists(GameTestHelper helper) {
        ItemStack gun = TGContent.REVOLVER.toStack();
        gun.set(TGContent.ROUNDS.get(), 3);
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var tag = ItemStack.CODEC.encodeStart(ops, gun).getOrThrow();
        ItemStack decoded = ItemStack.CODEC.parse(ops, tag).getOrThrow();
        helper.assertTrue(decoded.is(TGContent.REVOLVER.get()), "Item ID survives serialization");
        helper.assertValueEqual(RevolverItem.rounds(decoded), 3, "Saved magazine");
        helper.succeed();
    }

    private static void reloadTiming(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack ammo = TGContent.PISTOL_ROUNDS.toStack(2);
        player.getInventory().setItem(1, ammo);
        ItemStack gun = player.getMainHandItem();
        TGContent.REVOLVER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem(), "Reload started");
        for (int i = 0; i < Weapons.REVOLVER.reloadTicks() - 1; i++) player.tick();
        helper.assertValueEqual(RevolverItem.rounds(gun), 0, "Not loaded before 45 ticks");
        helper.assertValueEqual(ammo.getCount(), 2, "Ammo retained until completion");
        player.tick();
        helper.assertValueEqual(RevolverItem.rounds(gun), 6, "Loaded cylinder");
        helper.assertValueEqual(ammo.getCount(), 1, "One bundle consumed");
        helper.succeed();
    }

    private static void reloadCancellation(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack gun = player.getMainHandItem();
        ItemStack ammo = TGContent.PISTOL_ROUNDS.toStack(2);
        player.getInventory().setItem(1, ammo);
        TGContent.REVOLVER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        for (int i = 0; i < 10; i++) player.tick();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (int i = 0; i < 50; i++) player.tick();
        helper.assertTrue(!player.isUsingItem(), "Changing item cancels reload");
        helper.assertValueEqual(RevolverItem.rounds(gun), 0, "Cancelled magazine");
        helper.assertValueEqual(ammo.getCount(), 2, "Cancelled reload consumes no ammo");
        helper.succeed();
    }

    private static void reloadRechecksInventory(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack gun = player.getMainHandItem();
        player.getInventory().setItem(1, TGContent.PISTOL_ROUNDS.toStack());
        TGContent.REVOLVER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        player.getInventory().setItem(1, ItemStack.EMPTY);
        for (int i = 0; i < 45; i++) player.tick();
        helper.assertValueEqual(RevolverItem.rounds(gun), 0, "Cannot reload with removed ammo");
        helper.succeed();
    }

    private static void reloadSameItemSwap(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack original = player.getMainHandItem();
        ItemStack replacement = TGContent.REVOLVER.toStack();
        ItemStack ammo = TGContent.PISTOL_ROUNDS.toStack(2);
        player.getInventory().setItem(1, ammo);
        TGContent.REVOLVER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        for (int i = 0; i < 10; i++) player.tick();
        player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
        for (int i = 0; i < 50; i++) player.tick();
        helper.assertTrue(!player.isUsingItem(), "Swapping identical weapons cancels reload");
        helper.assertValueEqual(RevolverItem.rounds(original), 0, "Original remains empty");
        helper.assertValueEqual(RevolverItem.rounds(replacement), 0, "Replacement remains empty");
        helper.assertValueEqual(ammo.getCount(), 2, "No ammo consumed on swap");
        helper.succeed();
    }

    private static void fireCooldown(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack gun = player.getMainHandItem();
        gun.set(TGContent.ROUNDS.get(), 6);
        helper.assertTrue(RevolverItem.fire(helper.getLevel(), player, gun), "First shot allowed");
        helper.assertValueEqual(RevolverItem.rounds(gun), 5, "Shot consumes one round");
        helper.assertTrue(!RevolverItem.fire(helper.getLevel(), player, gun), "Immediate repeated shot rejected");
        ItemStack otherGun = TGContent.REVOLVER.toStack();
        otherGun.set(TGContent.ROUNDS.get(), 6);
        player.setItemInHand(InteractionHand.MAIN_HAND, otherGun);
        helper.assertTrue(!RevolverItem.fire(helper.getLevel(), player, otherGun), "Swapping guns does not bypass cooldown");
        for (int i = 0; i < 6; i++) player.getCooldowns().tick();
        helper.assertTrue(RevolverItem.fire(helper.getLevel(), player, otherGun), "Shot allowed after cooldown");
        helper.succeed();
    }

    private static void bulletCollision(GameTestHelper helper, boolean wall) {
        Player owner = player(helper);
        var target = helper.spawnWithNoFreeWill(EntityTypes.COW, new Vec3(8, 2, 2));
        target.setNoGravity(true);
        float originalHealth = target.getHealth();
        if (wall) {
            for (int y = 1; y <= 4; y++) for (int z = 1; z <= 3; z++) helper.setBlock(5, y, z, Blocks.STONE);
        }
        Bullet bullet = new Bullet(TGContent.BULLET.get(), helper.getLevel());
        bullet.setOwner(owner);
        bullet.setPos(helper.absoluteVec(new Vec3(2, 2.8, 2)));
        bullet.setDeltaMovement(2, 0, 0);
        helper.getLevel().addFreshEntity(bullet);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(bullet.isRemoved(), "Bullet removed on impact");
            if (wall) helper.assertValueEqual(target.getHealth(), originalHealth, "Wall blocks damage");
            else helper.assertValueEqual(target.getHealth(), originalHealth - 8, "Close-range damage");
            helper.succeed();
        });
    }

    private static void bulletExpires(GameTestHelper helper) {
        Bullet bullet = new Bullet(TGContent.BULLET.get(), helper.getLevel());
        bullet.setPos(helper.absoluteVec(new Vec3(5, 3, 5)));
        helper.getLevel().addFreshEntity(bullet);
        helper.runAfterDelay(42, () -> {
            helper.assertTrue(bullet.isRemoved(), "Projectile lifetime is bounded");
            helper.succeed();
        });
    }

    private WeaponGameTests() {}
}
