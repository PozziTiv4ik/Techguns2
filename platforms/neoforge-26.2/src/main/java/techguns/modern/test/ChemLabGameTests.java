package techguns.modern.test;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.fluid.TGFluids;
import techguns.modern.machine.*;

final class ChemLabGameTests {
    private static final BlockPos POS=new BlockPos(4,2,4), OTHER=new BlockPos(8,2,4);
    private record Case(String name,String first,int a,String second,int b,String bottle,int c,String fluid,int amount,String output,int count,int power,boolean swap) {}
    private static final List<Case> CASES=List.of(
        new Case("gunpowder","minecraft:redstone",1,"minecraft:coal",1,"",0,"minecraft:water",250,"minecraft:gunpowder",1,5,true),
        new Case("plastic","rawrubber",1,"minecraft:coal",1,"",0,"minecraft:water",1000,"rawplastic",1,25,true),
        new Case("acid","biomass",1,"minecraft:gunpowder",1,"",0,"minecraft:water",1000,"",0,20,true),
        new Case("tgx","minecraft:gunpowder",1,"minecraft:lapis_lazuli",1,"",0,"minecraft:lava",500,"tgx",1,20,true),
        new Case("fuel","fueltankempty",1,"",0,"",0,"minecraft:lava",500,"fueltank",1,1,false),
        new Case("rocket","rocket",1,"",0,"",0,"minecraft:lava",250,"rocket_high_velocity",1,5,false),
        new Case("carbon","minecraft:diamond",1,"minecraft:blaze_powder",1,"",0,"minecraft:lava",1000,"carbonfibers",2,25,true),
        new Case("rubber","minecraft:oak_log",1,"",0,"",0,"minecraft:water",1000,"rawrubber",1,20,false),
        new Case("bio","biomass",1,"",0,"biotankempty",1,"minecraft:water",500,"biotank",1,1,false),
        new Case("air","minecraft:coal",1,"",0,"compressedairtankempty",1,"minecraft:water",250,"compressedairtank",1,5,true),
        new Case("nether","minecraft:netherrack",1,"minecraft:soul_sand",1,"",0,"minecraft:lava",1000,"nethercharge",4,20,true),
        new Case("uranium","ore_uranium",1,"",0,"",0,"creeper_acid",250,"yellowcake",3,20,false),
        new Case("nuclear","enricheduranium",1,"",0,"nuclearpowercelldepleted",1,"minecraft:water",1000,"nuclearpowercell",1,40,true),
        new Case("slime","minecraft:green_dye",1,"rawrubber",1,"",0,"milk",500,"minecraft:slime_ball",1,25,true),
        new Case("treated_leather","minecraft:leather",2,"minecraft:slime_ball",1,"",0,"creeper_acid",500,"treatedleather",2,20,true),
        new Case("leather","minecraft:rotten_flesh",1,"",0,"",0,"minecraft:water",500,"minecraft:leather",1,15,false),
        new Case("concrete","minecraft:gravel",1,"minecraft:sand",1,"",0,"minecraft:water",250,"minecraft:light_gray_concrete",2,5,false),
        new Case("clay","minecraft:gravel",1,"minecraft:dirt",1,"",0,"minecraft:water",250,"minecraft:clay",2,5,false),
        new Case("rifle_fire","riflerounds",1,"minecraft:blaze_powder",1,"",0,"minecraft:lava",250,"riflerounds_incendiary",1,25,false),
        new Case("pistol_fire","pistolrounds",2,"minecraft:blaze_powder",1,"",0,"minecraft:lava",250,"pistolrounds_incendiary",2,25,false),
        new Case("sniper_fire","sniperrounds",1,"minecraft:blaze_powder",1,"",0,"minecraft:lava",250,"sniperrounds_incendiary",1,25,false),
        new Case("shotgun_fire","shotgunrounds",8,"minecraft:blaze_powder",1,"",0,"minecraft:lava",250,"shotgunrounds_incendiary",8,25,false),
        new Case("pills","minecraft:sugar",4,"minecraft:glistering_melon_slice",1,"minecraft:glass_bottle",1,"milk",1000,"radpills",4,20,true),
        new Case("radaway","minecraft:nether_wart",1,"minecraft:glistering_melon_slice",1,"infusionbag",1,"creeper_acid",250,"radaway",1,25,true));
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        for (Case c:CASES) registry.register("chem_recipe_"+c.name(), () -> h -> production(h,c));
        registry.register("chem_fluid_only_reload_batch", () -> ChemLabGameTests::fluidBatchReload);
        registry.register("chem_fluid_automation_and_rollback", () -> ChemLabGameTests::automation);
        registry.register("chem_output_obstruction_and_power", () -> ChemLabGameTests::obstruction);
        registry.register("chem_real_bucket_interaction", () -> ChemLabGameTests::buckets);
        registry.register("chem_fuel_and_oil_rules", () -> ChemLabGameTests::conditionalFluids);
        registry.register("chem_reject_wrong_amounts_and_slots", () -> ChemLabGameTests::rejectInputs);
        registry.register("chem_logs_to_plastic_hopper_chain", () -> ChemLabGameTests::plasticChain);
        registry.register("chem_acid_to_yellowcake_transfer", () -> ChemLabGameTests::uraniumChain);
        registry.register("chem_recipe_network_codecs", () -> ChemLabGameTests::codecs);
        registry.register("chem_menu_tanks_and_permissions", () -> ChemLabGameTests::menu);
        registry.register("chem_break_returns_item_inputs", () -> ChemLabGameTests::breakInputs);
        if (Boolean.getBoolean("techguns.chemistryTest")) {
            var optional=List.of(
                new Case("dust","minecraft:redstone",1,"minecraft:coal",1,"",0,"minecraft:water",250,"minecraft:gunpowder",1,5,true),
                new Case("oil","rawrubber",1,"",0,"",0,"minecraft:water",500,"rawplastic",1,25,false),
                new Case("biofuel","minecraft:charcoal",4,"biotankempty",1,"",0,"minecraft:water",500,"biotank",1,1,true),
                new Case("bioacid","minecraft:charcoal",4,"minecraft:gunpowder",1,"",0,"minecraft:water",1000,"",0,20,true),
                new Case("fuel_tgx","minecraft:gunpowder",1,"minecraft:lapis_lazuli",1,"",0,"minecraft:lava",250,"tgx",1,20,true),
                new Case("fuel_bottle","",0,"",0,"fueltankempty",1,"minecraft:lava",250,"fueltank",1,1,false),
                new Case("fuel_rocket","rocket",1,"",0,"",0,"minecraft:lava",125,"rocket_high_velocity",1,5,false),
                new Case("neoforge_milk","minecraft:sugar",4,"minecraft:glistering_melon_slice",1,"minecraft:glass_bottle",1,"minecraft:milk",1000,"radpills",4,20,true));
            for (Case c:optional) registry.register("chem_optional_"+c.name(), () -> h -> production(h,c));
        }
    }
    private static Identifier id(String id) { return Identifier.parse(id.contains(":") ? id : "techguns:"+id); }
    static ItemStack item(String id,int count) { return id.isEmpty() ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(id(id)),count); }
    static FluidStack fluid(String id,int amount) { return new FluidStack(BuiltInRegistries.FLUID.getValue(id(id)),amount); }
    static ChemLabBlockEntity place(GameTestHelper h,BlockPos pos) { h.setBlock(pos,TGMachineContent.CHEM_LAB.get()); return h.getBlockEntity(pos,ChemLabBlockEntity.class); }
    static void charge(ChemLabBlockEntity lab,int energy) { try (Transaction tx=Transaction.openRoot()) { lab.energy().insert(energy,tx); tx.commit(); } }
    static void fill(ChemLabBlockEntity lab,String fluid,int amount) { try (Transaction tx=Transaction.openRoot()) {
        var stack=fluid(fluid,amount); if (lab.tanks().insert(0,FluidResource.of(stack),amount,tx)!=amount) throw new AssertionError("Test tank capacity"); tx.commit();
    } }
    private static void supply(ChemLabBlockEntity lab,Case c,boolean swap,int batch) {
        lab.setItem(0,item(swap ? c.second() : c.first(),(swap ? c.b() : c.a())*batch));
        lab.setItem(1,item(swap ? c.first() : c.second(),(swap ? c.a() : c.b())*batch));
        lab.setItem(2,item(c.bottle(),c.c()*batch)); fill(lab,c.fluid(),c.amount()*batch);
    }
    private static void assertOutput(GameTestHelper h,ChemLabBlockEntity lab,Case c,int batch) {
        for(int slot=0;slot<3;slot++) h.assertTrue(lab.getItem(slot).isEmpty(),"Exact original item inputs consumed");
        h.assertTrue(lab.tanks().stack(0).isEmpty(),"Exact original fluid input consumed");
        if (c.output().isEmpty()) {
            h.assertTrue(lab.tanks().stack(1).is(TGFluids.ACID.still.get()),"Acid recipe produces fluid with no item output");
            h.assertValueEqual(lab.tanks().stack(1).getAmount(),1000*batch,"Original acid volume");
            h.assertTrue(lab.getItem(3).isEmpty(),"No fictitious result item for fluid-only operation");
        } else {
            h.assertTrue(lab.getItem(3).is(item(c.output(),1).getItem()),"Original product "+c.output());
            h.assertValueEqual(lab.getItem(3).getCount(),c.count()*batch,"Original product quantity");
        }
    }
    private static void production(GameTestHelper h,Case c) {
        var first=place(h,POS); supply(first,c,false,1); charge(first,c.power()*100);
        for(int slot=0;slot<3;slot++) if (!first.getItem(slot).isEmpty())
            h.assertTrue(first.canPlaceItem(slot,first.getItem(slot)),"Recipe ingredient is accepted by actual slot rules: "+c.name()+"/"+slot);
        var second=c.swap() ? place(h,OTHER) : null;
        if(second!=null) { supply(second,c,true,1); charge(second,c.power()*100); }
        h.runAfterDelay(5, () -> {
            h.assertValueEqual(first.data.get(2),100,"Original operation duration");
            h.assertValueEqual(first.data.get(7),c.power(),"Original per-tick energy");
        });
        h.runAfterDelay(105, () -> {
            assertOutput(h,first,c,1); h.assertValueEqual(first.energy().getAmountAsLong(),0L,"Exact energy cost");
            if(second!=null) { assertOutput(h,second,c,1); h.assertValueEqual(second.energy().getAmountAsLong(),0L,"Swapped operation exact cost"); }
            h.succeed();
        });
    }
    private static void fluidBatchReload(GameTestHelper h) {
        var lab=place(h,POS); supply(lab,CASES.get(2),false,8); lab.setItem(4,item("machinestackupgrade",7)); charge(lab,16000);
        h.runAfterDelay(40, () -> {
            h.assertTrue(lab.working(),"A fluid-only batch remains active");
            h.assertValueEqual(lab.data.get(6),8,"Eight sets limited by eight-bucket input");
            h.assertValueEqual(lab.data.get(7),160,"ChemLab uses linear batch power");
            var tag=lab.saveWithFullMetadata(h.getLevel().registryAccess()); h.getLevel().removeBlockEntity(h.absolutePos(POS));
            var restored=(ChemLabBlockEntity)BlockEntity.loadStatic(h.absolutePos(POS),lab.getBlockState(),tag,h.getLevel().registryAccess());
            h.getLevel().setBlockEntity(restored); h.assertTrue(restored.working(),"Pending fluid survives reload with empty item result");
        });
        h.runAfterDelay(105, () -> {
            var restored=h.getBlockEntity(POS,ChemLabBlockEntity.class); assertOutput(h,restored,CASES.get(2),8);
            h.assertValueEqual(restored.energy().getAmountAsLong(),0L,"Reload does not repay or duplicate energy"); h.succeed();
        });
    }
    private static void automation(GameTestHelper h) {
        var lab=place(h,POS); var water=FluidResource.of(Fluids.WATER); var acid=FluidResource.of(TGFluids.ACID.still.get());
        for (Direction side:Direction.values()) {
            var handler=h.getLevel().getCapability(Capabilities.Fluid.BLOCK,h.absolutePos(POS),side);
            h.assertTrue(handler!=null,"All six sides expose fluid transfer");
            try (Transaction tx=Transaction.openRoot()) {
                h.assertValueEqual(handler.insert(1,water,1000,tx),0,"Automation cannot fill output tank");
                h.assertValueEqual(handler.insert(water,9000,tx),8000,"Input capacity is eight buckets");
                h.assertValueEqual(handler.insert(acid,1000,tx),0,"No fluid mixing");
                h.assertValueEqual(handler.extract(water,8000,tx),0,"Default extraction cannot steal input");
            }
            h.assertTrue(lab.tanks().stack(0).isEmpty(),"Uncommitted transfer rolls back");
        }
        fill(lab,"minecraft:water",8000); lab.tanks().set(1,acid,16000);
        var player=WeaponGameTests.player(h); lab.button(player,0);
        try(Transaction tx=Transaction.openRoot()) {
            h.assertValueEqual(lab.fluids().extract(acid,1000,tx),0,"Input drain mode hides output");
            h.assertValueEqual(lab.fluids().extract(water,250,tx),250,"Input drain mode is effective"); tx.commit();
        }
        h.assertValueEqual(lab.tanks().stack(0).getAmount(),7750,"Partial mB extraction committed");
        var saved=lab.saveWithFullMetadata(h.getLevel().registryAccess());
        var copy=(ChemLabBlockEntity)BlockEntity.loadStatic(h.absolutePos(POS),lab.getBlockState(),saved,h.getLevel().registryAccess());
        h.assertValueEqual(copy.data.get(3),1,"Drain mode persists");
        h.assertValueEqual(copy.tanks().stack(1).getAmount(),16000,"Full output tank persists"); h.succeed();
    }
    private static void obstruction(GameTestHelper h) {
        var lab=place(h,POS); supply(lab,CASES.get(2),false,1); charge(lab,1980);
        h.runAfterDelay(101, () -> {
            h.assertValueEqual(lab.data.get(1),99,"Cannot complete without last 20 FE");
            h.assertTrue(lab.tanks().stack(1).isEmpty(),"No fluid output before final energy payment");
            lab.tanks().set(1,FluidResource.of(TGFluids.MILK.still.get()),1); charge(lab,20);
        });
        h.runAfterDelay(105, () -> {
            h.assertValueEqual(lab.data.get(1),100,"Paid operation waits for compatible output");
            h.assertValueEqual(lab.tanks().stack(1).getAmount(),1,"Existing output is not replaced");
            lab.tanks().set(1,FluidResource.EMPTY,0);
        });
        h.runAfterDelay(108, () -> { assertOutput(h,lab,CASES.get(2),1); h.assertValueEqual(lab.energy().getAmountAsLong(),0L,"Waiting consumes no extra FE"); h.succeed(); });
    }
    private static void buckets(GameTestHelper h) {
        var lab=place(h,POS); var player=WeaponGameTests.player(h); player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));
        h.useBlock(POS,player);
        h.assertValueEqual(lab.tanks().stack(0).getAmount(),1000,"Bucket right-click fills machine input");
        h.assertTrue(player.getMainHandItem().is(Items.BUCKET),"Empty bucket returned in survival");
        lab.tanks().set(1,FluidResource.of(TGFluids.ACID.still.get()),1000); h.useBlock(POS,player);
        h.assertTrue(player.getMainHandItem().is(TGFluids.ACID.bucket.get()),"Output is collected before input interaction");
        h.assertTrue(lab.tanks().stack(1).isEmpty(),"Exactly one bucket extracted");
        lab.tanks().set(0,FluidResource.EMPTY,0); player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.MILK_BUCKET)); h.useBlock(POS,player);
        h.assertTrue(lab.tanks().stack(0).is(TGFluids.MILK.still.get()),"Vanilla drinking bucket supplies recipe milk");
        h.assertTrue(player.getMainHandItem().is(Items.BUCKET),"Vanilla milk container remainder retained"); h.succeed();
    }
    private static void conditionalFluids(GameTestHelper h) {
        var oils=ChemicalRules.OILS.get(); var fuels=ChemicalRules.FUELS.get(); boolean keep=ChemicalRules.KEEP_LAVA.get();
        try {
            ChemicalRules.FUELS.set(List.of("minecraft:lava")); ChemicalRules.OILS.set(List.of("minecraft:water"));
            ChemicalRules.KEEP_LAVA.set(false);
            var level=h.getLevel();
            var input=new ChemLabRecipe.Input(item("minecraft:gunpowder",1),item("minecraft:lapis_lazuli",1),ItemStack.EMPTY,fluid("minecraft:lava",250));
            var recipe=level.getServer().getRecipeManager().getRecipeFor(TGMachineContent.CHEM_LAB_RECIPE.get(),input,level).orElseThrow().value();
            h.assertValueEqual(recipe.fluidInput().orElseThrow().amount(),250,"Configured fuel uses original half-volume recipe");
            h.assertTrue(!ChemicalRules.active("lava_fuel_fallback"),"Fuel availability disables fallback by default");
            ChemicalRules.KEEP_LAVA.set(true); h.assertTrue(ChemicalRules.active("lava_fuel_fallback"),"Original keep-lava setting works");
            var oilInput=new ChemLabRecipe.Input(item("rawrubber",1),ItemStack.EMPTY,ItemStack.EMPTY,fluid("minecraft:water",500));
            var oilRecipe=level.getServer().getRecipeManager().getRecipeFor(TGMachineContent.CHEM_LAB_RECIPE.get(),oilInput,level).orElseThrow().value();
            h.assertValueEqual(oilRecipe.fluidInput().orElseThrow().amount(),500,"Configured oil needs no extra coal and only half a bucket");
            h.assertTrue(!ChemicalRules.active("oils_absent"),"Oil availability replaces fallback plastic recipe");
        } finally { ChemicalRules.OILS.set(oils); ChemicalRules.FUELS.set(fuels); ChemicalRules.KEEP_LAVA.set(keep); }
        h.succeed();
    }
    private static void rejectInputs(GameTestHelper h) {
        var lab=place(h,POS); supply(lab,CASES.get(22),true,1);
        var input=new ChemLabRecipe.Input(lab.getItem(0),lab.getItem(1),lab.getItem(2),lab.tanks().stack(0));
        var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(TGMachineContent.CHEM_LAB_RECIPE.get(),input,h.getLevel()).orElseThrow().value();
        h.assertValueEqual(recipe.counts(input),List.of(1,4,1),"Unequal swapped amounts follow the real slots");
        h.assertTrue(!recipe.matches(new ChemLabRecipe.Input(input.first(),item("minecraft:sugar",3),input.bottle(),input.fluid()),h.getLevel()),"Missing fourth sugar rejected");
        h.assertTrue(!recipe.matches(new ChemLabRecipe.Input(input.first(),input.second(),ItemStack.EMPTY,input.fluid()),h.getLevel()),"Missing flask rejected");
        h.assertTrue(!recipe.matches(new ChemLabRecipe.Input(input.first(),input.second(),input.bottle(),fluid("minecraft:water",1000)),h.getLevel()),"Wrong fluid rejected");
        h.assertTrue(!recipe.matches(new ChemLabRecipe.Input(input.first(),input.second(),input.bottle(),fluid("milk",999)),h.getLevel()),"Missing one mB rejected");
        h.assertTrue(!lab.canPlaceItem(3,item("minecraft:stone",1)),"No insertion in output slot");
        h.assertTrue(!lab.canPlaceItem(4,item("minecraft:stone",1)),"Upgrade validation"); h.succeed();
    }
    private static void plasticChain(GameTestHelper h) {
        var upper=place(h,POS.above(2)); upper.setItem(0,new ItemStack(Items.OAK_LOG)); fill(upper,"minecraft:water",1000); charge(upper,2000);
        h.setBlock(POS.above(),Blocks.HOPPER);
        var lower=place(h,POS); lower.setItem(1,new ItemStack(Items.COAL)); fill(lower,"minecraft:water",1000); charge(lower,2500);
        h.setBlock(POS.below(),Blocks.HOPPER); h.setBlock(POS.below(2),Blocks.FURNACE);
        var furnace=h.getBlockEntity(POS.below(2),net.minecraft.world.level.block.entity.FurnaceBlockEntity.class); furnace.setItem(1,new ItemStack(Items.COAL));
        h.runAfterDelay(450, () -> {
            h.assertTrue(furnace.getItem(2).is(item("plasticsheet",1).getItem()),"Log becomes rubber, raw plastic, then a plastic sheet using real hoppers and furnace");
            h.assertValueEqual(furnace.getItem(2).getCount(),1,"No duplication across three processing stages");
            h.assertTrue(upper.getItem(3).isEmpty() && lower.getItem(3).isEmpty(),"Both machine outputs extracted");
            h.assertValueEqual(upper.energy().getAmountAsLong()+lower.energy().getAmountAsLong(),0L,"Both laboratory energy costs paid");
            h.assertTrue(upper.tanks().stack(0).isEmpty() && lower.tanks().stack(0).isEmpty(),"Both water costs paid"); h.succeed();
        });
    }
    private static void uraniumChain(GameTestHelper h) {
        var source=place(h,POS); supply(source,CASES.get(2),false,1); charge(source,2000);
        var dest=place(h,OTHER); dest.setItem(0,item("ore_uranium",4)); dest.setItem(4,item("machinestackupgrade",3)); charge(dest,8000);
        h.runAfterDelay(105, () -> {
            var from=h.getLevel().getCapability(Capabilities.Fluid.BLOCK,h.absolutePos(POS),Direction.NORTH);
            var to=h.getLevel().getCapability(Capabilities.Fluid.BLOCK,h.absolutePos(OTHER),Direction.SOUTH);
            var acid=FluidResource.of(TGFluids.ACID.still.get());
            try(Transaction tx=Transaction.openRoot()) {
                int amount=from.extract(acid,1000,tx); h.assertValueEqual(to.insert(acid,amount,tx),amount,"Full fluid transfer through exposed machine capabilities"); tx.commit();
            }
        });
        h.runAfterDelay(220, () -> {
            h.assertTrue(dest.getItem(3).is(item("yellowcake",1).getItem()),"Produced acid processes mined uranium");
            h.assertValueEqual(dest.getItem(3).getCount(),12,"Four source uranium recipes produce twelve yellowcake");
            h.assertTrue(source.tanks().stack(1).isEmpty() && dest.tanks().stack(0).isEmpty(),"Transferred acid is consumed once");
            h.assertValueEqual(source.energy().getAmountAsLong()+dest.energy().getAmountAsLong(),0L,"Both stages pay original energy"); h.succeed();
        });
    }
    private static void codecs(GameTestHelper h) {
        var recipes=h.getLevel().getServer().getRecipeManager().recipeMap().byType(TGMachineContent.CHEM_LAB_RECIPE.get());
        h.assertValueEqual(recipes.size(),31,"Every live original recipe call has a loaded record, including conditional recipes");
        for (var holder:recipes) {
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try {
                net.minecraft.world.item.crafting.Recipe.STREAM_CODEC.encode(buffer,holder.value());
                var decoded=(ChemLabRecipe)net.minecraft.world.item.crafting.Recipe.STREAM_CODEC.decode(buffer);
                h.assertValueEqual(decoded.activation(),holder.value().activation(),"Recipe condition survives network codec");
                h.assertValueEqual(decoded.powerPerTick(),holder.value().powerPerTick(),"Recipe energy survives network codec");
                h.assertValueEqual(decoded.fluidInput().map(ChemLabRecipe.FluidInput::amount),holder.value().fluidInput().map(ChemLabRecipe.FluidInput::amount),"Fluid amount survives recipe sync");
                h.assertValueEqual(decoded.result(),holder.value().result(),"Item result and components survive recipe sync");
                h.assertValueEqual(decoded.fluidOutput(),holder.value().fluidOutput(),"Optional fluid-only output survives recipe sync");
            } finally { buffer.release(); }
        }
        h.succeed();
    }
    private static void menu(GameTestHelper h) {
        var lab=place(h,POS); var owner=WeaponGameTests.player(h); lab.setOwner(owner);
        fill(lab,"minecraft:water",8000); lab.tanks().set(1,FluidResource.of(TGFluids.ACID.still.get()),16000);
        var menu=new ChemLabMenu(73,owner.getInventory(),lab,lab.data); owner.containerMenu=menu;
        var client=new ChemLabMenu(73,owner.getInventory());
        var outgoing=lab.tanks().stack(1); outgoing.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Named batch"));
        var payload=new techguns.modern.network.MachineTanksPayload(73,lab.tanks().stack(0),outgoing);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            techguns.modern.network.MachineTanksPayload.CODEC.encode(buffer,payload);
            var decoded=techguns.modern.network.MachineTanksPayload.CODEC.decode(buffer); decoded.apply(client);
            var wrongMenu=new ChemLabMenu(74,owner.getInventory()); decoded.apply(wrongMenu);
            h.assertTrue(wrongMenu.fluid(1).isEmpty(),"Fluid updates cannot target another open container");
        } finally { buffer.release(); }
        h.assertTrue(client.fluid(0).is(Fluids.WATER) && client.fluid(1).is(TGFluids.ACID.still.get()),"Fluid IDs synchronize to the client menu");
        h.assertValueEqual(client.fluid(1).getAmount(),16000,"Full output volume synchronizes");
        h.assertValueEqual(client.fluid(1).get(net.minecraft.core.component.DataComponents.CUSTOM_NAME),outgoing.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME),"Fluid data components survive network synchronization");
        h.assertValueEqual(menu.slots.get(2).x,35,"Original flask slot X");
        h.assertValueEqual(menu.slots.get(2).y,40,"Original flask slot Y");
        h.assertTrue(menu.clickMenuButton(owner,3),"Owner may lock machine");
        var stranger=WeaponGameTests.player(h); stranger.containerMenu=menu;
        h.assertTrue(!menu.clickMenuButton(stranger,4),"Non-owner cannot empty private input tank");
        h.assertValueEqual(lab.tanks().stack(0).getAmount(),8000,"Unauthorized dump preserves fluid");
        h.assertTrue(menu.clickMenuButton(owner,4),"Owner may empty input tank");
        h.assertTrue(lab.tanks().stack(0).isEmpty() && !lab.tanks().stack(1).isEmpty(),"Input dump affects only selected tank");
        owner.containerMenu=owner.inventoryMenu;
        h.assertTrue(!menu.clickMenuButton(owner,5),"Closed menu cannot empty output"); h.succeed();
    }
    private static void breakInputs(GameTestHelper h) {
        var lab=place(h,POS); supply(lab,CASES.get(2),false,1); charge(lab,2000);
        h.runAfterDelay(30, () -> {
            h.assertTrue(lab.working(),"Break interrupts an unfinished fluid-only operation");
            h.getLevel().destroyBlock(h.absolutePos(POS),true);
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(h.absolutePos(POS)).inflate(2));
            for(String id:List.of("chem_lab","biomass","minecraft:gunpowder")) h.assertValueEqual(drops.stream().filter(e -> e.getItem().is(item(id,1).getItem())).mapToInt(e -> e.getItem().getCount()).sum(),1,"One returned "+id);
            h.assertTrue(drops.stream().noneMatch(e -> e.getItem().is(TGFluids.ACID.bucket.get())),"Unfinished fluid is not manufactured as a free bucket"); h.succeed();
        });
    }
}
