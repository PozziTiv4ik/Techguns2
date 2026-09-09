package techguns.modern.test;

import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.crafting.AmmoChangeRecipe;
import techguns.modern.radiation.*;

final class RocketGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        for (var variant : RocketVariant.values()) {
            registry.register("rocket_reload_" + variant.id(), () -> h -> reload(h, variant));
            registry.register("rocket_craft_variant_" + variant.id(), () -> h -> craftVariant(h, variant));
            registry.register("rocket_flight_save_" + variant.id(), () -> h -> flight(h, variant));
            registry.register("rocket_blast_radius_" + variant.id(), () -> h -> blastBand(h, variant));
        }
        registry.register("rocket_gun_and_warhead_recipes", () -> RocketGameTests::recipes);
        registry.register("rocket_reload_rejects_other_variants", () -> RocketGameTests::wrongAmmo);
        registry.register("rocket_direct_and_blast_cooldown", () -> RocketGameTests::directHit);
        registry.register("rocket_long_flight_direct_minimum", () -> RocketGameTests::longFlight);
        registry.register("rocket_solid_wall_safe", () -> h -> wall(h, Blocks.STONE, false));
        registry.register("rocket_solid_wall_unsafe", () -> h -> wall(h, Blocks.STONE, true));
        registry.register("rocket_obsidian_resists", () -> h -> wall(h, Blocks.OBSIDIAN, true));
        registry.register("rocket_water_drag", () -> RocketGameTests::water);
        registry.register("rocket_expiry_does_not_explode", () -> RocketGameTests::expiry);
        registry.register("rocket_failed_direct_damage_no_blast", () -> RocketGameTests::failedHit);
        registry.register("rocket_explosion_armor_and_knockback", () -> RocketGameTests::armor);
        registry.register("rocket_cancelled_spawn", () -> RocketGameTests::cancelledSpawn);
        registry.register("rocket_cancelled_impact", () -> RocketGameTests::cancelledImpact);
        registry.register("rocket_cancelled_explosion", () -> RocketGameTests::cancelledExplosion);
        registry.register("rocket_detonation_mutable_lists", () -> RocketGameTests::detonationHook);
        registry.register("rocket_safe_mode_cannot_break_event_blocks", () -> RocketGameTests::safeHook);
        registry.register("rocket_no_inner_loot_and_tnt_callback", () -> RocketGameTests::blockCallbacks);
        registry.register("rocket_invalid_save", () -> RocketGameTests::invalidSave);
        registry.register("rocket_variant_entity_sync", () -> RocketGameTests::sync);
        registry.register("rocket_safe_mode_persistence_and_permissions", () -> RocketGameTests::safeMode);
        registry.register("rocket_safe_mode_snapshot_at_fire", () -> RocketGameTests::safeShot);
        registry.register("rocket_nuclear_fallout_lifetime_and_save", () -> RocketGameTests::fallout);
        registry.register("rocket_nuclear_disabled_radiation", () -> RocketGameTests::disabledFallout);
        registry.register("rocket_nuclear_ammunition_radioactivity", () -> RocketGameTests::radioactiveAmmo);
    }
    private static void near(GameTestHelper h, double actual, double expected, String reason) {
        h.assertTrue(Math.abs(actual - expected) < .0002, reason + ": " + actual + " != " + expected);
    }
    private static RocketProjectile rocket(GameTestHelper h, RocketVariant variant, Vec3 pos, boolean unsafe) {
        var rocket = new RocketProjectile(TGContent.ROCKET.get(), h.getLevel());
        rocket.configure(Weapons.definition("rocketlauncher"), variant, unsafe);
        rocket.setOwner(WeaponGameTests.player(h)); rocket.setPos(h.absoluteVec(pos));
        return rocket;
    }
    private static LivingEntity target(GameTestHelper h, Vec3 relative) {
        var entity = h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, relative);
        entity.setNoGravity(true);
        entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); entity.setHealth(1000);
        return entity;
    }
    private static Player player(GameTestHelper h, RocketVariant variant, int rounds) {
        var player = WeaponGameTests.player(h);
        var stack = TGContent.GUNS.get("rocketlauncher").toStack();
        stack.set(TGContent.ROUNDS.get(), rounds); stack.set(TGContent.ROCKET_VARIANT.get(), variant);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }
    private static ItemStack item(String id) { return TGContent.AMMO.containsKey(id) ? TGContent.AMMO.get(id).toStack() : TGContent.MATERIALS.get(id).toStack(); }
    private static CraftingRecipe recipe(GameTestHelper h, CraftingInput input) {
        return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow().value();
    }
    private static ItemStack craft(GameTestHelper h, int width, int height, ItemStack... stacks) {
        var input = CraftingInput.of(width, height, List.of(stacks)); return recipe(h, input).assemble(input);
    }
    private static CompoundTag save(GameTestHelper h, Entity entity) {
        var problems = new ProblemReporter.Collector();
        var output = TagValueOutput.createWithContext(problems, h.getLevel().registryAccess()); entity.saveWithoutId(output);
        h.assertTrue(problems.isEmpty(), "Entity saves cleanly: " + problems.getReport()); return output.buildResult();
    }
    private static void load(GameTestHelper h, Entity entity, CompoundTag tag) {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), tag));
    }
    private static void reload(GameTestHelper h, RocketVariant variant) {
        var player = player(h, variant, 0); var stack = player.getMainHandItem();
        var ammo = TGContent.AMMO.get(variant.ammo()).toStack(2); player.getInventory().setItem(1, ammo);
        h.assertTrue(ReloadSessions.begin(player), "Selected rocket reloads");
        for (int tick = 0; tick < 39; tick++) ReloadSessions.tick(player);
        h.assertValueEqual(GunItem.rounds(stack), 0, "Rocket is not loaded before 40 ticks");
        h.assertValueEqual(ammo.getCount(), 2, "No early consumption"); ReloadSessions.tick(player);
        h.assertValueEqual(GunItem.rounds(stack), 1, "Single rocket capacity"); h.assertValueEqual(ammo.getCount(), 1, "One selected rocket consumed");
        h.assertTrue(!ReloadSessions.begin(player), "Full launcher cannot reload");
        var shot = rocket(h, variant, new Vec3(2, 60, 2), false);
        shot.shootLegacy(player, 0);
        near(h, shot.getDeltaMovement().length() / variant.speed(Weapons.definition("rocketlauncher").stats()), 1.5, "Factory velocity and legacy 1.5 multiplier", .15);
        h.succeed();
    }
    private static void near(GameTestHelper h, double actual, double expected, String reason, double tolerance) {
        h.assertTrue(Math.abs(actual - expected) < tolerance, reason + ": " + actual + " != " + expected);
    }
    private static void wrongAmmo(GameTestHelper h) {
        var player = player(h, RocketVariant.NUKE, 0);
        player.getInventory().setItem(1, item("rocket")); player.getInventory().setItem(2, item("rocket_high_velocity"));
        h.assertTrue(!ReloadSessions.begin(player), "Nuclear mode does not consume ordinary or HV rockets");
        player.getInventory().setItem(3, item("rocket_nuke")); h.assertTrue(ReloadSessions.begin(player), "Matching ammunition starts reload");
        player.getInventory().setItem(3, ItemStack.EMPTY);
        for (int i = 0; i < 40; i++) ReloadSessions.tick(player);
        h.assertValueEqual(GunItem.rounds(player.getMainHandItem()), 0, "Inventory is rechecked at completion");
        h.assertValueEqual(player.getInventory().getItem(1).getCount(), 1, "Wrong ammunition is untouched"); h.succeed();
    }
    private static void craftVariant(GameTestHelper h, RocketVariant variant) {
        var stack = player(h, RocketVariant.HIGH_VELOCITY, 1).getMainHandItem();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Saved launcher"));
        stack.set(TGContent.RELOAD_TICKS.get(), 10); stack.set(TGContent.AIMING.get(), true);
        var input = CraftingInput.of(2, 2, List.of(ItemStack.EMPTY, item(variant.ammo()), stack, ItemStack.EMPTY));
        var recipe = recipe(h, input); var result = recipe.assemble(input);
        h.assertTrue(recipe instanceof AmmoChangeRecipe, "Original custom ammo-change recipe");
        h.assertValueEqual(RocketAmmo.variant(result), variant, "Chosen variant"); h.assertValueEqual(GunItem.rounds(result), 1, "Replacement rocket is loaded");
        h.assertValueEqual(result.get(DataComponents.CUSTOM_NAME), stack.get(DataComponents.CUSTOM_NAME), "Gun name is preserved");
        h.assertTrue(!result.has(TGContent.RELOAD_TICKS.get()) && !result.has(TGContent.AIMING.get()), "Transient player actions are removed");
        h.assertValueEqual(RocketAmmo.variant(stack), RocketVariant.HIGH_VELOCITY, "Crafting preview never mutates input");
        h.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), "Source recipe consumes the old loaded rocket; no ammunition duplication");
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var saved = ItemStack.CODEC.encodeStart(ops, result).getOrThrow();
        h.assertValueEqual(RocketAmmo.variant(ItemStack.CODEC.parse(ops, saved).getOrThrow()), variant, "Variant component persists");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            AmmoChangeRecipe.STREAM_CODEC.encode(buffer, (AmmoChangeRecipe) recipe);
            h.assertValueEqual(RocketAmmo.variant(AmmoChangeRecipe.STREAM_CODEC.decode(buffer).assemble(input)), variant, "Recipe network codec preserves behavior");
        } finally { buffer.release(); }
        h.succeed();
    }
    private static void recipes(GameTestHelper h) {
        var barrel = item("obsidiansteelbarrel"); var receiver = item("steelreceiver");
        var loaded = craft(h, 3, 2, item("rocket"), barrel, barrel, ItemStack.EMPTY, receiver, ItemStack.EMPTY);
        var empty = craft(h, 3, 2, ItemStack.EMPTY, barrel, barrel, ItemStack.EMPTY, receiver, ItemStack.EMPTY);
        h.assertValueEqual(GunItem.rounds(loaded), 1, "Original loaded launcher recipe"); h.assertValueEqual(GunItem.rounds(empty), 0, "Original empty recipe");
        var plate = item("platelead"); var circuit = item("circuitboard"); var tgx = item("tgx");
        var warheads = craft(h, 3, 3, plate, circuit, plate, tgx, item("enricheduranium"), tgx, plate, circuit, plate);
        h.assertTrue(warheads.is(TGContent.MATERIALS.get("tacticalnukewarhead").get()), "Enriched uranium production feeds warhead crafting");
        h.assertValueEqual(warheads.getCount(), 2, "Original two-warhead output");
        h.assertTrue(craft(h, 2, 1, item("rocket"), warheads.copyWithCount(1)).is(TGContent.AMMO.get("rocket_nuke").get()), "Warhead attaches to ordinary rocket"); h.succeed();
    }
    private static void flight(GameTestHelper h, RocketVariant variant) {
        var rocket = rocket(h, variant, new Vec3(3, 80, 3), true);
        rocket.setDeltaMovement(.5, .2, .1); Vec3 start = rocket.position(); rocket.tick();
        near(h, rocket.getX(), start.x + .5, "Motion advances before drag");
        near(h, rocket.getDeltaMovement().x, .5 * (double) .99f, "Legacy air drag");
        near(h, rocket.getDeltaMovement().y, .2 * (double) .99f - .01, "Gravity applies after drag");
        var restored = rocket(h, RocketVariant.DEFAULT, new Vec3(0, 80, 0), false); load(h, restored, save(h, rocket));
        h.assertValueEqual(restored.variant(), variant, "Variant survives entity save"); h.assertTrue(restored.damagesBlocks(), "Firing-time block policy persists");
        h.assertValueEqual(restored.age(), 1, "Lifetime persists"); h.assertValueEqual(restored.getDeltaMovement(), rocket.getDeltaMovement(), "Motion persists");
        rocket.tick(); restored.tick(); h.assertValueEqual(restored.position(), rocket.position(), "Restored projectile follows the same trajectory");
        rocket.discard(); restored.discard(); h.succeed();
    }
    private static void blastBand(GameTestHelper h, RocketVariant variant) {
        var rocket = rocket(h, variant, new Vec3(3, 80, 3), false); var gun = rocket.weapon().stats();
        var targets = new ArrayList<LivingEntity>();
        double inner = variant.innerRadius(gun), outer = variant.outerRadius(gun);
        try {
            for (double d : new double[]{inner, inner + (outer - inner) * .125, inner + (outer - inner) * .875, outer + .01}) {
                var target = target(h, new Vec3(3 + d, 80, 3)); targets.add(target);
                target.setPos(rocket.position().add(d, -target.getEyeHeight(), 0));
            }
            h.assertTrue(rocket.explode(), "Server explosion accepted");
            for (var target : targets) {
                double distance = rocket.position().distanceTo(target.getEyePosition());
                near(h, 1000 - target.getHealth(), variant.blastDamage(gun, distance), "Explosion evaluates eye distance and source outer band");
            }
        } finally { targets.forEach(Entity::discard); }
        h.succeed();
    }
    private static void directHit(GameTestHelper h) {
        var target = target(h, new Vec3(6, 40, 3));
        var rocket = rocket(h, RocketVariant.DEFAULT, new Vec3(4, 41, 3), false); rocket.setDeltaMovement(4, 0, 0);
        try {
            rocket.tick(); near(h, 1000 - target.getHealth(), 50, "Direct 50 and same-tick 50 blast do not bypass hurt cooldown");
            h.assertTrue(rocket.isRemoved(), "Impact discards rocket"); near(h, rocket.getX(), h.absoluteVec(new Vec3(4,41,3)).x, "Explosion occurs at pre-movement position, as in the source");
        } finally { target.discard(); }
        h.succeed();
    }
    private static void longFlight(GameTestHelper h) {
        var rocket = rocket(h, RocketVariant.DEFAULT, new Vec3(3, 40, 3), false);
        rocket.tick(); rocket.setPos(rocket.position().add(10, 0, 0));
        var target = target(h, new Vec3(15, 39, 3)); rocket.setDeltaMovement(4, 0, 0);
        Consumer<ExplosionEvent.Start> cancel = event -> { if (event.getExplosion().getDirectSourceEntity() == rocket) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(cancel);
        try { rocket.tick(); near(h, 1000 - target.getHealth(), 10, "Direct damage uses displacement from original muzzle, not last segment"); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); target.discard(); }
        h.succeed();
    }
    private static void wall(GameTestHelper h, Block material, boolean unsafe) {
        var rocket = rocket(h, RocketVariant.DEFAULT, new Vec3(3.5, 41.5, 3.5), unsafe);
        var target = target(h, new Vec3(6.5, 40, 3.5));
        var positions = new ArrayList<BlockPos>();
        for (int y = 40; y <= 43; y++) for (int z = 2; z <= 4; z++) { var pos = new BlockPos(5,y,z); h.setBlock(pos, material); positions.add(pos); }
        try {
            rocket.setDeltaMovement(4, 0, 0); rocket.tick();
            boolean destroyed = unsafe && material == Blocks.STONE;
            h.assertTrue(h.getBlockState(new BlockPos(5,41,3)).isAir() == destroyed, "Resistance and safe mode select block destruction");
            h.assertTrue((target.getHealth() < 1000) == destroyed, "Visibility is tested after destructible walls are removed");
        } finally { target.discard(); positions.forEach(pos -> h.setBlock(pos, Blocks.AIR)); }
        h.succeed();
    }
    private static void water(GameTestHelper h) {
        var pos = new BlockPos(3,40,3); h.setBlock(pos, Blocks.WATER);
        var rocket = rocket(h, RocketVariant.DEFAULT, new Vec3(3.5,40.4,3.5), false); rocket.setDeltaMovement(.1,0,0);
        try {
            rocket.tick(); h.assertTrue(!rocket.isRemoved(), "Water is not an impact surface");
            near(h, rocket.getDeltaMovement().x, .1 * (double) .85f, "Original water drag .85");
        } finally { h.setBlock(pos, Blocks.AIR); rocket.discard(); }
        h.succeed();
    }
    private static void expiry(GameTestHelper h) {
        for (var variant : RocketVariant.values()) {
            var rocket = rocket(h, variant, new Vec3(3,80,3), false); var tag = save(h, rocket);
            tag.putInt("age", variant.lifetime(rocket.weapon().stats()) - 1); load(h, rocket, tag);
            int[] explosions = {0}; Consumer<ExplosionEvent.Start> listener = event -> { if (event.getExplosion().getDirectSourceEntity() == rocket) explosions[0]++; };
            NeoForge.EVENT_BUS.addListener(listener);
            try { rocket.tick(); h.assertTrue(rocket.isRemoved(), "Original TTL expires"); h.assertValueEqual(explosions[0], 0, "Expiry is silent and non-explosive"); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
        } h.succeed();
    }
    private static void failedHit(GameTestHelper h) {
        var rocket = rocket(h, RocketVariant.DEFAULT, new Vec3(3,41,3), false); rocket.setDeltaMovement(5,0,0);
        var target = target(h,new Vec3(6,40,3)); target.setInvulnerable(true);
        int[] explosions = {0}; Consumer<ExplosionEvent.Start> listener = event -> { if(event.getExplosion().getDirectSourceEntity()==rocket) explosions[0]++; };
        NeoForge.EVENT_BUS.addListener(listener);
        try { rocket.tick(); h.assertTrue(rocket.isRemoved(), "Failed direct hit consumes projectile"); h.assertValueEqual(explosions[0],0,"Original onHitEffect runs only on successful living damage"); }
        finally { NeoForge.EVENT_BUS.unregister(listener); target.discard(); } h.succeed();
    }
    private static void armor(GameTestHelper h) {
        var target = target(h,new Vec3(6,40,3)); target.getAttribute(Attributes.ARMOR).setBaseValue(10); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        var rocket = rocket(h,RocketVariant.DEFAULT,new Vec3(4,41,3),false); rocket.setOwner(WeaponGameTests.player(h));
        try {
            RocketDamage.hurt(h.getLevel(),rocket,target,50,3);
            near(h,1000-target.getHealth(),40,"Ten vanilla armor points contribute five explosion armor");
            near(h,target.getDeltaMovement().horizontalDistance(),1.2,"Direct rocket knockback is three times .4");
            target.invulnerableTime=0; target.setDeltaMovement(Vec3.ZERO);
            target.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack((Player) rocket.getOwner()),1);
            near(h,target.getDeltaMovement().horizontalDistance(),.4,"Scoped rocket knockback does not leak into subsequent damage");
            h.assertTrue(!RocketDamage.source(h.getLevel(),rocket).is(net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN),"Rocket damage retains normal invulnerability");
            h.assertTrue(RocketDamage.source(h.getLevel(),rocket).is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION),"Explosion enchantment category");
        } finally { target.discard(); } h.succeed();
    }
    private static void cancelledSpawn(GameTestHelper h) {
        var player=player(h,RocketVariant.NUKE,1);
        Consumer<EntityJoinLevelEvent> cancel=event -> { if(event.getEntity() instanceof RocketProjectile r && r.getOwner()==player) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(cancel);
        try { h.assertTrue(!GunItem.fire(h.getLevel(),player,player.getMainHandItem()),"Cancelled spawn rejects shot"); h.assertValueEqual(GunItem.rounds(player.getMainHandItem()),1,"No lost ammunition"); h.assertTrue(!player.getCooldowns().isOnCooldown(player.getMainHandItem()),"No cooldown on failed spawn"); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); } h.succeed();
    }
    private static void cancelledImpact(GameTestHelper h) {
        var rocket=rocket(h,RocketVariant.DEFAULT,new Vec3(3,41,3),false); rocket.setDeltaMovement(6,0,0); var target=target(h,new Vec3(6,40,3));
        Consumer<ProjectileImpactEvent> cancel=event -> { if(event.getProjectile()==rocket) event.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(cancel);
        try { rocket.tick(); h.assertTrue(!rocket.isRemoved(),"Cancelled impact continues flight"); near(h,target.getHealth(),1000,"Cancelled direct hit has no damage"); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); target.discard(); rocket.discard(); } h.succeed();
    }
    private static void cancelledExplosion(GameTestHelper h) {
        var rocket=rocket(h,RocketVariant.NUKE,new Vec3(3,80,3),true); var pos=new BlockPos(4,80,3); h.setBlock(pos,Blocks.STONE);
        var target=target(h,new Vec3(5,80,3)); boolean old=RadiationSystem.DISABLED.get(); RadiationSystem.DISABLED.set(false);
        Consumer<ExplosionEvent.Start> cancel=event -> { if(event.getExplosion().getDirectSourceEntity()==rocket) event.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(cancel);
        try { h.assertTrue(!rocket.explode(),"Explosion start cancellation"); near(h,target.getHealth(),1000,"No blast damage"); h.assertTrue(h.getBlockState(pos).is(Blocks.STONE),"No terrain damage"); h.assertTrue(zones(h,rocket.position()).isEmpty(),"Cancelled nuclear blast creates no fallout"); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); RadiationSystem.DISABLED.set(old); target.discard(); h.setBlock(pos,Blocks.AIR); } h.succeed();
    }
    private static void detonationHook(GameTestHelper h) {
        var rocket=rocket(h,RocketVariant.DEFAULT,new Vec3(3,41,3),true); var pos=new BlockPos(4,41,3); h.setBlock(pos,Blocks.STONE); var target=target(h,new Vec3(2,40,3));
        boolean[] lists={false}; Consumer<ExplosionEvent.Detonate> protect=event -> {
            if(event.getExplosion().getDirectSourceEntity()!=rocket) return;
            lists[0]=event.getAffectedBlocks().contains(h.absolutePos(pos)) && event.getAffectedEntities().contains(target);
            event.getAffectedBlocks().remove(h.absolutePos(pos)); event.getAffectedEntities().remove(target);
        }; NeoForge.EVENT_BUS.addListener(protect);
        try { rocket.explode(); h.assertTrue(lists[0],"Hook receives the actual block and entity lists"); h.assertTrue(h.getBlockState(pos).is(Blocks.STONE),"Protection removes block from explosion"); near(h,target.getHealth(),1000,"Protection removes entity from damage"); }
        finally { NeoForge.EVENT_BUS.unregister(protect); h.setBlock(pos,Blocks.AIR); target.discard(); } h.succeed();
    }
    private static void safeHook(GameTestHelper h) {
        var rocket=rocket(h,RocketVariant.DEFAULT,new Vec3(3,41,3),false); var pos=new BlockPos(4,41,3); h.setBlock(pos,Blocks.STONE);
        Consumer<ExplosionEvent.Detonate> add=event -> { if(event.getExplosion().getDirectSourceEntity()==rocket) event.getAffectedBlocks().add(h.absolutePos(pos)); }; NeoForge.EVENT_BUS.addListener(add);
        try { rocket.explode(); h.assertTrue(h.getBlockState(pos).is(Blocks.STONE),"Safe mode remains KEEP even when an event adds terrain"); }
        finally { NeoForge.EVENT_BUS.unregister(add); h.setBlock(pos,Blocks.AIR); } h.succeed();
    }
    private static void blockCallbacks(GameTestHelper h) {
        var rocket=rocket(h,RocketVariant.DEFAULT,new Vec3(3.5,41.5,3.5),true);
        var stone=new BlockPos(4,41,3); var chest=new BlockPos(3,41,4); var tnt=new BlockPos(2,41,3);
        h.setBlock(stone,Blocks.STONE); h.setBlock(chest,Blocks.CHEST); h.setBlock(tnt,Blocks.TNT);
        h.getBlockEntity(chest,ChestBlockEntity.class).setItem(0,new ItemStack(Items.DIAMOND,3));
        try {
            rocket.explode(); h.assertTrue(h.getBlockState(stone).isAir(),"Inner stone destroyed");
            var loot=h.getLevel().getEntitiesOfClass(ItemEntity.class,rocket.getBoundingBox().inflate(5));
            h.assertValueEqual(loot.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum(),3,"Block entity inventory drops once");
            h.assertTrue(loot.stream().noneMatch(e -> e.getItem().is(Items.COBBLESTONE)||e.getItem().is(Items.CHEST)),"No inner-radius block loot");
            h.assertValueEqual(h.getLevel().getEntitiesOfClass(PrimedTnt.class,rocket.getBoundingBox().inflate(5)).size(),1,"TNT receives normal explosion callback");
        } finally {
            h.getLevel().getEntitiesOfClass(ItemEntity.class,rocket.getBoundingBox().inflate(5)).forEach(Entity::discard);
            h.getLevel().getEntitiesOfClass(PrimedTnt.class,rocket.getBoundingBox().inflate(5)).forEach(Entity::discard);
            for(var pos:List.of(stone,chest,tnt)) h.setBlock(pos,Blocks.AIR);
        } h.succeed();
    }
    private static void invalidSave(GameTestHelper h) {
        var original=rocket(h,RocketVariant.DEFAULT,new Vec3(3,80,3),false);
        for(var field:List.of("weapon","variant")) {
            var tag=save(h,original); tag.putString(field,"unknown"); var restored=rocket(h,RocketVariant.DEFAULT,new Vec3(3,80,3),false); load(h,restored,tag);
            h.assertTrue(restored.isRemoved(),"Unknown saved " + field + " rejected");
        }
        var tag=save(h,original); tag.putString("weapon","revolver"); var restored=rocket(h,RocketVariant.DEFAULT,new Vec3(3,80,3),false); load(h,restored,tag); h.assertTrue(restored.isRemoved(),"Ballistic gun cannot become a rocket"); h.succeed();
    }
    private static void sync(GameTestHelper h) {
        var rocket=rocket(h,RocketVariant.NUKE,new Vec3(3,80,3),false); var replica=rocket(h,RocketVariant.DEFAULT,new Vec3(3,80,3),false);
        var values=rocket.getEntityData().getNonDefaultValues(); var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer,new ClientboundSetEntityDataPacket(rocket.getId(),values));
            replica.getEntityData().assignValues(ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer).packedItems());
            h.assertValueEqual(replica.variant(),RocketVariant.NUKE,"Client receives nuclear projectile texture variant");
            techguns.modern.network.SafeModePayload.CODEC.encode(buffer,techguns.modern.network.SafeModePayload.INSTANCE);
            h.assertValueEqual(techguns.modern.network.SafeModePayload.CODEC.decode(buffer),techguns.modern.network.SafeModePayload.INSTANCE,"B action has no client-supplied permission state");
        } finally { buffer.release(); } h.succeed();
    }
    private static void safeMode(GameTestHelper h) {
        var player=player(h,RocketVariant.DEFAULT,1); boolean old=SafeMode.OP_ONLY.get(); SafeMode.OP_ONLY.set(false);
        try {
            h.assertTrue(!SafeMode.enabled(player),"Legacy default is unsafe"); h.assertTrue(SafeMode.toggle(player)&&SafeMode.enabled(player),"B enables safe mode");
            var restored=WeaponGameTests.player(h); load(h,restored,save(h,player)); h.assertTrue(SafeMode.enabled(restored),"Safe mode survives full player save");
            h.assertTrue(SafeMode.toggle(player)&&!SafeMode.enabled(player),"B returns to unsafe when allowed");
            SafeMode.OP_ONLY.set(true); h.assertTrue(SafeMode.enabled(player),"OP policy immediately overrides stored unsafe mode");
            h.assertTrue(!SafeMode.toggle(player),"Non-OP cannot disable safe mode");
            h.assertTrue(net.neoforged.neoforge.server.permission.PermissionAPI.getRegisteredNodes().contains(SafeMode.UNSAFE),"Original permission node registered with NeoForge");
        } finally { SafeMode.OP_ONLY.set(old); } h.succeed();
    }
    private static void safeShot(GameTestHelper h) {
        var player=player(h,RocketVariant.HIGH_VELOCITY,1); player.setData(SafeMode.SAFE,true);
        h.assertTrue(GunItem.fire(h.getLevel(),player,player.getMainHandItem()),"Safe shot fires");
        var list=h.getLevel().getEntitiesOfClass(RocketProjectile.class,player.getBoundingBox().inflate(3),r -> r.getOwner()==player);
        h.assertValueEqual(list.size(),1,"One accepted rocket"); player.setData(SafeMode.SAFE,false);
        h.assertTrue(!list.getFirst().damagesBlocks(),"B changes subsequent shots, not rockets already in flight");
        h.assertValueEqual(list.getFirst().variant(),RocketVariant.HIGH_VELOCITY,"Fire selects the actual loaded variant"); list.forEach(Entity::discard); h.succeed();
    }
    private static List<RadiationZone> zones(GameTestHelper h,Vec3 pos) { return h.getLevel().getEntitiesOfClass(RadiationZone.class,new net.minecraft.world.phys.AABB(pos,pos).inflate(1)); }
    private static void fallout(GameTestHelper h) {
        boolean old=RadiationSystem.DISABLED.get(); RadiationSystem.DISABLED.set(false); var rocket=rocket(h,RocketVariant.NUKE,new Vec3(3,80,3),false);
        var targets=new ArrayList<LivingEntity>();
        try {
            rocket.explode(); var zones=zones(h,rocket.position()); h.assertValueEqual(zones.size(),1,"Nuke creates one fallout zone"); var zone=zones.getFirst();
            for(double distance:new double[]{2,16,24,25}) { var target=target(h,new Vec3(3+distance,80,3)); targets.add(target); }
            for(int i=0;i<19;i++) zone.tick(); h.assertTrue(!targets.getFirst().hasEffect(RadiationSystem.EXPOSURE),"No exposure before 20-tick interval"); zone.tick();
            int[] expected={9,3,8};
            for(int i=0;i<3;i++) { var effect=targets.get(i).getEffect(RadiationSystem.EXPOSURE); h.assertValueEqual(effect.getAmplifier(),expected[i],"Original fallout strength and increasing outer ring"); h.assertValueEqual(effect.getDuration(),22,"22-tick exposure refresh"); }
            h.assertTrue(!targets.get(3).hasEffect(RadiationSystem.EXPOSURE),"Outer radius boundary is excluded");
            var saved=save(h,zone); saved.putInt("age",899); var restored=new RadiationZone(TGContent.RADIATION_ZONE.get(),h.getLevel()); load(h,restored,saved);
            targets.forEach(t -> t.removeEffect(RadiationSystem.EXPOSURE)); restored.tick();
            h.assertValueEqual(targets.getFirst().getEffect(RadiationSystem.EXPOSURE).getAmplifier(),5,"Fallout decays in second half after reload");
            saved.putInt("age",1199); load(h,restored,saved); targets.forEach(t -> t.removeEffect(RadiationSystem.EXPOSURE)); restored.tick();
            h.assertValueEqual(targets.getFirst().getEffect(RadiationSystem.EXPOSURE).getAmplifier(),0,"Last interval retains source amplifier zero");
            h.assertTrue(!restored.isRemoved(),"Zone still exists at tick 1200"); restored.tick(); h.assertTrue(restored.isRemoved(),"Zone expires at tick 1201");
        } finally { RadiationSystem.DISABLED.set(old); targets.forEach(Entity::discard); zones(h,rocket.position()).forEach(Entity::discard); } h.succeed();
    }
    private static void disabledFallout(GameTestHelper h) {
        boolean old=RadiationSystem.DISABLED.get(); RadiationSystem.DISABLED.set(true); var rocket=rocket(h,RocketVariant.NUKE,new Vec3(3,80,3),false);
        try { rocket.explode(); h.assertTrue(zones(h,rocket.position()).isEmpty(),"Default-disabled radiation creates no nuclear fallout"); }
        finally { RadiationSystem.DISABLED.set(old); } h.succeed();
    }
    private static void radioactiveAmmo(GameTestHelper h) {
        h.assertValueEqual(RadiationSystem.inventoryStrength(item("rocket_nuke")),1,"Nuclear ammunition is a source radioactive item");
        h.assertValueEqual(RadiationSystem.inventoryStrength(item("tacticalnukewarhead")),1,"Warhead radioactivity");
        h.assertValueEqual(RadiationSystem.inventoryStrength(item("rocket")),0,"Ordinary rocket is not radioactive"); h.succeed();
    }
    private RocketGameTests() {}
}
