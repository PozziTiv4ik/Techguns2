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
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.armor.*;
import techguns.modern.machine.grinder.GrinderContent;
import techguns.modern.network.*;
import techguns.modern.npc.*;

final class AlienBlasterGameTests {
    private static final WeaponDefinition GUN = Weapons.definition("alienblaster");
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("alien_ten_shots_cadence_and_creative", () -> AlienBlasterGameTests::cadence);
        r.register("alien_native_partial_reload", () -> AlienBlasterGameTests::reload);
        for (String mode : List.of("ammo_removed", "stack")) r.register("alien_reload_cancel_" + mode, () -> h -> reloadCancel(h, mode));
        r.register("alien_item_codecs_and_factory", () -> AlienBlasterGameTests::codecs);
        r.register("alien_both_hands_and_launch_speed", () -> AlienBlasterGameTests::muzzle);
        r.register("alien_spawn_veto", () -> AlienBlasterGameTests::spawnVeto);
        for (String mode : List.of("bare", "vanilla", "npc", "witch", "immune", "resistance")) r.register("alien_damage_" + mode, () -> h -> damage(h, mode));
        r.register("alien_player_typed_armor", () -> AlienBlasterGameTests::playerArmor);
        r.register("alien_knockback_and_repeated_hits", () -> AlienBlasterGameTests::knockback);
        for (String mode : List.of("impact", "damage", "zero")) r.register("alien_cancel_" + mode, () -> h -> veto(h, mode));
        r.register("alien_owner_exclusion", () -> AlienBlasterGameTests::owner);
        r.register("alien_air_water_and_saved_lifetime", () -> AlienBlasterGameTests::flight);
        for (boolean npc : List.of(false, true)) r.register("alien_saved_" + (npc ? "npc" : "player"), () -> h -> saved(h, npc));
        r.register("alien_old_ghastling_save", () -> AlienBlasterGameTests::oldGhastling);
        r.register("alien_invalid_saves", () -> AlienBlasterGameTests::invalid);
        for (String mode : List.of("safe", "unsafe", "restricted")) r.register("alien_policy_" + mode, () -> h -> policy(h, mode));
        for (Direction face : Direction.values()) r.register("alien_ignite_" + face.getName(), () -> h -> fireFace(h, face));
        r.register("alien_ignite_probability_and_occupied_cell", () -> AlienBlasterGameTests::fireRules);
        r.register("alien_block_impact_veto_and_no_explosion", () -> AlienBlasterGameTests::blockImpact);
        r.register("alien_npc_single_shot_cycle", () -> AlienBlasterGameTests::npc);
        r.register("alien_native_flight", () -> AlienBlasterGameTests::nativeFlight);
        r.register("alien_no_invented_recipes", () -> AlienBlasterGameTests::recipes);
    }
    private static void near(GameTestHelper h, double actual, double expected, String message) { h.assertTrue(Math.abs(actual - expected) < .0002, message + ": " + actual + " != " + expected); }
    private static Player player(GameTestHelper h, int rounds) {
        var p = WeaponGameTests.player(h); var stack = TGContent.GUNS.get(GUN.id()).toStack(); stack.set(TGContent.ROUNDS.get(), rounds);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack); return p;
    }
    private static List<AlienBlasterProjectile> shots(GameTestHelper h, Entity owner) { return h.getLevel().getEntitiesOfClass(AlienBlasterProjectile.class, owner.getBoundingBox().inflate(5), s -> s.getOwner() == owner); }
    private static LivingEntity target(GameTestHelper h, String kind, Vec3 pos) {
        EntityType<? extends Mob> type = switch (kind) { case "npc" -> NpcContent.SUPER_MUTANT.get(); case "witch" -> EntityTypes.WITCH; case "immune" -> EntityTypes.BLAZE; default -> EntityTypes.PIG; };
        var t = h.spawnWithNoFreeWill(type, pos); t.setNoGravity(true); t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); t.setHealth(1000); return t;
    }
    private static AlienBlasterProjectile shot(GameTestHelper h, Vec3 pos, boolean unsafe) {
        var s = new AlienBlasterProjectile(TGContent.ALIEN_BLAST.get(), h.getLevel()); s.configure(GUN, unsafe);
        s.setOwner(WeaponGameTests.player(h)); s.setPos(pos); return s;
    }
    private static AlienBlasterProjectile aimed(GameTestHelper h, LivingEntity target) {
        var s = shot(h, target.position().add(-2, .5, 0), false); s.setDeltaMovement(3, 0, 0); h.getLevel().addFreshEntity(s); return s;
    }
    private static void cadence(GameTestHelper h) {
        var p = player(h, 10); var stack = p.getMainHandItem();
        for (int n = 0; n < 10; n++) {
            h.assertTrue(GunNetwork.handle(p, new GunActionPayload(false)), "Automatic shot " + n);
            h.assertValueEqual(shots(h, p).size(), 1, "One projectile per charge"); shots(h, p).forEach(Entity::discard);
            h.assertValueEqual(GunItem.rounds(stack), 9 - n, "One charge consumed");
            for (int tick = 0; tick < 8; tick++) { h.assertTrue(!GunNetwork.handle(p, new GunActionPayload(false)), "Eight-tick cadence"); p.getCooldowns().tick(); }
        }
        h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "Empty magazine"); p.getAbilities().instabuild = true; GunItem.completeReload(p, stack);
        h.assertTrue(GunItem.fire(h.getLevel(), p, stack), "Creative reload and fire"); h.assertValueEqual(GunItem.rounds(stack), 10, "Creative preserves charges");
        shots(h, p).forEach(Entity::discard); h.succeed();
    }
    private static void reload(GameTestHelper h) {
        var p = player(h, 7); var stack = p.getMainHandItem(); p.getInventory().setItem(1, TGContent.AMMO.get("energycellempty").toStack());
        h.assertTrue(!ReloadSessions.begin(p), "Empty cell cannot reload"); p.getInventory().setItem(1, TGContent.AMMO.get("energycell").toStack(2));
        h.assertTrue(ReloadSessions.begin(p), "Partial reload begins");
        for (int tick = 1; tick <= 35; tick++) { final int n = tick; h.runAfterDelay(tick, () -> {
            p.tick(); h.assertValueEqual(GunItem.rounds(stack), n == 35 ? 10 : 7, "Native R deadline");
            if (n == 35) { h.assertValueEqual(p.getInventory().countItem(TGContent.AMMO.get("energycell").get()), 1, "One cell spent");
                h.assertValueEqual(p.getInventory().countItem(TGContent.AMMO.get("energycellempty").get()), 1, "One empty cell returned"); p.discard(); h.succeed(); }
        }); }
    }
    private static void reloadCancel(GameTestHelper h, String mode) {
        var p = player(h, 0); var stack = p.getMainHandItem(); p.getInventory().setItem(1, TGContent.AMMO.get("energycell").toStack());
        h.assertTrue(ReloadSessions.begin(p), "R begins");
        if (mode.equals("stack")) p.setItemInHand(InteractionHand.MAIN_HAND, stack.copy()); else p.getInventory().setItem(1, ItemStack.EMPTY);
        for (int i = 0; i < 35; i++) ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack), 0, "Cancelled R creates no charges");
        h.assertValueEqual(p.getInventory().countItem(TGContent.AMMO.get("energycellempty").get()), 0, "No duplicate empty return");
        h.assertTrue(!ReloadSessions.active(p), "R clears"); h.succeed();
    }
    private static void codecs(GameTestHelper h) {
        var p = player(h, 7); var stack = p.getMainHandItem(); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Alien saved"));
        stack.set(TGContent.BALLISTIC_VARIANT.get(), BallisticVariant.EXPLOSIVE); stack.set(TGContent.RELOAD_TICKS.get(), 20);
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var copy = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()).getOrThrow();
        h.assertValueEqual(GunItem.rounds(copy), 7, "Charges persist"); h.assertValueEqual(copy.getHoverName(), stack.getHoverName(), "Name persists");
        h.assertTrue(!copy.has(TGContent.RELOAD_TICKS.get()), "R is transient");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try { ItemStack.STREAM_CODEC.encode(buffer, stack); h.assertTrue(ItemStack.matches(stack, ItemStack.STREAM_CODEC.decode(buffer)), "Network codec"); } finally { buffer.release(); }
        h.assertTrue(GunItem.fire(h.getLevel(), p, stack), "Injected ballistic variant cannot change Alien factory");
        h.assertValueEqual(shots(h, p).getFirst().weapon(), GUN, "Weapon profile preserved"); shots(h, p).forEach(Entity::discard); h.succeed();
    }
    private static void muzzle(GameTestHelper h) {
        var p = player(h, 10); p.setYRot(0); p.setXRot(0); var stack = p.getMainHandItem();
        for (InteractionHand hand : InteractionHand.values()) {
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); p.setItemInHand(hand, stack); p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(stack));
            h.assertTrue(GunItem.fire(h.getLevel(), p, stack), "Both hands fire"); var s = shots(h, p).getFirst();
            double side = (p.getMainArm() == HumanoidArm.RIGHT) != (hand == InteractionHand.OFF_HAND) ? -.16 : .16;
            near(h, s.getX(), p.getX() + side, "Legacy hand offset"); near(h, s.getY(), p.getEyeY() - .1, "Muzzle height");
            near(h, s.getYRot(), 0, "Zero angular spread"); near(h, s.getXRot(), 0, "Zero pitch spread");
            h.assertTrue(s.getDeltaMovement().length() > 1.4 && s.getDeltaMovement().length() < 1.6, "GenericProjectile retains 1.5 launch multiplier"); s.discard();
        } h.succeed();
    }
    private static void spawnVeto(GameTestHelper h) {
        var p = player(h, 10); var stack = p.getMainHandItem();
        Consumer<EntityJoinLevelEvent> veto = e -> { if (e.getEntity() instanceof AlienBlasterProjectile s && s.getOwner() == p) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(veto);
        try { h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "Spawn veto"); h.assertValueEqual(GunItem.rounds(stack), 10, "No lost charge"); h.assertTrue(!p.getCooldowns().isOnCooldown(stack), "No cooldown"); }
        finally { NeoForge.EVENT_BUS.unregister(veto); } h.succeed();
    }
    private static void damage(GameTestHelper h, String mode) {
        var t = target(h, mode, new Vec3(6, 90, 4));
        if (mode.equals("vanilla") || mode.equals("immune")) { t.getAttribute(Attributes.ARMOR).setBaseValue(20); t.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2); }
        if (mode.equals("resistance")) t.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100));
        float armor = t instanceof NpcTypedArmor npc ? npc.armorAgainst(DamageKind.FIRE) : ArmorMath.defaultArmor(DamageKind.FIRE, (float)t.getAttributeValue(Attributes.ARMOR), t.fireImmune());
        float expected = ArmorMath.afterArmor(16, armor, (float)t.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 1) * (mode.equals("witch") ? .15f : 1);
        var s = aimed(h, t); s.tick(); near(h, 1000 - t.getHealth(), expected, "FIRE armor and penetration before magic resistance");
        if (mode.equals("bare")) h.assertValueEqual(t.getRemainingFireTicks(), 60, "Three seconds of ignition");
        h.assertTrue(s.isRemoved(), "Entity impact consumes projectile"); t.discard(); h.succeed();
    }
    private static void playerArmor(GameTestHelper h) {
        var p = ArmorGameTests.player(h); ArmorGameTests.equipAll(p); var s = shot(h, p.position().add(4, 4, 0), false);
        p.hurtServer(h.getLevel(), s.shotDamage().source(h.getLevel(), AlienBlasterProjectile.DAMAGE_TYPE, s), 16);
        h.assertTrue(p.getHealth() > 984 && p.getHealth() < 1000, "Typed player FIRE absorption");
        h.assertTrue(TGArmorSystem.SLOTS.stream().anyMatch(slot -> p.getItemBySlot(slot).getDamageValue() > 0), "Armor wears"); s.discard(); h.succeed();
    }
    private static void knockback(GameTestHelper h) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); int[] calls = {0};
        Consumer<LivingIncomingDamageEvent> listener = e -> { if (e.getEntity() == t && e.getSource().getDirectEntity() instanceof AlienBlasterProjectile) calls[0]++; };
        NeoForge.EVENT_BUS.addListener(listener);
        try { aimed(h, t).tick(); h.assertTrue(t.getDeltaMovement().horizontalDistanceSqr() > 0, "Ordinary knockback"); aimed(h, t).tick();
            near(h, 1000 - t.getHealth(), 32, "Both magic hits bypass cooldown without dummy damage"); h.assertValueEqual(calls[0], 2, "Exactly one damage event per projectile"); }
        finally { NeoForge.EVENT_BUS.unregister(listener); t.discard(); } h.succeed();
    }
    private static void veto(GameTestHelper h, String mode) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var s = aimed(h, t); if (mode.equals("zero")) s.npcDamage(0);
        Consumer<ProjectileImpactEvent> impact = e -> { if (e.getProjectile() == s && mode.equals("impact")) e.setCanceled(true); };
        Consumer<LivingIncomingDamageEvent> damage = e -> { if (e.getSource().getDirectEntity() == s && mode.equals("damage")) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(impact); NeoForge.EVENT_BUS.addListener(damage);
        try { s.tick(); near(h, t.getHealth(), 1000, "No vetoed damage"); h.assertTrue(!t.isOnFire(), "No vetoed ignition"); h.assertValueEqual(s.isRemoved(), !mode.equals("impact"), "Impact veto continues flight"); }
        finally { NeoForge.EVENT_BUS.unregister(impact); NeoForge.EVENT_BUS.unregister(damage); s.discard(); t.discard(); } h.succeed();
    }
    private static void owner(GameTestHelper h) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var s = aimed(h, t); s.setOwner(t); var saved = NetherGameTests.save(h, s); saved.putBoolean("LeftOwner", true); NetherGameTests.load(h, s, saved);
        s.tick(); near(h, t.getHealth(), 1000, "Shooter excluded after leaving launch bounds"); h.assertTrue(!t.isOnFire() && !s.isRemoved(), "No self-ignition"); s.discard(); t.discard(); h.succeed();
    }
    private static void flight(GameTestHelper h) {
        var s = shot(h, h.absoluteVec(new Vec3(4, 90, 4)), false); s.setDeltaMovement(0, .1, 0); s.tick();
        near(h, s.getDeltaMovement().y, .1 * (double).99f, "Air drag without gravity");
        for (int i = 1; i < 39; i++) s.tick(); h.assertTrue(!s.isRemoved(), "39 movements survive");
        var saved = NetherGameTests.save(h, s); s.discard(); var copy = new AlienBlasterProjectile(TGContent.ALIEN_BLAST.get(), h.getLevel()); NetherGameTests.load(h, copy, saved);
        var before = copy.position(); var velocity = copy.getDeltaMovement(); copy.tick(); h.assertTrue(copy.isRemoved(), "40th movement expires"); near(h, copy.getY(), before.y + velocity.y, "Expiration follows movement");
        h.setBlock(4, 2, 4, Blocks.WATER); var wet = shot(h, h.absoluteVec(new Vec3(4.5, 2.2, 4.5)), false); wet.setDeltaMovement(.1, 0, 0); wet.igniteForSeconds(10); wet.tick();
        h.assertTrue(wet.isInWater() && !wet.isRemoved() && !wet.isOnFire(), "Water extinguishes but preserves flight"); near(h, wet.getDeltaMovement().x, .1 * (double).85f, "Water drag"); wet.discard(); h.succeed();
    }
    private static void saved(GameTestHelper h, boolean npc) {
        var owner = target(h, "npc", new Vec3(3, 90, 4)); var t = target(h, "bare", new Vec3(6, 90, 4)); var s = shot(h, t.position().add(-2, .5, 0), !npc);
        s.setOwner(owner); if (npc) s.npcDamage(.5f); s.tick(); s.setDeltaMovement(3, 0, 0); var saved = NetherGameTests.save(h, s); s.discard();
        var copy = new AlienBlasterProjectile(TGContent.ALIEN_BLAST.get(), h.getLevel()); NetherGameTests.load(h, copy, saved);
        h.assertValueEqual(copy.weapon(), GUN, "Gun profile survives save"); h.assertValueEqual(copy.age(), 1, "TTL preserved"); h.assertValueEqual(copy.getOwner(), owner, "Owner UUID resolves");
        h.assertValueEqual(copy.damagesBlocks(), !npc, "Launch-time block policy survives"); h.assertValueEqual(copy.shotDamage(), npc ? new ShotDamage(true, .5f) : ShotDamage.PLAYER, "NPC multiplier survives");
        h.getLevel().addFreshEntity(copy); copy.tick(); near(h, 1000 - t.getHealth(), copy.shotDamage().againstEntity(16), "Reloaded projectile hits with its profile");
        owner.discard(); t.discard(); h.succeed();
    }
    private static void oldGhastling(GameTestHelper h) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var s = new AlienBlasterProjectile(TGContent.ALIEN_BLAST.get(), h.getLevel());
        s.setPos(t.position().add(-2, .5, 0)); s.setDeltaMovement(3, 0, 0); var saved = NetherGameTests.save(h, s);
        for (String key : List.of("profile", "npc_shot", "damage_scale", "block_damage")) saved.remove(key); saved.putInt("age", 199);
        var copy = new AlienBlasterProjectile(TGContent.ALIEN_BLAST.get(), h.getLevel()); NetherGameTests.load(h, copy, saved);
        h.assertTrue(copy.ghastlingProfile() && !copy.damagesBlocks(), "Old age-only saves retain Ghastling"); h.assertValueEqual(copy.age(), 199, "Old TTL is not reset");
        h.getLevel().addFreshEntity(copy); copy.tick(); near(h, 1000 - t.getHealth(), new ShotDamage(true, 1).againstEntity(6), "Final old flight still deals six damage");
        h.assertTrue(copy.isRemoved(), "Old projectile expires"); t.discard(); h.succeed();
    }
    private static void invalid(GameTestHelper h) {
        var s = shot(h, h.absoluteVec(new Vec3(4, 90, 4)), false); var valid = NetherGameTests.save(h, s); s.discard();
        for (String mode : List.of("unknown", "wrong_family", "negative_age", "expired", "nan", "scale", "ghastling_blocks")) {
            var bad = valid.copy(); switch (mode) {
                case "unknown" -> bad.putString("profile", "missing"); case "wrong_family" -> bad.putString("profile", "blasterrifle");
                case "negative_age" -> bad.putInt("age", -1); case "expired" -> bad.putInt("age", 40);
                case "nan" -> bad.putFloat("damage_scale", Float.NaN); case "scale" -> bad.putFloat("damage_scale", 2);
                case "ghastling_blocks" -> { bad.putString("profile", "ghastling"); bad.putBoolean("block_damage", true); }
            }
            var copy = new AlienBlasterProjectile(TGContent.ALIEN_BLAST.get(), h.getLevel()); NetherGameTests.load(h, copy, bad); h.assertTrue(copy.isRemoved(), "Reject malformed state " + mode);
        } h.succeed();
    }
    private static void policy(GameTestHelper h, String mode) {
        var p = player(h, 10); boolean old = SafeMode.OP_ONLY.get(); p.setData(SafeMode.SAFE, false);
        try {
            SafeMode.OP_ONLY.set(mode.equals("restricted")); if (mode.equals("safe")) h.assertTrue(SafeMode.toggle(p), "B toggles safe mode");
            h.assertTrue(GunItem.fire(h.getLevel(), p, p.getMainHandItem()), "B only governs terrain"); var s = shots(h, p).getFirst();
            h.assertValueEqual(s.damagesBlocks(), mode.equals("unsafe"), "Server policy captured at launch"); shots(h, p).forEach(Entity::discard);
        } finally { SafeMode.OP_ONLY.set(old); } h.succeed();
    }
    private static void seed(AlienBlasterProjectile s, boolean ignite) {
        for (long seed = 0; seed < 100000; seed++) if (AlienBlasterRules.ignites(RandomSource.create(seed).nextDouble()) == ignite) { s.getRandom().setSeed(seed); return; }
        throw new IllegalStateException("No deterministic seed");
    }
    private static void fireFace(GameTestHelper h, Direction face) {
        var pos = h.absolutePos(new BlockPos(4, 4, 4)); var dest = pos.relative(face);
        h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()); h.getLevel().setBlockAndUpdate(dest.below(), Blocks.STONE.defaultBlockState()); h.getLevel().setBlockAndUpdate(dest, Blocks.AIR.defaultBlockState());
        var s = shot(h, Vec3.atCenterOf(pos).add(2, 2, 2), true); seed(s, true);
        h.assertTrue(s.igniteBlock(new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)), "Fire on hit face");
        h.assertTrue(h.getLevel().getBlockState(dest).is(Blocks.FIRE), "Correct neighbor ignites"); s.discard(); h.getLevel().setBlockAndUpdate(dest, Blocks.AIR.defaultBlockState()); h.succeed();
    }
    private static void fireRules(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(4, 3, 4)); h.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false); var s = shot(h, Vec3.atCenterOf(pos).add(2, 2, 2), false);
        seed(s, true); h.assertTrue(!s.igniteBlock(hit), "Safe mode suppresses winning roll");
        s.configure(GUN, true); seed(s, false); h.assertTrue(!s.igniteBlock(hit), "Losing roll cannot ignite");
        h.getLevel().setBlockAndUpdate(pos.above(), Blocks.TORCH.defaultBlockState()); seed(s, true); h.assertTrue(!s.igniteBlock(hit), "Replaceable non-air cell retained");
        h.assertTrue(h.getLevel().getBlockState(pos.above()).is(Blocks.TORCH), "No block replacement"); s.discard(); h.succeed();
    }
    private static void blockImpact(GameTestHelper h) {
        var wall = h.absolutePos(new BlockPos(5, 3, 4)); var dest = wall.west(); h.getLevel().setBlockAndUpdate(wall, Blocks.OAK_PLANKS.defaultBlockState()); h.getLevel().setBlockAndUpdate(dest.below(), Blocks.STONE.defaultBlockState());
        var t = target(h, "bare", new Vec3(6, 3, 4.5));
        for (boolean cancel : List.of(true, false)) {
            h.getLevel().setBlockAndUpdate(dest, Blocks.AIR.defaultBlockState()); var s = shot(h, Vec3.atCenterOf(dest), true); s.setDeltaMovement(3, 0, 0); seed(s, true);
            Consumer<ProjectileImpactEvent> listener = e -> { if (e.getProjectile() == s && cancel) e.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(listener);
            try { s.tick(); h.assertValueEqual(h.getLevel().getBlockState(dest).is(Blocks.FIRE), !cancel, "Impact veto controls ignition"); h.assertValueEqual(s.isRemoved(), !cancel, "Veto permits flight");
                h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.OAK_PLANKS), "Impact does not destroy wall"); near(h, t.getHealth(), 1000, "No area explosion through wall"); }
            finally { NeoForge.EVENT_BUS.unregister(listener); s.discard(); }
        } h.getLevel().setBlockAndUpdate(dest, Blocks.AIR.defaultBlockState()); t.discard(); h.succeed();
    }
    private static void npc(GameTestHelper h) {
        var npc = h.spawnWithNoFreeWill(NpcContent.BANDIT.get(), new Vec3(3, 90, 4)); var t = target(h, "bare", new Vec3(3, 90, 16));
        npc.setNoGravity(true); npc.setItemSlot(EquipmentSlot.MAINHAND, TGContent.GUNS.get(GUN.id()).toStack()); npc.setTarget(t);
        var goal = new NpcRangedGoal(npc); int[] clock = {0}; var ticks = new ArrayList<Integer>(); var fired = new ArrayList<AlienBlasterProjectile>();
        Consumer<EntityJoinLevelEvent> listener = e -> { if (e.getEntity() instanceof AlienBlasterProjectile s && s.getOwner() == npc) { ticks.add(clock[0]); fired.add(s); } };
        NeoForge.EVENT_BUS.addListener(listener);
        try { h.assertTrue(goal.canUse(), "Alien activates ranged goal"); for (; clock[0] <= 52; clock[0]++) goal.tick();
            h.assertValueEqual(ticks, List.of(26, 52), "Distance interval without burst"); h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()), 0, "NPC does not consume player charges");
            var s = fired.getFirst(); h.assertTrue(s.shotDamage().npc() && !s.damagesBlocks(), "Armed NPC policy differs from Ghastling and player");
            s.setPos(t.position().add(-2, .5, 0)); s.setDeltaMovement(3, 0, 0); s.tick(); near(h, 1000 - t.getHealth(), s.shotDamage().againstEntity(16), "NPC damage factor once");
        } finally { NeoForge.EVENT_BUS.unregister(listener); fired.forEach(Entity::discard); npc.discard(); t.discard(); } h.succeed();
    }
    private static void nativeFlight(GameTestHelper h) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var s = shot(h, t.position().add(-4, .5, 0), false); s.setDeltaMovement(1.5, 0, 0); h.getLevel().addFreshEntity(s);
        h.runAfterDelay(1, () -> { near(h, t.getHealth(), 1000, "Not an instant ray"); h.assertTrue(!s.isRemoved(), "Still flying"); });
        h.runAfterDelay(5, () -> { near(h, 1000 - t.getHealth(), 16, "Native delayed impact"); h.assertTrue(t.isOnFire() && s.isRemoved(), "Successful hit ignites and consumes projectile"); t.discard(); h.succeed(); });
    }
    private static void recipes(GameTestHelper h) {
        var manager = h.getLevel().getServer().getRecipeManager();
        for (String id : List.of("alienblaster", "alienblaster_alt")) h.assertTrue(manager.byKey(ResourceKey.create(Registries.RECIPE, TGContent.id(id))).isEmpty(), "No invented crafting recipe");
        h.assertTrue(manager.getRecipeFor(GrinderContent.RECIPE.get(), new SingleRecipeInput(TGContent.GUNS.get(GUN.id()).toStack()), h.getLevel()).isEmpty(), "No invented Grinder output"); h.succeed();
    }
    private AlienBlasterGameTests() {}
}
