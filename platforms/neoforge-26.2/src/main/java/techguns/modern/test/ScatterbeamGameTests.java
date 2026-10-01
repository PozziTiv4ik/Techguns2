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
import net.minecraft.world.item.crafting.SingleRecipeInput;
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
import techguns.modern.machine.charging.*;
import techguns.modern.machine.grinder.GrinderContent;
import techguns.modern.network.*;
import techguns.modern.npc.*;

final class ScatterbeamGameTests {
    private static final WeaponDefinition GUN = Weapons.definition("scatterbeamrifle");
    private static final String CELL = "energycell", EMPTY = "energycellempty";
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for (int rounds : new int[]{0, 1, 20, 39}) r.register("scatterbeam_reload_" + rounds, () -> h -> reload(h, rounds));
        for (String cancel : List.of("cell", "stack", "death", "use")) r.register("scatterbeam_cancel_" + cancel, () -> h -> cancel(h, cancel));
        r.register("scatterbeam_native_reload_timer", () -> ScatterbeamGameTests::nativeTimer);
        r.register("scatterbeam_overflow_and_creative", () -> ScatterbeamGameTests::overflow);
        r.register("scatterbeam_item_codecs_and_variant_guard", () -> ScatterbeamGameTests::itemCodecs);
        r.register("scatterbeam_forty_volleys_and_cadence", () -> ScatterbeamGameTests::cadence);
        r.register("scatterbeam_aim_and_hands", () -> ScatterbeamGameTests::muzzle);
        for (boolean first : List.of(false, true)) r.register("scatterbeam_spawn_veto_" + first, () -> h -> spawnVeto(h, first));
        r.register("scatterbeam_no_invented_recipes", () -> ScatterbeamGameTests::recipesAbsent);
        r.register("scatterbeam_charging_reload_hit_chain", () -> ScatterbeamGameTests::production);
        for (String armor : List.of("bare", "vanilla", "npc", "witch")) r.register("scatterbeam_hit_" + armor, () -> h -> hit(h, armor));
        r.register("scatterbeam_typed_player_armor", () -> ScatterbeamGameTests::playerArmor);
        r.register("scatterbeam_five_hits_and_single_impulse", () -> ScatterbeamGameTests::volleyDamage);
        for (String veto : List.of("impact", "damage", "zero")) r.register("scatterbeam_veto_" + veto, () -> h -> veto(h, veto));
        for (boolean unsafe : List.of(false, true)) r.register("scatterbeam_wall_" + unsafe, () -> h -> wall(h, unsafe));
        r.register("scatterbeam_first_target_stops_pellet", () -> ScatterbeamGameTests::firstTarget);
        r.register("scatterbeam_native_flight_is_not_hitscan", () -> ScatterbeamGameTests::nativeFlight);
        r.register("scatterbeam_water_and_chunk_save", () -> ScatterbeamGameTests::flight);
        r.register("scatterbeam_ttl_and_invalid_saves", () -> ScatterbeamGameTests::ttl);
        r.register("scatterbeam_npc_five_pellets_and_scale", () -> ScatterbeamGameTests::npc);
    }
    private static ItemStack item(String id) { return TGContent.AMMO.get(id).toStack(); }
    private static Player player(GameTestHelper h, int rounds) {
        var p = WeaponGameTests.player(h); var gun = TGContent.GUNS.get(GUN.id()).toStack(); gun.set(TGContent.ROUNDS.get(), rounds);
        p.setItemInHand(InteractionHand.MAIN_HAND, gun); return p;
    }
    private static void supply(Player p) { p.getInventory().setItem(1, item(CELL).copyWithCount(2)); }
    private static int count(Player p, String id) { return p.getInventory().countItem(item(id).getItem()); }
    private static void near(GameTestHelper h, double actual, double expected, String message) { h.assertTrue(Math.abs(actual - expected) < .002, message + ": " + actual + " != " + expected); }
    private static List<BlasterProjectile> shots(GameTestHelper h, Entity owner) { return h.getLevel().getEntitiesOfClass(BlasterProjectile.class, owner.getBoundingBox().inflate(5), s -> s.getOwner() == owner); }
    private static void reload(GameTestHelper h, int rounds) {
        var p = player(h, rounds); var stack = p.getMainHandItem();
        h.assertTrue(!ReloadSessions.begin(p), "Empty inventory cannot reload"); p.getInventory().setItem(1, item(EMPTY));
        h.assertTrue(!ReloadSessions.begin(p), "Empty cell is not ammunition"); supply(p);
        h.assertTrue(GunNetwork.handle(p, new GunActionPayload(true)), "R begins");
        for (int n = 0; n < 44; n++) ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack), rounds, "No early refill"); h.assertValueEqual(count(p, CELL), 2, "No early consumption");
        h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "No fire while reloading");
        p.getInventory().setItem(8, p.getInventory().removeItemNoUpdate(1)); ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack), 40, "Forty charges"); h.assertValueEqual(count(p, CELL), 1, "One relocated cell consumed");
        h.assertValueEqual(count(p, EMPTY), 1, "One empty cell, no residual energy item");
        h.assertTrue(!ReloadSessions.begin(p), "Full weapon cannot reload"); GunItem.completeReload(p, stack);
        h.assertValueEqual(count(p, EMPTY), 1, "Full reload creates no returns"); h.succeed();
    }
    private static void cancel(GameTestHelper h, String reason) {
        var p = player(h, 20); var stack = p.getMainHandItem(); supply(p); h.assertTrue(ReloadSessions.begin(p), "Start R");
        if (reason.equals("cell")) p.getInventory().setItem(1, item(EMPTY));
        if (reason.equals("stack")) p.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        if (reason.equals("death")) p.setHealth(0);
        if (reason.equals("use")) { p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BOW)); p.startUsingItem(InteractionHand.OFF_HAND); }
        for (int n = 0; n < 45; n++) ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack), 20, "Cancelled charges preserved");
        h.assertValueEqual(count(p, CELL), reason.equals("cell") ? 0 : 2, "No cancelled consumption");
        h.assertValueEqual(count(p, EMPTY), reason.equals("cell") ? 1 : 0, "No duplicated empty cell");
        h.assertTrue(!ReloadSessions.active(p) && !stack.has(TGContent.RELOAD_TICKS.get()), "Session cleared"); h.succeed();
    }
    private static void nativeTimer(GameTestHelper h) {
        var p = player(h, 0); supply(p); h.assertTrue(ReloadSessions.begin(p), "Native R starts");
        for (int tick = 1; tick <= 45; tick++) {
            final int n = tick;
            h.runAfterDelay(tick, () -> {
                p.tick(); h.assertValueEqual(GunItem.rounds(p.getMainHandItem()), n == 45 ? 40 : 0, "Native player event deadline");
                if (n == 45) { h.assertValueEqual(count(p, EMPTY), 1, "One completed reload"); p.discard(); h.succeed(); }
            });
        }
    }
    private static void overflow(GameTestHelper h) {
        var p = player(h, 39); for (int i = 1; i < p.getInventory().getContainerSize(); i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64)); supply(p);
        GunItem.completeReload(p, p.getMainHandItem());
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3), e -> e.getItem().is(item(EMPTY).getItem()));
        h.assertValueEqual(drops.stream().mapToInt(e -> e.getItem().getCount()).sum(), 1, "One overflow empty cell"); drops.forEach(Entity::discard);
        var creative = player(h, 0); creative.getAbilities().instabuild = true;
        h.assertTrue(!GunItem.fire(h.getLevel(), creative, creative.getMainHandItem()), "Creative still requires loaded charges");
        h.assertTrue(ReloadSessions.begin(creative), "Creative R without cell"); for (int i = 0; i < 45; i++) ReloadSessions.tick(creative);
        h.assertTrue(GunItem.fire(h.getLevel(), creative, creative.getMainHandItem()), "Creative fires");
        h.assertValueEqual(GunItem.rounds(creative.getMainHandItem()), 40, "Original Creative preserves charges");
        h.assertValueEqual(shots(h, creative).size(), 5, "Creative full volley"); h.assertValueEqual(count(creative, EMPTY), 0, "No Creative returns");
        shots(h, creative).forEach(Entity::discard); h.succeed();
    }
    private static void itemCodecs(GameTestHelper h) {
        var p = player(h, 5); var stack = p.getMainHandItem(); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Scatterbeam saved"));
        stack.set(TGContent.BALLISTIC_VARIANT.get(), BallisticVariant.EXPLOSIVE); stack.set(TGContent.RELOAD_TICKS.get(), 22);
        h.assertValueEqual(BallisticAmmo.variant(stack), BallisticVariant.DEFAULT, "Injected ballistic variant ignored");
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var loaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()).getOrThrow();
        h.assertValueEqual(GunItem.rounds(loaded), 5, "Charges saved"); h.assertValueEqual(loaded.getHoverName(), stack.getHoverName(), "Name saved");
        h.assertTrue(!loaded.has(TGContent.RELOAD_TICKS.get()), "R does not persist after reconnect");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try { ItemStack.STREAM_CODEC.encode(buffer, stack); h.assertTrue(ItemStack.matches(stack, ItemStack.STREAM_CODEC.decode(buffer)), "Components synchronize"); }
        finally { buffer.release(); }
        h.assertTrue(GunItem.fire(h.getLevel(), p, stack), "Injected variant still fires Blaster");
        h.assertValueEqual(shots(h, p).size(), 5, "Correct factory"); shots(h, p).forEach(Entity::discard); h.succeed();
    }
    private static void cadence(GameTestHelper h) {
        var p = player(h, 40); var stack = p.getMainHandItem();
        for (int volley = 0; volley < 40; volley++) {
            h.assertTrue(GunNetwork.handle(p, new GunActionPayload(false)), "Volley " + volley);
            h.assertValueEqual(shots(h, p).size(), 5, "Five accepted projectiles"); shots(h, p).forEach(Entity::discard);
            h.assertValueEqual(GunItem.rounds(stack), 39 - volley, "One charge per volley");
            for (int tick = 0; tick < 7; tick++) { h.assertTrue(!GunNetwork.handle(p, new GunActionPayload(false)), "Spam cannot bypass seven ticks"); p.getCooldowns().tick(); }
        }
        h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "Forty volleys exhaust the cell"); h.succeed();
    }
    private static void muzzle(GameTestHelper h) {
        var p = player(h, 40); p.setYRot(0); p.setXRot(0); h.assertTrue(AimSessions.set(p, true), "Toggle aim");
        h.assertTrue(GunItem.fire(h.getLevel(), p, p.getMainHandItem()), "Aimed volley");
        for (var s : shots(h, p)) {
            h.assertTrue(s.getX() < p.getX(), "Aimed Scatterbeam remains side-fired");
            near(h, s.getY(), p.getEyeY() - .1, "Muzzle height");
            h.assertTrue(Math.abs(s.getYRot()) <= 4.501 && Math.abs(s.getXRot()) <= 4.501, "Aim scales both dispersions by .75");
            h.assertTrue(s.getDeltaMovement().length() > 2.7 && s.getDeltaMovement().length() < 3.3, "Legacy launch multiplier 1.5, final speed 2"); s.discard();
        }
        for (int i = 0; i < 7; i++) p.getCooldowns().tick(); AimSessions.cancel(p);
        var stack = p.getMainHandItem(); p.setMainArm(HumanoidArm.LEFT); p.setItemInHand(InteractionHand.OFF_HAND, stack); p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(GunItem.fire(h.getLevel(), p, stack), "Left-dominant offhand volley");
        for (var s : shots(h, p)) { h.assertTrue(s.getX() < p.getX(), "Offhand uses right muzzle"); s.discard(); } h.succeed();
    }
    private static void spawnVeto(GameTestHelper h, boolean first) {
        var p = player(h, 40); var stack = p.getMainHandItem(); int[] attempts = {0};
        Consumer<EntityJoinLevelEvent> veto = e -> { if (e.getEntity() instanceof BlasterProjectile s && s.getOwner() == p && ++attempts[0] == (first ? 1 : 3)) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            h.assertValueEqual(GunItem.fire(h.getLevel(), p, stack), !first, "First veto rejects volley; later veto suppresses one pellet");
            h.assertValueEqual(GunItem.rounds(stack), first ? 40 : 39, "No refund after accepted pellets");
            h.assertValueEqual(shots(h, p).size(), first ? 0 : 4, "Only accepted pellets enter the world");
            h.assertValueEqual(p.getCooldowns().isOnCooldown(stack), !first, "Cooldown follows accepted volley");
        } finally { NeoForge.EVENT_BUS.unregister(veto); shots(h, p).forEach(Entity::discard); } h.succeed();
    }
    private static void recipesAbsent(GameTestHelper h) {
        var manager = h.getLevel().getServer().getRecipeManager();
        for (String name : List.of("scatterbeamrifle", "scatterbeamrifle_alt")) h.assertTrue(manager.byKey(ResourceKey.create(Registries.RECIPE, TGContent.id(name))).isEmpty(), "No source crafting recipe: " + name);
        h.assertTrue(manager.getRecipeFor(GrinderContent.RECIPE.get(), new SingleRecipeInput(TGContent.GUNS.get(GUN.id()).toStack()), h.getLevel()).isEmpty(), "No source salvage recipe"); h.succeed();
    }
    private static void production(GameTestHelper h) {
        var pos = new BlockPos(5, 2, 4); h.setBlock(pos, ChargingStationContent.BLOCK.get());
        var charger = h.getBlockEntity(pos, ChargingStationBlockEntity.class); charger.setItem(0, item(EMPTY));
        try (var tx = Transaction.openRoot()) { h.assertValueEqual(charger.energy().insert(49600, tx), 49600, "Source truncated charge cost"); tx.commit(); }
        for (int i = 0; i < 120; i++) ChargingStationBlockEntity.tick(h.getLevel(), charger.getBlockPos(), charger.getBlockState(), charger);
        h.assertTrue(charger.getItem(1).is(item(CELL).getItem()), "Cell actually charged");
        h.assertValueEqual(charger.energy().getAmountAsLong(), 0L, "Energy paid");
        var p = player(h, 0); p.getInventory().setItem(1, charger.removeItem(1, 1));
        h.assertTrue(ReloadSessions.begin(p), "Produced cell reloads"); for (int i = 0; i < 45; i++) ReloadSessions.tick(p);
        h.assertTrue(GunItem.fire(h.getLevel(), p, p.getMainHandItem()), "Produced cell fires");
        var target = target(h, "bare", new Vec3(6, 70, 4));
        for (var s : shots(h, p)) { aim(s, target); s.tick(); }
        near(h, target.getHealth(), 969.99, "All five manufactured energy charges hit"); target.discard();
        h.assertValueEqual(count(p, EMPTY), 1, "Empty case returned"); h.succeed();
    }
    private static LivingEntity target(GameTestHelper h, String kind, Vec3 pos) {
        EntityType<? extends Mob> type = kind.equals("npc") ? NpcContent.SUPER_MUTANT.get() : kind.equals("witch") ? EntityTypes.WITCH : EntityTypes.PIG;
        var t = h.spawnWithNoFreeWill(type, pos); t.setNoGravity(true);
        t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); t.setHealth(1000); return t;
    }
    private static BlasterProjectile shot(GameTestHelper h, Vec3 pos) {
        var s = new BlasterProjectile(TGContent.BLASTER.get(), h.getLevel()); s.configure(GUN); s.setOwner(WeaponGameTests.player(h)); s.setPos(pos); h.getLevel().addFreshEntity(s); return s;
    }
    private static void aim(BlasterProjectile s, Entity target) { s.setPos(target.position().add(-2, .5, 0)); s.setDeltaMovement(3, 0, 0); }
    private static BlasterProjectile aimed(GameTestHelper h, Entity target) { var s = shot(h, target.position().add(-2, .5, 0)); aim(s, target); return s; }
    private static void hit(GameTestHelper h, String kind) {
        var t = target(h, kind, new Vec3(6, 70, 4));
        if (kind.equals("vanilla")) { t.getAttribute(Attributes.ARMOR).setBaseValue(20); t.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2); }
        float armor = t instanceof NpcTypedArmor npc ? npc.armorAgainst(DamageKind.ENERGY) : (float)t.getAttributeValue(Attributes.ARMOR) * .5f;
        float physical = t instanceof NpcTypedArmor npc ? npc.armorAgainst(DamageKind.PHYSICAL) : (float)t.getAttributeValue(Attributes.ARMOR);
        float toughness = (float)t.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        var s = aimed(h, t); s.tick();
        near(h, 1000 - t.getHealth(), ArmorMath.afterArmor(6, armor, toughness, 0) * (kind.equals("witch") ? .15f : 1) + ArmorMath.afterArmor(.01f, physical, toughness, 0), "Energy armor/magic and separate physical impulse");
        h.assertTrue(s.isRemoved() && !t.isOnFire(), "Impact consumes pellet without burning"); t.discard(); h.succeed();
    }
    private static void playerArmor(GameTestHelper h) {
        var p = ArmorGameTests.player(h); ArmorGameTests.equipAll(p);
        var s = shot(h, p.position().add(4, 4, 0)); var source = s.shotDamage().source(h.getLevel(), BlasterProjectile.DAMAGE_TYPE, s);
        h.assertTrue(source.is(net.neoforged.neoforge.common.Tags.DamageTypes.IS_MAGIC) && source.is(DamageTypeTags.BYPASSES_COOLDOWN) && !source.is(DamageTypeTags.BYPASSES_ARMOR), "Magic keeps typed armor");
        p.hurtServer(h.getLevel(), source, 6);
        h.assertTrue(p.getHealth() < 1000 && p.getHealth() > 994, "Typed player armor absorbs actual ENERGY hit");
        h.assertTrue(TGArmorSystem.SLOTS.stream().anyMatch(slot -> p.getItemBySlot(slot).getDamageValue() > 0), "Armor durability spent"); s.discard(); h.succeed();
    }
    private static void volleyDamage(GameTestHelper h) {
        var t = target(h, "bare", new Vec3(6, 70, 4)); var p = player(h, 40); int[] impulses = {0};
        Consumer<LivingKnockBackEvent> listener = e -> { if (e.getEntity() == t) impulses[0]++; }; NeoForge.EVENT_BUS.addListener(listener);
        try {
            h.assertTrue(GunItem.fire(h.getLevel(), p, p.getMainHandItem()), "Real volley");
            for (var s : shots(h, p)) { aim(s, t); s.tick(); s.tick(); }
            near(h, 1000 - t.getHealth(), 30.01, "Five hits bypass cooldown and removed pellets never hit twice");
            h.assertValueEqual(impulses[0], 1, "One ordinary impulse for all immediate hits");
        } finally { NeoForge.EVENT_BUS.unregister(listener); t.discard(); } h.succeed();
    }
    private static void veto(GameTestHelper h, String kind) {
        var t = target(h, "bare", new Vec3(6, 70, 4)); var s = aimed(h, t); if (kind.equals("zero")) s.npcDamage(0);
        Consumer<ProjectileImpactEvent> impact = e -> { if (e.getProjectile() == s && kind.equals("impact")) e.setCanceled(true); };
        Consumer<LivingIncomingDamageEvent> damage = e -> { if (e.getSource().getDirectEntity() == s && kind.equals("damage")) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(impact); NeoForge.EVENT_BUS.addListener(damage);
        try { s.tick(); near(h, t.getHealth(), 1000, "Cancelled or zero damage preserved"); h.assertValueEqual(s.isRemoved(), !kind.equals("impact"), "Impact veto permits continued flight"); }
        finally { NeoForge.EVENT_BUS.unregister(impact); NeoForge.EVENT_BUS.unregister(damage); s.discard(); t.discard(); } h.succeed();
    }
    private static void wall(GameTestHelper h, boolean unsafe) {
        var p = player(h, 40); p.setData(SafeMode.SAFE, !unsafe); h.assertTrue(GunItem.fire(h.getLevel(), p, p.getMainHandItem()), "Both B modes permit Blaster");
        h.setBlock(5, 70, 4, Blocks.GLASS); var t = target(h, "bare", new Vec3(6, 70, 4)); int[] blasts = {0};
        Consumer<ExplosionEvent.Start> listener = e -> { if (e.getExplosion().getDirectSourceEntity() instanceof BlasterProjectile s && s.getOwner() == p) blasts[0]++; }; NeoForge.EVENT_BUS.addListener(listener);
        try {
            for (var s : shots(h, p)) { s.setPos(h.absoluteVec(new Vec3(3, 70.5, 4.5))); s.setDeltaMovement(6, 0, 0); s.tick(); h.assertTrue(s.isRemoved(), "Glass intercepts swept flight"); }
            h.assertBlockPresent(Blocks.GLASS, 5, 70, 4); near(h, t.getHealth(), 1000, "No through-wall damage"); h.assertValueEqual(blasts[0], 0, "Visual impact never explodes");
        } finally { NeoForge.EVENT_BUS.unregister(listener); t.discard(); } h.succeed();
    }
    private static void firstTarget(GameTestHelper h) {
        var first = target(h, "bare", new Vec3(5, 70, 4)); var next = target(h, "bare", new Vec3(7, 70, 4));
        var s = aimed(h, first); s.setDeltaMovement(6, 0, 0); s.tick(); s.tick();
        near(h, first.getHealth(), 993.99, "First target hit once"); near(h, next.getHealth(), 1000, "No entity piercing"); first.discard(); next.discard(); h.succeed();
    }
    private static void nativeFlight(GameTestHelper h) {
        var target = target(h, "bare", new Vec3(6, 70, 4)); var s = shot(h, target.position().add(-4, .5, 0)); s.setDeltaMovement(1.5, 0, 0);
        near(h, target.getHealth(), 1000, "No hitscan at spawn");
        h.runAfterDelay(1, () -> { near(h, target.getHealth(), 1000, "Target beyond first movement tick"); h.assertTrue(!s.isRemoved(), "Projectile still flying"); });
        h.runAfterDelay(5, () -> { near(h, target.getHealth(), 993.99, "Native world ticks cause delayed impact"); h.assertTrue(s.isRemoved(), "Impact removed native entity"); target.discard(); h.succeed(); });
    }
    private static void flight(GameTestHelper h) {
        var start = h.absoluteVec(new Vec3(4, 80, 4)); start = new Vec3(Math.floor(start.x / 16) * 16 + 15.9, start.y, start.z);
        var owner = target(h, "bare", new Vec3(4, 75, 4));
        var s = shot(h, start); s.setOwner(owner); s.npcDamage(.5f); s.setDeltaMovement(.4, .2, 0); var chunk = s.chunkPosition(); s.tick();
        near(h, s.getX(), start.x + .4, "Movement across chunk boundary"); h.assertTrue(!s.chunkPosition().equals(chunk), "Actually crossed a chunk");
        near(h, s.getDeltaMovement().y, .2 * (double).99f, "No gravity");
        var copy = new BlasterProjectile(TGContent.BLASTER.get(), h.getLevel()); NetherGameTests.load(h, copy, NetherGameTests.save(h, s));
        h.assertValueEqual(copy.age(), 1, "Age survives save"); h.assertValueEqual(copy.weapon(), GUN, "Factory survives save");
        h.assertValueEqual(copy.shotDamage(), new ShotDamage(true, .5f), "NPC profile survives save"); h.assertValueEqual(copy.getDeltaMovement(), s.getDeltaMovement(), "Velocity survives save");
        h.assertValueEqual(copy.getOwner(), s.getOwner(), "Owner survives save"); s.discard(); copy.discard(); owner.discard();
        h.setBlock(4, 2, 4, Blocks.WATER); var water = shot(h, h.absoluteVec(new Vec3(4.5, 2.2, 4.5))); water.setDeltaMovement(.1, 0, 0); water.setRemainingFireTicks(50); water.tick();
        h.assertTrue(!water.isRemoved() && water.isInWater(), "Water is traversable"); near(h, water.getDeltaMovement().x, .1 * (double).99f, "Water retains air drag");
        h.assertTrue(!water.isOnFire(), "Wet projectile extinguishes"); water.discard(); h.succeed();
    }
    private static void ttl(GameTestHelper h) {
        var s = shot(h, h.absoluteVec(new Vec3(4, 80, 4))); var saved = NetherGameTests.save(h, s);
        for (int i = 0; i < 14; i++) s.tick(); h.assertTrue(!s.isRemoved(), "Fourteen movement ticks");
        var copy = new BlasterProjectile(TGContent.BLASTER.get(), h.getLevel()); NetherGameTests.load(h, copy, NetherGameTests.save(h, s)); s.discard();
        var t = target(h, "bare", new Vec3(6, 70, 4)); aim(copy, t); copy.tick(); near(h, t.getHealth(), 993.99, "Final fifteenth tick can still hit"); t.discard();
        var expiring = new BlasterProjectile(TGContent.BLASTER.get(), h.getLevel()); var finalTick = saved.copy(); finalTick.putInt("age", 14); NetherGameTests.load(h, expiring, finalTick);
        expiring.setDeltaMovement(.1, 0, 0); var before = expiring.position(); expiring.tick(); h.assertTrue(expiring.isRemoved(), "Expires at ceil(30/2), not 30 or 20"); near(h, expiring.getX(), before.x + .1, "Last tick moves before expiry");
        for (String key : List.of("wrong_weapon", "unknown_weapon", "negative_age", "expired", "scale", "nan")) {
            var broken = saved.copy(); switch (key) { case "wrong_weapon" -> broken.putString("weapon", "lasergun"); case "unknown_weapon" -> broken.putString("weapon", "missing");
                case "negative_age" -> broken.putInt("age", -1); case "expired" -> broken.putInt("age", 15); case "scale" -> broken.putFloat("damage_scale", -1); case "nan" -> broken.putFloat("damage_scale", Float.NaN); }
            var bad = new BlasterProjectile(TGContent.BLASTER.get(), h.getLevel()); NetherGameTests.load(h, bad, broken); h.assertTrue(bad.isRemoved(), "Reject invalid save: " + key);
        } h.succeed();
    }
    private static void npc(GameTestHelper h) {
        var npc = h.spawnWithNoFreeWill(NpcContent.BANDIT.get(), new Vec3(3, 70, 4)); var t = target(h, "bare", new Vec3(6, 70, 4));
        npc.setNoGravity(true); npc.setItemSlot(EquipmentSlot.MAINHAND, TGContent.GUNS.get(GUN.id()).toStack());
        h.assertTrue(NpcCombat.fire(npc, t), "NPC Blaster factory"); var volley = shots(h, npc); h.assertValueEqual(volley.size(), 5, "NPC fires five pellets");
        float amount = volley.getFirst().shotDamage().againstEntity(6);
        for (var s : volley) { h.assertTrue(s.shotDamage().npc(), "NPC scaling captured"); aim(s, t); s.tick(); }
        near(h, 1000 - t.getHealth(), amount * 5 + (amount > 0 ? .01 : 0), "NPC scale applied once per pellet");
        h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()), 0, "NPC bypasses player inventory"); npc.discard(); t.discard(); h.succeed();
    }
    private ScatterbeamGameTests() {}
}
