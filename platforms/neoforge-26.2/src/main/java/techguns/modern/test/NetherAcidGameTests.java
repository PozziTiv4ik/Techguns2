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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.*;
import techguns.modern.fluid.TGFluids;
import techguns.modern.machine.reaction.*;
import techguns.modern.world.structure.*;

final class NetherAcidGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("location_acid_registry_and_source_template",()->NetherAcidGameTests::registry);
        for(int i=0;i<4;i++) { int turn=i; r.register("location_acid_rotation_"+i,()->h->rotation(h,turn)); }
        r.register("location_acid_sixty_foundation_columns",()->NetherAcidGameTests::foundation);
        for(int i=0;i<3;i++) { long seed=new long[]{0,1,Long.MIN_VALUE}[i]; r.register("location_acid_split_saved_mixture_"+i,()->h->clipping(h,seed)); }
        r.register("location_acid_distinct_saved_seeds",()->NetherAcidGameTests::variation);
        r.register("location_acid_contact_and_bucket",()->NetherAcidGameTests::damage);
        r.register("location_acid_emptied_source_stays_flowing",()->NetherAcidGameTests::noInfinite);
        r.register("location_acid_downward_flow",()->NetherAcidGameTests::flow);
        r.register("structure_acid_world_buckets_to_titanium",()->NetherAcidGameTests::titanium);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_acid_positive_natural_chunks",()->h->natural(h,1));
            r.register("structure_acid_negative_natural_chunks",()->h->natural(h,-1));
        }
    }
    private static NetherAcidStructure structure(ServerLevel l) { return (NetherAcidStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherAcidPiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos chunk) {
        var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),chunk,l,b->true);
    }
    private static NetherAcidPiece piece(GameTestHelper h,int turn,long seed) { return new NetherAcidPiece(h.getLevel().getServer().getStructureManager(),h.absolutePos(new BlockPos(1,0,1)).atY(120),turn,seed); }
    private static List<StructureTemplate.StructureBlockInfo> cells(NetherAcidPiece p,Block block) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),block); }
    private static void place(GameTestHelper h,NetherAcidPiece p,BoundingBox clip,long populationSeed) {
        p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(populationSeed),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition());
    }
    private static NetherAcidPiece placed(GameTestHelper h) { var p=piece(h,0,17); place(h,p,p.getBoundingBox(),1); return p; }
    private static Map<BlockPos,BlockState> mixture(ServerLevel level,NetherAcidPiece p) {
        Map<BlockPos,BlockState> result=new HashMap<>(); for(var c:cells(p,Blocks.STRUCTURE_BLOCK)) result.put(c.pos(),level.getBlockState(c.pos())); return result;
    }
    private static void clear(GameTestHelper h,NetherAcidPiece p) {
        var b=p.getBoundingBox(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);
    }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var decoded=(NetherAcidStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow();
        h.assertValueEqual(decoded.mediumGrid(),32,"Reserved medium grid"); h.assertValueEqual(decoded.bigGrid(),64,"Reserved big grid");
        h.assertValueEqual(decoded.step(),net.minecraft.world.level.levelgen.GenerationStep.Decoration.TOP_LAYER_MODIFICATION,"Place after vanilla deltas that would overwrite the pool");
        var t=l.getServer().getStructureManager().get(NetherAcidPiece.TEMPLATE).orElseThrow(); h.assertValueEqual(t.getSize(),new Vec3i(9,6,9),"Original complete scanned dimensions");
        h.assertValueEqual(t.save(new CompoundTag()).getListOrEmpty("blocks").size(),269,"Every source cell retained");
        var p=piece(h,0,1); h.assertValueEqual(cells(p,TGFluids.ACID.block.get()).size(),9,"Nine permanent sources"); h.assertValueEqual(cells(p,Blocks.STRUCTURE_BLOCK).size(),12,"Twelve independent weighted cells");
        for(var c:cells(p,Blocks.STRUCTURE_BLOCK)) { h.assertValueEqual(c.nbt().getStringOr("metadata",""),NetherAcidPiece.MIXTURE_MARKER,"Native data marker"); h.assertValueEqual(c.pos().getY(),123,"Random cells only on source waterline"); }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherAcidPiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dimension:List.of(Level.OVERWORLD,Level.END)) h.assertTrue(!s.generate(holder,dimension,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(16,16),0,l,b->true).isValid(),"Original Nether-only guard survives permissive biome predicate");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(16,16))).isEmpty(),"Global structures toggle"); } finally { LocationConfig.ENABLED.set(enabled); }
        h.succeed();
    }
    private static void rotation(GameTestHelper h,int turn) {
        var p=piece(h,turn,17); var b=p.getBoundingBox();
        for(var pos:BlockPos.betweenClosed(b.minX(),120,b.minZ(),b.maxX(),125,b.maxZ())) h.getLevel().setBlock(pos,Blocks.OBSIDIAN.defaultBlockState(),2);
        place(h,p,b,1); int count=0;
        for(var block:List.of(Blocks.NETHERRACK,Blocks.AIR,Blocks.SOUL_SAND,TGFluids.ACID.block.get(),Blocks.STRUCTURE_BLOCK)) for(var c:cells(p,block)) {
            var actual=h.getLevel().getBlockState(c.pos()); count++;
            if(block==Blocks.STRUCTURE_BLOCK) h.assertTrue(actual.is(Blocks.NETHERRACK) || actual.is(TGFluids.ACID.block.get()) && actual.getFluidState().isSource(),"Every marker becomes an original weighted block");
            else h.assertTrue(actual.is(block),"Every fixed source cell rotates with geometry, including carved air");
            if(actual.is(TGFluids.ACID.block.get())) h.assertTrue(actual.getFluidState().isSource(),"Original level zero sources");
            h.assertTrue(!actual.is(Blocks.STRUCTURE_BLOCK) && h.getLevel().getBlockEntity(c.pos())==null,"No data markers or block entities left in finished location");
        }
        h.assertValueEqual(count,269,"All original cells validated"); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,3,17); var base=cells(p,Blocks.NETHERRACK).stream().filter(c->c.pos().getY()==120).toList(); h.assertValueEqual(base.size(),60,"Sixty original bottom cells");
        for(var c:base) for(int d=1;d<=17;d++) h.getLevel().setBlock(c.pos().below(d),Blocks.AIR.defaultBlockState(),2);
        var stop=base.getFirst().pos(); for(int d:new int[]{2,4,5}) h.getLevel().setBlock(stop.below(d),Blocks.OBSIDIAN.defaultBlockState(),2);
        place(h,p,p.getBoundingBox(),1);
        for(var c:base) {
            for(int d=1;d<=16;d++) {
                var expected=c.pos().equals(stop)?(d==2 || d==4 || d==5?Blocks.OBSIDIAN:d>=6?Blocks.AIR:Blocks.NETHERRACK):Blocks.NETHERRACK;
                h.assertTrue(h.getLevel().getBlockState(c.pos().below(d)).is(expected),"Source replaceable-only depth and two-consecutive-solids rule");
            }
            h.assertTrue(h.getLevel().getBlockState(c.pos().below(17)).isAir(),"No extra foundation depth");
        }
        h.succeed();
    }
    private static void clipping(GameTestHelper h,long seed) {
        var p=piece(h,1,seed); place(h,p,p.getBoundingBox(),1); var expected=mixture(h.getLevel(),p); clear(h,p);
        var box=p.getBoundingBox(); int cut=p.templatePosition().getX()+4;
        var left=new BoundingBox(box.minX(),box.minY(),box.minZ(),cut,box.maxY(),box.maxZ()); var right=new BoundingBox(cut+1,box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ());
        var untouched=cells(p,Blocks.STRUCTURE_BLOCK).stream().filter(c->left.isInside(c.pos())).findFirst().orElseThrow().pos(); h.getLevel().setBlock(untouched,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
        place(h,p,right,777); h.assertTrue(h.getLevel().getBlockState(untouched).is(Blocks.DIAMOND_BLOCK),"Chunk clip excludes unprocessed marker");
        var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel()); var restored=(NetherAcidPiece)LocationContent.ACID_PIECE.get().load(ctx,p.createTag(ctx));
        h.assertValueEqual(restored.mixtureSeed(),seed,"All seed bits survive piece NBT"); h.assertValueEqual(restored.templatePosition(),p.templatePosition(),"Saved origin"); h.assertValueEqual(restored.getRotation(),p.getRotation(),"Saved rotation"); h.assertValueEqual(restored.getBoundingBox(),box,"Saved foundation bounds");
        place(h,restored,left,999); h.assertValueEqual(mixture(h.getLevel(),restored),expected,"Opposite chunk order, intervening save and different population RNG preserve identical mixture"); h.succeed();
    }
    private static void variation(GameTestHelper h) {
        Set<Map<BlockPos,BlockState>> patterns=new HashSet<>(); boolean acid=false,solid=false;
        for(long seed=1;seed<=8;seed++) {
            var p=piece(h,0,seed); place(h,p,p.getBoundingBox(),1); var pattern=mixture(h.getLevel(),p); patterns.add(pattern);
            for(var state:pattern.values()) { acid|=state.is(TGFluids.ACID.block.get()); solid|=state.is(Blocks.NETHERRACK); }
        }
        h.assertTrue(patterns.size()>1 && acid && solid,"Saved seed produces independent mixed rims, not one frozen pattern"); h.succeed();
    }
    private static void damage(GameTestHelper h) {
        var p=placed(h); var pos=p.templatePosition().offset(4,3,4); var state=h.getLevel().getBlockState(pos); var player=WeaponGameTests.player(h);
        player.setHealth(20); player.getAttribute(Attributes.ARMOR).setBaseValue(20); player.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8); player.setDeltaMovement(Vec3.ZERO);
        state.entityInside(h.getLevel(),pos,player,InsideBlockEffectApplier.NOOP,true); h.assertValueEqual(player.getHealth(),18f,"Generated acid uses original two poison damage despite normal armor");
        for(int i=0;i<20;i++) state.entityInside(h.getLevel(),pos,player,InsideBlockEffectApplier.NOOP,true);
        h.assertValueEqual(player.getHealth(),18f,"Contact respects hurt immunity"); h.assertValueEqual(player.getDeltaMovement(),Vec3.ZERO,"No acid knockback");
        var bucket=TGFluids.ACID.block.get().pickupBlock(player,h.getLevel(),pos,state); h.assertTrue(bucket.is(TGFluids.ACID.bucket.get()),"Generated source can be collected into registered acid bucket"); h.assertTrue(h.getLevel().getFluidState(pos).isEmpty(),"Pickup removes exactly that source");
        h.succeed();
    }
    private static void noInfinite(GameTestHelper h) {
        var p=placed(h); var pos=p.templatePosition().offset(4,3,4); TGFluids.ACID.block.get().pickupBlock(null,h.getLevel(),pos,h.getLevel().getBlockState(pos));
        h.runAfterDelay(25,()->{
            var fluid=h.getLevel().getFluidState(pos); h.assertTrue(!fluid.isEmpty() && fluid.getType().isSame(TGFluids.ACID.still.get()) && !fluid.isSource(),"Surrounded harvested pool cell refills only as flow");
            h.assertTrue(TGFluids.ACID.block.get().pickupBlock(null,h.getLevel(),pos,h.getLevel().getBlockState(pos)).isEmpty(),"No infinite-bucket farming");
            h.getLevel().getChunk(pos); h.assertTrue(!h.getLevel().getFluidState(pos).isSource(),"Repeated ready-chunk request does not regenerate the pool"); h.succeed();
        });
    }
    private static void flow(GameTestHelper h) {
        var p=placed(h); var pos=p.templatePosition().offset(4,3,4);
        h.getLevel().setBlock(pos.below(),Blocks.AIR.defaultBlockState(),3);
        h.runAfterDelay(8,()->{
            var below=h.getLevel().getFluidState(pos.below()); h.assertTrue(below.getType().isSame(TGFluids.ACID.still.get()) && !below.isSource(),"Natural pool uses real scheduled fluid flow through broken floor"); h.succeed();
        });
    }
    private static void titanium(GameTestHelper h) {
        var p=placed(h); var pos=new BlockPos(4,2,3); var player=WeaponGameTests.player(h);
        for(var part:ReactionStructure.parts(h.absolutePos(pos),Direction.SOUTH)) h.getLevel().setBlock(part.pos(),ReactionContent.block(part.kind()).defaultBlockState(),3);
        var chamber=h.getBlockEntity(pos,ReactionChamberBlockEntity.class); h.assertTrue(chamber.form(Direction.NORTH,player),"Real complete reaction chamber");
        while(chamber.data.get(3)<3) h.assertTrue(chamber.button(player,4),"Set original titanium fluid level");
        while(chamber.data.get(4)<5) h.assertTrue(chamber.button(player,0),"Set original titanium intensity");
        for(int x=3;x<=5;x++) {
            var source=p.templatePosition().offset(x,3,4); var bucket=TGFluids.ACID.block.get().pickupBlock(player,h.getLevel(),source,h.getLevel().getBlockState(source));
            h.assertTrue(bucket.is(TGFluids.ACID.bucket.get()),"Source exploration acid collected"); player.setItemInHand(InteractionHand.MAIN_HAND,bucket); h.useBlock(pos,player);
            h.assertTrue(player.getMainHandItem().is(Items.BUCKET),"Chamber consumes bucket contents, returns container");
        }
        h.assertValueEqual(chamber.tank().stack().getAmount(),3000,"Three real source buckets transferred");
        chamber.setItem(0,ReactionChamberGameTests.item("ore_titanium",1)); chamber.setItem(1,ReactionChamberGameTests.item("rcheatray",1));
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(chamber.energy().insert(25000,tx),25000,"Original paid reaction energy"); tx.commit(); }
        h.onEachTick(()->{
            if(chamber.working()) {
                int required=chamber.operation().requiredIntensity();
                while(chamber.data.get(4)!=required) h.assertTrue(chamber.button(player,chamber.data.get(4)<required?0:1),"Follow original reaction intensity");
            }
            if(!chamber.getItem(2).isEmpty()) {
                h.assertTrue(chamber.getItem(2).is(TGContent.MATERIALS.get("oretitanium").get()) && chamber.getItem(2).getCount()==2,"Pool acid produces two refined titanium ores");
                h.assertTrue(chamber.getItem(3).is(Items.IRON_ORE) && chamber.getItem(3).getCount()==1,"Original iron byproduct");
                h.assertValueEqual(chamber.tank().stack().getAmount(),2900,"Only original 100 mB consumed"); h.assertValueEqual(chamber.energy().getAmountAsInt(),0,"25000 FE consumed once"); h.succeed();
            }
        });
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel().getServer().getLevel(Level.NETHER); var s=structure(l); ChunkPos chosen=null;
        for(int n=1;n<=256;n++) { var candidate=new ChunkPos(sign*16,sign*16*n); if(s.findGenerationPoint(context(l,candidate)).isPresent()) { chosen=candidate; break; } }
        h.assertTrue(chosen!=null,"Source ticket and real cave condition find an acid pool"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s);
        h.assertTrue(start!=null && start.isValid(),"Native chunk has saved acid structure start"); var p=(NetherAcidPiece)start.getPieces().getFirst(); var box=p.getBoundingBox();
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z);
        for(var cell:cells(p,TGFluids.ACID.block.get())) h.assertTrue(l.getFluidState(cell.pos()).getType().isSame(TGFluids.ACID.still.get()) && l.getFluidState(cell.pos()).isSource(),"Natural fixed source actually placed at "+cell.pos()+": "+l.getBlockState(cell.pos()));
        var rim=mixture(l,p); h.assertValueEqual(rim.size(),12,"Natural mixed rim"); for(var state:rim.values()) h.assertTrue(state.is(Blocks.NETHERRACK) || state.is(TGFluids.ACID.block.get()) && state.getFluidState().isSource(),"Worldgen handles every data marker");
        for(var id:List.of(NetherAltarPiece.TEMPLATE,NetherLootPiece.TEMPLATE)) { var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"No second Techguns location at this site"); }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherAcidPiece.TEMPLATE));
        // Odd source sizes shift the origin into the previous chunk for two rotations. Query from the pool centre.
        var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(4,3,4),0,false); h.assertTrue(found!=null,"Native locate resolves generated pool");
        h.assertValueEqual(new ChunkPos(found.getFirst().getX()>>4,found.getFirst().getZ()>>4),chosen,"Locate points to the actual saved start");
        var ctx=StructurePieceSerializationContext.fromLevel(l); var saved=p.createTag(ctx); var restored=(NetherAcidPiece)LocationContent.ACID_PIECE.get().load(ctx,saved);
        h.assertValueEqual(restored.mixtureSeed(),p.mixtureSeed(),"Natural random mixture seed persisted"); l.getChunk(chosen.x(),chosen.z()); h.assertValueEqual(mixture(l,p),rim,"Ready chunk does not reroll its composition");
        com.mojang.logging.LogUtils.getLogger().info("Techguns native NetherAcidHole: chunk={}, origin={}, rotation={}, mixtureSeed={}, biome={}",chosen,p.templatePosition(),p.getRotation(),p.mixtureSeed(),l.getBiome(p.templatePosition().above(3)).unwrapKey().orElseThrow().identifier()); h.succeed();
    }
    private NetherAcidGameTests() {}
}
