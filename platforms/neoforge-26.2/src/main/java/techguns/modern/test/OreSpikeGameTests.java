package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.TGContent;
import techguns.modern.npc.AlienBug;
import techguns.modern.npc.spawner.*;
import techguns.modern.machine.drill.*;
import techguns.modern.world.*;
import techguns.modern.world.structure.*;

final class OreSpikeGameTests {
    private static List<Block> fixed() { return List.of(Blocks.AIR,Blocks.STONE,SlimyContent.EGGS.get(),SlimyContent.TRAIL.get(),NpcSpawnerContent.BLOCK.get()); }
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int type=0;type<7;type++) for(int turn=0;turn<4;turn++) { int t=type,q=turn; r.register("location_spike_type_"+type+"_rotation_"+turn,()->h->rotation(h,t,q)); }
        r.register("location_spike_exact_thirteen_clearing_columns",()->OreSpikeGameTests::clearing);
        for(int t=0;t<4;t++) { int turn=t; r.register("location_spike_split_save_"+t,()->h->clipping(h,turn)); }
        r.register("location_spike_two_independent_bug_encounters",()->OreSpikeGameTests::encounters);
        r.register("location_spike_registry_and_toggles",()->OreSpikeGameTests::registry);
        r.register("slimy_eggs_mining_and_no_slime_physics",()->OreSpikeGameTests::eggs);
        r.register("slimy_trail_attachment_climb_and_drop",()->OreSpikeGameTests::trail);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_spike_positive_natural_chunks",()->h->natural(h,1));
            r.register("structure_spike_negative_natural_chunks",()->h->natural(h,-1));
        }
    }
    private static OreSpikePiece piece(GameTestHelper h,int type,int turn) { return new OreSpikePiece(h.getLevel().getServer().getStructureManager(),h.absolutePos(new BlockPos(1,0,1)).atY(140),turn,type,Long.MIN_VALUE+17); }
    private static List<StructureTemplate.StructureBlockInfo> cells(OreSpikePiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static void place(GameTestHelper h,OreSpikePiece p,BoundingBox clip) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(17),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,OreSpikePiece p) { var b=p.getBoundingBox(); var out=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) out.put(pos.immutable(),l.getBlockState(pos)); return out; }
    private static void fill(GameTestHelper h,OreSpikePiece p,Block b) { for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,b.defaultBlockState(),2); }
    private static void verify(GameTestHelper h,ServerLevel l,OreSpikePiece p,boolean natural) {
        int count=0;
        for(var b:fixed()) for(var c:cells(p,b)) {
            count++; var actual=l.getBlockState(c.pos());
            // FREEZE_TOP_LAYER runs after native structures: only its one-layer snow overlay
            // may occupy scanned air; all resources, ladders and encounters remain exact.
            if(natural && c.state().isAir() && actual.is(Blocks.SNOW) && actual.getValue(SnowLayerBlock.LAYERS)==1) continue;
            h.assertValueEqual(actual,c.state(),"Original fixed cell and rotated facing at "+c.pos());
        }
        for(var c:cells(p,Blocks.STRUCTURE_BLOCK)) {
            count++; var expected=p.mixture(c.nbt().getStringOr("metadata",""),c.pos()); var actual=l.getBlockState(c.pos());
            if(!(natural && expected.isAir() && actual.is(Blocks.SNOW) && actual.getValue(SnowLayerBlock.LAYERS)==1)) h.assertValueEqual(actual,expected,"Saved resource mixture at "+c.pos());
            h.assertTrue(l.getBlockEntity(c.pos())==null,"No template marker left");
        }
        h.assertValueEqual(count,88,"All original scanned cells checked");
    }
    private static void rotation(GameTestHelper h,int type,int turn) { var p=piece(h,type,turn); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox()); verify(h,h.getLevel(),p,false); h.succeed(); }
    private static void clearing(GameTestHelper h) {
        var p=piece(h,1,1); fill(h,p,Blocks.OBSIDIAN); var source=new HashSet<BlockPos>(); var columns=new HashSet<BlockPos>();
        for(var b:new ArrayList<Block>() {{ addAll(fixed()); add(Blocks.STRUCTURE_BLOCK); }}) for(var cell:cells(p,b)) { source.add(cell.pos()); if(cell.pos().getY()==p.templatePosition().getY()) columns.add(cell.pos()); }
        h.assertValueEqual(columns.size(),13,"Original thirteen bottom cells"); var below=p.templatePosition().below(); h.getLevel().setBlock(below,Blocks.DIAMOND_BLOCK.defaultBlockState(),2); place(h,p,p.getBoundingBox());
        for(var entry:snapshot(h.getLevel(),p).entrySet()) if(!source.contains(entry.getKey())) {
            var pos=entry.getKey(); boolean cleared=pos.getY()>p.templatePosition().getY() && columns.contains(pos.atY(p.templatePosition().getY()));
            h.assertTrue(entry.getValue().is(cleared?Blocks.AIR:Blocks.OBSIDIAN),"Clearing does not expand to the enclosing rectangle");
        }
        h.assertTrue(h.getLevel().getBlockState(below).is(Blocks.DIAMOND_BLOCK),"No invented foundation"); h.succeed();
    }
    private static void clipping(GameTestHelper h,int turn) {
        var p=piece(h,5,turn); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox()); var expected=snapshot(h.getLevel(),p); fill(h,p,Blocks.OBSIDIAN);
        var box=p.getBoundingBox(); int cut=box.minX()+3; var left=new BoundingBox(box.minX(),box.minY(),box.minZ(),cut,box.maxY(),box.maxZ()); var right=new BoundingBox(cut+1,box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ());
        place(h,p,right); var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel()); var restored=(OreSpikePiece)LocationContent.SPIKE_PIECE.get().load(ctx,p.createTag(ctx));
        h.assertValueEqual(restored.clusterType(),5,"Type survives native piece save"); h.assertValueEqual(restored.mixtureSeed(),Long.MIN_VALUE+17,"64-bit mixture seed"); h.assertValueEqual(restored.getRotation(),p.getRotation(),"Rotation save");
        place(h,restored,left); h.assertValueEqual(snapshot(h.getLevel(),p),expected,"Reverse clipped placement with intervening save retains all resources, ladders and air"); h.succeed();
    }
    private static void encounters(GameTestHelper h) {
        var p=piece(h,0,0); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox()); var spawners=cells(p,NpcSpawnerContent.BLOCK.get()); h.assertValueEqual(spawners.size(),2,"Two independent holes");
        for(var cell:spawners) {
            var b=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(cell.pos()); h.assertValueEqual(b.remaining(),4,"Each hole has four lives"); h.assertValueEqual(b.maximum(),2,"Two simultaneous bugs per hole"); h.assertValueEqual(b.interval(),200,"Source delay"); h.assertValueEqual(b.range(),1.0,"Source range");
            for(int n=0;n<400;n++) NpcSpawnerBlockEntity.serverTick(h.getLevel(),cell.pos(),b.getBlockState(),b); h.assertValueEqual(b.activeCount(),2,"Placed encounter spawns actual AlienBugs");
            for(int deaths=0;deaths<4;deaths++) {
                var g=(AlienBug)h.getLevel().getEntity(b.activeIds().iterator().next()); g.removeFreeWill(); h.assertValueEqual(g.spawnerLink(),b.link(),"Correct hole ownership"); g.hurtServer(h.getLevel(),h.getLevel().damageSources().genericKill(),1000);
                for(int n=0;n<200 && !b.isRemoved();n++) NpcSpawnerBlockEntity.serverTick(h.getLevel(),cell.pos(),b.getBlockState(),b);
            }
            h.assertTrue(h.getLevel().getBlockState(cell.pos()).isAir(),"Hole disappears after its four actual deaths");
        } h.succeed();
    }
    private static OreSpikeStructure structure(ServerLevel l) { return (OreSpikeStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(OreSpikePiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); h.assertTrue(s!=null,"Native structure registry"); var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,OreSpikePiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dimension:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dimension,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(32,32),0,l,b->true).isValid(),"Overworld dimension guard");
        boolean enabled=LocationConfig.ENABLED.get(),ores=LocationConfig.ORE_CLUSTERS.get();
        try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32))).isEmpty(),"Global switch"); LocationConfig.ENABLED.set(true); LocationConfig.ORE_CLUSTERS.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32))).isEmpty(),"Ore switch"); LocationConfig.ORE_CLUSTERS.set(true); for(int n=-5;n<=5;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(64,64*n))).isEmpty(),"Big locations reserve their sites"); }
        finally { LocationConfig.ENABLED.set(enabled); LocationConfig.ORE_CLUSTERS.set(ores); } h.succeed();
    }
    private static void eggs(GameTestHelper h) {
        var b=SlimyContent.EGGS.get(); var s=b.defaultBlockState(); var pos=h.absolutePos(new BlockPos(3,2,3)); h.getLevel().setBlock(pos,s,2); var p=WeaponGameTests.player(h);
        h.assertValueEqual(s.getDestroySpeed(h.getLevel(),pos),4f,"Source hardness"); h.assertTrue(!b.isStickyBlock(s),"Eggs are ordinary ground blocks, not sticky slime blocks"); h.assertTrue(s.is(BlockTags.MINEABLE_WITH_PICKAXE),"Source pickaxe harvest");
        var pick=new ItemStack(Items.WOODEN_PICKAXE); h.assertTrue(pick.isCorrectToolForDrops(s),"Original level-zero pickaxe"); var drops=Block.getDrops(s,h.getLevel(),pos,null,p,pick); h.assertTrue(drops.size()==1 && drops.getFirst().is(b.asItem()),"Correct block self drop"); h.succeed();
    }
    private static void trail(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(3,2,3)); var l=h.getLevel(); var b=SlimyContent.TRAIL.get(); var s=b.defaultBlockState().setValue(LadderBlock.FACING,Direction.SOUTH);
        l.setBlock(pos.north(),Blocks.STONE.defaultBlockState(),3); h.assertTrue(s.canSurvive(l,pos),"Attaches to ordinary solid face"); l.setBlock(pos,s,3); h.assertTrue(s.is(BlockTags.CLIMBABLE),"Native ladder physics"); h.assertValueEqual(s.getDestroySpeed(l,pos),0f,"Source did not set vanilla ladder hardness");
        l.setBlock(pos.north(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3); h.assertTrue(!s.canSurvive(l,pos) && l.getBlockState(pos).isAir(),"Cannot attach to power source; support change removes trail");
        for(var support:List.of(Blocks.GLASS,Blocks.PISTON,Blocks.STICKY_PISTON,Blocks.GLOWSTONE,Blocks.SEA_LANTERN,Blocks.ICE,Blocks.BEACON,Blocks.STAINED_GLASS.white(),Blocks.SHULKER_BOX)) {
            l.setBlock(pos.north(),support.defaultBlockState(),3); h.assertTrue(!s.canSurvive(l,pos),"Original attachment exclusion: "+support);
        }
        var drops=Block.getDrops(s,l,pos,null); h.assertTrue(drops.size()==1 && drops.getFirst().is(b.asItem()),"One direction-independent item drop"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel(); var s=structure(l); ChunkPos chosen=null;
        for(int n=129;n<=640;n++) { var c=new ChunkPos(sign*32,sign*32*n); var point=s.findGenerationPoint(context(l,c)); if(point.isPresent()) { var candidate=(OreSpikePiece)point.get().getPiecesBuilder().build().pieces().getFirst(); if(sign<0 || candidate.clusterType()==0) { chosen=c; break; } } }
        h.assertTrue(chosen!=null,"Native surface search finds spike in new terrain"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Native saved spike start"); var p=(OreSpikePiece)start.getPieces().getFirst(); var box=p.getBoundingBox();
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z); verify(h,l,p,true);
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,OreSpikePiece.TEMPLATE)); var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(4,2,4),0,false); h.assertTrue(found!=null,"Native locate sees Overworld cluster");
        var before=snapshot(l,p); l.getChunk(chosen.x(),chosen.z()); h.assertValueEqual(snapshot(l,p),before,"Loaded chunks never reroll spike");
        if(sign>0) {
            var target=cells(p,Blocks.STRUCTURE_BLOCK).stream().filter(c->c.nbt().getStringOr("metadata","").equals("techguns:spike_cluster")).findFirst().orElseThrow().pos();
            var origin=target.west(2); l.setBlock(origin,OreDrillContent.BLOCKS.get("controller").get().defaultBlockState(),3); l.setBlock(origin.east(),OreDrillContent.BLOCKS.get("rod").get().defaultBlockState(),3);
            var d=(OreDrillBlockEntity)l.getBlockEntity(origin); var player=FakePlayerFactory.getMinecraft(l); player.setPos(Vec3.atCenterOf(origin).add(0,2,0)); h.assertTrue(d.form(player),"Tiny drill forms against naturally generated coal cluster");
            d.setItem(0,TGContent.MATERIALS.get("oredrillsmall_steel").toStack()); try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(d.energy().insert(57596,tx),57596,"Full original coal-cycle energy inserted"); tx.commit(); }
            for(int i=0;i<2058;i++) OreDrillBlockEntity.tick(l,origin,d.getBlockState(),d);
            var output=d.getItem(2); h.assertTrue(output.getCount()==1 && (output.is(Items.COAL_ORE) || output.is(Items.DIAMOND)),"Natural Overworld coal cluster produces an original 99:1 weighted output: "+output); h.assertValueEqual(d.energy().getAmountAsInt(),0,"Exact source cycle energy"); h.assertTrue(l.getBlockState(target).is(OreClusterContent.BLOCKS.get("ore_cluster_coal").get()),"Infinite cluster preserved");
        }
        com.mojang.logging.LogUtils.getLogger().info("Techguns native OreClusterSpike: chunk={}, origin={}, rotation={}, type={}, mixtureSeed={}, drill={}",chosen,p.templatePosition(),p.getRotation(),p.clusterType(),p.mixtureSeed(),sign>0); h.succeed();
    }
    private OreSpikeGameTests() {}
}
