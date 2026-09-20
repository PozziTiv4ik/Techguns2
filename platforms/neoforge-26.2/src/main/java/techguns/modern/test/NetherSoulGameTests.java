package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.StructureRules;
import techguns.modern.TGContent;
import techguns.modern.npc.Ghastling;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.NetherMetalContent;
import techguns.modern.world.structure.*;

final class NetherSoulGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("location_soul_registry_and_original_pivot",()->NetherSoulGameTests::registry);
        for(int i=0;i<4;i++) { int turn=i; r.register("location_soul_rotation_"+i,()->h->rotation(h,turn)); }
        r.register("location_soul_original_foundation",()->NetherSoulGameTests::foundation);
        r.register("location_soul_clipping_and_piece_reload",()->NetherSoulGameTests::clipping);
        r.register("location_soul_three_death_ghastling_encounter",()->NetherSoulGameTests::encounter);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_soul_positive_natural_chunks",()->h->natural(h,1));
            r.register("structure_soul_negative_natural_chunks",()->h->natural(h,-1));
        }
    }
    private static NetherSoulStructure structure(ServerLevel l) { return (NetherSoulStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherSoulPiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos chunk) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),chunk,l,b->true); }
    private static NetherSoulPiece piece(GameTestHelper h,int turn) { return new NetherSoulPiece(h.getLevel().getServer().getStructureManager(),h.absolutePos(new BlockPos(2,0,2)).atY(120),turn); }
    private static List<StructureTemplate.StructureBlockInfo> cells(NetherSoulPiece p,Block block) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),block); }
    private static void place(GameTestHelper h,NetherSoulPiece p,BoundingBox clip) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(1),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static BlockPos spawner(NetherSoulPiece p) { return cells(p,NpcSpawnerContent.BLOCK.get()).getFirst().pos(); }
    private static List<Block> palette() {
        var blocks=new ArrayList<Block>(); NetherMetalContent.BLOCKS.values().forEach(b->blocks.add(b.get())); blocks.addAll(List.of(Blocks.AIR,Blocks.SKELETON_SKULL,Blocks.SOUL_SAND,Blocks.GLOWSTONE,Blocks.NETHER_BRICK_FENCE,NpcSpawnerContent.BLOCK.get())); return blocks;
    }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var decoded=(NetherSoulStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow(); h.assertValueEqual(decoded.mediumGrid(),32,"Reserved medium grid"); h.assertValueEqual(decoded.bigGrid(),64,"Reserved large grid");
        var p=piece(h,1); h.assertValueEqual(p.template().getSize(),new Vec3i(13,10,13),"Full source scan is thirteen wide"); h.assertValueEqual(p.template().save(new CompoundTag()).getListOrEmpty("blocks").size(),898,"All source entries"); h.assertValueEqual(p.placeSettings().getRotationPivot(),new BlockPos(5,0,5),"Source registration uses eleven-wide pivot");
        h.assertValueEqual(spawner(p),p.templatePosition().offset(6,6,4),"Spawner rotates about registered pivot, not visual centre"); h.assertValueEqual(cells(p,Blocks.SKELETON_SKULL).size(),24,"Twenty-four original skulls");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherSoulPiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        h.assertTrue(!s.generate(holder,Level.OVERWORLD,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(16,16),0,l,b->true).isValid(),"Source Nether-only dimension guard");
        boolean old=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(16,16))).isEmpty(),"Global location toggle"); } finally { LocationConfig.ENABLED.set(old); } h.succeed();
    }
    private static void rotation(GameTestHelper h,int turn) {
        var p=piece(h,turn); var b=p.getBoundingBox();
        for(var pos:BlockPos.betweenClosed(b.minX(),120,b.minZ(),b.maxX(),129,b.maxZ())) h.getLevel().setBlock(pos,Blocks.NETHERRACK.defaultBlockState(),2);
        place(h,p,b); int count=0;
        for(var block:palette()) for(var cell:cells(p,block)) {
            var actual=h.getLevel().getBlockState(cell.pos()); h.assertTrue(actual.is(block),"Every source cell preserved after rotation, including air");
            if(block==Blocks.SKELETON_SKULL) h.assertValueEqual(actual.getValue(SkullBlock.ROTATION),0,"Original default tile-entity yaw retained"); count++;
        }
        h.assertValueEqual(count,898,"All cells verified"); var expected=StructureRules.rotate(6,6,turn,5,5); h.assertValueEqual(spawner(p),p.templatePosition().offset(expected[0],6,expected[1]),"Exact original spawner transform"); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,2); var metal=NetherMetalContent.BLOCKS.get("nethermetal_plate_black").get(); var base=cells(p,metal).stream().filter(c->c.pos().getY()==120).toList(); h.assertValueEqual(base.size(),9,"Nine source base columns");
        for(var c:base) for(int d=1;d<=17;d++) h.getLevel().setBlock(c.pos().below(d),Blocks.AIR.defaultBlockState(),2);
        var stop=base.getFirst().pos(); for(int d:new int[]{2,4,5}) h.getLevel().setBlock(stop.below(d),Blocks.OBSIDIAN.defaultBlockState(),2);
        place(h,p,p.getBoundingBox());
        for(var c:base) { for(int d=1;d<=16;d++) {
            var expected=c.pos().equals(stop)?(d==2 || d==4 || d==5?Blocks.OBSIDIAN:d>=6?Blocks.AIR:metal):metal;
            h.assertTrue(h.getLevel().getBlockState(c.pos().below(d)).is(expected),"Original depth and two-solid stopping rule");
        } h.assertTrue(h.getLevel().getBlockState(c.pos().below(17)).isAir(),"No foundation beyond sixteen"); } h.succeed();
    }
    private static void clipping(GameTestHelper h) {
        var p=piece(h,1); var o=p.templatePosition(); var box=p.getBoundingBox(); int cut=o.getX()+5;
        place(h,p,new BoundingBox(box.minX(),box.minY(),box.minZ(),cut,box.maxY(),box.maxZ())); h.assertTrue(h.getLevel().getBlockEntity(spawner(p))==null,"First slice cannot create owner in second slice");
        var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel()); var restored=(NetherSoulPiece)LocationContent.SOUL_PIECE.get().load(ctx,p.createTag(ctx));
        h.assertValueEqual(restored.templatePosition(),o,"Origin saved"); h.assertValueEqual(restored.getRotation(),p.getRotation(),"Rotation saved"); h.assertValueEqual(restored.getBoundingBox(),box,"Foundation bounds restored");
        place(h,restored,new BoundingBox(cut+1,box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()));
        var b=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(spawner(restored)); h.assertValueEqual(b.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("ghastling"),1)),"Second slice creates exact source encounter");
        for(var c:cells(restored,Blocks.SKELETON_SKULL)) h.assertValueEqual(h.getLevel().getBlockState(c.pos()).getValue(SkullBlock.ROTATION),0,"Skull rule survives reload"); h.succeed();
    }
    private static void encounter(GameTestHelper h) {
        var p=piece(h,0); place(h,p,p.getBoundingBox()); var pos=spawner(p); var b=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(pos);
        h.assertValueEqual(b.remaining(),3,"Three deaths"); h.assertValueEqual(b.maximum(),2,"Two living"); h.assertValueEqual(b.interval(),200,"Source delay"); h.assertValueEqual(b.range(),1d,"Source range");
        for(int i=0;i<400;i++) NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,b.getBlockState(),b); h.assertValueEqual(b.activeCount(),2,"Real placed template produces two Ghastlings");
        for(int death=1;death<=3;death++) {
            var g=(Ghastling)h.getLevel().getEntity(b.activeIds().iterator().next()); g.removeFreeWill(); h.assertTrue(g.getMainHandItem().isEmpty(),"Species uses its own attack"); h.assertValueEqual(g.spawnerLink(),b.link(),"Unarmed source links to owner");
            g.hurtServer(h.getLevel(),h.getLevel().damageSources().genericKill(),1000); h.assertValueEqual(b.remaining(),3-death,"Actual death charges budget once");
            if(death==1) for(int i=0;i<200;i++) NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,b.getBlockState(),b);
        }
        NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,b.getBlockState(),b); h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Finite original encounter is exhausted"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel().getServer().getLevel(Level.NETHER); var s=structure(l); ChunkPos chosen=null;
        for(int n=1;n<=256;n++) { var c=new ChunkPos(sign*16,sign*16*n); if(s.findGenerationPoint(context(l,c)).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Original second ticket finds a real cave"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Native saved structure start");
        var p=(NetherSoulPiece)start.getPieces().getFirst(); var box=p.getBoundingBox(); for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z);
        var pos=spawner(p); var b=(NpcSpawnerBlockEntity)l.getBlockEntity(pos); h.assertTrue(b!=null,"Actual feature stage placed encounter owner"); h.assertValueEqual(b.remaining(),3,"Natural death budget"); h.assertValueEqual(b.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("ghastling"),1)),"Natural owner chooses Ghastling");
        for(var c:cells(p,Blocks.SKELETON_SKULL)) h.assertTrue(l.getBlockState(c.pos()).is(Blocks.SKELETON_SKULL),"Twenty-four natural skulls survive terrain stages");
        for(var id:List.of(NetherAltarPiece.TEMPLATE,NetherLootPiece.TEMPLATE,NetherAcidPiece.TEMPLATE)) { var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"No competing Techguns site"); }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherSoulPiece.TEMPLATE));
        h.assertTrue(l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),pos,0,false)!=null,"Native locate finds source platform");
        var owner=b.instance(); l.getChunk(chosen.x(),chosen.z()); h.assertValueEqual(((NpcSpawnerBlockEntity)l.getBlockEntity(pos)).instance(),owner,"Repeat chunk request keeps same saved owner");
        com.mojang.logging.LogUtils.getLogger().info("Techguns native NetherSoulPlatform: chunk={}, origin={}, rotation={}, spawner={}",chosen,p.templatePosition(),p.getRotation(),pos); h.succeed();
    }
    private NetherSoulGameTests() {}
}
