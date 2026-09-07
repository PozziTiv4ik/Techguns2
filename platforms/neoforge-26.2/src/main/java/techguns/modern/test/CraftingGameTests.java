package techguns.modern.test;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.Weapons;
import techguns.core.CraftingContent;
import techguns.modern.GunItem;
import techguns.modern.ReloadSessions;
import techguns.modern.TGContent;
import techguns.modern.crafting.TagFallbackIngredient;
import techguns.modern.network.GunActionPayload;
import techguns.modern.network.GunNetwork;

final class CraftingGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        registry.register("craft_all_generated_recipes_loaded", () -> CraftingGameTests::allRecipesLoaded);
        registry.register("craft_survival_revolver", () -> CraftingGameTests::revolver);
        registry.register("craft_thompson_magazine_state", () -> CraftingGameTests::thompson);
        registry.register("craft_rifle_ammo_packing", () -> CraftingGameTests::riflePacking);
        registry.register("craft_metal_nugget_packing", () -> CraftingGameTests::metalPacking);
        registry.register("craft_tag_fallback", () -> CraftingGameTests::fallback);
        registry.register("craft_upgrade_codec", () -> CraftingGameTests::upgradeCodec);
        for (int rounds : new int[]{0, 3, 6}) registry.register("craft_gilding_" + rounds, () -> h -> gilding(h, rounds));
        for (String gun : new String[]{"thompson", "pistol", "ak47", "lmg", "as50"})
            registry.register("craft_magazine_" + gun, () -> h -> magazine(h, gun));
    }

    private static ItemStack material(String id) { return TGContent.MATERIALS.get(id).toStack(); }
    private static ItemStack ammo(String id) { return TGContent.AMMO.get(id).toStack(); }
    private static void allRecipesLoaded(GameTestHelper h) {
        var manager = h.getLevel().getServer().getRecipeManager();
        for (String id : CraftingContent.RECIPE_IDS) {
            var key = ResourceKey.create(Registries.RECIPE, TGContent.id(id));
            h.assertTrue(manager.byKey(key).isPresent(), "Generated recipe must load through actual Minecraft codecs: " + id);
        }
        h.succeed();
    }
    private static CraftingRecipe recipe(GameTestHelper helper, CraftingInput input) {
        return helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new AssertionError("No real crafting recipe matches the supplied ingredients")).value();
    }
    static ItemStack craft(GameTestHelper helper, int width, int height, ItemStack... grid) {
        CraftingInput input = CraftingInput.of(width, height, List.of(grid));
        return recipe(helper, input).assemble(input);
    }
    private static void assertItem(GameTestHelper h, ItemStack stack, ItemStack expected, int count) {
        h.assertTrue(stack.is(expected.getItem()), "Crafted item type");
        h.assertValueEqual(stack.getCount(), count, "Original recipe yield");
    }

    private static void revolver(GameTestHelper h) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT), empty = ItemStack.EMPTY;
        ItemStack mechanical = craft(h, 3, 3, empty, iron, empty, iron, new ItemStack(Items.FLINT), iron, empty, iron, empty);
        assertItem(h, mechanical, material("mechanicalpartsiron"), 1);
        ItemStack barrel = craft(h, 3, 3, iron, iron, iron, empty, empty, empty, iron, iron, iron);
        assertItem(h, barrel, material("ironbarrel"), 1);
        ItemStack log = new ItemStack(Items.BIRCH_LOG);
        ItemStack stock = craft(h, 2, 2, log, log, empty, log);
        ItemStack gun = craft(h, 3, 2, barrel, mechanical, new ItemStack(Items.FLINT_AND_STEEL), empty, stock, empty);
        assertItem(h, gun, TGContent.REVOLVER.toStack(), 1);
        h.assertValueEqual(GunItem.rounds(gun), 0, "Revolver is crafted empty");
        ItemStack copper = new ItemStack(Items.COPPER_NUGGET);
        ItemStack bullets = craft(h, 3, 3, copper, material("ingotlead"), copper, copper, new ItemStack(Items.GUNPOWDER), copper, copper, copper, copper);
        assertItem(h, bullets, ammo("pistolrounds"), 8);
        var player = WeaponGameTests.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.getInventory().setItem(1, bullets);
        h.assertTrue(ReloadSessions.begin(player), "Crafted ammo reloads a crafted revolver");
        for (int tick = 0; tick < 45; tick++) player.tick();
        h.assertTrue(GunNetwork.handle(player, new GunActionPayload(false)), "Crafted revolver fires");
        h.assertValueEqual(bullets.getCount(), 7, "Server reload spends exactly one crafted bundle");
        h.succeed();
    }

    private static void thompson(GameTestHelper h) {
        ItemStack empty = ItemStack.EMPTY;
        for (boolean loaded : new boolean[]{false, true}) {
            ItemStack gun = craft(h, 3, 2, material("ironbarrel"), material("ironreceiver"), material("woodstock"),
                    empty, ammo(loaded ? "smgmagazine" : "smgmagazineempty"), empty);
            assertItem(h, gun, TGContent.GUNS.get("thompson").toStack(), 1);
            h.assertValueEqual(GunItem.rounds(gun), loaded ? 20 : 0, "The consumed magazine determines initial ammo");
        }
        h.succeed();
    }

    private static void magazine(GameTestHelper h, String weapon) {
        var definition = Weapons.definition(weapon);
        int count = switch (weapon) { case "thompson", "as50" -> 2; case "pistol", "ak47" -> 3; case "lmg" -> 8; default -> throw new AssertionError(); };
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        if (weapon.equals("lmg")) {
            for (int i = 0; i < 9; i++) grid.set(i, ammo(i == 4 ? definition.ammo().emptyItem() : definition.ammo().looseItem()));
        } else {
            grid.set(0, ammo(definition.ammo().emptyItem()));
            for (int i = 1; i <= count; i++) grid.set(i, ammo(definition.ammo().looseItem()));
        }
        ItemStack filled = craft(h, 3, 3, grid.toArray(ItemStack[]::new));
        assertItem(h, filled, ammo(definition.ammo().item()), 1);
        var player = WeaponGameTests.player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, TGContent.GUNS.get(weapon).toStack());
        player.getInventory().setItem(1, filled);
        h.assertTrue(ReloadSessions.begin(player), "Crafted magazine is usable");
        for (int tick = 0; tick < definition.stats().reloadTicks(); tick++) player.tick();
        h.assertValueEqual(GunItem.rounds(player.getMainHandItem()), definition.stats().capacity(), "Full original magazine capacity");
        h.assertTrue(filled.isEmpty(), "Filled magazine was consumed");
        h.assertValueEqual(player.getInventory().countItem(TGContent.AMMO.get(definition.ammo().emptyItem()).get()), 1, "Empty magazine is returned once");
        h.succeed();
    }

    private static void riflePacking(GameTestHelper h) {
        ItemStack round = ammo("riflerounds");
        ItemStack bundle = craft(h, 2, 2, round, round, round, round);
        assertItem(h, bundle, ammo("rifleroundsstack"), 1);
        ItemStack unpacked = craft(h, 1, 1, bundle);
        assertItem(h, unpacked, round, 4);
        ItemStack magazine = craft(h, 3, 1, ammo("lmgmagazineempty"), bundle, bundle);
        assertItem(h, magazine, ammo("lmgmagazine"), 1);
        h.succeed();
    }

    private static void metalPacking(GameTestHelper h) {
        for (String metal : new String[]{"lead", "steel"}) {
            ItemStack nuggets = craft(h, 1, 1, material("ingot" + metal));
            assertItem(h, nuggets, material("nugget" + metal), 9);
            ItemStack nugget = nuggets.copyWithCount(1);
            assertItem(h, craft(h, 3, 3, nugget,nugget,nugget,nugget,nugget,nugget,nugget,nugget,nugget), material("ingot"+metal), 1);
        }
        h.succeed();
    }

    private static CraftingInput goldenGrid(ItemStack gun) {
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        return CraftingInput.of(3, 3, List.of(gold,gold,gold,gold,gun,gold,gold,gold,gold));
    }
    private static void gilding(GameTestHelper h, int rounds) {
        ItemStack source = TGContent.REVOLVER.toStack();
        source.set(TGContent.ROUNDS.get(), rounds);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Original serial 27"));
        source.set(TGContent.AIMING.get(), true);
        source.set(TGContent.RELOAD_TICKS.get(), 23);
        CraftingInput input = goldenGrid(source);
        ItemStack output = recipe(h, input).assemble(input);
        assertItem(h, output, TGContent.GUNS.get("goldenrevolver").toStack(), 1);
        h.assertValueEqual(GunItem.rounds(output), rounds, "Gilding cannot create or erase ammunition");
        h.assertTrue(output.getHoverName().equals(source.getHoverName()), "Custom item name survives upgrade");
        h.assertTrue(!output.has(TGContent.AIMING.get()) && !output.has(TGContent.RELOAD_TICKS.get()), "Player actions never transfer through crafting");
        h.assertValueEqual(GunItem.rounds(source), rounds, "Preview assembly does not mutate the input gun");
        h.assertTrue(source.has(TGContent.RELOAD_TICKS.get()), "Input is a separate component map");
        h.succeed();
    }
    private static void upgradeCodec(GameTestHelper h) {
        ItemStack source = TGContent.REVOLVER.toStack();
        source.set(TGContent.ROUNDS.get(), 2);
        CraftingInput input = goldenGrid(source);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            Recipe.STREAM_CODEC.encode(buffer, recipe(h, input));
            var decoded = (CraftingRecipe) Recipe.STREAM_CODEC.decode(buffer);
            h.assertTrue(decoded.matches(input, h.getLevel()), "Synchronized upgrade recipe matches");
            h.assertValueEqual(GunItem.rounds(decoded.assemble(input)), 2, "Custom assembly survives recipe network serialization");
            h.assertValueEqual(buffer.readableBytes(), 0, "No unread recipe payload bytes");
        } finally { buffer.release(); }
        h.succeed();
    }

    private static void fallback(GameTestHelper h) {
        var iron = TagKey.create(Registries.ITEM, Identifier.parse("c:ingots/iron"));
        var glass = TagKey.create(Registries.ITEM, Identifier.parse("c:glass_blocks"));
        var absent = TagKey.create(Registries.ITEM, TGContent.id("absent_test_material"));
        Ingredient preferred = new Ingredient(new TagFallbackIngredient(iron, glass));
        h.assertTrue(preferred.test(new ItemStack(Items.IRON_INGOT)) && !preferred.test(new ItemStack(Items.GLASS)), "Populated preferred tag excludes cheaper fallback");
        Ingredient fallback = new Ingredient(new TagFallbackIngredient(absent, glass));
        var ops = h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        Ingredient decoded = Ingredient.CODEC.parse(ops, Ingredient.CODEC.encodeStart(ops, fallback).getOrThrow()).getOrThrow();
        h.assertTrue(decoded.test(new ItemStack(Items.GLASS)), "An absent integration falls back after codec round trip");
        h.succeed();
    }
    private CraftingGameTests() {}
}
