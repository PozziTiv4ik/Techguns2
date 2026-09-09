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
import net.minecraft.world.InteractionHand;
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
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.TGContent;
import techguns.modern.fluid.TGFluids;
import techguns.modern.machine.SplitIntContainerData;
import techguns.modern.machine.TGMachineConfig;
import techguns.modern.machine.reaction.*;
import techguns.modern.network.MachineTanksPayload;

final class ReactionChamberGameTests {
    private static final BlockPos POS=new BlockPos(4,2,3);
    private record Case(String id,String input,String focus,String fluid,int level,int intensity,int checks,int power,String first,int count,String second,int secondCount,int consumed) {}
    private static final List<Case> CASES=List.of(
        new Case("uv_wheat","minecraft:wheat_seeds","rcuvemitter","minecraft:water",1,3,5,50000,"minecraft:wheat",1,"minecraft:wheat_seeds",2,1000),
        new Case("laser_focus","minecraft:diamond","rcheatray","minecraft:lava",4,3,5,100000,"laserfocus",1,"",0,4000),
        new Case("titanium","ore_titanium","rcheatray","creeper_acid",3,5,1,25000,"oretitanium",2,"minecraft:iron_ore",1,100),
        new Case("blazerod","quartzrod","rcheatray","minecraft:lava",4,7,3,250000,"minecraft:blaze_rod",1,"",0,1000),
        new Case("glowstone","minecraft:redstone_block","rcheatray","minecraft:lava",4,7,3,250000,"minecraft:glowstone",1,"",0,1000),
        new Case("antigrav","minecraft:nether_star","rcheatray","minecraft:lava",4,8,7,500000,"antigravcore",1,"",0,4000),
        new Case("uranium","yellowcake","rcheatray","minecraft:water",3,7,4,250000,"enricheduranium",1,"",0,1000));
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        for(var c:CASES) registry.register("reaction_recipe_"+c.id,() -> h -> production(h,c));
        registry.register("reaction_structure_rotations",() -> ReactionChamberGameTests::rotations);
        registry.register("reaction_structure_requires_all_36_blocks",() -> ReactionChamberGameTests::incomplete);
        registry.register("reaction_connector_capabilities_and_rollback",() -> ReactionChamberGameTests::connectors);
        registry.register("reaction_cached_ports_reject_broken_structure",() -> ReactionChamberGameTests::stalePorts);
        registry.register("reaction_target_level_does_not_delete_fluid",() -> ReactionChamberGameTests::tankTarget);
        registry.register("reaction_start_requires_exact_settings",() -> ReactionChamberGameTests::startChecks);
        registry.register("reaction_reload_keeps_progress_and_random_state",() -> ReactionChamberGameTests::reload);
        registry.register("reaction_focus_removal_fails",() -> ReactionChamberGameTests::focusFailure);
        registry.register("reaction_missing_power_reaches_deadline",() -> ReactionChamberGameTests::powerFailure);
        registry.register("reaction_dumped_fluid_cannot_produce",() -> ReactionChamberGameTests::dumpFailure);
        registry.register("reaction_redstone_and_structure_pause",() -> ReactionChamberGameTests::pause);
        registry.register("reaction_output_overflow_drops_once",() -> ReactionChamberGameTests::overflow);
        registry.register("reaction_break_refunds_reserved_input_once",() -> ReactionChamberGameTests::breakMaster);
        registry.register("reaction_low_risk_breaks_glass_and_spills_fluid",() -> h -> explosion(h,false));
        registry.register("reaction_medium_risk_explodes",() -> h -> explosion(h,true));
        registry.register("reaction_menu_security_shiftclick_and_data",() -> ReactionChamberGameTests::menu);
        registry.register("reaction_recipe_codecs",() -> ReactionChamberGameTests::codecs);
        registry.register("reaction_no_power_option",() -> ReactionChamberGameTests::optionalPower);
        registry.register("reaction_bucket_transfer",() -> ReactionChamberGameTests::bucket);
        registry.register("reaction_missing_controller_disables_cached_ports",() -> ReactionChamberGameTests::missingController);
        registry.register("reaction_titanium_hopper_furnace_chain",() -> ReactionChamberGameTests::titaniumChain);
        registry.register("reaction_invalid_saved_fluid_recovers_input",() -> ReactionChamberGameTests::invalidSavedFluid);
        if(Boolean.getBoolean("techguns.chemistryTest")) {
            registry.register("chem_optional_reaction_redstone",() -> h -> production(h,new Case("external_redstone","minecraft:diamond","rcheatray","minecraft:water",4,3,5,100000,"laserfocus",1,"",0,4000)));
            registry.register("chem_optional_reaction_ender",() -> h -> production(h,new Case("external_ender","minecraft:nether_star","rcheatray","minecraft:water",4,8,7,500000,"antigravcore",1,"",0,4000)));
        }
    }
    static ItemStack item(String id,int amount) { return ChemLabGameTests.item(id,amount); }
    private static ReactionChamberBlockEntity place(GameTestHelper h,Direction inward,boolean form) {
        for(var part:ReactionStructure.parts(h.absolutePos(POS),inward)) h.getLevel().setBlock(part.pos(),ReactionContent.block(part.kind()).defaultBlockState(),3);
        var chamber=h.getBlockEntity(POS,ReactionChamberBlockEntity.class);
        if(form) h.assertTrue(chamber.form(inward.getOpposite(),WeaponGameTests.player(h)),"All 36 source blocks form a chamber");
        return chamber;
    }
    private static ReactionChamberBlockEntity place(GameTestHelper h) { return place(h,Direction.SOUTH,true); }
    private static int charge(ReactionChamberBlockEntity chamber,int amount) { try(Transaction tx=Transaction.openRoot()) { int n=chamber.energy().insert(amount,tx); tx.commit(); return n; } }
    private static void settings(ReactionChamberBlockEntity chamber,Player player,int intensity,int level) {
        while(chamber.data.get(4)!=intensity) if(!chamber.button(player,chamber.data.get(4)<intensity ? 0 : 1)) throw new AssertionError("Intensity button failed");
        while(chamber.data.get(3)!=level) if(!chamber.button(player,chamber.data.get(3)<level ? 4 : 5)) throw new AssertionError("Liquid button failed");
    }
    private static void supply(GameTestHelper h,ReactionChamberBlockEntity chamber,Case c) {
        settings(chamber,WeaponGameTests.player(h),c.intensity,c.level);
        chamber.setItem(0,item(c.input,1)); chamber.setItem(1,item(c.focus,1));
        var fluid=ChemLabGameTests.fluid(c.fluid,c.level*1000);
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(chamber.tank().insert(0,FluidResource.of(fluid),fluid.getAmount(),tx),fluid.getAmount(),"Fill exact reaction level"); tx.commit(); }
    }
    private static void tick(GameTestHelper h,ReactionChamberBlockEntity chamber,int count) {
        for(int i=0;i<count;i++) ReactionChamberBlockEntity.tick(h.getLevel(),chamber.getBlockPos(),chamber.getBlockState(),chamber);
    }
    private static void assertItem(GameTestHelper h,ItemStack actual,String id,int count) {
        h.assertTrue(actual.is(item(id,1).getItem()),"Correct original reaction product: "+id); h.assertValueEqual(actual.getCount(),count,"Correct product yield");
    }
    private static void production(GameTestHelper h,Case c) {
        var chamber=place(h); supply(h,chamber,c); var player=WeaponGameTests.player(h); long[] supplied={charge(chamber,1000000)};
        if(c.id.startsWith("external_")) {
            var candidate=h.getLevel().getServer().getRecipeManager().recipeMap().byType(ReactionContent.RECIPE.get()).stream().map(r -> r.value())
                    .filter(r -> r.fluid().equals(c.id.endsWith("redstone") ? "redstone" : "ender")).findFirst().orElseThrow();
            h.assertTrue(candidate.fluidMatches(net.minecraft.world.level.material.Fluids.WATER) && !candidate.fluidMatches(net.minecraft.world.level.material.Fluids.LAVA),"Preferred supplied fluid disables lava fallback");
        }
        h.onEachTick(() -> {
            supplied[0]+=charge(chamber,1000000);
            if(chamber.working()) settings(chamber,player,chamber.operation().requiredIntensity(),c.level);
            if(!chamber.getItem(2).isEmpty()) {
                assertItem(h,chamber.getItem(2),c.first,c.count);
                if(c.secondCount>0) assertItem(h,chamber.getItem(3),c.second,c.secondCount);
                h.assertValueEqual(supplied[0]-chamber.energy().getAmountAsLong(),(long)c.checks*c.power,"Power paid only for original 60-tick reaction checks");
                h.assertValueEqual(chamber.tank().stack().getAmount(),c.level*1000-c.consumed,"Fluid consumed on success only");
                h.assertTrue(chamber.getItem(0).isEmpty() && chamber.getItem(1).getCount()==1 && !chamber.working(),"One reagent consumed; focus is reusable");
                h.succeed();
            }
        });
    }
    private static void rotations(GameTestHelper h) {
        for(Direction inward:Direction.Plane.HORIZONTAL) {
            var chamber=place(h,inward,true); int[] counts=new int[4];
            for(var part:ReactionStructure.parts(h.absolutePos(POS),inward)) {
                counts[part.connector()]++;
                h.assertTrue(h.getLevel().getBlockState(part.pos()).getValue(ReactionChamberBlock.FORMED),"Every part changes to its formed state");
                if(part.connector()!=0) h.assertTrue(((ReactionPartBlockEntity)h.getLevel().getBlockEntity(part.pos())).master()==chamber,"Part resolves rotated controller");
            }
            h.assertValueEqual(counts[2],3,"Three lower fluid/item ports"); h.assertValueEqual(counts[3],1,"One top energy port");
            h.assertValueEqual(counts[1],31,"31 ordinary linked blocks"); chamber.unform();
            for(var part:ReactionStructure.parts(h.absolutePos(POS),inward)) h.getLevel().setBlock(part.pos(),Blocks.AIR.defaultBlockState(),3);
        }
        h.succeed();
    }
    private static void incomplete(GameTestHelper h) {
        var chamber=place(h,Direction.SOUTH,false); var player=WeaponGameTests.player(h);
        h.setBlock(POS.south().above(),Blocks.AIR);
        h.assertTrue(!chamber.form(Direction.NORTH,player),"Interior glass is mandatory, despite being inside the finished model");
        h.assertTrue(!chamber.form(Direction.UP,player),"Vertical formation rejected");
        for(var part:ReactionStructure.parts(h.absolutePos(POS),Direction.SOUTH)) if(h.getLevel().getBlockState(part.pos()).getBlock() instanceof ReactionChamberBlock)
            h.assertTrue(!h.getLevel().getBlockState(part.pos()).getValue(ReactionChamberBlock.FORMED),"Failed formation is atomic");
        h.succeed();
    }
    private static void connectors(GameTestHelper h) {
        var chamber=place(h); var player=WeaponGameTests.player(h); settings(chamber,player,5,3);
        for(var part:ReactionStructure.parts(chamber.getBlockPos(),chamber.inward())) {
            var energy=h.getLevel().getCapability(Capabilities.Energy.BLOCK,part.pos(),Direction.DOWN);
            var items=h.getLevel().getCapability(Capabilities.Item.BLOCK,part.pos(),Direction.DOWN);
            var fluids=h.getLevel().getCapability(Capabilities.Fluid.BLOCK,part.pos(),Direction.DOWN);
            h.assertTrue((energy!=null)==(part.connector()==0 || part.connector()==3),"Energy only on controller and top center");
            h.assertTrue((items!=null)==(part.connector()==0 || part.connector()==2),"Items only on controller and three lower ports");
            h.assertTrue((fluids!=null)==(part.connector()==0 || part.connector()==2),"Fluids only on controller and three lower ports");
            if(part.connector()==2) {
                var acid=FluidResource.of(TGFluids.ACID.still.get()); var ore=ItemResource.of(item("ore_titanium",1));
                try(Transaction tx=Transaction.openRoot()) {
                    h.assertValueEqual(items.insert(0,ore,2,tx),2,"Port accepts actual reagent"); h.assertValueEqual(fluids.insert(0,acid,4000,tx),3000,"Port obeys target level");
                    h.assertValueEqual(items.extract(0,ore,2,tx),0,"Cannot automate extraction of inputs");
                }
                h.assertTrue(chamber.getItem(0).isEmpty() && chamber.tank().stack().isEmpty(),"Aborted transfers restore master inventory and tank");
            }
        }
        h.succeed();
    }
    private static void stalePorts(GameTestHelper h) {
        var chamber=place(h); settings(chamber,WeaponGameTests.player(h),5,3);
        var pos=chamber.center().south();
        var items=h.getLevel().getCapability(Capabilities.Item.BLOCK,pos,Direction.DOWN);
        var fluids=h.getLevel().getCapability(Capabilities.Fluid.BLOCK,pos,Direction.DOWN);
        var energy=h.getLevel().getCapability(Capabilities.Energy.BLOCK,chamber.center().above(3),Direction.UP);
        h.getLevel().removeBlock(chamber.center().above(),false);
        try(Transaction tx=Transaction.openRoot()) {
            h.assertValueEqual(items.insert(0,ItemResource.of(item("ore_titanium",1)),1,tx),0,"Old item handle cannot feed a broken chamber");
            h.assertValueEqual(fluids.insert(0,FluidResource.of(TGFluids.ACID.still.get()),1000,tx),0,"Old fluid handle cannot feed a broken chamber");
            h.assertValueEqual(energy.insert(25000,tx),0,"Old energy handle cannot charge a broken chamber"); tx.commit();
        }
        h.assertTrue(!chamber.formed(),"Breaking a slave invalidates controller"); h.succeed();
    }
    private static void tankTarget(GameTestHelper h) {
        var chamber=place(h); var player=WeaponGameTests.player(h); settings(chamber,player,5,4);
        var acid=FluidResource.of(TGFluids.ACID.still.get());
        try(Transaction tx=Transaction.openRoot()) { chamber.tank().insert(0,acid,4000,tx); tx.commit(); }
        settings(chamber,player,5,2);
        try(Transaction tx=Transaction.openRoot()) {
            h.assertValueEqual(chamber.tank().insert(0,acid,1000,tx),0,"Lower target does not allow negative fill");
            h.assertValueEqual(chamber.tank().getAmountAsInt(0),4000,"Lowering target never deletes excess");
            h.assertValueEqual(chamber.tank().extract(0,acid,9000,tx),2000,"Only excess is drainable");
        }
        h.assertValueEqual(chamber.tank().getAmountAsInt(0),4000,"Excess drain rolls back");
        settings(chamber,player,5,0);
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(chamber.tank().extract(0,acid,10000,tx),4000,"Level zero permits complete drain"); tx.commit(); }
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(chamber.tank().extract(0,acid,1000,tx),0,"Empty tank drain is safe"); }
        h.succeed();
    }
    private static void missingController(GameTestHelper h) {
        var chamber=place(h); var port=chamber.center().south();
        var items=h.getLevel().getCapability(Capabilities.Item.BLOCK,port,Direction.DOWN);
        var tag=chamber.saveWithFullMetadata(h.getLevel().registryAccess()); var state=chamber.getBlockState();
        h.getLevel().removeBlockEntity(chamber.getBlockPos());
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(items.insert(0,ItemResource.of(item("ore_titanium",1)),1,tx),0,"Cached port refuses a missing controller"); tx.commit(); }
        var restored=(ReactionChamberBlockEntity)BlockEntity.loadStatic(chamber.getBlockPos(),state,tag,h.getLevel().registryAccess()); h.getLevel().setBlockEntity(restored);
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(items.insert(0,ItemResource.of(item("ore_titanium",1)),1,tx),1,"Restored matching formation reconnects"); tx.commit(); }
        h.assertValueEqual(restored.getItem(0).getCount(),1,"Input reaches restored controller exactly once");
        h.assertTrue(chamber.getItem(0).isEmpty(),"Removed controller instance is never mutated"); h.succeed();
    }
    private static void startChecks(GameTestHelper h) {
        var chamber=place(h); var c=CASES.get(2); supply(h,chamber,c); var player=WeaponGameTests.player(h);
        tick(h,chamber,1); h.assertTrue(!chamber.working(),"Start requires one check's energy"); charge(chamber,25000);
        settings(chamber,player,4,3); tick(h,chamber,1); h.assertTrue(!chamber.working(),"Wrong initial intensity rejected");
        settings(chamber,player,5,2); tick(h,chamber,1); h.assertTrue(!chamber.working(),"Wrong level and excess fluid rejected");
        settings(chamber,player,5,3); chamber.setItem(1,item("rcuvemitter",1)); tick(h,chamber,1); h.assertTrue(!chamber.working(),"Wrong focus rejected");
        chamber.setItem(1,item("rcheatray",1)); chamber.tank().set(0,FluidResource.of(TGFluids.ACID.still.get()),2999); tick(h,chamber,1); h.assertTrue(!chamber.working(),"One missing millibucket prevents start");
        chamber.tank().set(0,FluidResource.of(TGFluids.ACID.still.get()),3000); tick(h,chamber,1); h.assertTrue(chamber.working(),"Exact settings start without charging until the first check");
        h.assertValueEqual(chamber.energy().getAmountAsInt(),25000,"No upfront duplicate energy payment"); h.succeed();
    }
    private static void reload(GameTestHelper h) {
        var chamber=place(h); supply(h,chamber,CASES.getFirst());
        var named=chamber.tank().stack(); named.set(DataComponents.CUSTOM_NAME,Component.literal("Reaction water")); chamber.tank().set(0,FluidResource.of(named),named.getAmount());
        charge(chamber,1000000); tick(h,chamber,85);
        var before=chamber.operation(); var tag=chamber.saveWithFullMetadata(h.getLevel().registryAccess()); var state=chamber.getBlockState();
        h.getLevel().removeBlockEntity(chamber.getBlockPos());
        var restored=(ReactionChamberBlockEntity)BlockEntity.loadStatic(chamber.getBlockPos(),state,tag,h.getLevel().registryAccess()); h.getLevel().setBlockEntity(restored);
        h.assertTrue(restored.operation().equals(before),"Reload preserves elapsed time, completion, next check, intensity and random state");
        h.assertTrue(net.neoforged.neoforge.fluids.FluidStack.matches(restored.tank().stack(),named),"Reload retains fluid identity and data components");
        for(var part:ReactionStructure.parts(restored.getBlockPos(),restored.inward())) if(part.connector()!=0) {
            var old=h.getLevel().getBlockEntity(part.pos()); var saved=old.saveWithFullMetadata(h.getLevel().registryAccess()); var partState=old.getBlockState();
            h.getLevel().removeBlockEntity(part.pos()); h.getLevel().setBlockEntity(BlockEntity.loadStatic(part.pos(),partState,saved,h.getLevel().registryAccess()));
        }
        var player=WeaponGameTests.player(h);
        for(int tick=0;tick<600 && restored.working();tick++) { settings(restored,player,restored.operation().requiredIntensity(),1); tick(h,restored,1); }
        assertItem(h,restored.getItem(2),"minecraft:wheat",1); assertItem(h,restored.getItem(3),"minecraft:wheat_seeds",2);
        h.assertValueEqual(restored.energy().getAmountAsInt(),750000,"Reload doesn't repay or skip a check"); h.succeed();
    }
    private static void focusFailure(GameTestHelper h) {
        var chamber=place(h); supply(h,chamber,CASES.get(6)); charge(chamber,1000000); tick(h,chamber,61); chamber.setItem(1,ItemStack.EMPTY); tick(h,chamber,60);
        h.assertTrue(!chamber.working() && chamber.getItem(2).isEmpty() && chamber.getItem(0).isEmpty(),"Removing focus loses reagent with no output");
        h.assertValueEqual(chamber.tank().stack().getAmount(),3000,"Break-item failure preserves fluid"); h.assertValueEqual(chamber.energy().getAmountAsInt(),500000,"Failed focus check still costs power"); h.succeed();
    }
    private static void invalidSavedFluid(GameTestHelper h) {
        var chamber=place(h); supply(h,chamber,CASES.get(2)); charge(chamber,25000); tick(h,chamber,35);
        var tag=chamber.saveWithFullMetadata(h.getLevel().registryAccess()); tag.remove("consumed_fluid"); var state=chamber.getBlockState();
        h.getLevel().removeBlockEntity(chamber.getBlockPos());
        var restored=(ReactionChamberBlockEntity)BlockEntity.loadStatic(chamber.getBlockPos(),state,tag,h.getLevel().registryAccess()); h.getLevel().setBlockEntity(restored);
        h.assertTrue(!restored.working(),"Incomplete saved recipe cannot resume with a free fluid input");
        tick(h,restored,35); h.assertTrue(restored.getItem(2).isEmpty(),"Recovered reagent restarts the full check timer");
        tick(h,restored,26); assertItem(h,restored.getItem(2),"oretitanium",2);
        h.assertValueEqual(restored.tank().stack().getAmount(),2900,"Recovered operation pays its fluid consumption once"); h.succeed();
    }
    private static void powerFailure(GameTestHelper h) {
        var chamber=place(h); supply(h,chamber,CASES.get(6)); charge(chamber,250000); tick(h,chamber,301);
        h.assertTrue(!chamber.working() && chamber.getItem(2).isEmpty(),"Power shortage fails at the original deadline"); h.assertValueEqual(chamber.tank().stack().getAmount(),3000,"Failed reaction doesn't consume fluid"); h.succeed();
    }
    private static void dumpFailure(GameTestHelper h) {
        var chamber=place(h); supply(h,chamber,CASES.get(2)); charge(chamber,25000); tick(h,chamber,1); chamber.button(WeaponGameTests.player(h),6); tick(h,chamber,60);
        h.assertTrue(chamber.getItem(2).isEmpty() && !chamber.working(),"Dumping reserved fluid cannot create free titanium"); h.succeed();
    }
    private static void pause(GameTestHelper h) {
        var chamber=place(h); supply(h,chamber,CASES.get(2)); charge(chamber,25000); tick(h,chamber,17); var before=chamber.operation(); var player=WeaponGameTests.player(h);
        chamber.button(player,2); tick(h,chamber,100); h.assertTrue(chamber.operation().equals(before),"Redstone disabled freezes clock and randomness");
        chamber.button(player,2); chamber.button(player,2); chamber.unform(); tick(h,chamber,100); h.assertTrue(chamber.operation().equals(before),"Unformed chamber cannot advance or spend energy");
        h.assertTrue(chamber.form(Direction.NORTH,player),"Intact chamber can be reformed"); tick(h,chamber,60); assertItem(h,chamber.getItem(2),"oretitanium",2); h.succeed();
    }
    private static int dropped(GameTestHelper h,String item,BlockPos center) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(center).inflate(6)).stream().filter(e -> e.getItem().is(item(item,1).getItem())).mapToInt(e -> e.getItem().getCount()).sum();
    }
    private static void overflow(GameTestHelper h) {
        var chamber=place(h); supply(h,chamber,CASES.get(2)); charge(chamber,25000); for(int i=2;i<6;i++) chamber.setItem(i,new ItemStack(Items.COBBLESTONE,64)); tick(h,chamber,61);
        h.assertValueEqual(dropped(h,"oretitanium",chamber.getBlockPos()),2,"Full outputs spill original titanium yield");
        h.assertValueEqual(dropped(h,"minecraft:iron_ore",chamber.getBlockPos()),1,"Full outputs spill original byproduct"); tick(h,chamber,70);
        h.assertValueEqual(dropped(h,"oretitanium",chamber.getBlockPos()),2,"Completed operation cannot spill twice"); h.succeed();
    }
    private static void breakMaster(GameTestHelper h) {
        var chamber=place(h); supply(h,chamber,CASES.get(2)); chamber.setItem(0,item("ore_titanium",3)); charge(chamber,25000); tick(h,chamber,12);
        h.getLevel().destroyBlock(chamber.getBlockPos(),true);
        h.assertValueEqual(dropped(h,"ore_titanium",chamber.getBlockPos()),3,"Reserved reagent plus remaining inventory drop exactly once");
        h.assertValueEqual(dropped(h,"oretitanium",chamber.getBlockPos()),0,"Unfinished output never drops");
        h.assertTrue(!h.getLevel().getBlockState(chamber.center().above()).getValue(ReactionChamberBlock.FORMED),"Removing controller unforms glass"); h.succeed();
    }
    private static void explosion(GameTestHelper h,boolean medium) {
        var chamber=place(h); var c=medium ? CASES.get(4) : CASES.getFirst(); supply(h,chamber,c); chamber.setItem(0,item(c.input,2)); charge(chamber,1000000); tick(h,chamber,1);
        BlockPos center=chamber.center().above(); chamber.setItem(1,ItemStack.EMPTY); tick(h,chamber,60);
        h.assertTrue(!chamber.formed() && !chamber.working(),"Fatal reaction clears operation and unforms all parts");
        h.assertValueEqual(chamber.energy().getAmountAsInt(),0,"Rupture empties energy storage"); h.assertTrue(chamber.tank().stack().isEmpty(),"Rupture empties fluid tank");
        h.assertTrue(!h.getLevel().getFluidState(center).isEmpty(),"Rupture spills the original source fluid");
        h.assertValueEqual(dropped(h,c.input,center),1,"Queued reagent survives exactly once; failed reagent is lost");
        if(medium) { h.assertTrue(h.getLevel().getBlockState(center.below()).is(Blocks.GRAVEL),"Medium explosion leaves source gravel below spilled fluid"); h.assertTrue(h.getLevel().getBlockState(center.above()).isAir(),"Upper central glass is removed"); }
        else h.assertTrue(h.getLevel().getBlockState(center.above()).is(ReactionContent.GLASS.get()),"Low rupture preserves upper glass layer");
        h.succeed();
    }
    private static void menu(GameTestHelper h) {
        var chamber=place(h); var player=WeaponGameTests.player(h); chamber.setOwner(player); var menu=new ReactionChamberMenu(51,player.getInventory(),chamber,chamber.data); player.containerMenu=menu;
        h.assertTrue(menu.clickMenuButton(player,0) && menu.clickMenuButton(player,4),"Menu buttons are server-authoritative");
        player.getInventory().setItem(9,item("rcheatray",1)); h.assertValueEqual(menu.quickMoveStack(player,6).getCount(),1,"Shift-click routes focus");
        player.getInventory().setItem(10,item("ore_titanium",2)); h.assertValueEqual(menu.quickMoveStack(player,7).getCount(),2,"Shift-click routes reagent");
        h.assertTrue(!menu.getSlot(2).mayPlace(new ItemStack(Items.DIAMOND)),"No manual insertion in output slots");
        h.assertTrue(menu.clickMenuButton(player,3),"Owner can lock chamber"); var stranger=WeaponGameTests.player(h); stranger.setUUID(UUID.randomUUID());
        h.assertTrue(!chamber.canOpen(stranger) && !menu.clickMenuButton(stranger,0),"Other players cannot control locked chamber");
        h.assertTrue(!menu.clickMenuButton(player,44),"Unknown button rejected");
        charge(chamber,987654); var clientData=new SimpleContainerData(ReactionChamberBlockEntity.DATA_COUNT); var split=new SplitIntContainerData(chamber.data); var decoded=new SplitIntContainerData(clientData);
        for(int i=0;i<split.getCount();i++) decoded.set(i,(short)split.get(i)); h.assertValueEqual(clientData.get(0),987654,"Million-FE values survive signed menu words");
        var clientMenu=new ReactionChamberMenu(51,player.getInventory()); var fluid=ChemLabGameTests.fluid("creeper_acid",1000); fluid.set(DataComponents.CUSTOM_NAME,Component.literal("Named acid"));
        new MachineTanksPayload(52,fluid,fluid).apply(clientMenu); h.assertTrue(clientMenu.fluid().isEmpty(),"Stale menu update rejected");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try { MachineTanksPayload.CODEC.encode(buffer,new MachineTanksPayload(51,fluid,fluid)); MachineTanksPayload.CODEC.decode(buffer).apply(clientMenu); }
        finally { buffer.release(); }
        h.assertTrue(net.neoforged.neoforge.fluids.FluidStack.matches(clientMenu.fluid(),fluid),"Full fluid components survive menu networking");
        player.setPos(player.getX()+30,player.getY(),player.getZ()); h.assertTrue(!menu.clickMenuButton(player,0),"Distant packet rejected"); h.succeed();
    }
    private static void codecs(GameTestHelper h) {
        var recipes=h.getLevel().getServer().getRecipeManager().recipeMap().byType(ReactionContent.RECIPE.get()); h.assertValueEqual(recipes.size(),7,"All live source recipes loaded");
        for(var holder:recipes) {
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try { Recipe.STREAM_CODEC.encode(buffer,holder.value()); var decoded=(ReactionChamberRecipe)Recipe.STREAM_CODEC.decode(buffer);
                h.assertTrue(decoded.rules().equals(holder.value().rules()),"Network recipe preserves reaction parameters"); h.assertTrue(decoded.risk().equals(holder.value().risk()),"Failure risk is synchronized");
            } finally { buffer.release(); }
        }
        h.succeed();
    }
    private static void optionalPower(GameTestHelper h) {
        var chamber=place(h); supply(h,chamber,CASES.get(2)); boolean previous=TGMachineConfig.MACHINES_NEED_NO_POWER.get();
        try { TGMachineConfig.MACHINES_NEED_NO_POWER.set(true); tick(h,chamber,61); assertItem(h,chamber.getItem(2),"oretitanium",2); h.assertValueEqual(chamber.energy().getAmountAsInt(),0,"No-power setting includes reaction start"); }
        finally { TGMachineConfig.MACHINES_NEED_NO_POWER.set(previous); } h.succeed();
    }
    private static void bucket(GameTestHelper h) {
        var chamber=place(h); var player=WeaponGameTests.player(h); settings(chamber,player,5,3); player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET)); h.useBlock(POS,player);
        h.assertValueEqual(chamber.tank().stack().getAmount(),1000,"Real bucket fills target tank"); h.assertTrue(player.getMainHandItem().is(Items.BUCKET),"Bucket returns empty container");
        settings(chamber,player,5,0); h.useBlock(POS,player); h.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET) && chamber.tank().stack().isEmpty(),"Lowering level allows bucket extraction"); h.succeed();
    }
    private static void titaniumChain(GameTestHelper h) {
        var chamber=place(h); supply(h,chamber,CASES.get(2)); charge(chamber,25000);
        h.setBlock(POS.below(),Blocks.HOPPER); h.setBlock(POS.below(2),Blocks.FURNACE);
        var furnace=h.getBlockEntity(POS.below(2),net.minecraft.world.level.block.entity.FurnaceBlockEntity.class); furnace.setItem(1,new ItemStack(Items.COAL));
        h.succeedWhen(() -> {
            assertItem(h,furnace.getItem(2),"ingottitanium",2);
            h.assertValueEqual(chamber.tank().stack().getAmount(),2900,"Ore refinement uses source 100 mB acid");
            h.assertTrue(chamber.getItem(0).isEmpty(),"One raw ore yields two processed ores through actual machine and hopper ticks");
            h.assertValueEqual(chamber.getItem(1).getCount(),1,"Hopper cannot remove reusable focus");
        });
    }
    private ReactionChamberGameTests() {}
}
