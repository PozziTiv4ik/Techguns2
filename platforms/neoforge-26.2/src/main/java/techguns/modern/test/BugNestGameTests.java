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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.BugNestLayout;
import techguns.modern.npc.AlienBug;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.SlimyContent;
import techguns.modern.world.structure.*;

final class BugNestGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int i=0;i<6;i++) { int seed=i; r.register("location_bugnest_seed_"+i,()->h->placement(h,seed)); }
        for(int sign:List.of(-1,1)) r.register("location_bugnest_reverse_chunk_save_"+(sign<0?"negative":"positive"),()->h->clipping(h,sign));
        r.register("location_bugnest_seven_deaths_three_live",()->BugNestGameTests::encounter);
        r.register("location_bugnest_hardened_sand_harvest",()->BugNestGameTests::sand);
        r.register("location_bugnest_registry_and_toggles",()->BugNestGameTests::registry);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_bugnest_positive_natural_chunks",()->h->natural(h,1));
            r.register("structure_bugnest_negative_natural_chunks",()->h->natural(h,-1));
        }
    }
    private static BugNestPiece piece(int slot,int sign,long seed) {
        // Nests exceed the small shared fixture; separate sites prevent tests overwriting each other.
        var plan=BugNestLayout.create(new BugNestLayout.Pos(sign*(1500000+slot*256),120,sign*1500000),16+(int)seed,31-(int)seed,seed,seed*7907-19,p->BugNestLayout.TERRAIN);
        return new BugNestPiece(plan,seed,seed*7907-19);
    }
    private static void load(ServerLevel l,BugNestPiece p) {
        var b=p.getBoundingBox(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) l.getChunk(x,z);
    }
    private static void place(GameTestHelper h,BugNestPiece p,BoundingBox clip) {
        var at=p.getLocatorPosition(); p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(72),clip,new ChunkPos(at.getX()>>4,at.getZ()>>4),at);
    }
    private static void verify(GameTestHelper h,ServerLevel l,BugNestPiece p) {
        int holes=0;
        for(var cell:p.plan().cells().entrySet()) {
            var pos=BugNestPiece.pos(cell.getKey()); h.assertValueEqual(l.getBlockState(pos),BugNestPiece.state(cell.getValue()),"Saved nest cell at "+pos);
            if(cell.getValue()==BugNestLayout.SPAWNER) {
                holes++; var be=(NpcSpawnerBlockEntity)l.getBlockEntity(pos); h.assertTrue(be!=null,"Native spawner tile");
                h.assertValueEqual(be.remaining(),7,"Source finite death budget"); h.assertValueEqual(be.maximum(),3,"Source active limit");
                h.assertValueEqual(be.interval(),150,"Source delay"); h.assertValueEqual(be.range(),1.0,"Source spawn range");
                h.assertValueEqual(be.entries().stream().map(e->e.id()+":"+e.weight()).toList(),List.of("techguns:alienbug:1"),"Only AlienBug encounters");
            }
        }
        h.assertTrue(holes>=5 && holes<=10,"Five branches and optional secondary-room encounters");
    }
    private static void placement(GameTestHelper h,int seed) {
        var p=piece(seed,1,seed); load(h.getLevel(),p);
        for(var pos:p.plan().cells().keySet()) h.getLevel().setBlock(BugNestPiece.pos(pos),Blocks.OBSIDIAN.defaultBlockState(),2);
        var outside=new BlockPos(p.getBoundingBox().maxX()+1,120,p.getBoundingBox().maxZ()); h.getLevel().setBlock(outside,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
        place(h,p,p.getBoundingBox()); verify(h,h.getLevel(),p);
        h.assertTrue(h.getLevel().getBlockState(outside).is(Blocks.DIAMOND_BLOCK),"Writes remain inside authored cells"); h.succeed();
    }
    private static void clipping(GameTestHelper h,int sign) {
        var p=piece(8,sign,3); load(h.getLevel(),p); var b=p.getBoundingBox();
        var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel());
        var chunks=new ArrayList<BoundingBox>(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) chunks.add(new BoundingBox(x*16,b.minY(),z*16,x*16+15,b.maxY(),z*16+15));
        Collections.reverse(chunks);
        for(var clip:chunks) {
            place(h,p,clip); p=(BugNestPiece)LocationContent.BUGNEST_PIECE.get().load(ctx,p.createTag(ctx));
        }
        verify(h,h.getLevel(),p); h.assertValueEqual(p.plan(),piece(8,sign,3).plan(),"Full plan, graph and attachments survive each chunk save");
        h.assertValueEqual(p.layoutSeed(),3L,"Layout seed saved"); h.assertValueEqual(p.decorSeed(),3L*7907-19,"Decoration seed saved"); h.succeed();
    }
    private static void encounter(GameTestHelper h) {
        var p=piece(10,1,1); load(h.getLevel(),p); place(h,p,p.getBoundingBox());
        var pos=p.plan().cells().entrySet().stream().filter(e->e.getValue()==BugNestLayout.SPAWNER).map(e->BugNestPiece.pos(e.getKey())).findFirst().orElseThrow();
        var be=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(pos); h.assertValueEqual(be.delay(),200,"Initial legacy delay differs from 150-tick interval");
        drain(h,pos,0);
    }
    private static void drain(GameTestHelper h,BlockPos pos,int killed) {
        h.getLevel().getChunkAt(pos); var be=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(pos);
        for(int tick=0;tick<700;tick++) NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,be.getBlockState(),be);
        h.assertValueEqual(be.activeCount(),Math.min(3,7-killed),"Active reservations never exceed remaining death budget or three");
        h.runAfterDelay(1,()->{
            var live=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(pos); var ids=live.activeIds();
            for(var id:ids) {
                var mob=(Mob)h.getLevel().getEntity(id); h.assertTrue(mob instanceof AlienBug,"Source spider NPC materialized"); mob.removeFreeWill();
                mob.hurtServer(h.getLevel(),h.getLevel().damageSources().genericKill(),1000);
            }
            NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,live.getBlockState(),live);
            if(killed+ids.size()==7) { h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Finite encounter removes its hole after seven actual deaths"); h.succeed(); }
            else drain(h,pos,killed+ids.size());
        });
    }
    private static void sand(GameTestHelper h) {
        var b=SlimyContent.SAND.get(); var s=b.defaultBlockState(); var pos=h.absolutePos(new BlockPos(2,3,2)); h.getLevel().setBlock(pos,s,3);
        h.assertValueEqual(s.getDestroySpeed(h.getLevel(),pos),3f,"Original hardness"); h.assertTrue(s.is(BlockTags.MINEABLE_WITH_SHOVEL),"Original shovel tool");
        h.assertTrue(!(b instanceof FallingBlock),"Hardened sand remains suspended");
        var drops=Block.getDrops(s,h.getLevel(),pos,null,WeaponGameTests.player(h),ItemStack.EMPTY);
        h.assertTrue(drops.size()==1 && drops.getFirst().is(b.asItem()),"Original sand material drops without requiring a tool"); h.succeed();
    }
    private static BugNestStructure structure(ServerLevel l) { return (BugNestStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BugNestPiece.ID); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); h.assertTrue(s!=null,"Native registry entry");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,BugNestPiece.ID)); var g=l.getChunkSource().getGenerator();
        for(var dim:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dim,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(32,32),0,l,b->true).isValid(),"Overworld only");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32))).isEmpty(),"Global generation switch"); }
        finally { LocationConfig.ENABLED.set(enabled); }
        for(int n=-5;n<=5;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(64,n*64))).isEmpty(),"Big sites reserved"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel(); var s=structure(l); ChunkPos chosen=null;
        for(int n=129;n<=1800;n++) { var c=new ChunkPos(sign*32,sign*32*n); if(s.findGenerationPoint(context(l,c)).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Find sandy native nest in new terrain");
        var start=l.getChunk(chosen.x(),chosen.z()).getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Native nest start persisted");
        var p=(BugNestPiece)start.getPieces().getFirst(); load(l,p); verify(h,l,p);
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,BugNestPiece.ID));
        h.assertTrue(l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.getLocatorPosition(),0,false)!=null,"Native locate finds nest");
        var ctx=StructurePieceSerializationContext.fromLevel(l); var restored=(BugNestPiece)LocationContent.BUGNEST_PIECE.get().load(ctx,p.createTag(ctx));
        h.assertValueEqual(restored.plan(),p.plan(),"Saved natural nest cannot reroll");
        boolean ores=LocationConfig.ORE_CLUSTERS.get(); try {
            LocationConfig.ORE_CLUSTERS.set(false); boolean found=false;
            for(int n=129;n<600 && !found;n++) found=s.findGenerationPoint(context(l,new ChunkPos(sign*32,sign*32*n))).isPresent();
            h.assertTrue(found,"Nest continues generating with ore structures disabled");
        } finally { LocationConfig.ORE_CLUSTERS.set(ores); }
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural AlienBugNest: chunk={}, rooms={}, cells={}, box={}, layoutSeed={}, decorSeed={}",chosen,p.plan().rooms().size(),p.plan().cells().size(),p.getBoundingBox(),p.layoutSeed(),p.decorSeed()); h.succeed();
    }
    private BugNestGameTests() {}
}
