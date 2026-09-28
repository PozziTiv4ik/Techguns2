package techguns.modern.test;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
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
import techguns.modern.crafting.AmmoChangeRecipe;
import techguns.modern.machine.*;
import techguns.modern.network.*;
import techguns.modern.npc.*;

final class ExplosiveAmmoGameTests {
    private static final WeaponDefinition AS50 = Weapons.definition("as50");
    private static final String MAGAZINE = "as50magazine_explosive", LOOSE = "sniperrounds_explosive";

    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for (var from : BallisticVariant.values()) for (var to : BallisticVariant.values()) if (from != to)
            r.register("explosive_switch_" + from.id() + "_to_" + to.id(), () -> h -> switching(h, from, to));
        for (int rounds : new int[]{0, 4, 5, 9}) r.register("explosive_reload_remainder_" + rounds, () -> h -> reload(h, rounds));
        for (String reason : List.of("variant", "stack", "ammo")) r.register("explosive_reload_cancel_" + reason, () -> h -> cancelReload(h, reason));
        for (String kind : List.of("bare", "armor", "npc")) r.register("explosive_direct_" + kind, () -> h -> direct(h, kind));
        for (int distance : new int[]{40, 50, 61}) r.register("explosive_saved_falloff_" + distance, () -> h -> falloff(h, distance));
        for (String kind : List.of("impact", "damage", "start", "detonate")) r.register("explosive_cancel_" + kind, () -> h -> cancel(h, kind));
        for (boolean unsafe : new boolean[]{false, true}) for (boolean resistant : new boolean[]{false, true})
            r.register("explosive_wall_" + unsafe + "_" + resistant, () -> h -> wall(h, unsafe, resistant));
        r.register("explosive_production_to_magazine_and_shot", () -> ExplosiveAmmoGameTests::production);
        r.register("explosive_full_inventory_and_creative", () -> ExplosiveAmmoGameTests::inventory);
        r.register("explosive_wrong_ammo_and_injected_variants", () -> ExplosiveAmmoGameTests::invalidItems);
        r.register("explosive_spawn_veto_and_cooldown", () -> ExplosiveAmmoGameTests::spawnVeto);
        r.register("explosive_direct_knockback_and_cooldown", () -> ExplosiveAmmoGameTests::knockback);
        r.register("explosive_typed_player_armor", () -> ExplosiveAmmoGameTests::playerArmor);
        r.register("explosive_blast_exposure_radius_and_source", () -> ExplosiveAmmoGameTests::blast);
        r.register("explosive_blast_vanilla_armor", () -> ExplosiveAmmoGameTests::blastArmor);
        r.register("explosive_nonliving_impact_no_blast", () -> ExplosiveAmmoGameTests::nonLiving);
        r.register("explosive_flight_save_and_water", () -> ExplosiveAmmoGameTests::flight);
        r.register("explosive_expiry_and_invalid_saves", () -> ExplosiveAmmoGameTests::expiry);
        r.register("explosive_safe_launch_and_persistence", () -> ExplosiveAmmoGameTests::permission);
        r.register("explosive_npc_equipped_variant", () -> ExplosiveAmmoGameTests::npc);
        r.register("explosive_zero_direct_damage_no_blast", () -> ExplosiveAmmoGameTests::zeroDamage);
    }
    private static void near(GameTestHelper h, double actual, double expected, String text) {
        h.assertTrue(Math.abs(actual - expected) < .001, text + ": " + actual + " vs " + expected);
    }
    private static ItemStack gun(int rounds, BallisticVariant variant) {
        var stack = TGContent.GUNS.get("as50").toStack(); stack.set(TGContent.ROUNDS.get(), rounds);
        stack.set(TGContent.BALLISTIC_VARIANT.get(), variant); return stack;
    }
    private static ItemStack ammo(String id, int count) { return TGContent.AMMO.get(id).toStack(count); }
    private static Player player(GameTestHelper h, int rounds) {
        var p = WeaponGameTests.player(h); p.setItemInHand(InteractionHand.MAIN_HAND, gun(rounds, BallisticVariant.EXPLOSIVE));
        p.setData(SafeMode.SAFE, true); return p;
    }
    private static int count(Player p, String id) {
        int total = 0; for (var stack : p.getInventory()) if (stack.is(TGContent.AMMO.get(id).get())) total += stack.getCount(); return total;
    }
    private static CraftingRecipe recipe(GameTestHelper h, CraftingInput input) {
        return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow().value();
    }
    private static void switching(GameTestHelper h, BallisticVariant from, BallisticVariant to) {
        var source = gun(4, from); source.set(DataComponents.CUSTOM_NAME, Component.literal("AS50 saved name"));
        source.set(TGContent.AIMING.get(), true); source.set(TGContent.RELOAD_TICKS.get(), 40);
        var original = source.copy(); var input = CraftingInput.of(2, 1, List.of(source, ammo(to.ammo(AS50).item(), 1)));
        var recipe = (AmmoChangeRecipe) recipe(h, input); var result = recipe.assemble(input);
        h.assertValueEqual(BallisticAmmo.variant(result), to, "Selected ammunition");
        h.assertValueEqual(GunItem.rounds(result), 10, "Original switch fully loads AS50");
        h.assertValueEqual(result.get(DataComponents.CUSTOM_NAME), source.get(DataComponents.CUSTOM_NAME), "Name retained");
        h.assertTrue(!result.has(TGContent.AIMING.get()) && !result.has(TGContent.RELOAD_TICKS.get()), "Transient actions cleared");
        h.assertTrue(ItemStack.matches(source, original), "Craft preview preserves input");
        h.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), "Old rounds consumed without refunds");
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        h.assertTrue(ItemStack.matches(result, ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, result).getOrThrow()).getOrThrow()), "Saved item retains variant");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, result); h.assertTrue(ItemStack.matches(result, ItemStack.STREAM_CODEC.decode(buffer)), "Item packet");
            AmmoChangeRecipe.STREAM_CODEC.encode(buffer, recipe);
            h.assertTrue(ItemStack.matches(result, AmmoChangeRecipe.STREAM_CODEC.decode(buffer).assemble(input)), "Recipe packet");
        } finally { buffer.release(); }
        h.succeed();
    }
    private static void reload(GameTestHelper h, int rounds) {
        var p = player(h, rounds); var stack = p.getMainHandItem(); p.getInventory().setItem(1, ammo(MAGAZINE, 2));
        p.getInventory().setItem(2, ammo("as50magazine", 3)); p.getInventory().setItem(3, ammo("as50magazine_incendiary", 4));
        h.assertTrue(GunNetwork.handle(p, new GunActionPayload(true)), "R packet starts selected reload");
        for (int n = 0; n < 79; n++) ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack), rounds, "No early rounds"); h.assertValueEqual(count(p, MAGAZINE), 2, "No early consumption");
        ReloadSessions.tick(p); h.assertValueEqual(GunItem.rounds(stack), 10, "80-tick reload complete");
        h.assertValueEqual(count(p, MAGAZINE), 1, "One explosive magazine consumed");
        h.assertValueEqual(count(p, "as50magazineempty"), 1, "One empty returned"); h.assertValueEqual(count(p, LOOSE), rounds / 5, "Only whole original bundles returned");
        h.assertValueEqual(count(p, "as50magazine"), 3, "Default magazine untouched"); h.assertValueEqual(count(p, "as50magazine_incendiary"), 4, "Incendiary untouched");
        h.assertTrue(GunNetwork.handle(p, new GunActionPayload(false)), "Server fires selected projectile");
        var shots = h.getLevel().getEntitiesOfClass(ExplosiveBullet.class, p.getBoundingBox().inflate(4), s -> s.getOwner() == p);
        h.assertValueEqual(shots.size(), 1, "One explosive round"); shots.forEach(Entity::discard);
        h.assertValueEqual(GunItem.rounds(stack), 9, "One round spent"); h.succeed();
    }
    private static void cancelReload(GameTestHelper h, String reason) {
        var p = player(h, 0); var stack = p.getMainHandItem(); p.getInventory().setItem(1, ammo(MAGAZINE, 1)); p.getInventory().setItem(2, ammo("as50magazine", 1));
        h.assertTrue(ReloadSessions.begin(p), "Reload starts");
        if (reason.equals("variant")) stack.set(TGContent.BALLISTIC_VARIANT.get(), BallisticVariant.DEFAULT);
        if (reason.equals("stack")) p.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        if (reason.equals("ammo")) p.getInventory().setItem(1, ItemStack.EMPTY);
        for (int n = 0; n < 80; n++) ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack), 0, "Cancelled reload gives no rounds"); h.assertValueEqual(count(p, "as50magazine"), 1, "No wrong-family fallback");
        h.assertValueEqual(count(p, MAGAZINE), reason.equals("ammo") ? 0 : 1, "No input lost on cancel");
        h.assertTrue(!ReloadSessions.active(p), "Session cleared"); h.succeed();
    }
    private static LivingEntity target(GameTestHelper h, EntityType<? extends Mob> type, Vec3 position) {
        var t = h.spawnWithNoFreeWill(type, position); t.setNoGravity(true);
        t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); t.setHealth(1000); return t;
    }
    private static ExplosiveBullet shot(GameTestHelper h, Vec3 position, boolean unsafe) {
        var s = new ExplosiveBullet(TGContent.EXPLOSIVE_BULLET.get(), h.getLevel()); s.configure(AS50, unsafe);
        s.setOwner(WeaponGameTests.player(h)); s.setPos(position); h.getLevel().addFreshEntity(s); return s;
    }
    private static ExplosiveBullet aimed(GameTestHelper h, LivingEntity target) {
        var s = shot(h, target.position().add(-1, .5, 0), false); s.setDeltaMovement(2, 0, 0); return s;
    }
    private static void direct(GameTestHelper h, String kind) {
        var t = target(h, kind.equals("npc") ? NpcContent.SUPER_MUTANT.get() : EntityTypes.PIG, new Vec3(6, 70, 4));
        if (kind.equals("armor")) { t.getAttribute(Attributes.ARMOR).setBaseValue(30); t.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2); }
        var s = aimed(h, t); int[] explosions = {0};
        Consumer<ExplosionEvent.Start> check = e -> { if (e.getExplosion().getDirectSourceEntity() == s) {
            explosions[0]++; near(h, e.getExplosion().radius(), 1.5, "Actual vanilla power");
            h.assertTrue(e.getExplosion().center().x > s.getX() - .001, "Explosion uses impact center");
        }};
        NeoForge.EVENT_BUS.addListener(check);
        try {
            float armor = t instanceof NpcTypedArmor typed ? typed.armorAgainst(DamageKind.EXPLOSION) : kind.equals("armor") ? 15 : 0;
            float expected = ArmorMath.afterArmor(36.8f, armor, (float)t.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 2);
            s.tick(); near(h, 1000 - t.getHealth(), expected, "Direct EXPLOSION armor and penetration, same-tick blast respects cooldown");
            h.assertValueEqual(explosions[0], 1, "Exactly one explosion on accepted living hit"); h.assertTrue(s.isRemoved(), "Round consumed");
        } finally { NeoForge.EVENT_BUS.unregister(check); s.discard(); t.discard(); } h.succeed();
    }
    private static void falloff(GameTestHelper h, int distance) {
        var t = target(h, EntityTypes.PIG, new Vec3(6, 70, 4)); var s = aimed(h, t); var saved = NetherGameTests.save(h, s);
        saved.putDouble("origin_x", s.getX() - distance); saved.putDouble("origin_y", s.getY()); saved.putDouble("origin_z", s.getZ());
        NetherGameTests.load(h, s, saved);
        try { s.tick(); near(h, 1000 - t.getHealth(), distance == 40 ? 36.8 : distance == 50 ? 32.2 : 27.6, "Saved start-of-tick falloff"); }
        finally { s.discard(); t.discard(); } h.succeed();
    }
    private static void cancel(GameTestHelper h, String kind) {
        var t = target(h, EntityTypes.PIG, new Vec3(6, 70, 4)); var bystander = target(h, EntityTypes.PIG, new Vec3(6, 70, 5.5)); var s = aimed(h, t);
        int[] explosions = {0};
        Consumer<ProjectileImpactEvent> impact = e -> { if (e.getProjectile() == s && kind.equals("impact")) e.setCanceled(true); };
        Consumer<LivingIncomingDamageEvent> damage = e -> { if (e.getSource().getDirectEntity() == s && kind.equals("damage")) e.setCanceled(true); };
        Consumer<ExplosionEvent.Start> start = e -> { if (e.getExplosion().getDirectSourceEntity() == s) { explosions[0]++; if (kind.equals("start")) e.setCanceled(true); }};
        Consumer<ExplosionEvent.Detonate> detonate = e -> { if (e.getExplosion().getDirectSourceEntity() == s && kind.equals("detonate")) e.getAffectedEntities().clear(); };
        NeoForge.EVENT_BUS.addListener(impact); NeoForge.EVENT_BUS.addListener(damage); NeoForge.EVENT_BUS.addListener(start); NeoForge.EVENT_BUS.addListener(detonate);
        try {
            s.tick(); boolean direct = kind.equals("start") || kind.equals("detonate");
            near(h, 1000 - t.getHealth(), direct ? 36.8 : 0, "Direct damage independent of explosion veto");
            near(h, bystander.getHealth(), 1000, "Protected bystander unharmed"); near(h, bystander.getDeltaMovement().length(), 0, "Veto prevents blast impulse");
            h.assertValueEqual(explosions[0], direct ? 1 : 0, "Failed or cancelled direct hit does not explode");
            h.assertValueEqual(s.isRemoved(), !kind.equals("impact"), "Impact cancellation allows flight");
        } finally { NeoForge.EVENT_BUS.unregister(impact); NeoForge.EVENT_BUS.unregister(damage); NeoForge.EVENT_BUS.unregister(start); NeoForge.EVENT_BUS.unregister(detonate); s.discard(); t.discard(); bystander.discard(); }
        h.succeed();
    }
    private static void wall(GameTestHelper h, boolean unsafe, boolean resistant) {
        var material = resistant ? Blocks.OBSIDIAN : Blocks.DIRT; var positions = new ArrayList<BlockPos>();
        for (int y = 69; y <= 73; y++) for (int z = 1; z <= 7; z++) { var p = new BlockPos(5, y, z); h.setBlock(p, material); positions.add(p); }
        var t = target(h, EntityTypes.PIG, new Vec3(6.5, 70, 4.5)); var s = shot(h, h.absoluteVec(new Vec3(4, 70.5, 4.5)), unsafe); s.setDeltaMovement(2, 0, 0);
        Consumer<ExplosionEvent.Detonate> add = e -> { if (e.getExplosion().getDirectSourceEntity() == s && !unsafe) e.getAffectedBlocks().add(h.absolutePos(new BlockPos(5, 70, 4))); };
        NeoForge.EVENT_BUS.addListener(add);
        try {
            s.tick(); near(h, s.getX(), h.absolutePos(new BlockPos(5, 70, 4)).getX(), "Blast at block hit face, not pre-movement position");
            near(h, 1000 - t.getHealth(), 1, "Vanilla fully occluded baseline before terrain destruction");
            h.assertValueEqual(h.getBlockState(new BlockPos(5, 70, 4)).isAir(), unsafe && !resistant, "B, resistance and authoritative KEEP");
            h.assertTrue(positions.stream().noneMatch(p -> h.getBlockState(p).is(Blocks.FIRE)), "Explosive ammunition never ignites");
        } finally { NeoForge.EVENT_BUS.unregister(add); t.discard(); s.discard(); positions.forEach(p -> h.setBlock(p, Blocks.AIR)); } h.succeed();
    }
    private static void production(GameTestHelper h) {
        var pos = new BlockPos(4, 2, 4); h.setBlock(pos, TGMachineContent.METAL_PRESS.get());
        var machine = (MetalPressBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
        machine.setItem(0, ammo("sniperrounds_incendiary", 2)); machine.setItem(1, TGContent.MATERIALS.get("tgx").toStack(2));
        try (var tx = Transaction.openRoot()) { h.assertValueEqual(machine.energy().insert(4000, tx), 4000, "Two source cycles powered"); tx.commit(); }
        // Drive actual machine ticks, retaining the asynchronous recipe-check interval.
        for (int i = 0; i < 2; i++) {
            for (int n = 0; n < 101; n++) ProcessingMachineBlockEntity.tick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
            if (i == 0) machine.setItem(0, machine.getItem(0).copy());
        }
        h.assertTrue(machine.getItem(2).is(TGContent.AMMO.get(LOOSE).get()), "Metal Press produces explosive rounds");
        h.assertValueEqual(machine.getItem(2).getCount(), 2, "Two bundles produced");
        h.assertValueEqual(machine.energy().getAmountAsLong(), 0L, "4000 FE used");
        var input = CraftingInput.of(3, 1, List.of(ammo("as50magazineempty", 1), machine.getItem(2).copyWithCount(1), machine.getItem(2).copyWithCount(1)));
        var magazine = recipe(h, input).assemble(input); h.assertTrue(magazine.is(TGContent.AMMO.get(MAGAZINE).get()), "Original full magazine crafted");
        var switchInput = CraftingInput.of(2, 1, List.of(gun(0, BallisticVariant.DEFAULT), magazine)); var p = player(h, 0);
        p.setItemInHand(InteractionHand.MAIN_HAND, recipe(h, switchInput).assemble(switchInput));
        h.assertTrue(GunItem.fire(h.getLevel(), p, p.getMainHandItem()), "Produced ammunition fires");
        var shots = h.getLevel().getEntitiesOfClass(ExplosiveBullet.class, p.getBoundingBox().inflate(4), s -> s.getOwner() == p);
        h.assertValueEqual(shots.size(), 1, "Actual explosive entity"); shots.forEach(Entity::discard); h.succeed();
    }
    private static void inventory(GameTestHelper h) {
        var p = player(h, 9); for (int i = 1; i < p.getInventory().getContainerSize(); i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        p.getInventory().setItem(1, ammo(MAGAZINE, 2)); GunItem.completeReload(p, p.getMainHandItem());
        for (String id : List.of("as50magazineempty", LOOSE)) {
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3), e -> e.getItem().is(TGContent.AMMO.get(id).get()));
            h.assertValueEqual(drops.stream().mapToInt(e -> e.getItem().getCount()).sum(), 1, "Exact overflow remainder " + id); drops.forEach(Entity::discard);
        }
        var creative = player(h, 0); creative.getAbilities().instabuild = true;
        h.assertTrue(ReloadSessions.begin(creative), "Creative R without ammunition"); for (int i = 0; i < 80; i++) ReloadSessions.tick(creative);
        h.assertValueEqual(GunItem.rounds(creative.getMainHandItem()), 10, "Creative reload complete"); h.assertValueEqual(count(creative, "as50magazineempty"), 0, "No creative refund duplication"); h.succeed();
    }
    private static void invalidItems(GameTestHelper h) {
        h.assertTrue(BallisticAmmo.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("explosive")).result().isPresent(), "Explosive codec supported");
        h.assertTrue(BallisticAmmo.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("EXPLOSIVE")).error().isPresent(), "Unknown values rejected");
        for (var definition : Weapons.ALL) if (!definition.id().equals("as50")) {
            var stack = TGContent.GUNS.get(definition.id()).toStack(); stack.set(TGContent.BALLISTIC_VARIANT.get(), BallisticVariant.EXPLOSIVE);
            h.assertValueEqual(BallisticAmmo.variant(stack), BallisticVariant.DEFAULT, "Injected explosive variant rejected for " + definition.id());
        }
        var p = player(h, 0); p.getInventory().setItem(1, ammo("as50magazine", 3)); p.getInventory().setItem(2, ammo("as50magazine_incendiary", 3));
        h.assertTrue(!ReloadSessions.begin(p), "No fallback to other AS50 magazines");
        for (var wrong : List.of(ammo(LOOSE, 1), ammo("as50magazineempty", 1), ammo("lmgmagazine", 1))) {
            var input = CraftingInput.of(2, 1, List.of(p.getMainHandItem(), wrong));
            h.assertTrue(h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).isEmpty(), "No loose/empty/wrong-family switch");
        } h.succeed();
    }
    private static void spawnVeto(GameTestHelper h) {
        var p = player(h, 10); var stack = p.getMainHandItem();
        Consumer<EntityJoinLevelEvent> veto = e -> { if (e.getEntity() instanceof ExplosiveBullet s && s.getOwner() == p) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(veto);
        try { h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "Spawn veto rejects shot"); h.assertValueEqual(GunItem.rounds(stack), 10, "No ammo lost"); h.assertTrue(!p.getCooldowns().isOnCooldown(stack), "No false cooldown"); }
        finally { NeoForge.EVENT_BUS.unregister(veto); }
        h.assertTrue(GunItem.fire(h.getLevel(), p, stack), "Accepted spawn"); h.assertTrue(!GunItem.fire(h.getLevel(), p, stack), "Duplicate fire rejected");
        h.getLevel().getEntitiesOfClass(ExplosiveBullet.class, p.getBoundingBox().inflate(4), s -> s.getOwner() == p).forEach(Entity::discard); h.succeed();
    }
    private static void knockback(GameTestHelper h) {
        var t = target(h, EntityTypes.PIG, new Vec3(6, 70, 4)); var s = aimed(h, t); float[] strength = {0}; int[] starts = {0};
        Consumer<ExplosionEvent.Start> cancel = e -> { if (e.getExplosion().getDirectSourceEntity() instanceof ExplosiveBullet) { starts[0]++; e.setCanceled(true); }};
        Consumer<LivingKnockBackEvent> impulse = e -> { if (e.getEntity() == t) strength[0] = e.getStrength(); };
        NeoForge.EVENT_BUS.addListener(cancel); NeoForge.EVENT_BUS.addListener(impulse);
        try {
            s.tick(); near(h, strength[0], 1.2, "Original direct knockback multiplier three");
            var repeated = aimed(h, t); repeated.tick(); near(h, 1000 - t.getHealth(), 36.8, "Ordinary cooldown suppresses repeated impact");
            h.assertValueEqual(starts[0], 1, "Cooldown-rejected hit does not detonate"); repeated.discard();
        } finally { NeoForge.EVENT_BUS.unregister(cancel); NeoForge.EVENT_BUS.unregister(impulse); s.discard(); t.discard(); } h.succeed();
    }
    private static void playerArmor(GameTestHelper h) {
        var p = ArmorGameTests.player(h); ArmorGameTests.equipAll(p); var s = shot(h, p.position().add(2, 1, 0), false);
        try {
            var source = s.shotDamage().source(h.getLevel(), ExplosiveBullet.DAMAGE_TYPE, s);
            h.assertTrue(source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypeTags.IS_PROJECTILE), "Direct hit explosion classification");
            p.hurtServer(h.getLevel(), source, 10); near(h, 1000 - p.getHealth(), 6.2, "Typed T2 explosion absorption with raw penetration two per piece");
        } finally { s.discard(); } h.succeed();
    }
    private static void blast(GameTestHelper h) {
        for (double distance : new double[]{0, 1.5, 3, 3.01}) {
            var t = target(h, EntityTypes.PIG, new Vec3(6, 70, 4)); var s = shot(h, t.position().add(-distance, 0, 0), false); s.npcDamage(0);
            int[] events = {0}; Consumer<LivingIncomingDamageEvent> listener = e -> { if (e.getEntity() == t) {
                events[0]++; h.assertTrue(e.getSource().is(DamageTypes.EXPLOSION), "Vanilla explosion source");
                h.assertTrue(e.getSource().getEntity() == null && e.getSource().getDirectEntity() == null, "Original splash has no shooter or penetration");
            }};
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                h.assertTrue(s.explode(), "Explosion accepted"); near(h, 1000 - t.getHealth(), distance == 0 ? 22 : distance == 1.5 ? 8 : distance == 3 ? 1 : 0, "Native integer radius/exposure and no NPC scaling");
                h.assertTrue(!s.explode(), "Explosion cannot repeat"); h.assertValueEqual(events[0], distance > 3 ? 0 : 1, "Radius enforced");
            } finally { NeoForge.EVENT_BUS.unregister(listener); s.discard(); t.discard(); }
        } h.succeed();
    }
    private static void blastArmor(GameTestHelper h) {
        var t = target(h, EntityTypes.PIG, new Vec3(6, 70, 4)); t.getAttribute(Attributes.ARMOR).setBaseValue(20);
        var s = shot(h, t.position().add(-1.5, 0, 0), false);
        try { s.explode(); near(h, 1000 - t.getHealth(), 2.88, "Splash uses vanilla armor, not direct AS50 penetration"); }
        finally { s.discard(); t.discard(); } h.succeed();
    }
    private static void nonLiving(GameTestHelper h) {
        var boat = h.spawn(EntityTypes.OAK_BOAT, new Vec3(6, 70, 4)); boat.setNoGravity(true);
        var s = shot(h, boat.position().add(-2, .25, 0), false); s.setDeltaMovement(4, 0, 0); int[] blasts = {0};
        Consumer<ExplosionEvent.Start> listener = e -> { if (e.getExplosion().getDirectSourceEntity() == s) blasts[0]++; }; NeoForge.EVENT_BUS.addListener(listener);
        try { s.tick(); h.assertTrue(s.isRemoved(), "Boat consumes impact"); h.assertValueEqual(blasts[0], 0, "Non-living direct hit never calls explosion hook"); }
        finally { NeoForge.EVENT_BUS.unregister(listener); s.discard(); boat.discard(); } h.succeed();
    }
    private static void flight(GameTestHelper h) {
        var s = shot(h, h.absoluteVec(new Vec3(4, 80, 4)), true); s.npcDamage(.5f); s.setDeltaMovement(.4, .2, .1); var start = s.position(); s.tick();
        near(h, s.getX(), start.x + .4, "Movement before drag"); near(h, s.getDeltaMovement().y, .2 * (double).99f, "No forwarded gravity");
        var restored = new ExplosiveBullet(TGContent.EXPLOSIVE_BULLET.get(), h.getLevel()); NetherGameTests.load(h, restored, NetherGameTests.save(h, s));
        h.assertTrue(restored.damagesBlocks() && restored.shotDamage().npc(), "Launch policy and NPC state saved"); near(h, restored.shotDamage().scale(), .5, "Saved scale");
        h.assertValueEqual(restored.age(), 1, "Age saved"); s.tick(); restored.tick(); h.assertValueEqual(restored.position(), s.position(), "Reloaded trajectory"); s.discard(); restored.discard();
        h.setBlock(4, 2, 4, Blocks.WATER); var water = shot(h, h.absoluteVec(new Vec3(4.5, 2.2, 4.5)), false); water.setDeltaMovement(.1, 0, 0); water.tick();
        h.assertTrue(!water.isRemoved() && water.isInWater(), "Water does not explode or extinguish bullet"); near(h, water.getDeltaMovement().x, .1 * (double).85f, "Source water drag"); water.discard(); h.succeed();
    }
    private static void expiry(GameTestHelper h) {
        var s = shot(h, h.absoluteVec(new Vec3(4, 80, 4)), false); var saved = NetherGameTests.save(h, s); int[] blasts = {0};
        Consumer<ExplosionEvent.Start> listener = e -> { if (e.getExplosion().getDirectSourceEntity() == s) blasts[0]++; }; NeoForge.EVENT_BUS.addListener(listener);
        try { for (int i = 0; i < 89; i++) s.tick(); h.assertTrue(!s.isRemoved(), "Lives through 89 ticks"); s.tick(); h.assertTrue(s.isRemoved(), "Expires at 90"); h.assertValueEqual(blasts[0], 0, "TTL never explodes"); }
        finally { NeoForge.EVENT_BUS.unregister(listener); s.discard(); }
        for (String key : List.of("weapon", "negative_age", "expired", "origin", "nan", "scale")) {
            var broken = saved.copy();
            switch (key) { case "weapon" -> broken.putString("weapon", "revolver"); case "negative_age" -> broken.putInt("age", -1); case "expired" -> broken.putInt("age", 90);
                case "origin" -> broken.putDouble("origin_x", 1); case "nan" -> { broken.putDouble("origin_x", Double.NaN); broken.putDouble("origin_y", 0); broken.putDouble("origin_z", 0); } case "scale" -> broken.putFloat("damage_scale", -1); }
            var bad = new ExplosiveBullet(TGContent.EXPLOSIVE_BULLET.get(), h.getLevel()); NetherGameTests.load(h, bad, broken); h.assertTrue(bad.isRemoved(), "Reject damaged save: " + key);
        } h.succeed();
    }
    private static void permission(GameTestHelper h) {
        boolean before = SafeMode.OP_ONLY.get();
        try {
            for (int mode = 0; mode < 3; mode++) {
                var p = player(h, 10); p.setData(SafeMode.SAFE, mode == 0); SafeMode.OP_ONLY.set(mode == 2);
                h.assertTrue(GunItem.fire(h.getLevel(), p, p.getMainHandItem()), "Safe/unsafe/denied player may fire");
                var shots = h.getLevel().getEntitiesOfClass(ExplosiveBullet.class, p.getBoundingBox().inflate(4), e -> e.getOwner() == p);
                h.assertValueEqual(shots.size(), 1, "One projectile"); var s = shots.getFirst();
                h.assertValueEqual(s.damagesBlocks(), mode == 1, "B and OP restriction evaluated at launch");
                p.setData(SafeMode.SAFE, true); var restored = new ExplosiveBullet(TGContent.EXPLOSIVE_BULLET.get(), h.getLevel()); NetherGameTests.load(h, restored, NetherGameTests.save(h, s));
                h.assertValueEqual(restored.damagesBlocks(), mode == 1, "Save retains launch-time policy after B change"); s.discard(); restored.discard();
            }
        } finally { SafeMode.OP_ONLY.set(before); } h.succeed();
    }
    private static void npc(GameTestHelper h) {
        var mob = h.spawnWithNoFreeWill(NpcContent.BANDIT.get(), new Vec3(3, 70, 4)); var t = target(h, EntityTypes.PIG, new Vec3(6, 70, 4));
        mob.setItemSlot(EquipmentSlot.MAINHAND, gun(0, BallisticVariant.EXPLOSIVE)); mob.setNoGravity(true);
        try {
            h.assertTrue(NpcCombat.fire(mob, t), "NPC honors equipped AS50 ammunition");
            var shots = h.getLevel().getEntitiesOfClass(ExplosiveBullet.class, mob.getBoundingBox().inflate(4), s -> s.getOwner() == mob);
            h.assertValueEqual(shots.size(), 1, "NPC creates explosive projectile"); h.assertTrue(shots.getFirst().shotDamage().npc() && !shots.getFirst().damagesBlocks(), "NPC scale and no terrain damage"); shots.forEach(Entity::discard);
        } finally { mob.discard(); t.discard(); } h.succeed();
    }
    private static void zeroDamage(GameTestHelper h) {
        var t = target(h, EntityTypes.PIG, new Vec3(6, 70, 4)); var s = aimed(h, t); s.npcDamage(0); int[] blasts = {0};
        Consumer<ExplosionEvent.Start> listener = e -> { if (e.getExplosion().getDirectSourceEntity() == s) blasts[0]++; }; NeoForge.EVENT_BUS.addListener(listener);
        try { s.tick(); near(h, t.getHealth(), 1000, "Zero direct damage suppressed"); h.assertValueEqual(blasts[0], 0, "No living hit effect without positive accepted damage"); }
        finally { NeoForge.EVENT_BUS.unregister(listener); s.discard(); t.discard(); } h.succeed();
    }
}
