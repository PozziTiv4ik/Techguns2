package techguns.modern.test;

import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.armor.*;
import techguns.modern.machine.charging.*;
import techguns.modern.machine.grinder.*;
import techguns.modern.network.*;
import techguns.modern.npc.*;

final class BlasterRifleGameTests {
    private static final WeaponDefinition GUN = Weapons.definition("blasterrifle");
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        double[] distances = {0, 24.99, 25, 25.01, 30, 34.99, 35, 35.01};
        double[] amounts = {10, 10, 10, 9.998, 9, 8.002, 8, 8};
        for (int n = 0; n < distances.length; n++) { final int index = n; r.register("blaster_falloff_" + n, () -> h -> falloff(h, distances[index], amounts[index])); }
        r.register("blaster_pre_movement_damage", () -> BlasterRifleGameTests::preMovement);
        r.register("blaster_displacement_not_path_length", () -> BlasterRifleGameTests::displacement);
        r.register("blaster_saved_origin_owner_and_npc_scale", () -> BlasterRifleGameTests::savedFlight);
        r.register("blaster_old_scatterbeam_save_compatibility", () -> BlasterRifleGameTests::oldScatterbeam);
        r.register("blaster_invalid_saved_origin", () -> BlasterRifleGameTests::invalid);
        r.register("blaster_thirty_tick_lifetime_and_water", () -> BlasterRifleGameTests::ttlWater);
        r.register("blaster_fifty_shots_cadence_and_creative", () -> BlasterRifleGameTests::cadence);
        r.register("blaster_native_partial_reload", () -> BlasterRifleGameTests::reload);
        r.register("blaster_aim_and_item_codecs", () -> BlasterRifleGameTests::aimAndCodecs);
        r.register("blaster_cancelled_spawn", () -> BlasterRifleGameTests::spawnVeto);
        for (String veto : List.of("impact", "damage")) r.register("blaster_veto_" + veto, () -> h -> veto(h, veto));
        for (boolean empty : List.of(false, true)) r.register("blaster_craft_" + (empty ? "empty" : "loaded"), () -> h -> craftGun(h, empty));
        r.register("blaster_craft_charge_reload_hit_chain", () -> BlasterRifleGameTests::production);
        for (String armor : List.of("vanilla", "npc", "witch")) r.register("blaster_armor_" + armor, () -> h -> armor(h, armor));
        r.register("blaster_typed_player_armor", () -> BlasterRifleGameTests::playerArmor);
        r.register("blaster_npc_five_shot_burst", () -> BlasterRifleGameTests::npc);
        r.register("blaster_native_flight", () -> BlasterRifleGameTests::nativeFlight);
        if (Boolean.getBoolean("techguns.chemistryTest")) r.register("chem_optional_blaster_materials", () -> BlasterRifleGameTests::optionalMaterials);
    }
    private static void near(GameTestHelper h, double actual, double expected, String message) { h.assertTrue(Math.abs(actual - expected) < .0002, message + ": " + actual + " != " + expected); }
    private static ItemStack item(String id) { return TGContent.AMMO.containsKey(id) ? TGContent.AMMO.get(id).toStack() : TGContent.MATERIALS.get(id).toStack(); }
    private static Player player(GameTestHelper h, int rounds) {
        var p = WeaponGameTests.player(h); var stack = TGContent.GUNS.get(GUN.id()).toStack(); stack.set(TGContent.ROUNDS.get(), rounds);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack); return p;
    }
    private static List<BlasterProjectile> shots(GameTestHelper h, Entity owner) { return h.getLevel().getEntitiesOfClass(BlasterProjectile.class, owner.getBoundingBox().inflate(5), s -> s.getOwner() == owner); }
    private static LivingEntity target(GameTestHelper h, String kind, Vec3 pos) {
        EntityType<? extends Mob> type = kind.equals("npc") ? NpcContent.SUPER_MUTANT.get() : kind.equals("witch") ? EntityTypes.WITCH : EntityTypes.PIG;
        var target = h.spawnWithNoFreeWill(type, pos); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000); return target;
    }
    private static BlasterProjectile shot(GameTestHelper h, Vec3 pos) {
        var shot = new BlasterProjectile(TGContent.BLASTER.get(), h.getLevel()); shot.configure(GUN);
        shot.setOwner(WeaponGameTests.player(h)); shot.setPos(pos); return shot;
    }
    private static BlasterProjectile travelled(GameTestHelper h, LivingEntity target, double distance) {
        var start = target.position().add(-2, .5, 0);
        var s = shot(h, start.add(0, distance, 0)); s.tick(); // Establish the actual first-tick origin, without a fabricated save.
        s.setPos(start); s.setDeltaMovement(3, 0, 0); h.getLevel().addFreshEntity(s); return s;
    }
    private static void falloff(GameTestHelper h, double distance, double amount) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var s = travelled(h, t, distance); s.tick();
        near(h, 1000 - t.getHealth(), amount + .01, "Damage at three-dimensional displacement " + distance);
        h.assertTrue(s.isRemoved(), "First impact consumes projectile"); t.discard(); h.succeed();
    }
    private static void preMovement(GameTestHelper h) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var start = t.position().add(-2, .5, 0);
        var s = shot(h, start.add(-24, 0, 0)); s.tick(); s.setPos(start); s.setDeltaMovement(4, 0, 0); h.getLevel().addFreshEntity(s); s.tick();
        near(h, 1000 - t.getHealth(), 10.01, "Start at 24 blocks stays full damage even when collision crosses 25"); t.discard(); h.succeed();
    }
    private static void displacement(GameTestHelper h) {
        var start = h.absoluteVec(new Vec3(4, 90.5, 4)); var s = shot(h, start);
        // Four actual moves total 80 blocks but return exactly to the launch point before impact.
        for (double y : new double[]{20, -20, 20, -20}) { s.setDeltaMovement(0, y, 0); s.tick(); }
        var t = target(h, "bare", new Vec3(6, 90, 4)); s.setDeltaMovement(3, 0, 0); h.getLevel().addFreshEntity(s); s.tick();
        near(h, 1000 - t.getHealth(), 10.01, "Falloff uses displacement, not accumulated path"); t.discard(); h.succeed();
    }
    private static void savedFlight(GameTestHelper h) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var owner = target(h, "npc", new Vec3(4, 85, 4));
        var s = travelled(h, t, 30); s.setOwner(owner); s.npcDamage(.5f); var saved = NetherGameTests.save(h, s); s.discard();
        var restored = new BlasterProjectile(TGContent.BLASTER.get(), h.getLevel()); NetherGameTests.load(h, restored, saved);
        h.assertValueEqual(restored.weapon(), GUN, "Rifle survives native save"); h.assertValueEqual(restored.getOwner(), owner, "Owner UUID resolves");
        h.assertValueEqual(restored.age(), 1, "Remaining TTL preserved"); h.assertValueEqual(restored.shotDamage(), new ShotDamage(true, .5f), "NPC scale preserved");
        h.getLevel().addFreshEntity(restored); restored.tick();
        near(h, 1000 - t.getHealth(), new ShotDamage(true, .5f).againstEntity(9) + .01, "Reload preserves origin and applies NPC scale after falloff");
        t.discard(); owner.discard(); h.succeed();
    }
    private static void oldScatterbeam(GameTestHelper h) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var s = travelled(h, t, 30); s.configure(Weapons.definition("scatterbeamrifle"));
        var saved = NetherGameTests.save(h, s); s.discard(); saved.remove("origin_x"); saved.remove("origin_y"); saved.remove("origin_z"); saved.putInt("age", 5);
        var restored = new BlasterProjectile(TGContent.BLASTER.get(), h.getLevel()); NetherGameTests.load(h, restored, saved);
        h.assertTrue(!restored.isRemoved(), "Pre-origin Scatterbeam saves remain valid"); h.assertValueEqual(restored.age(), 5, "Compatibility never renews TTL");
        h.getLevel().addFreshEntity(restored); restored.tick(); near(h, 1000 - t.getHealth(), 6.01, "Old Scatterbeam still deals constant damage"); t.discard(); h.succeed();
    }
    private static void invalid(GameTestHelper h) {
        var s = shot(h, h.absoluteVec(new Vec3(4, 90, 4))); s.tick(); var valid = NetherGameTests.save(h, s); s.discard();
        for (String mode : List.of("missing", "partial", "nan", "infinity", "bounds", "negative_age", "expired", "wrong_weapon", "bad_scale")) {
            var bad = valid.copy();
            switch (mode) {
                case "missing" -> { bad.remove("origin_x"); bad.remove("origin_y"); bad.remove("origin_z"); }
                case "partial" -> bad.remove("origin_y");
                case "nan" -> bad.putDouble("origin_x", Double.NaN);
                case "infinity" -> bad.putDouble("origin_y", Double.POSITIVE_INFINITY);
                case "bounds" -> bad.putDouble("origin_z", -30_000_001);
                case "negative_age" -> bad.putInt("age", -1);
                case "expired" -> bad.putInt("age", 30);
                case "wrong_weapon" -> bad.putString("weapon", "lasergun");
                case "bad_scale" -> bad.putFloat("damage_scale", 2);
            }
            var copy = new BlasterProjectile(TGContent.BLASTER.get(), h.getLevel()); NetherGameTests.load(h, copy, bad); h.assertTrue(copy.isRemoved(), "Reject damaged state: " + mode);
        }
        var unborn = shot(h, h.absoluteVec(new Vec3(4, 90, 4))); var copy = new BlasterProjectile(TGContent.BLASTER.get(), h.getLevel());
        NetherGameTests.load(h, copy, NetherGameTests.save(h, unborn)); h.assertTrue(!copy.isRemoved(), "A save before its first tick legitimately has no origin");
        copy.tick(); h.assertValueEqual(copy.age(), 1, "Unborn save can initialize origin"); unborn.discard(); copy.discard(); h.succeed();
    }
    private static void ttlWater(GameTestHelper h) {
        var s = shot(h, h.absoluteVec(new Vec3(4, 90, 4))); for (int i = 0; i < 29; i++) s.tick();
        h.assertTrue(!s.isRemoved(), "Rifle survives 29 movements"); var saved = NetherGameTests.save(h, s); s.discard();
        var copy = new BlasterProjectile(TGContent.BLASTER.get(), h.getLevel()); NetherGameTests.load(h, copy, saved);
        copy.setDeltaMovement(.1, 0, 0); var before = copy.position(); copy.tick();
        h.assertTrue(copy.isRemoved(), "Saved rifle expires on movement 30"); near(h, copy.getX(), before.x + .1, "Final tick still moves");
        h.setBlock(4, 2, 4, Blocks.WATER); var wet = shot(h, h.absoluteVec(new Vec3(4.5, 2.2, 4.5))); wet.setDeltaMovement(.1, 0, 0); wet.tick();
        h.assertTrue(wet.isInWater() && !wet.isRemoved(), "Rifle projectile crosses water"); near(h, wet.getDeltaMovement().x, .1 * (double).99f, "Water preserves air drag"); wet.discard(); h.succeed();
    }
    private static void cadence(GameTestHelper h) {
        var p = player(h, 50); var stack = p.getMainHandItem();
        for (int n = 0; n < 50; n++) {
            h.assertTrue(GunNetwork.handle(p, new GunActionPayload(false)), "Shot " + n); var shots = shots(h, p);
            h.assertValueEqual(shots.size(), 1, "One rifle projectile per charge"); shots.forEach(Entity::discard);
            h.assertValueEqual(GunItem.rounds(stack), 49 - n, "One charge consumed");
            for (int tick = 0; tick < 5; tick++) { h.assertTrue(!GunNetwork.handle(p, new GunActionPayload(false)), "Five tick cadence"); p.getCooldowns().tick(); }
        }
        h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "Empty rifle cannot fire");
        p.getAbilities().instabuild = true; GunItem.completeReload(p, stack); h.assertTrue(GunItem.fire(h.getLevel(), p, stack), "Creative reload/fire");
        h.assertValueEqual(GunItem.rounds(stack), 50, "Creative preserves charges"); shots(h, p).forEach(Entity::discard); h.succeed();
    }
    private static void reload(GameTestHelper h) {
        var p = player(h, 37); var stack = p.getMainHandItem(); p.getInventory().setItem(1, item("energycellempty"));
        h.assertTrue(!ReloadSessions.begin(p), "Empty cell cannot reload rifle"); p.getInventory().setItem(1, item("energycell").copyWithCount(2));
        h.assertTrue(ReloadSessions.begin(p), "Partial reload begins");
        for (int tick = 1; tick <= 45; tick++) { final int n = tick; h.runAfterDelay(tick, () -> {
            p.tick(); h.assertValueEqual(GunItem.rounds(stack), n == 45 ? 50 : 37, "Native R deadline");
            if (n == 45) { h.assertValueEqual(p.getInventory().countItem(item("energycell").getItem()), 1, "One cell consumed");
                h.assertValueEqual(p.getInventory().countItem(item("energycellempty").getItem()), 1, "One empty return"); p.discard(); h.succeed(); }
        }); }
    }
    private static void aimAndCodecs(GameTestHelper h) {
        var p = player(h, 49); var stack = p.getMainHandItem(); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Rifle saved"));
        stack.set(TGContent.BALLISTIC_VARIANT.get(), BallisticVariant.EXPLOSIVE); stack.set(TGContent.RELOAD_TICKS.get(), 20);
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var restored = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()).getOrThrow();
        h.assertValueEqual(GunItem.rounds(restored), 49, "Saved charge count"); h.assertValueEqual(restored.getHoverName(), stack.getHoverName(), "Saved custom name");
        h.assertTrue(!restored.has(TGContent.RELOAD_TICKS.get()), "Reload does not persist");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try { ItemStack.STREAM_CODEC.encode(buffer, stack); h.assertTrue(ItemStack.matches(stack, ItemStack.STREAM_CODEC.decode(buffer)), "Item state synchronizes"); } finally { buffer.release(); }
        p.setYRot(0); p.setXRot(0); h.assertTrue(AimSessions.set(p, true), "Aim toggle");
        h.assertTrue(GunItem.fire(h.getLevel(), p, stack), "Aimed rifle fires"); var shot = shots(h, p).getFirst();
        near(h, shot.getX(), p.getX(), "Centered rifle aim"); near(h, shot.getY(), p.getEyeY() - .1, "Source muzzle height");
        h.assertTrue(Math.abs(shot.getYRot()) <= .751 && Math.abs(shot.getXRot()) <= .751, "Rifle aimed angular spread");
        h.assertValueEqual(shot.weapon(), GUN, "Injected explosive component cannot change factory"); shot.discard(); h.succeed();
    }
    private static void spawnVeto(GameTestHelper h) {
        var p = player(h, 50); var stack = p.getMainHandItem();
        Consumer<EntityJoinLevelEvent> veto = e -> { if (e.getEntity() instanceof BlasterProjectile s && s.getOwner() == p) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(veto);
        try { h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "Vetoed rifle shot rejected"); h.assertValueEqual(GunItem.rounds(stack), 50, "No lost charge"); h.assertTrue(!p.getCooldowns().isOnCooldown(stack), "No cooldown on rejection"); }
        finally { NeoForge.EVENT_BUS.unregister(veto); } h.succeed();
    }
    private static void veto(GameTestHelper h, String mode) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var s = travelled(h, t, 30);
        Consumer<ProjectileImpactEvent> impact = e -> { if (e.getProjectile() == s && mode.equals("impact")) e.setCanceled(true); };
        Consumer<LivingIncomingDamageEvent> damage = e -> { if (e.getSource().getDirectEntity() == s && mode.equals("damage")) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(impact); NeoForge.EVENT_BUS.addListener(damage);
        try { s.tick(); near(h, t.getHealth(), 1000, "Veto preserves health"); h.assertValueEqual(s.isRemoved(), mode.equals("damage"), "Impact veto continues flight"); }
        finally { NeoForge.EVENT_BUS.unregister(impact); NeoForge.EVENT_BUS.unregister(damage); s.discard(); t.discard(); } h.succeed();
    }
    private static CraftingInput grid(boolean empty, Item glass) { return CraftingInput.of(3, 3, List.of(item("platecarbon"), item("circuitboardelite"), new ItemStack(glass),
            item("laserbarrel"), item("carbonreceiver"), item("carbonstock"), ItemStack.EMPTY, item(empty ? "energycellempty" : "energycell"), ItemStack.EMPTY)); }
    private static CraftingRecipe recipe(GameTestHelper h, boolean empty) { return (CraftingRecipe)h.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, TGContent.id("blasterrifle" + (empty ? "_alt" : "")))).orElseThrow().value(); }
    private static ItemStack crafted(GameTestHelper h, boolean empty) {
        var recipe = recipe(h, empty); var input = grid(empty, Items.GLASS); h.assertTrue(recipe.matches(input, h.getLevel()), "Original recipe with plain-glass fallback"); return recipe.assemble(input);
    }
    private static void craftGun(GameTestHelper h, boolean empty) {
        var result = crafted(h, empty); h.assertTrue(result.is(TGContent.GUNS.get(GUN.id()).get()), "Native rifle crafting"); h.assertValueEqual(GunItem.rounds(result), empty ? 0 : 50, "Source loaded/empty output"); h.succeed();
    }
    private static void production(GameTestHelper h) {
        var gun = crafted(h, true); var pos = new BlockPos(5, 2, 4); h.setBlock(pos, ChargingStationContent.BLOCK.get());
        var charger = h.getBlockEntity(pos, ChargingStationBlockEntity.class); charger.setItem(0, item("energycellempty"));
        try (var tx = Transaction.openRoot()) { h.assertValueEqual(charger.energy().insert(49600, tx), 49600, "Source rounded charge cost"); tx.commit(); }
        for (int i = 0; i < 120; i++) ChargingStationBlockEntity.tick(h.getLevel(), charger.getBlockPos(), charger.getBlockState(), charger);
        var p = player(h, 0); p.setItemInHand(InteractionHand.MAIN_HAND, gun); p.getInventory().setItem(1, charger.removeItem(1, 1));
        h.assertTrue(ReloadSessions.begin(p), "Produced cell reloads crafted rifle"); for (int i = 0; i < 45; i++) ReloadSessions.tick(p);
        h.assertTrue(GunItem.fire(h.getLevel(), p, gun), "Survival chain fires"); var s = shots(h, p).getFirst(); var t = target(h, "bare", new Vec3(6, 90, 4));
        s.setPos(t.position().add(-2, .5, 0)); s.setDeltaMovement(3, 0, 0); s.tick(); near(h, 1000 - t.getHealth(), 10.01, "Actual produced ammunition hits");
        h.assertValueEqual(GunItem.rounds(gun), 49, "One charge consumed"); h.assertValueEqual(p.getInventory().countItem(item("energycellempty").getItem()), 1, "Empty cell returned"); t.discard(); h.succeed();
    }
    private static void armor(GameTestHelper h, String kind) {
        var t = target(h, kind, new Vec3(6, 90, 4)); if (kind.equals("vanilla")) { t.getAttribute(Attributes.ARMOR).setBaseValue(20); t.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2); }
        float energy = t instanceof NpcTypedArmor npc ? npc.armorAgainst(DamageKind.ENERGY) : (float)t.getAttributeValue(Attributes.ARMOR) * .5f;
        float physical = t instanceof NpcTypedArmor npc ? npc.armorAgainst(DamageKind.PHYSICAL) : (float)t.getAttributeValue(Attributes.ARMOR);
        float toughness = (float)t.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        var s = travelled(h, t, 30); s.tick();
        near(h, 1000 - t.getHealth(), ArmorMath.afterArmor(9, energy, toughness, 1) * (kind.equals("witch") ? .15f : 1) + ArmorMath.afterArmor(.01f, physical, toughness, 0), "Falloff then ENERGY armor/penetration and magic resistance");
        t.discard(); h.succeed();
    }
    private static void playerArmor(GameTestHelper h) {
        var p = ArmorGameTests.player(h); ArmorGameTests.equipAll(p); var s = shot(h, p.position().add(4, 4, 0));
        p.hurtServer(h.getLevel(), s.shotDamage().source(h.getLevel(), BlasterProjectile.DAMAGE_TYPE, s), 10);
        h.assertTrue(p.getHealth() < 1000 && p.getHealth() > 990, "Typed player armor absorbs ENERGY");
        h.assertTrue(TGArmorSystem.SLOTS.stream().anyMatch(slot -> p.getItemBySlot(slot).getDamageValue() > 0), "Armor durability spent"); s.discard(); h.succeed();
    }
    private static void npc(GameTestHelper h) {
        // Both XZ footprints must remain inside the 12x12 template's entity-ticking chunks.
        // The (8,9) separation stays in the original 20-tick interval band at roughly 12 blocks.
        var npc = h.spawnWithNoFreeWill(NpcContent.BANDIT.get(), new Vec3(2, 90, 2)); var t = target(h, "bare", new Vec3(10, 90, 11));
        h.assertTrue(h.getLevel().areEntitiesActuallyLoadedAndTicking(t.chunkPosition())
                && h.getLevel().getEntity(t.getUUID()) == t, "NPC target is registered in an owned ticking chunk");
        npc.setNoGravity(true); npc.setItemSlot(EquipmentSlot.MAINHAND, TGContent.GUNS.get(GUN.id()).toStack()); npc.setTarget(t);
        var goal = new NpcRangedGoal(npc); int[] clock = {0}; var ticks = new ArrayList<Integer>(); var fired = new ArrayList<BlasterProjectile>();
        Consumer<EntityJoinLevelEvent> listener = e -> { if (e.getEntity() instanceof BlasterProjectile s && s.getOwner() == npc) { ticks.add(clock[0]); fired.add(s); } };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            h.assertTrue(goal.canUse(), "Rifle activates ranged goal"); for (; clock[0] <= 32; clock[0]++) goal.tick();
            h.assertValueEqual(ticks, List.of(20, 23, 26, 29, 32), "Actual ranged goal creates five separate shots");
            h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()), 0, "NPC does not consume player inventory");
            var s = fired.getFirst(); h.assertTrue(s.shotDamage().npc(), "Launch captures NPC profile");
            s.setPos(t.position().add(-2, .5, 0)); s.setDeltaMovement(3, 0, 0); s.tick();
            near(h, 1000 - t.getHealth(), s.shotDamage().againstEntity(10) + .01, "Native NPC shot applies damage factor once");
        } finally { NeoForge.EVENT_BUS.unregister(listener); fired.forEach(Entity::discard); npc.discard(); t.discard(); } h.succeed();
    }
    private static void nativeFlight(GameTestHelper h) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var s = shot(h, t.position().add(-4, .5, 0)); s.setDeltaMovement(1.5, 0, 0); h.getLevel().addFreshEntity(s);
        h.runAfterDelay(1, () -> { near(h, t.getHealth(), 1000, "Not an instant beam"); h.assertTrue(!s.isRemoved(), "Native projectile still travelling"); });
        h.runAfterDelay(5, () -> { near(h, 1000 - t.getHealth(), 10.01, "World ticks deliver delayed impact"); h.assertTrue(s.isRemoved(), "Hit removes entity"); t.discard(); h.succeed(); });
    }
    private static void optionalMaterials(GameTestHelper h) {
        // This profile supplies obsidian as external hardened glass and emerald as electrum.
        for (boolean empty : List.of(false, true)) {
            var recipe = recipe(h, empty); var preferred = grid(empty, Items.OBSIDIAN); var plain = grid(empty, Items.GLASS);
            h.assertTrue(recipe.matches(preferred, h.getLevel()) && !recipe.matches(plain, h.getLevel()), "Preferred hardened glass excludes fallback");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try { Recipe.STREAM_CODEC.encode(buffer, recipe); var copy = (CraftingRecipe)Recipe.STREAM_CODEC.decode(buffer);
                h.assertTrue(copy.matches(preferred, h.getLevel()) && !copy.matches(plain, h.getLevel()), "Preference survives recipe network codec");
                h.assertValueEqual(GunItem.rounds(copy.assemble(preferred)), empty ? 0 : 50, "Recipe packet preserves charges");
            } finally { buffer.release(); }
        }
        var pos = new BlockPos(5, 2, 4); h.setBlock(pos, GrinderContent.BLOCK.get()); var grinder = h.getBlockEntity(pos, GrinderBlockEntity.class);
        var loaded = TGContent.GUNS.get(GUN.id()).toStack(); loaded.set(TGContent.ROUNDS.get(), 50); grinder.setItem(0, loaded);
        try (var tx = Transaction.openRoot()) { grinder.energy().insert(500, tx); tx.commit(); }
        for (int i = 0; i < 101; i++) GrinderBlockEntity.tick(h.getLevel(), grinder.getBlockPos(), grinder.getBlockState(), grinder);
        var outputs = new HashMap<Item, Integer>(); for (int slot = 2; slot < 11; slot++) { var stack = grinder.getItem(slot); if (!stack.isEmpty()) outputs.merge(stack.getItem(), stack.getCount(), Integer::sum); }
        h.assertValueEqual(outputs, Map.of(item("carbonfibers").getItem(), 3, item("plasticsheet").getItem(), 1, Items.REDSTONE, 20, Items.EMERALD, 3), "Real Grinder prefers external electrum and never refunds loaded energy"); h.succeed();
    }
    private BlasterRifleGameTests() {}
}
