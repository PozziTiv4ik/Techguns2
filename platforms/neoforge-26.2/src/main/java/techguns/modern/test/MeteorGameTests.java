package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.npc.*;
import techguns.modern.npc.spawner.*;
import techguns.modern.machine.drill.*;
import techguns.modern.world.*;
import techguns.modern.world.structure.*;

final class MeteorGameTests {
    private static List<Block> fixed() { return List.of(Blocks.STONE,Blocks.GRAVEL,FortificationContent.SANDBAGS.get(),Blocks.IRON_BARS,Blocks.AIR,
            NpcSpawnerContent.BLOCK.get(),BuildingContent.BLOCKS.get("metalpanel_panel_large_border").get(),
            BuildingContent.BLOCKS.get("concrete_brown_light_scaff").get(),FortificationContent.LAMPS.get("lamp_white").get(),
            Blocks.GLASS_PANE,BuildingContent.BLOCKS.get("metalpanel_steelframe_scaffold").get(),FortificationContent.DOOR.get(),
            Blocks.MAGMA_BLOCK,BuildingContent.BLOCKS.get("ladder_metal").get(),Blocks.STRUCTURE_BLOCK); }
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int type=0;type<8;type++) for(int turn=0;turn<4;turn++) { int t=type,q=turn; r.register("location_meteor_type_"+type+"_rotation_"+turn,()->h->rotation(h,t,q)); }
        r.register("location_meteor_all_289_clearing_columns",()->MeteorGameTests::clearing);
        for(int t=0;t<4;t++) { int turn=t; r.register("location_meteor_split_save_"+t,()->h->clipping(h,turn)); }
        r.register("location_meteor_five_finite_army_commando_encounters",()->MeteorGameTests::encounters);
        r.register("location_meteor_registry_tickets_and_toggles",()->MeteorGameTests::registry);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_meteor_positive_natural_chunks",()->h->natural(h,1));
            r.register("structure_meteor_negative_natural_chunks",()->h->natural(h,-1));
        }
    }
    private static MeteorPiece piece(GameTestHelper h,int type,int turn) { return new MeteorPiece(h.getLevel().getServer().getStructureManager(),h.absolutePos(new BlockPos(1,0,1)).atY(140),turn,type,Long.MIN_VALUE+17); }
    private static List<StructureTemplate.StructureBlockInfo> cells(MeteorPiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static void place(GameTestHelper h,MeteorPiece p,BoundingBox clip) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(17),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,MeteorPiece p) { var b=p.getBoundingBox(); var out=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) out.put(pos.immutable(),l.getBlockState(pos)); return out; }
    private static void fill(GameTestHelper h,MeteorPiece p,Block b) { for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,b.defaultBlockState(),2); }
    private static void verify(GameTestHelper h,ServerLevel l,MeteorPiece p,boolean natural) {
        int count=0,overwrittenUndergroundStone=0;
        for(var b:fixed()) for(var c:cells(p,b)) {
            count++; var actual=l.getBlockState(c.pos()); BlockState expected=c.state();
            if(b==Blocks.STRUCTURE_BLOCK) expected=p.mixture(c.nbt().getStringOr("metadata",""),c.pos());
            else if(b==FortificationContent.SANDBAGS.get()) expected=((SandbagBlock)b).connected(expected,l,c.pos());
            else if(b==Blocks.GLASS_PANE) expected=Block.updateFromNeighbourShapes(expected,l,c.pos());
            if(natural && expected.isAir() && actual.is(Blocks.SNOW) && actual.getValue(SnowLayerBlock.LAYERS)==1) continue;
            if(natural && expected.is(Blocks.STONE) && c.pos().getY()<p.templatePosition().getY()+5
                    && (actual.is(Blocks.COAL_ORE) || actual.is(Blocks.GRAVEL) || actual.is(Blocks.DIRT))) {
                // The template wrote stone, then normal terrain decoration replaced a few
                // buried stones before the root chunk became FULL. Resource and encounter
                // cells remain strict; the limit catches a missing or clipped template.
                overwrittenUndergroundStone++; continue;
            }
            h.assertValueEqual(actual,expected,"Original meteor cell or mixture at "+c.pos());
            h.assertTrue(l.getBlockEntity(c.pos())==null || b==NpcSpawnerContent.BLOCK.get(),"No marker block entity remains");
        }
        h.assertValueEqual(count,2265,"All scanned cells kept");
        if(natural) h.assertTrue(overwrittenUndergroundStone<=64,"At most 64 of 1304 stone cells change to native geology");
        for(var c:cells(p,NpcSpawnerContent.BLOCK.get())) {
            var s=(NpcSpawnerBlockEntity)l.getBlockEntity(c.pos()); h.assertTrue(s!=null,"Spawner tile persists");
            h.assertValueEqual(s.remaining(),2,"Two death budget"); h.assertValueEqual(s.maximum(),1,"Single live NPC");
            h.assertValueEqual(s.interval(),200,"Original 200-tick delay"); h.assertValueEqual(s.range(),1.0,"One-block spawn range");
            h.assertValueEqual(s.entries().stream().map(e->e.id().toString()+":"+e.weight()).toList(),List.of("techguns:armysoldier:4","techguns:commando:1"),"Army/Commando weight 4:1");
        }
    }
    private static void rotation(GameTestHelper h,int type,int turn) { var p=piece(h,type,turn); place(h,p,p.getBoundingBox()); verify(h,h.getLevel(),p,false); h.succeed(); }
    private static void clearing(GameTestHelper h) {
        var p=piece(h,2,1); fill(h,p,Blocks.OBSIDIAN); var l=h.getLevel();
        l.setBlock(p.templatePosition().above(36),Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
        place(h,p,p.getBoundingBox()); verify(h,l,p,false);
        var bottom=new HashSet<BlockPos>(); for(var b:List.of(Blocks.STONE,Blocks.AIR)) for(var c:cells(p,b)) if(c.pos().getY()==p.templatePosition().getY()) bottom.add(c.pos());
        h.assertValueEqual(bottom.size(),289,"All 17x17 bottom columns, including one air cell");
        for(var c:bottom) for(int dy=12;dy<=35;dy++) h.assertTrue(l.getBlockState(c.above(dy)).isAir(),"Clearing reaches thirty above surface");
        h.assertTrue(l.getBlockState(p.templatePosition().above(36)).is(Blocks.DIAMOND_BLOCK),"No clearing beyond range"); h.succeed();
    }
    private static void clipping(GameTestHelper h,int turn) {
        var p=piece(h,7,turn); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox()); var expected=snapshot(h.getLevel(),p); fill(h,p,Blocks.OBSIDIAN);
        var box=p.getBoundingBox(); int cut=box.minX()+7; var left=new BoundingBox(box.minX(),box.minY(),box.minZ(),cut,box.maxY(),box.maxZ()); var right=new BoundingBox(cut+1,box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ());
        place(h,p,right); var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel()); var restored=(MeteorPiece)LocationContent.METEOR_PIECE.get().load(ctx,p.createTag(ctx));
        h.assertValueEqual(restored.clusterType(),7,"Type survives native piece save"); h.assertValueEqual(restored.mixtureSeed(),Long.MIN_VALUE+17,"64-bit mixture seed"); h.assertValueEqual(restored.getRotation(),p.getRotation(),"Rotation survives save");
        place(h,restored,left); h.assertValueEqual(snapshot(h.getLevel(),p),expected,"Reverse clipped placement has same 2265 cells, clearance and connections"); h.succeed();
    }
    private static void encounters(GameTestHelper h) {
        var p=piece(h,0,0); place(h,p,p.getBoundingBox()); var holes=cells(p,NpcSpawnerContent.BLOCK.get()); h.assertValueEqual(holes.size(),5,"Five separate encounters");
        var box=p.getBoundingBox(); for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) h.getLevel().getChunk(x,z);
        encounter(h,holes,0,0);
    }
    private static void encounter(GameTestHelper h,List<StructureTemplate.StructureBlockInfo> holes,int index,int deaths) {
        if(index==holes.size()) { h.succeed(); return; }
        var cell=holes.get(index); var s=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(cell.pos());
        if(deaths==0) for(int n=0;n<400;n++) NpcSpawnerBlockEntity.serverTick(h.getLevel(),cell.pos(),s.getBlockState(),s);
        h.assertValueEqual(s.activeCount(),1,"At most one NPC per hole");
        // Entities added during a GameTest callback can enter the lookup on the next server tick.
        h.runAfterDelay(1,()->{
            var mob=(Mob)h.getLevel().getEntity(s.activeIds().iterator().next()); h.assertTrue(mob!=null,"Spawner's live NPC is registered"); mob.removeFreeWill();
            h.assertTrue(mob instanceof ArmySoldier || mob instanceof Commando,"Original encounter variants");
            mob.hurtServer(h.getLevel(),h.getLevel().damageSources().genericKill(),1000);
            for(int n=0;n<200 && !s.isRemoved();n++) NpcSpawnerBlockEntity.serverTick(h.getLevel(),cell.pos(),s.getBlockState(),s);
            if(deaths==0) encounter(h,holes,index,1);
            else { h.assertTrue(h.getLevel().getBlockState(cell.pos()).isAir(),"Hole vanishes after two actual deaths"); encounter(h,holes,index+1,0); }
        });
    }
    private static MeteorStructure structure(ServerLevel l) { return (MeteorStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(MeteorPiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); h.assertTrue(s!=null,"Native meteor structure registered");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,MeteorPiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dimension:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dimension,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(32,32),0,l,b->true).isValid(),"Overworld dimension guard");
        boolean enabled=LocationConfig.ENABLED.get(),ores=LocationConfig.ORE_CLUSTERS.get();
        try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32))).isEmpty(),"Global switch");
              LocationConfig.ENABLED.set(true); LocationConfig.ORE_CLUSTERS.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32))).isEmpty(),"Ore switch");
              LocationConfig.ORE_CLUSTERS.set(true); for(int n=-5;n<=5;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(64,64*n))).isEmpty(),"Big sites reserved"); }
        finally { LocationConfig.ENABLED.set(enabled); LocationConfig.ORE_CLUSTERS.set(ores); } h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel(); var s=structure(l); ChunkPos chosen=null;
        for(int n=129;n<=800;n++) { var c=new ChunkPos(sign*32,sign*32*n); if(s.findGenerationPoint(context(l,c)).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Native surface search finds meteor base in new terrain");
        var start=l.getChunk(chosen.x(),chosen.z()).getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Native saved meteor start");
        var p=(MeteorPiece)start.getPieces().getFirst(); var box=p.getBoundingBox();
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z);
        verify(h,l,p,true);
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,MeteorPiece.TEMPLATE));
        var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(8,5,8),0,false); h.assertTrue(found!=null,"Native locate finds meteor base");
        var before=snapshot(l,p); l.getChunk(chosen.x(),chosen.z()); h.assertValueEqual(snapshot(l,p),before,"Chunk reload cannot reroll mixtures");
        if(sign>0) {
            var target=cells(p,Blocks.STRUCTURE_BLOCK).stream().filter(c->c.nbt().getStringOr("metadata","").equals("techguns:meteor_cluster")).findFirst().orElseThrow().pos();
            var origin=target.west(2); l.setBlock(origin,OreDrillContent.BLOCKS.get("controller").get().defaultBlockState(),3); l.setBlock(origin.east(),OreDrillContent.BLOCKS.get("rod").get().defaultBlockState(),3);
            var d=(OreDrillBlockEntity)l.getBlockEntity(origin); var player=FakePlayerFactory.getMinecraft(l); player.setPos(Vec3.atCenterOf(origin).add(0,2,0));
            h.assertTrue(d.form(player),"Tiny drill recognizes naturally generated meteor cluster");
        }
        com.mojang.logging.LogUtils.getLogger().info("Techguns native OreClusterMeteorBasis: chunk={}, origin={}, rotation={}, type={}, mixtureSeed={}",chosen,p.templatePosition(),p.getRotation(),p.clusterType(),p.mixtureSeed()); h.succeed();
    }
    private MeteorGameTests() {}
}
