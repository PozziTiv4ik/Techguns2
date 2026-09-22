package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.*;
import techguns.core.*;
import techguns.modern.TGContent;
import techguns.modern.machine.camo.*;
import techguns.modern.world.*;
import net.neoforged.neoforge.registries.DeferredRegister;

final class BuildingGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var v:BuildingBlocks.ALL) {
            r.register("building_properties_"+v.id(),()->h->properties(h,v));
            if(v.ladder()) {
                for(var d:Direction.Plane.HORIZONTAL) r.register("building_place_"+v.id()+"_"+d.getName(),()->h->placement(h,v,d));
                r.register("building_vertical_climb_"+v.id(),()->h->vertical(h,v));
            }
        }
        for(var palette:BuildingBlocks.PALETTES) r.register("building_camo_"+palette.id(),()->h->camo(h,palette));
        for(var color:DyeColor.values()) r.register("building_concrete_"+color.getName(),()->h->concrete(h,color));
        for(var plate:List.of("plateiron","platetin")) {
            r.register("building_panel_recipe_"+plate,()->h->plateRecipe(h,plate,false));
            r.register("building_ladder_recipe_"+plate,()->h->plateRecipe(h,plate,true));
        }
        r.register("building_concrete_rejects_powder",()->h->invalidConcrete(h,true));
        r.register("building_concrete_requires_bars",()->h->invalidConcrete(h,false));
    }
    private static Block block(String id) { return BuildingContent.BLOCKS.get(id).get(); }
    private static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); }
    private static void properties(GameTestHelper h,BuildingBlocks.Variant v) {
        var b=block(v.id()); var s=b.defaultBlockState(); var pos=h.absolutePos(new BlockPos(4,2,4)); var l=h.getLevel();
        l.setBlock(pos,s,3); var p=WeaponGameTests.player(h);
        h.assertValueEqual(s.getDestroySpeed(l,pos),(float)v.hardness(),"Final TGBlocks hardness");
        h.assertValueEqual(b.getExplosionResistance(),(float)v.hardness(),"Source hardness-derived resistance");
        h.assertValueEqual(s.getSoundType(),v.family().equals("concrete")?SoundType.STONE:SoundType.METAL,"Source sound material");
        h.assertTrue(s.is(BlockTags.MINEABLE_WITH_PICKAXE),"Pickaxe mineable");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); h.assertTrue(!p.hasCorrectToolForDrops(s),"No hand harvest");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WOODEN_PICKAXE)); h.assertTrue(p.hasCorrectToolForDrops(s),"No invented higher tool tier");
        var drops=Block.getDrops(s,l,pos,null,p,p.getMainHandItem());
        h.assertTrue(drops.size()==1 && drops.getFirst().is(b.asItem()) && drops.getFirst().getCount()==1,"Own palette variant drops exactly once");
        h.assertValueEqual(s.isCollisionShapeFullBlock(l,pos),!v.ladder(),"Original full cube versus ladder slab");
        h.assertValueEqual(s.getLightEmission(l,pos),0,"No invented light");
        if(v.ladder()) for(var d:Direction.Plane.HORIZONTAL) {
            var state=s.setValue(MetalLadderBlock.FACING,d); var shape=state.getCollisionShape(l,pos).bounds();
            AABB expected=switch(d) { case NORTH->new AABB(0,0,.875,1,1,1); case SOUTH->new AABB(0,0,0,1,1,.125); case WEST->new AABB(.875,0,0,1,1,1); default->new AABB(0,0,0,.125,1,1); };
            h.assertValueEqual(shape,expected,"Exact 1/8 collision slab"); h.assertValueEqual(state.getShape(l,pos).bounds(),expected,"Same outline");
            for(var face:Direction.values()) h.assertTrue(!state.isFaceSturdy(l,pos,face),"Original UNDEFINED support faces");
            for(var type:PathComputationType.values()) h.assertTrue(!state.isPathfindable(type),"Source iron material blocks walking paths");
            for(var rotation:Rotation.values()) h.assertValueEqual(state.rotate(rotation).getValue(MetalLadderBlock.FACING),rotation.rotate(d),"Native rotation");
            for(var mirror:Mirror.values()) h.assertValueEqual(state.mirror(mirror).getValue(MetalLadderBlock.FACING),mirror.mirror(d),"Native mirror");
            var encoded=BlockState.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow();
            h.assertValueEqual(BlockState.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow(),state,"Native state serialization retains variant and facing");
            var orientedDrops=Block.getDrops(state,l,pos,null,p,p.getMainHandItem());
            h.assertTrue(orientedDrops.size()==1 && orientedDrops.getFirst().is(b.asItem()),"All facings drop same source item variant");
        }
        h.succeed();
    }
    private static void placement(GameTestHelper h,BuildingBlocks.Variant v,Direction face) {
        var l=h.getLevel(); var support=h.absolutePos(new BlockPos(4,3,4)); var target=support.relative(face);
        l.setBlock(support,Blocks.STONE.defaultBlockState(),3); l.setBlock(target,Blocks.AIR.defaultBlockState(),3);
        var p=WeaponGameTests.player(h); p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1,1,1))));
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block(v.id()),2));
        var hit=new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(.5)),face,support,false);
        p.getMainHandItem().getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit));
        var state=l.getBlockState(target); h.assertTrue(state.is(block(v.id())),"Real BlockItem places chosen style");
        h.assertValueEqual(state.getValue(MetalLadderBlock.FACING),face,"Horizontal click overrides player facing");
        h.assertValueEqual(p.getMainHandItem().getCount(),1,"Survival placement consumes one");
        l.setBlock(support,Blocks.AIR.defaultBlockState(),3);
        h.assertValueEqual(l.getBlockState(target),state,"Support removal keeps free-standing source ladder");
        h.assertTrue(state.canSurvive(l,target),"No support required"); h.succeed();
    }
    private static void vertical(GameTestHelper h,BuildingBlocks.Variant v) {
        var l=h.getLevel(); var pos=h.absolutePos(new BlockPos(4,3,4)); var p=WeaponGameTests.player(h); p.setYRot(90);
        var b=(MetalLadderBlock)block(v.id());
        for(var face:List.of(Direction.UP,Direction.DOWN)) {
            l.setBlock(pos,Blocks.AIR.defaultBlockState(),3); l.setBlock(pos.below(),Blocks.AIR.defaultBlockState(),3); l.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
            var ctx=new BlockPlaceContext(p,InteractionHand.MAIN_HAND,new ItemStack(b),new BlockHitResult(Vec3.atCenterOf(pos),face,pos,false));
            h.assertValueEqual(ctx.getClickedPos(),pos,"Fixture places into replaceable air");
            h.assertValueEqual(b.getStateForPlacement(ctx).getValue(MetalLadderBlock.FACING),p.getDirection().getOpposite(),"Vertical click falls back to opposite player facing");
            l.setBlock(pos.above(),block("ladder_carbon").defaultBlockState().setValue(MetalLadderBlock.FACING,Direction.EAST),3);
            h.assertValueEqual(b.getStateForPlacement(ctx).getValue(MetalLadderBlock.FACING),Direction.EAST,"Copies upper ladder across styles");
            l.setBlock(pos.below(),block("ladder_shiny").defaultBlockState().setValue(MetalLadderBlock.FACING,Direction.SOUTH),3);
            h.assertValueEqual(b.getStateForPlacement(ctx).getValue(MetalLadderBlock.FACING),Direction.SOUTH,"Lower ladder takes precedence");
            l.setBlock(pos,b.getStateForPlacement(ctx),3); p.setPos(Vec3.atBottomCenterOf(pos));
            h.assertTrue(p.onClimbable(),"Native living-entity climbing recognizes metal ladder");
        }
        h.succeed();
    }
    private static void camo(GameTestHelper h,CamoPalette palette) {
        var pos=new BlockPos(4,2,4); h.setBlock(pos,CamoBenchContent.BLOCK.get()); var bench=h.getBlockEntity(pos,CamoBenchBlockEntity.class);
        var p=WeaponGameTests.player(h); bench.setOwner(p); var menu=new CamoBenchMenu(45,p.getInventory(),bench); p.containerMenu=menu;
        var original=new ItemStack(item(palette.items().getFirst()),64); original.set(DataComponents.CUSTOM_NAME,Component.literal("Building supplies")); bench.setItem(0,original.copy());
        for(int button:new int[]{1,2}) for(int step=1;step<=palette.items().size();step++) {
            h.assertTrue(menu.clickMenuButton(p,button),"Camo Bench processes new family"); var actual=bench.getItem(0);
            int index=Math.floorMod(button==1?step:-step,palette.items().size()); var id=palette.items().get(index); var v=BuildingBlocks.variant(id).orElseThrow();
            h.assertTrue(actual.is(item(id)),"Source enum order and wraparound"); h.assertValueEqual(actual.getCount(),64,"Full stack preserved");
            h.assertValueEqual(actual.getHoverName(),original.getHoverName(),"Custom component retained");
            h.assertValueEqual(CamoCycling.count(actual),palette.items().size(),"Exact family size"); h.assertValueEqual(CamoCycling.index(actual),index,"Source index");
            h.assertValueEqual(CamoCycling.variantName(actual),Component.translatable(v.camoKey()),"Original Camo name, including separate ladder labels");
            h.assertValueEqual(actual.get(DataComponents.ITEM_MODEL),actual.getItem().getDefaultInstance().get(DataComponents.ITEM_MODEL),"New model default follows color");
        }
        h.assertTrue(ItemStack.matches(original,bench.getItem(0)),"Both full cycles restore exact stack"); h.succeed();
    }
    private static ItemStack craft(GameTestHelper h,List<ItemStack> grid) {
        var input=CraftingInput.of(3,3,grid); return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input);
    }
    private static List<ItemStack> concreteGrid(DyeColor color,boolean powder) {
        var list=new ArrayList<ItemStack>(); for(int i=0;i<9;i++) list.add(new ItemStack(i==4?Items.IRON_BARS:powder?Items.CONCRETE_POWDER.pick(color):Items.CONCRETE.pick(DyeColor.byId((color.getId()+i)%16)))); return list;
    }
    private static void concrete(GameTestHelper h,DyeColor color) {
        var out=craft(h,concreteGrid(color,false)); h.assertTrue(out.is(block("concrete_brown").asItem()),"Mixed vanilla concrete colors accepted"); h.assertValueEqual(out.getCount(),16,"Original 16 reinforced blocks"); h.succeed();
    }
    private static void invalidConcrete(GameTestHelper h,boolean powder) {
        var grid=concreteGrid(DyeColor.WHITE,powder); if(!powder) grid.set(4,ItemStack.EMPTY);
        var input=CraftingInput.of(3,3,grid); var match=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel());
        h.assertTrue(match.isEmpty() || !match.get().value().assemble(input).is(block("concrete_brown").asItem()),"No powder substitution or free reinforcement"); h.succeed();
    }
    private static void plateRecipe(GameTestHelper h,String plate,boolean ladder) {
        var p=TGContent.MATERIALS.get(plate).toStack(); var s=new ItemStack(Items.STONE);
        var out=craft(h,ladder?List.of(p,p,p,ItemStack.EMPTY,p,ItemStack.EMPTY,p,p,p):List.of(s,s,s,p,s,p,s,s,s));
        h.assertTrue(out.is(block(ladder?"ladder_metal":"metalpanel_container_red").asItem()),"Original iron/tin plate alternative");
        h.assertValueEqual(out.getCount(),ladder?16:32,"Source output quantity"); h.succeed();
    }
}
