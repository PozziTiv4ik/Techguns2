package techguns.modern.test;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
import techguns.modern.machine.ProcessingMachineBlockEntity;
import techguns.modern.machine.SplitIntContainerData;
import techguns.modern.machine.TGMachineConfig;
import techguns.modern.machine.fabricator.*;
import techguns.modern.machine.multiblock.*;

final class FabricatorGameTests {
    private static final BlockPos POS=new BlockPos(4,2,4);
    private record Case(String output,String input,int a,String wire,int b,String powder,int c,String plate,int d,int count) {}
    private static final List<Case> CASES=List.of(
        new Case("energycellempty","minecraft:gold_ingot",1,"copperwire",1,"minecraft:redstone",3,"plasticsheet",1,1),
        new Case("cyberneticparts","minecraft:soul_sand",1,"goldwire",1,"minecraft:redstone",1,"plasticsheet",1,1),
        new Case("powerplating","ingottitanium",2,"circuitboard",4,"mechanicalpartscarbon",1,"platecarbon",4,2),
        new Case("sonicemitter","coil",1,"circuitboardelite",2,"mechanicalpartscarbon",1,"platetitanium",1,1),
        new Case("rademitter","enricheduranium",1,"circuitboardelite",2,"mechanicalpartscarbon",2,"platelead",2,1),
        new Case("nuclearpowercelldepleted","ingotsteel",1,"circuitboard",1,"minecraft:redstone",4,"platelead",2,1));
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        for(var c:CASES) {
            registry.register("fabricator_recipe_"+c.output,() -> h -> production(h,c,1));
            registry.register("fabricator_batch_"+c.output,() -> h -> production(h,c,8));
        }
        registry.register("fabricator_structure_four_rotations",() -> FabricatorGameTests::rotations);
        registry.register("fabricator_incomplete_structure_atomic",() -> FabricatorGameTests::incomplete);
        registry.register("fabricator_other_formation_cannot_be_claimed",() -> FabricatorGameTests::overlap);
        registry.register("fabricator_ports_roles_and_rollback",() -> FabricatorGameTests::ports);
        registry.register("fabricator_cached_ports_after_break",() -> FabricatorGameTests::stalePorts);
        registry.register("fabricator_reload_batch_and_links",() -> FabricatorGameTests::reload);
        registry.register("fabricator_missing_controller_blocks_transfer",() -> FabricatorGameTests::missingController);
        registry.register("fabricator_power_redstone_and_unformed_pause",() -> FabricatorGameTests::pause);
        registry.register("fabricator_output_room_limits_batch",() -> FabricatorGameTests::batchRoom);
        registry.register("fabricator_ingredients_limit_batch",() -> FabricatorGameTests::batchIngredients);
        registry.register("fabricator_completed_batch_waits_for_output",() -> FabricatorGameTests::blockedOutput);
        registry.register("fabricator_break_refunds_all_reserved_inputs",() -> FabricatorGameTests::breakRefund);
        registry.register("fabricator_recipe_codecs_and_input_order",() -> FabricatorGameTests::codecs);
        registry.register("fabricator_menu_slots_security_and_energy",() -> FabricatorGameTests::menu);
        registry.register("fabricator_hopper_to_nuclear_chemistry",() -> FabricatorGameTests::nuclearChain);
        registry.register("fabricator_no_power_setting",() -> FabricatorGameTests::optionalPower);
        registry.register("fabricator_orphan_formation_recovery",() -> FabricatorGameTests::orphan);
    }
    private static ItemStack item(String id,int count) { return ChemLabGameTests.item(id,count); }
    private static FabricatorBlockEntity place(GameTestHelper h,BlockPos pos,Direction facing,boolean form) {
        for(var part:FabricatorBlockEntity.parts(h.absolutePos(pos),facing)) h.getLevel().setBlock(part.pos(),part.block().defaultBlockState(),3);
        var machine=h.getBlockEntity(pos,FabricatorBlockEntity.class);
        if(form) h.assertTrue(machine.form(facing,WeaponGameTests.player(h)),"Source 2x2x2 structure forms");
        return machine;
    }
    private static FabricatorBlockEntity place(GameTestHelper h) { return place(h,POS,Direction.SOUTH,true); }
    private static void supply(FabricatorBlockEntity machine,Case c,int batch) {
        machine.setItem(0,item(c.input,c.a*batch)); machine.setItem(1,item(c.wire,c.b*batch));
        machine.setItem(2,item(c.powder,c.c*batch)); machine.setItem(3,item(c.plate,c.d*batch));
        if(batch>1) machine.setItem(5,TGContent.MATERIALS.get("machinestackupgrade").toStack(batch-1));
    }
    private static void charge(FabricatorBlockEntity machine,int amount) { try(Transaction tx=Transaction.openRoot()) { if(machine.energy().insert(amount,tx)!=amount) throw new AssertionError("Test energy capacity"); tx.commit(); } }
    private static void tick(GameTestHelper h,FabricatorBlockEntity machine,int count) { for(int i=0;i<count;i++) ProcessingMachineBlockEntity.tick(h.getLevel(),machine.getBlockPos(),machine.getBlockState(),machine); }
    private static void output(GameTestHelper h,FabricatorBlockEntity machine,Case c,int batch) {
        h.assertTrue(machine.getItem(4).is(item(c.output,1).getItem()),"Correct original output "+c.output); h.assertValueEqual(machine.getItem(4).getCount(),c.count*batch,"Original product amount");
    }
    private static void production(GameTestHelper h,Case c,int batch) {
        var machine=place(h); supply(machine,c,batch); charge(machine,8000*batch);
        for(int i=0;i<4;i++) h.assertTrue(machine.canPlaceItem(i,machine.getItem(i)),"Original material enters its fixed slot");
        h.runAfterDelay(5,() -> {
            h.assertValueEqual(machine.data.get(2),100,"Original five-second batch duration"); h.assertValueEqual(machine.data.get(7),80*batch,"Linear FE per tick, including upgrades");
            for(int i=0;i<4;i++) h.assertTrue(machine.getItem(i).isEmpty(),"All counted inputs reserved at start");
            h.assertTrue(machine.getItem(4).isEmpty(),"No early output");
        });
        h.runAfterDelay(105,() -> {
            output(h,machine,c,batch); h.assertValueEqual(machine.energy().getAmountAsInt(),0,"Exact 8000 FE per product batch");
            h.assertValueEqual(machine.getItem(5).getCount(),batch-1,"Upgrade is reusable"); h.succeed();
        });
    }
    private static void rotations(GameTestHelper h) {
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            var machine=place(h,POS,facing,true); var parts=FabricatorBlockEntity.parts(machine.getBlockPos(),facing);
            h.assertValueEqual(parts.size(),8,"Exactly eight blocks");
            h.assertValueEqual(parts.stream().filter(p -> p.connector()==2).count(),3L,"Three lower housing connectors");
            for(var p:parts) {
                var state=h.getLevel().getBlockState(p.pos()); h.assertTrue(state.getValue(MachineFormation.FORMED),"Every part changes state");
                if(p.connector()!=0) h.assertTrue(((FabricatorPartBlockEntity)h.getLevel().getBlockEntity(p.pos())).master()==machine,"Every part resolves its rotated master");
                double height=state.getCollisionShape(h.getLevel(),p.pos()).max(Direction.Axis.Y);
                h.assertTrue(Math.abs(height-(p.connector()==1 ? .9 : 1))<.000001,"Source formed glass height is 0.9 blocks; housing stays full");
            }
            machine.formation().unform();
            for(var p:parts) { h.assertTrue(!h.getLevel().getBlockState(p.pos()).getValue(MachineFormation.FORMED),"All parts unform"); h.getLevel().setBlock(p.pos(),Blocks.AIR.defaultBlockState(),3); }
        }
        h.succeed();
    }
    private static void incomplete(GameTestHelper h) {
        var machine=place(h,POS,Direction.SOUTH,false); h.setBlock(POS.above(),Blocks.GLASS);
        var player=WeaponGameTests.player(h);
        h.assertTrue(!machine.form(Direction.UP,player) && !machine.form(Direction.SOUTH,player),"Vertical formation and ordinary glass rejected");
        for(var p:FabricatorBlockEntity.parts(machine.getBlockPos(),Direction.SOUTH)) if(h.getLevel().getBlockState(p.pos()).hasProperty(MachineFormation.FORMED))
            h.assertTrue(!h.getLevel().getBlockState(p.pos()).getValue(MachineFormation.FORMED),"Failed formation has no partial links"); h.succeed();
    }
    private static void overlap(GameTestHelper h) {
        var first=place(h); BlockPos other=new BlockPos(3,2,5);
        for(var p:FabricatorBlockEntity.parts(h.absolutePos(other),Direction.SOUTH)) if(h.getLevel().getBlockState(p.pos()).isAir()) h.getLevel().setBlock(p.pos(),p.block().defaultBlockState(),3);
        var second=h.getBlockEntity(other,FabricatorBlockEntity.class);
        h.assertTrue(!second.form(Direction.SOUTH,WeaponGameTests.player(h)),"Another controller cannot steal formed housing or glass");
        h.assertValueEqual(first.formation().status(),1,"Existing formation remains valid after rejected overlap"); h.succeed();
    }
    private static void ports(GameTestHelper h) {
        var machine=place(h);
        for(var p:FabricatorBlockEntity.parts(machine.getBlockPos(),Direction.SOUTH)) for(Direction side:Direction.values()) {
            var energy=h.getLevel().getCapability(Capabilities.Energy.BLOCK,p.pos(),side); var items=h.getLevel().getCapability(Capabilities.Item.BLOCK,p.pos(),side);
            h.assertTrue((energy!=null)==(p.connector()!=1) && (items!=null)==(p.connector()!=1),"Only controller and lower housing expose energy/items on all sides");
            h.assertTrue(h.getLevel().getCapability(Capabilities.Fluid.BLOCK,p.pos(),side)==null,"Fabricator has no fictitious fluid tank");
            if(p.connector()==2) try(Transaction tx=Transaction.openRoot()) {
                h.assertValueEqual(energy.insert(70000,tx),70000,"Large energy values reach controller"); h.assertValueEqual(energy.extract(500,tx),500,"Source storage allows energy extraction");
                h.assertValueEqual(items.insert(1,ItemResource.of(item("goldwire",1)),3,tx),3,"Wire uses correct slot");
                h.assertValueEqual(items.insert(0,ItemResource.of(item("goldwire",1)),1,tx),0,"Wire cannot enter generic material slot");
                h.assertValueEqual(items.insert(4,ItemResource.of(item("goldwire",1)),1,tx),0,"Output is insertion-protected");
            }
        }
        h.assertValueEqual(machine.energy().getAmountAsInt(),0,"All energy transactions rolled back"); h.assertTrue(machine.getItem(1).isEmpty(),"All item transactions rolled back"); h.succeed();
    }
    private static void stalePorts(GameTestHelper h) {
        var machine=place(h); BlockPos port=machine.getBlockPos().west();
        var items=h.getLevel().getCapability(Capabilities.Item.BLOCK,port,Direction.DOWN); var energy=h.getLevel().getCapability(Capabilities.Energy.BLOCK,port,Direction.DOWN);
        h.getLevel().removeBlock(machine.getBlockPos().above(),false);
        try(Transaction tx=Transaction.openRoot()) {
            h.assertValueEqual(items.insert(0,ItemResource.of(new ItemStack(Items.SOUL_SAND)),1,tx),0,"Old item handle rejects broken machine");
            h.assertValueEqual(energy.insert(8000,tx),0,"Old energy handle rejects broken machine"); tx.commit();
        }
        h.assertTrue(!machine.formed(),"Broken glass invalidates controller"); h.succeed();
    }
    private static FabricatorBlockEntity reloadController(GameTestHelper h,FabricatorBlockEntity machine) {
        var tag=machine.saveWithFullMetadata(h.getLevel().registryAccess()); var state=machine.getBlockState(); h.getLevel().removeBlockEntity(machine.getBlockPos());
        var restored=(FabricatorBlockEntity)BlockEntity.loadStatic(machine.getBlockPos(),state,tag,h.getLevel().registryAccess()); h.getLevel().setBlockEntity(restored); return restored;
    }
    private static void reload(GameTestHelper h) {
        var machine=place(h); supply(machine,CASES.get(2),8); machine.getItem(0).set(DataComponents.CUSTOM_NAME,Component.literal("Named titanium")); charge(machine,64000); tick(h,machine,43);
        var restored=reloadController(h,machine); h.assertValueEqual(restored.data.get(1),42,"Batch progress survives save"); h.assertValueEqual(restored.data.get(6),8,"Batch size survives save");
        for(var p:FabricatorBlockEntity.parts(restored.getBlockPos(),Direction.SOUTH)) if(p.connector()!=0) {
            var old=h.getLevel().getBlockEntity(p.pos()); var tag=old.saveWithFullMetadata(h.getLevel().registryAccess()); var state=old.getBlockState(); h.getLevel().removeBlockEntity(p.pos());
            h.getLevel().setBlockEntity(BlockEntity.loadStatic(p.pos(),state,tag,h.getLevel().registryAccess()));
        }
        tick(h,restored,58); output(h,restored,CASES.get(2),8); h.assertValueEqual(restored.energy().getAmountAsInt(),0,"Restart pays only remaining ticks"); h.succeed();
    }
    private static void missingController(GameTestHelper h) {
        var machine=place(h); var port=machine.getBlockPos().west(); var handler=h.getLevel().getCapability(Capabilities.Item.BLOCK,port,Direction.DOWN);
        var saved=machine.saveWithFullMetadata(h.getLevel().registryAccess()); var state=machine.getBlockState(); h.getLevel().removeBlockEntity(machine.getBlockPos());
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(handler.insert(0,ItemResource.of(new ItemStack(Items.SOUL_SAND)),1,tx),0,"Missing controller cannot receive through cached port"); tx.commit(); }
        var restored=(FabricatorBlockEntity)BlockEntity.loadStatic(machine.getBlockPos(),state,saved,h.getLevel().registryAccess()); h.getLevel().setBlockEntity(restored);
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(handler.insert(0,ItemResource.of(new ItemStack(Items.SOUL_SAND)),1,tx),1,"Matching restored generation reconnects"); tx.commit(); }
        h.assertTrue(machine.getItem(0).isEmpty() && restored.getItem(0).getCount()==1,"Removed instance is never mutated"); h.succeed();
    }
    private static void pause(GameTestHelper h) {
        var machine=place(h,POS,Direction.SOUTH,false); supply(machine,CASES.getFirst(),1); charge(machine,8000); tick(h,machine,20);
        h.assertTrue(!machine.working() && machine.getItem(0).getCount()==1,"Unformed machine cannot reserve inputs");
        var player=WeaponGameTests.player(h); machine.form(Direction.SOUTH,player); tick(h,machine,20); int progress=machine.data.get(1); int energy=machine.energy().getAmountAsInt();
        machine.button(player,2); tick(h,machine,110); h.assertValueEqual(machine.data.get(1),progress,"Redstone pauses progress"); h.assertValueEqual(machine.energy().getAmountAsInt(),energy,"Redstone pauses power");
        machine.button(player,2); machine.button(player,2); machine.formation().unform(); tick(h,machine,110); h.assertValueEqual(machine.data.get(1),progress,"Broken formation pauses reserved batch");
        machine.form(Direction.SOUTH,player); machine.energy().set(0); tick(h,machine,110); h.assertValueEqual(machine.data.get(1),progress,"No power freezes production rather than losing a batch");
        charge(machine,energy); tick(h,machine,100-progress); output(h,machine,CASES.getFirst(),1); h.succeed();
    }
    private static void batchRoom(GameTestHelper h) {
        var machine=place(h); var c=CASES.getFirst(); supply(machine,c,8); machine.setItem(4,item(c.output,63)); charge(machine,8000); tick(h,machine,101);
        output(h,machine,c,64); h.assertValueEqual(machine.getItem(0).getCount(),7,"Output room limits reservation to one batch"); h.assertValueEqual(machine.energy().getAmountAsInt(),0,"Limited batch costs only 8000 FE"); h.succeed();
    }
    private static void batchIngredients(GameTestHelper h) {
        var machine=place(h); var c=CASES.get(1); supply(machine,c,8); machine.setItem(1,item(c.wire,3)); charge(machine,24000); tick(h,machine,101);
        output(h,machine,c,3); h.assertValueEqual(machine.getItem(0).getCount(),5,"Smallest ingredient stack limits batch"); h.assertTrue(machine.getItem(1).isEmpty(),"Limiting ingredient consumed exactly"); h.succeed();
    }
    private static void blockedOutput(GameTestHelper h) {
        var machine=place(h); var c=CASES.getFirst(); supply(machine,c,1); charge(machine,9000); tick(h,machine,2); machine.setItem(4,new ItemStack(Items.STONE,64)); tick(h,machine,150);
        h.assertTrue(machine.working() && machine.data.get(1)==100,"Completed batch waits without discarding its output"); h.assertValueEqual(machine.energy().getAmountAsInt(),1000,"Waiting does not charge additional power");
        machine.setItem(4,ItemStack.EMPTY); tick(h,machine,1); output(h,machine,c,1); h.succeed();
    }
    private static int dropped(GameTestHelper h,String id) { return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(POS)).inflate(3)).stream().filter(e -> e.getItem().is(item(id,1).getItem())).mapToInt(e -> e.getItem().getCount()).sum(); }
    private static void breakRefund(GameTestHelper h) {
        var machine=place(h); var c=CASES.get(2); supply(machine,c,8); charge(machine,64000); machine.getItem(0).set(DataComponents.CUSTOM_NAME,Component.literal("Named input")); tick(h,machine,18);
        var restored=reloadController(h,machine); h.getLevel().destroyBlock(restored.getBlockPos(),true);
        h.assertValueEqual(dropped(h,c.input),16,"Returns eight batches of titanium"); h.assertValueEqual(dropped(h,c.wire),32,"Returns reserved circuits");
        h.assertValueEqual(dropped(h,c.powder),8,"Returns reserved mechanisms"); h.assertValueEqual(dropped(h,c.plate),32,"Returns reserved plates"); h.assertValueEqual(dropped(h,c.output),0,"Unfinished output never drops");
        var titanium=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(POS)).inflate(3)).stream().filter(e -> e.getItem().is(item(c.input,1).getItem())).findFirst().orElseThrow();
        h.assertTrue(Component.literal("Named input").equals(titanium.getItem().get(DataComponents.CUSTOM_NAME)),"Refund after reload preserves input components"); h.succeed();
    }
    private static void codecs(GameTestHelper h) {
        var recipes=h.getLevel().getServer().getRecipeManager().recipeMap().byType(FabricatorContent.RECIPE.get()); h.assertValueEqual(recipes.size(),6,"All six source recipes loaded");
        for(var c:CASES) {
            var input=new FabricatorRecipe.Input(List.of(item(c.input,c.a),item(c.wire,c.b),item(c.powder,c.c),item(c.plate,c.d)));
            var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(FabricatorContent.RECIPE.get(),input,h.getLevel()).orElseThrow().value();
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try { Recipe.STREAM_CODEC.encode(buffer,recipe); var decoded=(FabricatorRecipe)Recipe.STREAM_CODEC.decode(buffer);
                h.assertTrue(decoded.matches(input,h.getLevel()),"Decoded recipe matches tagged inputs"); h.assertTrue(decoded.counts().equals(List.of(c.a,c.b,c.c,c.d)),"Exact separate consumption arguments survive codec");
                h.assertTrue(!decoded.matches(new FabricatorRecipe.Input(List.of(input.getItem(0),input.getItem(2),input.getItem(1),input.getItem(3))),h.getLevel()),"Wire and powder cannot be swapped");
                h.assertValueEqual(decoded.powerPerTick(),80,"Original base power");
            } finally { buffer.release(); }
        }
        h.succeed();
    }
    private static void menu(GameTestHelper h) {
        var machine=place(h); var player=WeaponGameTests.player(h); machine.setOwner(player); var menu=new FabricatorMenu(61,player.getInventory(),machine,machine.displayData); player.containerMenu=menu;
        String[] ids={"minecraft:gold_ingot","copperwire","minecraft:redstone","plasticsheet","machinestackupgrade"}; int[] target={0,1,2,3,5};
        for(int i=0;i<ids.length;i++) { player.getInventory().setItem(9+i,item(ids[i],1)); h.assertValueEqual(menu.quickMoveStack(player,6+i).getCount(),1,"Shift-click routes source category"); h.assertValueEqual(machine.getItem(target[i]).getCount(),1,"Correct slot receives item"); }
        h.assertTrue(!menu.getSlot(4).mayPlace(new ItemStack(Items.DIAMOND)),"Output slot rejects manual insertion"); h.assertTrue(!menu.clickMenuButton(player,0),"Fabricator has no fictitious mode control");
        h.assertTrue(menu.clickMenuButton(player,3),"Owner can make machine private"); var other=WeaponGameTests.player(h); other.setUUID(UUID.randomUUID()); h.assertTrue(!machine.canOpen(other) && !menu.clickMenuButton(other,2),"Another player cannot change private machine");
        charge(machine,98765); var sent=new SplitIntContainerData(machine.displayData); var clientData=new SimpleContainerData(9); var received=new SplitIntContainerData(clientData);
        for(int i=0;i<sent.getCount();i++) received.set(i,(short)sent.get(i)); h.assertValueEqual(clientData.get(0),98765,"Energy survives vanilla signed-word synchronization"); h.assertValueEqual(clientData.get(8),100000,"Capacity is not truncated");
        player.setPos(player.getX()+30,player.getY(),player.getZ()); h.assertTrue(!menu.clickMenuButton(player,2) && menu.quickMoveStack(player,0).isEmpty(),"Distant packets rejected"); h.succeed();
    }
    private static void nuclearChain(GameTestHelper h) {
        var machine=place(h); supply(machine,CASES.get(5),1); charge(machine,8000); h.setBlock(POS.below(),Blocks.HOPPER);
        var lab=ChemLabGameTests.place(h,POS.below(2)); lab.setItem(0,item("enricheduranium",1)); ChemLabGameTests.fill(lab,"minecraft:water",1000); ChemLabGameTests.charge(lab,4000);
        h.succeedWhen(() -> {
            h.assertTrue(lab.getItem(3).is(item("nuclearpowercell",1).getItem()),"Fabricated empty cell reaches the actual Chemical Laboratory through a hopper");
            h.assertValueEqual(lab.getItem(3).getCount(),1,"One complete nuclear cell produced"); h.assertTrue(lab.tanks().stack(0).isEmpty(),"Original water consumed");
            h.assertValueEqual(machine.energy().getAmountAsInt(),0,"Fabricator exact energy cost"); h.assertValueEqual(lab.energy().getAmountAsInt(),0,"Laboratory exact energy cost");
        });
    }
    private static void optionalPower(GameTestHelper h) {
        var machine=place(h); supply(machine,CASES.getFirst(),1); boolean previous=TGMachineConfig.MACHINES_NEED_NO_POWER.get();
        try { TGMachineConfig.MACHINES_NEED_NO_POWER.set(true); tick(h,machine,101); output(h,machine,CASES.getFirst(),1); h.assertValueEqual(machine.energy().getAmountAsInt(),0,"Shared no-power configuration works"); }
        finally { TGMachineConfig.MACHINES_NEED_NO_POWER.set(previous); } h.succeed();
    }
    private static void orphan(GameTestHelper h) {
        var machine=place(h); var tag=machine.saveWithFullMetadata(h.getLevel().registryAccess()); tag.remove("formation"); var state=machine.getBlockState(); h.getLevel().removeBlockEntity(machine.getBlockPos());
        var restored=(FabricatorBlockEntity)BlockEntity.loadStatic(machine.getBlockPos(),state,tag,h.getLevel().registryAccess()); h.getLevel().setBlockEntity(restored);
        h.runAfterDelay(25,() -> { for(var p:FabricatorBlockEntity.parts(restored.getBlockPos(),Direction.SOUTH)) h.assertTrue(!h.getLevel().getBlockState(p.pos()).getValue(MachineFormation.FORMED),"Orphan state is repaired without inventing a new ownership link"); h.succeed(); });
    }
    private FabricatorGameTests() {}
}
