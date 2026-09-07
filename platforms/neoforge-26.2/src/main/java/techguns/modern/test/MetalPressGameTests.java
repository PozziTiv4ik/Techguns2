package techguns.modern.test;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.TGContent;
import techguns.modern.machine.MetalPressBlockEntity;
import techguns.modern.machine.MetalPressMenu;
import techguns.modern.machine.MetalPressRecipe;
import techguns.modern.machine.TGMachineContent;

final class MetalPressGameTests {
    private static final BlockPos POS = new BlockPos(4, 2, 4);
    private record Case(String first, String second, String output, int count) {}
    // Independent expectations transcribed from TGMachineRecipes, not from generated recipes.
    private static final List<Case> CASES = List.of(
            new Case("ingottin","ingottin","platetin",2), new Case("minecraft:copper_ingot","minecraft:copper_ingot","platecopper",2),
            new Case("ingotbronze","ingotbronze","platebronze",2), new Case("minecraft:iron_ingot","minecraft:iron_ingot","plateiron",2),
            new Case("ingotsteel","ingotsteel","platesteel",2), new Case("ingotlead","ingotlead","platelead",2),
            new Case("plateiron","minecraft:flint","mechanicalpartsiron",1), new Case("plateobsidiansteel","minecraft:quartz","mechanicalpartsobsidiansteel",1),
            new Case("platecarbon","minecraft:blaze_rod","mechanicalpartscarbon",2), new Case("platecopper","platecopper","copperwire",8),
            new Case("carbonfibers","carbonfibers","platecarbon",2), new Case("plateobsidiansteel","tgx","advancedrounds",16),
            new Case("ingotobsidiansteel","ingotobsidiansteel","plateobsidiansteel",2), new Case("platesteel","platebronze","steamarmorplate",1),
            new Case("minecraft:gold_ingot","minecraft:gold_ingot","goldwire",2), new Case("plateiron","minecraft:tnt","40mmgrenade",16),
            new Case("ingottitanium","ingottitanium","platetitanium",2), new Case("plateobsidiansteel","platetitanium","gaussrifleslugs",4),
            new Case("sniperrounds_incendiary","tgx","sniperrounds_explosive",1));

    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        for (Case expected : CASES) registry.register("metal_recipe_"+expected.output(), () -> h -> production(h, expected));
        registry.register("metal_autosplit_left", () -> h -> autoSplit(h, 0));
        registry.register("metal_autosplit_right", () -> h -> autoSplit(h, 1));
        registry.register("metal_autosplit_pair_validation", () -> MetalPressGameTests::pairValidation);
        registry.register("metal_batch_quadratic_reload", () -> MetalPressGameTests::batchAndReload);
        registry.register("metal_break_refunds_inputs", () -> MetalPressGameTests::breakMachine);
        registry.register("metal_hopper_production_chain", () -> MetalPressGameTests::chain);
        registry.register("metal_menu_and_transfer", () -> MetalPressGameTests::menu);
        registry.register("metal_recipe_codec_and_order", () -> MetalPressGameTests::codec);
        registry.register("metal_crafting_variants", () -> MetalPressGameTests::crafting);
    }
    private static ItemStack item(String id) {
        Identifier key = id.contains(":") ? Identifier.parse(id) : TGContent.id(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new AssertionError("Missing recipe item: " + id);
        return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
    }
    private static MetalPressBlockEntity place(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, TGMachineContent.METAL_PRESS.get());
        return get(h, pos);
    }
    private static MetalPressBlockEntity get(GameTestHelper h, BlockPos pos) {
        return (MetalPressBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }
    private static void charge(GameTestHelper h, BlockPos pos, int amount) {
        var energy = h.getLevel().getCapability(Capabilities.Energy.BLOCK, h.absolutePos(pos), Direction.UP);
        h.assertTrue(energy != null, "Metal Press exposes energy capability");
        try (Transaction tx = Transaction.openRoot()) {
            h.assertValueEqual(energy.insert(amount, tx), amount, "Expected energy transfer"); tx.commit();
        }
    }
    private static void output(GameTestHelper h, MetalPressBlockEntity machine, String item, int count) {
        h.assertTrue(machine.getItem(2).is(item(item).getItem()), "Expected original production result: "+item);
        h.assertValueEqual(machine.getItem(2).getCount(), count, "Expected result quantity");
    }
    private static void production(GameTestHelper h, Case expected) {
        var normal = place(h, POS); var reverse = place(h, POS.east(4));
        normal.setItem(0, item(expected.first())); normal.setItem(1, item(expected.second()));
        reverse.setItem(0, item(expected.second())); reverse.setItem(1, item(expected.first()));
        charge(h, POS, 2000); charge(h, POS.east(4), 2000);
        h.runAfterDelay(5, () -> {
            h.assertTrue(normal.getItem(0).isEmpty() && normal.getItem(1).isEmpty(), "Consume one of each input at start");
            h.assertValueEqual(normal.data.get(2), 100, "Original duration");
            h.assertTrue(normal.getItem(2).isEmpty(), "No premature output");
        });
        h.runAfterDelay(105, () -> {
            for (var machine : List.of(normal, reverse)) {
                output(h, machine, expected.output(), expected.count());
                h.assertValueEqual(machine.energy().getAmountAsLong(), 0L, "One unupgraded cycle costs 2000 FE");
            }
            h.succeed();
        });
    }
    private static void autoSplit(GameTestHelper h, int side) {
        var machine = place(h, POS);
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 5);
        iron.set(DataComponents.CUSTOM_NAME, Component.literal("Batch 27"));
        machine.setItem(side, iron); machine.button(WeaponGameTests.player(h), 0); charge(h, POS, 4000);
        h.runAfterDelay(5, () -> {
            h.assertValueEqual(machine.getItem(side).getCount(), 2, "Odd source retains ceil(5/2) minus one reserved item");
            h.assertValueEqual(machine.getItem(1-side).getCount(), 1, "Target gets floor(5/2) minus one reserved item");
            h.assertTrue(machine.getItem(1-side).getHoverName().equals(iron.getHoverName()), "Splitting preserves stack components");
        });
        h.runAfterDelay(210, () -> {
            output(h, machine, "plateiron", 4);
            h.assertValueEqual(machine.getItem(side).getCount(), 1, "Odd remainder is retained");
            h.assertTrue(machine.getItem(1-side).isEmpty(), "No duplicated second input");
            h.assertValueEqual(machine.energy().getAmountAsLong(), 0L, "Two normal cycles cost 4000 FE"); h.succeed();
        });
    }
    private static void pairValidation(GameTestHelper h) {
        var machine = place(h, POS); machine.setItem(0, item("plateiron").copyWithCount(3));
        machine.button(WeaponGameTests.player(h), 0); charge(h, POS, 2000);
        h.runAfterDelay(5, () -> {
            h.assertTrue(machine.getItem(1).isEmpty() && !machine.working(), "Only a real identical-input recipe may be split");
            h.assertTrue(!machine.canPlaceItem(1, item("minecraft:gold_ingot")), "Reject a known material with an incompatible other input");
            h.assertTrue(machine.canPlaceItem(1, item("minecraft:flint")), "Accept compatible counterpart");
            machine.setItem(1, item("minecraft:flint"));
        });
        h.runAfterDelay(112, () -> { output(h, machine, "mechanicalpartsiron", 1); h.assertValueEqual(machine.getItem(0).getCount(), 2, "Unused plates remain"); h.succeed(); });
    }
    private static void batchAndReload(GameTestHelper h) {
        var machine = place(h, POS);
        machine.setItem(0, new ItemStack(Items.IRON_INGOT, 4)); machine.setItem(1, new ItemStack(Items.IRON_INGOT, 4));
        machine.setItem(3, item("machinestackupgrade").copyWithCount(3)); charge(h, POS, 20000);
        h.runAfterDelay(45, () -> {
            h.assertValueEqual(machine.data.get(6), 4, "Three upgrades process four pairs");
            h.assertValueEqual(machine.data.get(7), 320, "Menu receives the actual working-tick power requirement");
            h.assertValueEqual(machine.energy().getAmountAsLong(), 20000L - machine.data.get(1)*320L, "Original double multiplier: 20 * 4 * 4 FE per tick");
            var saved = machine.saveWithFullMetadata(h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(h.absolutePos(POS));
            var restored = (MetalPressBlockEntity) BlockEntity.loadStatic(h.absolutePos(POS), TGMachineContent.METAL_PRESS.get().defaultBlockState(), saved, h.getLevel().registryAccess());
            h.getLevel().setBlockEntity(restored);
            h.assertValueEqual(restored.data.get(6), 4, "Batch multiplier persists");
        });
        h.runAfterDelay(50, () -> charge(h, POS, 12000));
        h.runAfterDelay(105, () -> {
            var restored = get(h, POS); output(h, restored, "plateiron", 8);
            h.assertValueEqual(restored.energy().getAmountAsLong(), 0L, "Four-pair operation costs exactly 32000 FE after reload");
            h.assertTrue(restored.getItem(0).isEmpty() && restored.getItem(1).isEmpty(), "Reserved inputs are not duplicated"); h.succeed();
        });
    }
    private static void breakMachine(GameTestHelper h) {
        var machine = place(h, POS);
        ItemStack plate = item("plateobsidiansteel"); plate.set(DataComponents.CUSTOM_NAME, Component.literal("Plate 27"));
        machine.setItem(0, plate); machine.setItem(1, item("minecraft:quartz")); charge(h, POS, 2000);
        h.runAfterDelay(20, () -> h.getLevel().destroyBlock(h.absolutePos(POS), true));
        h.runAfterDelay(22, () -> {
            int plates=0, quartz=0, product=0, blocks=0;
            for (var entity : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(POS)).inflate(3))) {
                var stack = entity.getItem();
                if (stack.is(item("plateobsidiansteel").getItem())) { plates += stack.getCount(); h.assertTrue(stack.getHoverName().getString().equals("Plate 27"), "Reserved input components survive refund"); }
                if (stack.is(Items.QUARTZ)) quartz += stack.getCount();
                if (stack.is(item("mechanicalpartsobsidiansteel").getItem())) product += stack.getCount();
                if (stack.is(TGMachineContent.METAL_PRESS_ITEM.get())) blocks += stack.getCount();
            }
            h.assertValueEqual(plates,1,"Consumed plate returns once"); h.assertValueEqual(quartz,1,"Consumed quartz returns once");
            h.assertValueEqual(product,0,"Promised output is not also refunded"); h.assertValueEqual(blocks,1,"Machine loot"); h.succeed();
        });
    }
    private static void chain(GameTestHelper h) {
        var lower = place(h, POS); var upper = place(h, POS.above(2));
        h.setBlock(POS.above(), Blocks.HOPPER);
        upper.setItem(0, new ItemStack(Items.IRON_INGOT, 2)); upper.button(WeaponGameTests.player(h), 0);
        lower.setItem(1, new ItemStack(Items.FLINT)); charge(h, POS, 2000); charge(h, POS.above(2), 2000);
        h.runAfterDelay(250, () -> {
            output(h, lower, "mechanicalpartsiron", 1);
            h.assertTrue(upper.getItem(2).isEmpty(), "Hopper transfers plates out of the first machine");
            h.assertValueEqual(lower.getItem(0).getCount(), 1, "Remaining plate is preserved in the second machine");
            h.assertValueEqual(upper.energy().getAmountAsLong() + lower.energy().getAmountAsLong(), 0L, "Both processing stages consume their own power"); h.succeed();
        });
    }
    private static void menu(GameTestHelper h) {
        var machine = place(h, POS); Player player = WeaponGameTests.player(h);
        var menu = new MetalPressMenu(24, player.getInventory(), machine, machine.data); player.containerMenu = menu;
        machine.setItem(0, item("plateiron")); player.getInventory().setItem(9, item("minecraft:gold_ingot"));
        h.assertTrue(menu.quickMoveStack(player,4).isEmpty(), "Shift-click cannot insert an incompatible counterpart");
        player.getInventory().setItem(10, item("minecraft:flint"));
        h.assertValueEqual(menu.quickMoveStack(player,5).getCount(),1,"Compatible item is routed to second input");
        h.assertTrue(!menu.slots.get(2).mayPlace(item("plateiron")),"Output slot rejects insertion");
        h.assertTrue(menu.clickMenuButton(player,0),"Auto split button works through active menu");
        h.assertValueEqual(machine.data.get(3),1,"Auto split toggled");
        h.assertTrue(!menu.clickMenuButton(player,1),"Ammo Press previous-plan button is not valid for Metal Press");
        machine.setItem(2,item("mechanicalpartsiron"));
        h.assertValueEqual(menu.quickMoveStack(player,2).getCount(),1,"Output shift-click uses four-slot machine layout");
        var handler = machine.automation();
        try (Transaction tx = Transaction.openRoot()) {
            h.assertValueEqual(handler.insert(3,ItemResource.of(item("machinestackupgrade")),8,tx),7,"Upgrade slot has correct transactional size and capacity");
            h.assertValueEqual(handler.extract(0,ItemResource.of(item("plateiron")),1,tx),0,"Automation cannot extract input");
        }
        h.assertTrue(machine.getItem(3).isEmpty(),"Aborted upgrade insertion rolls back"); h.succeed();
    }
    private static void codec(GameTestHelper h) {
        var input = new MetalPressRecipe.Input(item("plateiron"),item("minecraft:flint"));
        var original = h.getLevel().getServer().getRecipeManager().getRecipeFor(TGMachineContent.METAL_PRESS_RECIPE.get(),input,h.getLevel()).orElseThrow().value();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            Recipe.STREAM_CODEC.encode(buffer,original); var decoded = (MetalPressRecipe) Recipe.STREAM_CODEC.decode(buffer);
            h.assertTrue(decoded.matches(new MetalPressRecipe.Input(input.second(),input.first()),h.getLevel()),"Swap permission survives recipe synchronization");
            h.assertValueEqual(decoded.powerPerTick(),20,"Base power survives synchronization");
            var ordered = new MetalPressRecipe(Ingredient.of(Items.IRON_INGOT),Ingredient.of(Items.FLINT),false,original.result(),100,20);
            h.assertTrue(!ordered.matches(new MetalPressRecipe.Input(new ItemStack(Items.FLINT),new ItemStack(Items.IRON_INGOT)),h.getLevel()),"Data packs can forbid input swapping");
        } finally { buffer.release(); }
        h.succeed();
    }
    private static void crafting(GameTestHelper h) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT), engine=item("electricengine"), redstone=new ItemStack(Items.REDSTONE);
        for (ItemStack top : List.of(new ItemStack(Items.IRON_BLOCK),item("plateiron"))) {
            ItemStack press = CraftingGameTests.craft(h,3,3,iron,top,iron,iron,engine,iron,iron,redstone,iron);
            h.assertTrue(press.is(TGMachineContent.METAL_PRESS_ITEM.get()),"Both original crafting variants produce the real block");
            h.assertValueEqual(press.getCount(),1,"Machine recipe yield");
        }
        h.succeed();
    }
    private MetalPressGameTests() {}
}
