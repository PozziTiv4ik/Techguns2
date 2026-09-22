package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.modern.*;
import techguns.modern.machine.camo.CamoCycling;
import techguns.modern.world.*;

final class FortificationGameTests {
    private static final BlockPos POS=new BlockPos(4,3,4);
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int i=0;i<16;i++) { int mask=i; r.register("sandbags_cardinal_"+i,()->h->sandShape(h,mask)); }
        for(int i=0;i<4;i++) { int corner=i; r.register("sandbags_diagonal_update_"+i,()->h->sandCorner(h,corner)); }
        r.register("sandbags_exceptions_and_one_way_fences",()->FortificationGameTests::sandConnections);
        r.register("sandbags_original_mining_and_torch",()->FortificationGameTests::sandProperties);
        for(var entry:FortificationContent.LAMPS.entrySet()) {
            String id=entry.getKey();
            for(var d:Direction.values()) r.register("fortification_place_"+id+"_"+d.getName(),()->h->lampPlace(h,id,d));
            r.register("fortification_attachment_"+id,()->h->lampSupports(h,id));
            r.register("fortification_selected_support_"+id,()->h->lampSelected(h,id));
        }
        for(var d:Direction.Plane.HORIZONTAL) for(var hinge:DoorHingeSide.values()) {
            r.register("bunker_place_"+d.getName()+"_"+hinge.getSerializedName(),()->h->doorPlace(h,d,hinge));
            for(var half:DoubleBlockHalf.values()) r.register("bunker_pair_"+d.getName()+"_"+hinge.getSerializedName()+"_"+half.getSerializedName(),()->h->doorPair(h,d,hinge,half));
        }
        for(var half:DoubleBlockHalf.values()) {
            r.register("bunker_power_"+half.getSerializedName(),()->h->doorPower(h,half));
            for(int i=0;i<3;i++) { int mode=i; r.register("bunker_mining_"+half.getSerializedName()+"_"+i,()->h->doorMining(h,half,mode)); }
        }
        r.register("bunker_pair_mismatch_and_perpendicular",()->FortificationGameTests::doorMismatches);
        r.register("bunker_sound_and_native_properties",()->FortificationGameTests::doorSound);
        r.register("bunker_invalid_placement_and_lost_floor",()->FortificationGameTests::doorInvalid);
        r.register("bunker_hinge_legacy_neighbors",()->FortificationGameTests::doorHinge);
        r.register("bunker_rejects_foreign_half",()->FortificationGameTests::doorForeignHalf);
        r.register("fortification_eight_original_recipes",()->FortificationGameTests::recipes);
    }
    private static BlockPos pos(GameTestHelper h) { return h.absolutePos(POS); }
    private static void put(GameTestHelper h,BlockPos p,BlockState s) { h.getLevel().setBlock(p,s,3); }
    private static void clear(GameTestHelper h,BlockPos p) { put(h,p,Blocks.AIR.defaultBlockState()); }
    private static BlockState sand() { return FortificationContent.SANDBAGS.get().defaultBlockState(); }
    private static IndustrialLampBlock lamp(String id) { return FortificationContent.LAMPS.get(id).get(); }
    private static void near(GameTestHelper h,double a,double b,String why) { h.assertTrue(Math.abs(a-b)<1e-6,why+": "+a+" vs "+b); }
    private static int drops(GameTestHelper h,BlockPos p,Item item) { return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(3),e->e.getItem().is(item)).stream().mapToInt(e->e.getItem().getCount()).sum(); }
    private static void sandShape(GameTestHelper h,int mask) {
        var p=pos(h); var l=h.getLevel();
        for(int i=0;i<4;i++) put(h,p.relative(SandbagBlock.DIRECTIONS.get(i)),((mask & 1<<i)!=0?Blocks.STONE:Blocks.AIR).defaultBlockState());
        put(h,p,sand()); var s=l.getBlockState(p);
        for(int i=0;i<4;i++) { h.assertValueEqual(s.getValue(SandbagBlock.SIDES.get(i)),(mask & 1<<i)!=0,"Cardinal connection"); h.assertTrue(!s.getValue(SandbagBlock.CORNERS.get(i)),"No corner without diagonal block"); }
        var collision=s.getCollisionShape(l,p); double volume=collision.toAabbs().stream().mapToDouble(a->a.getXsize()*a.getYsize()*a.getZsize()).sum();
        near(h,volume,.25+.125*Integer.bitCount(mask),"Center plus cardinal arms only");
        var outline=s.getShape(l,p).bounds(); h.assertValueEqual(outline,collision.bounds(),"Outline encloses arms");
        h.assertTrue(s.getShape(l,p).toAabbs().size()==1,"Outline is the enclosing single box");
        for(var r:Rotation.values()) for(int i=0;i<4;i++) h.assertValueEqual(s.rotate(r).getValue(SandbagBlock.SIDES.get(SandbagBlock.DIRECTIONS.indexOf(r.rotate(SandbagBlock.DIRECTIONS.get(i))))),s.getValue(SandbagBlock.SIDES.get(i)),"Rotation retains connectivity");
        h.assertValueEqual(BlockState.CODEC.parse(JsonOps.INSTANCE,BlockState.CODEC.encodeStart(JsonOps.INSTANCE,s).getOrThrow()).getOrThrow(),s,"Native saved state"); h.succeed();
    }
    private static void sandCorner(GameTestHelper h,int corner) {
        var p=pos(h); var a=SandbagBlock.DIRECTIONS.get(corner); var b=SandbagBlock.DIRECTIONS.get((corner+1)%4); var diagonal=p.relative(a).relative(b);
        put(h,p.relative(a),Blocks.STONE.defaultBlockState()); put(h,p.relative(b),Blocks.STONE.defaultBlockState()); put(h,p,sand());
        var before=h.getLevel().getBlockState(p).getCollisionShape(h.getLevel(),p);
        for(int flags:new int[]{2,3}) {
            h.getLevel().setBlock(diagonal,Blocks.STONE.defaultBlockState(),flags); var s=h.getLevel().getBlockState(p);
            h.assertTrue(s.getValue(SandbagBlock.CORNERS.get(corner)),"Diagonal-only update reaches render state even with flags=2");
            h.assertTrue(!Shapes.joinIsNotEmpty(before,s.getCollisionShape(h.getLevel(),p),BooleanOp.NOT_SAME),"Visual corner never fills collision");
            for(var mirror:Mirror.values()) h.assertValueEqual(s.mirror(mirror).mirror(mirror),s,"Mirror corner round trip");
            h.getLevel().setBlock(diagonal,Blocks.AIR.defaultBlockState(),flags); h.assertTrue(!h.getLevel().getBlockState(p).getValue(SandbagBlock.CORNERS.get(corner)),"Corner clears without cardinal change");
        }
        // Native structure placement can suppress shape updates; final reconciliation is explicit.
        h.getLevel().setBlock(diagonal,sand(),18); h.getLevel().setBlock(p.relative(a),sand(),18);
        var recomputed=Block.updateFromNeighbourShapes(h.getLevel().getBlockState(p),h.getLevel(),p);
        h.assertTrue(recomputed.getValue(SandbagBlock.CORNERS.get(corner)),"Native structure shape reconciliation");
        put(h,p.relative(a),Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,a.getClockWise()));
        put(h,p.relative(b),Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,b.getClockWise()));
        put(h,diagonal,Blocks.IRON_BARS.defaultBlockState());
        h.assertTrue(!h.getLevel().getBlockState(p).getValue(SandbagBlock.CORNERS.get(corner)),"Pane callback does not connect to either gate");
        put(h,diagonal,Blocks.OAK_FENCE.defaultBlockState());
        h.assertTrue(!h.getLevel().getBlockState(p).getValue(SandbagBlock.CORNERS.get(corner)),"Both gate crossbars point toward the center, not toward the diagonal fence");
        put(h,p.relative(b),Blocks.STONE.defaultBlockState());
        h.assertTrue(h.getLevel().getBlockState(p).getValue(SandbagBlock.CORNERS.get(corner)),"Diagonal fence callback accepts the adjacent solid block");
        put(h,diagonal,Blocks.IRON_BARS.defaultBlockState());
        h.assertTrue(h.getLevel().getBlockState(p).getValue(SandbagBlock.CORNERS.get(corner)),"Diagonal pane callback also accepts the solid block"); h.succeed();
    }
    private static void sandConnections(GameTestHelper h) {
        var p=pos(h); put(h,p,sand());
        for(var b:List.of(Blocks.GLASS,Blocks.STAINED_GLASS.white(),Blocks.PISTON,Blocks.STICKY_PISTON,Blocks.BARRIER,Blocks.MELON,Blocks.PUMPKIN,Blocks.CARVED_PUMPKIN,Blocks.JACK_O_LANTERN,Blocks.OAK_LEAVES,Blocks.GLOWSTONE,Blocks.SEA_LANTERN,Blocks.ICE,Blocks.SHULKER_BOX,Blocks.IRON_BARS,Blocks.COBBLESTONE_WALL)) {
            put(h,p.north(),b.defaultBlockState()); h.assertTrue(!h.getLevel().getBlockState(p).getValue(SandbagBlock.SIDES.getFirst()),"Source no arm toward "+b);
        }
        for(var fence:List.of(Blocks.OAK_FENCE,Blocks.NETHER_BRICK_FENCE)) {
            put(h,p.north(),fence.defaultBlockState());
            h.assertTrue(!h.getLevel().getBlockState(p).getValue(SandbagBlock.SIDES.getFirst()),"Sandbag does not extend to a fence");
            // Reconcile the freshly set fence as BlockItem would do before placement.
            var s=Block.updateFromNeighbourShapes(h.getLevel().getBlockState(p.north()),h.getLevel(),p.north()); put(h,p.north(),s);
            h.assertTrue(h.getLevel().getBlockState(p.north()).getValue(FenceBlock.SOUTH),"Both fence materials extend toward sandbags");
        }
        for(var d:Direction.Plane.HORIZONTAL) {
            put(h,p.north(),Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,d));
            h.assertValueEqual(h.getLevel().getBlockState(p).getValue(SandbagBlock.SIDES.getFirst()),d.getAxis()==Direction.Axis.X,"Gate connects along its crossbar");
        }
        put(h,p.north(),sand()); h.assertTrue(h.getLevel().getBlockState(p).getValue(SandbagBlock.SIDES.getFirst()),"Other sandbags connect"); h.succeed();
    }
    private static void sandProperties(GameTestHelper h) {
        var p=pos(h); put(h,p,sand()); var s=h.getLevel().getBlockState(p); var player=WeaponGameTests.player(h); player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        h.assertTrue(player.hasCorrectToolForDrops(s),"Original cloth material permits hand harvest"); h.assertValueEqual(s.getDestroySpeed(h.getLevel(),p),6f,"Registration overrides constructor hardness");
        h.assertValueEqual(s.getBlock().getExplosionResistance(),9f,"setResistance(15) uses legacy 3/5 scaling"); h.assertValueEqual(s.getSoundType(),SoundType.WOOL,"Cloth sound");
        var d=Block.getDrops(s,h.getLevel(),p,null,player,ItemStack.EMPTY); h.assertTrue(d.size()==1 && d.getFirst().is(s.getBlock().asItem()),"One hand-harvest drop");
        h.assertTrue(Blocks.TORCH.defaultBlockState().canSurvive(h.getLevel(),p.above()),"Original torch-on-top support");
        for(var face:Direction.Plane.HORIZONTAL) h.assertTrue(!s.isFaceSturdy(h.getLevel(),p,face),"No full side attachment");
        h.assertValueEqual(CamoCycling.count(new ItemStack(s.getBlock())),0,"No invented sandbag palette"); h.succeed();
    }
    private static net.minecraft.world.InteractionResult place(GameTestHelper h,ItemStack stack,BlockPos support,Direction face) {
        var p=WeaponGameTests.player(h); p.setPos(h.absoluteVec(new Vec3(1,1,1))); p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        return stack.getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(.5)),face,support,false)));
    }
    private static void lampPlace(GameTestHelper h,String id,Direction clicked) {
        var support=pos(h); var target=support.relative(clicked); var b=lamp(id); put(h,support,Blocks.STONE.defaultBlockState()); clear(h,target); var stack=new ItemStack(b,2);
        h.assertTrue(place(h,stack,support,clicked).consumesAction(),"Real lamp item placement"); var s=h.getLevel().getBlockState(target);
        h.assertTrue(s.is(b),"Correct style placed"); h.assertValueEqual(s.getValue(IndustrialLampBlock.FACING),clicked.getOpposite(),"Facing points toward selected support");
        h.assertValueEqual(stack.getCount(),1,"One item consumed"); h.assertValueEqual(s.getLightEmission(h.getLevel(),target),15,"Full original light");
        h.assertValueEqual(s.getDestroySpeed(h.getLevel(),target),4f,"Final lamp hardness"); h.assertValueEqual(b.getExplosionResistance(),4f,"Lamp resistance"); h.assertValueEqual(s.getSoundType(),SoundType.GLASS,"Glass sounds");
        var box=s.getShape(h.getLevel(),target).bounds(); h.assertValueEqual(box,s.getCollisionShape(h.getLevel(),target).bounds(),"Solid original shape");
        near(h,box.getXsize()*box.getYsize()*box.getZsize(),b.lantern()?.1875:.046875,"Original lantern/lamp volume");
        if(!b.lantern()) near(h,switch(clicked.getAxis()) { case X->box.getXsize(); case Y->box.getYsize(); case Z->box.getZsize(); },.1875,"3/16 flat thickness");
        if(!b.lantern()) {
            var face=s.getValue(IndustrialLampBlock.FACING); double edge=face.getAxisDirection()==Direction.AxisDirection.POSITIVE?box.max(face.getAxis()):box.min(face.getAxis());
            near(h,edge,face.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:0,"Flat collision touches selected support plane");
        }
        var player=WeaponGameTests.player(h); player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); h.assertTrue(!player.hasCorrectToolForDrops(s),"Iron material requires pickaxe");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WOODEN_PICKAXE)); h.assertTrue(player.hasCorrectToolForDrops(s),"Original zero-tier pickaxe");
        h.assertValueEqual(CamoCycling.count(new ItemStack(b)),0,"Source disables lamp Camo Bench recipes");
        var saved=BlockState.CODEC.parse(JsonOps.INSTANCE,BlockState.CODEC.encodeStart(JsonOps.INSTANCE,s).getOrThrow()).getOrThrow(); h.assertValueEqual(saved,s,"Saved orientation including lanterns");
        clear(h,support); h.assertTrue(h.getLevel().getBlockState(target).isAir(),"Selected support removal drops lamp"); h.assertValueEqual(drops(h,target,b.asItem()),1,"Exactly one lamp dropped"); h.succeed();
    }
    private static void lampSupports(GameTestHelper h,String id) {
        var p=pos(h); var b=lamp(id); var target=p.above();
        for(var support:List.of(Blocks.OAK_FENCE,Blocks.HOPPER,Blocks.ANVIL,Blocks.COBBLESTONE_WALL,FortificationContent.SANDBAGS.get(),Blocks.GLASS,Blocks.STAINED_GLASS.white())) {
            clear(h,target); put(h,p,support.defaultBlockState()); var stack=new ItemStack(b); boolean expected=support!=Blocks.ANVIL && (support!=Blocks.OAK_FENCE && support!=Blocks.HOPPER || b.lantern());
            h.assertValueEqual(place(h,stack,p,Direction.UP).consumesAction(),expected,"Original item face filter on "+support);
            h.assertValueEqual(h.getLevel().getBlockState(target).is(b),expected,"Placement outcome"); clear(h,target);
        }
        for(var support:List.of(Blocks.GLASS,Blocks.PISTON,Blocks.STICKY_PISTON,Blocks.ICE,Blocks.GLOWSTONE,Blocks.BEACON,Blocks.OAK_LEAVES,Blocks.SHULKER_BOX)) {
            put(h,p,support.defaultBlockState()); clear(h,p.north()); var stack=new ItemStack(b);
            h.assertTrue(!place(h,stack,p,Direction.NORTH).consumesAction(),"Excluded horizontal attachment "+support); h.assertValueEqual(stack.getCount(),1,"Rejected placement preserves item");
        }
        clear(h,p); clear(h,p.below()); put(h,p,b.defaultBlockState()); h.assertTrue(h.getLevel().getBlockState(p).isAir(),"onPlace drops unsupported lamp immediately"); h.succeed();
    }
    private static void lampSelected(GameTestHelper h,String id) {
        var p=pos(h); var b=lamp(id); for(var d:Direction.values()) put(h,p.relative(d),Blocks.STONE.defaultBlockState());
        put(h,p,b.defaultBlockState()); var s=h.getLevel().getBlockState(p);
        for(var d:Direction.values()) h.assertValueEqual(s.getValue(IndustrialLampBlock.CONNECTIONS.get(d)),b.lantern(),"All six lantern brackets reflect supports");
        clear(h,p.north()); h.assertTrue(h.getLevel().getBlockState(p).is(b),"Unselected support loss retains lamp");
        h.assertTrue(!h.getLevel().getBlockState(p).getValue(IndustrialLampBlock.CONNECTIONS.get(Direction.NORTH)),"North bracket updates");
        clear(h,p.below()); h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Other five supports do not replace selected floor"); h.succeed();
    }
    private static BlockState door(Direction d,DoorHingeSide hinge,boolean open) { return FortificationContent.DOOR.get().defaultBlockState().setValue(DoorBlock.FACING,d).setValue(DoorBlock.HINGE,hinge).setValue(DoorBlock.OPEN,open); }
    private static void doorAt(GameTestHelper h,BlockPos pos,BlockState state) {
        put(h,pos.below(),Blocks.STONE.defaultBlockState()); h.getLevel().setBlock(pos,state.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2); h.getLevel().setBlock(pos.above(),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
    }
    private static void toggle(GameTestHelper h,BlockPos pos) { var s=h.getLevel().getBlockState(pos); h.assertTrue(s.useWithoutItem(h.getLevel(),WeaponGameTests.player(h),new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false)).consumesAction(),"Manual door interaction"); }
    private static void halves(GameTestHelper h,BlockPos pos,boolean open) { for(var p:List.of(pos,pos.above())) h.assertTrue(h.getLevel().getBlockState(p).is(FortificationContent.DOOR.get()) && h.getLevel().getBlockState(p).getValue(DoorBlock.OPEN)==open,"Both door halves track open state"); }
    private static void doorPlace(GameTestHelper h,Direction facing,DoorHingeSide hinge) {
        var target=pos(h); var floor=target.below(); put(h,floor,Blocks.STONE.defaultBlockState()); var p=WeaponGameTests.player(h); p.setPos(h.absoluteVec(new Vec3(1,1,1)));
        p.setYRot(switch(facing) { case SOUTH->0; case WEST->90; case NORTH->180; default->270; }); var stack=FortificationContent.DOOR_ITEM.toStack(2); p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        boolean right=hinge==DoorHingeSide.RIGHT; double x=facing==Direction.NORTH?(right?.75:.25):facing==Direction.SOUTH?(right?.25:.75):.5;
        double z=facing==Direction.WEST?(right?.25:.75):facing==Direction.EAST?(right?.75:.25):.5;
        var hit=new BlockHitResult(new Vec3(floor.getX()+x,floor.getY()+1,floor.getZ()+z),Direction.UP,floor,false);
        h.assertTrue(stack.getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Native two-block door placement");
        var s=h.getLevel().getBlockState(target); h.assertValueEqual(s.getValue(DoorBlock.FACING),facing,"Player direction"); h.assertValueEqual(s.getValue(DoorBlock.HINGE),hinge,"Source click hinge"); halves(h,target,false); h.assertValueEqual(stack.getCount(),1,"One placer consumed"); h.succeed();
    }
    private static void doorPair(GameTestHelper h,Direction d,DoorHingeSide hinge,DoubleBlockHalf half) {
        var p=pos(h); var other=p.relative(hinge==DoorHingeSide.LEFT?d.getClockWise():d.getCounterClockWise());
        doorAt(h,p,door(d,hinge,false)); doorAt(h,other,door(d,hinge==DoorHingeSide.LEFT?DoorHingeSide.RIGHT:DoorHingeSide.LEFT,false));
        var click=half==DoubleBlockHalf.LOWER?p:p.above(); toggle(h,click); halves(h,p,true); halves(h,other,true); toggle(h,click); halves(h,p,false); halves(h,other,false); h.succeed();
    }
    private static void doorPower(GameTestHelper h,DoubleBlockHalf half) {
        var p=pos(h); doorAt(h,p,door(Direction.NORTH,DoorHingeSide.LEFT,false)); var power=(half==DoubleBlockHalf.LOWER?p:p.above()).north();
        put(h,power,Blocks.REDSTONE_BLOCK.defaultBlockState()); halves(h,p,true); h.assertTrue(h.getLevel().getBlockState(p).getValue(DoorBlock.POWERED),"Power at either half");
        toggle(h,p.above()); halves(h,p,false); h.assertTrue(h.getLevel().getBlockState(p).getValue(DoorBlock.POWERED),"Manual override preserves power bit until next edge");
        clear(h,power); halves(h,p,false); put(h,power,Blocks.REDSTONE_BLOCK.defaultBlockState()); halves(h,p,true); clear(h,power); halves(h,p,false); h.succeed();
    }
    private static void doorMining(GameTestHelper h,DoubleBlockHalf half,int mode) {
        var p=pos(h); doorAt(h,p,door(Direction.NORTH,DoorHingeSide.LEFT,false)); var type=mode==2?GameType.CREATIVE:GameType.SURVIVAL;
        var player=(ServerPlayer)h.makeMockServerPlayer(type); var cookie=CommonListenerCookie.createInitial(player.getGameProfile(),false);
        var connection=new Connection(PacketFlow.SERVERBOUND); var channel=new EmbeddedChannel(connection); player.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,player,cookie);
        player.setPos(Vec3.atCenterOf(p).add(0,0,3)); player.setItemInHand(InteractionHand.MAIN_HAND,mode==0?new ItemStack(Items.WOODEN_PICKAXE):ItemStack.EMPTY);
        try {
            var gameMode=new ServerPlayerGameMode(player); gameMode.changeGameModeForPlayer(type);
            h.assertTrue(gameMode.destroyBlock(half==DoubleBlockHalf.LOWER?p:p.above()),"Native server mining");
            h.assertTrue(h.getLevel().getBlockState(p).isAir() && h.getLevel().getBlockState(p.above()).isAir(),"Both halves removed");
            h.assertValueEqual(drops(h,p,FortificationContent.DOOR_ITEM.get()),mode==0?1:0,"Exactly one correct-tool survival drop, no creative/hand duplicate");
        } finally { channel.finishAndReleaseAll(); }
        h.succeed();
    }
    private static void doorMismatches(GameTestHelper h) {
        var p=pos(h); var other=p.east(); doorAt(h,p,door(Direction.NORTH,DoorHingeSide.LEFT,false)); doorAt(h,other,door(Direction.NORTH,DoorHingeSide.RIGHT,true));
        toggle(h,p); halves(h,other,true); toggle(h,p); halves(h,other,false);
        // The source deliberately checks only registry identity and previous OPEN, not facing or hinge.
        doorAt(h,other,door(Direction.EAST,DoorHingeSide.LEFT,false)); toggle(h,p); halves(h,other,true);
        clear(h,other); clear(h,other.above()); h.getLevel().setBlock(other,Blocks.IRON_DOOR.defaultBlockState(),2); h.getLevel().setBlock(other.above(),Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
        toggle(h,p); h.assertTrue(!h.getLevel().getBlockState(other).getValue(DoorBlock.OPEN),"Unrelated vanilla door untouched"); h.succeed();
    }
    private static void doorSound(GameTestHelper h) {
        var p=pos(h); doorAt(h,p,door(Direction.NORTH,DoorHingeSide.LEFT,false)); var s=h.getLevel().getBlockState(p); var sounds=new ArrayList<PlayLevelSoundEvent>();
        Consumer<PlayLevelSoundEvent> listener=e->{ if(e.getLevel()==h.getLevel()) sounds.add(e); }; NeoForge.EVENT_BUS.addListener(listener);
        try { toggle(h,p); toggle(h,p.above()); } finally { NeoForge.EVENT_BUS.unregister(listener); }
        h.assertValueEqual(sounds.size(),2,"One custom sound per interaction");
        for(var e:sounds) { h.assertValueEqual(e.getSound().value(),FortificationContent.DOOR_SOUND.get(),"Same source sound for opening and closing"); h.assertValueEqual(e.getOriginalPitch(),1f,"Exact original pitch"); h.assertValueEqual(e.getOriginalVolume(),1f,"Exact original volume"); }
        h.assertValueEqual(s.getDestroySpeed(h.getLevel(),p),8f,"Door hardness"); h.assertValueEqual(s.getBlock().getExplosionResistance(),8f,"Door resistance"); h.assertValueEqual(s.getSoundType(),SoundType.METAL,"Original metal sound type");
        h.assertValueEqual(s.getPistonPushReaction(),PushReaction.BLOCK,"Piston blocked"); h.assertTrue(s.is(BlockTags.DOORS),"Native door tag");
        h.assertTrue(!FortificationContent.DOOR.get().type().canOpenByHand() && !FortificationContent.DOOR.get().type().canOpenByWindCharge(),"Manual override does not turn iron door into AI wooden door or wind target");
        for(var at:List.of(p,p.above())) h.assertTrue(h.getLevel().getBlockState(at).getCloneItemStack(h.getLevel(),at,false).is(FortificationContent.DOOR_ITEM.get()),"Both pick-block halves resolve the placer");
        for(var d:Direction.Plane.HORIZONTAL) for(var hinge:DoorHingeSide.values()) for(boolean open:new boolean[]{false,true}) {
            var state=door(d,hinge,open); var box=state.getShape(h.getLevel(),p).bounds(); near(h,box.getXsize()*box.getYsize()*box.getZsize(),.1875,"Native original 3/16 door slab");
            h.assertValueEqual(BlockState.CODEC.parse(JsonOps.INSTANCE,BlockState.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow()).getOrThrow(),state,"Saved facing hinge open and powered state");
        }
        h.succeed();
    }
    private static void doorInvalid(GameTestHelper h) {
        var p=pos(h); put(h,p.below(),Blocks.STONE.defaultBlockState()); put(h,p.above(),Blocks.STONE.defaultBlockState()); var stack=FortificationContent.DOOR_ITEM.toStack();
        h.assertTrue(!place(h,stack,p.below(),Direction.UP).consumesAction(),"Blocked upper half rejects placement"); h.assertValueEqual(stack.getCount(),1,"Rejected placer retained");
        clear(h,p.above()); h.assertTrue(!place(h,stack,p.below(),Direction.NORTH).consumesAction(),"Only top-face placement allowed");
        doorAt(h,p,door(Direction.NORTH,DoorHingeSide.LEFT,false)); clear(h,p.below()); h.assertTrue(h.getLevel().getBlockState(p).isAir() && h.getLevel().getBlockState(p.above()).isAir(),"Lost floor removes whole door"); h.assertValueEqual(drops(h,p,FortificationContent.DOOR_ITEM.get()),1,"Floor loss drops once"); h.succeed();
    }
    private static void doorHinge(GameTestHelper h) {
        var p=pos(h); put(h,p.below(),Blocks.STONE.defaultBlockState()); var player=WeaponGameTests.player(h); player.setYRot(180);
        var c=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,FortificationContent.DOOR_ITEM.toStack(),new BlockHitResult(Vec3.atBottomCenterOf(p),Direction.UP,p.below(),false));
        for(var b:List.of(Blocks.STONE,Blocks.REDSTONE_BLOCK,Blocks.GLOWSTONE,Blocks.SEA_LANTERN,Blocks.GLASS)) {
            put(h,p.east(),b.defaultBlockState()); var s=FortificationContent.DOOR.get().getStateForPlacement(c);
            h.assertValueEqual(s.getValue(DoorBlock.HINGE),b==Blocks.STONE?DoorHingeSide.RIGHT:DoorHingeSide.LEFT,"Legacy normal-cube balance excludes sources and glass materials");
        }
        clear(h,p.east()); h.getLevel().setBlock(p.west().above(),door(Direction.NORTH,DoorHingeSide.LEFT,false).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),18);
        h.assertValueEqual(FortificationContent.DOOR.get().getStateForPlacement(c).getValue(DoorBlock.HINGE),DoorHingeSide.RIGHT,"Original hinge sees neighboring upper half too");
        h.getLevel().setBlock(p.west().above(),Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),18);
        h.assertValueEqual(FortificationContent.DOOR.get().getStateForPlacement(c).getValue(DoorBlock.HINGE),DoorHingeSide.LEFT,"Other door types do not influence paired hinge"); h.succeed();
    }
    private static void doorForeignHalf(GameTestHelper h) {
        var p=pos(h); doorAt(h,p,door(Direction.NORTH,DoorHingeSide.LEFT,false));
        put(h,p.above(),Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Foreign upper half cannot transmute bunker lower into vanilla door");
        h.assertValueEqual(drops(h,p,FortificationContent.DOOR_ITEM.get()),1,"Invalid pair drops original lower once"); h.succeed();
    }
    private static ItemStack craft(GameTestHelper h,int w,int height,List<ItemStack> grid) {
        var input=CraftingInput.of(w,height,grid); return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input);
    }
    private static void recipes(GameTestHelper h) {
        var sandGrid=new ArrayList<ItemStack>(); sandGrid.add(TGContent.MATERIALS.get("rubberbar").toStack()); for(int i=0;i<8;i++) sandGrid.add(new ItemStack(i%2==0?Items.SAND:Items.RED_SAND));
        var out=craft(h,3,3,sandGrid); h.assertTrue(out.is(FortificationContent.SANDBAGS.get().asItem()) && out.getCount()==8,"Eight sands and one rubber make eight bags");
        var plate=TGContent.MATERIALS.get("plateiron").toStack(); out=craft(h,2,3,Collections.nCopies(6,plate)); h.assertTrue(out.is(FortificationContent.DOOR_ITEM.get()) && out.getCount()==2,"Six iron plates make two doors");
        var n=new ItemStack(Items.IRON_NUGGET); var g=new ItemStack(Items.GLASS_PANE); var r=new ItemStack(Items.REDSTONE);
        for(boolean lantern:new boolean[]{false,true}) {
            var yellow=lamp(lantern?"lantern_yellow":"lamp_yellow"); var white=lamp(lantern?"lantern_white":"lamp_white");
            out=craft(h,3,3,List.of(n,n,n,g,r,g,lantern?n:g,lantern?n:g,lantern?n:g)); h.assertTrue(out.is(yellow.asItem()) && out.getCount()==16,"Original sixteen lights recipe");
            out=craft(h,1,1,List.of(new ItemStack(yellow))); h.assertTrue(out.is(white.asItem()) && out.getCount()==1,"Yellow to white workbench conversion");
            out=craft(h,1,1,List.of(out)); h.assertTrue(out.is(yellow.asItem()) && out.getCount()==1,"White to yellow workbench conversion");
        }
        h.succeed();
    }
}
