package techguns.modern.test;

import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.TGContent;
import techguns.modern.armor.*;
import techguns.modern.machine.repair.*;

final class RepairBenchGameTests {
    private static final BlockPos POS = new BlockPos(4, 2, 4);
    private record Fixture(RepairBenchBlockEntity bench, Player player, RepairBenchMenu menu) {}
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for (var slot : RepairBenchMenu.ARMOR) r.register("repair_worn_" + slot.getName(), () -> h -> worn(h, slot));
        r.register("repair_bench_components", () -> RepairBenchGameTests::components);
        r.register("repair_offhand", () -> RepairBenchGameTests::offhand);
        r.register("repair_partial_rounding", () -> RepairBenchGameTests::partial);
        r.register("repair_split_named_materials", () -> RepairBenchGameTests::split);
        r.register("repair_missing_material_rollback", () -> RepairBenchGameTests::missing);
        r.register("repair_no_player_materials", () -> RepairBenchGameTests::playerMaterials);
        r.register("repair_wrong_and_healthy_items", () -> RepairBenchGameTests::wrong);
        r.register("repair_current_target_after_swap", () -> RepairBenchGameTests::swap);
        r.register("repair_repeated_button", () -> RepairBenchGameTests::repeat);
        r.register("repair_bonuses_restore", () -> RepairBenchGameTests::bonuses);
        r.register("repair_access_all_buttons_and_slots", () -> RepairBenchGameTests::access);
        r.register("repair_closed_menu", () -> RepairBenchGameTests::closed);
        r.register("repair_remote_and_removed", () -> RepairBenchGameTests::remote);
        r.register("repair_saved_inventory_and_owner", () -> RepairBenchGameTests::saved);
        r.register("repair_menu_network_slots_and_access", () -> RepairBenchGameTests::sync);
        r.register("repair_quick_move_boundaries", () -> RepairBenchGameTests::quickMove);
        r.register("repair_binding_curse", () -> RepairBenchGameTests::binding);
        r.register("repair_full_player_inventory", () -> RepairBenchGameTests::fullInventory);
        r.register("repair_ports_transactions_no_energy", () -> RepairBenchGameTests::ports);
        r.register("repair_hopper_chain", () -> RepairBenchGameTests::hopper);
        r.register("repair_original_recipe_and_shape", () -> RepairBenchGameTests::recipe);
        r.register("repair_break_returns_contents_once", () -> RepairBenchGameTests::breaking);
        r.register("repair_spectator_no_mutation", () -> RepairBenchGameTests::spectator);
        r.register("repair_unowned_claim", () -> RepairBenchGameTests::claim);
    }
    private static ItemStack material(String id, int count) { return TGContent.MATERIALS.get(id).toStack(count); }
    private static ItemStack armor(EquipmentSlot slot, int damage) {
        var stack = ArmorContent.ITEMS.get(techguns.core.ArmorSlot.valueOf(slot.name())).toStack();
        stack.setDamageValue(damage); TGArmorItem.setCamo(stack, 3); return stack;
    }
    private static Fixture fixture(GameTestHelper h) {
        h.setBlock(POS, RepairBenchContent.BLOCK.get());
        var bench = h.getBlockEntity(POS, RepairBenchBlockEntity.class);
        var player = WeaponGameTests.player(h); player.getInventory().clearContent(); bench.setOwner(player);
        var menu = new RepairBenchMenu(53, player.getInventory(), bench); player.containerMenu = menu;
        return new Fixture(bench, player, menu);
    }
    private static void supply(RepairBenchBlockEntity bench, int metal, int cloth) {
        bench.setItem(0, material("ingotobsidiansteel", metal)); bench.setItem(1, material("heavycloth", cloth));
    }
    private static void worn(GameTestHelper h, EquipmentSlot slot) {
        var f = fixture(h); var armor = armor(slot, 989); f.player.setItemSlot(slot, armor);
        int[][] costs = {{1,1},{2,2},{1,2},{1,1}}; int index = RepairBenchMenu.ARMOR.indexOf(slot);
        supply(f.bench, 8, 8); h.assertTrue(f.menu.clickMenuButton(f.player, index + 1), "Equipped armor repairs without energy");
        h.assertValueEqual(armor.getDamageValue(), 0, "Full condition restored");
        h.assertValueEqual(f.bench.getItem(0).getCount(), 8 - costs[index][0], "Original metal cost");
        h.assertValueEqual(f.bench.getItem(1).getCount(), 8 - costs[index][1], "Original cloth cost"); h.succeed();
    }
    private static void components(GameTestHelper h) {
        var f = fixture(h); var armor = armor(EquipmentSlot.CHEST, 989);
        armor.set(DataComponents.CUSTOM_NAME, Component.literal("Field kit")); armor.set(DataComponents.REPAIR_COST, 15);
        var enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchants.set(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
        armor.set(DataComponents.ENCHANTMENTS, enchants.toImmutable());
        var expected = armor.copy(); expected.setDamageValue(0); f.bench.setItem(9, armor); supply(f.bench, 2, 2);
        f.player.experienceLevel = 7;
        h.assertTrue(f.menu.clickMenuButton(f.player, 6), "Bench slot repaired");
        h.assertTrue(ItemStack.matches(f.bench.getItem(9), expected), "Name, enchantments, camo, equipment asset and anvil penalty retained");
        h.assertValueEqual(f.player.experienceLevel, 7, "Repair Bench consumes no XP"); h.succeed();
    }
    private static void offhand(GameTestHelper h) {
        var f = fixture(h); var armor = armor(EquipmentSlot.FEET, 989); f.player.setItemSlot(EquipmentSlot.OFFHAND, armor); supply(f.bench, 1, 1);
        h.assertTrue(f.menu.clickMenuButton(f.player, 5) && armor.getDamageValue() == 0, "Offhand repair uses its own target"); h.succeed();
    }
    private static void partial(GameTestHelper h) {
        var f = fixture(h);
        int[][] cases = {{1,1,0},{247,1,0},{248,1,1},{494,1,1},{495,2,1},{741,2,1},{742,2,2},{989,2,2}};
        for (var c : cases) {
            f.bench.setItem(9, armor(EquipmentSlot.CHEST, c[0])); supply(f.bench, 8, 8);
            h.assertTrue(f.menu.clickMenuButton(f.player, 6), "Partial repair succeeds at " + c[0]);
            h.assertValueEqual(f.bench.getItem(0).getCount(), 8 - c[1], "Metal rounding threshold");
            h.assertValueEqual(f.bench.getItem(1).getCount(), 8 - c[2], "Cloth rounding threshold");
        }
        h.succeed();
    }
    private static void split(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(9, armor(EquipmentSlot.CHEST, 989));
        for (int slot : new int[]{0,7}) { var metal = material("ingotobsidiansteel", 1); metal.set(DataComponents.CUSTOM_NAME, Component.literal("Salvage " + slot)); f.bench.setItem(slot, metal); }
        f.bench.setItem(2, material("heavycloth", 1)); f.bench.setItem(8, material("heavycloth", 1)); f.bench.setItem(4, new ItemStack(Items.DIAMOND, 13));
        h.assertTrue(f.menu.clickMenuButton(f.player, 6), "Materials can be distributed across all nine slots with unrelated names");
        h.assertTrue(f.bench.getItem(0).isEmpty() && f.bench.getItem(7).isEmpty() && f.bench.getItem(8).isEmpty(), "Required items consumed exactly once");
        h.assertValueEqual(f.bench.getItem(4).getCount(), 13, "Unrelated supplies remain"); h.succeed();
    }
    private static void missing(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(9, armor(EquipmentSlot.CHEST, 989)); supply(f.bench, 2, 1);
        var before = f.bench.getItem(0).copy();
        h.assertTrue(!f.menu.clickMenuButton(f.player, 6), "Missing cloth rejects whole repair");
        h.assertTrue(ItemStack.matches(f.bench.getItem(0), before), "Earlier metal extraction rolled back");
        h.assertValueEqual(f.bench.getItem(1).getCount(), 1, "Partial cloth extraction rolled back");
        h.assertValueEqual(f.bench.getItem(9).getDamageValue(), 989, "Failed repair cannot improve condition"); h.succeed();
    }
    private static void playerMaterials(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(9, armor(EquipmentSlot.HEAD, 989)); f.player.getInventory().setItem(9, material("ingotobsidiansteel", 64));
        f.player.getInventory().setItem(10, material("heavycloth", 64));
        h.assertTrue(!f.menu.clickMenuButton(f.player, 6), "Player inventory never contributes to repair costs");
        h.assertValueEqual(f.player.getInventory().getItem(9).getCount(), 64, "Player resources unchanged"); h.succeed();
    }
    private static void wrong(GameTestHelper h) {
        var f = fixture(h); supply(f.bench, 8, 8);
        for (var item : List.of(ItemStack.EMPTY, armor(EquipmentSlot.HEAD, 0), new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.IRON_SWORD))) {
            f.bench.setItem(9, item); h.assertTrue(!f.menu.clickMenuButton(f.player, 6), "Only damaged supported armor is repaired");
        }
        for (int button : new int[]{-1,7,255}) h.assertTrue(!f.menu.clickMenuButton(f.player, button), "Invalid button rejected");
        h.assertValueEqual(f.bench.getItem(0).getCount(), 8, "Invalid actions cost no materials"); h.succeed();
    }
    private static void swap(GameTestHelper h) {
        var f = fixture(h); var old = armor(EquipmentSlot.CHEST, 989); f.bench.setItem(9, old);
        h.assertValueEqual(RepairBenchBlockEntity.costs(f.menu.target(6)).size(), 2, "Initial quote has both materials");
        var replacement = armor(EquipmentSlot.HEAD, 1); f.bench.setItem(9, replacement); supply(f.bench, 1, 0);
        h.assertTrue(f.menu.clickMenuButton(f.player, 6), "Server recomputes current target and cost");
        h.assertValueEqual(replacement.getDamageValue(), 0, "Current stack repaired"); h.assertValueEqual(old.getDamageValue(), 989, "Removed stack untouched"); h.succeed();
    }
    private static void repeat(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(9, armor(EquipmentSlot.HEAD, 989)); supply(f.bench, 8, 8);
        h.assertTrue(f.menu.clickMenuButton(f.player, 6), "First request accepted");
        for (int i = 0; i < 8; i++) h.assertTrue(!f.menu.clickMenuButton(f.player, 6), "Repeated request cannot charge again");
        h.assertValueEqual(f.bench.getItem(0).getCount(), 7, "Only one repair charged"); h.succeed();
    }
    private static void bonuses(GameTestHelper h) {
        var f = fixture(h); f.player.setItemSlot(EquipmentSlot.HEAD, armor(EquipmentSlot.HEAD, 989)); TGArmorSystem.refresh(f.player);
        h.assertTrue(Math.abs(f.player.getAttributeValue(Attributes.MOVEMENT_SPEED) - .1) < .00001, "Worn armor has no speed bonus");
        supply(f.bench, 1, 1); f.menu.clickMenuButton(f.player, 1);
        h.assertTrue(Math.abs(f.player.getAttributeValue(Attributes.MOVEMENT_SPEED) - .11) < .00001, "Repair immediately restores speed bonus");
        h.assertValueEqual((int)f.player.getAttributeValue(Attributes.ARMOR), 5, "Armor indicator restored"); h.succeed();
    }
    private static Player other(GameTestHelper h) { var player = WeaponGameTests.player(h); player.setUUID(UUID.randomUUID()); return player; }
    private static void access(GameTestHelper h) {
        var f = fixture(h); var other = other(h); var otherMenu = new RepairBenchMenu(54, other.getInventory(), f.bench); other.containerMenu = otherMenu;
        h.assertTrue(otherMenu.stillValid(other), "Public bench is shared"); h.assertTrue(!otherMenu.clickMenuButton(other, 0), "Only owner changes access");
        supply(f.bench, 8, 8); f.bench.setItem(9, armor(EquipmentSlot.HEAD, 989));
        h.assertTrue(f.menu.clickMenuButton(f.player, 0), "Owner makes bench private");
        for (int button = 0; button <= 6; button++) h.assertTrue(!otherMenu.clickMenuButton(other, button), "Every button checks current access");
        otherMenu.clicked(0, 0, ContainerInput.PICKUP, other);
        h.assertTrue(otherMenu.getCarried().isEmpty() && otherMenu.quickMoveStack(other, 0).isEmpty(), "Open stale viewer cannot take supplies");
        h.assertValueEqual(f.bench.getItem(0).getCount(), 8, "Private supplies retained");
        h.assertTrue(f.menu.clickMenuButton(f.player, 6), "Owner still repairs"); h.succeed();
    }
    private static void closed(GameTestHelper h) {
        var f = fixture(h); supply(f.bench, 8, 8); f.bench.setItem(9, armor(EquipmentSlot.HEAD, 989)); f.player.containerMenu = f.player.inventoryMenu;
        for (int button = 0; button <= 6; button++) h.assertTrue(!f.menu.clickMenuButton(f.player, button), "Closed menu cannot execute a request");
        f.menu.clicked(0, 0, ContainerInput.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().isEmpty() && f.menu.quickMoveStack(f.player, 0).isEmpty(), "Closed menu cannot transfer items"); h.succeed();
    }
    private static void remote(GameTestHelper h) {
        var f = fixture(h); supply(f.bench, 8, 8); f.bench.setItem(9, armor(EquipmentSlot.HEAD, 989));
        var position = f.player.position(); f.player.setPos(position.add(100, 0, 0));
        for (int button = 0; button <= 6; button++) h.assertTrue(!f.menu.clickMenuButton(f.player, button), "Distance checked for every target");
        f.player.setPos(position); h.getLevel().destroyBlock(f.bench.getBlockPos(), false);
        h.assertTrue(!f.menu.clickMenuButton(f.player, 6) && !f.menu.stillValid(f.player), "Removed bench invalidates existing menu"); h.succeed();
    }
    private static RepairBenchBlockEntity reload(GameTestHelper h, RepairBenchBlockEntity old) {
        var tag = old.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored = (RepairBenchBlockEntity)BlockEntity.loadStatic(old.getBlockPos(), old.getBlockState(), tag, h.getLevel().registryAccess());
        h.getLevel().removeBlockEntity(old.getBlockPos()); h.getLevel().setBlockEntity(restored); return restored;
    }
    private static void saved(GameTestHelper h) {
        var f = fixture(h); var item = armor(EquipmentSlot.CHEST, 989); item.set(DataComponents.CUSTOM_NAME, Component.literal("Saved armor"));
        f.bench.setItem(9, item); supply(f.bench, 3, 4); f.menu.clickMenuButton(f.player, 0); var restored = reload(h, f.bench);
        h.assertTrue(restored.isOwner(f.player) && !restored.canOpen(other(h)), "Private owner survives reload");
        h.assertTrue(ItemStack.matches(restored.getItem(9), item), "Damaged armor and components survive reload");
        var menu = new RepairBenchMenu(55, f.player.getInventory(), restored); f.player.containerMenu = menu;
        h.assertTrue(menu.clickMenuButton(f.player, 6), "Saved supplies repair saved armor"); restored = reload(h, restored);
        h.assertTrue(restored.getItem(9).getDamageValue() == 0 && restored.getItem(0).getCount() == 1 && restored.getItem(1).getCount() == 2, "Committed repair persists without replay"); h.succeed();
    }
    private static void sync(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(9, armor(EquipmentSlot.CHEST, 989)); supply(f.bench, 2, 2); f.menu.clickMenuButton(f.player, 0);
        var client = new RepairBenchMenu(53, other(h).getInventory());
        for (int slot = 0; slot < 10; slot++) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                ClientboundContainerSetSlotPacket.STREAM_CODEC.encode(buffer, new ClientboundContainerSetSlotPacket(53, 7, slot, f.menu.getSlot(slot).getItem()));
                var packet = ClientboundContainerSetSlotPacket.STREAM_CODEC.decode(buffer); client.setItem(packet.getSlot(), packet.getStateId(), packet.getItem());
            } finally { buffer.release(); }
        }
        for (int key = 0; key < 2; key++) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundContainerSetDataPacket.STREAM_CODEC.encode(buffer, new ClientboundContainerSetDataPacket(53, key, f.menu.value(key)));
                var packet = ClientboundContainerSetDataPacket.STREAM_CODEC.decode(buffer); client.setData(packet.getId(), packet.getValue());
            } finally { buffer.release(); }
        }
        h.assertTrue(ItemStack.matches(client.target(6), f.menu.target(6)), "Client quote uses actual synced armor components");
        h.assertValueEqual(client.available(material("heavycloth", 1)), 2, "Client sees material inventory");
        h.assertTrue(client.value(0) == 1 && client.value(1) == 1, "Access and per-viewer ownership survive menu packets");
        h.assertValueEqual(client.slots.size(), 51, "Ten bench, 36 player, four armor and offhand slots"); h.succeed();
    }
    private static void quickMove(GameTestHelper h) {
        var f = fixture(h); f.player.getInventory().setItem(9, material("heavycloth", 12));
        h.assertTrue(!f.menu.quickMoveStack(f.player, 10).isEmpty() && f.bench.getItem(0).getCount() == 12, "First main inventory slot goes to material row");
        f.player.getInventory().setItem(0, armor(EquipmentSlot.HEAD, 900));
        h.assertTrue(!f.menu.quickMoveStack(f.player, 37).isEmpty() && !f.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "First hotbar slot equips armor");
        f.player.getInventory().setItem(8, armor(EquipmentSlot.HEAD, 989));
        h.assertTrue(!f.menu.quickMoveStack(f.player, 45).isEmpty() && f.bench.getItem(9).getDamageValue() == 989, "Occupied equipment routes spare armor to repair slot");
        h.assertTrue(!f.menu.quickMoveStack(f.player, 9).isEmpty() && f.bench.getItem(9).isEmpty(), "Repair slot returns item to player");
        h.assertTrue(!f.menu.quickMoveStack(f.player, 46).isEmpty() && f.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "Armor row returns item to player");
        f.player.setItemSlot(EquipmentSlot.OFFHAND, armor(EquipmentSlot.FEET, 20));
        h.assertTrue(!f.menu.quickMoveStack(f.player, 50).isEmpty() && f.player.getOffhandItem().isEmpty(), "Offhand boundary returns item");
        for (int invalid : new int[]{-1,51,500}) h.assertTrue(f.menu.quickMoveStack(f.player, invalid).isEmpty(), "Out of range slots rejected"); h.succeed();
    }
    private static void binding(GameTestHelper h) {
        var f = fixture(h); var armor = armor(EquipmentSlot.HEAD, 989); var enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchants.set(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1); armor.set(DataComponents.ENCHANTMENTS, enchants.toImmutable());
        f.player.setItemSlot(EquipmentSlot.HEAD, armor); supply(f.bench, 1, 1);
        h.assertTrue(f.menu.quickMoveStack(f.player, 46).isEmpty(), "Binding curse prevents unequipping through bench");
        h.assertTrue(f.menu.clickMenuButton(f.player, 1) && armor.getDamageValue() == 0, "Worn bound armor can still be repaired"); h.succeed();
    }
    private static void fullInventory(GameTestHelper h) {
        var f = fixture(h); for (int i = 0; i < 36; i++) f.player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        var armor = armor(EquipmentSlot.CHEST, 989); f.bench.setItem(9, armor);
        h.assertTrue(f.menu.quickMoveStack(f.player, 9).isEmpty() && ItemStack.matches(f.bench.getItem(9), armor), "Full player inventory cannot delete target"); h.succeed();
    }
    private static void ports(GameTestHelper h) {
        var f = fixture(h); var sides = new ArrayList<Direction>(List.of(Direction.values())); sides.add(null);
        for (var side : sides) {
            var handler = h.getLevel().getCapability(Capabilities.Item.BLOCK, f.bench.getBlockPos(), side);
            h.assertTrue(handler != null && handler.size() == 10, "All faces and unsided expose ten slots");
            h.assertTrue(h.getLevel().getCapability(Capabilities.Energy.BLOCK, f.bench.getBlockPos(), side) == null, "No energy port");
            h.assertTrue(h.getLevel().getCapability(Capabilities.Fluid.BLOCK, f.bench.getBlockPos(), side) == null, "No fluid port");
            try (var tx = Transaction.openRoot()) {
                h.assertValueEqual(handler.insert(8, ItemResource.of(material("heavycloth", 1)), 80, tx), 64, "Material slots retain native stack limit");
                h.assertValueEqual(handler.extract(8, ItemResource.of(material("heavycloth", 1)), 1, tx), 1, "Stored materials can be withdrawn");
                h.assertValueEqual(handler.insert(9, ItemResource.of(new ItemStack(Items.STONE)), 1, tx), 0, "Repair slot rejects unrelated automated input");
                h.assertValueEqual(handler.insert(9, ItemResource.of(armor(EquipmentSlot.HEAD, 989)), 2, tx), 1, "Repair slot accepts one armor item");
            }
            h.assertTrue(f.bench.isEmpty(), "Aborted transactions restore entire inventory");
        }
        try (var tx = Transaction.openRoot()) { f.bench.automation().insert(9, ItemResource.of(armor(EquipmentSlot.HEAD, 989)), 1, tx); tx.commit(); }
        h.assertValueEqual(f.bench.getItem(9).getDamageValue(), 989, "Committed insertion preserves wear"); h.succeed();
    }
    private static void hopper(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(9, armor(EquipmentSlot.HEAD, 989));
        h.setBlock(POS.above(), Blocks.HOPPER); var input = h.getBlockEntity(POS.above(), HopperBlockEntity.class);
        input.setItem(0, material("ingotobsidiansteel", 1)); input.setItem(1, material("heavycloth", 1));
        h.runAfterDelay(25, () -> {
            h.assertTrue(f.menu.clickMenuButton(f.player, 6), "Real hopper supplies repair materials"); h.setBlock(POS.below(), Blocks.HOPPER);
        });
        h.runAfterDelay(55, () -> {
            var output = h.getBlockEntity(POS.below(), HopperBlockEntity.class);
            h.assertTrue(output.getItem(0).is(ArmorContent.ITEMS.get(techguns.core.ArmorSlot.HEAD).get()) && output.getItem(0).getDamageValue() == 0, "Real hopper extracts repaired armor");
            h.assertTrue(f.bench.isEmpty() && input.isEmpty(), "No duplicate supplies or armor left behind"); h.succeed();
        });
    }
    private static void recipe(GameTestHelper h) {
        var f = fixture(h); var plate = material("plateiron", 1); var nugget = new ItemStack(Items.IRON_NUGGET);
        var grid = CraftingInput.of(3, 3, List.of(plate, material("mechanicalpartsiron", 1), plate, nugget, new ItemStack(Items.CRAFTING_TABLE), nugget, nugget, nugget, nugget));
        var output = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, h.getLevel()).orElseThrow().value().assemble(grid);
        h.assertTrue(output.is(RepairBenchContent.ITEM.get()) && output.getCount() == 1, "Original metadata 9 recipe with iron plates, parts, nuggets and workbench");
        for (var facing : Direction.Plane.HORIZONTAL) {
            var state = RepairBenchContent.BLOCK.get().defaultBlockState().setValue(RepairBenchBlock.FACING, facing); h.setBlock(POS, state);
            h.assertTrue(state.canOcclude(), "Original opaque full cube");
            h.assertValueEqual(state.getCollisionShape(h.getLevel(), f.bench.getBlockPos()).bounds(), new AABB(0,0,0,1,1,1), "Full cube collision");
            h.assertValueEqual(state.rotate(Rotation.CLOCKWISE_90).getValue(RepairBenchBlock.FACING), facing.getClockWise(), "Facing rotates consistently with model");
        }
        h.succeed();
    }
    private static void breaking(GameTestHelper h) {
        var f = fixture(h); supply(f.bench, 3, 5); var armor = armor(EquipmentSlot.CHEST, 789); armor.set(DataComponents.CUSTOM_NAME, Component.literal("Dropped kit")); f.bench.setItem(9, armor);
        h.getLevel().destroyBlock(f.bench.getBlockPos(), true);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(f.bench.getBlockPos()).inflate(2)).stream().map(ItemEntity::getItem).toList();
        h.assertValueEqual(drops.stream().filter(s -> s.is(RepairBenchContent.ITEM.get())).mapToInt(ItemStack::getCount).sum(), 1, "One bench block returned");
        h.assertValueEqual(drops.stream().filter(s -> s.is(material("heavycloth", 1).getItem())).mapToInt(ItemStack::getCount).sum(), 5, "All cloth returned");
        h.assertValueEqual(drops.stream().filter(s -> s.is(material("ingotobsidiansteel", 1).getItem())).mapToInt(ItemStack::getCount).sum(), 3, "All metal returned");
        var result = drops.stream().filter(s -> s.getItem() instanceof TGArmorItem).toList();
        h.assertTrue(result.size() == 1 && result.getFirst().getDamageValue() == 789 && result.getFirst().getHoverName().getString().equals("Dropped kit"), "Armor dropped exactly once with wear and name");
        try (var tx = Transaction.openRoot()) { h.assertValueEqual(f.bench.automation().extract(9, ItemResource.of(result.getFirst()), 1, tx), 0, "Stale handler cannot extract dropped armor again"); }
        h.succeed();
    }
    private static void spectator(GameTestHelper h) {
        var f = fixture(h); var spectator = h.makeMockPlayer(GameType.SPECTATOR); spectator.setPos(f.player.position());
        var menu = new RepairBenchMenu(56, spectator.getInventory(), f.bench); spectator.containerMenu = menu;
        supply(f.bench, 8, 8); f.bench.setItem(9, armor(EquipmentSlot.HEAD, 989));
        h.assertTrue(!menu.clickMenuButton(spectator, 6) && menu.quickMoveStack(spectator, 0).isEmpty(), "Spectators cannot consume or extract supplies"); h.succeed();
    }
    private static void claim(GameTestHelper h) {
        h.setBlock(POS, RepairBenchContent.BLOCK.get()); var bench = h.getBlockEntity(POS, RepairBenchBlockEntity.class); var player = WeaponGameTests.player(h);
        var menu = bench.createMenu(57, player.getInventory(), player); player.containerMenu = menu;
        h.assertTrue(bench.isOwner(player) && menu.clickMenuButton(player, 0), "First real opener claims an unowned placed-by-command bench"); h.succeed();
    }
}
