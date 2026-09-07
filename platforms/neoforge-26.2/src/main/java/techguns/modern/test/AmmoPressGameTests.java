package techguns.modern.test;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.TGContent;
import techguns.modern.machine.AmmoPressBlockEntity;
import techguns.modern.machine.AmmoPressMenu;
import techguns.modern.machine.AmmoPressRecipe;
import techguns.modern.machine.TGMachineConfig;
import techguns.modern.machine.TGMachineContent;

final class AmmoPressGameTests {
    private static final BlockPos POS = new BlockPos(4, 2, 4);
    private static final String[] OUTPUTS = {"pistolrounds", "shotgunrounds", "riflerounds", "sniperrounds"};
    private static final int[] YIELDS = {12, 16, 8, 4};
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        for (int i = 0; i < 4; i++) { final int plan = i; registry.register("press_plan_" + i, () -> h -> production(h, plan)); }
        registry.register("press_insufficient_energy", () -> AmmoPressGameTests::insufficientEnergy);
        registry.register("press_redstone_pause", () -> AmmoPressGameTests::redstonePause);
        registry.register("press_output_obstruction", () -> AmmoPressGameTests::outputObstruction);
        registry.register("press_batch_upgrade", () -> AmmoPressGameTests::batchUpgrade);
        registry.register("press_reload_mid_operation", () -> AmmoPressGameTests::reload);
        registry.register("press_break_refunds_inputs", () -> AmmoPressGameTests::breakMachine);
        registry.register("press_transactional_automation", () -> AmmoPressGameTests::automation);
        registry.register("press_menu_controls", () -> AmmoPressGameTests::controls);
        registry.register("press_shift_click", () -> AmmoPressGameTests::shiftClick);
        registry.register("press_recipe_codec", () -> AmmoPressGameTests::codec);
        registry.register("press_optional_power", () -> AmmoPressGameTests::optionalPower);
        registry.register("press_hopper_production", () -> AmmoPressGameTests::hoppers);
        registry.register("press_crafting_chain", () -> AmmoPressGameTests::crafting);
        registry.register("press_earlier_port_save", () -> AmmoPressGameTests::earlierSave);
    }
    private static AmmoPressBlockEntity machine(GameTestHelper h) {
        h.setBlock(POS, TGMachineContent.AMMO_PRESS.get());
        var machine = (AmmoPressBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(POS));
        h.assertTrue(machine != null, "Placed block creates its real BlockEntity");
        return machine;
    }
    private static void supply(AmmoPressBlockEntity machine, int batches) {
        machine.setItem(0, TGContent.MATERIALS.get("ingotlead").toStack(batches));
        machine.setItem(1, new ItemStack(Items.COPPER_INGOT, batches * 2));
        machine.setItem(2, new ItemStack(Items.GUNPOWDER, batches));
    }
    private static void charge(GameTestHelper h, int amount) {
        var energy = h.getLevel().getCapability(Capabilities.Energy.BLOCK, h.absolutePos(POS), Direction.NORTH);
        h.assertTrue(energy != null, "NeoForge energy capability is registered");
        try (Transaction tx = Transaction.openRoot()) {
            h.assertValueEqual(energy.insert(amount, tx), amount, "External generator can charge machine"); tx.commit();
        }
    }
    private static void assertOutput(GameTestHelper h, AmmoPressBlockEntity machine, int plan, int count) {
        h.assertTrue(machine.getItem(3).is(TGContent.AMMO.get(OUTPUTS[plan]).get()), "Correct original build plan output");
        h.assertValueEqual(machine.getItem(3).getCount(), count, "Original batch yield");
    }
    private static void production(GameTestHelper h, int plan) {
        var machine = machine(h);
        Player player = WeaponGameTests.player(h);
        for (int i = 0; i < plan; i++) h.assertTrue(machine.button(player, 0), "Select plan");
        supply(machine, 1); charge(h, 500);
        h.runAfterDelay(5, () -> {
            h.assertTrue(machine.getItem(0).isEmpty() && machine.getItem(1).isEmpty() && machine.getItem(2).isEmpty(), "Inputs are reserved at start");
            h.assertTrue(machine.getItem(3).isEmpty(), "No early production");
            h.assertValueEqual(machine.data.get(2), 100, "Original cycle duration");
            h.assertValueEqual(machine.data.get(7), 5, "Menu reports Ammo Press power independently of Metal Press scaling");
        });
        h.runAfterDelay(105, () -> {
            assertOutput(h, machine, plan, YIELDS[plan]);
            h.assertValueEqual(machine.energy().getAmountAsLong(), 0L, "One batch costs exactly 500 FE");
            h.succeed();
        });
    }
    private static void insufficientEnergy(GameTestHelper h) {
        var machine = machine(h); supply(machine, 1);
        h.runAfterDelay(8, () -> {
            h.assertTrue(machine.working(), "Starting reserves the original ingredients even before power arrives");
            h.assertValueEqual(machine.data.get(1), 0, "No progress without energy"); charge(h, 495);
        });
        h.runAfterDelay(115, () -> {
            h.assertValueEqual(machine.data.get(1), 99, "Partial power cannot complete a batch");
            h.assertTrue(machine.getItem(3).isEmpty(), "No unpaid final product"); charge(h, 5);
        });
        h.runAfterDelay(120, () -> { assertOutput(h, machine, 0, 12); h.succeed(); });
    }
    private static void redstonePause(GameTestHelper h) {
        var machine = machine(h); supply(machine, 1); charge(h, 500);
        Player player = WeaponGameTests.player(h);
        h.assertTrue(machine.button(player, 2), "Require high signal");
        h.runAfterDelay(8, () -> {
            h.assertTrue(!machine.working(), "High mode does not reserve inputs while unpowered");
            h.setBlock(POS.east(), Blocks.REDSTONE_BLOCK);
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(machine.data.get(1) > 0, "Signal permits processing");
            machine.button(player, 2); // now require low
            int progress = machine.data.get(1);
            h.runAfterDelay(8, () -> {
                h.assertValueEqual(machine.data.get(1), progress, "Opposite signal pauses rather than resetting");
                h.setBlock(POS.east(), Blocks.AIR);
            });
        });
        h.runAfterDelay(135, () -> { assertOutput(h, machine, 0, 12); h.succeed(); });
    }
    private static void outputObstruction(GameTestHelper h) {
        var machine = machine(h); supply(machine, 1); charge(h, 500);
        machine.setItem(3, new ItemStack(Items.STONE));
        h.runAfterDelay(8, () -> {
            h.assertTrue(!machine.working(), "Occupied output blocks input reservation");
            h.assertValueEqual(machine.energy().getAmountAsLong(), 500L, "No wasted energy before start");
            machine.setItem(3, ItemStack.EMPTY);
        });
        h.runAfterDelay(20, () -> machine.setItem(3, new ItemStack(Items.STONE)));
        h.runAfterDelay(120, () -> {
            h.assertValueEqual(machine.data.get(1), 100, "Finished work waits for output space");
            h.assertTrue(machine.getItem(3).is(Items.STONE), "Existing output is not overwritten");
            h.assertValueEqual(machine.energy().getAmountAsLong(), 0L, "Waiting output consumes no additional energy");
            machine.setItem(3, ItemStack.EMPTY);
        });
        h.runAfterDelay(125, () -> { assertOutput(h, machine, 0, 12); h.succeed(); });
    }
    private static void batchUpgrade(GameTestHelper h) {
        var machine = machine(h); supply(machine, 8); charge(h, 4000);
        machine.setItem(4, TGContent.MATERIALS.get("machinestackupgrade").toStack(7));
        h.runAfterDelay(105, () -> {
            assertOutput(h, machine, 0, 60);
            h.assertValueEqual(machine.getItem(0).getCount(), 3, "Batch multiplier is limited by the 64-item output slot");
            h.assertValueEqual(machine.energy().getAmountAsLong(), 1500L, "Power scales with five processed batches");
            var handler = machine.automation();
            try (Transaction tx = Transaction.openRoot()) {
                h.assertValueEqual(handler.extract(3, ItemResource.of(machine.getItem(3)), 60, tx), 60, "Automation removes completed output"); tx.commit();
            }
        });
        h.runAfterDelay(212, () -> {
            assertOutput(h, machine, 0, 36);
            h.assertValueEqual(machine.energy().getAmountAsLong(), 0L, "All eight batches cost 4000 FE");
            h.assertValueEqual(machine.getItem(4).getCount(), 7, "Upgrade is reusable"); h.succeed();
        });
    }
    private static void reload(GameTestHelper h) {
        var machine = machine(h); supply(machine, 1); charge(h, 500);
        h.runAfterDelay(45, () -> {
            h.assertTrue(machine.button(WeaponGameTests.player(h), 0), "Next plan may change while the current batch is reserved");
            int progress = machine.data.get(1); long power = machine.energy().getAmountAsLong();
            var tag = machine.saveWithFullMetadata(h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(h.absolutePos(POS));
            var restored = (AmmoPressBlockEntity) BlockEntity.loadStatic(h.absolutePos(POS), TGMachineContent.AMMO_PRESS.get().defaultBlockState(), tag, h.getLevel().registryAccess());
            h.assertTrue(restored != null, "BlockEntity loads through registered type and Minecraft NBT");
            h.getLevel().setBlockEntity(restored);
            h.assertValueEqual(restored.data.get(1), progress, "Progress survives reload");
            h.assertValueEqual(restored.data.get(3), 1, "Next plan survives independently of the current output");
            h.assertValueEqual(restored.energy().getAmountAsLong(), power, "Stored energy survives reload");
        });
        h.runAfterDelay(110, () -> {
            var restored = (AmmoPressBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(POS));
            assertOutput(h, restored, 0, 12);
            h.assertValueEqual(restored.energy().getAmountAsLong(), 0L, "Resume cannot skip remaining power cost");
            h.assertTrue(restored.getItem(0).isEmpty(), "No duplicate reserved ingredients"); h.succeed();
        });
    }
    private static void breakMachine(GameTestHelper h) {
        var machine = machine(h); supply(machine, 1); charge(h, 500);
        h.runAfterDelay(20, () -> h.getLevel().destroyBlock(h.absolutePos(POS), true));
        h.runAfterDelay(22, () -> {
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(POS)).inflate(3));
            int lead = 0, copper = 0, powder = 0, bullets = 0, blocks = 0;
            for (var entity : drops) {
                var stack = entity.getItem();
                if (stack.is(TGContent.MATERIALS.get("ingotlead").get())) lead += stack.getCount();
                if (stack.is(Items.COPPER_INGOT)) copper += stack.getCount();
                if (stack.is(Items.GUNPOWDER)) powder += stack.getCount();
                if (stack.is(TGContent.AMMO.get("pistolrounds").get())) bullets += stack.getCount();
                if (stack.is(TGMachineContent.AMMO_PRESS_ITEM.get())) blocks += stack.getCount();
            }
            h.assertValueEqual(lead, 1, "Reserved first metal returns once");
            h.assertValueEqual(copper, 2, "Reserved casing metal returns once");
            h.assertValueEqual(powder, 1, "Reserved powder returns once");
            h.assertValueEqual(bullets, 0, "Unfinished result is not also dropped");
            h.assertValueEqual(blocks, 1, "Machine block loot is valid"); h.succeed();
        });
    }
    private static void automation(GameTestHelper h) {
        var machine = machine(h);
        var handler = h.getLevel().getCapability(Capabilities.Item.BLOCK, h.absolutePos(POS), Direction.UP);
        h.assertTrue(handler != null, "Item capability exposed on machine");
        var lead = ItemResource.of(TGContent.MATERIALS.get("ingotlead").toStack());
        try (Transaction tx = Transaction.openRoot()) {
            h.assertValueEqual(machine.energy().insert(25000, tx), 20000, "Original energy capacity");
        }
        h.assertValueEqual(machine.energy().getAmountAsLong(), 0L, "Capacity probe rolls back");
        try (Transaction tx = Transaction.openRoot()) {
            h.assertValueEqual(handler.insert(0, lead, 3, tx), 3, "Accept matching input");
            h.assertValueEqual(machine.energy().insert(500, tx), 500, "Energy participates in the same transaction");
        }
        h.assertTrue(machine.getItem(0).isEmpty(), "Aborted item transfer rolls back");
        h.assertValueEqual(machine.energy().getAmountAsLong(), 0L, "Aborted energy transfer rolls back");
        try (Transaction tx = Transaction.openRoot()) {
            h.assertValueEqual(handler.insert(0, ItemResource.of(new ItemStack(Items.GOLD_INGOT)), 1, tx), 0, "Wrong metal rejected");
            h.assertValueEqual(handler.insert(0, lead, 3, tx), 3, "Valid metal accepted");
            h.assertValueEqual(handler.extract(0, lead, 3, tx), 0, "Automation cannot extract inputs");
            h.assertValueEqual(handler.insert(3, lead, 1, tx), 0, "Automation cannot insert into output");
            h.assertValueEqual(handler.insert(4, ItemResource.of(TGContent.MATERIALS.get("machinestackupgrade").toStack()), 64, tx), 7, "Original upgrade stack limit");
            tx.commit();
        }
        h.assertValueEqual(machine.getItem(0).getCount(), 3, "Committed transfer persists"); h.succeed();
    }
    private static void controls(GameTestHelper h) {
        var machine = machine(h); Player player = WeaponGameTests.player(h); machine.setOwner(player);
        var menu = new AmmoPressMenu(17, player.getInventory(), machine, machine.data); player.containerMenu = menu;
        h.assertTrue(menu.clickMenuButton(player, 0), "Open menu can select plan");
        h.assertValueEqual(machine.data.get(3), 1, "Next plan applied");
        h.assertTrue(!menu.clickMenuButton(player, 42), "Unknown button rejected");
        h.assertTrue(menu.clickMenuButton(player, 3), "Owner can restrict access");
        Player other = WeaponGameTests.player(h); other.setUUID(UUID.fromString("00000000-0000-0000-0000-000000000027"));
        h.assertTrue(!machine.canOpen(other), "Owner-only machine rejects another player");
        player.setPos(player.getX() + 30, player.getY(), player.getZ());
        h.assertTrue(!menu.clickMenuButton(player, 0), "Remote menu actions rejected"); h.succeed();
    }
    private static void shiftClick(GameTestHelper h) {
        var machine = machine(h); Player player = WeaponGameTests.player(h);
        var menu = new AmmoPressMenu(18, player.getInventory(), machine, machine.data); player.containerMenu = menu;
        player.getInventory().setItem(9, TGContent.MATERIALS.get("ingotlead").toStack(3));
        h.assertValueEqual(menu.quickMoveStack(player, 5).getCount(), 3, "Shift-click routes first metal");
        h.assertValueEqual(machine.getItem(0).getCount(), 3, "Input receives entire stack");
        player.getInventory().setItem(10, new ItemStack(Items.STONE));
        h.assertTrue(menu.quickMoveStack(player, 6).isEmpty(), "Invalid item remains in player inventory");
        machine.setItem(3, TGContent.AMMO.get("pistolrounds").toStack(12));
        h.assertValueEqual(menu.quickMoveStack(player, 3).getCount(), 12, "Shift-click extracts output");
        h.assertTrue(machine.getItem(3).isEmpty(), "Output is not duplicated");
        h.assertValueEqual(player.getInventory().countItem(TGContent.AMMO.get("pistolrounds").get()), 12, "Player receives output exactly once"); h.succeed();
    }
    private static void codec(GameTestHelper h) {
        var machine = machine(h); supply(machine, 1);
        var input = new AmmoPressRecipe.Input(0, List.of(machine.getItem(0), machine.getItem(1), machine.getItem(2)));
        var recipe = h.getLevel().getServer().getRecipeManager().getRecipeFor(TGMachineContent.AMMO_PRESS_RECIPE.get(), input, h.getLevel()).orElseThrow().value();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            Recipe.STREAM_CODEC.encode(buffer, recipe);
            var decoded = (AmmoPressRecipe) Recipe.STREAM_CODEC.decode(buffer);
            h.assertTrue(decoded.matches(input, h.getLevel()), "Synced recipe matches real tags");
            h.assertValueEqual(decoded.duration(), 100, "Cycle time survives codec");
            h.assertValueEqual(decoded.powerPerTick(), 5, "Power cost survives codec");
            h.assertValueEqual(decoded.assemble(input).getCount(), 12, "Output survives codec");
        } finally { buffer.release(); }
        h.succeed();
    }
    private static void optionalPower(GameTestHelper h) {
        var machine = machine(h); supply(machine, 1);
        boolean previous = TGMachineConfig.MACHINES_NEED_NO_POWER.get();
        try {
            TGMachineConfig.MACHINES_NEED_NO_POWER.set(true);
            for (int tick = 0; tick < 101; tick++) AmmoPressBlockEntity.tick(h.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
            assertOutput(h, machine, 0, 12);
            h.assertValueEqual(machine.energy().getAmountAsLong(), 0L, "Original no-power config works without inventing a generator");
        } finally { TGMachineConfig.MACHINES_NEED_NO_POWER.set(previous); }
        h.succeed();
    }
    private static void hoppers(GameTestHelper h) {
        var machine = machine(h); charge(h, 500);
        h.setBlock(POS.above(), Blocks.HOPPER);
        h.setBlock(POS.below(), Blocks.HOPPER);
        var top = (HopperBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(POS.above()));
        var bottom = (HopperBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(POS.below()));
        top.setItem(0, TGContent.MATERIALS.get("ingotlead").toStack());
        top.setItem(1, new ItemStack(Items.IRON_INGOT, 2));
        top.setItem(2, new ItemStack(Items.GUNPOWDER));
        h.runAfterDelay(250, () -> {
            int output = 0;
            for (int slot = 0; slot < bottom.getContainerSize(); slot++) {
                ItemStack stack = bottom.getItem(slot);
                h.assertTrue(stack.isEmpty() || stack.is(TGContent.AMMO.get("pistolrounds").get()), "Hopper only extracts the output slot");
                output += stack.getCount();
            }
            h.assertValueEqual(output, 12, "Real hoppers feed and collect one complete production batch");
            h.assertTrue(top.isEmpty() && machine.getItem(3).isEmpty(), "Materials and output were transferred once");
            h.succeed();
        });
    }
    private static void crafting(GameTestHelper h) {
        ItemStack empty = ItemStack.EMPTY, copperNugget = new ItemStack(Items.COPPER_NUGGET);
        ItemStack wire = CraftingGameTests.craft(h, 3, 3, empty,copperNugget,copperNugget,empty,copperNugget,empty,copperNugget,copperNugget,empty);
        h.assertTrue(wire.is(TGContent.MATERIALS.get("copperwire").get()), "Original copper wire recipe");
        ItemStack redstone = new ItemStack(Items.REDSTONE), iron = new ItemStack(Items.IRON_INGOT);
        ItemStack mechanical = TGContent.MATERIALS.get("mechanicalpartsiron").toStack();
        ItemStack engine = CraftingGameTests.craft(h, 3, 3, wire,redstone,wire,iron,mechanical,iron,wire,redstone,wire);
        h.assertTrue(engine.is(TGContent.MATERIALS.get("electricengine").get()), "Original motor recipe");
        ItemStack copper = new ItemStack(Items.COPPER_INGOT), lead = TGContent.MATERIALS.get("ingotlead").toStack();
        ItemStack press = CraftingGameTests.craft(h, 3, 3, iron,lead,iron,copper,engine,copper,iron,redstone,iron);
        h.assertTrue(press.is(TGMachineContent.AMMO_PRESS_ITEM.get()), "Workbench produces registered machine BlockItem");
        h.assertValueEqual(press.getCount(), 1, "One machine per recipe");
        ItemStack plate = TGContent.MATERIALS.get("plateiron").toStack(), gold = new ItemStack(Items.GOLD_INGOT);
        ItemStack upgrade = CraftingGameTests.craft(h, 3, 3, plate,new ItemStack(Items.DYE.green()),plate,gold,new ItemStack(Items.CHEST),gold,plate,gold,plate);
        h.assertTrue(upgrade.is(TGContent.MATERIALS.get("machinestackupgrade").get()), "Original reusable batch upgrade recipe");
        h.assertValueEqual(upgrade.getMaxStackSize(), 7, "Upgrade retains original stack limit");
        h.succeed();
    }
    private static void earlierSave(GameTestHelper h) {
        var machine = machine(h);
        var saved = machine.saveWithFullMetadata(h.getLevel().registryAccess());
        saved.remove("mode");
        saved.putInt("plan", 3); // field written by the first 26.2 Ammo Press port
        var restored = (AmmoPressBlockEntity) BlockEntity.loadStatic(h.absolutePos(POS), machine.getBlockState(), saved, h.getLevel().registryAccess());
        h.assertValueEqual(restored.data.get(3), 3, "Shared machine implementation still reads the earlier port's saved plan");
        h.assertValueEqual(restored.getContainerSize(), 5, "Inventory layout remains compatible");
        h.succeed();
    }
    private AmmoPressGameTests() {}
}
