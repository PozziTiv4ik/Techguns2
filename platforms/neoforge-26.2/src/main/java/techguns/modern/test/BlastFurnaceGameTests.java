package techguns.modern.test;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.TGContent;
import techguns.modern.machine.*;

final class BlastFurnaceGameTests {
    private static final BlockPos POS = new BlockPos(4,2,4);
    private record Case(String id, String first, int firstCount, String second, int secondCount, String output, int count, int ticks) {}
    private static final List<Case> CASES = List.of(
            new Case("steel_coal","minecraft:iron_ingot",4,"minecraft:coal",1,"ingotsteel",4,800),
            new Case("steel_charcoal","minecraft:iron_ingot",4,"minecraft:charcoal",1,"ingotsteel",4,800),
            new Case("obsidian_steel","ingotsteel",1,"minecraft:obsidian",1,"ingotobsidiansteel",1,200),
            new Case("bronze","minecraft:copper_ingot",3,"ingottin",1,"ingotbronze",4,100));
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        for (Case value : CASES) registry.register("blast_recipe_"+value.id(), () -> h -> production(h, value));
        registry.register("blast_count_order_codec", () -> BlastFurnaceGameTests::countAndOrder);
        registry.register("blast_bronze_batch", () -> BlastFurnaceGameTests::bronzeBatch);
        registry.register("blast_reload_quadratic", () -> BlastFurnaceGameTests::reload);
        registry.register("blast_menu_packets", () -> BlastFurnaceGameTests::menuPackets);
        registry.register("blast_menu_slots", () -> BlastFurnaceGameTests::menuSlots);
        registry.register("blast_break_refund", () -> BlastFurnaceGameTests::breakMachine);
        registry.register("blast_furnace_to_press", () -> BlastFurnaceGameTests::chain);
        registry.register("blast_capacity_rollback", () -> BlastFurnaceGameTests::capacity);
        registry.register("blast_no_power", () -> BlastFurnaceGameTests::noPower);
        registry.register("blast_redstone_pause", () -> BlastFurnaceGameTests::redstone);
        registry.register("blast_crafting", () -> BlastFurnaceGameTests::crafting);
    }
    private static ItemStack item(String name, int count) {
        Identifier id = name.contains(":") ? Identifier.parse(name) : TGContent.id(name);
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new AssertionError("Missing ingredient: "+name);
        return new ItemStack(BuiltInRegistries.ITEM.getValue(id), count);
    }
    private static BlastFurnaceBlockEntity place(GameTestHelper h) {
        h.setBlock(POS, TGMachineContent.BLAST_FURNACE.get());
        return get(h);
    }
    private static BlastFurnaceBlockEntity get(GameTestHelper h) { return (BlastFurnaceBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(POS)); }
    private static void charge(GameTestHelper h, BlockPos pos, int amount) {
        var energy = h.getLevel().getCapability(Capabilities.Energy.BLOCK, h.absolutePos(pos), Direction.NORTH);
        h.assertTrue(energy != null, "Machine has standard energy capability");
        try (Transaction tx = Transaction.openRoot()) { h.assertValueEqual(energy.insert(amount,tx),amount,"Energy accepted"); tx.commit(); }
    }
    private static void supply(BlastFurnaceBlockEntity furnace, Case value, int batch) {
        furnace.setItem(0,item(value.first(),value.firstCount()*batch)); furnace.setItem(1,item(value.second(),value.secondCount()*batch));
    }
    private static void output(GameTestHelper h, BlastFurnaceBlockEntity furnace, String id, int count) {
        h.assertTrue(furnace.getItem(2).is(item(id,1).getItem()),"Expected alloy: "+id);
        h.assertValueEqual(furnace.getItem(2).getCount(),count,"Original output amount");
    }
    private static void production(GameTestHelper h, Case value) {
        var furnace=place(h); supply(furnace,value,1); charge(h,POS,value.ticks()*10);
        h.runAfterDelay(5, () -> {
            h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(1).isEmpty(),"Exact counted inputs reserved at start");
            h.assertValueEqual(furnace.data.get(2),value.ticks(),"Original processing time");
            h.assertValueEqual(furnace.data.get(7),10,"Original power per tick");
        });
        h.runAfterDelay(value.ticks()+5, () -> {
            output(h,furnace,value.output(),value.count());
            h.assertValueEqual(furnace.energy().getAmountAsLong(),0L,"Cycle consumes exact recipe energy"); h.succeed();
        });
    }
    private static void countAndOrder(GameTestHelper h) {
        var valid = new MetalPressRecipe.Input(new ItemStack(Items.IRON_INGOT,4),new ItemStack(Items.COAL));
        var recipe = h.getLevel().getServer().getRecipeManager().getRecipeFor(TGMachineContent.BLAST_FURNACE_RECIPE.get(),valid,h.getLevel()).orElseThrow().value();
        h.assertTrue(!recipe.matches(new MetalPressRecipe.Input(new ItemStack(Items.IRON_INGOT,3),valid.second()),h.getLevel()),"Three ingots cannot pay a four-ingot recipe");
        h.assertTrue(!recipe.matches(new MetalPressRecipe.Input(valid.second(),valid.first()),h.getLevel()),"Furnace recipes preserve slot order");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            Recipe.STREAM_CODEC.encode(buffer,recipe); var decoded=(BlastFurnaceRecipe)Recipe.STREAM_CODEC.decode(buffer);
            h.assertValueEqual(decoded.firstCount(),4,"Input count survives recipe synchronization");
            h.assertValueEqual(decoded.duration(),800,"Long duration survives synchronization");
            h.assertTrue(decoded.matches(valid,h.getLevel()),"Decoded recipe works");
        } finally { buffer.release(); }
        h.succeed();
    }
    private static void bronzeBatch(GameTestHelper h) {
        var furnace=place(h); supply(furnace,CASES.get(3),4); furnace.setItem(3,item("machinestackupgrade",3)); charge(h,POS,16000);
        h.runAfterDelay(5, () -> {
            h.assertValueEqual(furnace.data.get(6),4,"Four recipe sets per batch");
            h.assertValueEqual(furnace.data.get(7),160,"Original squared batch power");
            h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(1).isEmpty(),"Batch consumes twelve copper and four tin");
        });
        h.runAfterDelay(105, () -> { output(h,furnace,"ingotbronze",16); h.assertValueEqual(furnace.energy().getAmountAsLong(),0L,"Four sets cost 16000 FE"); h.succeed(); });
    }
    private static void reload(GameTestHelper h) {
        var furnace=place(h); supply(furnace,CASES.getFirst(),2); furnace.setItem(3,item("machinestackupgrade",1)); charge(h,POS,32000);
        h.runAfterDelay(200, () -> {
            int progress=furnace.data.get(1); long energy=furnace.energy().getAmountAsLong();
            h.assertValueEqual(energy,32000L-progress*40L,"Squared power before restart");
            var saved=furnace.saveWithFullMetadata(h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(h.absolutePos(POS));
            var restored=(BlastFurnaceBlockEntity)BlockEntity.loadStatic(h.absolutePos(POS),furnace.getBlockState(),saved,h.getLevel().registryAccess());
            h.getLevel().setBlockEntity(restored);
            h.assertValueEqual(restored.energy().getAmountAsLong(),energy,"Loading does not clamp the 40000-FE machine to press capacity");
            h.assertValueEqual(restored.data.get(1),progress,"Long operation retains progress");
        });
        h.runAfterDelay(805, () -> {
            var restored=get(h); output(h,restored,"ingotsteel",8);
            h.assertValueEqual(restored.energy().getAmountAsLong(),0L,"Restart pays remaining squared energy cost"); h.succeed();
        });
    }
    private static void menuPackets(GameTestHelper h) {
        var furnace=place(h); Player player=WeaponGameTests.player(h);
        var raw=new SimpleContainerData(ProcessingMachineBlockEntity.DATA_COUNT);
        int[] expected={40000,70000,72000,0,2,1,8,40000,40000};
        for(int i=0;i<expected.length;i++) raw.set(i,expected[i]);
        var server=new BlastFurnaceMenu(31,player.getInventory(),furnace,raw);
        var client=new BlastFurnaceMenu(31,player.getInventory());
        var words=new SplitIntContainerData(raw);
        for(int i=words.getCount()-1;i>=0;i--) {
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {
                ClientboundContainerSetDataPacket.STREAM_CODEC.encode(buffer,new ClientboundContainerSetDataPacket(server.containerId,i,words.get(i)));
                var decoded=ClientboundContainerSetDataPacket.STREAM_CODEC.decode(buffer);
                client.setData(decoded.getId(),decoded.getValue());
            } finally { buffer.release(); }
        }
        for(int i=0;i<expected.length;i++) h.assertValueEqual(client.value(i),expected[i],"Menu integer survives vanilla signed-short packets at index "+i);
        h.succeed();
    }
    private static void menuSlots(GameTestHelper h) {
        var furnace=place(h); Player player=WeaponGameTests.player(h);
        var menu=new BlastFurnaceMenu(32,player.getInventory(),furnace,furnace.data); player.containerMenu=menu;
        player.getInventory().setItem(9,new ItemStack(Items.COAL,2)); player.getInventory().setItem(10,new ItemStack(Items.IRON_INGOT,8));
        h.assertValueEqual(menu.quickMoveStack(player,4).getCount(),2,"Coal enters second recipe slot");
        h.assertValueEqual(menu.quickMoveStack(player,5).getCount(),8,"Iron enters first recipe slot");
        h.assertTrue(furnace.getItem(0).is(Items.IRON_INGOT) && furnace.getItem(1).is(Items.COAL),"Input positions are distinct");
        h.assertTrue(!menu.clickMenuButton(player,0),"Furnace has no auto-split or plan-selection action");
        h.assertValueEqual(menu.slots.get(0).x,19,"Original first-slot position");
        h.assertValueEqual(menu.slots.get(2).y,50,"Original output position");
        h.succeed();
    }
    private static void breakMachine(GameTestHelper h) {
        var furnace=place(h); supply(furnace,CASES.getFirst(),1); charge(h,POS,8000);
        h.runAfterDelay(20, () -> h.getLevel().destroyBlock(h.absolutePos(POS),true));
        h.runAfterDelay(22, () -> {
            int iron=0,coal=0,steel=0,blocks=0;
            for(ItemEntity entity:h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(POS)).inflate(3))) {
                ItemStack stack=entity.getItem();
                if(stack.is(Items.IRON_INGOT)) iron+=stack.getCount(); if(stack.is(Items.COAL)) coal+=stack.getCount();
                if(stack.is(item("ingotsteel",1).getItem())) steel+=stack.getCount(); if(stack.is(TGMachineContent.BLAST_FURNACE_ITEM.get())) blocks+=stack.getCount();
            }
            h.assertValueEqual(iron,4,"Reserved ingot count is refunded"); h.assertValueEqual(coal,1,"Reserved carbon is refunded");
            h.assertValueEqual(steel,0,"Unfinished output is not also dropped"); h.assertValueEqual(blocks,1,"Block loot"); h.succeed();
        });
    }
    private static void chain(GameTestHelper h) {
        var furnace=place(h); supply(furnace,CASES.getFirst(),1); charge(h,POS,8000);
        BlockPos pressPos=POS.below(2); h.setBlock(pressPos,TGMachineContent.METAL_PRESS.get()); h.setBlock(POS.below(),Blocks.HOPPER);
        var press=(MetalPressBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pressPos));
        press.button(WeaponGameTests.player(h),0); press.setItem(3,item("machinestackupgrade",1)); charge(h,pressPos,8000);
        h.runAfterDelay(1040, () -> {
            h.assertTrue(press.getItem(2).is(item("platesteel",1).getItem()),"Furnace steel reaches the real press through a hopper");
            h.assertValueEqual(press.getItem(2).getCount(),4,"Four iron ingots ultimately produce four steel plates");
            h.assertTrue(furnace.getItem(2).isEmpty(),"All steel transferred"); h.succeed();
        });
    }
    private static void capacity(GameTestHelper h) {
        var furnace=place(h);
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(furnace.energy().insert(50000,tx),40000,"Original tank capacity"); }
        h.assertValueEqual(furnace.energy().getAmountAsLong(),0L,"Aborted energy transfer rolls back");
        var handler=furnace.automation();
        try(Transaction tx=Transaction.openRoot()) {
            h.assertValueEqual(handler.insert(0,ItemResource.of(new ItemStack(Items.COAL)),1,tx),0,"Coal cannot enter first slot");
            h.assertValueEqual(handler.insert(1,ItemResource.of(new ItemStack(Items.COAL)),1,tx),1,"Carbon accepted in second slot");
            h.assertValueEqual(handler.extract(1,ItemResource.of(new ItemStack(Items.COAL)),1,tx),0,"Automation cannot reclaim a recipe input");
        }
        h.assertTrue(furnace.getItem(1).isEmpty(),"Aborted item transfer rolls back"); h.succeed();
    }
    private static void noPower(GameTestHelper h) {
        var furnace=place(h); supply(furnace,CASES.getFirst(),1); boolean previous=TGMachineConfig.MACHINES_NEED_NO_POWER.get();
        try {
            TGMachineConfig.MACHINES_NEED_NO_POWER.set(true);
            for(int tick=0;tick<801;tick++) ProcessingMachineBlockEntity.tick(h.getLevel(),furnace.getBlockPos(),furnace.getBlockState(),furnace);
            output(h,furnace,"ingotsteel",4); h.assertValueEqual(furnace.energy().getAmountAsLong(),0L,"No-power setting retains counted processing");
        } finally { TGMachineConfig.MACHINES_NEED_NO_POWER.set(previous); }
        h.succeed();
    }
    private static void redstone(GameTestHelper h) {
        var furnace=place(h); supply(furnace,CASES.get(3),1); charge(h,POS,1000); Player player=WeaponGameTests.player(h);
        furnace.button(player,2);
        h.runAfterDelay(8, () -> { h.assertTrue(!furnace.working(),"High-signal mode prevents initial reservation"); h.setBlock(POS.east(),Blocks.REDSTONE_BLOCK); });
        h.runAfterDelay(25, () -> {
            furnace.button(player,2); int progress=furnace.data.get(1);
            h.runAfterDelay(10, () -> { h.assertValueEqual(furnace.data.get(1),progress,"Low-signal mode pauses without resetting"); h.setBlock(POS.east(),Blocks.AIR); });
        });
        h.runAfterDelay(140, () -> { output(h,furnace,"ingotbronze",4); h.succeed(); });
    }
    private static void crafting(GameTestHelper h) {
        ItemStack plate=item("plateiron",1), stone=new ItemStack(Items.STONE_BRICKS);
        ItemStack furnace=CraftingGameTests.craft(h,3,3,plate,new ItemStack(Items.REDSTONE),plate,plate,new ItemStack(Items.FURNACE),plate,stone,new ItemStack(Items.IRON_BLOCK),stone);
        h.assertTrue(furnace.is(TGMachineContent.BLAST_FURNACE_ITEM.get()),"Original stonebrick metadata recipe produces the modern machine");
        h.succeed();
    }
    private BlastFurnaceGameTests() {}
}
