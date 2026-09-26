package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.*;
import techguns.modern.machine.drill.*;
import techguns.modern.npc.ZombiePigmanSoldier;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.*;
import techguns.modern.world.structure.*;

final class NetherCastleGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int turn=0;turn<4;turn++) {
            int t=turn; r.register("location_nether_castle_rotation_"+turn,()->h->rotation(h,t));
            r.register("location_nether_castle_reverse_split_save_"+turn,()->h->clipping(h,t));
        }
        r.register("location_nether_castle_sixteen_foundation_columns",()->NetherCastleGameTests::foundation);
        r.register("location_nether_castle_four_rifle_guard_encounters",()->NetherCastleGameTests::encounters);
        r.register("location_nether_castle_registry_and_toggles",()->NetherCastleGameTests::registry);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_nether_castle_positive_natural_chunks",()->h->natural(h,1));
            r.register("structure_nether_castle_negative_natural_chunks",()->h->natural(h,-1));
        }
    }
    private static List<Block> palette() { return List.of(NetherMetalContent.BLOCKS.get("nethermetal_panel").get(),NetherMetalContent.BLOCKS.get("nethermetal_plate_black").get(),
            Blocks.NETHER_BRICKS,Blocks.AIR,Blocks.NETHER_BRICK_FENCE,NetherMetalContent.BLOCKS.get("nethermetal_border_red").get(),NpcSpawnerContent.BLOCK.get(),Blocks.STRUCTURE_BLOCK,OreClusterContent.netherCrystal()); }
    private static NetherCastlePiece piece(GameTestHelper h,int turn) { return new NetherCastlePiece(h.getLevel().getServer().getStructureManager(),h.absolutePos(new BlockPos(1,0,1)).atY(120),turn,Long.MIN_VALUE+17+turn); }
    private static List<StructureTemplate.StructureBlockInfo> cells(NetherCastlePiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static void place(GameTestHelper h,NetherCastlePiece p,BoundingBox clip) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(712),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,NetherCastlePiece p) { var b=p.getBoundingBox(); var result=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) result.put(pos.immutable(),l.getBlockState(pos)); return result; }
    private static void fill(GameTestHelper h,NetherCastlePiece p,Block block) { for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,block.defaultBlockState(),2); }
    private static void verify(GameTestHelper h,ServerLevel l,NetherCastlePiece p) {
        int count=0;
        for(var block:palette()) for(var c:cells(p,block)) {
            count++; var expected=c.state();
            if(block==Blocks.STRUCTURE_BLOCK) expected=p.mixture(c.pos());
            else if(block==Blocks.NETHER_BRICK_FENCE) expected=Block.updateFromNeighbourShapes(expected,l,c.pos());
            h.assertValueEqual(l.getBlockState(c.pos()),expected,"Original castle cell and connections at "+c.pos());
            if(block==Blocks.STRUCTURE_BLOCK) h.assertTrue(l.getBlockEntity(c.pos())==null,"No markers remain");
        }
        h.assertValueEqual(count,642,"All original cells, including scanned air");
        var guards=cells(p,NpcSpawnerContent.BLOCK.get()); h.assertValueEqual(guards.size(),4,"Four distinct source guard posts");
        for(var c:guards) {
            var b=(NpcSpawnerBlockEntity)l.getBlockEntity(c.pos()); h.assertTrue(b!=null,"Source block entity loaded");
            h.assertValueEqual(b.remaining(),2,"Two actual deaths per post"); h.assertValueEqual(b.maximum(),1,"One live guard per post");
            h.assertValueEqual(b.interval(),200,"Source interval"); h.assertValueEqual(b.range(),0d,"No random horizontal spawn offset");
            h.assertValueEqual(b.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("zombiepigmansoldier"),1)),"Only pigman soldier");
            h.assertTrue(b.weaponOverride().getItem() instanceof GunItem gun && gun.definition().id().equals("boltaction"),"NBT keeps original bolt-action override");
        }
    }
    private static void rotation(GameTestHelper h,int turn) {
        var p=piece(h,turn); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox()); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void clipping(GameTestHelper h,int turn) {
        var p=piece(h,turn); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox()); var expected=snapshot(h.getLevel(),p); fill(h,p,Blocks.AIR);
        var box=p.getBoundingBox(); int split=box.minX()+5;
        var left=new BoundingBox(box.minX(),box.minY(),box.minZ(),split,box.maxY(),box.maxZ());
        var right=new BoundingBox(split+1,box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ());
        var pending=new BlockPos(box.minX(),p.templatePosition().getY()+2,box.minZ()); h.getLevel().setBlock(pending,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
        place(h,p,right); h.assertTrue(h.getLevel().getBlockState(pending).is(Blocks.DIAMOND_BLOCK),"Other half never overwritten before its turn");
        var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel()); var restored=(NetherCastlePiece)LocationContent.NETHER_CASTLE_PIECE.get().load(ctx,p.createTag(ctx));
        h.assertValueEqual(restored.mixtureSeed(),Long.MIN_VALUE+17+turn,"All seed bits saved"); h.assertValueEqual(restored.getBoundingBox(),box,"Foundation bounds saved");
        h.assertValueEqual(restored.getRotation(),p.getRotation(),"Rotation saved"); place(h,restored,left);
        h.assertValueEqual(snapshot(h.getLevel(),p),expected,"Reverse clips preserve all foundation, mixture and fence cells"); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,1); fill(h,p,Blocks.AIR); var block=NetherMetalContent.BLOCKS.get("nethermetal_plate_black").get();
        var bottoms=cells(p,block).stream().filter(c->c.pos().getY()==p.templatePosition().getY()).toList(); h.assertValueEqual(bottoms.size(),16,"Exactly sixteen scanned bottom supports");
        var blocked=bottoms.getFirst().pos(); for(int depth:List.of(2,4,5)) h.getLevel().setBlock(blocked.below(depth),Blocks.OBSIDIAN.defaultBlockState(),2);
        for(var c:bottoms) h.getLevel().setBlock(c.pos().below(17),Blocks.AIR.defaultBlockState(),2);
        place(h,p,p.getBoundingBox());
        for(var c:bottoms) for(int depth=1;depth<=17;depth++) {
            var pos=c.pos().below(depth); var expected=depth==17?Blocks.AIR:block;
            if(c.pos().equals(blocked)) expected=List.of(2,4,5).contains(depth)?Blocks.OBSIDIAN:depth>=6?Blocks.AIR:block;
            h.assertTrue(h.getLevel().getBlockState(pos).is(expected),"Replaceable-only foundation, two-solid stop and sixteen-cell cap at "+pos);
        }
        var columns=new HashSet<BlockPos>(); bottoms.forEach(c->columns.add(c.pos())); var box=p.getBoundingBox();
        for(int x=box.minX();x<=box.maxX();x++) for(int z=box.minZ();z<=box.maxZ();z++) {
            var pos=new BlockPos(x,p.templatePosition().getY(),z); if(!columns.contains(pos)) h.assertTrue(h.getLevel().getBlockState(pos.below()).isAir(),"No invented rectangle foundation");
        }
        h.succeed();
    }
    private static void encounters(GameTestHelper h) {
        var p=piece(h,0); place(h,p,p.getBoundingBox()); var box=p.getBoundingBox();
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) h.getLevel().getChunk(x,z);
        encounter(h,cells(p,NpcSpawnerContent.BLOCK.get()),0,0);
    }
    private static void encounter(GameTestHelper h,List<StructureTemplate.StructureBlockInfo> guards,int index,int deaths) {
        if(index==guards.size()) { h.succeed(); return; }
        var pos=guards.get(index).pos(); var b=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(pos);
        for(int tick=0;tick<400;tick++) NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,b.getBlockState(),b);
        h.assertValueEqual(b.activeCount(),1,"One live guard regardless of repeated ticks");
        h.runAfterDelay(1,()->{
            var entity=h.getLevel().getEntity(b.activeIds().iterator().next()); h.assertTrue(entity instanceof ZombiePigmanSoldier,"Source NPC spawned");
            var guard=(ZombiePigmanSoldier)entity; guard.removeFreeWill();
            h.assertTrue(guard.getMainHandItem().getItem() instanceof GunItem gun && gun.definition().id().equals("boltaction"),"Weapon override replaces randomly rolled NPC equipment");
            var target=WeaponGameTests.player(h); target.setPos(guard.position().add(0,4,0));
            h.assertTrue(guard.fireAt(target),"Template rifle is usable by actual NPC combat");
            guard.hurtServer(h.getLevel(),h.getLevel().damageSources().genericKill(),10000); NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,b.getBlockState(),b);
            if(deaths==0) { h.assertValueEqual(b.remaining(),1,"First real death charged once"); encounter(h,guards,index,1); }
            else { h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Second death removes only this finite post"); encounter(h,guards,index+1,0); }
        });
    }
    private static NetherCastleStructure structure(ServerLevel l) { return (NetherCastleStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherCastlePiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos chunk) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),chunk,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var restored=(NetherCastleStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow(); h.assertValueEqual(restored.bigGrid(),64,"Reserved big grid in native codec");
        var placement=(RandomSpreadStructurePlacement)l.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(NetherCastlePiece.TEMPLATE).placement();
        h.assertValueEqual(placement.spacing(),32,"Original medium grid"); h.assertValueEqual(placement.separation(),31,"Zero random placement offset");
        for(int sign:List.of(-1,1)) h.assertValueEqual(placement.getPotentialStructureChunk(l.getSeed(),32*sign,64*sign),new ChunkPos(32*sign,64*sign),"Signed modulo lattice");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherCastlePiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dim:List.of(Level.OVERWORLD,Level.END)) h.assertTrue(!s.generate(holder,dim,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(32,32),0,l,b->true).isValid(),"Explicit Nether-only guard");
        boolean enabled=LocationConfig.ENABLED.get(),ores=LocationConfig.ORE_CLUSTERS.get();
        try {
            LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32))).isEmpty(),"Global switch");
            LocationConfig.ENABLED.set(true); LocationConfig.ORE_CLUSTERS.set(false);
            for(int n=-4;n<=4;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32*n))).isEmpty(),"Ore switch");
            LocationConfig.ORE_CLUSTERS.set(true); for(int n=-4;n<=4;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(64,64*n))).isEmpty(),"Big sites excluded");
        } finally { LocationConfig.ENABLED.set(enabled); LocationConfig.ORE_CLUSTERS.set(ores); } h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel().getServer().getLevel(Level.NETHER); var s=structure(l); ChunkPos chosen=null;
        for(int n=129;n<=640;n++) { var c=new ChunkPos(sign*32,sign*32*n); if(s.findGenerationPoint(context(l,c)).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Find a medium castle in native Nether caves"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s);
        h.assertTrue(start!=null && start.isValid(),"Native structure start saved"); var p=(NetherCastlePiece)start.getPieces().getFirst(); var box=p.getBoundingBox();
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z); verify(h,l,p);
        for(var id:List.of(NetherAltarPiece.TEMPLATE,NetherLootPiece.TEMPLATE,NetherAcidPiece.TEMPLATE,NetherSoulPiece.TEMPLATE,NetherClusterPiece.TEMPLATE)) {
            var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"Small locations never occupy this medium site");
        }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherCastlePiece.TEMPLATE));
        h.assertTrue(l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(5,4,5),0,false)!=null,"Native locate finds castle");
        var ids=cells(p,NpcSpawnerContent.BLOCK.get()).stream().map(c->((NpcSpawnerBlockEntity)l.getBlockEntity(c.pos())).instance()).toList();
        var before=snapshot(l,p); l.getChunk(chosen.x(),chosen.z()); h.assertValueEqual(snapshot(l,p),before,"Loaded chunk never rerolls clusters");
        h.assertValueEqual(cells(p,NpcSpawnerContent.BLOCK.get()).stream().map(c->((NpcSpawnerBlockEntity)l.getBlockEntity(c.pos())).instance()).toList(),ids,"Existing guard owners are not recreated");
        if(sign>0) drill(h,l,p);
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural NetherOreClusterCastle: chunk={}, origin={}, rotation={}, mixtureSeed={}, drill={}",chosen,p.templatePosition(),p.getRotation(),p.mixtureSeed(),sign>0); h.succeed();
    }
    private static void drill(GameTestHelper h,ServerLevel l,NetherCastlePiece p) {
        var target=cells(p,OreClusterContent.netherCrystal()).stream().map(StructureTemplate.StructureBlockInfo::pos).min(Comparator.comparingInt(BlockPos::getX)).orElseThrow();
        var origin=target.west(2); l.setBlock(origin,OreDrillContent.BLOCKS.get("controller").get().defaultBlockState(),3); l.setBlock(origin.east(),OreDrillContent.BLOCKS.get("rod").get().defaultBlockState(),3);
        var machine=(OreDrillBlockEntity)l.getBlockEntity(origin); var player=FakePlayerFactory.getMinecraft(l); player.setPos(Vec3.atCenterOf(origin).add(0,2,0));
        h.assertTrue(machine.form(player),"Real drill forms against naturally generated castle cluster"); machine.setItem(0,TGContent.MATERIALS.get("oredrillsmall_obsidiansteel").toStack());
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(machine.energy().insert(288000,tx),288000,"Exact source energy budget inserted"); tx.commit(); }
        for(int tick=0;tick<6001;tick++) OreDrillBlockEntity.tick(l,origin,machine.getBlockState(),machine);
        var out=machine.getItem(2); h.assertTrue(out.getCount()==1 && (out.is(Items.NETHER_QUARTZ_ORE) || out.is(Items.GLOWSTONE) || out.is(Items.BLAZE_ROD)),"One original weighted output: "+out);
        h.assertValueEqual(machine.energy().getAmountAsInt(),0,"6000 working ticks consume exactly 288000 FE"); h.assertTrue(l.getBlockState(target).is(OreClusterContent.netherCrystal()),"Infinite source survives production");
    }
    private NetherCastleGameTests() {}
}
