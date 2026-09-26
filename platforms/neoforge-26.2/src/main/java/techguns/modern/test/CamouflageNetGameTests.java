package techguns.modern.test;

import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.machine.camo.*;
import techguns.modern.world.*;

final class CamouflageNetGameTests {
    private static final BlockPos POS = new BlockPos(4, 3, 4);
    // Independent copy of the legacy table: catches wrong bit order or shape lookup at runtime.
    private static final int[][] TOP = {
        {4,4,12,12}, {0,4,9,12}, {4,7,12,16}, {0,7,9,16},
        {7,4,16,12}, {0,4,16,12}, {7,7,16,16}, {0,7,16,16},
        {4,0,12,9}, {0,0,9,9}, {4,0,12,16}, {0,0,9,16},
        {7,0,16,9}, {0,0,16,9}, {7,0,16,16}, {0,0,16,16}
    };
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for (var v : CamouflageNets.ALL) {
            for (int mask = 0; mask < 16; mask++) { int m = mask;
                r.register("camonet_connections_" + v.id() + "_" + mask, () -> h -> shape(h, v, m));
            }
            r.register("camonet_place_and_mine_" + v.id(), () -> h -> placement(h, v));
            r.register("camonet_other_neighbors_" + v.id(), () -> h -> neighbors(h, v));
        }
        for (var palette : CamouflageNets.PALETTES) r.register("camonet_bench_" + palette.id(), () -> h -> camo(h, palette));
        r.register("camonet_original_recipes", () -> CamouflageNetGameTests::recipes);
        r.register("camonet_reject_wrong_recipe_ingredients", () -> CamouflageNetGameTests::invalidRecipes);
        r.register("camonet_structure_reconciliation", () -> CamouflageNetGameTests::structure);
    }
    private static CamouflageNetBlock block(String id) { return CamouflageNetContent.BLOCKS.get(id).get(); }
    private static void put(GameTestHelper h, BlockPos pos, BlockState state) { h.getLevel().setBlock(pos, state, 3); }
    private static void clear(GameTestHelper h, BlockPos pos) { put(h, pos, Blocks.AIR.defaultBlockState()); }
    private static void near(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < 1e-7, message + ": " + actual + " vs " + expected);
    }
    private static int mask(BlockState state) {
        int result = 0;
        for (int i = 0; i < 4; i++) if (state.getValue(CamouflageNetBlock.SIDES.get(i))) result |= 8 >> i;
        return result;
    }
    private static void shape(GameTestHelper h, CamouflageNets.Variant v, int mask) {
        var p = h.absolutePos(POS); var l = h.getLevel(); var b = block(v.id());
        for (int i = 0; i < 4; i++) {
            var other = block(v.family() + "_" + List.of("desert", "snow", "wood", "desert").get(i));
            put(h, p.relative(CamouflageNetBlock.DIRECTIONS.get(i)), (mask & (8 >> i)) != 0 ? other.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        put(h, p, b.defaultBlockState()); var state = l.getBlockState(p);
        h.assertValueEqual(mask(state), mask, "Neighbors from any camouflage connect in original N8 E4 S2 W1 order");
        var collision = state.getCollisionShape(l, p); var outline = state.getShape(l, p);
        AABB bounds;
        if (v.canopy()) {
            int[] box = TOP[mask]; bounds = new AABB(box[0]/16d, 0, box[1]/16d, box[2]/16d, 1/16d, box[3]/16d);
            h.assertValueEqual(collision.toAabbs(), List.of(bounds), "Canopy collision uses the original thin bounding box");
        } else {
            bounds = new AABB((mask & 1) != 0 ? 0 : 7/16d, 0, (mask & 8) != 0 ? 0 : 7/16d,
                    (mask & 4) != 0 ? 1 : 9/16d, 1, (mask & 2) != 0 ? 1 : 9/16d);
            double volume = collision.toAabbs().stream().mapToDouble(a -> a.getXsize()*a.getYsize()*a.getZsize()).sum();
            near(h, volume, (4 + 14 * Integer.bitCount(mask))/256d, "Pole and arms do not fill corners");
            for (int x : new int[]{1, 14}) for (int z : new int[]{1, 14}) {
                var corner = Block.box(x, 1, z, x+1, 15, z+1);
                h.assertTrue(!Shapes.joinIsNotEmpty(collision, corner, BooleanOp.AND), "Corner remains passable");
            }
        }
        h.assertValueEqual(outline.toAabbs(), List.of(bounds), "Selection is one original enclosing box");
        h.assertValueEqual(collision.bounds(), bounds, "Collision bounds agree with selection");
        for (var rotation : Rotation.values()) for (int i = 0; i < 4; i++) {
            var direction = rotation.rotate(CamouflageNetBlock.DIRECTIONS.get(i));
            h.assertValueEqual(state.rotate(rotation).getValue(CamouflageNetBlock.SIDES.get(CamouflageNetBlock.DIRECTIONS.indexOf(direction))),
                    (mask & (8 >> i)) != 0, "Structure rotation carries connections");
        }
        for (var mirror : Mirror.values()) for (int i = 0; i < 4; i++) {
            var direction = mirror.mirror(CamouflageNetBlock.DIRECTIONS.get(i));
            h.assertValueEqual(state.mirror(mirror).getValue(CamouflageNetBlock.SIDES.get(CamouflageNetBlock.DIRECTIONS.indexOf(direction))),
                    (mask & (8 >> i)) != 0, "Structure mirror carries connections");
        }
        h.assertValueEqual(BlockState.CODEC.parse(JsonOps.INSTANCE, BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow(), state, "World save preserves block and sides");
        for (int i = 0; i < 4; i++) clear(h, p.relative(CamouflageNetBlock.DIRECTIONS.get(i)));
        h.assertValueEqual(mask(l.getBlockState(p)), 0, "Removing neighbors clears all render connections");
        h.succeed();
    }
    private static void placement(GameTestHelper h, CamouflageNets.Variant v) {
        var p = h.absolutePos(POS); var l = h.getLevel(); var b = block(v.id()); var floor = p.below();
        put(h, floor, Blocks.STONE.defaultBlockState());
        put(h, p.north(), block(v.family() + "_snow").defaultBlockState());
        var player = WeaponGameTests.player(h); var stack = new ItemStack(b, 2); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false);
        h.assertTrue(stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "Native BlockItem placement");
        h.assertValueEqual(stack.getCount(), 1, "One net consumed");
        var state = l.getBlockState(p); h.assertTrue(state.is(b), "Correct family and color placed");
        h.assertValueEqual(mask(state), 8, "Placement computes existing neighbor");
        h.assertTrue(l.getBlockState(p.north()).getValue(CamouflageNetBlock.SIDES.get(2)), "Existing neighbor also updates");
        clear(h, floor); clear(h, p.north());
        h.assertTrue(l.getBlockState(p).is(b) && state.canSurvive(l, p), "Original net needs no support");
        near(h, state.getDestroySpeed(l, p), 2, "Original hardness"); near(h, b.getExplosionResistance(), 2, "Original effective resistance");
        h.assertValueEqual(state.getSoundType(), SoundType.WOOL, "Original cloth sound");
        h.assertTrue(!state.isSolidRender() && !state.isCollisionShapeFullBlock(l, p), "Open net never becomes opaque/full cube");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(player.hasCorrectToolForDrops(state), "Hand mining retains source drops");
        h.assertTrue(l.destroyBlock(p, true, player), "Net destroyed");
        var drops = l.getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(2), e -> e.getItem().is(b.asItem()));
        h.assertValueEqual(drops.stream().mapToInt(e -> e.getItem().getCount()).sum(), 1, "Exactly one matching color drops");
        h.succeed();
    }
    private static void neighbors(GameTestHelper h, CamouflageNets.Variant v) {
        var p = h.absolutePos(POS); var l = h.getLevel(); var b = block(v.id()); put(h, p, b.defaultBlockState());
        var other = block((v.canopy() ? "camonet" : "camonet_top") + "_wood");
        for (var neighbor : List.of(Blocks.STONE, Blocks.OAK_FENCE, Blocks.NETHER_BRICK_FENCE, Blocks.COBBLESTONE_WALL,
                Blocks.OAK_FENCE_GATE, FortificationContent.SANDBAGS.get(), other, Blocks.IRON_BARS, Blocks.GLASS_PANE, Blocks.STAINED_GLASS_PANE.red())) {
            put(h, p.north(), neighbor.defaultBlockState());
            h.assertValueEqual(mask(l.getBlockState(p)), 0, "No net arm to " + neighbor);
            var actual = Block.updateFromNeighbourShapes(l.getBlockState(p.north()), l, p.north()); put(h, p.north(), actual);
            if (neighbor instanceof IronBarsBlock) h.assertValueEqual(actual.getValue(IronBarsBlock.SOUTH), !v.canopy(), "Source pane extends only to vertical net");
            if (neighbor instanceof FenceBlock) h.assertTrue(!actual.getValue(FenceBlock.SOUTH), "Source fences never attach to a thin cloth pole");
        }
        clear(h, p.north()); put(h, p.above(), b.defaultBlockState()); put(h, p.below(), b.defaultBlockState());
        h.assertValueEqual(mask(l.getBlockState(p)), 0, "No vertical connection bits");
        for (var face : Direction.values()) h.assertTrue(!l.getBlockState(p).isFaceSturdy(l, p, face), "No full support face");
        h.succeed();
    }
    private static void camo(GameTestHelper h, CamoPalette palette) {
        var pos = new BlockPos(4, 2, 4); h.setBlock(pos, CamoBenchContent.BLOCK.get());
        var bench = h.getBlockEntity(pos, CamoBenchBlockEntity.class); var p = WeaponGameTests.player(h); bench.setOwner(p);
        var menu = new CamoBenchMenu(57, p.getInventory(), bench); p.containerMenu = menu; p.experienceLevel = 9;
        var original = new ItemStack(block(palette.id() + "_wood"), 64); original.set(DataComponents.CUSTOM_NAME, Component.literal("Concealment supply"));
        var data = new CompoundTag(); data.putString("source", "supply crate"); original.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); bench.setItem(0, original.copy());
        for (int button : new int[]{1, 2}) for (int step = 1; step <= 3; step++) {
            h.assertTrue(menu.clickMenuButton(p, button), "Authorized server Camo Bench action");
            int index = Math.floorMod(button == 1 ? step : -step, 3);
            var actual = bench.getItem(0); var expected = block(palette.id() + "_" + List.of("wood", "desert", "snow").get(index));
            h.assertTrue(actual.is(expected.asItem()), "Original enum order and reverse wrap");
            h.assertValueEqual(actual.getCount(), 64, "Whole stack survives");
            h.assertValueEqual(actual.get(DataComponents.CUSTOM_NAME), original.get(DataComponents.CUSTOM_NAME), "Custom name retained");
            h.assertValueEqual(actual.get(DataComponents.CUSTOM_DATA), original.get(DataComponents.CUSTOM_DATA), "Custom data retained");
            h.assertValueEqual(actual.get(DataComponents.ITEM_MODEL), new ItemStack(expected).get(DataComponents.ITEM_MODEL), "New inventory model default");
            h.assertValueEqual(CamoCycling.count(actual), 3, "All three variants exposed"); h.assertValueEqual(CamoCycling.index(actual), index, "Matching camo index");
            h.assertValueEqual(CamoCycling.variantName(actual), Component.translatable("block.techguns." + palette.items().get(index).split(":")[1]), "Original translated name");
        }
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var saved = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, bench.getItem(0)).getOrThrow()).getOrThrow();
        h.assertTrue(ItemStack.matches(saved, original), "Both palette cycles and save roundtrip preserve exact stack");
        h.assertValueEqual(p.experienceLevel, 9, "No XP, dye or power cost"); h.succeed();
    }
    private static List<ItemStack> grid(boolean canopy, Item dirt) {
        var s = new ItemStack(Items.STICK); var d = new ItemStack(dirt);
        return new ArrayList<>(canopy ? List.of(s,d,s,d,new ItemStack(Items.STRING),d,s,d,s) : List.of(s,d,s,s,d,s,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY));
    }
    private static ItemStack craft(GameTestHelper h, List<ItemStack> grid) {
        var input = CraftingInput.of(3, 3, grid);
        return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
    }
    private static void recipes(GameTestHelper h) {
        for (boolean canopy : new boolean[]{false, true}) {
            var result = craft(h, grid(canopy, Items.DIRT));
            h.assertTrue(result.is(block(canopy ? "camonet_top_wood" : "camonet_wood").asItem()), "Original shaped recipe loaded");
            h.assertValueEqual(result.getCount(), canopy ? 16 : 8, "Original output amount");
        }
        h.succeed();
    }
    private static void invalidRecipes(GameTestHelper h) {
        for (boolean canopy : new boolean[]{false, true}) {
            for (var dirt : List.of(Items.COARSE_DIRT, Items.PODZOL, Items.GRASS_BLOCK, Items.MUD, Items.ROOTED_DIRT, Items.SAND))
                h.assertTrue(craft(h, grid(canopy, dirt)).isEmpty(), "Source dirt ore tag does not accept " + dirt);
            var wrong = grid(canopy, Items.DIRT); wrong.set(0, new ItemStack(Items.BLAZE_ROD));
            h.assertTrue(craft(h, wrong).isEmpty(), "Wooden sticks required");
            wrong = grid(canopy, Items.DIRT); wrong.set(4, ItemStack.EMPTY);
            h.assertTrue(craft(h, wrong).isEmpty(), "No omitted stick/string");
        }
        h.succeed();
    }
    private static void structure(GameTestHelper h) {
        var p = h.absolutePos(POS); var l = h.getLevel();
        for (String family : List.of("camonet", "camonet_top")) {
            clear(h, p); clear(h, p.east());
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
            l.setBlock(p, block(family + "_wood").defaultBlockState(), flags);
            l.setBlock(p.east(), block(family + "_snow").defaultBlockState(), flags);
            h.assertValueEqual(mask(l.getBlockState(p)), 0, "Fixture suppresses shape callbacks as structure placement does");
            h.assertTrue(l.getBlockState(p).getCollisionShape(l, p).bounds().maxX == 1, "Live collision already sees neighbor");
            for (var pos : List.of(p, p.east())) l.setBlock(pos, Block.updateFromNeighbourShapes(l.getBlockState(pos), l, pos), flags);
            h.assertValueEqual(mask(l.getBlockState(p)), 4, "Final reconciliation restores east render arm");
            h.assertValueEqual(mask(l.getBlockState(p.east())), 1, "Final reconciliation restores west render arm");
        }
        h.succeed();
    }
}
