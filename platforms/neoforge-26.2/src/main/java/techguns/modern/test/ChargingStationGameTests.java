package techguns.modern.test;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.item.ItemResource;
import techguns.modern.GunItem;
import techguns.modern.ReloadSessions;
import techguns.modern.TGContent;
import techguns.modern.machine.ProcessingMachineBlock;
import techguns.modern.machine.SplitIntContainerData;
import techguns.modern.machine.TGMachineConfig;
import techguns.modern.machine.charging.*;
import techguns.modern.machine.fabricator.FabricatorBlockEntity;

final class ChargingStationGameTests {
    private static final BlockPos POS = new BlockPos(4, 2, 4);
    private record Case(String empty, String full, int ticks, int energy) {}
    private static final List<Case> CASES = List.of(new Case("energycellempty", "energycell", 62, 49600), new Case("redstone_battery_empty", "redstone_battery", 25, 20000));
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        for (Case c : CASES) for (int batch : new int[]{1,8})
            registry.register("charging_recipe_" + c.full + "_" + batch, () -> h -> production(h, c, batch));
        registry.register("charging_energy_pause", () -> ChargingStationGameTests::powerPause);
        registry.register("charging_redstone_pause", () -> ChargingStationGameTests::redstone);
        registry.register("charging_blocked_finished_batch", () -> ChargingStationGameTests::blockedBatch);
        registry.register("charging_batch_space_limit", () -> ChargingStationGameTests::space);
        registry.register("charging_upgrade_snapshot", () -> ChargingStationGameTests::upgradeSnapshot);
        registry.register("charging_saved_batch", () -> ChargingStationGameTests::savedBatch);
        registry.register("charging_break_refunds_input", () -> ChargingStationGameTests::breakBatch);
        registry.register("charging_ports_and_transactions", () -> ChargingStationGameTests::ports);
        registry.register("charging_original_crafting", () -> ChargingStationGameTests::crafting);
        registry.register("charging_recipe_codec", () -> ChargingStationGameTests::codec);
        registry.register("charging_menu_security_full_energy", () -> ChargingStationGameTests::menu);
        registry.register("charging_render_item_codec", () -> ChargingStationGameTests::renderData);
        registry.register("charging_direct_full_cycle", () -> ChargingStationGameTests::direct);
        registry.register("charging_direct_partial_power", () -> ChargingStationGameTests::partialPower);
        registry.register("charging_direct_empty_buffer", () -> ChargingStationGameTests::noPower);
        registry.register("charging_direct_receiver_limit", () -> ChargingStationGameTests::receiverLimit);
        registry.register("charging_direct_large_stack_energy", () -> ChargingStationGameTests::stackEnergy);
        registry.register("charging_direct_rejecting_receiver", () -> ChargingStationGameTests::refusingItem);
        registry.register("charging_direct_save", () -> ChargingStationGameTests::savedItem);
        registry.register("charging_direct_break", () -> ChargingStationGameTests::breakItem);
        registry.register("charging_direct_blocked_output", () -> ChargingStationGameTests::blockedItem);
        registry.register("charging_direct_item_replacement", () -> ChargingStationGameTests::transform);
        registry.register("charging_direct_replacement_save", () -> ChargingStationGameTests::savedReplacement);
        registry.register("charging_direct_replacement_input_swap", () -> ChargingStationGameTests::swappedReplacement);
        registry.register("charging_no_power_setting", () -> ChargingStationGameTests::powerSetting);
        registry.register("charging_fabricator_hopper_laser_chain", () -> ChargingStationGameTests::chain);
    }
    private static ItemStack item(String id) { return TGContent.AMMO.containsKey(id) ? TGContent.AMMO.get(id).toStack() : TGContent.MATERIALS.get(id).toStack(); }
    private static ChargingStationBlockEntity place(GameTestHelper h) {
        h.setBlock(POS, ChargingStationContent.BLOCK.get());
        return h.getBlockEntity(POS, ChargingStationBlockEntity.class);
    }
    private static int charge(ChargingStationBlockEntity m, int amount) {
        try (Transaction tx = Transaction.openRoot()) { int n = m.energy().insert(amount, tx); tx.commit(); return n; }
    }
    private static void tick(GameTestHelper h, ChargingStationBlockEntity m, int count) {
        for (int i = 0; i < count; i++) ChargingStationBlockEntity.tick(h.getLevel(), m.getBlockPos(), m.getBlockState(), m);
    }
    private static void supply(ChargingStationBlockEntity m, Case c, int batch) {
        m.setItem(0, item(c.empty).copyWithCount(batch));
        if (batch > 1) m.setItem(2, item("machinestackupgrade").copyWithCount(batch - 1));
    }
    private static void output(GameTestHelper h, ChargingStationBlockEntity m, Case c, int count) {
        h.assertTrue(m.getItem(1).is(item(c.full).getItem()), "Original charged battery");
        h.assertValueEqual(m.getItem(1).getCount(), count, "Exact output count");
    }
    private static void production(GameTestHelper h, Case c, int batch) {
        var m = place(h); supply(m, c, batch);
        int[] supplied = {charge(m, 100000)};
        h.onEachTick(() -> supplied[0] += charge(m, 100000));
        h.runAfterDelay(5, () -> {
            h.assertValueEqual(m.data.get(2), c.ticks, "Original duration truncates nominal charge/800");
            h.assertValueEqual(m.data.get(7), 800 * batch, "MachineOperation applies batch multiplier once");
            h.assertTrue(m.getItem(0).isEmpty(), "Batch input reserved");
        });
        h.succeedWhen(() -> {
            output(h, m, c, batch);
            h.assertValueEqual(supplied[0] - m.energy().getAmountAsInt(), c.energy * batch, "Exact total energy for real server production");
        });
    }
    private static void powerPause(GameTestHelper h) {
        var m = place(h); supply(m, CASES.getFirst(), 1); charge(m, 799);
        tick(h, m, 10);
        h.assertValueEqual(m.data.get(1), 0, "Insufficient power cannot advance or consume a partial tick");
        h.assertValueEqual(m.energy().getAmountAsInt(), 799, "Partial power remains stored");
        charge(m, 1); tick(h, m, 1);
        h.assertValueEqual(m.data.get(1), 1, "Exactly 800 FE allows one tick");
        charge(m, 48800); tick(h, m, 61); output(h, m, CASES.getFirst(), 1);
        h.assertValueEqual(m.energy().getAmountAsInt(), 0, "Remaining power is exact"); h.succeed();
    }
    private static void redstone(GameTestHelper h) {
        var m = place(h); supply(m, CASES.get(1), 1); charge(m, 20000);
        var player = WeaponGameTests.player(h); h.assertTrue(m.button(player, 2), "Require signal");
        tick(h, m, 30); h.assertTrue(!m.working(), "No recipe starts without requested signal");
        h.setBlock(POS.relative(Direction.WEST), Blocks.REDSTONE_BLOCK); tick(h, m, 6);
        int progress = m.data.get(1), energy = m.energy().getAmountAsInt();
        h.setBlock(POS.relative(Direction.WEST), Blocks.AIR); tick(h, m, 20);
        h.assertValueEqual(m.data.get(1), progress, "Job paused by redstone");
        h.assertValueEqual(m.energy().getAmountAsInt(), energy, "Paused job spends no energy");
        h.setBlock(POS.relative(Direction.WEST), Blocks.REDSTONE_BLOCK); tick(h, m, 25); output(h, m, CASES.get(1), 1);
        h.setBlock(POS.relative(Direction.WEST), Blocks.AIR);
        m.setItem(0, ChargingTestItems.DEVICE.toStack()); charge(m, 5000); tick(h, m, 20);
        h.assertValueEqual(m.getItem(0).getOrDefault(ChargingTestItems.ENERGY.get(), 0), 0, "Redstone also pauses direct charging");
        h.succeed();
    }
    private static void blockedBatch(GameTestHelper h) {
        var m = place(h); supply(m, CASES.get(1), 1); charge(m, 30000); tick(h, m, 1);
        m.setItem(1, new ItemStack(Items.STONE)); tick(h, m, 50);
        h.assertValueEqual(m.energy().getAmountAsInt(), 10000, "Completed blocked result does not consume more power");
        h.assertTrue(m.working(), "Result waits safely for output");
        m.setItem(1, ItemStack.EMPTY); tick(h, m, 1); output(h, m, CASES.get(1), 1); h.succeed();
    }
    private static void space(GameTestHelper h) {
        var m = place(h); supply(m, CASES.get(1), 8); m.setItem(1, item("redstone_battery").copyWithCount(62)); charge(m, 40000);
        tick(h, m, 26); output(h, m, CASES.get(1), 64);
        h.assertValueEqual(m.getItem(0).getCount(), 6, "Output space bounds the batch before input consumption");
        h.assertValueEqual(m.energy().getAmountAsInt(), 0, "Only two cells charged"); h.succeed();
    }
    private static void upgradeSnapshot(GameTestHelper h) {
        var m = place(h); supply(m, CASES.get(1), 2); charge(m, 40000); tick(h, m, 1);
        m.setItem(2, ItemStack.EMPTY); tick(h, m, 25); output(h, m, CASES.get(1), 2);
        h.assertValueEqual(m.energy().getAmountAsInt(), 0, "Removing upgrades cannot reduce reserved batch cost"); h.succeed();
    }
    private static ChargingStationBlockEntity reload(GameTestHelper h, ChargingStationBlockEntity old) {
        var tag = old.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored = (ChargingStationBlockEntity) BlockEntity.loadStatic(old.getBlockPos(), old.getBlockState(), tag, h.getLevel().registryAccess());
        h.getLevel().removeBlockEntity(old.getBlockPos()); h.getLevel().setBlockEntity(restored);
        return restored;
    }
    private static void savedBatch(GameTestHelper h) {
        var m = place(h); supply(m, CASES.getFirst(), 1); charge(m, 49600); tick(h, m, 18);
        int progress = m.data.get(1), energy = m.energy().getAmountAsInt();
        var restored = reload(h, m);
        h.assertValueEqual(restored.data.get(1), progress, "Saved processing time");
        h.assertValueEqual(restored.energy().getAmountAsInt(), energy, "Saved energy");
        tick(h, restored, 62 - progress); output(h, restored, CASES.getFirst(), 1);
        h.assertValueEqual(restored.energy().getAmountAsInt(), 0, "No free or repeated charging after load"); h.succeed();
    }
    private static List<ItemStack> breakDrops(GameTestHelper h, ChargingStationBlockEntity m) {
        h.getLevel().destroyBlock(m.getBlockPos(), true);
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(m.getBlockPos()).inflate(2)).stream().map(ItemEntity::getItem).toList();
    }
    private static void breakBatch(GameTestHelper h) {
        var m = place(h); var input = item("energycellempty"); input.set(DataComponents.CUSTOM_NAME, Component.literal("Reserved cell"));
        m.setItem(0, input); charge(m, 1600); tick(h, m, 3);
        var drops = breakDrops(h, m);
        var cells = drops.stream().filter(s -> s.is(item("energycellempty").getItem())).toList();
        h.assertValueEqual(cells.stream().mapToInt(ItemStack::getCount).sum(), 1, "Reserved input returned exactly once");
        h.assertValueEqual(cells.getFirst().getHoverName().getString(), "Reserved cell", "Reserved components retained");
        h.assertTrue(drops.stream().noneMatch(s -> s.is(item("energycell").getItem())), "No unfinished charged output");
        h.assertValueEqual(drops.stream().filter(s -> s.is(ChargingStationContent.ITEM.get())).mapToInt(ItemStack::getCount).sum(), 1, "Original block drops once");
        h.succeed();
    }
    private static void ports(GameTestHelper h) {
        var m = place(h);
        for (Direction side : Direction.values()) {
            var items = h.getLevel().getCapability(Capabilities.Item.BLOCK, m.getBlockPos(), side);
            var energy = h.getLevel().getCapability(Capabilities.Energy.BLOCK, m.getBlockPos(), side);
            h.assertTrue(items != null && energy != null, "All six sides expose item and energy ports");
            try (Transaction tx = Transaction.openRoot()) {
                h.assertValueEqual(energy.insert(110000, tx), 100000, "Original energy capacity");
                h.assertValueEqual(items.insert(0, ItemResource.of(item("energycellempty")), 4, tx), 4, "Accept batteries");
                h.assertValueEqual(items.extract(0, ItemResource.of(item("energycellempty")), 4, tx), 0, "Pipes cannot extract inputs");
                h.assertValueEqual(items.insert(1, ItemResource.of(item("energycell")), 1, tx), 0, "Output accepts no external input");
                h.assertValueEqual(items.insert(2, ItemResource.of(item("machinestackupgrade")), 64, tx), 7, "Upgrade stack limit");
            }
            h.assertTrue(m.getItem(0).isEmpty() && m.getItem(2).isEmpty(), "Aborted item transfer rolls back");
            h.assertValueEqual(m.energy().getAmountAsInt(), 0, "Aborted energy transfer rolls back");
        }
        h.assertTrue(!m.canPlaceItem(0, new ItemStack(Items.STONE)) && !m.canPlaceItem(0, item("machinestackupgrade")), "Input slot rejects wrong items and upgrades");
        h.assertTrue(m.canPlaceItem(0, ChargingTestItems.DEVICE.toStack()), "Input accepts an external energy capability");
        h.assertTrue(h.getLevel().getCapability(Capabilities.Fluid.BLOCK, m.getBlockPos(), null) == null, "No invented fluid port");
        h.succeed();
    }
    private static void crafting(GameTestHelper h) {
        var steel = item("platesteel"); var wire = item("goldwire"); var coil = item("coil");
        var grid = CraftingInput.of(3, 3, List.of(steel, wire, steel, coil, item("circuitboard"), coil, steel, wire, steel));
        var output = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel()).orElseThrow().value().assemble(grid);
        h.assertTrue(output.is(ChargingStationContent.ITEM.get()), "Original simplemachine metadata 10 recipe");
        h.assertValueEqual(output.getCount(), 1, "One station per recipe");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = ChargingStationContent.BLOCK.get().defaultBlockState().setValue(ProcessingMachineBlock.FACING, facing);
            h.setBlock(POS, state);
            h.assertTrue(!state.canOcclude(), "Open frame is not opaque");
            h.assertValueEqual(state.getCollisionShape(h.getLevel(), h.absolutePos(POS)).bounds(), new AABB(0,0,0,1,1,1), "Original full-cube collision even for open model");
        }
        h.succeed();
    }
    private static void codec(GameTestHelper h) {
        var manager = h.getLevel().getServer().getRecipeManager();
        for (Case c : CASES) {
            var recipe = manager.byKey(ResourceKey.create(Registries.RECIPE, TGContent.id("charging_station/" + c.full))).orElseThrow().value();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                Recipe.STREAM_CODEC.encode(buffer, recipe);
                var decoded = (ChargingStationRecipe) Recipe.STREAM_CODEC.decode(buffer);
                h.assertTrue(decoded.matches(new SingleRecipeInput(item(c.empty)), h.getLevel()), "Decoded recipe accepts source input");
                h.assertTrue(!decoded.matches(new SingleRecipeInput(item(c.full)), h.getLevel()), "Already charged batteries cannot duplicate energy");
                h.assertValueEqual(decoded.duration(), c.ticks, "Decoded charge amount retains truncated duration");
                h.assertTrue(decoded.assemble(new SingleRecipeInput(item(c.empty))).is(item(c.full).getItem()), "Recipe output survives network serialization");
            } finally { buffer.release(); }
        }
        h.succeed();
    }
    private static void menu(GameTestHelper h) {
        var m = place(h); charge(m, 100000); var owner = WeaponGameTests.player(h); m.setOwner(owner);
        var menu = new ChargingStationMenu(45, owner.getInventory(), m, m.displayData); owner.containerMenu = menu;
        owner.getInventory().setItem(9, ChargingTestItems.DEVICE.toStack());
        h.assertTrue(!menu.quickMoveStack(owner, 3).isEmpty() && m.getItem(0).is(ChargingTestItems.DEVICE.get()), "Shift-click routes energy items into input");
        owner.getInventory().setItem(10, item("machinestackupgrade"));
        h.assertTrue(!menu.quickMoveStack(owner, 4).isEmpty() && m.getItem(2).getCount() == 1, "Upgrade has its own slot");
        h.assertTrue(!menu.clickMenuButton(owner, 0) && !menu.clickMenuButton(owner, 99), "No unsupported recipe mode buttons");
        h.assertTrue(menu.clickMenuButton(owner, 3), "Owner can restrict access");
        var other = WeaponGameTests.player(h); other.setUUID(UUID.randomUUID()); other.containerMenu = menu;
        h.assertTrue(!menu.stillValid(other) && !menu.clickMenuButton(other, 2), "Access is validated on the server");
        var client = new ChargingStationMenu(45, owner.getInventory()); var words = new SplitIntContainerData(m.displayData);
        for (int i = words.getCount() - 1; i >= 0; i--) {
            var buffer = new net.minecraft.network.FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundContainerSetDataPacket.STREAM_CODEC.encode(buffer, new ClientboundContainerSetDataPacket(45, i, words.get(i)));
                var packet = ClientboundContainerSetDataPacket.STREAM_CODEC.decode(buffer); client.setData(packet.getId(), packet.getValue());
            } finally { buffer.release(); }
        }
        h.assertValueEqual(client.value(0), 100000, "Full energy survives signed-short menu packets");
        h.assertValueEqual(client.value(8), 100000, "Full capacity survives menu packets");
        h.succeed();
    }
    private static void renderData(GameTestHelper h) {
        var m = place(h); var input = item("energycellempty"); input.set(DataComponents.CUSTOM_NAME, Component.literal("Charging visual"));
        m.setItem(0, input); charge(m, 49600); tick(h, m, 3);
        h.assertTrue(m.displayItem().is(TGContent.AMMO.get("energycellempty").get()), "Reserved input is still shown during recipe");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        var client = new ChargingStationBlockEntity(m.getBlockPos(), m.getBlockState());
        try {
            ClientboundBlockEntityDataPacket.STREAM_CODEC.encode(buffer, m.getUpdatePacket());
            var packet = ClientboundBlockEntityDataPacket.STREAM_CODEC.decode(buffer);
            client.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), packet.getTag()));
            h.assertValueEqual(client.displayItem().getHoverName().getString(), "Charging visual", "Real block-entity packet retains render components");
            h.assertTrue(client.getItem(0).isEmpty(), "Render update does not populate client inventory");
            h.assertTrue(!packet.getTag().contains("Items") && !packet.getTag().contains("owner"), "Render packet includes no full inventory or owner data");
        } finally { buffer.release(); }
        tick(h, m, 62);
        client.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), m.getUpdateTag(h.getLevel().registryAccess())));
        h.assertTrue(client.displayItem().isEmpty(), "Completed operation clears displayed input"); h.succeed();
    }
    private static ItemStack device() {
        var stack = ChargingTestItems.DEVICE.toStack(); stack.set(DataComponents.CUSTOM_NAME, Component.literal("External device")); return stack;
    }
    private static int energy(ItemStack stack) { return stack.getOrDefault(ChargingTestItems.ENERGY.get(), 0); }
    private static void direct(GameTestHelper h) {
        var m = place(h); m.setItem(0, device()); charge(m, 10000);
        tick(h, m, 1);
        h.assertValueEqual(energy(m.getItem(0)), 1600, "Direct transfer has its own 1600 FE rate");
        h.assertValueEqual(m.displayData.get(7), 1600, "Menu reports accepted energy");
        tick(h, m, 3);
        h.assertValueEqual(energy(m.getItem(0)), 5000, "Last transfer fills remaining 200 FE only");
        h.assertValueEqual(m.displayData.get(7), 200, "Partial final transfer reported accurately");
        tick(h, m, 1);
        h.assertTrue(m.getItem(0).isEmpty() && m.getItem(1).is(ChargingTestItems.DEVICE.get()), "Full device moves to output on the next tick");
        h.assertValueEqual(m.energy().getAmountAsInt(), 5000, "Only energy accepted by item is spent");
        h.assertValueEqual(m.getItem(1).getHoverName().getString(), "External device", "Unrelated components retained"); h.succeed();
    }
    private static void partialPower(GameTestHelper h) {
        var m = place(h); m.setItem(0, device()); charge(m, 731); tick(h, m, 1);
        h.assertValueEqual(energy(m.getItem(0)), 731, "Direct charging uses a partial available supply");
        h.assertValueEqual(m.energy().getAmountAsInt(), 0, "Actual accepted amount deducted");
        tick(h, m, 8); h.assertValueEqual(energy(m.getItem(0)), 731, "No further energy appears without supply"); h.succeed();
    }
    private static void noPower(GameTestHelper h) {
        var m = place(h); var stack = device(); m.setItem(0, stack); tick(h, m, 10);
        h.assertValueEqual(energy(m.getItem(0)), 0, "Capability simulation is rolled back");
        h.assertValueEqual(m.getItem(0).getHoverName().getString(), "External device", "Simulation preserves other components");
        h.assertTrue(m.getItem(1).isEmpty(), "An empty buffer does not mark an unfinished device complete");
        h.assertValueEqual(m.displayData.get(7), 0, "Menu shows no actual transfer"); h.succeed();
    }
    private static void receiverLimit(GameTestHelper h) {
        var m = place(h); m.setItem(0, ChargingTestItems.LIMITED.toStack()); charge(m, 5000); tick(h, m, 10);
        h.assertValueEqual(energy(m.getItem(0)), 1370, "External receiver's 137 FE limit is respected per tick");
        h.assertValueEqual(m.energy().getAmountAsInt(), 3630, "Rejected portion is not consumed"); h.succeed();
    }
    private static void stackEnergy(GameTestHelper h) {
        var m = place(h); var stack = ChargingTestItems.STACK.toStack(4); stack.set(ChargingTestItems.ENERGY.get(), Integer.MAX_VALUE - 500);
        m.setItem(0, stack); charge(m, 10000); tick(h, m, 3);
        h.assertValueEqual(m.getItem(1).getCount(), 4, "Stacked devices remain four items");
        h.assertValueEqual(energy(m.getItem(1)), Integer.MAX_VALUE, "Capacities above signed-int total do not overflow");
        h.assertValueEqual(m.energy().getAmountAsInt(), 8000, "Stack energy is charged once per item, without multiplication exploits"); h.succeed();
    }
    private static void refusingItem(GameTestHelper h) {
        var m = place(h); m.setItem(0, ChargingTestItems.REFUSER.toStack()); charge(m, 1000); tick(h, m, 1);
        h.assertTrue(m.getItem(1).is(ChargingTestItems.REFUSER.get()) && m.getItem(0).isEmpty(), "Source behavior returns an item that accepts no energy");
        h.assertValueEqual(m.energy().getAmountAsInt(), 1000, "Rejected transfer costs nothing"); h.succeed();
    }
    private static void savedItem(GameTestHelper h) {
        var m = place(h); m.setItem(0, device()); charge(m, 7000); tick(h, m, 2);
        var restored = reload(h, m); h.assertValueEqual(energy(restored.getItem(0)), 3200, "Partially charged item's components survive block-entity save");
        tick(h, restored, 3);
        h.assertValueEqual(energy(restored.getItem(1)), 5000, "Restored item finishes charging");
        h.assertValueEqual(restored.energy().getAmountAsInt(), 2000, "Resumed transfer conserves energy"); h.succeed();
    }
    private static void breakItem(GameTestHelper h) {
        var m = place(h); m.setItem(0, device()); charge(m, 5000); tick(h, m, 1);
        var drops = breakDrops(h, m).stream().filter(s -> s.is(ChargingTestItems.DEVICE.get())).toList();
        h.assertValueEqual(drops.stream().mapToInt(ItemStack::getCount).sum(), 1, "Charging device drops exactly once");
        h.assertValueEqual(energy(drops.getFirst()), 1600, "Spent energy remains in dropped device");
        h.assertValueEqual(drops.getFirst().getHoverName().getString(), "External device", "Dropped device retains components"); h.succeed();
    }
    private static void blockedItem(GameTestHelper h) {
        var m = place(h); var full = ChargingTestItems.STACK.toStack(2); full.set(ChargingTestItems.ENERGY.get(), Integer.MAX_VALUE);
        m.setItem(0, full); m.setItem(1, full.copyWithCount(1)); charge(m, 1000); tick(h, m, 5);
        h.assertValueEqual(m.getItem(0).getCount(), 2, "Original direct mode waits for an empty output rather than merging stacks");
        h.assertValueEqual(m.getItem(1).getCount(), 1, "Occupied output unchanged");
        m.setItem(1, ItemStack.EMPTY); tick(h, m, 1);
        h.assertValueEqual(m.getItem(1).getCount(), 2, "Entire completed stack moves after output clears");
        h.assertValueEqual(m.energy().getAmountAsInt(), 1000, "Completed item consumes no extra energy"); h.succeed();
    }
    private static void transform(GameTestHelper h) {
        var m = place(h); var stack = ChargingTestItems.TRANSFORM.toStack(); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Transformed"));
        m.setItem(0, stack); charge(m, 5000); tick(h, m, 2);
        h.assertTrue(m.getItem(0).isEmpty() && m.getItem(1).is(Items.DIAMOND), "ItemAccess supports changed item type with no remaining energy capability");
        h.assertValueEqual(m.getItem(1).getHoverName().getString(), "Transformed", "Replacement retains its provided components");
        h.assertValueEqual(m.energy().getAmountAsInt(), 4000, "Rolled-back probe does not also charge the replacement"); h.succeed();
    }
    private static void savedReplacement(GameTestHelper h) {
        var m = place(h); m.setItem(0, ChargingTestItems.TRANSFORM.toStack()); m.setItem(1, new ItemStack(Items.STONE)); charge(m, 5000); tick(h, m, 1);
        var restored = reload(h, m); tick(h, restored, 10);
        h.assertTrue(restored.getItem(0).is(Items.DIAMOND), "Completed replacement waits safely through save and blocked output");
        h.assertValueEqual(restored.energy().getAmountAsInt(), 4000, "Replacement is not charged repeatedly");
        restored.setItem(1, ItemStack.EMPTY); tick(h, restored, 1);
        h.assertTrue(restored.getItem(1).is(Items.DIAMOND) && restored.getItem(0).isEmpty(), "Ready marker survives even after original capability disappears"); h.succeed();
    }
    private static void swappedReplacement(GameTestHelper h) {
        var m = place(h); m.setItem(0, ChargingTestItems.TRANSFORM.toStack()); m.setItem(1, new ItemStack(Items.STONE)); charge(m, 10000); tick(h, m, 1);
        ItemStack finished = m.removeItem(0, 1); m.setItem(0, device()); tick(h, m, 1);
        h.assertTrue(finished.is(Items.DIAMOND), "Player can recover completed replacement from input");
        h.assertValueEqual(energy(m.getItem(0)), 1600, "New input does not inherit previous item's ready status");
        h.assertValueEqual(m.energy().getAmountAsInt(), 7400, "New input has its own power cost"); h.succeed();
    }
    private static void powerSetting(GameTestHelper h) {
        boolean previous = TGMachineConfig.MACHINES_NEED_NO_POWER.get();
        try {
            TGMachineConfig.MACHINES_NEED_NO_POWER.set(true);
            var m = place(h); supply(m, CASES.get(1), 1); tick(h, m, 26); output(h, m, CASES.get(1), 1);
            m.setItem(1, ItemStack.EMPTY); m.setItem(0, device()); tick(h, m, 10);
            h.assertValueEqual(energy(m.getItem(0)), 0, "Original no-power recipe setting never creates external item energy");
            charge(m, 5000); tick(h, m, 5);
            h.assertValueEqual(energy(m.getItem(1)), 5000, "External device still charges from supplied power");
            h.assertValueEqual(m.energy().getAmountAsInt(), 0, "External charge remains paid even with no-power recipes");
        } finally { TGMachineConfig.MACHINES_NEED_NO_POWER.set(previous); }
        h.succeed();
    }
    private static void chain(GameTestHelper h) {
        var charger = place(h); charge(charger, 49600);
        h.setBlock(POS.above(), Blocks.HOPPER);
        BlockPos fabPos = POS.above(2);
        for (var part : FabricatorBlockEntity.parts(h.absolutePos(fabPos), Direction.SOUTH))
            h.getLevel().setBlock(part.pos(), part.block().defaultBlockState(), 3);
        var fab = h.getBlockEntity(fabPos, FabricatorBlockEntity.class);
        h.assertTrue(fab.form(Direction.SOUTH, WeaponGameTests.player(h)), "Fabricator assembled above the hopper");
        fab.setItem(0, new ItemStack(Items.GOLD_INGOT)); fab.setItem(1, item("copperwire"));
        fab.setItem(2, new ItemStack(Items.REDSTONE, 3)); fab.setItem(3, item("plasticsheet"));
        try (Transaction tx = Transaction.openRoot()) { h.assertValueEqual(fab.energy().insert(8000, tx), 8000, "Supply Fabricator operation"); tx.commit(); }
        h.startSequence().thenWaitUntil(() -> output(h, charger, CASES.getFirst(), 1)).thenExecute(() -> {
            h.assertTrue(fab.getItem(4).isEmpty(), "Real hopper moved the fabricated empty cell");
            h.assertValueEqual(fab.energy().getAmountAsInt(), 0, "Fabrication cost paid");
            h.assertValueEqual(charger.energy().getAmountAsInt(), 0, "Charging cost paid");
            var player = WeaponGameTests.player(h); player.setItemInHand(InteractionHand.MAIN_HAND, TGContent.GUNS.get("lasergun").toStack());
            player.getInventory().setItem(1, charger.removeItem(1, 1));
            h.assertTrue(ReloadSessions.begin(player), "Produced charged cell loads the laser rifle");
            for (int tick = 0; tick < 45; tick++) ReloadSessions.tick(player);
            h.assertValueEqual(GunItem.rounds(player.getMainHandItem()), 45, "Produced cell supplies 45 shots");
            h.assertTrue(GunItem.fire(h.getLevel(), player, player.getMainHandItem()), "Rifle fires with the manufactured cell");
            h.assertValueEqual(GunItem.rounds(player.getMainHandItem()), 44, "One charge consumed by firing");
        }).thenSucceed();
    }
}
