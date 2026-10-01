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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.armor.*;
import techguns.modern.machine.*;
import techguns.modern.machine.charging.*;
import techguns.modern.network.*;
import techguns.modern.npc.*;

final class GaussGameTests {
    private static final WeaponDefinition GUN = Weapons.definition("gaussrifle");
    private static final String SLUGS = "gaussrifleslugs", CELL = "energycell", EMPTY = "energycellempty";
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for (int rounds : new int[]{0, 1, 4, 7}) r.register("gauss_reload_" + rounds, () -> h -> reload(h, rounds));
        for (String missing : List.of(SLUGS, CELL)) {
            r.register("gauss_missing_" + missing, () -> h -> missing(h, missing, false));
            r.register("gauss_removed_during_reload_" + missing, () -> h -> missing(h, missing, true));
        }
        for (String cancel : List.of("stack", "death", "use")) r.register("gauss_cancel_" + cancel, () -> h -> cancel(h, cancel));
        r.register("gauss_native_reload_timer", () -> GaussGameTests::nativeTimer);
        r.register("gauss_reload_moved_inputs_and_full_noop", () -> GaussGameTests::movedInputs);
        r.register("gauss_overflow_and_creative", () -> GaussGameTests::overflow);
        r.register("gauss_item_codecs_and_variant_guard", () -> GaussGameTests::itemCodecs);
        r.register("gauss_cadence_aim_and_hands", () -> GaussGameTests::cadence);
        r.register("gauss_spawn_veto", () -> GaussGameTests::spawnVeto);
        for (boolean empty : new boolean[]{false, true}) r.register("gauss_craft_" + (empty ? "empty" : "loaded"), () -> h -> craftGun(h, empty));
        r.register("gauss_barrel_craft", () -> GaussGameTests::barrel);
        r.register("gauss_press_charging_reload_hit_chain", () -> GaussGameTests::production);
        for (String armor : List.of("bare", "vanilla", "npc")) r.register("gauss_hit_" + armor, () -> h -> hit(h, armor));
        r.register("gauss_typed_player_armor", () -> GaussGameTests::playerArmor);
        r.register("gauss_cooldown_and_single_impulse", () -> GaussGameTests::cooldown);
        for (String veto : List.of("impact", "damage", "zero")) r.register("gauss_veto_" + veto, () -> h -> veto(h, veto));
        for (boolean unsafe : new boolean[]{false, true}) r.register("gauss_wall_" + unsafe, () -> h -> wall(h, unsafe));
        r.register("gauss_first_target_stops_slug", () -> GaussGameTests::firstTarget);
        r.register("gauss_air_water_and_save", () -> GaussGameTests::flight);
        r.register("gauss_ttl_and_invalid_saves", () -> GaussGameTests::ttl);
        r.register("gauss_npc_factory_and_scale", () -> GaussGameTests::npc);
    }
    private static ItemStack item(String id) { return TGContent.AMMO.containsKey(id) ? TGContent.AMMO.get(id).toStack() : TGContent.MATERIALS.get(id).toStack(); }
    private static Player player(GameTestHelper h, int rounds) {
        var p = WeaponGameTests.player(h); var gun = TGContent.GUNS.get("gaussrifle").toStack(); gun.set(TGContent.ROUNDS.get(), rounds);
        p.setItemInHand(InteractionHand.MAIN_HAND, gun); return p;
    }
    private static void supply(Player p) { p.getInventory().setItem(1, item(SLUGS).copyWithCount(2)); p.getInventory().setItem(2, item(CELL).copyWithCount(2)); }
    private static int count(Player p, String id) { return p.getInventory().countItem(item(id).getItem()); }
    private static void near(GameTestHelper h, double actual, double expected, String message) { h.assertTrue(Math.abs(actual - expected) < .002, message + ": " + actual + " != " + expected); }
    private static List<GaussProjectile> shots(GameTestHelper h, Entity owner) { return h.getLevel().getEntitiesOfClass(GaussProjectile.class, owner.getBoundingBox().inflate(5), s -> s.getOwner() == owner); }

    private static void reload(GameTestHelper h, int rounds) {
        var p = player(h, rounds); var stack = p.getMainHandItem(); supply(p);
        h.assertTrue(GunNetwork.handle(p, new GunActionPayload(true)), "R starts with both inputs");
        for (int n = 0; n < 59; n++) ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack), rounds, "No early refill");
        h.assertValueEqual(count(p, SLUGS), 2, "No early slug consumption"); h.assertValueEqual(count(p, CELL), 2, "No early cell consumption");
        h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "Cannot fire while reloading");
        ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack), 8, "Eight charges");
        h.assertValueEqual(count(p, SLUGS), 1, "One source slug bundle, no partial refund");
        h.assertValueEqual(count(p, CELL), 1, "One cell"); h.assertValueEqual(count(p, EMPTY), 1, "One empty cell");
        h.assertTrue(!ReloadSessions.active(p), "Session ended"); h.succeed();
    }
    private static void missing(GameTestHelper h, String missing, boolean late) {
        var p = player(h, 4); var stack = p.getMainHandItem(); supply(p);
        if (late) { h.assertTrue(ReloadSessions.begin(p), "Begin before inventory changes"); for (int i = 0; i < 59; i++) ReloadSessions.tick(p); }
        p.getInventory().setItem(missing.equals(SLUGS) ? 1 : 2, item(EMPTY));
        h.assertValueEqual(GunItem.availableAmmo(p, stack), 0, "Requires complete pairs");
        h.assertValueEqual(GunItem.availableAmmo(p, GUN), 0, "Definition also counts complete pairs");
        if (late) ReloadSessions.tick(p);
        else { h.assertTrue(!ReloadSessions.begin(p), "Missing input rejects R"); GunItem.completeReload(p, stack); }
        h.assertValueEqual(GunItem.rounds(stack), 4, "Loaded charges preserved");
        h.assertValueEqual(count(p, missing.equals(SLUGS) ? CELL : SLUGS), 2, "Other input untouched");
        h.assertValueEqual(count(p, EMPTY), 1, "Empty cell never substitutes for a charged one or duplicates");
        h.assertTrue(!ReloadSessions.active(p), "No stale session"); h.succeed();
    }
    private static void cancel(GameTestHelper h, String reason) {
        var p = player(h, 4); var stack = p.getMainHandItem(); supply(p); h.assertTrue(ReloadSessions.begin(p), "Start R");
        if (reason.equals("stack")) p.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        if (reason.equals("death")) p.setHealth(0);
        if (reason.equals("use")) { p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BOW)); p.startUsingItem(InteractionHand.OFF_HAND); }
        for (int n = 0; n < 60; n++) ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack), 4, "Cancelled charges preserved");
        h.assertValueEqual(count(p, SLUGS), 2, "Slugs preserved"); h.assertValueEqual(count(p, CELL), 2, "Cells preserved");
        h.assertValueEqual(count(p, EMPTY), 0, "No return on cancel"); h.assertTrue(!stack.has(TGContent.RELOAD_TICKS.get()), "Transient state cleared"); h.succeed();
    }
    private static void nativeTimer(GameTestHelper h) {
        var p = player(h, 0); supply(p); h.assertTrue(ReloadSessions.begin(p), "Native R starts");
        for (int tick = 1; tick <= 60; tick++) {
            final int n = tick;
            h.runAfterDelay(tick, () -> {
                p.tick(); h.assertValueEqual(GunItem.rounds(p.getMainHandItem()), n == 60 ? 8 : 0, "Native event reload deadline");
                if (n == 60) { h.assertValueEqual(count(p, EMPTY), 1, "One completed transaction"); p.discard(); h.succeed(); }
            });
        }
    }
    private static void movedInputs(GameTestHelper h) {
        var p = player(h, 0); supply(p); h.assertTrue(ReloadSessions.begin(p), "R starts");
        p.getInventory().setItem(8, p.getInventory().removeItemNoUpdate(1)); p.getInventory().setItem(20, p.getInventory().removeItemNoUpdate(2));
        for (int i = 0; i < 60; i++) ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(p.getMainHandItem()), 8, "Rechecks current slots");
        h.assertTrue(!ReloadSessions.begin(p), "Full weapon cannot reload"); GunItem.completeReload(p, p.getMainHandItem());
        h.assertValueEqual(count(p, SLUGS), 1, "Full no-op keeps slugs"); h.assertValueEqual(count(p, CELL), 1, "Full no-op keeps cell");
        h.assertValueEqual(count(p, EMPTY), 1, "No duplicate returns"); h.succeed();
    }
    private static void overflow(GameTestHelper h) {
        var p = player(h, 7); for (int i = 1; i < p.getInventory().getContainerSize(); i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64)); supply(p);
        GunItem.completeReload(p, p.getMainHandItem());
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3), e -> e.getItem().is(item(EMPTY).getItem()));
        h.assertValueEqual(drops.stream().mapToInt(e -> e.getItem().getCount()).sum(), 1, "One overflow empty cell"); drops.forEach(Entity::discard);
        var creative = player(h, 0); creative.getAbilities().instabuild = true;
        h.assertTrue(ReloadSessions.begin(creative), "Creative R without either input"); for (int i = 0; i < 60; i++) ReloadSessions.tick(creative);
        h.assertTrue(GunItem.fire(h.getLevel(), creative, creative.getMainHandItem()), "Creative fires");
        h.assertValueEqual(GunItem.rounds(creative.getMainHandItem()), 8, "Original Creative preserves charges");
        h.assertValueEqual(count(creative, EMPTY), 0, "Creative creates no returns"); shots(h, creative).forEach(Entity::discard); h.succeed();
    }
    private static void itemCodecs(GameTestHelper h) {
        var p = player(h, 5); var stack = p.getMainHandItem(); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Gauss saved"));
        stack.set(TGContent.BALLISTIC_VARIANT.get(), BallisticVariant.EXPLOSIVE); stack.set(TGContent.RELOAD_TICKS.get(), 22);
        h.assertValueEqual(BallisticAmmo.variant(stack), BallisticVariant.DEFAULT, "Injected AS50 variant ignored");
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var loaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()).getOrThrow();
        h.assertValueEqual(GunItem.rounds(loaded), 5, "Charges saved"); h.assertValueEqual(loaded.getHoverName(), stack.getHoverName(), "Name saved");
        h.assertTrue(!loaded.has(TGContent.RELOAD_TICKS.get()), "R does not persist after reconnect");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try { ItemStack.STREAM_CODEC.encode(buffer, stack); h.assertTrue(ItemStack.matches(stack, ItemStack.STREAM_CODEC.decode(buffer)), "Components synchronize"); }
        finally { buffer.release(); }
        h.assertTrue(GunItem.fire(h.getLevel(), p, stack), "Injected component still fires Gauss");
        h.assertValueEqual(shots(h, p).size(), 1, "Gauss factory selected"); shots(h, p).forEach(Entity::discard); h.succeed();
    }
    private static void cadence(GameTestHelper h) {
        var p = player(h, 8); var stack = p.getMainHandItem(); p.setYRot(0); p.setXRot(0);
        h.assertTrue(AimSessions.set(p, true), "Aim begins"); h.assertTrue(GunNetwork.handle(p, new GunActionPayload(false)), "Aimed first shot");
        var slug = shots(h, p).getFirst(); near(h, slug.getX(), p.getX(), "Centered muzzle");
        near(h, slug.getY(), p.getEyeY() - .1, "Source muzzle height");
        h.assertTrue(slug.getDeltaMovement().length() > 6 && slug.getDeltaMovement().length() < 9, "Original 1.5 speed multiplier retained");
        near(h, slug.getYRot(), 0, "Aim removes angular spread"); slug.discard();
        for (int i = 0; i < 29; i++) { p.tick(); h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "Thirty-tick fire delay"); }
        p.tick(); AimSessions.cancel(p); p.setMainArm(HumanoidArm.LEFT);
        p.setItemInHand(InteractionHand.OFF_HAND, stack); p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(GunItem.fire(h.getLevel(), p, stack), "Offhand fires after cooldown");
        slug = shots(h, p).getFirst(); h.assertTrue(slug.getX() < p.getX(), "Left-dominant offhand uses right muzzle"); slug.discard();
        h.assertValueEqual(GunItem.rounds(stack), 6, "Two shots consume two charges"); h.succeed();
    }
    private static void spawnVeto(GameTestHelper h) {
        var p = player(h, 8); var stack = p.getMainHandItem();
        Consumer<EntityJoinLevelEvent> veto = e -> { if (e.getEntity() instanceof GaussProjectile s && s.getOwner() == p) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(veto);
        try { h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "Spawn veto rejects shot"); h.assertValueEqual(GunItem.rounds(stack), 8, "No lost charge"); h.assertTrue(!p.getCooldowns().isOnCooldown(stack), "No cooldown on rejected shot"); }
        finally { NeoForge.EVENT_BUS.unregister(veto); } h.succeed();
    }
    private static ItemStack craft(GameTestHelper h, String id, List<ItemStack> grid) {
        var input = CraftingInput.of(3, 3, grid);
        var recipe = (CraftingRecipe)h.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, TGContent.id(id))).orElseThrow().value();
        h.assertTrue(recipe.matches(input, h.getLevel()), "Native recipe: " + id); return recipe.assemble(input);
    }
    private static void craftGun(GameTestHelper h, boolean empty) {
        var result = craft(h, "gaussrifle" + (empty ? "_alt" : ""), List.of(new ItemStack(Items.DIAMOND), item("platetitanium"), item("circuitboardelite"),
                item("gaussbarrel"), item("carbonreceiver"), item("carbonstock"), ItemStack.EMPTY, empty ? ItemStack.EMPTY : item(SLUGS), item(empty ? EMPTY : CELL)));
        h.assertTrue(result.is(TGContent.GUNS.get("gaussrifle").get()), "Usable Gauss gun"); h.assertValueEqual(GunItem.rounds(result), empty ? 0 : 8, "Source loaded/empty recipe"); h.succeed();
    }
    private static void barrel(GameTestHelper h) {
        var result = craft(h, "gaussbarrel", List.of(item("platetitanium"), item("goldwire"), item("goldwire"), item("carbonbarrel"), item("carbonbarrel"), item("circuitboardelite"), item("platetitanium"), item("goldwire"), item("goldwire")));
        h.assertTrue(result.is(item("gaussbarrel").getItem()) && result.getCount() == 1, "Original Gauss barrel costs"); h.succeed();
    }
    private static void production(GameTestHelper h) {
        var pressPos = new BlockPos(3, 2, 4); h.setBlock(pressPos, TGMachineContent.METAL_PRESS.get());
        var press = h.getBlockEntity(pressPos, MetalPressBlockEntity.class); press.setItem(0, item("plateobsidiansteel")); press.setItem(1, item("platetitanium"));
        var chargePos = new BlockPos(5, 2, 4); h.setBlock(chargePos, ChargingStationContent.BLOCK.get());
        var charger = h.getBlockEntity(chargePos, ChargingStationBlockEntity.class); charger.setItem(0, item(EMPTY));
        try (var tx = Transaction.openRoot()) { h.assertValueEqual(press.energy().insert(2000, tx), 2000, "Press power"); h.assertValueEqual(charger.energy().insert(49600, tx), 49600, "Legacy truncated charge cost"); tx.commit(); }
        for (int i = 0; i < 120; i++) {
            ProcessingMachineBlockEntity.tick(h.getLevel(), press.getBlockPos(), press.getBlockState(), press);
            ChargingStationBlockEntity.tick(h.getLevel(), charger.getBlockPos(), charger.getBlockState(), charger);
        }
        h.assertTrue(press.getItem(2).is(item(SLUGS).getItem()) && press.getItem(2).getCount() == 4, "Source press makes four bundles");
        h.assertTrue(charger.getItem(1).is(item(CELL).getItem()), "Real charged cell");
        h.assertValueEqual(press.energy().getAmountAsLong() + charger.energy().getAmountAsLong(), 0L, "Both machine costs paid");
        var p = player(h, 0); p.getInventory().setItem(1, press.removeItem(2, 1)); p.getInventory().setItem(2, charger.removeItem(1, 1));
        h.assertTrue(ReloadSessions.begin(p), "Produced pair reloads"); for (int i = 0; i < 60; i++) ReloadSessions.tick(p);
        h.assertTrue(GunItem.fire(h.getLevel(), p, p.getMainHandItem()), "Produced pair fires");
        var slug = shots(h, p).getFirst(); var target = target(h, false, new Vec3(6, 70, 4));
        slug.setPos(target.position().add(-2, .5, 0)); slug.setDeltaMovement(4, 0, 0); slug.tick();
        near(h, target.getHealth(), 959.99, "Actual manufactured ammunition hits"); target.discard();
        h.assertValueEqual(count(p, EMPTY), 1, "Case returned"); h.succeed();
    }
    private static LivingEntity target(GameTestHelper h, boolean npc, Vec3 pos) {
        EntityType<? extends Mob> type = npc ? NpcContent.SUPER_MUTANT.get() : EntityTypes.PIG;
        var t = h.spawnWithNoFreeWill(type, pos); t.setNoGravity(true);
        t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); t.setHealth(1000); return t;
    }
    private static GaussProjectile shot(GameTestHelper h, Vec3 pos) {
        var s = new GaussProjectile(TGContent.GAUSS.get(), h.getLevel()); s.configure(GUN); s.setOwner(WeaponGameTests.player(h)); s.setPos(pos); h.getLevel().addFreshEntity(s); return s;
    }
    private static GaussProjectile aimed(GameTestHelper h, Entity target) { var s = shot(h, target.position().add(-2, .5, 0)); s.setDeltaMovement(4, 0, 0); return s; }
    private static void hit(GameTestHelper h, String kind) {
        var t = target(h, kind.equals("npc"), new Vec3(6, 70, 4));
        if (kind.equals("vanilla")) { t.getAttribute(Attributes.ARMOR).setBaseValue(30); t.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2); }
        float armor = t instanceof NpcTypedArmor npc ? npc.armorAgainst(DamageKind.PROJECTILE) : (float)t.getAttributeValue(Attributes.ARMOR);
        float physical = t instanceof NpcTypedArmor npc ? npc.armorAgainst(DamageKind.PHYSICAL) : armor;
        float toughness = (float)t.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        var s = aimed(h, t); s.tick();
        near(h, 1000 - t.getHealth(), ArmorMath.afterArmor(40, armor, toughness, 2) + ArmorMath.afterArmor(.01f, physical, toughness, 0), "Projectile armor and separate physical impulse");
        h.assertTrue(s.isRemoved(), "First impact consumes slug"); t.discard(); h.succeed();
    }
    private static void playerArmor(GameTestHelper h) {
        var p = ArmorGameTests.player(h); ArmorGameTests.equipAll(p);
        var s = shot(h, p.position().add(4, 4, 0)); var source = s.shotDamage().source(h.getLevel(), GaussProjectile.DAMAGE_TYPE, s);
        h.assertTrue(source.is(DamageTypeTags.IS_PROJECTILE) && source.is(DamageTypeTags.BYPASSES_COOLDOWN) && !source.is(DamageTypeTags.BYPASSES_ARMOR), "Correct native tags");
        p.hurtServer(h.getLevel(), source, 40);
        h.assertTrue(p.getHealth() < 1000 && p.getHealth() > 960, "Typed player armor absorbs actual Gauss hit");
        h.assertTrue(TGArmorSystem.SLOTS.stream().anyMatch(slot -> p.getItemBySlot(slot).getDamageValue() > 0), "Armor durability spent"); s.discard(); h.succeed();
    }
    private static void cooldown(GameTestHelper h) {
        var t = target(h, false, new Vec3(6, 70, 4)); int[] impulses = {0};
        Consumer<LivingKnockBackEvent> listener = e -> { if (e.getEntity() == t) impulses[0]++; }; NeoForge.EVENT_BUS.addListener(listener);
        try {
            aimed(h, t).tick(); aimed(h, t).tick();
            near(h, 1000 - t.getHealth(), 80.01, "Main bullet bypasses cooldown; impulse does not");
            h.assertValueEqual(impulses[0], 1, "One ordinary impulse for both immediate hits");
        } finally { NeoForge.EVENT_BUS.unregister(listener); t.discard(); } h.succeed();
    }
    private static void veto(GameTestHelper h, String kind) {
        var t = target(h, false, new Vec3(6, 70, 4)); var s = aimed(h, t); if (kind.equals("zero")) s.npcDamage(0);
        Consumer<ProjectileImpactEvent> impact = e -> { if (e.getProjectile() == s && kind.equals("impact")) e.setCanceled(true); };
        Consumer<LivingIncomingDamageEvent> damage = e -> { if (e.getSource().getDirectEntity() == s && kind.equals("damage")) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(impact); NeoForge.EVENT_BUS.addListener(damage);
        try { s.tick(); near(h, t.getHealth(), 1000, "Cancelled or zero damage preserved"); h.assertValueEqual(s.isRemoved(), !kind.equals("impact"), "Native impact veto permits continued flight"); }
        finally { NeoForge.EVENT_BUS.unregister(impact); NeoForge.EVENT_BUS.unregister(damage); s.discard(); t.discard(); } h.succeed();
    }
    private static void wall(GameTestHelper h, boolean unsafe) {
        var p = player(h, 8); p.setData(SafeMode.SAFE, !unsafe); h.assertTrue(GunItem.fire(h.getLevel(), p, p.getMainHandItem()), "Both B modes permit Gauss"); var s = shots(h, p).getFirst();
        h.setBlock(5, 70, 4, Blocks.DIRT); var t = target(h, false, new Vec3(6, 70, 4));
        s.setPos(h.absoluteVec(new Vec3(3, 70.5, 4.5))); s.setDeltaMovement(7.5, 0, 0); int[] blasts = {0};
        Consumer<ExplosionEvent.Start> listener = e -> { if (e.getExplosion().getDirectSourceEntity() == s) blasts[0]++; }; NeoForge.EVENT_BUS.addListener(listener);
        try { s.tick(); h.assertTrue(s.isRemoved(), "Wall intercepts swept flight"); h.assertBlockPresent(Blocks.DIRT, 5, 70, 4); near(h, t.getHealth(), 1000, "No through-wall damage"); h.assertValueEqual(blasts[0], 0, "Gauss FX never imply an explosion"); }
        finally { NeoForge.EVENT_BUS.unregister(listener); t.discard(); s.discard(); } h.succeed();
    }
    private static void firstTarget(GameTestHelper h) {
        var first = target(h, false, new Vec3(5, 70, 4)); var next = target(h, false, new Vec3(7, 70, 4));
        var s = aimed(h, first); s.setDeltaMovement(7.5, 0, 0); s.tick(); s.tick();
        near(h, first.getHealth(), 959.99, "First target hit once"); near(h, next.getHealth(), 1000, "Armor penetration does not pierce entities");
        first.discard(); next.discard(); h.succeed();
    }
    private static void flight(GameTestHelper h) {
        var s = shot(h, h.absoluteVec(new Vec3(4, 80, 4))); s.npcDamage(.5f); s.setDeltaMovement(.4, .2, 0); var start = s.position(); s.tick();
        near(h, s.getX(), start.x + .4, "Move before air drag"); near(h, s.getDeltaMovement().y, .2 * (double).99f, "No Gauss gravity");
        var copy = new GaussProjectile(TGContent.GAUSS.get(), h.getLevel()); NetherGameTests.load(h, copy, NetherGameTests.save(h, s));
        h.assertValueEqual(copy.age(), 1, "Age survives save"); h.assertValueEqual(copy.weapon(), GUN, "Factory survives save");
        h.assertValueEqual(copy.shotDamage(), new ShotDamage(true, .5f), "NPC profile survives save"); h.assertValueEqual(copy.getDeltaMovement(), s.getDeltaMovement(), "Velocity survives save"); s.discard(); copy.discard();
        h.setBlock(4, 2, 4, Blocks.WATER); var water = shot(h, h.absoluteVec(new Vec3(4.5, 2.2, 4.5))); water.setDeltaMovement(.1, 0, 0); water.tick();
        h.assertTrue(!water.isRemoved() && water.isInWater(), "Water is traversable"); near(h, water.getDeltaMovement().x, .1 * (double).85f, "Water drag"); water.discard(); h.succeed();
    }
    private static void ttl(GameTestHelper h) {
        var s = shot(h, h.absoluteVec(new Vec3(4, 80, 4))); var saved = NetherGameTests.save(h, s);
        for (int i = 0; i < 17; i++) s.tick(); h.assertTrue(!s.isRemoved(), "17 valid movement ticks");
        var copy = new GaussProjectile(TGContent.GAUSS.get(), h.getLevel()); NetherGameTests.load(h, copy, NetherGameTests.save(h, s)); s.discard(); copy.tick(); h.assertTrue(copy.isRemoved(), "Restored final tick expires at 18");
        for (String key : List.of("wrong_weapon", "unknown_weapon", "negative_age", "expired", "scale", "nan")) {
            var broken = saved.copy(); switch (key) { case "wrong_weapon" -> broken.putString("weapon", "revolver"); case "unknown_weapon" -> broken.putString("weapon", "missing");
                case "negative_age" -> broken.putInt("age", -1); case "expired" -> broken.putInt("age", 18); case "scale" -> broken.putFloat("damage_scale", -1); case "nan" -> broken.putFloat("damage_scale", Float.NaN); }
            var bad = new GaussProjectile(TGContent.GAUSS.get(), h.getLevel()); NetherGameTests.load(h, bad, broken); h.assertTrue(bad.isRemoved(), "Reject invalid save: " + key);
        } h.succeed();
    }
    private static void npc(GameTestHelper h) {
        var npc = h.spawnWithNoFreeWill(NpcContent.BANDIT.get(), new Vec3(3, 70, 4)); var t = target(h, false, new Vec3(6, 70, 4));
        npc.setNoGravity(true); npc.setItemSlot(EquipmentSlot.MAINHAND, TGContent.GUNS.get("gaussrifle").toStack());
        h.assertTrue(NpcCombat.fire(npc, t), "NPC factory supports Gauss"); var s = shots(h, npc).getFirst();
        h.assertTrue(s.shotDamage().npc(), "NPC scaling captured"); s.setPos(t.position().add(-2, .5, 0)); s.setDeltaMovement(4, 0, 0);
        float amount = s.shotDamage().againstEntity(40); s.tick(); near(h, 1000 - t.getHealth(), amount + (amount > 0 ? .01 : 0), "NPC scale applied once");
        h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()), 0, "NPC does not use player inventory"); npc.discard(); t.discard(); h.succeed();
    }
    private GaussGameTests() {}
}
