package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.machine.*;
import techguns.modern.machine.drill.*;
import techguns.modern.machine.multiblock.*;
import techguns.modern.network.MachineTanksPayload;
import techguns.modern.world.*;

final class OreDrillGameTests {
    private record Fixture(OreDrillBlockEntity drill,Player player,BlockPos target) {}
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var dir:Direction.values()) r.register("drill_tiny_direction_"+dir.getName(),()->h->direction(h,dir));
        for(int i=1;i<=4;i++) { int radius=i; r.register("drill_full_size_"+i,()->h->size(h,radius)); }
        r.register("drill_wrong_structure_rejected",()->OreDrillGameTests::invalid);
        r.register("drill_rod_placement_and_cluster_exclusivity",()->OreDrillGameTests::placement);
        r.register("drill_all_heads_recipes_and_creative_tab",()->OreDrillGameTests::content);
        r.register("drill_all_weighted_outputs_and_boundaries",()->OreDrillGameTests::weights);
        r.register("drill_paid_coal_cycle",()->h->production(h,"coal","oredrillsmall_steel",2057,28));
        r.register("drill_paid_nether_cycle",()->h->production(h,"nether_crystal","oredrillsmall_obsidiansteel",6000,48));
        r.register("drill_insufficient_head_cobblestone",()->OreDrillGameTests::insufficient);
        r.register("drill_no_head_wrong_size_and_pause",()->OreDrillGameTests::pause);
        r.register("drill_solid_fuel_priority_and_rounding",()->OreDrillGameTests::solidFuel);
        r.register("drill_fuel_exhaustion_then_fe",()->OreDrillGameTests::mixedPower);
        r.register("drill_configured_liquid_fuel_value",()->OreDrillGameTests::configuredFuel);
        r.register("drill_lava_bucket_container",()->OreDrillGameTests::fuelContainer);
        r.register("drill_liquid_lava_and_fuel",()->OreDrillGameTests::liquidFuel);
        r.register("drill_fluid_automation_rollback_and_buckets",()->OreDrillGameTests::fluidPorts);
        r.register("drill_saved_random_job_and_formation",()->OreDrillGameTests::reload);
        r.register("drill_output_overflow_ejected_once",()->OreDrillGameTests::overflow);
        r.register("drill_item_ports_hopper_and_no_head_extraction",()->OreDrillGameTests::itemPorts);
        r.register("drill_real_menu_security_redstone_and_long_data",()->OreDrillGameTests::menu);
        r.register("drill_head_change_cancels_at_source_poll",()->OreDrillGameTests::headChange);
        r.register("drill_break_unforms_without_unearned_output",()->OreDrillGameTests::breakPart);
        r.register("drill_zero_power_and_free_power",()->OreDrillGameTests::freePower);
        r.register("drill_empty_oil_cluster_fallback_timing",()->OreDrillGameTests::emptyOil);
        r.register("drill_connected_cluster_size_and_material_boundary",()->OreDrillGameTests::connected);
        r.register("drill_removed_controller_rejects_cached_ports",()->OreDrillGameTests::removed);
        r.register("drill_tanks_payload_roundtrip",()->OreDrillGameTests::payload);
        if(Boolean.getBoolean("techguns.chemistryTest")) {
            r.register("chem_optional_drill_oil_saved_output",()->h->oil(h,false));
            r.register("chem_optional_drill_oil_full_tank",()->h->oil(h,true));
            r.register("chem_optional_drill_extra_ore_tags",()->OreDrillGameTests::optionalOres);
        }
        OreDrillWorldGameTests.register(r);
    }
    private static OreDrillRules.Size tiny() { return new OreDrillRules.Size(0,1,0); }
    private static ItemStack head(String id) { return TGContent.MATERIALS.get(id).toStack(); }
    private static Block cluster(String type) { return OreClusterContent.BLOCKS.get("ore_cluster_"+type).get(); }
    private static Fixture fixture(GameTestHelper h,OreDrillRules.Size size,Direction dir,String cluster,boolean form,boolean caps) {
        var l=h.getLevel(); var origin=h.absolutePos(new BlockPos(5,0,5)).atY(160);
        for(var c:OreDrillRules.air(size)) l.setBlock(OreDrillStructure.position(origin,dir,c),Blocks.AIR.defaultBlockState(),2);
        for(var c:OreDrillRules.parts(size)) l.setBlock(OreDrillStructure.position(origin,dir,c),OreDrillContent.BLOCKS.get(c.kind()).get().defaultBlockState(),3);
        for(var c:OreDrillRules.endCap(size)) l.setBlock(OreDrillStructure.position(origin,dir,c),(caps?OreDrillContent.BLOCKS.get("scaffold").get():Blocks.AIR).defaultBlockState(),3);
        var target=origin.relative(dir,size.length()+1); l.setBlock(target,cluster(cluster).defaultBlockState(),3);
        var p=WeaponGameTests.player(h); p.setPos(Vec3.atCenterOf(origin).add(0,1,2));
        var d=(OreDrillBlockEntity)l.getBlockEntity(origin); d.setOwner(p);
        if(form) h.assertTrue(d.form(p),"Source drill forms"); return new Fixture(d,p,target);
    }
    private static Fixture fixture(GameTestHelper h) { return fixture(h,tiny(),Direction.UP,"coal",true,false); }
    private static void tick(OreDrillBlockEntity d,int count) { for(int i=0;i<count;i++) OreDrillBlockEntity.tick(d.getLevel(),d.getBlockPos(),d.getBlockState(),d); }
    private static void charge(OreDrillBlockEntity d,int amount) { try(Transaction tx=Transaction.openRoot()) { if(d.energy().insert(amount,tx)!=amount) throw new AssertionError("Fixture capacity"); tx.commit(); } }
    private static int outputs(OreDrillBlockEntity d) { int n=0; for(int i=2;i<11;i++) n+=d.getItem(i).getCount(); return n; }
    private static void direction(GameTestHelper h,Direction dir) {
        var f=fixture(h,tiny(),dir,"coal",true,false); var d=f.drill;
        h.assertValueEqual(d.drillDirection(),dir,"Original six-axis detection"); h.assertValueEqual(d.formation().status(),1,"Live assembly complete");
        var part=(OreDrillPart)h.getLevel().getBlockEntity(d.getBlockPos().relative(dir)); h.assertTrue(part.master()==d,"Rod links to exact controller instance");
        h.assertTrue(h.getLevel().getCapability(Capabilities.Energy.BLOCK,part.getBlockPos(),dir)==null,"Original slave has no energy port");
        d.setItem(0,head("oredrillsmall_steel")); charge(d,28); tick(d,2); h.assertValueEqual(d.data.get(1),1,"One paid tick in every direction"); h.succeed();
    }
    private static void size(GameTestHelper h,int radius) {
        var shape=new OreDrillRules.Size(radius,2*radius-1,radius-1); var f=fixture(h,shape,Direction.UP,"uranium",true,true); var d=f.drill;
        h.assertValueEqual(d.size(),shape,"Detected dimensions"); h.assertValueEqual(d.formation().status(),1,"Whole frame, engine and scaffold assembly");
        for(var c:OreDrillRules.endCap(shape)) { var pos=OreDrillStructure.position(d.getBlockPos(),Direction.UP,c); h.assertTrue(h.getLevel().getBlockState(pos).getValue(OreDrillBlock.END_CAP),"Optional end scaffold hidden after formation"); }
        for(var spec:OreDrillCatalog.HEADS) { var stack=head(spec.id()); h.assertValueEqual(d.acceptsHead(stack),spec.size()==shape.headSize(),"Correct source head size in menu"); d.setItem(0,stack); h.assertValueEqual(d.headLevel(),spec.size()==shape.headSize()?spec.level():0,"Effective head tier"); }
        d.formation().unform(); for(var c:OreDrillRules.parts(shape)) h.assertTrue(!h.getLevel().getBlockState(OreDrillStructure.position(d.getBlockPos(),Direction.UP,c)).getValue(MachineFormation.FORMED),"Unform restores all block states"); h.succeed();
    }
    private static void invalid(GameTestHelper h) {
        var shape=new OreDrillRules.Size(2,3,1); var f=fixture(h,shape,Direction.UP,"coal",false,false); var l=h.getLevel();
        var frame=OreDrillRules.parts(shape).stream().filter(c->c.kind().equals("frame")).findFirst().orElseThrow(); var pos=OreDrillStructure.position(f.drill.getBlockPos(),Direction.UP,frame);
        l.setBlock(pos,Blocks.AIR.defaultBlockState(),3); h.assertTrue(!f.drill.form(f.player),"Missing frame rejected, no partial formation");
        l.setBlock(pos,OreDrillContent.BLOCKS.get("frame").get().defaultBlockState(),3);
        var obstruction=OreDrillStructure.position(f.drill.getBlockPos(),Direction.UP,OreDrillRules.air(shape).getFirst()); l.setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
        h.assertTrue(!f.drill.form(f.player),"Internal drill clearance required"); l.setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);
        var second=f.drill.getBlockPos().east(); l.setBlock(second,OreDrillContent.BLOCKS.get("rod").get().defaultBlockState(),3); h.assertTrue(!f.drill.form(f.player),"Two possible axes rejected"); l.setBlock(second,Blocks.AIR.defaultBlockState(),3);
        h.assertTrue(f.drill.form(f.player),"Repairing actual faults permits formation"); h.succeed();
    }
    private static void placement(GameTestHelper h) {
        var l=h.getLevel(); var base=h.absolutePos(new BlockPos(3,0,3)).atY(160); var p=WeaponGameTests.player(h); p.setPos(Vec3.atCenterOf(base).add(2,0,0));
        var rod=OreDrillContent.BLOCKS.get("rod").get(); l.setBlock(base,Blocks.STONE.defaultBlockState(),3); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(rod));
        var hit=new BlockHitResult(Vec3.atCenterOf(base).add(0,.5,0),Direction.UP,base,false);
        rod.asItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit)); h.assertTrue(l.getBlockState(base.above()).isAir(),"Cannot begin rods on ordinary terrain");
        l.setBlock(base,cluster("coal").defaultBlockState(),3); rod.asItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit)); h.assertTrue(l.getBlockState(base.above()).is(rod),"First rod really places on a cluster");
        h.assertTrue(!OreDrillStructure.rodPlacementAllowed(l,base.east()),"Second access to same connected cluster rejected");
        l.setBlock(base.above(),rod.defaultBlockState().setValue(MachineFormation.FORMED,true),3); h.assertTrue(!OreDrillStructure.rodPlacementAllowed(l,base.east()),"Formed rod cannot be bypassed"); h.succeed();
    }
    private static void content(GameTestHelper h) {
        var tab=TGContent.TAB.get(); tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS,true,h.getLevel().registryAccess()));
        var shown=tab.getDisplayItems();
        for(var b:OreClusterContent.BLOCKS.values()) h.assertTrue(shown.stream().anyMatch(s->s.is(b.get().asItem())),"Previous cluster omission fixed in actual creative tab");
        for(var b:OreDrillContent.BLOCKS.values()) h.assertTrue(shown.stream().anyMatch(s->s.is(b.get().asItem())),"Every source part discoverable");
        for(var spec:OreDrillCatalog.HEADS) { var stack=head(spec.id()); h.assertValueEqual(stack.getMaxStackSize(),1,"Original one-head stack"); h.assertTrue(shown.stream().anyMatch(s->s.is(stack.getItem())),"Every head discoverable"); }
        var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,List.of(ChemLabGameTests.item("ingotsteel",1),ChemLabGameTests.item("platesteel",1),ChemLabGameTests.item("ingotsteel",1),ChemLabGameTests.item("ingotsteel",1),ChemLabGameTests.item("platesteel",1),ChemLabGameTests.item("ingotsteel",1),ItemStack.EMPTY,ChemLabGameTests.item("ingotsteel",1),ItemStack.EMPTY));
        var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,h.getLevel()).orElseThrow(); h.assertTrue(recipe.value().assemble(input).is(head("oredrillsmall_steel").getItem()),"Original five ingots plus two plates craft steel head"); h.succeed();
    }
    private static void weights(GameTestHelper h) {
        for(var v:OreClusters.ALL) {
            var entries=ClusterOutputs.entries(v.type()); if(entries.isEmpty()) { h.assertTrue(v.type().equals("oil"),"Only optional oil missing in baseline"); continue; }
            var counts=new HashMap<String,Integer>(); int total=entries.stream().mapToInt(ClusterOutputs.Output::weight).sum();
            for(int roll=0;roll<total;roll++) { var chosen=ClusterOutputs.select(entries,roll); String id=chosen.item().isEmpty()?chosen.fluid().getFluid().toString():BuiltInRegistries.ITEM.getKey(chosen.item().getItem()).toString(); counts.merge(id,1,Integer::sum); }
            for(var entry:entries) { String id=entry.item().isEmpty()?entry.fluid().getFluid().toString():BuiltInRegistries.ITEM.getKey(entry.item().getItem()).toString(); h.assertValueEqual(counts.get(id),entry.weight(),"Exact strict weighted boundary "+id); }
        }
        var crystal=ClusterOutputs.entries("nether_crystal"); h.assertTrue(ClusterOutputs.select(crystal,49).item().is(Items.NETHER_QUARTZ_ORE),"Quartz last ticket"); h.assertTrue(ClusterOutputs.select(crystal,50).item().is(Items.GLOWSTONE),"No inclusive mixture bug in ore output"); h.assertTrue(ClusterOutputs.select(crystal,90).item().is(Items.BLAZE_ROD),"Blaze rod boundary"); h.succeed();
    }
    private static void production(GameTestHelper h,String type,String head,int duration,int power) {
        var f=fixture(h,tiny(),Direction.UP,type,true,false); var d=f.drill; d.setItem(0,head(head)); charge(d,duration*power); tick(d,1);
        h.assertValueEqual(d.data.get(2),duration,"Original production duration"); h.assertValueEqual(d.data.get(3),power,"Original power rate");
        tick(d,duration-1); h.assertValueEqual(outputs(d),0,"No early output"); tick(d,1); h.assertValueEqual(outputs(d),1,"One source resource per cycle"); h.assertValueEqual(d.energy().getAmountAsInt(),0,"Exact paid energy");
        h.assertTrue(h.getLevel().getBlockState(f.target).is(cluster(type)),"Infinite cluster unchanged"); h.assertTrue(d.getItem(0).is(head(head).getItem()),"Head never consumed");
        var list=ClusterOutputs.entries(type); h.assertTrue(list.stream().anyMatch(e->e.item().is(d.getItem(2).getItem())),"Actual original resource output"); h.succeed();
    }
    private static void insufficient(GameTestHelper h) {
        var d=fixture(h,tiny(),Direction.UP,"nether_crystal",true,false).drill; d.setItem(0,head("oredrillsmall_steel")); charge(d,9600); tick(d,401);
        h.assertTrue(d.getItem(2).is(Items.COBBLESTONE),"Low tier produces cobblestone"); h.assertValueEqual(d.getItem(2).getCount(),1,"Twenty-second fallback"); h.assertValueEqual(d.energy().getAmountAsInt(),0,"400 times 24 FE"); h.succeed();
    }
    private static void pause(GameTestHelper h) {
        var d=fixture(h).drill; charge(d,280); tick(d,30); h.assertTrue(!d.working(),"No head cannot start");
        d.setItem(0,head("oredrillmedium_carbon")); tick(d,30); h.assertTrue(!d.working(),"Wrong size head cannot start even through automation");
        d.setItem(0,head("oredrillsmall_steel")); tick(d,51); h.assertValueEqual(d.data.get(1),10,"Only ten paid ticks; no-power attempts pause"); charge(d,28); tick(d,1); h.assertValueEqual(d.data.get(1),11,"Resumes same operation"); h.succeed();
    }
    private static void solidFuel(GameTestHelper h) {
        var d=fixture(h).drill; d.setItem(0,head("oredrillsmall_steel")); d.setItem(1,new ItemStack(Items.COAL)); charge(d,100); tick(d,11);
        h.assertTrue(d.getItem(1).isEmpty(),"One fuel consumed"); h.assertValueEqual(d.data.get(4),1590,"Original minimum one burn-time unit per powered tick"); h.assertValueEqual(d.data.get(5),1600,"Native coal burn time"); h.assertValueEqual(d.energy().getAmountAsInt(),100,"Internal fuel precedes FE"); h.succeed();
    }
    private static void fuelContainer(GameTestHelper h) {
        var d=fixture(h).drill; d.setItem(0,head("oredrillsmall_steel")); d.setItem(1,new ItemStack(Items.LAVA_BUCKET)); tick(d,2);
        h.assertTrue(d.getItem(2).is(Items.BUCKET) && d.getItem(1).isEmpty(),"Original lava bucket remainder sent to output"); h.assertValueEqual(d.data.get(4),19999,"Bucket burns for twenty thousand fuel units"); h.succeed();
    }
    private static void mixedPower(GameTestHelper h) {
        double factor=OreDrillConfig.FUEL.get(); var d=fixture(h).drill;
        try {
            OreDrillConfig.FUEL.set(1.0); d.setItem(0,head("oredrillsmall_steel")); d.setItem(1,new ItemStack(Items.COAL)); charge(d,24);
            tick(d,59); h.assertValueEqual(d.data.get(1),58,"Coal pays 1596, four buffered units plus 24 FE pay next tick");
            h.assertValueEqual(d.data.get(4),0,"Remaining buffer consumed"); h.assertValueEqual(d.energy().getAmountAsInt(),0,"FE pays only remainder"); tick(d,1); h.assertValueEqual(d.data.get(1),58,"Next tick pauses");
        } finally { OreDrillConfig.FUEL.set(factor); } h.succeed();
    }
    private static void configuredFuel(GameTestHelper h) {
        var fuels=ChemicalRules.FUELS.get(); var d=fixture(h).drill;
        try {
            ChemicalRules.FUELS.set(List.of("minecraft:water")); d.setItem(0,head("oredrillsmall_steel")); d.tanks().set(0,FluidResource.of(Fluids.WATER),500); tick(d,2);
            h.assertValueEqual(d.data.get(4),49999,"Configured fuel gives 100 burn units per mB"); h.assertTrue(d.tanks().stack(0).isEmpty(),"Partial configured fluid consumed once");
        } finally { ChemicalRules.FUELS.set(fuels); } h.succeed();
    }
    private static void liquidFuel(GameTestHelper h) {
        var d=fixture(h).drill; d.setItem(0,head("oredrillsmall_steel")); d.tanks().set(0,FluidResource.of(Fluids.LAVA),500); tick(d,2);
        h.assertValueEqual(d.data.get(4),9999,"Partial bucket uses twenty burn units per mB"); h.assertTrue(d.tanks().stack(0).isEmpty(),"Only available lava drained");
        h.succeed();
    }
    private static void fluidPorts(GameTestHelper h) {
        var f=fixture(h); var d=f.drill; var l=h.getLevel(); var port=l.getCapability(Capabilities.Fluid.BLOCK,d.getBlockPos(),Direction.NORTH);
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(port.insert(0,FluidResource.of(Fluids.LAVA),1000,tx),1000,"Input accepts fluid"); }
        h.assertTrue(d.tanks().stack(0).isEmpty(),"Aborted fill rolls back");
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(port.insert(1,FluidResource.of(Fluids.LAVA),1000,tx),0,"Output cannot fill externally"); port.insert(0,FluidResource.of(Fluids.LAVA),1000,tx); tx.commit(); }
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(port.extract(0,FluidResource.of(Fluids.LAVA),1000,tx),0,"Source handler never drains input"); tx.commit(); }
        d.tanks().set(1,FluidResource.of(Fluids.WATER),1000); f.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BUCKET));
        h.assertTrue(net.neoforged.neoforge.transfer.fluid.FluidUtil.interactWithFluidHandler(f.player,InteractionHand.MAIN_HAND,d.getBlockPos(),d.fluidAutomation(),null),"Real empty bucket drains output");
        h.assertTrue(f.player.getMainHandItem().is(Items.WATER_BUCKET) && d.tanks().stack(1).isEmpty(),"Bucket content and tank conserved"); h.succeed();
    }
    private static OreDrillBlockEntity reload(GameTestHelper h,OreDrillBlockEntity d) {
        var l=h.getLevel(); var tag=d.saveWithFullMetadata(l.registryAccess()); var state=d.getBlockState(); l.removeBlockEntity(d.getBlockPos());
        var restored=(OreDrillBlockEntity)BlockEntity.loadStatic(d.getBlockPos(),state,tag,l.registryAccess()); l.setBlockEntity(restored); return restored;
    }
    private static void reload(GameTestHelper h) {
        var d=fixture(h).drill; d.setItem(0,head("oredrillsmall_steel")); charge(d,57596); tick(d,78);
        var saved=d.saveWithFullMetadata(h.getLevel().registryAccess()); var restored=reload(h,d);
        h.assertValueEqual(restored.data.get(1),77,"Progress survives disk roundtrip"); h.assertValueEqual(restored.formation().status(),1,"Formation UUID reconnects rod");
        h.assertValueEqual(restored.saveWithFullMetadata(h.getLevel().registryAccess()).getCompoundOrEmpty("pending"),saved.getCompoundOrEmpty("pending"),"Chosen weighted output not rerolled");
        tick(restored,1980); h.assertValueEqual(outputs(restored),1,"Only remaining ticks produce one output"); h.assertValueEqual(restored.energy().getAmountAsInt(),0,"No extra energy after reload"); h.succeed();
    }
    private static void overflow(GameTestHelper h) {
        var d=fixture(h,tiny(),Direction.UP,"nether_crystal",true,false).drill; d.setItem(0,head("oredrillsmall_steel"));
        for(int i=2;i<11;i++) d.setItem(i,new ItemStack(Items.STONE,64)); charge(d,9600); tick(d,401);
        int dropped=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(d.getBlockPos()).inflate(2)).stream().filter(e->e.getItem().is(Items.COBBLESTONE)).mapToInt(e->e.getItem().getCount()).sum();
        h.assertValueEqual(dropped,1,"Original full inventory ejects one item at controller"); h.assertValueEqual(outputs(d),576,"Existing inventory untouched"); tick(d,100); h.assertValueEqual(d.energy().getAmountAsInt(),0,"Unpowered next job cannot duplicate output"); h.succeed();
    }
    private static void itemPorts(GameTestHelper h) {
        var d=fixture(h).drill; var port=d.automation();
        try(Transaction tx=Transaction.openRoot()) {
            h.assertValueEqual(port.insert(0,ItemResource.of(head("oredrillsmall_steel")),5,tx),1,"Only one single-stack head fits");
            h.assertValueEqual(port.insert(1,ItemResource.of(new ItemStack(Items.COAL)),1,tx),1,"Fuel port accepts coal");
            h.assertValueEqual(port.insert(2,ItemResource.of(new ItemStack(Items.STONE)),1,tx),0,"Output insertion denied"); tx.commit();
        }
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(port.extract(0,ItemResource.of(d.getItem(0)),1,tx),0,"No automated head extraction"); h.assertValueEqual(port.extract(1,ItemResource.of(d.getItem(1)),1,tx),0,"No automated fuel extraction"); tx.commit(); }
        d.setItem(2,new ItemStack(Items.DIAMOND)); var below=d.getBlockPos().below(); h.getLevel().setBlock(below,Blocks.HOPPER.defaultBlockState(),3);
        h.runAfterDelay(12,()->{ var hopper=(HopperBlockEntity)h.getLevel().getBlockEntity(below); h.assertTrue(d.getItem(2).isEmpty() && java.util.stream.IntStream.range(0,5).anyMatch(i->hopper.getItem(i).is(Items.DIAMOND)),"Real hopper extracts output only"); h.assertTrue(!d.getItem(0).isEmpty(),"Hopper leaves installed head"); h.succeed(); });
    }
    private static void menu(GameTestHelper h) {
        var f=fixture(h); var d=f.drill; var p=f.player; var menu=new OreDrillMenu(64,p.getInventory(),d,d.data); p.containerMenu=menu;
        p.getInventory().setItem(9,head("oredrillsmall_steel")); h.assertTrue(!menu.quickMoveStack(p,11).isEmpty(),"Player shift-click inserts fitting head"); h.assertTrue(menu.quickMoveStack(p,0).isEmpty(),"Original head shift-click extraction disabled");
        p.getInventory().setItem(9,new ItemStack(Items.COAL,2)); menu.quickMoveStack(p,11); h.assertValueEqual(d.getItem(1).getCount(),2,"Fuel shift-click goes to fuel slot");
        charge(d,500000); var split=new SplitIntContainerData(d.data); var client=new SimpleContainerData(OreDrillBlockEntity.DATA_COUNT); var joined=new SplitIntContainerData(client);
        for(int i=0;i<split.getCount();i++) joined.set(i,split.get(i)); h.assertValueEqual(client.get(0),500000,"Menu energy exceeds signed short without truncation");
        h.assertTrue(menu.clickMenuButton(p,2),"Redstone high selected"); tick(d,10); h.assertTrue(!d.working(),"High mode pauses without signal");
        h.getLevel().setBlock(d.getBlockPos().east(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3); tick(d,2); h.assertValueEqual(d.data.get(1),1,"High signal powers processing");
        h.assertTrue(menu.clickMenuButton(p,3),"Owner can lock menu"); var other=WeaponGameTests.player(h); other.setPos(p.position()); h.assertTrue(!d.canOpen(other),"Private machine rejects another player");
        p.setPos(p.position().add(20,0,0)); h.assertTrue(!menu.clickMenuButton(p,2),"Remote button injection rejected"); h.succeed();
    }
    private static void headChange(GameTestHelper h) {
        var d=fixture(h).drill; d.setItem(0,head("oredrillsmall_steel")); charge(d,10000); tick(d,2); d.setItem(0,ItemStack.EMPTY);
        h.runAfterDelay(25,()->{ h.assertTrue(!d.working() && d.data.get(1)==0,"Source twenty-tick head poll cancels incomplete operation"); h.assertValueEqual(outputs(d),0,"No output earned with missing head"); h.succeed(); });
    }
    private static void breakPart(GameTestHelper h) {
        var f=fixture(h); var d=f.drill; d.setItem(0,head("oredrillsmall_steel")); charge(d,2800); tick(d,11);
        h.getLevel().destroyBlock(d.getBlockPos().above(),false); tick(d,1);
        h.assertTrue(!d.formed() && !d.working(),"Breaking a linked rod unforms and cancels"); h.assertValueEqual(d.energy().getAmountAsInt(),2520,"Already spent energy is not refunded"); h.assertValueEqual(outputs(d),0,"Breaking cannot obtain pending output"); h.assertTrue(h.getLevel().getBlockState(f.target).is(cluster("coal")),"Cluster persists"); h.succeed();
    }
    private static void freePower(GameTestHelper h) {
        var d=fixture(h).drill; d.setItem(0,head("oredrillsmall_steel")); double before=OreDrillConfig.POWER.get(); boolean free=TGMachineConfig.MACHINES_NEED_NO_POWER.get();
        try { OreDrillConfig.POWER.set(0.0); tick(d,11); h.assertValueEqual(d.data.get(1),10,"Zero-power config operates"); h.assertValueEqual(d.data.get(4),0,"No negative fuel buffer for zero power");
            OreDrillConfig.POWER.set(before); var other=reload(h,d); var tag=other.saveWithFullMetadata(h.getLevel().registryAccess()); tag.putInt("power",28);
            h.getLevel().removeBlockEntity(other.getBlockPos()); var restored=(OreDrillBlockEntity)BlockEntity.loadStatic(other.getBlockPos(),other.getBlockState(),tag,h.getLevel().registryAccess()); h.getLevel().setBlockEntity(restored);
            TGMachineConfig.MACHINES_NEED_NO_POWER.set(true); tick(restored,1); h.assertValueEqual(restored.data.get(1),11,"Global free-power setting bypasses positive cost too"); }
        finally { OreDrillConfig.POWER.set(before); TGMachineConfig.MACHINES_NEED_NO_POWER.set(free); } h.succeed();
    }
    private static void emptyOil(GameTestHelper h) {
        var d=fixture(h,tiny(),Direction.UP,"oil",true,false).drill; d.setItem(0,head("oredrillsmall_obsidiansteel")); tick(d,1);
        h.assertValueEqual(d.data.get(2),6000,"Empty cluster keeps computed source duration"); h.assertValueEqual(d.data.get(3),24,"Empty oil uses cobblestone fallback power"); h.succeed();
    }
    private static void connected(GameTestHelper h) {
        var f=fixture(h,new OreDrillRules.Size(1,3,0),Direction.UP,"coal",true,false); var l=h.getLevel();
        l.setBlock(f.target.east(),cluster("coal").defaultBlockState(),3); l.setBlock(f.target.east(2),cluster("coal").defaultBlockState(),3); l.setBlock(f.target.east(3),cluster("coal").defaultBlockState(),3); l.setBlock(f.target.east(4),cluster("coal").defaultBlockState(),3);
        h.assertValueEqual(OreDrillStructure.connected(l,f.target,4),4,"Connected size capped by length"); l.setBlock(f.target.east(2),cluster("common_metal").defaultBlockState(),3);
        h.assertValueEqual(OreDrillStructure.connected(l,f.target,4),2,"Different cluster variant breaks connectivity"); f.drill.setItem(0,head("oredrillmedium_steel")); tick(f.drill,1); h.assertValueEqual(f.drill.data.get(2),900,"Two cells times effective tier two gives eighty ores/hour"); h.succeed();
    }
    private static void removed(GameTestHelper h) {
        var d=fixture(h).drill; var energy=d.energy(); var items=d.automation(); var fluids=d.fluidAutomation(); d.tanks().set(1,FluidResource.of(Fluids.WATER),1000); h.getLevel().removeBlockEntity(d.getBlockPos());
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(energy.insert(100,tx),0,"Removed cached energy receiver rejected"); h.assertValueEqual(items.insert(1,ItemResource.of(new ItemStack(Items.COAL)),1,tx),0,"Removed inventory receiver rejected"); h.assertValueEqual(fluids.extract(1,FluidResource.of(Fluids.WATER),1000,tx),0,"Removed cached fluid output rejected"); h.assertValueEqual(fluids.insert(0,FluidResource.of(Fluids.LAVA),1000,tx),0,"Removed cached fluid input rejected"); tx.commit(); } h.succeed();
    }
    private static void payload(GameTestHelper h) {
        var in=new FluidStack(Fluids.LAVA,15000); var out=new FluidStack(Fluids.WATER,31000); var packet=new MachineTanksPayload(62,in,out);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess()); MachineTanksPayload.CODEC.encode(buffer,packet); var decoded=MachineTanksPayload.CODEC.decode(buffer); buffer.release();
        var menu=new OreDrillMenu(62,WeaponGameTests.player(h).getInventory()); decoded.apply(menu); h.assertTrue(FluidStack.matches(menu.fluid(0),in) && FluidStack.matches(menu.fluid(1),out),"Both full fluid stacks survive packet"); new MachineTanksPayload(63,FluidStack.EMPTY,FluidStack.EMPTY).apply(menu); h.assertValueEqual(menu.fluid(0).getAmount(),15000,"Stale container ID ignored"); h.succeed();
    }
    private static void oil(GameTestHelper h,boolean full) {
        h.assertTrue(ClusterOutputs.hasWorldOil(),"Block-backed oil also enables the original desert location ticket");
        var d=fixture(h,tiny(),Direction.UP,"oil",true,false).drill; d.setItem(0,head("oredrillsmall_obsidiansteel")); d.tanks().set(0,FluidResource.of(Fluids.LAVA),1000);
        if(full) d.tanks().set(1,FluidResource.of(Fluids.WATER),31500);
        tick(d,2001); h.assertValueEqual(d.data.get(3),96,"Original oil power multiplier"); var restored=reload(h,d); tick(restored,4000);
        h.assertTrue(restored.tanks().stack(1).getFluid()==Fluids.WATER,"Test datapack oil representative survives saved operation");
        h.assertValueEqual(restored.tanks().stack(1).getAmount(),full?32000:1000,"Source oil yields one bucket; overflowing tank discards excess");
        h.assertValueEqual(restored.data.get(4),14000,"Saved fuel pays exactly six thousand ticks"); h.assertValueEqual(outputs(restored),0,"No duplicate item output"); h.succeed();
    }
    private static void optionalOres(GameTestHelper h) {
        var rare=ClusterOutputs.entries("rare_metal"); h.assertValueEqual(rare.size(),3,"Lead, tagged osmium and tagged aluminium");
        h.assertTrue(rare.get(1).item().is(Items.CLAY_BALL) && rare.get(2).item().is(Items.BRICK),"Original first tag representatives, not all tag items");
        h.assertValueEqual(ClusterOutputs.entries("shiny_metal").size(),3,"Optional silver participates");
        var gems=ClusterOutputs.entries("common_gem"); h.assertValueEqual(gems.size(),4,"Optional ordinary and charged certus"); h.assertValueEqual(gems.getLast().weight(),5,"Charged certus source weight"); h.succeed();
    }
    private OreDrillGameTests() {}
}
