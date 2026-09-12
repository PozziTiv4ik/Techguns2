package techguns.modern.test;

import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.armor.*;
import techguns.modern.machine.camo.*;
import techguns.modern.machine.repair.*;
import techguns.modern.TGContent;

final class CamoBenchGameTests {
    private static final BlockPos POS = new BlockPos(4,2,4);
    private record Fixture(CamoBenchBlockEntity bench, Player player, CamoBenchMenu menu) {}
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for (String family : List.of("wool","concrete","concrete_powder","terracotta","stained_glass","stained_glass_pane","banner","carpet"))
            r.register("camo_palette_" + family, () -> h -> palette(h, family));
        for (var slot : CamoBenchMenu.ARMOR) r.register("camo_equipped_" + slot.getName(), () -> h -> equipped(h, slot));
        r.register("camo_armor_components_and_reverse", () -> CamoBenchGameTests::armorComponents);
        r.register("camo_banner_patterns_and_item_defaults", () -> CamoBenchGameTests::banner);
        r.register("camo_banner_placement", () -> CamoBenchGameTests::bannerPlacement);
        r.register("camo_unsupported_items", () -> CamoBenchGameTests::unsupported);
        r.register("camo_current_stack_after_swap", () -> CamoBenchGameTests::swap);
        r.register("camo_public_viewers_and_equipment", () -> CamoBenchGameTests::viewers);
        r.register("camo_access_revocation", () -> CamoBenchGameTests::access);
        r.register("camo_closed_and_wrong_bench_menu", () -> CamoBenchGameTests::closed);
        r.register("camo_distance_and_removed_bench", () -> CamoBenchGameTests::distance);
        r.register("camo_spectator", () -> CamoBenchGameTests::spectator);
        r.register("camo_saved_palette_armor_and_owner", () -> CamoBenchGameTests::saved);
        r.register("camo_menu_slots_and_packet", () -> CamoBenchGameTests::sync);
        r.register("camo_shift_into_empty_slot", () -> CamoBenchGameTests::shift);
        r.register("camo_shift_merge_and_full_input", () -> CamoBenchGameTests::merge);
        r.register("camo_shift_armor_fallback", () -> CamoBenchGameTests::armorShift);
        r.register("camo_binding_curse", () -> CamoBenchGameTests::binding);
        r.register("camo_full_inventory", () -> CamoBenchGameTests::fullInventory);
        r.register("camo_ports_no_energy", () -> CamoBenchGameTests::ports);
        r.register("camo_hopper_chain", () -> CamoBenchGameTests::hopper);
        r.register("camo_original_recipe_and_shape", () -> CamoBenchGameTests::recipe);
        r.register("camo_break_once", () -> CamoBenchGameTests::breaking);
        r.register("camo_unowned_claim", () -> CamoBenchGameTests::claim);
        r.register("camo_repair_shared_saved_schema", () -> CamoBenchGameTests::repairSchema);
    }
    private static Fixture fixture(GameTestHelper h) {
        h.setBlock(POS, CamoBenchContent.BLOCK.get()); var bench = h.getBlockEntity(POS,CamoBenchBlockEntity.class);
        var player = WeaponGameTests.player(h); player.getInventory().clearContent(); bench.setOwner(player);
        var menu = new CamoBenchMenu(61,player.getInventory(),bench); player.containerMenu = menu;
        return new Fixture(bench,player,menu);
    }
    private static Item item(String path) { return Objects.requireNonNull(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path))); }
    private static ItemStack armor(EquipmentSlot slot) {
        var stack = ArmorContent.ITEMS.get(techguns.core.ArmorSlot.valueOf(slot.name())).toStack(); stack.setDamageValue(517); TGArmorItem.setCamo(stack,5);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Field camouflage")); return stack;
    }
    private static Player other(GameTestHelper h) { var player = WeaponGameTests.player(h); player.setUUID(UUID.randomUUID()); return player; }
    private static void palette(GameTestHelper h,String family) {
        var f = fixture(h); boolean banner = family.equals("banner"); int count = banner ? 16 : 64;
        var initial = new ItemStack(item((banner ? "black" : "white") + "_" + family),count); initial.set(DataComponents.CUSTOM_NAME,Component.literal("Bulk paint")); f.bench.setItem(0,initial);
        f.player.experienceLevel = 7;
        for (int direction : new int[]{1,2}) for (int step = 1; step <= 16; step++) {
            h.assertTrue(f.menu.clickMenuButton(f.player,direction),"Server changes palette " + family);
            int index = Math.floorMod(direction == 1 ? step : -step,16);
            String color = DyeColor.byId(banner ? 15 - index : index).getName(); var target = item(color + "_" + family); var actual = f.bench.getItem(0);
            h.assertTrue(actual.is(target),"Original metadata order at " + family + ":" + index);
            h.assertValueEqual(actual.getCount(),count,"Whole stack count survives");
            h.assertValueEqual(actual.getHoverName().getString(),"Bulk paint","Custom name survives");
            h.assertValueEqual(actual.get(DataComponents.ITEM_MODEL),target.getDefaultInstance().get(DataComponents.ITEM_MODEL),"New item uses its own native model default");
        }
        h.assertValueEqual(f.player.experienceLevel,7,"Recoloring spends no XP or dyes"); h.succeed();
    }
    private static void equipped(GameTestHelper h,EquipmentSlot slot) {
        var f = fixture(h); var original = armor(slot); f.player.setItemSlot(slot,original); int forward = 3 + 2 * CamoBenchMenu.ARMOR.indexOf(slot);
        h.assertTrue(f.menu.clickMenuButton(f.player,forward),"Equipped item supports forward cycle");
        h.assertValueEqual(TGArmorItem.camo(f.player.getItemBySlot(slot)),0,"Last camo wraps to first");
        h.assertTrue(f.menu.clickMenuButton(f.player,forward + 1),"Equipped item supports backward cycle");
        h.assertTrue(ItemStack.matches(f.player.getItemBySlot(slot),original),"Round trip keeps wear, name and equipment asset"); h.succeed();
    }
    private static void armorComponents(GameTestHelper h) {
        var f = fixture(h); var original = armor(EquipmentSlot.CHEST); original.set(DataComponents.REPAIR_COST,31);
        var enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY); enchants.set(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING),1);
        original.set(DataComponents.ENCHANTMENTS,enchants.toImmutable()); f.bench.setItem(0,original.copy());
        for (int i = 0; i < 6; i++) h.assertTrue(f.menu.clickMenuButton(f.player,1),"All six armor variants reachable");
        h.assertTrue(ItemStack.matches(f.bench.getItem(0),original),"Full cycle preserves every component including armor toughness and enchants");
        h.assertValueEqual(original.getDamageValue(),517,"Input snapshot is not mutated"); h.succeed();
    }
    private static ItemStack patterned(GameTestHelper h) {
        var stack = new ItemStack(Items.BANNER.pick(DyeColor.BLACK),8);
        var lookup = h.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        var patterns = new BannerPatternLayers.Builder().add(lookup.getOrThrow(BannerPatterns.CROSS),DyeColor.YELLOW).add(lookup.getOrThrow(BannerPatterns.BORDER),DyeColor.LIME).build();
        stack.set(DataComponents.BANNER_PATTERNS,patterns); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Unit flag"));
        var tag = new CompoundTag(); tag.putString("owner_note","original pattern"); stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag)); return stack;
    }
    private static void banner(GameTestHelper h) {
        var f = fixture(h); var initial = patterned(h); f.bench.setItem(0,initial.copy()); f.menu.clickMenuButton(f.player,1); var result = f.bench.getItem(0);
        h.assertTrue(result.is(Items.BANNER.pick(DyeColor.RED)),"Legacy dye metadata 0 to 1 is black to red");
        h.assertValueEqual(result.get(DataComponents.BANNER_PATTERNS),initial.get(DataComponents.BANNER_PATTERNS),"Pattern shapes, order and colors retained");
        h.assertValueEqual(result.get(DataComponents.CUSTOM_DATA),initial.get(DataComponents.CUSTOM_DATA),"Custom data retained");
        h.assertValueEqual(((BannerItem)result.getItem()).getColor(),DyeColor.RED,"Native renderer receives the new base color");
        f.menu.clickMenuButton(f.player,2); h.assertTrue(ItemStack.matches(f.bench.getItem(0),initial),"Reverse cycle restores complete patterned banner"); h.succeed();
    }
    private static void bannerPlacement(GameTestHelper h) {
        var f = fixture(h); var initial = patterned(h); f.bench.setItem(0,initial); f.menu.clickMenuButton(f.player,1);
        var flag = f.bench.getItem(0).copyWithCount(1); var floor = new BlockPos(7,1,7); h.setBlock(floor,Blocks.STONE);
        var absolute = h.absolutePos(floor); f.player.setItemInHand(InteractionHand.MAIN_HAND,flag);
        flag.getItem().useOn(new UseOnContext(f.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(absolute).add(0,.5,0),Direction.UP,absolute,false)));
        var placed = h.getBlockEntity(floor.above(),BannerBlockEntity.class);
        h.assertValueEqual(placed.getBaseColor(),DyeColor.RED,"Placed banner has cycled base color");
        h.assertValueEqual(placed.getPatterns(),initial.get(DataComponents.BANNER_PATTERNS),"Actual block entity receives preserved patterns"); h.succeed();
    }
    private static void unsupported(GameTestHelper h) {
        var f = fixture(h);
        for (var stack : List.of(ItemStack.EMPTY,new ItemStack(Items.STONE,64),new ItemStack(Items.BED.pick(DyeColor.WHITE)),new ItemStack(Items.GLASS),new ItemStack(Items.TERRACOTTA),TGContent.REVOLVER.toStack())) {
            f.bench.setItem(0,stack.copy()); h.assertTrue(!f.menu.clickMenuButton(f.player,1) && !f.menu.clickMenuButton(f.player,2),"Unsupported item stays untouched");
            h.assertTrue(ItemStack.matches(stack,f.bench.getItem(0)),"No deletion or invented color family");
        }
        for (int button : new int[]{-1,11,12,14,255,Integer.MAX_VALUE}) h.assertTrue(!f.menu.clickMenuButton(f.player,button),"Unported targets and invalid buttons rejected"); h.succeed();
    }
    private static void swap(GameTestHelper h) {
        var f = fixture(h); var old = new ItemStack(Items.WOOL.pick(DyeColor.WHITE),12); f.bench.setItem(0,old);
        f.bench.setItem(0,new ItemStack(Items.BANNER.pick(DyeColor.BLACK),3)); h.assertTrue(f.menu.clickMenuButton(f.player,1),"Current item determines palette");
        h.assertTrue(f.bench.getItem(0).is(Items.BANNER.pick(DyeColor.RED)) && f.bench.getItem(0).getCount() == 3,"No stale wool item recreated");
        h.assertTrue(old.is(Items.WOOL.pick(DyeColor.WHITE)) && old.getCount() == 12,"Removed stack untouched"); h.succeed();
    }
    private static void viewers(GameTestHelper h) {
        var f = fixture(h); var other = other(h); var menu = new CamoBenchMenu(62,other.getInventory(),f.bench); other.containerMenu = menu;
        f.bench.setItem(0,new ItemStack(Items.WOOL.pick(DyeColor.WHITE),9)); f.menu.clickMenuButton(f.player,1); menu.clickMenuButton(other,1);
        h.assertTrue(f.bench.getItem(0).is(Items.WOOL.pick(DyeColor.MAGENTA)) && f.bench.getItem(0).getCount() == 9,"Both viewers act on current server stack");
        f.player.setItemSlot(EquipmentSlot.HEAD,armor(EquipmentSlot.HEAD)); other.setItemSlot(EquipmentSlot.HEAD,armor(EquipmentSlot.HEAD)); menu.clickMenuButton(other,3);
        h.assertValueEqual(TGArmorItem.camo(other.getItemBySlot(EquipmentSlot.HEAD)),0,"Viewer changes own worn armor");
        h.assertValueEqual(TGArmorItem.camo(f.player.getItemBySlot(EquipmentSlot.HEAD)),5,"Owner armor remains independent"); h.succeed();
    }
    private static void access(GameTestHelper h) {
        var f = fixture(h); var other = other(h); var menu = new CamoBenchMenu(62,other.getInventory(),f.bench); other.containerMenu = menu;
        f.bench.setItem(0,new ItemStack(Items.WOOL.pick(DyeColor.WHITE),9)); h.assertTrue(!menu.clickMenuButton(other,0),"Only owner changes access"); f.menu.clickMenuButton(f.player,0);
        for (int button = 0; button <= 10; button++) h.assertTrue(!menu.clickMenuButton(other,button),"All actions recheck permissions");
        menu.clicked(0,0,ContainerInput.PICKUP,other);
        h.assertTrue(menu.getCarried().isEmpty() && menu.quickMoveStack(other,0).isEmpty(),"Revoked viewer cannot take items");
        h.assertTrue(f.menu.clickMenuButton(f.player,1),"Owner continues editing"); h.succeed();
    }
    private static void closed(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(0,new ItemStack(Items.WOOL.pick(DyeColor.WHITE))); f.player.containerMenu = f.player.inventoryMenu;
        h.assertTrue(!f.menu.clickMenuButton(f.player,1) && f.menu.quickMoveStack(f.player,0).isEmpty(),"Closed menu rejects requests");
        h.setBlock(POS.east(2),CamoBenchContent.BLOCK.get()); var second = h.getBlockEntity(POS.east(2),CamoBenchBlockEntity.class);
        var menu = new CamoBenchMenu(62,f.player.getInventory(),second); f.player.containerMenu = menu;
        h.assertTrue(!f.bench.button(f.player,1),"Another workbench's open menu cannot operate the old bench"); h.succeed();
    }
    private static void distance(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(0,new ItemStack(Items.WOOL.pick(DyeColor.WHITE))); var position = f.player.position(); f.player.setPos(position.add(100,0,0));
        for (int button = 0; button <= 10; button++) h.assertTrue(!f.menu.clickMenuButton(f.player,button),"Distance checked for all actions");
        f.player.setPos(position); h.getLevel().destroyBlock(f.bench.getBlockPos(),false);
        h.assertTrue(!f.menu.clickMenuButton(f.player,1) && !f.menu.stillValid(f.player),"Removed bench invalidates menu"); h.succeed();
    }
    private static void spectator(GameTestHelper h) {
        var f = fixture(h); var player = h.makeMockPlayer(GameType.SPECTATOR); player.setPos(f.player.position());
        var menu = new CamoBenchMenu(62,player.getInventory(),f.bench); player.containerMenu = menu; f.bench.setItem(0,new ItemStack(Items.WOOL.pick(DyeColor.WHITE)));
        h.assertTrue(!menu.clickMenuButton(player,1) && menu.quickMoveStack(player,0).isEmpty(),"Spectators cannot recolor or remove items"); h.succeed();
    }
    private static CamoBenchBlockEntity reload(GameTestHelper h,CamoBenchBlockEntity old) {
        var restored = (CamoBenchBlockEntity)BlockEntity.loadStatic(old.getBlockPos(),old.getBlockState(),old.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.getLevel().removeBlockEntity(old.getBlockPos()); h.getLevel().setBlockEntity(restored); return restored;
    }
    private static void saved(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(0,patterned(h)); f.menu.clickMenuButton(f.player,1); f.menu.clickMenuButton(f.player,0); var expected = f.bench.getItem(0).copy();
        var restored = reload(h,f.bench);
        h.assertTrue(ItemStack.matches(restored.getItem(0),expected) && restored.isOwner(f.player) && !restored.canOpen(other(h)),"Item, patterns and owner access persist");
        var menu = new CamoBenchMenu(63,f.player.getInventory(),restored); f.player.containerMenu = menu; restored.setItem(0,armor(EquipmentSlot.CHEST)); menu.clickMenuButton(f.player,1);
        var armor = restored.getItem(0).copy(); restored = reload(h,restored);
        h.assertTrue(ItemStack.matches(restored.getItem(0),armor),"Cycled armor equipment asset and damage survive reload"); h.succeed();
    }
    private static void sync(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(0,patterned(h)); f.player.setItemSlot(EquipmentSlot.HEAD,armor(EquipmentSlot.HEAD));
        f.menu.clickMenuButton(f.player,1); f.menu.clickMenuButton(f.player,3); f.menu.clickMenuButton(f.player,0); var client = new CamoBenchMenu(61,other(h).getInventory());
        for (int slot : new int[]{0,37}) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try {
                ClientboundContainerSetSlotPacket.STREAM_CODEC.encode(buffer,new ClientboundContainerSetSlotPacket(61,9,slot,f.menu.getSlot(slot).getItem()));
                var packet = ClientboundContainerSetSlotPacket.STREAM_CODEC.decode(buffer); client.setItem(packet.getSlot(),packet.getStateId(),packet.getItem());
            } finally { buffer.release(); }
        }
        for (int key = 0; key < 2; key++) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { ClientboundContainerSetDataPacket.STREAM_CODEC.encode(buffer,new ClientboundContainerSetDataPacket(61,key,f.menu.value(key))); var packet = ClientboundContainerSetDataPacket.STREAM_CODEC.decode(buffer); client.setData(packet.getId(),packet.getValue()); }
            finally { buffer.release(); }
        }
        h.assertTrue(ItemStack.matches(client.target(0),f.menu.target(0)) && ItemStack.matches(client.target(1),f.menu.target(1)),"Packets carry palette item, patterns and worn armor asset");
        h.assertTrue(client.value(0) == 1 && client.value(1) == 1,"Shared access data reaches client menu");
        h.assertValueEqual(client.slots.size(),42,"One input, 36 inventory, four armor, one offhand; no fictitious extra slots"); h.succeed();
    }
    private static void shift(GameTestHelper h) {
        var f = fixture(h); f.player.getInventory().setItem(9,new ItemStack(Items.WOOL.pick(DyeColor.WHITE),32));
        h.assertTrue(!f.menu.quickMoveStack(f.player,1).isEmpty() && f.bench.getItem(0).getCount() == 32,"Shift-click fills an empty bench slot");
        h.assertTrue(!f.menu.quickMoveStack(f.player,0).isEmpty() && f.bench.isEmpty(),"Input returns to player");
        f.player.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.CARPET.pick(DyeColor.BLUE),7));
        h.assertTrue(!f.menu.quickMoveStack(f.player,41).isEmpty() && f.bench.getItem(0).is(Items.CARPET.pick(DyeColor.BLUE)),"Offhand reaches input");
        for (int invalid : new int[]{-1,42,1000}) h.assertTrue(f.menu.quickMoveStack(f.player,invalid).isEmpty(),"Invalid slots rejected"); h.succeed();
    }
    private static void merge(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(0,new ItemStack(Items.WOOL.pick(DyeColor.WHITE),60)); f.player.getInventory().setItem(0,new ItemStack(Items.WOOL.pick(DyeColor.WHITE),10));
        h.assertTrue(!f.menu.quickMoveStack(f.player,28).isEmpty(),"Compatible stack merges");
        h.assertValueEqual(f.bench.getItem(0).getCount(),64,"Native limit respected"); h.assertValueEqual(f.player.getInventory().getItem(0).getCount(),6,"Excess stays in hotbar");
        f.player.getInventory().setItem(8,new ItemStack(Items.WOOL.pick(DyeColor.BLUE),8));
        h.assertTrue(f.menu.quickMoveStack(f.player,36).isEmpty() && f.player.getInventory().getItem(8).getCount() == 8,"Full or different input leaves stack untouched"); h.succeed();
    }
    private static void armorShift(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(0,armor(EquipmentSlot.HEAD));
        h.assertTrue(!f.menu.quickMoveStack(f.player,0).isEmpty() && !f.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"Input armor equips in its native slot");
        f.bench.setItem(0,armor(EquipmentSlot.HEAD)); h.assertTrue(!f.menu.quickMoveStack(f.player,0).isEmpty() && f.bench.isEmpty(),"Occupied armor slot falls back to ordinary inventory");
        h.assertTrue(!f.menu.quickMoveStack(f.player,37).isEmpty() && f.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"Worn armor can move into input"); h.succeed();
    }
    private static void binding(GameTestHelper h) {
        var f = fixture(h); var armor = armor(EquipmentSlot.HEAD); var enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchants.set(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE),1); armor.set(DataComponents.ENCHANTMENTS,enchants.toImmutable()); f.player.setItemSlot(EquipmentSlot.HEAD,armor);
        h.assertTrue(f.menu.quickMoveStack(f.player,37).isEmpty(),"Binding prevents moving worn item into input");
        h.assertTrue(f.menu.clickMenuButton(f.player,3) && TGArmorItem.camo(f.player.getItemBySlot(EquipmentSlot.HEAD)) == 0,"Bound worn item can change camouflage without removal");
        h.assertValueEqual(f.player.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.ENCHANTMENTS),armor.get(DataComponents.ENCHANTMENTS),"Curse is retained"); h.succeed();
    }
    private static void fullInventory(GameTestHelper h) {
        var f = fixture(h); for (int i = 0; i < 36; i++) f.player.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        f.bench.setItem(0,new ItemStack(Items.WOOL.pick(DyeColor.WHITE),12));
        h.assertTrue(f.menu.quickMoveStack(f.player,0).isEmpty() && f.bench.getItem(0).getCount() == 12,"Full inventory cannot delete input"); h.succeed();
    }
    private static void ports(GameTestHelper h) {
        var f = fixture(h); var sides = new ArrayList<Direction>(List.of(Direction.values())); sides.add(null);
        for (var side : sides) {
            var handler = h.getLevel().getCapability(Capabilities.Item.BLOCK,f.bench.getBlockPos(),side);
            h.assertTrue(handler != null && handler.size() == 1,"One item port from all faces and unsided");
            h.assertTrue(h.getLevel().getCapability(Capabilities.Energy.BLOCK,f.bench.getBlockPos(),side) == null && h.getLevel().getCapability(Capabilities.Fluid.BLOCK,f.bench.getBlockPos(),side) == null,"No invented energy or fluid ports");
            try (var tx = Transaction.openRoot()) {
                h.assertValueEqual(handler.insert(0,ItemResource.of(Items.WOOL.pick(DyeColor.WHITE)),80,tx),64,"Bulk palette items accepted up to native stack limit");
                h.assertValueEqual(handler.extract(0,ItemResource.of(Items.WOOL.pick(DyeColor.WHITE)),4,tx),4,"Items can be withdrawn");
            }
            h.assertTrue(f.bench.isEmpty(),"Aborted transfers restore input");
        }
        try (var tx = Transaction.openRoot()) { h.assertValueEqual(f.bench.automation().insert(0,ItemResource.of(Items.STONE),3,tx),3,"Original unrestricted storage accepts non-palette items"); tx.commit(); }
        h.assertTrue(!f.menu.clickMenuButton(f.player,1) && f.bench.getItem(0).getCount() == 3,"Unsupported storage is not a recoloring recipe"); h.succeed();
    }
    private static void hopper(GameTestHelper h) {
        var f = fixture(h); h.setBlock(POS.above(),Blocks.HOPPER); var input = h.getBlockEntity(POS.above(),HopperBlockEntity.class); input.setItem(0,new ItemStack(Items.WOOL.pick(DyeColor.GRAY),3));
        h.runAfterDelay(30,() -> { h.assertValueEqual(f.bench.getItem(0).getCount(),3,"Real hopper feeds full batch"); h.assertTrue(f.menu.clickMenuButton(f.player,1),"Supplied stack recolors"); h.setBlock(POS.below(),Blocks.HOPPER); });
        h.runAfterDelay(70,() -> { var output = h.getBlockEntity(POS.below(),HopperBlockEntity.class); h.assertTrue(output.getItem(0).is(Items.WOOL.pick(DyeColor.LIGHT_GRAY)) && output.getItem(0).getCount() == 3,"Real hopper extracts recolored batch"); h.assertTrue(f.bench.isEmpty() && input.isEmpty(),"No duplicate input or output"); h.succeed(); });
    }
    private static void recipe(GameTestHelper h) {
        var f = fixture(h); var nugget = new ItemStack(Items.IRON_NUGGET);
        var grid = CraftingInput.of(3,3,List.of(new ItemStack(Items.DYE.pick(DyeColor.RED)),new ItemStack(Items.DYE.pick(DyeColor.BLUE)),new ItemStack(Items.DYE.pick(DyeColor.WHITE)),nugget,new ItemStack(Items.CRAFTING_TABLE),nugget,nugget,nugget,nugget));
        var result = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,h.getLevel()).orElseThrow().value().assemble(grid);
        h.assertTrue(result.is(CamoBenchContent.ITEM.get()) && result.getCount() == 1,"Original metadata 8 recipe accepts mixed dye colors");
        for (var facing : Direction.Plane.HORIZONTAL) { var state = CamoBenchContent.BLOCK.get().defaultBlockState().setValue(CamoBenchBlock.FACING,facing); h.setBlock(POS,state); h.assertTrue(state.canOcclude(),"Original opaque cube"); h.assertValueEqual(state.getCollisionShape(h.getLevel(),f.bench.getBlockPos()).bounds(),new AABB(0,0,0,1,1,1),"Original cube collision"); h.assertValueEqual(state.rotate(Rotation.CLOCKWISE_90).getValue(CamoBenchBlock.FACING),facing.getClockWise(),"Shared block rotation"); }
        h.succeed();
    }
    private static void breaking(GameTestHelper h) {
        var f = fixture(h); f.bench.setItem(0,patterned(h)); f.menu.clickMenuButton(f.player,1); var expected = f.bench.getItem(0).copy(); h.getLevel().destroyBlock(f.bench.getBlockPos(),true);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(f.bench.getBlockPos()).inflate(2)).stream().map(ItemEntity::getItem).toList();
        h.assertValueEqual(drops.stream().filter(s -> s.is(CamoBenchContent.ITEM.get())).mapToInt(ItemStack::getCount).sum(),1,"Bench drops once");
        var flags = drops.stream().filter(s -> s.is(Items.BANNER.pick(DyeColor.RED))).toList();
        h.assertTrue(flags.size() == 1 && ItemStack.matches(flags.getFirst(),expected),"Recolored patterned stack drops unchanged");
        try (var tx = Transaction.openRoot()) { h.assertValueEqual(f.bench.automation().extract(0,ItemResource.of(expected),8,tx),0,"Stale handler cannot duplicate dropped stack"); }
        h.succeed();
    }
    private static void claim(GameTestHelper h) {
        h.setBlock(POS,CamoBenchContent.BLOCK.get()); var bench = h.getBlockEntity(POS,CamoBenchBlockEntity.class); var player = WeaponGameTests.player(h);
        var menu = bench.createMenu(65,player.getInventory(),player); player.containerMenu = menu;
        h.assertTrue(bench.isOwner(player) && menu.clickMenuButton(player,0),"First ordinary opener claims unowned Camo Bench"); h.succeed();
    }
    private static void repairSchema(GameTestHelper h) {
        var f = fixture(h); h.setBlock(POS.east(2),RepairBenchContent.BLOCK.get()); var old = h.getBlockEntity(POS.east(2),RepairBenchBlockEntity.class); old.setOwner(f.player);
        old.setItem(9,armor(EquipmentSlot.CHEST)); old.setItem(0,TGContent.MATERIALS.get("ingotobsidiansteel").toStack(8)); old.setItem(1,TGContent.MATERIALS.get("heavycloth").toStack(8));
        var menu = new RepairBenchMenu(66,f.player.getInventory(),old); f.player.containerMenu = menu; menu.clickMenuButton(f.player,0);
        var tag = old.saveWithFullMetadata(h.getLevel().registryAccess()); h.assertTrue(tag.contains("Items") && tag.contains("owner") && tag.contains("owner_only"),"Shared base retains existing save keys");
        var restored = (RepairBenchBlockEntity)BlockEntity.loadStatic(old.getBlockPos(),old.getBlockState(),tag,h.getLevel().registryAccess()); h.getLevel().removeBlockEntity(old.getBlockPos()); h.getLevel().setBlockEntity(restored);
        menu = new RepairBenchMenu(67,f.player.getInventory(),restored); f.player.containerMenu = menu;
        h.assertTrue(restored.getContainerSize() == 10 && restored.ownerOnly() && menu.clickMenuButton(f.player,6),"Shared refactor preserves ten-slot repair behavior and owner");
        h.assertValueEqual(restored.getItem(9).getDamageValue(),0,"Repair still restores condition"); h.succeed();
    }
}
