package techguns.modern.test;

import java.util.function.Consumer;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.WeaponDefinition;
import techguns.core.Weapons;
import techguns.modern.Bullet;
import techguns.modern.GunItem;
import techguns.modern.ReloadSessions;
import techguns.modern.TGContent;
import techguns.modern.network.GunActionPayload;
import techguns.modern.network.GunNetwork;

final class ArsenalGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        for (WeaponDefinition gun : Weapons.ALL) registry.register("arsenal_" + gun.id(), () -> h -> weaponCycle(h, gun));
        registry.register("shotgun_damage_stacks", () -> ArsenalGameTests::pelletDamage);
        registry.register("magazine_returns_remainders", () -> ArsenalGameTests::magazineRemainders);
        registry.register("shells_reload_across_stacks", () -> ArsenalGameTests::shellsAcrossStacks);
        registry.register("projectile_weapon_persists", () -> ArsenalGameTests::projectilePersists);
        registry.register("handcannon_gravity", () -> ArsenalGameTests::handcannonGravity);
        registry.register("cross_weapon_cooldown", () -> ArsenalGameTests::crossWeaponCooldown);
        registry.register("survival_handcannon_crafting", () -> ArsenalGameTests::survivalCrafting);
    }

    private static void weaponCycle(GameTestHelper helper, WeaponDefinition gun) {
        Player player = WeaponGameTests.player(helper);
        ItemStack stack = TGContent.GUNS.get(gun.id()).toStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.getInventory().setItem(1, TGContent.AMMO.get(gun.ammo().item()).toStack(gun.stats().capacity() + 1));
        helper.assertTrue(ReloadSessions.begin(player), "Reload starts: " + gun.id());
        for (int tick = 0; tick < gun.stats().reloadTicks(); tick++) player.tick();
        helper.assertValueEqual(GunItem.rounds(stack), gun.stats().capacity(), "Capacity: " + gun.id());
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var data = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        helper.assertValueEqual(GunItem.rounds(ItemStack.CODEC.parse(ops, data).getOrThrow()), gun.stats().capacity(), "Saved ammo");
        helper.assertTrue(GunNetwork.handle(player, new GunActionPayload(false)), "Shot fires: " + gun.id());
        helper.assertValueEqual(GunItem.rounds(stack), gun.stats().capacity() - 1, "Only one charge per shot");
        var bullets = helper.getLevel().getEntitiesOfClass(Bullet.class, player.getBoundingBox().inflate(3), b -> b.getOwner() == player);
        helper.assertValueEqual(bullets.size(), gun.projectileCount(), "Projectile count: " + gun.id());
        for (Bullet bullet : bullets) helper.assertValueEqual(bullet.weapon().id(), gun.id(), "Correct projectile parameters");
        helper.assertTrue(!GunNetwork.handle(player, new GunActionPayload(false)), "Same-tick spam rejected");
        helper.succeed();
    }

    private static void pelletDamage(GameTestHelper helper) {
        var target = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(8, 2, 2));
        target.setNoGravity(true);
        float before = target.getHealth();
        Player owner = WeaponGameTests.player(helper);
        for (int n = 0; n < 8; n++) {
            Bullet bullet = new Bullet(TGContent.BULLET.get(), helper.getLevel());
            bullet.configure(Weapons.definition("sawedoff"));
            bullet.setOwner(owner);
            // Keep the impact within drop_start: the eight independent rounds must all count.
            bullet.setPos(target.position().add(-0.8, 1, 0));
            bullet.setDeltaMovement(1, 0, 0);
            helper.getLevel().addFreshEntity(bullet);
        }
        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(target.getHealth(), before - 32, "All eight pellets contribute, bypassing vanilla hurt cooldown");
            helper.succeed();
        });
    }

    private static void magazineRemainders(GameTestHelper helper) {
        Player player = WeaponGameTests.player(helper);
        ItemStack gun = TGContent.GUNS.get("thompson").toStack();
        gun.set(TGContent.ROUNDS.get(), 15);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.getInventory().setItem(1, TGContent.AMMO.get("smgmagazine").toStack(2));
        helper.assertTrue(ReloadSessions.begin(player), "Magazine reload starts");
        for (int i = 0; i < 40; i++) player.tick();
        helper.assertValueEqual(GunItem.rounds(gun), 20, "Full magazine loaded");
        helper.assertValueEqual(count(player, "smgmagazine"), 1, "One magazine consumed");
        helper.assertValueEqual(count(player, "smgmagazineempty"), 1, "Empty magazine returned");
        helper.assertValueEqual(count(player, "pistolrounds"), 1, "Whole unspent bundle returned");
        helper.succeed();
    }

    private static int count(Player player, String id) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(TGContent.AMMO.get(id).get())) count += stack.getCount();
        }
        return count;
    }

    private static void shellsAcrossStacks(GameTestHelper helper) {
        Player player = WeaponGameTests.player(helper);
        ItemStack gun = TGContent.GUNS.get("combatshotgun").toStack();
        gun.set(TGContent.ROUNDS.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.getInventory().setItem(1, TGContent.AMMO.get("shotgunrounds").toStack());
        player.getInventory().setItem(2, TGContent.AMMO.get("shotgunrounds").toStack());
        helper.assertTrue(ReloadSessions.begin(player), "Partial shell reload starts");
        for (int i = 0; i < 50; i++) player.tick();
        helper.assertValueEqual(GunItem.rounds(gun), 5, "Partial reload keeps existing shells");
        helper.assertValueEqual(count(player, "shotgunrounds"), 0, "Both ammo stacks consumed");
        helper.succeed();
    }

    private static void projectilePersists(GameTestHelper helper) {
        Bullet original = new Bullet(TGContent.BULLET.get(), helper.getLevel());
        original.configure(Weapons.definition("handcannon"));
        original.setPos(helper.absoluteVec(new Vec3(2, 4, 2)));
        original.setDeltaMovement(0.5, 0, 0);
        original.tick();
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        helper.assertTrue(original.save(saved), "Projectile serializes");
        Bullet restored = new Bullet(TGContent.BULLET.get(), helper.getLevel());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));
        helper.assertValueEqual(restored.weapon().id(), "handcannon", "Projectile keeps original weapon");
        helper.assertValueEqual(restored.getDeltaMovement(), original.getDeltaMovement(), "Projectile velocity survives save");
        helper.succeed();
    }

    private static void handcannonGravity(GameTestHelper helper) {
        Bullet bullet = new Bullet(TGContent.BULLET.get(), helper.getLevel());
        bullet.configure(Weapons.definition("handcannon"));
        Vec3 start = helper.absoluteVec(new Vec3(1, 4, 1));
        bullet.setPos(start);
        bullet.setDeltaMovement(0.5, 0, 0);
        for (int tick = 0; tick < 10; tick++) bullet.tick();
        helper.assertTrue(Math.abs(bullet.getY() - (start.y - .015 * 45)) < 0.00001, "Legacy gravity accumulates per tick");
        helper.succeed();
    }

    private static void crossWeaponCooldown(GameTestHelper helper) {
        Player player = WeaponGameTests.player(helper);
        player.getMainHandItem().set(TGContent.ROUNDS.get(), 6);
        helper.assertTrue(GunNetwork.handle(player, new GunActionPayload(false)), "Revolver fires");
        ItemStack thompson = TGContent.GUNS.get("thompson").toStack();
        thompson.set(TGContent.ROUNDS.get(), 20);
        player.setItemInHand(InteractionHand.MAIN_HAND, thompson);
        helper.assertTrue(!GunNetwork.handle(player, new GunActionPayload(false)), "Switching weapon types cannot evade rate limit");
        for (int tick = 0; tick < 6; tick++) player.getCooldowns().tick();
        helper.assertTrue(GunNetwork.handle(player, new GunActionPayload(false)), "New weapon fires after previous weapon delay");
        helper.succeed();
    }

    private static ItemStack craft(GameTestHelper helper, int width, int height, ItemStack... items) {
        CraftingInput input = CraftingInput.of(width, height, List.of(items));
        var recipe = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new AssertionError("Expected survival recipe to exist"));
        return recipe.value().assemble(input);
    }

    private static void survivalCrafting(GameTestHelper helper) {
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack log = new ItemStack(Items.OAK_LOG);
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        ItemStack barrel = craft(helper, 3, 3, stone, stone, stone, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, stone, stone, stone);
        helper.assertTrue(barrel.is(TGContent.MATERIALS.get("stonebarrel").get()), "Stone barrel recipe");
        ItemStack stock = craft(helper, 2, 2, log, log, ItemStack.EMPTY, log);
        helper.assertTrue(stock.is(TGContent.MATERIALS.get("woodstock").get()), "Wooden stock recipe");
        ItemStack gun = craft(helper, 3, 1, barrel, new ItemStack(Items.FLINT_AND_STEEL), stock);
        helper.assertTrue(gun.is(TGContent.GUNS.get("handcannon").get()), "Craft hand cannon from its actual parts");
        helper.assertValueEqual(GunItem.rounds(gun), 0, "Crafted gun is unloaded like the legacy metadata recipe");
        ItemStack ammo = craft(helper, 2, 2, cobble, cobble, cobble, new ItemStack(Items.GUNPOWDER));
        helper.assertTrue(ammo.is(TGContent.AMMO.get("stonebullets").get()), "Stone ammunition recipe");
        helper.assertValueEqual(ammo.getCount(), 16, "Legacy crafting yield");
        Player player = WeaponGameTests.player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.getInventory().setItem(1, ammo);
        helper.assertTrue(ReloadSessions.begin(player), "Crafted ammunition can load crafted gun");
        for (int tick = 0; tick < 30; tick++) player.tick();
        helper.assertTrue(GunNetwork.handle(player, new GunActionPayload(false)), "Crafted gun can fire in survival");
        helper.assertValueEqual(ammo.getCount(), 15, "Survival reload consumed exactly one stone bullet");
        helper.succeed();
    }

    private ArsenalGameTests() {}
}
