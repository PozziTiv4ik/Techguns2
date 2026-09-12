package techguns.modern.test;

import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.core.ArmorSlot;
import techguns.modern.*;
import techguns.modern.armor.*;
import techguns.modern.machine.TGMachineConfig;
import techguns.modern.machine.grinder.*;

final class GrinderGameTests {
    private static final BlockPos POS = new BlockPos(4,2,4);
    private record Case(String input, String results) {}
    // Independent fixtures from TGMachineRecipes: the misleading steel helper returns 1+5 ingots.
    private static final List<Case> GUNS = List.of(
            new Case("handcannon","minecraft:cobblestone=3,minecraft:oak_log=1,minecraft:iron_nugget=4"),
            new Case("sawedoff","minecraft:iron_ingot=4,minecraft:oak_log=1,minecraft:flint=1"),
            new Case("revolver","minecraft:iron_ingot=3,minecraft:oak_log=1"),
            new Case("goldenrevolver","minecraft:iron_ingot=3,minecraft:oak_log=1,minecraft:gold_ingot=4"),
            new Case("thompson","minecraft:iron_ingot=4,minecraft:oak_log=1"),
            new Case("ak47","ingotobsidiansteel=3,minecraft:iron_ingot=4,minecraft:oak_log=1"),
            new Case("boltaction","ingotobsidiansteel=3,minecraft:iron_ingot=4,minecraft:oak_log=1"),
            new Case("m4","ingotobsidiansteel=3,ingotsteel=2,plasticsheet=2"),
            new Case("m4_infiltrator","ingotobsidiansteel=3,ingotsteel=3,plasticsheet=2,minecraft:redstone=1"),
            new Case("pistol","ingotobsidiansteel=3,plasticsheet=1,minecraft:iron_ingot=2"),
            new Case("combatshotgun","ingotobsidiansteel=3,ingotsteel=2,plasticsheet=2"),
            new Case("mac10","ingotobsidiansteel=3,ingotsteel=6,minecraft:iron_ingot=1"),
            new Case("rocketlauncher","ingotobsidiansteel=6,ingotsteel=6"),
            new Case("aug","ingotobsidiansteel=3,ingotsteel=2,plasticsheet=2"),
            new Case("netherblaster","ingotobsidiansteel=4,cyberneticparts=2"),
            new Case("lmg","ingotobsidiansteel=6,ingotsteel=4,plasticsheet=2"),
            new Case("as50","ingotobsidiansteel=9,plasticsheet=2,minecraft:diamond=1"),
            new Case("vector","ingotobsidiansteel=5,plasticsheet=2"),
            new Case("scar","ingotobsidiansteel=5,plasticsheet=2"),
            new Case("lasergun","ingotobsidiansteel=2,plasticsheet=2,minecraft:redstone=20,minecraft:gold_ingot=3"));
    private static final List<String> AMMO = List.of("riflerounds","pistolrounds","sniperrounds","shotgunrounds","riflerounds_incendiary","pistolrounds_incendiary","sniperrounds_incendiary","shotgunrounds_incendiary","sniperrounds_explosive");
    private record Fixture(GrinderBlockEntity machine, Player player, GrinderMenu menu) {}
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for (var c : GUNS) r.register("grinder_gun_" + c.input, () -> h -> gun(h,c));
        for (var ammo : AMMO) r.register("grinder_ammo_" + ammo, () -> h -> ammo(h,ammo));
        for (var slot : ArmorSlot.values()) for (int damage : new int[]{0,1,989}) r.register("grinder_armor_" + slot.name().toLowerCase(Locale.ROOT) + "_" + damage, () -> h -> armor(h,slot,damage));
        r.register("grinder_power_pause", () -> GrinderGameTests::powerPause);
        r.register("grinder_redstone_and_upgrade_snapshot", () -> GrinderGameTests::redstone);
        r.register("grinder_batch_space_limit", () -> GrinderGameTests::space);
        r.register("grinder_paid_output_blocked", () -> GrinderGameTests::blocked);
        r.register("grinder_saved_roll_and_input", () -> GrinderGameTests::saved);
        r.register("grinder_break_unfinished", () -> h -> breaking(h,false));
        r.register("grinder_break_paid", () -> h -> breaking(h,true));
        r.register("grinder_ports_transactions", () -> GrinderGameTests::ports);
        r.register("grinder_menu_access_and_shift_click", () -> GrinderGameTests::menu);
        r.register("grinder_original_crafting", () -> GrinderGameTests::crafting);
        r.register("grinder_recipe_codec_and_ammo_state", () -> GrinderGameTests::codec);
        r.register("grinder_private_render_packet", () -> GrinderGameTests::render);
        r.register("grinder_no_power_setting", () -> GrinderGameTests::noPower);
        r.register("grinder_hopper_processing_chain", () -> GrinderGameTests::hopper);
        r.register("grinder_empty_rolled_output", () -> GrinderGameTests::emptyResult);
        r.register("grinder_component_stack_capacity", () -> GrinderGameTests::stackCapacity);
        if (Boolean.getBoolean("techguns.chemistryTest")) r.register("chem_optional_grinder_electrum", () -> GrinderGameTests::electrum);
    }
    private static ItemStack item(String id) { return new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id.contains(":") ? id : "techguns:" + id))); }
    private static Map<Item,Integer> expected(String outputs) {
        var result = new HashMap<Item,Integer>();
        for (String entry : outputs.split(",")) { var parts = entry.split("="); result.merge(item(parts[0]).getItem(),Integer.parseInt(parts[1]),Integer::sum); }
        return result;
    }
    private static Map<Item,Integer> counts(List<ItemStack> stacks) {
        var result = new HashMap<Item,Integer>(); for (var stack : stacks) if (!stack.isEmpty()) result.merge(stack.getItem(),stack.getCount(),Integer::sum); return result;
    }
    private static List<ItemStack> output(GrinderBlockEntity m) { return java.util.stream.IntStream.range(2,11).mapToObj(m::getItem).toList(); }
    private static GrinderBlockEntity place(GameTestHelper h) { h.setBlock(POS,GrinderContent.BLOCK.get()); return h.getBlockEntity(POS,GrinderBlockEntity.class); }
    private static Fixture fixture(GameTestHelper h) {
        var m = place(h); var player = WeaponGameTests.player(h); player.getInventory().clearContent(); m.setOwner(player);
        var menu = new GrinderMenu(57,player.getInventory(),m,m.metrics); player.containerMenu = menu; return new Fixture(m,player,menu);
    }
    private static void charge(GrinderBlockEntity m,int amount) { try (var tx = Transaction.openRoot()) { m.energy().insert(amount,tx); tx.commit(); } }
    private static void tick(GameTestHelper h,GrinderBlockEntity m,int amount) { for (int i=0;i<amount;i++) GrinderBlockEntity.tick(h.getLevel(),m.getBlockPos(),m.getBlockState(),m); }
    private static GrinderBlockEntity load(GameTestHelper h,GrinderBlockEntity old,CompoundTag tag) {
        var restored = (GrinderBlockEntity)BlockEntity.loadStatic(old.getBlockPos(),old.getBlockState(),tag,h.getLevel().registryAccess());
        h.getLevel().removeBlockEntity(old.getBlockPos()); h.getLevel().setBlockEntity(restored); return restored;
    }
    private static CompoundTag save(GameTestHelper h,GrinderBlockEntity m) { return m.saveWithFullMetadata(h.getLevel().registryAccess()); }
    private static List<ItemStack> pending(GameTestHelper h,GrinderBlockEntity m) { return ItemStack.OPTIONAL_CODEC.listOf().parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),save(h,m).get("pending")).getOrThrow(); }
    private static void gun(GameTestHelper h,Case c) {
        var m = place(h); var gun = item(c.input); gun.set(TGContent.ROUNDS.get(),0); gun.set(DataComponents.CUSTOM_NAME,Component.literal("Used gun")); m.setItem(0,gun); charge(m,500);
        h.succeedWhen(() -> {
            h.assertTrue(!m.working() && m.getItem(0).isEmpty(),"Real server finishes reserved gun");
            h.assertValueEqual(counts(output(m)),expected(c.results),"Exact original gun materials, without ammunition refund");
            h.assertValueEqual(m.energy().getAmountAsInt(),0,"Exactly 100 ticks at 5 FE");
        });
    }
    private static void ammo(GameTestHelper h,String id) {
        var m = place(h); m.setItem(0,item(id).copyWithCount(8)); m.setItem(1,item("machinestackupgrade").copyWithCount(7)); charge(m,4000);
        h.succeedWhen(() -> {
            h.assertTrue(!m.working() && m.getItem(0).isEmpty(),"Eight rounds finish through server ticks");
            var result = counts(output(m)); int lead=id.startsWith("rifle")?8:id.startsWith("pistol")?6:id.startsWith("sniper")?16:4;
            int copper=id.startsWith("rifle")?16:id.startsWith("pistol")?12:id.startsWith("sniper")?32:8;
            h.assertValueEqual(result.getOrDefault(item("nuggetlead").getItem(),0),lead,"Combined batch lead expectation");
            h.assertValueEqual(result.getOrDefault(item("nuggetcopper").getItem(),0),copper,"Copper factor greater than one is retained");
            int powder=result.getOrDefault(Items.GUNPOWDER,0), target=id.startsWith("sniper")?2:1;
            h.assertTrue(powder <= target && powder >= (id.startsWith("rifle") || id.startsWith("sniper") ? target : 0),"Original powder fraction");
            if (id.contains("incendiary") || id.contains("explosive")) h.assertValueEqual(result.getOrDefault(Items.BLAZE_POWDER,0),1,"Incendiary powder");
            if (id.contains("explosive")) h.assertValueEqual(result.getOrDefault(item("tgx").getItem(),0),4,"Explosive material factor");
            h.assertValueEqual(m.energy().getAmountAsInt(),0,"8 x 500 FE, without quadratic power");
            h.assertValueEqual(m.getItem(1).getCount(),7,"Reusable upgrades retained");
        });
    }
    private static void armor(GameTestHelper h,ArmorSlot slot,int damage) {
        var m=place(h); var stack=ArmorContent.ITEMS.get(slot).toStack(); stack.setDamageValue(damage); TGArmorItem.setCamo(stack,3); m.setItem(0,stack); charge(m,500);
        int[] parts=damage==989?new int[]{1,0}:switch(slot) {
            case HEAD,FEET -> damage==0?new int[]{2,1}:new int[]{1,1};
            case CHEST -> damage==0?new int[]{3,2}:new int[]{2,2};
            case LEGS -> damage==0?new int[]{2,2}:new int[]{1,2};
        };
        var wanted=new HashMap<Item,Integer>(); wanted.put(item("ingotobsidiansteel").getItem(),parts[0]); if(parts[1]>0) wanted.put(item("heavycloth").getItem(),parts[1]);
        h.succeedWhen(() -> { h.assertTrue(!m.working() && m.getItem(0).isEmpty(),"Armor operation completed"); h.assertValueEqual(counts(output(m)),wanted,"Original inverse-damage salvage including healthy rounding"); });
    }
    private static void powerPause(GameTestHelper h) {
        var m=place(h); m.setItem(0,item("platecarbon")); charge(m,4); tick(h,m,10);
        h.assertTrue(m.working() && m.getItem(0).isEmpty(),"Input reserved without enough energy"); h.assertValueEqual(m.metrics.get(1),0,"No fractional tick"); h.assertValueEqual(m.energy().getAmountAsInt(),4,"No partial extraction");
        charge(m,1); tick(h,m,1); h.assertValueEqual(m.metrics.get(1),1,"One exact paid tick"); charge(m,495); tick(h,m,99);
        h.assertValueEqual(counts(output(m)),expected("carbonfibers=1"),"Carbon plate source recipe"); h.assertValueEqual(m.energy().getAmountAsInt(),0,"500 FE total"); h.succeed();
    }
    private static void redstone(GameTestHelper h) {
        var f=fixture(h); var m=f.machine; m.setItem(0,item("platecarbon").copyWithCount(8)); m.setItem(1,item("machinestackupgrade").copyWithCount(7)); charge(m,4000);
        h.assertTrue(f.menu.clickMenuButton(f.player,1),"Require high signal"); tick(h,m,10); h.assertTrue(!m.working(),"Unpowered control prevents start");
        h.setBlock(POS.west(),Blocks.REDSTONE_BLOCK); tick(h,m,11); m.setItem(1,ItemStack.EMPTY);
        h.setBlock(POS.west(),Blocks.AIR); int progress=m.metrics.get(1),energy=m.energy().getAmountAsInt(); tick(h,m,20);
        h.assertValueEqual(m.metrics.get(1),progress,"Progress pauses"); h.assertValueEqual(m.energy().getAmountAsInt(),energy,"Energy pauses");
        h.assertTrue(f.menu.clickMenuButton(f.player,1),"Require low signal"); tick(h,m,100-progress);
        h.assertValueEqual(counts(output(m)),expected("carbonfibers=8"),"Reserved batch survives upgrade removal"); h.assertValueEqual(m.energy().getAmountAsInt(),0,"Power remains 40 FE per tick"); h.succeed();
    }
    private static void fill(GrinderBlockEntity m) { for(int slot=2;slot<11;slot++) m.setItem(slot,new ItemStack(Items.COBBLESTONE,64)); }
    private static void space(GameTestHelper h) {
        var m=place(h); fill(m); m.setItem(2,item("nuggetlead").copyWithCount(57)); m.setItem(3,item("nuggetcopper").copyWithCount(54)); m.setItem(4,new ItemStack(Items.GUNPOWDER,63));
        m.setItem(0,item("pistolrounds").copyWithCount(8)); m.setItem(1,item("machinestackupgrade").copyWithCount(7)); charge(m,3000); tick(h,m,1);
        h.assertValueEqual(m.metrics.get(5),6,"Largest batch fits worst-case output before any roll"); h.assertValueEqual(m.getItem(0).getCount(),2,"Only fitting inputs reserved");
        tick(h,m,100); h.assertValueEqual(m.getItem(3).getCount(),63,"Nine copper merged without spilling"); h.assertValueEqual(m.energy().getAmountAsInt(),0,"Six-item batch costs 3000 FE"); h.succeed();
    }
    private static void blocked(GameTestHelper h) {
        var m=place(h); m.setItem(0,item("revolver")); charge(m,500); tick(h,m,1); fill(m); tick(h,m,150);
        h.assertValueEqual(m.metrics.get(1),100,"Paid job waits for output capacity"); h.assertValueEqual(m.metrics.get(3),3,"Blocked state");
        var rolled=pending(h,m); var restored=load(h,m,save(h,m)); charge(restored,1000); tick(h,restored,20);
        h.assertValueEqual(restored.energy().getAmountAsInt(),1000,"Completed blocked job spends no further energy after reload");
        restored.setItem(2,ItemStack.EMPTY); tick(h,restored,1); h.assertTrue(restored.working(),"One free slot cannot fit two materials");
        restored.setItem(3,ItemStack.EMPTY); tick(h,restored,1); h.assertTrue(!restored.working(),"Second free slot completes atomically");
        h.assertValueEqual(counts(List.of(restored.getItem(2),restored.getItem(3))),counts(rolled),"Stored result delivered exactly once"); h.succeed();
    }
    private static void saved(GameTestHelper h) {
        var f=fixture(h); var m=f.machine; f.menu.clickMenuButton(f.player,0); m.setItem(0,item("pistolrounds").copyWithCount(8)); m.setItem(1,item("machinestackupgrade").copyWithCount(7)); charge(m,4000); tick(h,m,38);
        var rolled=pending(h,m); var restored=load(h,m,save(h,m)); h.assertTrue(restored.ownerOnly() && restored.isOwner(f.player),"Owner/security persist"); h.assertValueEqual(restored.metrics.get(1),37,"Paid progress persists");
        for(int i=0;i<3;i++) { restored=load(h,restored,save(h,restored)); h.assertValueEqual(counts(pending(h,restored)),counts(rolled),"Reload cannot reroll ammunition"); }
        tick(h,restored,63); h.assertValueEqual(counts(output(restored)),counts(rolled),"Saved random output committed once"); h.assertValueEqual(restored.energy().getAmountAsInt(),0,"Saved energy exact"); h.succeed();
    }
    private static void breaking(GameTestHelper h,boolean paid) {
        var m=place(h); var input=item("revolver"); input.set(TGContent.ROUNDS.get(),2); input.set(DataComponents.CUSTOM_NAME,Component.literal("Keep magazine")); m.setItem(0,input.copy()); charge(m,500); tick(h,m,1);
        if(paid) fill(m); tick(h,m,paid?100:37); var rolled=pending(h,m);
        h.getLevel().destroyBlock(m.getBlockPos(),true); var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(m.getBlockPos()).inflate(2)).stream().map(ItemEntity::getItem).toList(); var result=counts(drops);
        h.assertValueEqual(result.getOrDefault(GrinderContent.ITEM.get(),0),1,"Station drops once");
        if(paid) { h.assertValueEqual(result.getOrDefault(input.getItem(),0),0,"Paid operation does not return original gun"); for(var entry:counts(rolled).entrySet()) h.assertValueEqual(result.getOrDefault(entry.getKey(),0),entry.getValue(),"Paid outputs returned exactly once"); }
        else { h.assertValueEqual(result.getOrDefault(input.getItem(),0),1,"Unfinished gun returned once"); h.assertTrue(drops.stream().anyMatch(s -> ItemStack.matches(s,input)),"Magazine and custom name preserved"); h.assertTrue(!result.containsKey(Items.IRON_INGOT),"No unpaid output"); }
        h.succeed();
    }
    private static void ports(GameTestHelper h) {
        var m=place(h); var sides=new ArrayList<Direction>(List.of(Direction.values())); sides.add(null);
        for(var side:sides) {
            var items=h.getLevel().getCapability(Capabilities.Item.BLOCK,m.getBlockPos(),side); var energy=h.getLevel().getCapability(Capabilities.Energy.BLOCK,m.getBlockPos(),side);
            h.assertTrue(items!=null && items.size()==11 && energy!=null,"Eleven item slots and FE on every side including unsided");
            try(var tx=Transaction.openRoot()) {
                h.assertValueEqual(energy.insert(30000,tx),20000,"Original FE capacity");
                h.assertValueEqual(items.insert(0,ItemResource.of(item("riflerounds")),65,tx),64,"Stack input");
                h.assertValueEqual(items.extract(0,ItemResource.of(item("riflerounds")),1,tx),0,"Automation cannot steal reserved input slot");
                h.assertValueEqual(items.insert(1,ItemResource.of(item("machinestackupgrade")),9,tx),7,"Upgrade limit");
                h.assertValueEqual(items.insert(10,ItemResource.of(Items.IRON_INGOT),1,tx),0,"Ninth output rejects insertion");
            }
            h.assertTrue(m.isEmpty() && m.energy().getAmountAsInt()==0,"Aborted transfers roll back");
        }
        m.setItem(10,new ItemStack(Items.IRON_INGOT,3));
        try(var tx=Transaction.openRoot()) { h.assertValueEqual(m.automation().extract(10,ItemResource.of(Items.IRON_INGOT),3,tx),3,"Ninth output extracts"); tx.commit(); }
        h.assertTrue(m.isEmpty(),"Committed extraction"); h.succeed();
    }
    private static void menu(GameTestHelper h) {
        var f=fixture(h); h.assertValueEqual(f.menu.slots.size(),47,"Original eleven machine slots plus player inventory");
        f.player.getInventory().setItem(9,item("platecarbon").copyWithCount(2)); h.assertValueEqual(f.menu.quickMoveStack(f.player,11).getCount(),2,"First player slot shift-clicks into input");
        f.player.getInventory().setItem(10,item("machinestackupgrade")); h.assertTrue(!f.menu.quickMoveStack(f.player,12).isEmpty() && f.machine.getItem(1).getCount()==1,"Upgrade routed to dedicated slot");
        for(int i=0;i<36;i++) f.player.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64)); f.player.getInventory().setItem(9,ItemStack.EMPTY); f.machine.setItem(10,new ItemStack(Items.IRON_INGOT,3));
        h.assertTrue(!f.menu.quickMoveStack(f.player,10).isEmpty() && f.player.getInventory().getItem(9).is(Items.IRON_INGOT),"Output can use first player slot, fixing original skipped slot");
        h.assertTrue(f.menu.clickMenuButton(f.player,0),"Owner locks machine"); var other=WeaponGameTests.player(h); var otherMenu=new GrinderMenu(58,other.getInventory(),f.machine,f.machine.metrics); other.containerMenu=otherMenu;
        h.assertTrue(!otherMenu.stillValid(other) && !otherMenu.clickMenuButton(other,1) && otherMenu.quickMoveStack(other,0).isEmpty(),"Private machine rejects other player's actions");
        otherMenu.clicked(0,0,ContainerInput.PICKUP,other); h.assertValueEqual(f.machine.getItem(0).getCount(),2,"Click cannot bypass private access");
        var restored=load(h,f.machine,save(h,f.machine)); h.assertTrue(restored.ownerOnly() && restored.isOwner(f.player),"Security survives save");
        f.player.setPos(f.player.position().add(100,0,0)); h.assertTrue(!f.menu.clickMenuButton(f.player,0) && f.menu.quickMoveStack(f.player,0).isEmpty(),"Remote/stale menu rejects mutation"); h.succeed();
    }
    private static void crafting(GameTestHelper h) {
        var plate=item("plateiron"); var part=item("mechanicalpartsiron"); var grid=CraftingInput.of(3,3,List.of(plate,part,plate,part,item("electricengine"),part,plate,new ItemStack(Items.REDSTONE),plate));
        var result=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,h.getLevel()).orElseThrow().value().assemble(grid);
        h.assertTrue(result.is(GrinderContent.ITEM.get()) && result.getCount()==1,"Original simplemachine2 metadata 8 crafting");
        var m=place(h); h.assertTrue(m.getBlockState().canOcclude(),"Source full opaque block"); h.assertValueEqual(m.getBlockState().getCollisionShape(h.getLevel(),m.getBlockPos()).bounds(),new AABB(0,0,0,1,1,1),"Original collision"); h.succeed();
    }
    private static void codec(GameTestHelper h) {
        var manager=h.getLevel().getServer().getRecipeManager(); var records=manager.recipeMap().byType(GrinderContent.RECIPE.get()); h.assertValueEqual(records.size(),38,"All selected source records loaded");
        for(var holder:records) {
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try { GrinderRecipe.STREAM_CODEC.encode(buffer,holder.value()); var copy=GrinderRecipe.STREAM_CODEC.decode(buffer); h.assertValueEqual(copy.outputs(),holder.value().outputs(),"Output counts/factors/preferred tag survive codec"); } finally { buffer.release(); }
        }
        var loaded=item("revolver"); loaded.set(TGContent.ROUNDS.get(),6); var empty=loaded.copy(); empty.set(TGContent.ROUNDS.get(),0);
        var recipe=manager.getRecipeFor(GrinderContent.RECIPE.get(),new SingleRecipeInput(loaded),h.getLevel()).orElseThrow().value();
        h.assertTrue(recipe.matches(new SingleRecipeInput(empty),h.getLevel()),"Original GenericGun normalizes metadata to zero and stores ammo in NBT");
        h.assertValueEqual(counts(recipe.results(loaded,1,false,() -> { throw new AssertionError("Nonrandom recipe drew RNG"); })),counts(recipe.results(empty,1,true,() -> 0)),"Loaded gun yields same materials without extra ammo");
        h.assertTrue(manager.getRecipeFor(GrinderContent.RECIPE.get(),new SingleRecipeInput(item("laserpistol")),h.getLevel()).isEmpty(),"No invented laser pistol salvage recipe");
        var chance=manager.getRecipeFor(GrinderContent.RECIPE.get(),new SingleRecipeInput(item("pistolrounds")),h.getLevel()).orElseThrow().value(); int[] rolls={0};
        h.assertValueEqual(counts(chance.results(item("pistolrounds"),8,false,() -> { rolls[0]++; return .5; })),expected("nuggetlead=6,nuggetcopper=12,minecraft:gunpowder=1"),"Combined random batch at fixed roll");
        h.assertValueEqual(rolls[0],3,"Exactly one draw per output, including integer expectations"); h.succeed();
    }
    private static void render(GameTestHelper h) {
        var m=place(h); m.setItem(0,item("pistolrounds")); charge(m,500); tick(h,m,31);
        var tag=m.getUpdateTag(h.getLevel().registryAccess()); h.assertTrue(!tag.contains("pending") && !tag.contains("reserved") && !tag.contains("Items") && !tag.contains("energy"),"Client receives no hidden roll, energy or inventory");
        var client=new GrinderBlockEntity(m.getBlockPos(),m.getBlockState()); client.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),tag));
        h.assertTrue(client.displayItem().is(item("pistolrounds").getItem()),"Display input survives update packet"); h.assertValueEqual(client.displayProgress(0),.3f,"Display paid progress");
        tick(h,m,70); client.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),m.getUpdateTag(h.getLevel().registryAccess())));
        h.assertTrue(client.displayItem().isEmpty(),"Finished job clears client input");
        var owner=WeaponGameTests.player(h); m.setOwner(owner); var serverMenu=new GrinderMenu(59,owner.getInventory(),m,m.metrics); owner.containerMenu=serverMenu; serverMenu.clickMenuButton(owner,0); charge(m,20000);
        var clientMenu=new GrinderMenu(59,owner.getInventory()); var words=new techguns.modern.machine.SplitIntContainerData(m.metrics);
        for(int index=0;index<2+words.getCount();index++) {
            int value=index<2?serverMenu.value(index):words.get(index-2); var buffer=new net.minecraft.network.FriendlyByteBuf(Unpooled.buffer());
            try {
                var codec=net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket.STREAM_CODEC;
                codec.encode(buffer,new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(59,index,value)); var packet=codec.decode(buffer); clientMenu.setData(packet.getId(),packet.getValue());
            } finally { buffer.release(); }
        }
        h.assertTrue(clientMenu.value(0)==1 && clientMenu.value(1)==1,"Access fields remain before metric words");
        for(int index=0;index<GrinderBlockEntity.METRICS;index++) h.assertValueEqual(clientMenu.metric(index),serverMenu.metric(index),"All metrics survive native menu packets"); h.succeed();
    }
    private static void noPower(GameTestHelper h) {
        boolean previous=TGMachineConfig.MACHINES_NEED_NO_POWER.get();
        try { TGMachineConfig.MACHINES_NEED_NO_POWER.set(true); var m=place(h); m.setItem(0,item("platecarbon")); tick(h,m,101); h.assertValueEqual(counts(output(m)),expected("carbonfibers=1"),"Original no-power setting still processes for 100 ticks"); h.assertValueEqual(m.energy().getAmountAsInt(),0,"No invented generator or energy"); } finally { TGMachineConfig.MACHINES_NEED_NO_POWER.set(previous); }
        h.succeed();
    }
    private static void hopper(GameTestHelper h) {
        var m=place(h); charge(m,500); h.setBlock(POS.above(),Blocks.HOPPER); h.setBlock(POS.below(),Blocks.HOPPER);
        var input=h.getBlockEntity(POS.above(),HopperBlockEntity.class); var output=h.getBlockEntity(POS.below(),HopperBlockEntity.class); input.setItem(0,item("platecarbon"));
        h.succeedWhen(() -> { h.assertTrue(input.isEmpty() && !m.working() && m.isEmpty(),"Real hoppers supply and drain station"); h.assertTrue(output.getItem(0).is(item("carbonfibers").getItem()) && output.getItem(0).getCount()==1,"Hopper receives processed output"); h.assertValueEqual(m.energy().getAmountAsInt(),0,"Actual ticked chain cost"); });
    }
    private static void emptyResult(GameTestHelper h) {
        var m=place(h); m.setItem(0,item("platecarbon")); charge(m,500); tick(h,m,1); var tag=save(h,m); tag.put("pending",new ListTag());
        var restored=load(h,m,tag); h.assertTrue(restored.working(),"Reserved input identifies a valid all-zero random result"); tick(h,restored,100);
        h.assertTrue(restored.isEmpty() && !restored.working(),"Empty output operation completes without reroll or stuck input"); h.assertValueEqual(restored.energy().getAmountAsInt(),0,"Empty output still pays cycle"); h.succeed();
    }
    private static void stackCapacity(GameTestHelper h) {
        var f=fixture(h); var m=f.machine; m.setItem(0,item("platecarbon")); charge(m,20000); tick(h,m,1);
        var tag=save(h,m); var custom=new ItemStack(Items.COBBLESTONE,99); custom.set(DataComponents.MAX_STACK_SIZE,99);
        tag.put("pending",ItemStack.OPTIONAL_CODEC.listOf().encodeStart(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),List.of(custom)).getOrThrow());
        var restored=load(h,m,tag); tick(h,restored,100);
        h.assertValueEqual(restored.getItem(2).getCount(),64,"Container limit applies even with a 99-item stack component");
        h.assertValueEqual(restored.getItem(3).getCount(),35,"Remainder goes to next output without being clamped away");
        h.assertValueEqual(counts(output(restored)),Map.of(Items.COBBLESTONE,99),"Full custom output survives save and delivery"); h.succeed();
    }
    private static void electrum(GameTestHelper h) {
        // Existing conditional datapack uses emerald as an external electrum stand-in.
        var m=place(h); m.setItem(0,item("lasergun")); charge(m,500); tick(h,m,101);
        h.assertValueEqual(counts(output(m)),expected("ingotobsidiansteel=2,plasticsheet=2,minecraft:redstone=20,minecraft:emerald=3"),"External electrum replaces gold fallback"); h.succeed();
    }
}
