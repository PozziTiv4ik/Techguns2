package techguns.modern.test;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.modern.machine.AmmoPressBlockEntity;
import techguns.modern.machine.TGMachineContent;

final class SmeltingGameTests {
    private static final BlockPos POS=new BlockPos(4,2,4);
    private record Case(String input,String result,int count,float xp) {}
    private static final List<Case> CASES=List.of(
            new Case("ore_copper","ingotcopper",1,.5f), new Case("ore_tin","ingottin",1,.5f), new Case("ore_lead","ingotlead",1,1f),
            new Case("turretarmoriron","minecraft:iron_ingot",5,0), new Case("turretarmorsteel","ingotsteel",5,0),
            new Case("turretarmorobsidiansteel","ingotobsidiansteel",5,0), new Case("ironbarrel","minecraft:iron_ingot",6,0),
            new Case("obsidiansteelbarrel","ingotobsidiansteel",6,0), new Case("platebronze","ingotbronze",1,0),
            new Case("platecopper","ingotcopper",1,0), new Case("platetin","ingottin",1,0), new Case("platelead","ingotlead",1,0),
            new Case("plateiron","minecraft:iron_ingot",1,0), new Case("plateobsidiansteel","ingotobsidiansteel",1,0),
            new Case("platetitanium","ingottitanium",1,0), new Case("platesteel","ingotsteel",1,0),
            new Case("rawrubber","rubberbar",1,0), new Case("rawplastic","plasticsheet",1,0), new Case("oretitanium","ingottitanium",1,0));
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        for (Case value:CASES) registry.register("smelting_"+value.input(), () -> h -> production(h,value));
        registry.register("smelting_complex_ores_rejected", () -> SmeltingGameTests::noShortcuts);
        registry.register("smelting_multi_output_obstruction", () -> SmeltingGameTests::obstruction);
        registry.register("smelting_reload_operation", () -> SmeltingGameTests::reload);
        registry.register("smelting_ore_to_ammo_hopper_chain", () -> SmeltingGameTests::chain);
    }
    private static ItemStack item(String id,int count) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id.contains(":") ? id : "techguns:"+id)),count);
    }
    private static FurnaceBlockEntity furnace(GameTestHelper h,BlockPos pos,String input) {
        h.setBlock(pos,Blocks.FURNACE); var furnace=h.getBlockEntity(pos,FurnaceBlockEntity.class);
        furnace.setItem(0,item(input,1)); furnace.setItem(1,new ItemStack(Items.COAL)); return furnace;
    }
    private static void output(GameTestHelper h,FurnaceBlockEntity furnace,String id,int count) {
        h.assertTrue(furnace.getItem(2).is(item(id,1).getItem()),"Furnace output "+id);
        h.assertValueEqual(furnace.getItem(2).getCount(),count,"Original smelting yield");
        h.assertTrue(furnace.getItem(0).isEmpty(),"One input consumed");
    }
    private static void production(GameTestHelper h,Case value) {
        var input=new SingleRecipeInput(item(value.input(),1));
        var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.SMELTING,input,h.getLevel()).orElseThrow();
        h.assertTrue(recipe.id().identifier().getNamespace().equals("techguns"),"Original mod recipe loaded");
        h.assertValueEqual(recipe.value().experience(),value.xp(),"Original experience");
        h.assertValueEqual(recipe.value().cookingTime(),200,"Original furnace cycle");
        var furnace=furnace(h,POS,value.input());
        h.runAfterDelay(195, () -> h.assertTrue(furnace.getItem(2).isEmpty(),"No output before the smelting cycle"));
        h.runAfterDelay(205, () -> { output(h,furnace,value.result(),value.count()); h.succeed(); });
    }
    private static void noShortcuts(GameTestHelper h) {
        for (String id:List.of("ore_titanium","ore_uranium")) {
            var input=new SingleRecipeInput(item(id,1));
            h.assertTrue(h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.SMELTING,input,h.getLevel()).isEmpty(),
                    "Original chemical processing cannot be skipped: "+id);
            h.assertTrue(h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.BLASTING,input,h.getLevel()).isEmpty(),
                    "Vanilla blast furnace cannot skip the chemical step: "+id);
        }
        h.succeed();
    }
    private static void obstruction(GameTestHelper h) {
        var furnace=furnace(h,POS,"ironbarrel"); furnace.setItem(2,new ItemStack(Items.IRON_INGOT,60));
        h.runAfterDelay(10, () -> {
            h.assertValueEqual(furnace.getItem(1).getCount(),1,"Six-output recipe cannot ignite with only four free spaces");
            h.assertValueEqual(furnace.getItem(0).getCount(),1,"Blocked input retained");
            furnace.removeItem(2,2);
        });
        h.runAfterDelay(215, () -> { output(h,furnace,"minecraft:iron_ingot",64); h.succeed(); });
    }
    private static void reload(GameTestHelper h) {
        var furnace=furnace(h,POS,"obsidiansteelbarrel");
        h.runAfterDelay(100, () -> {
            var saved=furnace.saveWithFullMetadata(h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(h.absolutePos(POS));
            var restored=(FurnaceBlockEntity)BlockEntity.loadStatic(h.absolutePos(POS),furnace.getBlockState(),saved,h.getLevel().registryAccess());
            h.getLevel().setBlockEntity(restored);
            h.assertTrue(restored.getItem(1).isEmpty(),"Reload does not restore spent fuel");
        });
        h.runAfterDelay(205, () -> { output(h,h.getBlockEntity(POS,FurnaceBlockEntity.class),"ingotobsidiansteel",6); h.succeed(); });
    }
    private static void chain(GameTestHelper h) {
        h.setBlock(POS,TGMachineContent.AMMO_PRESS.get());
        var press=h.getBlockEntity(POS,AmmoPressBlockEntity.class);
        press.setItem(1,item("ingotcopper",2)); press.setItem(2,new ItemStack(Items.GUNPOWDER));
        try (var tx=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            press.energy().insert(500,tx); tx.commit();
        }
        h.setBlock(POS.above(),Blocks.HOPPER);
        var furnace=furnace(h,POS.above(2),"ore_lead");
        h.runAfterDelay(340, () -> {
            h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(2).isEmpty(),"Mined ore is smelted and output extracted by a real hopper");
            h.assertTrue(press.getItem(3).is(item("pistolrounds",1).getItem()),"Smelted lead plus original copper makes rounds");
            h.assertValueEqual(press.getItem(3).getCount(),12,"Original Ammo Press yield from smelted ore");
            h.assertValueEqual(press.energy().getAmountAsLong(),0L,"Full source energy cost paid"); h.succeed();
        });
    }
}
