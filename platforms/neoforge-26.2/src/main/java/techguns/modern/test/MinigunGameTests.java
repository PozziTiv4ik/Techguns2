package techguns.modern.test;

import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.network.*;

final class MinigunGameTests {
    private static final GunActionPayload FIRE = new GunActionPayload(false), RELOAD = new GunActionPayload(true);

    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for (var variant : BallisticVariant.values()) {
            String id = variant.id();
            r.register("minigun_held_200_ticks_" + id, () -> h -> held(h, variant));
            r.register("minigun_reload_100_ticks_" + id, () -> h -> reload(h, variant));
            r.register("minigun_packed_drum_" + id, () -> h -> drum(h, variant));
            r.register("minigun_real_repeated_hits_" + id, () -> h -> hits(h, variant));
            r.register("minigun_saved_flight_" + id, () -> h -> flight(h, variant));
        }
        for (boolean empty : new boolean[]{false, true})
            r.register("minigun_craft_" + (empty ? "empty" : "loaded"), () -> h -> craftGun(h, empty));
        for (String mode : List.of("stack", "variant", "ammo", "death"))
            r.register("minigun_reload_cancel_" + mode, () -> h -> cancelReload(h, mode));
        r.register("minigun_empty_drum_craft", () -> MinigunGameTests::emptyDrum);
        r.register("minigun_packet_spam_swap_and_creative", () -> MinigunGameTests::spam);
        r.register("minigun_spawn_veto_is_atomic", () -> MinigunGameTests::spawnVeto);
        r.register("minigun_full_inventory_remainders", () -> MinigunGameTests::fullInventory);
        r.register("minigun_sound_spin_and_item_save", () -> MinigunGameTests::effects);
    }

    private static ItemStack gun(int rounds, BallisticVariant variant) {
        var stack = TGContent.GUNS.get("minigun").toStack();
        stack.set(TGContent.ROUNDS.get(), rounds);
        stack.set(TGContent.BALLISTIC_VARIANT.get(), variant);
        return stack;
    }
    private static Player player(GameTestHelper h, int rounds, BallisticVariant variant) {
        var p = WeaponGameTests.player(h);
        p.setPos(h.absoluteVec(new Vec3(3, 80, 3)));
        p.setItemInHand(InteractionHand.MAIN_HAND, gun(rounds, variant));
        return p;
    }
    private static List<Projectile> shots(GameTestHelper h, Player p) {
        return h.getLevel().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(6), s -> s.getOwner() == p);
    }
    private static int count(Player p, String name) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            var s = p.getInventory().getItem(i);
            if (s.is(TGContent.AMMO.get(name).get())) n += s.getCount();
        }
        return n;
    }
    private static void near(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < .002, message + ": " + actual + " / " + expected);
    }

    private static void held(GameTestHelper h, BallisticVariant variant) {
        var p = player(h, 200, variant); var stack = p.getMainHandItem();
        for (int shot = 0; shot < 200; shot++) {
            final int index = shot;
            h.runAfterDelay(shot + 1, () -> {
                p.tick(); // Mock players are not world-listed: run the native player tick once per real server tick.
                h.assertTrue(GunNetwork.handle(p, FIRE), "Held trigger fires on tick " + index);
                for (int duplicate = 0; duplicate < 8; duplicate++)
                    h.assertTrue(!GunNetwork.handle(p, FIRE), "Same-tick packets never multiply a shot");
                h.assertValueEqual(GunItem.rounds(stack), 199 - index, "One round consumed, including last round");
                var fired = shots(h, p);
                h.assertValueEqual(fired.size(), 1, "One real projectile added each tick");
                h.assertValueEqual(fired.getFirst() instanceof IncendiaryBullet, variant == BallisticVariant.INCENDIARY, "Selected projectile factory");
                fired.forEach(Entity::discard);
            });
        }
        h.runAfterDelay(201, () -> {
            p.tick(); h.assertTrue(!GunNetwork.handle(p, FIRE), "Empty drum without spare cannot fire or reload");
            h.assertTrue(!ReloadSessions.active(p), "No invented ammunition"); p.discard(); h.succeed();
        });
    }

    private static void reload(GameTestHelper h, BallisticVariant variant) {
        int initial = variant == BallisticVariant.DEFAULT ? 0 : 99;
        var p = player(h, initial, variant); var stack = p.getMainHandItem(); var ammo = BallisticAmmo.ammo(stack);
        p.getInventory().setItem(1, TGContent.AMMO.get(ammo.item()).toStack(2));
        h.assertTrue(GunNetwork.handle(p, initial == 0 ? FIRE : RELOAD), "Empty trigger or R starts reload");
        for (int tick = 1; tick <= 100; tick++) {
            final int n = tick;
            h.runAfterDelay(tick, () -> {
                p.tick();
                if (n < 100) {
                    h.assertValueEqual(GunItem.rounds(stack), initial, "No early ammunition");
                    h.assertValueEqual(count(p, ammo.item()), 2, "No early consumption");
                    h.assertTrue(!GunNetwork.handle(p, FIRE) && !GunNetwork.handle(p, RELOAD), "Packets cannot finish or restart reload");
                } else {
                    h.assertValueEqual(GunItem.rounds(stack), 200, "One hundred native player ticks fill drum");
                    h.assertValueEqual(count(p, ammo.item()), 1, "Exactly one selected drum consumed");
                    h.assertValueEqual(count(p, ammo.emptyItem()), 1, "One empty drum returned");
                    h.assertValueEqual(count(p, ammo.looseItem()), initial * 16 / 200, "Only whole original rifle bundles returned");
                    h.assertTrue(!ReloadSessions.active(p) && !stack.has(TGContent.RELOAD_TICKS.get()), "Reload session ends");
                    h.assertTrue(GunNetwork.handle(p, FIRE), "Held trigger resumes after reload");
                    shots(h, p).forEach(Entity::discard); p.discard(); h.succeed();
                }
            });
        }
    }

    private static void cancelReload(GameTestHelper h, String mode) {
        var p = player(h, 99, BallisticVariant.INCENDIARY); var stack = p.getMainHandItem();
        p.getInventory().setItem(1, TGContent.AMMO.get("minigundrum_incendiary").toStack(2));
        h.assertTrue(GunNetwork.handle(p, RELOAD), "Partial reload starts");
        for (int tick = 0; tick < 99; tick++) p.tick();
        switch (mode) {
            case "stack" -> p.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
            case "variant" -> stack.set(TGContent.BALLISTIC_VARIANT.get(), BallisticVariant.DEFAULT);
            case "ammo" -> p.getInventory().setItem(1, ItemStack.EMPTY);
            case "death" -> p.setHealth(0);
        }
        p.tick();
        h.assertValueEqual(GunItem.rounds(stack), 99, "Changed conditions never create ammunition");
        h.assertValueEqual(count(p, "minigundrumempty"), 0, "No duplicate empty drum");
        h.assertValueEqual(count(p, "minigundrum_incendiary"), mode.equals("ammo") ? 0 : 2, "No cancelled consumption");
        h.assertTrue(!ReloadSessions.active(p) && !stack.has(TGContent.RELOAD_TICKS.get()), "Session cleaned");
        p.discard(); h.succeed();
    }

    private static void spam(GameTestHelper h) {
        var p = player(h, 1, BallisticVariant.DEFAULT); var first = p.getMainHandItem();
        h.assertTrue(GunNetwork.handle(p, FIRE), "Last round fires");
        var other = gun(200, BallisticVariant.INCENDIARY); p.setItemInHand(InteractionHand.MAIN_HAND, other);
        for (int i = 0; i < 64; i++) h.assertTrue(!GunNetwork.handle(p, FIRE), "Copied stack and variant cannot bypass shared cooldown");
        p.setItemInHand(InteractionHand.OFF_HAND, other); p.setItemInHand(InteractionHand.MAIN_HAND, first);
        h.assertTrue(!GunItem.fire(h.getLevel(), p, other), "Other hand cannot bypass shared cooldown");
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); p.setItemInHand(InteractionHand.MAIN_HAND, other);
        p.getAbilities().instabuild = true; p.tick();
        h.assertTrue(GunNetwork.handle(p, FIRE), "Creative follows the same tick gate");
        h.assertValueEqual(GunItem.rounds(other), 200, "Legacy Creative preserves loaded rounds");
        h.assertTrue(!GunNetwork.handle(p, FIRE), "Creative spam rejected");
        p.tick(); other.set(TGContent.ROUNDS.get(), 0);
        h.assertTrue(GunNetwork.handle(p, FIRE), "Empty Creative trigger reloads");
        for (int i = 0; i < 100; i++) p.tick();
        h.assertValueEqual(GunItem.rounds(other), 200, "Creative refill without inventory");
        h.assertValueEqual(count(p, "minigundrumempty"), 0, "Creative never creates empty drums");
        shots(h, p).forEach(Entity::discard); p.discard(); h.succeed();
    }

    private static void spawnVeto(GameTestHelper h) {
        var p = player(h, 1, BallisticVariant.DEFAULT); var stack = p.getMainHandItem();
        Consumer<EntityJoinLevelEvent> veto = e -> { if (e.getEntity() instanceof Bullet b && b.getOwner() == p) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            h.assertTrue(!GunNetwork.handle(p, FIRE), "Spawn veto rejects shot");
            h.assertValueEqual(GunItem.rounds(stack), 1, "No lost last round");
            h.assertTrue(!p.getCooldowns().isOnCooldown(stack) && !stack.has(TGContent.MINIGUN_SPIN_TIME.get()), "No cooldown or animation on veto");
        } finally { NeoForge.EVENT_BUS.unregister(veto); }
        h.assertTrue(GunNetwork.handle(p, FIRE), "Retry can use same last round");
        shots(h, p).forEach(Entity::discard); p.discard(); h.succeed();
    }

    private static ItemStack item(String id) {
        return TGContent.AMMO.containsKey(id) ? TGContent.AMMO.get(id).toStack() : TGContent.MATERIALS.get(id).toStack();
    }
    private static ItemStack craft(GameTestHelper h, String id, int width, int height, List<ItemStack> items) {
        var input = CraftingInput.of(width, height, items);
        var recipe = (CraftingRecipe) h.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, TGContent.id(id))).orElseThrow().value();
        h.assertTrue(recipe.matches(input, h.getLevel()), "Native recipe accepts original inputs: " + id);
        return recipe.assemble(input);
    }
    private static void craftGun(GameTestHelper h, boolean empty) {
        var result = craft(h, "minigun" + (empty ? "_alt" : ""), 3, 3, List.of(
                item("obsidiansteelbarrel"), item("obsidiansteelbarrel"), item("electricengine"),
                item("obsidiansteelbarrel"), item("obsidiansteelbarrel"), item("obsidiansteelreceiver"),
                item("obsidiansteelbarrel"), item("obsidiansteelbarrel"), item(empty ? "minigundrumempty" : "minigundrum")));
        h.assertTrue(result.getItem() instanceof MinigunItem, "Crafted usable weapon");
        h.assertValueEqual(GunItem.rounds(result), empty ? 0 : 200, "Loaded versus empty source recipe"); h.succeed();
    }
    private static void emptyDrum(GameTestHelper h) {
        var result = craft(h, "minigundrumempty", 3, 3, List.of(item("ingotsteel"), item("ingotsteel"), item("ingotsteel"),
                item("plasticsheet"), item("mechanicalpartsobsidiansteel"), item("plasticsheet"), item("ingotsteel"), item("ingotsteel"), item("ingotsteel")));
        h.assertTrue(result.is(TGContent.AMMO.get("minigundrumempty").get()), "Empty shell result");
        h.assertValueEqual(result.getCount(), 4, "Four drums per source craft"); h.succeed();
    }
    private static void drum(GameTestHelper h, BallisticVariant variant) {
        String suffix = variant == BallisticVariant.DEFAULT ? "" : "_incendiary";
        var inputs = new ArrayList<ItemStack>(); inputs.add(item("minigundrumempty"));
        for (int i = 0; i < 4; i++) inputs.add(item("rifleroundsstack" + suffix));
        inputs.add(ItemStack.EMPTY);
        var result = craft(h, "minigundrum" + suffix, 3, 2, inputs);
        h.assertTrue(result.is(TGContent.AMMO.get("minigundrum" + suffix).get()), "Four packed stacks fill correct drum");
        h.assertValueEqual(result.getCount(), 1, "Single loaded drum"); h.succeed();
    }

    private static void fullInventory(GameTestHelper h) {
        var p = player(h, 99, BallisticVariant.INCENDIARY);
        for (int i = 1; i < p.getInventory().getContainerSize(); i++) p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        p.getInventory().setItem(1, TGContent.AMMO.get("minigundrum_incendiary").toStack(2));
        h.assertTrue(GunNetwork.handle(p, RELOAD), "Full inventory still permits drum swap");
        for (int i = 0; i < 100; i++) p.tick();
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3));
        h.assertValueEqual(drops.stream().filter(e -> e.getItem().is(TGContent.AMMO.get("minigundrumempty").get())).mapToInt(e -> e.getItem().getCount()).sum(), 1, "Empty drum drops without loss");
        h.assertValueEqual(drops.stream().filter(e -> e.getItem().is(TGContent.AMMO.get("riflerounds_incendiary").get())).mapToInt(e -> e.getItem().getCount()).sum(), 7, "Seven complete leftover bundles drop");
        drops.forEach(Entity::discard); p.discard(); h.succeed();
    }

    private static void hits(GameTestHelper h, BallisticVariant variant) {
        var p = player(h, 3, variant); p.setYRot(0); p.setYHeadRot(0); p.setXRot(0);
        var target = h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new Vec3(3, 80, 6)); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
        for (int i = 0; i < 3; i++) {
            p.tick(); h.assertTrue(GunNetwork.handle(p, FIRE), "Real network shot");
            var fired = shots(h, p); h.assertValueEqual(fired.size(), 1, "One outgoing round");
            fired.getFirst().tick(); h.assertTrue(fired.getFirst().isRemoved(), "Projectile actually collides");
        }
        near(h, 1000 - target.getHealth(), variant == BallisticVariant.DEFAULT ? 15 : 16.51, "Three consecutive hits bypass hurt cooldown");
        if (variant == BallisticVariant.INCENDIARY) h.assertTrue(target.isOnFire(), "Actual incendiary hits ignite target");
        target.discard(); p.discard(); h.succeed();
    }

    private static void flight(GameTestHelper h, BallisticVariant variant) {
        var p = player(h, 1, variant); h.assertTrue(GunNetwork.handle(p, FIRE), "Shot for persistence");
        var shot = shots(h, p).getFirst(); shot.setDeltaMovement(.2, .1, 0); shot.tick();
        Projectile restored = variant == BallisticVariant.DEFAULT ? new Bullet(TGContent.BULLET.get(), h.getLevel()) : new IncendiaryBullet(TGContent.INCENDIARY_BULLET.get(), h.getLevel());
        NetherGameTests.load(h, restored, NetherGameTests.save(h, shot));
        h.assertTrue(!restored.isRemoved(), "Saved source weapon accepted");
        for (int i = 0; i < 8; i++) {
            shot.tick(); restored.tick(); near(h, shot.position().distanceTo(restored.position()), 0, "Saved trajectory resumes");
        }
        h.assertValueEqual(restored instanceof Bullet b ? b.weapon().id() : ((IncendiaryBullet) restored).weapon().id(), "minigun", "Weapon profile survives save");
        shot.discard(); restored.discard(); p.discard(); h.succeed();
    }

    private static void effects(GameTestHelper h) {
        var p = player(h, 10, BallisticVariant.DEFAULT); var stack = p.getMainHandItem(); var sounds = new ArrayList<String>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = e -> {
            if (e.getPosition().distanceTo(p.position()) < .01 && e.getSound() != null) sounds.add(e.getSound().value().location().getPath());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            h.assertTrue(GunNetwork.handle(p, FIRE), "Accepted shot starts spin and sound");
            long first = stack.get(TGContent.MINIGUN_SPIN_TIME.get());
            p.tick(); h.assertTrue(GunNetwork.handle(p, FIRE), "Next player tick fires");
            h.assertValueEqual(stack.get(TGContent.MINIGUN_SPIN_TIME.get()), first, "Active five-tick spin is not reset");
            h.assertValueEqual(sounds, List.of("guns.minigunfire", "guns.minigunfire"), "Finite source samples, no invented start loop");
            var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var saved = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()).getOrThrow();
            h.assertValueEqual(GunItem.rounds(saved), 8, "Rounds persist");
            h.assertTrue(!saved.has(TGContent.MINIGUN_SPIN_TIME.get()), "Spin clock never persists");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try { ItemStack.STREAM_CODEC.encode(buffer, stack); h.assertTrue(ItemStack.matches(stack, ItemStack.STREAM_CODEC.decode(buffer)), "Spin and rounds synchronize"); }
            finally { buffer.release(); }
            stack.set(TGContent.MINIGUN_SPIN_TIME.get(), h.getLevel().getGameTime() - 5);
            p.tick(); h.assertTrue(GunNetwork.handle(p, FIRE), "New spin cycle");
            h.assertValueEqual(stack.get(TGContent.MINIGUN_SPIN_TIME.get()), h.getLevel().getGameTime(), "Full five-tick cycle restarts");
            p.tick(); p.getInventory().setItem(1, item("minigundrum"));
            h.assertTrue(GunNetwork.handle(p, RELOAD), "R after shot");
            h.assertValueEqual(sounds.getLast(), "guns.minigunreload", "Source reload sample");
        } finally { NeoForge.EVENT_BUS.unregister(listener); ReloadSessions.cancel(p); shots(h, p).forEach(Entity::discard); p.discard(); }
        h.succeed();
    }
}
