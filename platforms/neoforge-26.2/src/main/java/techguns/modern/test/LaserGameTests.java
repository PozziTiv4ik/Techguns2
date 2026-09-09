package techguns.modern.test;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.Weapons;
import techguns.modern.AimSessions;
import techguns.modern.GunItem;
import techguns.modern.LaserBeam;
import techguns.modern.ReloadSessions;
import techguns.modern.TGContent;
import techguns.modern.network.GunActionPayload;
import techguns.modern.network.GunNetwork;

final class LaserGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        if (Boolean.getBoolean("techguns.chemistryTest"))
            registry.register("chem_optional_laser_electrum", () -> LaserGameTests::electrum);
        for (String id : new String[]{"lasergun", "laserpistol"}) {
            registry.register("laser_damage_" + id, () -> h -> damage(h, id));
            registry.register("laser_reload_" + id, () -> h -> cellReload(h, id));
            for (boolean charged : new boolean[]{false, true})
                registry.register("laser_craft_" + id + (charged ? "_charged" : "_empty"), () -> h -> gunCraft(h, id, charged));
        }
        registry.register("laser_stone_wall", () -> h -> barrier(h, Blocks.STONE, false));
        registry.register("laser_glass_wall", () -> h -> barrier(h, Blocks.GLASS, false));
        registry.register("laser_water_passes", () -> h -> barrier(h, Blocks.WATER, true));
        registry.register("laser_nearest_target", () -> LaserGameTests::nearest);
        registry.register("laser_trace_range", () -> LaserGameTests::range);
        registry.register("laser_miss_geometry", () -> LaserGameTests::miss);
        registry.register("laser_energy_armor", () -> LaserGameTests::armor);
        registry.register("laser_witch_magic_resistance", () -> LaserGameTests::witch);
        registry.register("laser_cooldown_and_knockback", () -> LaserGameTests::repeated);
        registry.register("laser_saved_visual_no_second_hit", () -> LaserGameTests::save);
        registry.register("laser_invalid_save_rejected", () -> LaserGameTests::invalidSave);
        registry.register("laser_entity_data_codec", () -> LaserGameTests::synchronization);
        registry.register("laser_cancelled_spawn", () -> LaserGameTests::cancelledSpawn);
        registry.register("laser_cancelled_impact", () -> LaserGameTests::cancelledImpact);
        registry.register("laser_pistol_redstone_cycle", () -> LaserGameTests::redstoneCycle);
        registry.register("laser_barrel_gold_fallback", () -> LaserGameTests::barrel);
        registry.register("laser_server_aim", () -> LaserGameTests::aim);
    }

    private static LivingEntity target(GameTestHelper h, Vec3 position) {
        var target = h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, position);
        target.setNoGravity(true);
        return target;
    }
    private static Player player(GameTestHelper h, String id) {
        var player = WeaponGameTests.player(h);
        var gun = TGContent.GUNS.get(id).toStack();
        gun.set(TGContent.ROUNDS.get(), Weapons.definition(id).stats().capacity());
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        return player;
    }
    private static LaserBeam beam(GameTestHelper h, String id, Vec3 from, Vec3 movement) {
        var beam = new LaserBeam(TGContent.LASER_BEAM.get(), h.getLevel());
        beam.configure(Weapons.definition(id));
        beam.setOwner(WeaponGameTests.player(h));
        beam.setPos(h.absoluteVec(from));
        beam.setDeltaMovement(movement);
        h.assertTrue(h.getLevel().addFreshEntity(beam), "Beam enters the server world");
        return beam;
    }
    private static void near(GameTestHelper h, double value, double expected, String message) {
        h.assertTrue(Math.abs(value - expected) < .0001, message + ": " + value + " != " + expected);
    }
    private static void damage(GameTestHelper h, String id) {
        var target = target(h, new Vec3(8, 2, 2));
        var beam = beam(h, id, new Vec3(2, 3, 2), new Vec3(12, 0, 0));
        float initial = target.getHealth(), damage = Weapons.definition(id).stats().damage();
        beam.trace();
        near(h, target.getHealth(), initial - damage, "Damage is applied in the firing tick");
        beam.trace();
        for (int n = 0; n < 6; n++) beam.tick();
        near(h, target.getHealth(), initial - damage, "Visible beam never hits again");
        h.assertTrue(!beam.isRemoved(), "Beam remains visible through six ticks");
        beam.tick();
        h.assertTrue(beam.isRemoved(), "Seven-tick lifetime");
        h.succeed();
    }
    private static void barrier(GameTestHelper h, Block block, boolean passes) {
        var target = target(h, new Vec3(8, 2, 2));
        var wall = new BlockPos(5, 3, 2);
        h.setBlock(wall, block);
        var beam = beam(h, "lasergun", new Vec3(2, 3.5, 2.5), new Vec3(12, 0, 0));
        float initial = target.getHealth();
        beam.trace();
        near(h, target.getHealth(), initial - (passes ? 12 : 0), "Collision uses solid shapes and ignores fluids");
        if (!passes) near(h, beam.endOffset().x, 3, "Beam stops at the front face of the wall");
        h.assertTrue(h.getBlockState(wall).is(block), "Laser does not destroy its barrier");
        h.succeed();
    }
    private static void nearest(GameTestHelper h) {
        var first = target(h, new Vec3(6, 2, 2));
        var second = target(h, new Vec3(10, 2, 2));
        var beam = beam(h, "lasergun", new Vec3(2, 3, 2), new Vec3(12, 0, 0));
        float firstHealth = first.getHealth(), secondHealth = second.getHealth();
        beam.trace();
        near(h, first.getHealth(), firstHealth - 12, "Nearest target is hit");
        near(h, second.getHealth(), secondHealth, "Beam does not penetrate a second entity");
        near(h, beam.endOffset().x, first.getBoundingBox().minX - .3 - beam.getX(), "Legacy 0.3-block entity hit margin");
        h.succeed();
    }
    private static void range(GameTestHelper h) {
        // Start above the framework's barrier roof as well as the neighboring test structures.
        var far = target(h, new Vec3(3, 130, 3));
        var beyond = target(h, new Vec3(3, 175, 3));
        var beam = beam(h, "lasergun", new Vec3(3, 20, 3), new Vec3(0, 150, 0));
        float health = far.getHealth(), beyondHealth = beyond.getHealth();
        beam.trace();
        near(h, far.getHealth(), health - 12, "Actual legacy trace reaches beyond nominal 100 blocks without damage drop");
        near(h, beyond.getHealth(), beyondHealth, "Nothing beyond the trace is hit");
        h.assertTrue(beam.endOffset().y > 100, "Visible beam reaches a far impact");
        far.discard(); beyond.discard();
        h.succeed();
    }
    private static void miss(GameTestHelper h) {
        var beam = beam(h, "laserpistol", new Vec3(2, 20, 2), new Vec3(0, 150, 0));
        Vec3 from = beam.position();
        beam.trace();
        near(h, beam.endOffset().length(), 100, "Legacy miss displays nominal beam length");
        h.assertValueEqual(beam.getDeltaMovement(), Vec3.ZERO, "Visual has no moving projectile velocity");
        h.assertTrue(beam.beamBounds().contains(from.add(0, 99, 0)), "Culling bounds cover the far end");
        beam.tick();
        h.assertValueEqual(beam.position(), from, "Stationary beam origin");
        h.succeed();
    }
    private static void armor(GameTestHelper h) {
        var target = target(h, new Vec3(8, 2, 2));
        target.getAttribute(Attributes.ARMOR).setBaseValue(10);
        target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        float initial = target.getHealth();
        beam(h, "lasergun", new Vec3(2, 3, 2), new Vec3(12, 0, 0)).trace();
        near(h, target.getHealth(), initial - 9.6, "Ten default armor points give five protection against ENERGY");
        h.succeed();
    }
    private static void repeated(GameTestHelper h) {
        var target = target(h, new Vec3(8, 2, 2));
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        Vec3 velocity = target.getDeltaMovement();
        float initial = target.getHealth();
        beam(h, "lasergun", new Vec3(2, 3, 2), new Vec3(12, 0, 0)).trace();
        beam(h, "laserpistol", new Vec3(2, 3, 2), new Vec3(12, 0, 0)).trace();
        near(h, target.getHealth(), initial - 21, "Independent laser hits bypass vanilla hurt cooldown");
        h.assertValueEqual(target.getDeltaMovement(), velocity, "Energy beam adds no knockback");
        h.succeed();
    }
    private static void witch(GameTestHelper h) {
        var witch = h.spawnWithNoFreeWill(EntityTypes.WITCH, new Vec3(8, 2, 2));
        witch.setNoGravity(true);
        float initial = witch.getHealth();
        var beam = beam(h, "laserpistol", new Vec3(2, 3, 2), new Vec3(12, 0, 0));
        var holder = h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(LaserBeam.DAMAGE_TYPE);
        h.assertTrue(holder.is(net.neoforged.neoforge.common.Tags.DamageTypes.IS_MAGIC), "Legacy ENERGY sets magic behavior for vanilla integrations");
        beam.trace();
        near(h, witch.getHealth(), initial - 1.35, "Witch retains 85 percent resistance to magic laser damage");
        h.succeed();
    }
    private static net.minecraft.nbt.CompoundTag saved(GameTestHelper h, LaserBeam beam) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        beam.saveWithoutId(output);
        return output.buildResult();
    }
    private static LaserBeam load(GameTestHelper h, net.minecraft.nbt.CompoundTag tag) {
        var beam = new LaserBeam(TGContent.LASER_BEAM.get(), h.getLevel());
        beam.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), tag));
        return beam;
    }
    private static void save(GameTestHelper h) {
        var target = target(h, new Vec3(8, 2, 2));
        var original = beam(h, "laserpistol", new Vec3(2, 3, 2), new Vec3(12, 0, 0));
        original.trace(); original.tick(); original.tick();
        float afterHit = target.getHealth();
        var restored = load(h, saved(h, original));
        h.assertValueEqual(restored.weapon().id(), "laserpistol", "Saved source weapon");
        h.assertValueEqual(restored.age(), 2, "Saved remaining lifetime");
        h.assertValueEqual(restored.endOffset(), original.endOffset(), "Saved endpoint");
        h.assertTrue(!restored.isRemoved(), "Valid visual survives load");
        original.discard();
        h.assertTrue(h.getLevel().addFreshEntity(restored), "Saved beam can rejoin the level");
        restored.trace();
        for (int n = 0; n < 4; n++) restored.tick();
        h.assertTrue(!restored.isRemoved(), "Restored beam keeps only its remaining ticks");
        restored.tick();
        h.assertTrue(restored.isRemoved(), "Restored beam expires on original deadline");
        near(h, target.getHealth(), afterHit, "Reloading never repeats damage");
        h.succeed();
    }
    private static void invalidSave(GameTestHelper h) {
        var beam = beam(h, "lasergun", new Vec3(2, 20, 2), new Vec3(0, 150, 0)); beam.trace();
        var valid = saved(h, beam);
        for (String id : new String[]{"unknown_weapon", "revolver"}) {
            var tag = valid.copy(); tag.putString("weapon", id);
            h.assertTrue(load(h, tag).isRemoved(), "Invalid projectile family rejected");
        }
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 1000000}) {
            var tag = valid.copy(); tag.putDouble("end_x", value);
            h.assertTrue(load(h, tag).isRemoved(), "Unsafe visual coordinate rejected");
        }
        var old = valid.copy(); old.putInt("age", 7);
        h.assertTrue(load(h, old).isRemoved(), "Expired visual rejected");
        var pending = valid.copy(); pending.putBoolean("traced", false);
        h.assertTrue(load(h, pending).isRemoved(), "Uncommitted save cannot fire after loading");
        h.succeed();
    }
    private static void synchronization(GameTestHelper h) {
        var beam = beam(h, "lasergun", new Vec3(3, 20, 3), new Vec3(0, 150, 0));
        beam.trace(); beam.tick(); beam.tick();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            var packet = new ClientboundSetEntityDataPacket(beam.getId(), beam.getEntityData().getNonDefaultValues());
            ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer, packet);
            var decoded = ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);
            var clientCopy = new LaserBeam(TGContent.LASER_BEAM.get(), h.getLevel());
            clientCopy.getEntityData().assignValues(decoded.packedItems());
            h.assertValueEqual(clientCopy.endOffset(), beam.endOffset(), "Endpoint survives actual vanilla entity-data codec");
            h.assertValueEqual(clientCopy.age(), beam.age(), "Animation age synchronized for tracking clients");
            h.assertValueEqual(buffer.readableBytes(), 0, "Packet completely consumed");
        } finally { buffer.release(); }
        h.succeed();
    }
    private static void cancelledSpawn(GameTestHelper h) {
        var player = player(h, "lasergun"); player.setYRot(-90);
        var target = target(h, new Vec3(8, 2, 2)); float health = target.getHealth();
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof LaserBeam beam && beam.getOwner() == player) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            h.assertTrue(!GunNetwork.handle(player, new GunActionPayload(false)), "Cancelled spawn rejects firing");
            h.assertValueEqual(GunItem.rounds(player.getMainHandItem()), 45, "Cancelled spawn costs no ammunition");
            h.assertTrue(!player.getCooldowns().isOnCooldown(player.getMainHandItem()), "No cooldown for rejected shot");
            near(h, target.getHealth(), health, "No damage before spawn acceptance");
        } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        h.succeed();
    }
    private static void cancelledImpact(GameTestHelper h) {
        var target = target(h, new Vec3(8, 2, 2)); float health = target.getHealth();
        var beam = beam(h, "lasergun", new Vec3(2, 3, 2), new Vec3(12, 0, 0));
        Consumer<ProjectileImpactEvent> cancel = event -> { if (event.getProjectile() == beam) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(cancel);
        try { beam.trace(); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); }
        beam.trace(); beam.tick();
        near(h, target.getHealth(), health, "Cancelled hit is neither applied nor retried after listener removal");
        h.succeed();
    }
    private static int count(Player player, String id) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.is(TGContent.AMMO.get(id).get())) total += stack.getCount();
        }
        return total;
    }
    private static void cellReload(GameTestHelper h, String id) {
        var player = player(h, id); var gun = Weapons.definition(id);
        player.getMainHandItem().set(TGContent.ROUNDS.get(), gun.stats().capacity() - 1);
        player.getInventory().setItem(1, TGContent.AMMO.get(gun.ammo().item()).toStack(2));
        h.assertTrue(ReloadSessions.begin(player), "Partial cell replacement starts");
        for (int n = 0; n < gun.stats().reloadTicks() - 1; n++) ReloadSessions.tick(player);
        h.assertValueEqual(count(player, gun.ammo().item()), 2, "No cell consumed before reload deadline");
        ReloadSessions.tick(player);
        h.assertValueEqual(GunItem.rounds(player.getMainHandItem()), gun.stats().capacity(), "New cell fills the weapon");
        h.assertValueEqual(count(player, gun.ammo().item()), 1, "Exactly one charged cell consumed");
        h.assertValueEqual(count(player, gun.ammo().emptyItem()), 1, "Exactly one empty cell returned");
        h.assertTrue(!ReloadSessions.begin(player), "Full cell cannot be reloaded to generate containers");
        h.succeed();
    }
    private static ItemStack item(String id) {
        return TGContent.MATERIALS.containsKey(id) ? TGContent.MATERIALS.get(id).toStack() : TGContent.AMMO.get(id).toStack();
    }
    private static ItemStack craft(GameTestHelper h, int width, int height, ItemStack... stacks) {
        var input = CraftingInput.of(width, height, List.of(stacks));
        return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel())
                .orElseThrow(() -> new AssertionError("Expected original workbench recipe")).value().assemble(input);
    }
    private static void gunCraft(GameTestHelper h, String id, boolean charged) {
        var definition = Weapons.definition(id);
        var cell = item(charged ? definition.ammo().item() : definition.ammo().emptyItem());
        ItemStack result;
        if (id.equals("lasergun")) {
            result = craft(h, 3, 3, ItemStack.EMPTY, new ItemStack(Items.GLASS_PANE), new ItemStack(Items.REDSTONE),
                    item("laserbarrel"), item("obsidiansteelreceiver"), item("plasticstock"),
                    ItemStack.EMPTY, cell, item("mechanicalpartscarbon"));
        } else {
            var plate = item("plateobsidiansteel"); var steel = item("nuggetsteel");
            result = craft(h, 3, 3, plate, item("laserbarrel"), plate, steel, item("circuitboardelite"), cell,
                    steel, steel, item("plasticsheet"));
        }
        h.assertTrue(result.is(TGContent.GUNS.get(id).get()), "Original gun recipe is usable");
        h.assertValueEqual(GunItem.rounds(result), charged ? definition.stats().capacity() : 0, "Cell state sets initial ammunition");
        h.succeed();
    }
    private static void redstoneCycle(GameTestHelper h) {
        var copper = item("nuggetcopper"); var redstone = new ItemStack(Items.REDSTONE);
        var batteries = craft(h, 3, 3, copper, item("copperwire"), copper, copper, redstone, copper, copper, redstone, copper);
        h.assertTrue(batteries.is(TGContent.AMMO.get("redstone_battery").get()), "Copper/redstone battery recipe");
        h.assertValueEqual(batteries.getCount(), 2, "Original battery yield");
        var player = player(h, "laserpistol");
        player.getMainHandItem().set(TGContent.ROUNDS.get(), 0);
        player.getInventory().setItem(1, batteries);
        h.assertTrue(ReloadSessions.begin(player), "Crafted battery can load pistol");
        for (int n = 0; n < 40; n++) ReloadSessions.tick(player);
        h.assertTrue(GunNetwork.handle(player, new GunActionPayload(false)), "Pistol fires using crafted battery");
        h.assertValueEqual(GunItem.rounds(player.getMainHandItem()), 19, "Pistol has twenty shots per battery");
        h.assertValueEqual(count(player, "redstone_battery_empty"), 1, "Spent container returned");
        ItemStack empty = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            if (player.getInventory().getItem(slot).is(TGContent.AMMO.get("redstone_battery_empty").get())) empty = player.getInventory().removeItem(slot, 1);
        var recharged = craft(h, 2, 1, empty, redstone);
        h.assertTrue(recharged.is(TGContent.AMMO.get("redstone_battery").get()), "Recovered container recharges with redstone");
        h.assertValueEqual(recharged.getCount(), 1, "Recharging does not duplicate containers");
        h.succeed();
    }
    private static void barrel(GameTestHelper h) {
        var gold = new ItemStack(Items.GOLD_INGOT); var glass = new ItemStack(Items.GLASS);
        var result = craft(h, 3, 3, gold, gold, gold, glass, glass, item("laserfocus"), gold, gold, gold);
        h.assertTrue(result.is(TGContent.MATERIALS.get("laserbarrel").get()), "Six gold ingots and ordinary glass work without optional materials");
        h.succeed();
    }
    private static void electrum(GameTestHelper h) {
        // Dedicated conditional pack uses emeralds as a stand-in for another mod's electrum.
        var electrum = new ItemStack(Items.EMERALD); var glass = new ItemStack(Items.GLASS);
        var input = CraftingInput.of(3, 3, List.of(electrum, electrum, electrum, glass, glass, item("laserfocus"), electrum, electrum, electrum));
        var manager = h.getLevel().getServer().getRecipeManager();
        var recipe = manager.getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow().value();
        h.assertTrue(recipe.assemble(input).is(TGContent.MATERIALS.get("laserbarrel").get()), "External electrum is used for laser barrel");
        var gold = new ItemStack(Items.GOLD_INGOT);
        var cheaper = CraftingInput.of(3, 3, List.of(gold, gold, gold, glass, glass, item("laserfocus"), gold, gold, gold));
        h.assertTrue(!recipe.matches(cheaper, h.getLevel()), "Gold fallback is disabled when electrum exists");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            net.minecraft.world.item.crafting.Recipe.STREAM_CODEC.encode(buffer, recipe);
            var decoded = (net.minecraft.world.item.crafting.CraftingRecipe) net.minecraft.world.item.crafting.Recipe.STREAM_CODEC.decode(buffer);
            h.assertTrue(decoded.matches(input, h.getLevel()) && !decoded.matches(cheaper, h.getLevel()), "Material preference survives recipe network codec");
            h.assertValueEqual(buffer.readableBytes(), 0, "Complete recipe packet");
        } finally { buffer.release(); }
        h.succeed();
    }
    private static void aim(GameTestHelper h) {
        var player = player(h, "lasergun");
        h.assertTrue(AimSessions.set(player, true), "Rifle supports original toggle aim");
        h.assertTrue(GunNetwork.handle(player, new GunActionPayload(false)), "Aimed laser fires through the server protocol");
        h.assertTrue(AimSessions.active(player, player.getMainHandItem()), "Shot preserves toggle aim");
        AimSessions.cancel(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, TGContent.GUNS.get("laserpistol").toStack());
        h.assertTrue(!AimSessions.set(player, true), "Pistol cannot acquire unsupported zoom");
        h.succeed();
    }
}
