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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.NeonLights;
import techguns.modern.machine.camo.*;
import techguns.modern.world.NeonContent;

final class NeonGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var v:NeonLights.ALL) {
            r.register("neon_place_mine_save_"+v.id(),()->h->properties(h,v));
            r.register("neon_real_light_and_removal_"+v.id(),()->h->light(h,v));
        }
        r.register("neon_recipe_accepts_original_glass_pane_family",()->NeonGameTests::recipes);
        r.register("neon_recipe_rejects_substitutions",()->NeonGameTests::invalidRecipes);
        r.register("neon_bench_full_forward_reverse_stack_and_save",()->NeonGameTests::camo);
        r.register("neon_craft_style_and_place_seven_helipad_lights",()->NeonGameTests::helipad);
    }
    private static Block block(int metadata) { return NeonContent.fromMetadata(metadata); }
    private static void properties(GameTestHelper h,NeonLights.Variant v) {
        var l=h.getLevel(); var b=block(v.metadata()); var s=b.defaultBlockState(); var pos=h.absolutePos(new BlockPos(6,5,6));
        h.assertValueEqual(s.getDestroySpeed(l,pos),4f,"Original hardness"); h.assertValueEqual(b.getExplosionResistance(),4f,"Hardness-derived source resistance");
        h.assertValueEqual(s.getSoundType(),SoundType.GLASS,"Glass sound"); h.assertValueEqual(s.getMapColor(l,pos),MapColor.COLOR_YELLOW,"Source yellow map color");
        h.assertValueEqual(s.getLightEmission(l,pos),15,"Always-on source light level");
        h.assertTrue(s.isSolidRender() && s.isCollisionShapeFullBlock(l,pos),"GenericBlock remains an opaque full cube despite its glass material");
        h.assertValueEqual(s.getShape(l,pos).bounds(),new AABB(0,0,0,1,1,1),"Full selection box");
        h.assertTrue(!s.isRedstoneConductor(l,pos),"Glass material did not form a normal redstone conductor");
        for(var d:Direction.values()) h.assertTrue(s.isFaceSturdy(l,pos,d),"Full support face");
        for(var rotation:Rotation.values()) h.assertValueEqual(s.rotate(rotation),s,"No invented directional block state");
        for(var mirror:Mirror.values()) h.assertValueEqual(s.mirror(mirror),s,"Texture variants are not automatically mirrored");
        h.assertValueEqual(BlockState.CODEC.parse(JsonOps.INSTANCE,BlockState.CODEC.encodeStart(JsonOps.INSTANCE,s).getOrThrow()).getOrThrow(),s,"Native save keeps the exact flattened variant");
        var p=WeaponGameTests.player(h); p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        h.assertTrue(p.hasCorrectToolForDrops(s),"Source material needs no harvest tool or Silk Touch");
        h.assertValueEqual(new ItemStack(Items.DIAMOND_PICKAXE).getDestroySpeed(s),1f,"No invented pickaxe mining bonus");
        var drops=new ArrayList<ItemEntity>(); Consumer<EntityJoinLevelEvent> observe=e->{if(e.getLevel()==l && e.getEntity() instanceof ItemEntity item && item.getItem().is(b.asItem()) && new AABB(pos).inflate(3).contains(item.position()))drops.add(item);}; NeoForge.EVENT_BUS.addListener(observe);
        try {
            for(var face:Direction.values()) {
                l.setBlock(pos,Blocks.STONE.defaultBlockState(),3); var at=pos.relative(face); l.setBlock(at,Blocks.AIR.defaultBlockState(),3);
                p.setYRot(face.ordinal()*60); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(b,2));
                var hit=new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(.5)),face,pos,false);
                h.assertTrue(p.getMainHandItem().getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Real placement on every clicked face");
                h.assertValueEqual(l.getBlockState(at),s,"Click direction and player facing preserve the chosen texture"); h.assertValueEqual(p.getMainHandItem().getCount(),1,"One item spent");
                l.setBlock(pos,Blocks.AIR.defaultBlockState(),3); h.assertTrue(l.getBlockState(at).is(b) && s.canSurvive(l,at),"No supporting block required");
                p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); h.assertTrue(l.destroyBlock(at,true,p),"Actual hand-compatible block drop");
            }
            h.assertValueEqual(drops.stream().mapToInt(e->e.getItem().getCount()).sum(),6,"Each placed variant drops exactly once without Silk Touch");
            l.setBlock(pos,s,3); var note=Block.updateFromNeighbourShapes(Blocks.NOTE_BLOCK.defaultBlockState(),l,pos.above());
            h.assertValueEqual(note.getValue(NoteBlock.INSTRUMENT),NoteBlockInstrument.HAT,"Source glass note instrument");
            l.setBlock(pos,Blocks.AIR.defaultBlockState(),3); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); drops.forEach(Entity::discard); }
    }
    private static void light(GameTestHelper h,NeonLights.Variant v) {
        var l=h.getLevel(); var pos=h.absolutePos(new BlockPos(5,6,5));
        // A closed stone shell keeps light from adjacent concurrently running fixtures out.
        for(int x=-2;x<=2;x++) for(int y=-2;y<=2;y++) for(int z=-2;z<=2;z++)
            l.setBlock(pos.offset(x,y,z),Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z)))==2?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        h.startSequence().thenExecute(()->l.setBlock(pos,block(v.metadata()).defaultBlockState(),3))
                .thenWaitUntil(()->h.assertValueEqual(l.getBrightness(LightLayer.BLOCK,pos.east()),14,"Real neighboring block light"))
                .thenExecute(()->l.setBlock(pos,Blocks.AIR.defaultBlockState(),3))
                .thenWaitUntil(()->h.assertValueEqual(l.getBrightness(LightLayer.BLOCK,pos.east()),0,"Removing the neon clears propagated light")).thenSucceed();
    }
    private static List<ItemStack> grid(Item left,Item right) {
        var n=new ItemStack(Items.IRON_NUGGET); return new ArrayList<>(List.of(n,n,n,new ItemStack(left),new ItemStack(Items.GLOWSTONE_DUST),new ItemStack(right),n,n,n));
    }
    private static ItemStack craft(GameTestHelper h,List<ItemStack> grid) {
        var input=CraftingInput.of(3,3,grid); return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input);
    }
    private static void recipes(GameTestHelper h) {
        var panes=new ArrayList<Item>(); panes.add(Items.GLASS_PANE); for(var color:DyeColor.values())panes.add(Items.STAINED_GLASS_PANE.pick(color));
        for(var pane:panes) {
            var result=craft(h,grid(pane,Items.GLASS_PANE)); h.assertTrue(result.is(block(0).asItem()),"Source paneGlass tag accepts plain and stained panes"); h.assertValueEqual(result.getCount(),16,"Original sixteen double-neon blocks");
        }
        h.succeed();
    }
    private static void invalidRecipes(GameTestHelper h) {
        for(int variant=0;variant<4;variant++) {
            var inputs=grid(Items.GLASS_PANE,Items.GLASS_PANE);
            if(variant==0)inputs.set(4,new ItemStack(Items.REDSTONE));
            if(variant==1)inputs.set(3,new ItemStack(Items.GLASS));
            if(variant==2)inputs.set(0,ItemStack.EMPTY);
            if(variant==3)inputs.set(5,new ItemStack(block(4)));
            var input=CraftingInput.of(3,3,inputs); var match=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel());
            h.assertTrue(match.isEmpty() || !match.get().value().assemble(input).is(block(0).asItem()),"No cheaper ingredient, missing nugget or recycled-light substitution");
        }
        h.succeed();
    }
    private static void camo(GameTestHelper h) {
        var pos=new BlockPos(4,2,4); h.setBlock(pos,CamoBenchContent.BLOCK.get()); var bench=h.getBlockEntity(pos,CamoBenchBlockEntity.class);
        var p=WeaponGameTests.player(h); bench.setOwner(p); p.experienceLevel=7; var menu=new CamoBenchMenu(62,p.getInventory(),bench); p.containerMenu=menu;
        var original=new ItemStack(block(0),64); original.set(DataComponents.CUSTOM_NAME,Component.literal("Helipad lights"));
        var data=new CompoundTag(); data.putString("fixture","neon supply"); original.set(DataComponents.CUSTOM_DATA,CustomData.of(data)); bench.setItem(0,original.copy());
        for(int button:new int[]{1,2}) for(int step=1;step<=5;step++) {
            h.assertTrue(menu.clickMenuButton(p,button),"Native authorized bench operation"); int index=Math.floorMod(button==1?step:-step,5); var actual=bench.getItem(0);
            h.assertTrue(actual.is(block(index).asItem()),"Exact source enum order and both wrap directions"); h.assertValueEqual(actual.getCount(),64,"Whole stack preserved");
            h.assertValueEqual(actual.get(DataComponents.CUSTOM_NAME),original.get(DataComponents.CUSTOM_NAME),"Custom name retained"); h.assertValueEqual(actual.get(DataComponents.CUSTOM_DATA),original.get(DataComponents.CUSTOM_DATA),"Custom data retained");
            h.assertValueEqual(CamoCycling.count(actual),5,"Five styles, not the source max-index value four"); h.assertValueEqual(CamoCycling.index(actual),index,"Source metadata index");
            h.assertValueEqual(CamoCycling.variantName(actual),Component.translatable("block.techguns."+NeonLights.byMetadata(index).id()),"Translated source style name");
            h.assertValueEqual(actual.get(DataComponents.ITEM_MODEL),new ItemStack(block(index)).get(DataComponents.ITEM_MODEL),"Correct inventory model after each change");
            var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE); h.assertTrue(ItemStack.matches(actual,ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,actual).getOrThrow()).getOrThrow()),"Every style saves with stack components");
        }
        h.assertTrue(ItemStack.matches(original,bench.getItem(0)),"Forward and backward full cycles restore exact input"); h.assertValueEqual(p.experienceLevel,7,"No invented XP cost"); h.succeed();
    }
    private static void helipad(GameTestHelper h) {
        var crafted=craft(h,grid(Items.GLASS_PANE,Items.GLASS_PANE));
        var pos=new BlockPos(2,2,2); h.setBlock(pos,CamoBenchContent.BLOCK.get()); var bench=h.getBlockEntity(pos,CamoBenchBlockEntity.class);
        var player=WeaponGameTests.player(h); bench.setOwner(player); var menu=new CamoBenchMenu(63,player.getInventory(),bench); player.containerMenu=menu; bench.setItem(0,crafted);
        h.assertTrue(menu.clickMenuButton(player,2),"One reverse cycle selects source Helipad metadata 4"); var square=bench.removeItem(0,16); h.assertTrue(square.is(block(4).asItem()),"Square neon light obtained from crafted tubes");
        player.setItemInHand(InteractionHand.MAIN_HAND,square);
        for(var point:new int[][]{{2,2},{3,2},{4,2},{3,3},{2,4},{3,4},{4,4}}) {
            var support=h.absolutePos(new BlockPos(point[0]+4,2,point[1]+4)); h.getLevel().setBlock(support,Blocks.STONE.defaultBlockState(),3);
            var hit=new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false);
            h.assertTrue(square.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Crafted and styled item is placeable");
            var state=h.getLevel().getBlockState(support.above()); h.assertTrue(state.is(block(4)),"All seven original H cells use the correct square variant"); h.assertValueEqual(state.getLightEmission(),15,"Every H cell emits maximum light");
        }
        h.assertValueEqual(square.getCount(),9,"Seven placed lights consume seven of the original sixteen"); h.succeed();
    }
    private NeonGameTests() {}
}
