package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.OreClusters;
import techguns.modern.world.*;
import techguns.modern.world.structure.*;

final class OreClusterGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var v:OreClusters.ALL) r.register("ore_cluster_block_"+v.type(),()->h->block(h,v));
        r.register("ore_cluster_live_config_tooltip",()->OreClusterGameTests::tooltip);
        r.register("ore_cluster_survives_real_explosion",()->OreClusterGameTests::explosion);
        r.register("location_cluster_registry_and_toggles",()->OreClusterGameTests::registry);
        for(int t=0;t<4;t++) { int turn=t; r.register("location_cluster_rotation_"+t,()->h->rotation(h,turn)); }
        r.register("location_cluster_nine_foundation_columns",()->OreClusterGameTests::foundation);
        for(int i=0;i<3;i++) { long seed=new long[]{0,1,Long.MIN_VALUE}[i]; r.register("location_cluster_split_save_"+i,()->h->clipping(h,seed)); }
        r.register("location_cluster_independent_foundation_rolls",()->OreClusterGameTests::variation);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_cluster_positive_natural_chunks",()->h->natural(h,1));
            r.register("structure_cluster_negative_natural_chunks",()->h->natural(h,-1));
        }
    }
    private static void block(GameTestHelper h,OreClusters.Variant v) {
        var l=h.getLevel(); var b=OreClusterContent.BLOCKS.get(v.id()).get(); var s=b.defaultBlockState(); var p=WeaponGameTests.player(h);
        var base=h.absolutePos(new BlockPos(3,0,3)).atY(120); l.setBlock(base,Blocks.STONE.defaultBlockState(),2); l.setBlock(base.above(),Blocks.AIR.defaultBlockState(),2);
        p.setPos(Vec3.atCenterOf(base.offset(2,1,0))); var stack=new ItemStack(b); p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        b.asItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(base).add(0,.5,0),Direction.UP,base,false)));
        h.assertTrue(l.getBlockState(base.above()).is(b),"Actual block item places its own variant");
        h.assertValueEqual(s.getDestroySpeed(l,base.above()),-1f,"Unbreakable source hardness"); h.assertValueEqual(b.getExplosionResistance(),3600000f,"Converted 1.12 setter/getter resistance");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.NETHERITE_PICKAXE));
        h.assertValueEqual(s.getDestroyProgress(p,l,base.above()),0f,"Best ordinary pickaxe cannot mine cluster");
        h.assertTrue(Block.getDrops(s,l,base.above(),null,p,p.getMainHandItem()).isEmpty(),"No block or mineral loot");
        h.assertTrue(!PistonBaseBlock.isPushable(s,l,base.above(),Direction.EAST,true,Direction.EAST),"Piston cannot displace unbreakable cluster");
        h.assertTrue(s.isCollisionShapeFullBlock(l,base.above()) && !s.hasBlockEntity() && !s.isRandomlyTicking(),"Solid non-ticking source block, no invented depletion");
        var c=OreClusterConfig.VALUES.get(v.id()); h.assertValueEqual(c.miningLevel().get(),v.miningLevel(),"TGConfig mining level");
        h.assertValueEqual(c.ores().get(),v.oreMultiplier(),"TGConfig actual output multiplier"); h.assertValueEqual(c.power().get(),v.powerMultiplier(),"TGConfig power multiplier"); h.succeed();
    }
    private static void tooltip(GameTestHelper h) {
        var c=OreClusterConfig.VALUES.get("ore_cluster_nether_crystal"); int mining=c.miningLevel().get(); double power=c.power().get(),ores=c.ores().get();
        try {
            c.miningLevel().set(7); c.power().set(2.5); c.ores().set(9.3);
            var stack=new ItemStack(OreClusterContent.netherCrystal()); var lines=new ArrayList<Component>();
            stack.getItem().appendHoverText(stack,Item.TooltipContext.EMPTY,TooltipDisplay.DEFAULT,lines::add,TooltipFlag.NORMAL);
            h.assertValueEqual(lines.size(),3,"Original three cluster lines");
            h.assertTrue(lines.get(0).getString().endsWith(": 7") && lines.get(1).getString().endsWith(": x2.5") && lines.get(2).getString().endsWith(": x9.3"),"Tooltips use live server settings, in source order");
        } finally { c.miningLevel().set(mining); c.power().set(power); c.ores().set(ores); }
        h.succeed();
    }
    private static void explosion(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(3,0,3)).atY(120); var l=h.getLevel(); l.setBlock(pos,OreClusterContent.netherCrystal().defaultBlockState(),2);
        var fragile=pos.above(2); l.setBlock(fragile,Blocks.DIRT.defaultBlockState(),2);
        l.explode(null,fragile.getX()+.5,fragile.getY()+.5,fragile.getZ()+.5,4f,Level.ExplosionInteraction.BLOCK);
        h.assertTrue(l.getBlockState(fragile).isAir(),"Explosion really destroys ordinary terrain"); h.assertTrue(l.getBlockState(pos).is(OreClusterContent.netherCrystal()),"Original cluster survives blast"); h.succeed();
    }
    private static NetherClusterStructure structure(ServerLevel l) { return (NetherClusterStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherClusterPiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),c,l,b->true); }
    private static NetherClusterPiece piece(GameTestHelper h,int turn,long seed) { return new NetherClusterPiece(h.getLevel().getServer().getStructureManager(),h.absolutePos(new BlockPos(1,0,1)).atY(120),turn,seed); }
    private static List<StructureTemplate.StructureBlockInfo> cells(NetherClusterPiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static void place(GameTestHelper h,NetherClusterPiece p,BoundingBox clip,long seed) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(seed),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,NetherClusterPiece p) {
        var box=p.getBoundingBox(); var out=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())) out.put(pos.immutable(),l.getBlockState(pos)); return out;
    }
    private static void clear(GameTestHelper h,NetherClusterPiece p) { for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var restored=(NetherClusterStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow();
        h.assertValueEqual(restored.mediumGrid(),32,"Reserved medium grid"); h.assertValueEqual(restored.bigGrid(),64,"Reserved big grid");
        h.assertValueEqual(s.step(),net.minecraft.world.level.levelgen.GenerationStep.Decoration.TOP_LAYER_MODIFICATION,"Placement after vanilla delta features");
        var t=l.getServer().getStructureManager().get(NetherClusterPiece.TEMPLATE).orElseThrow(); h.assertValueEqual(t.getSize(),new Vec3i(3,3,3),"Source dimensions");
        h.assertValueEqual(t.save(new CompoundTag()).getListOrEmpty("blocks").size(),27,"Every source cell retained");
        var p=piece(h,0,0); h.assertValueEqual(cells(p,OreClusterContent.netherCrystal()).size(),1,"One guaranteed cluster core"); h.assertValueEqual(cells(p,Blocks.STRUCTURE_BLOCK).size(),6,"Six independently weighted cells");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherClusterPiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dim:List.of(Level.OVERWORLD,Level.END)) h.assertTrue(!s.generate(holder,dim,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(16,16),0,l,b->true).isValid(),"Explicit Nether guard");
        boolean enabled=LocationConfig.ENABLED.get(),ores=LocationConfig.ORE_CLUSTERS.get();
        try {
            LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(16,16))).isEmpty(),"Global toggle");
            LocationConfig.ENABLED.set(true); LocationConfig.ORE_CLUSTERS.set(false);
            for(int n=-64;n<=64;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(16,16*n))).isEmpty(),"Disabled ore candidate never selected");
        } finally { LocationConfig.ENABLED.set(enabled); LocationConfig.ORE_CLUSTERS.set(ores); } h.succeed();
    }
    private static void rotation(GameTestHelper h,int turn) {
        var p=piece(h,turn,17); for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,Blocks.OBSIDIAN.defaultBlockState(),2);
        place(h,p,p.getBoundingBox(),1); int count=0;
        for(var b:List.of(Blocks.MAGMA_BLOCK,Blocks.AIR,OreClusterContent.netherCrystal(),Blocks.STRUCTURE_BLOCK)) for(var c:cells(p,b)) {
            var actual=h.getLevel().getBlockState(c.pos()); count++;
            if(b==Blocks.STRUCTURE_BLOCK) h.assertTrue(actual.is(OreClusterContent.netherCrystal()) || actual.is(c.nbt().getStringOr("metadata","").equals(NetherClusterPiece.ROCK_MARKER)?Blocks.NETHERRACK:Blocks.AIR),"Correct weighted alternative at "+c.pos());
            else h.assertTrue(actual.is(b),"Fixed block or carved air at "+c.pos());
            h.assertTrue(h.getLevel().getBlockEntity(c.pos())==null,"No marker entity left in world");
        }
        h.assertValueEqual(count,27,"All source cells verified under rotation"); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,1,17); clear(h,p); var center=p.templatePosition().offset(1,0,1); var fixed=cells(p,Blocks.MAGMA_BLOCK);
        h.assertValueEqual(fixed.size(),8,"Eight magma bottom cells plus mixed centre"); var solid=fixed.getFirst().pos().below(); h.getLevel().setBlock(solid,Blocks.OBSIDIAN.defaultBlockState(),2);
        for(var c:fixed) h.getLevel().setBlock(c.pos().below(3),Blocks.AIR.defaultBlockState(),2); h.getLevel().setBlock(center.below(3),Blocks.AIR.defaultBlockState(),2);
        place(h,p,p.getBoundingBox(),1);
        for(var c:fixed) { for(int depth=1;depth<=2;depth++) h.assertTrue(h.getLevel().getBlockState(c.pos().below(depth)).is(c.pos().below(depth).equals(solid)?Blocks.OBSIDIAN:Blocks.MAGMA_BLOCK),"Two-deep replaceable-only magma support"); h.assertTrue(h.getLevel().getBlockState(c.pos().below(3)).isAir(),"Never a third foundation layer"); }
        for(int depth=1;depth<=2;depth++) { var s=h.getLevel().getBlockState(center.below(depth)); h.assertTrue(s.is(Blocks.NETHERRACK) || s.is(OreClusterContent.netherCrystal()),"Mixed foundation centre is resolved, not a structure marker"); }
        h.assertTrue(h.getLevel().getBlockState(center.below(3)).isAir(),"Mixed centre also stops at depth two"); h.succeed();
    }
    private static void clipping(GameTestHelper h,long seed) {
        var p=piece(h,2,seed); clear(h,p); place(h,p,p.getBoundingBox(),1); var expected=snapshot(h.getLevel(),p); clear(h,p);
        var box=p.getBoundingBox(); int cut=box.minX()+1; var left=new BoundingBox(box.minX(),box.minY(),box.minZ(),cut,box.maxY(),box.maxZ()); var right=new BoundingBox(cut+1,box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ());
        var untouched=p.templatePosition().offset(1,1,1); h.getLevel().setBlock(untouched,Blocks.DIAMOND_BLOCK.defaultBlockState(),2); place(h,p,right,777);
        h.assertTrue(h.getLevel().getBlockState(untouched).is(Blocks.DIAMOND_BLOCK),"Clip prevents writes to pending half");
        var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel()); var saved=(NetherClusterPiece)LocationContent.CLUSTER_PIECE.get().load(ctx,p.createTag(ctx));
        h.assertValueEqual(saved.mixtureSeed(),seed,"Seed all 64 bits survive save"); h.assertValueEqual(saved.getBoundingBox(),box,"Saved foundation bounds"); h.assertValueEqual(saved.getRotation(),p.getRotation(),"Saved rotation");
        place(h,saved,left,999); h.assertValueEqual(snapshot(h.getLevel(),p),expected,"Saving between opposite chunk order preserves every template and foundation cell"); h.succeed();
    }
    private static void variation(GameTestHelper h) {
        var combinations=new HashSet<List<BlockState>>();
        for(int seed=0;seed<64;seed++) { var p=piece(h,0,seed); clear(h,p); place(h,p,p.getBoundingBox(),1); var c=p.templatePosition().offset(1,0,1); combinations.add(List.of(h.getLevel().getBlockState(c),h.getLevel().getBlockState(c.below()),h.getLevel().getBlockState(c.below(2)))); }
        h.assertValueEqual(combinations.size(),8,"All eight independent mixtures of centre and two foundation cells occur"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel().getServer().getLevel(Level.NETHER); var s=structure(l); ChunkPos chosen=null;
        for(int n=1;n<=256;n++) { var c=new ChunkPos(sign*16,sign*16*n); if(s.findGenerationPoint(context(l,c)).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Original selection and real cave find cluster location"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s);
        h.assertTrue(start!=null && start.isValid(),"Actual saved structure start"); var p=(NetherClusterPiece)start.getPieces().getFirst(); var box=p.getBoundingBox();
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z);
        h.assertTrue(l.getBlockState(p.templatePosition().offset(1,1,1)).is(OreClusterContent.netherCrystal()),"Guaranteed crystal core present in real generated terrain");
        for(var b:List.of(Blocks.MAGMA_BLOCK,Blocks.AIR)) for(var c:cells(p,b)) h.assertTrue(l.getBlockState(c.pos()).is(b),"Real source magma/air cell survives population");
        for(var c:cells(p,Blocks.STRUCTURE_BLOCK)) { var state=l.getBlockState(c.pos()); h.assertTrue(state.is(OreClusterContent.netherCrystal()) || state.is(c.nbt().getStringOr("metadata","").equals(NetherClusterPiece.ROCK_MARKER)?Blocks.NETHERRACK:Blocks.AIR),"Real marker resolved"); }
        for(var id:List.of(NetherAltarPiece.TEMPLATE,NetherLootPiece.TEMPLATE,NetherAcidPiece.TEMPLATE,NetherSoulPiece.TEMPLATE)) { var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"Five mutually exclusive candidates"); }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherClusterPiece.TEMPLATE));
        var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(1,1,1),0,false);
        h.assertTrue(found!=null,"Native locate finds generated cluster"); h.assertValueEqual(new ChunkPos(found.getFirst().getX()>>4,found.getFirst().getZ()>>4),chosen,"Locate saved start");
        var before=snapshot(l,p); l.getChunk(chosen.x(),chosen.z()); h.assertValueEqual(snapshot(l,p),before,"Ready chunks never reroll cluster cells");
        com.mojang.logging.LogUtils.getLogger().info("Techguns native NetherOreClusterSmall: chunk={}, origin={}, rotation={}, mixtureSeed={}, biome={}",chosen,p.templatePosition(),p.getRotation(),p.mixtureSeed(),l.getBiome(p.templatePosition()).unwrapKey().orElseThrow().identifier()); h.succeed();
    }
    private OreClusterGameTests() {}
}
