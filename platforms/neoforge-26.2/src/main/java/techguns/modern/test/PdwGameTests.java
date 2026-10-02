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
import techguns.modern.machine.*;
import techguns.modern.machine.camo.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import techguns.modern.machine.grinder.*;
import techguns.modern.network.*;
import techguns.modern.npc.*;

final class PdwGameTests {
    private static final WeaponDefinition GUN = Weapons.definition("pdw");
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        double[] distances = {0, 17.99, 18, 18.01, 21.5, 24.99, 25, 25.01};
        for (int n = 0; n < distances.length; n++) { final double d = distances[n]; final double amount = d <= 18 ? 5 : d >= 25 ? 3 : 5 - (d - 18) * 2 / 7;
            r.register("pdw_falloff_" + n, () -> h -> falloff(h, d, amount)); }
        r.register("pdw_impact_tick_falloff", () -> PdwGameTests::preMovement);
        r.register("pdw_displacement_not_path", () -> PdwGameTests::displacement);
        r.register("pdw_saved_origin_owner_npc", () -> PdwGameTests::savedFlight);
        r.register("pdw_invalid_saves", () -> PdwGameTests::invalid);
        r.register("pdw_air_water_lifetime", () -> PdwGameTests::flight);
        r.register("pdw_last_tick_hit", () -> PdwGameTests::lastTick);
        r.register("pdw_forty_shots_cadence_creative", () -> PdwGameTests::cadence);
        for (int rounds : new int[]{0,13,14,26,27,39}) r.register("pdw_partial_reload_" + rounds, () -> h -> reload(h, rounds));
        r.register("pdw_native_reload_deadline", () -> PdwGameTests::nativeReload);
        for (boolean swap : List.of(false,true)) r.register("pdw_reload_cancel_" + swap, () -> h -> reloadCancel(h, swap));
        r.register("pdw_full_inventory_refunds", () -> PdwGameTests::overflow);
        r.register("pdw_item_codecs_camo_factory", () -> PdwGameTests::codecs);
        r.register("pdw_both_hands_launch", () -> PdwGameTests::muzzle);
        r.register("pdw_spawn_veto", () -> PdwGameTests::spawnVeto);
        for (String mode : List.of("impact","damage")) r.register("pdw_veto_" + mode, () -> h -> veto(h,mode));
        r.register("pdw_owner_exclusion", () -> PdwGameTests::owner);
        r.register("pdw_impulse_main_damage_events", () -> PdwGameTests::events);
        r.register("pdw_zero_npc_damage", () -> PdwGameTests::zero);
        for (String kind : List.of("vanilla","npc","witch")) r.register("pdw_armor_" + kind, () -> h -> armor(h,kind));
        r.register("pdw_typed_player_armor", () -> PdwGameTests::playerArmor);
        r.register("pdw_npc_four_shot_burst", () -> PdwGameTests::npc);
        r.register("pdw_native_flight", () -> PdwGameTests::nativeFlight);
        r.register("pdw_wall_and_impact_veto", () -> PdwGameTests::wall);
        for (boolean empty : List.of(false,true)) r.register("pdw_craft_" + empty, () -> h -> craft(h,empty));
        r.register("pdw_press_craft_reload_hit", () -> PdwGameTests::production);
        r.register("pdw_camo_bench_cycles_save_access", () -> PdwGameTests::camos);
    }
    private static void near(GameTestHelper h, double actual, double expected, String message) { h.assertTrue(Math.abs(actual - expected) < .0002, message + ": " + actual + " != " + expected); }

    private static ItemStack item(String id) { return TGContent.AMMO.containsKey(id) ? TGContent.AMMO.get(id).toStack() : TGContent.MATERIALS.get(id).toStack(); }

    private static Player player(GameTestHelper h, int rounds) {
        var p = WeaponGameTests.player(h); var stack = TGContent.GUNS.get(GUN.id()).toStack(); stack.set(TGContent.ROUNDS.get(), rounds);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack); return p;
    }

    private static List<AdvancedBulletProjectile> shots(GameTestHelper h, Entity owner) { return h.getLevel().getEntitiesOfClass(AdvancedBulletProjectile.class, owner.getBoundingBox().inflate(5), s -> s.getOwner() == owner); }

    private static LivingEntity target(GameTestHelper h, String kind, Vec3 pos) {
        EntityType<? extends Mob> type = kind.equals("npc") ? NpcContent.SUPER_MUTANT.get() : kind.equals("witch") ? EntityTypes.WITCH : EntityTypes.PIG;
        var target = h.spawnWithNoFreeWill(type, pos); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000); return target;
    }

    private static AdvancedBulletProjectile shot(GameTestHelper h, Vec3 pos) {
        var shot = new AdvancedBulletProjectile(TGContent.ADVANCED_BULLET.get(), h.getLevel()); shot.configure(GUN);
        shot.setOwner(WeaponGameTests.player(h)); shot.setPos(pos); return shot;
    }

    private static AdvancedBulletProjectile travelled(GameTestHelper h, LivingEntity target, double distance) {
        var start = target.position().add(-2, .5, 0);
        var s = shot(h, start.add(0, distance, 0)); s.tick(); // Establish the actual first-tick origin, without a fabricated save.
        s.setPos(start); s.setDeltaMovement(3, 0, 0); h.getLevel().addFreshEntity(s); return s;
    }

    private static void falloff(GameTestHelper h, double distance, double amount) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var s = travelled(h, t, distance); s.tick();
        near(h, 1000 - t.getHealth(), amount + .01, "Damage at three-dimensional displacement " + distance);
        h.assertTrue(s.isRemoved(), "First impact consumes projectile"); t.discard(); h.succeed();
    }

    private static void displacement(GameTestHelper h) {
        var start = h.absoluteVec(new Vec3(4, 90.5, 4)); var s = shot(h, start);
        // Four actual moves total 80 blocks but return exactly to the launch point before impact.
        for (double y : new double[]{20, -20, 20, -20}) { s.setDeltaMovement(0, y, 0); s.tick(); }
        var t = target(h, "bare", new Vec3(6, 90, 4)); s.setDeltaMovement(3, 0, 0); h.getLevel().addFreshEntity(s); s.tick();
        near(h, 1000 - t.getHealth(), 5.01, "Falloff uses displacement, not accumulated path"); t.discard(); h.succeed();
    }

    private static void savedFlight(GameTestHelper h) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var owner = target(h, "npc", new Vec3(4, 85, 4));
        var s = travelled(h, t, 21.5); s.setOwner(owner); s.npcDamage(.5f); var saved = NetherGameTests.save(h, s); s.discard();
        var restored = new AdvancedBulletProjectile(TGContent.ADVANCED_BULLET.get(), h.getLevel()); NetherGameTests.load(h, restored, saved);
        h.assertValueEqual(restored.weapon(), GUN, "PDW survives native save"); h.assertValueEqual(restored.getOwner(), owner, "Owner UUID resolves");
        h.assertValueEqual(restored.age(), 1, "Remaining TTL preserved"); h.assertValueEqual(restored.shotDamage(), new ShotDamage(true, .5f), "NPC scale preserved");
        h.getLevel().addFreshEntity(restored); restored.tick();
        near(h, 1000 - t.getHealth(), new ShotDamage(true, .5f).againstEntity(4) + .01, "Reload preserves origin and applies NPC scale after falloff");
        t.discard(); owner.discard(); h.succeed();
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
                case "expired" -> bad.putInt("age", 20);
                case "wrong_weapon" -> bad.putString("weapon", "lasergun");
                case "bad_scale" -> bad.putFloat("damage_scale", 2);
            }
            var copy = new AdvancedBulletProjectile(TGContent.ADVANCED_BULLET.get(), h.getLevel()); NetherGameTests.load(h, copy, bad); h.assertTrue(copy.isRemoved(), "Reject damaged state: " + mode);
        }
        var unborn = shot(h, h.absoluteVec(new Vec3(4, 90, 4))); var copy = new AdvancedBulletProjectile(TGContent.ADVANCED_BULLET.get(), h.getLevel());
        NetherGameTests.load(h, copy, NetherGameTests.save(h, unborn)); h.assertTrue(!copy.isRemoved(), "A save before its first tick legitimately has no origin");
        copy.tick(); h.assertValueEqual(copy.age(), 1, "Unborn save can initialize origin"); unborn.discard(); copy.discard(); h.succeed();
    }

    private static void cadence(GameTestHelper h) {
        var p = player(h, 40); var stack = p.getMainHandItem();
        for (int n = 0; n < 40; n++) {
            h.assertTrue(GunNetwork.handle(p, new GunActionPayload(false)), "Shot " + n); var shots = shots(h, p);
            h.assertValueEqual(shots.size(), 1, "One PDW projectile per charge"); shots.forEach(Entity::discard);
            h.assertValueEqual(GunItem.rounds(stack), 39 - n, "One charge consumed");
            for (int tick = 0; tick < 1; tick++) { h.assertTrue(!GunNetwork.handle(p, new GunActionPayload(false)), "One tick cadence"); p.getCooldowns().tick(); }
        }
        h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "Empty PDW cannot fire");
        p.getAbilities().instabuild = true; GunItem.completeReload(p, stack); h.assertTrue(GunItem.fire(h.getLevel(), p, stack), "Creative reload/fire");
        h.assertValueEqual(GunItem.rounds(stack), 40, "Creative preserves charges"); shots(h, p).forEach(Entity::discard); h.succeed();
    }

    private static void spawnVeto(GameTestHelper h) {
        var p = player(h, 40); var stack = p.getMainHandItem();
        Consumer<EntityJoinLevelEvent> veto = e -> { if (e.getEntity() instanceof AdvancedBulletProjectile s && s.getOwner() == p) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(veto);
        try { h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "Vetoed PDW shot rejected"); h.assertValueEqual(GunItem.rounds(stack), 40, "No lost charge"); h.assertTrue(!p.getCooldowns().isOnCooldown(stack), "No cooldown on rejection"); }
        finally { NeoForge.EVENT_BUS.unregister(veto); } h.succeed();
    }

    private static void veto(GameTestHelper h, String mode) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var s = travelled(h, t, 21.5);
        Consumer<ProjectileImpactEvent> impact = e -> { if (e.getProjectile() == s && mode.equals("impact")) e.setCanceled(true); };
        Consumer<LivingIncomingDamageEvent> damage = e -> { if (e.getSource().getDirectEntity() == s && mode.equals("damage")) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(impact); NeoForge.EVENT_BUS.addListener(damage);
        try { s.tick(); near(h, t.getHealth(), 1000, "Veto preserves health"); h.assertValueEqual(s.isRemoved(), mode.equals("damage"), "Impact veto continues flight"); }
        finally { NeoForge.EVENT_BUS.unregister(impact); NeoForge.EVENT_BUS.unregister(damage); s.discard(); t.discard(); } h.succeed();
    }

    private static void armor(GameTestHelper h, String kind) {
        var t = target(h, kind, new Vec3(6, 90, 4)); if (kind.equals("vanilla")) { t.getAttribute(Attributes.ARMOR).setBaseValue(20); t.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2); }
        float energy = t instanceof NpcTypedArmor npc ? npc.armorAgainst(DamageKind.PROJECTILE) : (float)t.getAttributeValue(Attributes.ARMOR);
        float physical = t instanceof NpcTypedArmor npc ? npc.armorAgainst(DamageKind.PHYSICAL) : (float)t.getAttributeValue(Attributes.ARMOR);
        float toughness = (float)t.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        var s = travelled(h, t, 21.5); s.tick();
        near(h, 1000 - t.getHealth(), ArmorMath.afterArmor(4, energy, toughness, 1) + ArmorMath.afterArmor(.01f, physical, toughness, 0), "Displacement falloff then PROJECTILE armor; no witch energy resistance");
        t.discard(); h.succeed();
    }

    private static void playerArmor(GameTestHelper h) {
        var p = ArmorGameTests.player(h); ArmorGameTests.equipAll(p); var s = shot(h, p.position().add(4, 4, 0));
        p.hurtServer(h.getLevel(), s.shotDamage().source(h.getLevel(), AdvancedBulletProjectile.DAMAGE_TYPE, s), 10);
        h.assertTrue(p.getHealth() < 1000 && p.getHealth() > 990, "Typed player armor absorbs PROJECTILE");
        h.assertTrue(TGArmorSystem.SLOTS.stream().anyMatch(slot -> p.getItemBySlot(slot).getDamageValue() > 0), "Armor durability spent"); s.discard(); h.succeed();
    }

    private static void npc(GameTestHelper h) {
        // Both XZ footprints must remain inside the 12x12 template's entity-ticking chunks.
        // The (8,9) separation stays in the original 23-tick interval band at roughly 12 blocks.
        var npc = h.spawnWithNoFreeWill(NpcContent.BANDIT.get(), new Vec3(2, 90, 2)); var t = target(h, "bare", new Vec3(10, 90, 11));
        h.assertTrue(h.getLevel().areEntitiesActuallyLoadedAndTicking(t.chunkPosition())
                && h.getLevel().getEntity(t.getUUID()) == t, "NPC target is registered in an owned ticking chunk");
        npc.setNoGravity(true); npc.setItemSlot(EquipmentSlot.MAINHAND, TGContent.GUNS.get(GUN.id()).toStack()); npc.setTarget(t);
        var goal = new NpcRangedGoal(npc); int[] clock = {0}; var ticks = new ArrayList<Integer>(); var fired = new ArrayList<AdvancedBulletProjectile>();
        Consumer<EntityJoinLevelEvent> listener = e -> { if (e.getEntity() instanceof AdvancedBulletProjectile s && s.getOwner() == npc) { ticks.add(clock[0]); fired.add(s); } };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            h.assertTrue(goal.canUse(), "PDW activates ranged goal"); for (; clock[0] <= 52; clock[0]++) goal.tick();
            h.assertValueEqual(ticks, List.of(23, 25, 27, 29, 52), "Actual ranged goal creates four burst shots and the next cycle");
            h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()), 0, "NPC does not consume player inventory");
            var s = fired.getFirst(); h.assertTrue(s.shotDamage().npc(), "Launch captures NPC profile");
            s.setPos(t.position().add(-2, .5, 0)); s.setDeltaMovement(3, 0, 0); s.tick();
            near(h, 1000 - t.getHealth(), s.shotDamage().againstEntity(5) + .01, "Native NPC shot applies damage factor once");
        } finally { NeoForge.EVENT_BUS.unregister(listener); fired.forEach(Entity::discard); npc.discard(); t.discard(); } h.succeed();
    }

    private static void nativeFlight(GameTestHelper h) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var s = shot(h, t.position().add(-4, .5, 0)); s.setDeltaMovement(1.5, 0, 0); h.getLevel().addFreshEntity(s);
        h.runAfterDelay(1, () -> { near(h, t.getHealth(), 1000, "Not an instant beam"); h.assertTrue(!s.isRemoved(), "Native projectile still travelling"); });
        h.runAfterDelay(5, () -> { near(h, 1000 - t.getHealth(), 5.01, "World ticks deliver delayed impact"); h.assertTrue(s.isRemoved(), "Hit removes entity"); t.discard(); h.succeed(); });
    }

    private static void owner(GameTestHelper h) {
        var t = target(h, "bare", new Vec3(6, 90, 4)); var s = travelled(h, t, 0); s.setOwner(t); var saved = NetherGameTests.save(h, s); saved.putBoolean("LeftOwner", true); NetherGameTests.load(h, s, saved);
        s.tick(); near(h, t.getHealth(), 1000, "Shooter excluded after leaving launch bounds"); h.assertTrue(!t.isOnFire() && !s.isRemoved(), "No self-ignition"); s.discard(); t.discard(); h.succeed();
    }

    private static void flight(GameTestHelper h) {
        var s = shot(h, h.absoluteVec(new Vec3(4, 90, 4))); s.setDeltaMovement(0, .1, 0); s.tick();
        near(h, s.getDeltaMovement().y, .1 * (double).99f, "Air drag without gravity");
        for (int i = 1; i < 19; i++) s.tick(); h.assertTrue(!s.isRemoved(), "19 movements survive");
        var saved = NetherGameTests.save(h, s); s.discard(); var copy = new AdvancedBulletProjectile(TGContent.ADVANCED_BULLET.get(), h.getLevel()); NetherGameTests.load(h, copy, saved);
        var before = copy.position(); var velocity = copy.getDeltaMovement(); copy.tick(); h.assertTrue(copy.isRemoved(), "20th movement expires"); near(h, copy.getY(), before.y + velocity.y, "Expiration follows movement");
        h.setBlock(4, 2, 4, Blocks.WATER); var wet = shot(h, h.absoluteVec(new Vec3(4.5, 2.2, 4.5))); wet.setDeltaMovement(.1, 0, 0); wet.igniteForSeconds(10); wet.tick();
        h.assertTrue(wet.isInWater() && !wet.isRemoved() && !wet.isOnFire(), "Water extinguishes but preserves flight"); near(h, wet.getDeltaMovement().x, .1 * (double).85f, "Water drag"); wet.discard(); h.succeed();
    }

    private static void preMovement(GameTestHelper h) {
        var t = target(h,"bare",new Vec3(6,90,4)); var start = t.position().add(-2,.5,0);
        var s = shot(h,start.add(0,18,0)); s.tick(); s.setPos(start); s.setDeltaMovement(3,0,0); h.getLevel().addFreshEntity(s); s.tick();
        near(h,1000-t.getHealth(),5.01,"Impact crosses drop start, but uses displacement at the start of that tick"); t.discard(); h.succeed();
    }
    private static void lastTick(GameTestHelper h) {
        var t=target(h,"bare",new Vec3(6,90,4)); var s=shot(h,t.position().add(-2,.5,0));
        for(int i=0;i<19;i++) s.tick(); s.setDeltaMovement(3,0,0); h.getLevel().addFreshEntity(s); s.tick();
        near(h,1000-t.getHealth(),5.01,"Movement 20 can still hit before expiry"); h.assertTrue(s.isRemoved(),"Hit consumes bullet"); t.discard(); h.succeed();
    }
    private static void reload(GameTestHelper h,int rounds) {
        var p=player(h,rounds); var stack=p.getMainHandItem(); p.getInventory().setItem(1,item("advancedmagazineempty"));
        h.assertTrue(!ReloadSessions.begin(p),"Empty magazine is not ammunition"); p.getInventory().setItem(1,item("advancedmagazine").copyWithCount(2));
        h.assertTrue(ReloadSessions.begin(p),"Partial reload begins"); for(int i=0;i<39;i++) ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack),rounds,"No ammunition before deadline"); ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack),40,"Full PDW magazine"); h.assertValueEqual(p.getInventory().countItem(item("advancedmagazine").getItem()),1,"Consume one magazine");
        h.assertValueEqual(p.getInventory().countItem(item("advancedmagazineempty").getItem()),1,"Return one empty magazine");
        h.assertValueEqual(p.getInventory().countItem(item("advancedrounds").getItem()),(int)Math.floor(rounds/(40f/3)),"Source float rounding of three bundles"); h.succeed();
    }
    private static void nativeReload(GameTestHelper h) {
        var p=player(h,27); var stack=p.getMainHandItem(); p.getInventory().setItem(1,item("advancedmagazine")); h.assertTrue(ReloadSessions.begin(p),"Native reload begins");
        for(int tick=1;tick<=40;tick++) { final int n=tick; h.runAfterDelay(tick,()->{
            p.tick(); h.assertValueEqual(GunItem.rounds(stack),n==40?40:27,"Forty native ticks before reload completion");
            if(n==40) { h.assertValueEqual(p.getInventory().countItem(item("advancedrounds").getItem()),2,"Native tick returns two bundles"); p.discard(); h.succeed(); }
        }); }
    }
    private static void reloadCancel(GameTestHelper h,boolean swap) {
        var p=player(h,27); var stack=p.getMainHandItem(); p.getInventory().setItem(1,item("advancedmagazine")); h.assertTrue(ReloadSessions.begin(p),"R begins");
        if(swap) p.setItemInHand(InteractionHand.MAIN_HAND,stack.copy()); else p.getInventory().setItem(1,ItemStack.EMPTY);
        for(int i=0;i<40;i++) ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack),27,"Cancellation preserves original charges");
        h.assertValueEqual(p.getInventory().countItem(item("advancedrounds").getItem()),0,"No duplicate bundle refunds");
        h.assertTrue(!ReloadSessions.active(p),"Cancellation clears session"); h.succeed();
    }
    private static void overflow(GameTestHelper h) {
        var p=player(h,39); for(int i=1;i<36;i++) p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        p.getInventory().setItem(1,item("advancedmagazine").copyWithCount(2)); GunItem.completeReload(p,p.getMainHandItem()); h.assertValueEqual(GunItem.rounds(p.getMainHandItem()),40,"Reload with full inventory");
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(3));
        h.assertValueEqual(drops.stream().filter(e->e.getItem().is(item("advancedrounds").getItem())).mapToInt(e->e.getItem().getCount()).sum(),2,"Two bundles dropped once");
        h.assertValueEqual(drops.stream().filter(e->e.getItem().is(item("advancedmagazineempty").getItem())).mapToInt(e->e.getItem().getCount()).sum(),1,"Empty magazine dropped once");
        drops.forEach(Entity::discard); h.succeed();
    }
    private static void codecs(GameTestHelper h) {
        var p=player(h,27); var stack=p.getMainHandItem(); GunCamo.set(stack,2); stack.set(DataComponents.CUSTOM_NAME,Component.literal("PDW saved"));
        stack.set(TGContent.BALLISTIC_VARIANT.get(),BallisticVariant.EXPLOSIVE); stack.set(TGContent.RELOAD_TICKS.get(),20);
        var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var copy=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,stack).getOrThrow()).getOrThrow();
        h.assertValueEqual(GunItem.rounds(copy),27,"Charges persist"); h.assertValueEqual(GunCamo.index(copy),2,"Camo persists");
        h.assertValueEqual(copy.getHoverName(),stack.getHoverName(),"Name persists"); h.assertTrue(!copy.has(TGContent.RELOAD_TICKS.get()),"Reload is transient");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try { ItemStack.STREAM_CODEC.encode(buffer,stack); h.assertTrue(ItemStack.matches(stack,ItemStack.STREAM_CODEC.decode(buffer)),"Complete item state synchronizes"); } finally { buffer.release(); }
        h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"Injected ballistic variant cannot change factory"); h.assertValueEqual(shots(h,p).getFirst().weapon(),GUN,"Advanced projectile remains selected"); shots(h,p).forEach(Entity::discard);
        copy.set(TGContent.GUN_CAMO.get(),3); h.assertTrue(ItemStack.CODEC.encodeStart(ops,copy).error().isPresent(),"Malformed camo cannot persist"); h.assertValueEqual(GunCamo.index(copy),0,"Out-of-range component has base fallback"); h.succeed();
    }
    private static void muzzle(GameTestHelper h) {
        var p=player(h,40); p.setYRot(0); p.setXRot(0); var stack=p.getMainHandItem();
        for(InteractionHand hand:InteractionHand.values()) {
            p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); p.setItemInHand(hand,stack); p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(stack));
            h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"Both hands fire"); var s=shots(h,p).getFirst();
            double side=(p.getMainArm()==HumanoidArm.RIGHT)!=(hand==InteractionHand.OFF_HAND)?-.16:.16;
            near(h,s.getX(),p.getX()+Math.cos(Math.toRadians(s.getYRot()))*side,"Source side offset"); near(h,s.getY(),p.getEyeY()-.1,"Muzzle height");
            h.assertTrue(Math.abs(s.getYRot())<=1.201 && Math.abs(s.getXRot())<=1.201,"Source angular accuracy .03");
            h.assertTrue(s.getDeltaMovement().length()>2.8 && s.getDeltaMovement().length()<3.2,"Speed 2 retains launch multiplier 1.5"); near(h,s.renderSpeed(),2,"Client receives configured speed"); s.discard();
        } h.succeed();
    }
    private static void events(GameTestHelper h) {
        var t=target(h,"bare",new Vec3(6,90,4)); var amounts=new ArrayList<Float>();
        Consumer<LivingIncomingDamageEvent> listener=e->{ if(e.getEntity()==t && e.getSource().getDirectEntity() instanceof AdvancedBulletProjectile) amounts.add(e.getAmount()); };
        NeoForge.EVENT_BUS.addListener(listener);
        try { travelled(h,t,0).tick(); h.assertTrue(t.getDeltaMovement().horizontalDistanceSqr()>0,"Physical impulse supplies knockback"); travelled(h,t,0).tick();
            near(h,1000-t.getHealth(),10.01,"Main shots bypass cooldown; second ordinary impulse respects it");
            h.assertValueEqual(amounts,List.of(.01f,5f,.01f,5f),"Each bullet offers PHYSICAL impulse then PROJECTILE damage");
        } finally { NeoForge.EVENT_BUS.unregister(listener); t.discard(); } h.succeed();
    }
    private static void zero(GameTestHelper h) {
        var t=target(h,"bare",new Vec3(6,90,4)); var s=travelled(h,t,0); s.npcDamage(0); s.tick();
        near(h,t.getHealth(),1000,"Zero NPC damage also suppresses dummy impulse"); near(h,t.getDeltaMovement().length(),0,"No knockback"); h.assertTrue(s.isRemoved(),"Still consumes impact"); t.discard(); h.succeed();
    }
    private static void wall(GameTestHelper h) {
        var wall=new BlockPos(5,3,4); h.setBlock(wall,Blocks.OAK_PLANKS); var t=target(h,"bare",new Vec3(6,3,4.5));
        for(boolean cancel:List.of(true,false)) for(boolean safe:List.of(true,false)) {
            var p=player(h,40); p.setData(SafeMode.SAFE,safe); var s=shot(h,h.absoluteVec(new Vec3(4.5,3.5,4.5))); s.setOwner(p); s.setDeltaMovement(3,0,0);
            Consumer<ProjectileImpactEvent> listener=e->{if(e.getProjectile()==s && cancel)e.setCanceled(true);}; NeoForge.EVENT_BUS.addListener(listener);
            try { s.tick(); h.assertValueEqual(s.isRemoved(),!cancel,"Impact veto allows flight"); near(h,t.getHealth(),1000,"Wall stops entity hit");
                h.assertTrue(h.getLevel().getBlockState(h.absolutePos(wall)).is(Blocks.OAK_PLANKS),"No terrain destruction in either B mode");
                h.assertTrue(h.getLevel().getBlockState(h.absolutePos(wall.west())).isAir(),"No fire placed");
            } finally { NeoForge.EVENT_BUS.unregister(listener); s.discard(); }
        } t.discard(); h.succeed();
    }
    private static CraftingRecipe recipe(GameTestHelper h,String id) { return (CraftingRecipe)h.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,TGContent.id(id))).orElseThrow().value(); }
    private static ItemStack crafted(GameTestHelper h,boolean empty) {
        var input=CraftingInput.of(3,2,List.of(item("carbonbarrel"),item("carbonreceiver"),item("carbonstock"),ItemStack.EMPTY,item(empty?"advancedmagazineempty":"advancedmagazine"),ItemStack.EMPTY));
        var recipe=recipe(h,empty?"pdw_alt":"pdw"); h.assertTrue(recipe.matches(input,h.getLevel()),"Exact original PDW workbench pattern"); return recipe.assemble(input);
    }
    private static void craft(GameTestHelper h,boolean empty) {
        var result=crafted(h,empty); h.assertTrue(result.is(TGContent.GUNS.get("pdw").get()),"Crafts PDW"); h.assertValueEqual(GunItem.rounds(result),empty?0:40,"Source loaded/empty recipe state"); h.succeed();
    }
    private static void production(GameTestHelper h) {
        var pos=new BlockPos(5,2,4); h.setBlock(pos,TGMachineContent.METAL_PRESS.get()); var press=h.getBlockEntity(pos,MetalPressBlockEntity.class);
        press.setItem(0,item("plateobsidiansteel")); press.setItem(1,item("tgx"));
        try(var tx=Transaction.openRoot()) { h.assertValueEqual(press.energy().insert(2000,tx),2000,"Source press energy"); tx.commit(); }
        for(int i=0;i<101;i++) MetalPressBlockEntity.tick(h.getLevel(),press.getBlockPos(),press.getBlockState(),press);
        h.assertTrue(press.getItem(2).is(item("advancedrounds").getItem()),"Native press produces advanced rounds"); h.assertValueEqual(press.getItem(2).getCount(),16,"Source batch size"); near(h,press.energy().getAmountAsLong(),0,"2000 FE spent");
        var round=press.removeItem(2,3); var input=CraftingInput.of(2,2,List.of(item("advancedmagazineempty"),round.copyWithCount(1),round.copyWithCount(1),round.copyWithCount(1)));
        var mag=recipe(h,"advancedmagazine"); h.assertTrue(mag.matches(input,h.getLevel()),"Three produced bundles fill magazine");
        var p=player(h,0); var gun=crafted(h,true); p.setItemInHand(InteractionHand.MAIN_HAND,gun); p.getInventory().setItem(1,mag.assemble(input));
        h.assertTrue(ReloadSessions.begin(p),"Produced magazine reloads crafted PDW"); for(int i=0;i<40;i++) ReloadSessions.tick(p);
        h.assertTrue(GunItem.fire(h.getLevel(),p,gun),"Survival chain fires"); var s=shots(h,p).getFirst(); var t=target(h,"bare",new Vec3(6,90,4));
        s.setPos(t.position().add(-2,.5,0)); s.setDeltaMovement(3,0,0); s.tick(); near(h,1000-t.getHealth(),5.01,"Produced ammunition deals actual damage");
        h.assertValueEqual(GunItem.rounds(gun),39,"One shot spent"); h.assertValueEqual(p.getInventory().countItem(item("advancedmagazineempty").getItem()),1,"Empty magazine returned"); t.discard(); h.succeed();
    }
    private static void camos(GameTestHelper h) {
        var pos=new BlockPos(4,2,4); h.setBlock(pos,CamoBenchContent.BLOCK.get()); var bench=h.getBlockEntity(pos,CamoBenchBlockEntity.class);
        var p=player(h,27); bench.setOwner(p); var menu=new CamoBenchMenu(61,p.getInventory(),bench); p.containerMenu=menu;
        var stack=p.getMainHandItem().copy(); stack.set(DataComponents.CUSTOM_NAME,Component.literal("PDW camouflage")); GunCamo.set(stack,0); bench.setItem(0,stack.copy());
        for(int button:new int[]{1,2}) for(int i=1;i<=3;i++) { h.assertTrue(menu.clickMenuButton(p,button),"Native bench cycles PDW");
            h.assertValueEqual(GunCamo.index(bench.getItem(0)),Math.floorMod(button==1?i:-i,3),"Source order and wrap"); h.assertValueEqual(GunItem.rounds(bench.getItem(0)),27,"Rounds retained"); }
        h.assertTrue(ItemStack.matches(stack,bench.getItem(0)),"Forward/backward cycle keeps every component"); menu.clickMenuButton(p,1); menu.clickMenuButton(p,0);
        var expected=bench.getItem(0).copy(); var restored=(CamoBenchBlockEntity)BlockEntity.loadStatic(bench.getBlockPos(),bench.getBlockState(),bench.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.getLevel().removeBlockEntity(bench.getBlockPos()); h.getLevel().setBlockEntity(restored); h.assertTrue(ItemStack.matches(expected,restored.getItem(0)),"Camo, rounds and custom name survive bench save");
        var other=WeaponGameTests.player(h); other.setUUID(UUID.randomUUID()); var forbidden=new CamoBenchMenu(62,other.getInventory(),restored); other.containerMenu=forbidden;
        h.assertTrue(!forbidden.clickMenuButton(other,1),"Private bench rejects other player");
        var resumed=new CamoBenchMenu(63,p.getInventory(),restored); p.containerMenu=resumed; p.setPos(p.position().add(30,0,0)); h.assertTrue(!resumed.clickMenuButton(p,1),"Distance authorization still applies");
        h.assertTrue(ItemStack.matches(expected,restored.getItem(0)),"Rejected requests preserve gun"); h.succeed();
    }
    private PdwGameTests() {}
}
