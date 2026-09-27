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
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.castle.CastleSegments.SegmentType;
import techguns.modern.TGContent;
import techguns.modern.npc.*;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.MilitaryCrateDrops;
import techguns.modern.world.structure.*;

final class CastleGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int f=0;f<6;f++) for(var t:SegmentType.values()) { int family=f;
            r.register("castle_template_"+CastlePlan.FAMILIES.get(f)+"_"+t.name().toLowerCase(Locale.ROOT),()->h->template(h,family,t)); }
        for(int i=0;i<6;i++) { int seed=i; r.register("castle_complete_layout_"+i,()->h->complete(h,seed)); }
        for(int sign:List.of(-1,1)) r.register("castle_saved_chunks_"+name(sign),()->h->clipping(h,sign));
        r.register("castle_consumed_loot_and_guard_save",()->CastleGameTests::spent);
        r.register("castle_real_nested_supplies",()->CastleGameTests::loot);
        r.register("castle_two_active_two_deaths",()->CastleGameTests::encounters);
        r.register("castle_registry_selection_config_dimensions",()->CastleGameTests::registry);
        if(Boolean.getBoolean("techguns.worldgenTest")) for(int sign:List.of(-1,1))
            r.register("structure_castle_"+name(sign)+"_natural_chunks",()->h->natural(h,sign));
    }
    private static String name(int sign) { return sign<0?"negative":"positive"; }
    private static BlockPos origin(int slot,int sign) { return new BlockPos(sign*(5200013+slot*80),120,sign*5200013); }
    private static CastlePiece castle(int slot,int sign,int seed) { return new CastlePiece(CastlePlan.create(origin(slot,sign),32+seed*3,24+seed*3,47-seed*3,seed)); }
    private static List<ChunkPos> chunks(CastlePiece p) {
        var b=p.getBoundingBox(); var result=new ArrayList<ChunkPos>();
        for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) result.add(new ChunkPos(x,z));
        return result;
    }
    private static void load(ServerLevel l,CastlePiece p) { for(var c:chunks(p)) l.getChunk(c.x(),c.z()); }
    private static void place(ServerLevel l,CastlePiece p,ChunkPos c) {
        p.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(c.pack()),
            new BoundingBox(c.getMinBlockX(),l.getMinY(),c.getMinBlockZ(),c.getMaxBlockX(),l.getMaxY()-1,c.getMaxBlockZ()),c,p.plan().origin());
    }
    private static void place(ServerLevel l,CastlePiece p) { for(var c:chunks(p)) place(l,p,c); }
    private static CastlePiece restore(ServerLevel l,CastlePiece p) {
        var context=StructurePieceSerializationContext.fromLevel(l);
        return (CastlePiece)LocationContent.CASTLE_PIECE.get().load(context,p.createTag(context));
    }
    private static void verify(GameTestHelper h,CastlePiece p) {
        var l=h.getLevel(); var plan=p.plan(); var view=plan.view(l);
        for(var e:plan.cells().entrySet()) {
            var actual=l.getBlockState(e.getKey()); var expected=CastlePlan.connected(e.getValue(),view,e.getKey());
            h.assertValueEqual(actual,expected,"Entire saved castle at "+e.getKey());
            if(actual.getBlock() instanceof StairBlock||actual.getBlock() instanceof IronBarsBlock)
                h.assertValueEqual(actual,Block.updateFromNeighbourShapes(actual,l,e.getKey()),"Native neighbour shape at "+e.getKey());
            if(actual.getBlock() instanceof DoorBlock) {
                var half=actual.getValue(DoorBlock.HALF); var other=l.getBlockState(half==DoubleBlockHalf.LOWER?e.getKey().above():e.getKey().below());
                h.assertTrue(other.is(actual.getBlock()),"Both door halves exist");
                h.assertValueEqual(other.getValue(DoorBlock.FACING),actual.getValue(DoorBlock.FACING),"Door direction survives rotation");
                h.assertValueEqual(other.getValue(DoorBlock.HINGE),actual.getValue(DoorBlock.HINGE),"Door hinge survives rotation");
            }
        }
        for(var e:plan.loot().entrySet()) {
            var chest=(ChestBlockEntity)l.getBlockEntity(e.getKey()); h.assertTrue(chest!=null,"Real provision chest");
            h.assertValueEqual(chest.getLootTable(),ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/castle")),"Source castle loot");
            h.assertValueEqual(chest.getLootTableSeed(),e.getValue(),"Preserved chest seed");
        }
        for(var pos:plan.posts()) {
            h.assertTrue(l.getBlockState(pos).is(NpcSpawnerContent.BLOCK.get()),"Source default monster spawner is HOLE");
            var post=(NpcSpawnerBlockEntity)l.getBlockEntity(pos); h.assertTrue(post!=null,"Real guard post");
            h.assertValueEqual(post.remaining(),2,"Two deaths"); h.assertValueEqual(post.maximum(),2,"Two simultaneous guards");
            h.assertValueEqual(post.interval(),200,"Source delay"); h.assertValueEqual(post.range(),2.0,"Source radius");
            h.assertValueEqual(post.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("zombiesoldier"),1),new NpcSpawnerBlockEntity.Entry(TGContent.id("skeletonsoldier"),1)),"Equal source guard weights");
        }
    }
    private static void template(GameTestHelper h,int family,SegmentType type) {
        for(int rotation=0;rotation<4;rotation++) {
            var plan=CastlePlan.template(origin((family*16+type.ordinal())*4+rotation,1),family,type,rotation,31);
            var p=new CastlePiece(plan); load(h.getLevel(),p); place(h.getLevel(),p); verify(h,p);
            h.assertValueEqual(plan.cells().size(),type==SegmentType.RAMP?250:125,"All source cells, including clearing air");
            h.assertValueEqual(restore(h.getLevel(),p).plan(),plan,"Every template survives native piece serialization");
            for(var s:plan.cells().values()) if(s.is(Blocks.SKELETON_SKULL)) h.assertValueEqual(s.getValue(SkullBlock.ROTATION),rotation,"Legacy raw skull yaw is intentionally not quarter turns");
        }
        h.succeed();
    }
    private static void complete(GameTestHelper h,int seed) {
        var p=castle(400+seed,1,seed); load(h.getLevel(),p); place(h.getLevel(),p); verify(h,p);
        h.assertValueEqual(p.plan(),castle(400+seed,1,seed).plan(),"Layout and decoration consume one reproducible world RNG");
        h.assertValueEqual(p.plan().graph().nodes().size(),Collections.max(p.plan().graph().attempts()),"Best of five whole mazes");
        h.assertTrue(!p.plan().posts().isEmpty(),"A real dungeon encounter");
        h.assertTrue(p.plan().segments().stream().anyMatch(s->s.type()==SegmentType.RAMP),"Connected levels include full ramps");
        h.succeed();
    }
    private static void clipping(GameTestHelper h,int sign) {
        var p=castle(410,sign,5); var expected=p.plan(); load(h.getLevel(),p); var cs=chunks(p); Collections.reverse(cs);
        var first=cs.getFirst(); var outside=p.plan().cells().keySet().stream().filter(q->!ChunkPos.containing(q).equals(first)).findFirst().orElseThrow();
        h.getLevel().setBlock(outside,Blocks.DIAMOND_BLOCK.defaultBlockState(),2); place(h.getLevel(),p,first);
        h.assertTrue(h.getLevel().getBlockState(outside).is(Blocks.DIAMOND_BLOCK),"First chunk cannot write into its neighbour");
        p=restore(h.getLevel(),p);
        for(var c:cs) { place(h.getLevel(),p,c); p=restore(h.getLevel(),p); }
        verify(h,p); h.assertValueEqual(p.plan(),expected,"Reverse chunk order and repeated serialization keep the entire graph and seeds"); h.succeed();
    }
    private static void spent(GameTestHelper h) {
        var p=castle(412,1,5); var l=h.getLevel(); load(l,p); place(l,p);
        var pos=p.plan().loot().keySet().iterator().next(); var chest=(ChestBlockEntity)l.getBlockEntity(pos);
        h.assertTrue(!chest.isEmpty(),"Native castle chest resolves rewards"); chest.clearContent();
        var post=p.plan().posts().iterator().next(); l.setBlock(post,Blocks.AIR.defaultBlockState(),2);
        var used=p.plan().cells().keySet().stream().filter(q->l.getBlockState(q).is(Blocks.STONE_BRICKS)).findFirst().orElseThrow(); l.setBlock(used,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
        p=restore(l,p); place(l,p);
        h.assertTrue(chest.isEmpty()&&chest.getLootTable()==null,"Empty inventory never refills on chunk reentry");
        h.assertTrue(l.getBlockState(post).isAir()&&l.getBlockState(used).is(Blocks.DIAMOND_BLOCK),"Spent guards and player edits remain after save"); h.succeed();
    }
    private static void loot(GameTestHelper h) {
        var l=h.getLevel(); var player=WeaponGameTests.player(h);
        try {
            var table=l.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/castle")));
            var json=LootTable.DIRECT_CODEC.encodeStart(l.registryAccess().createSerializationContext(JsonOps.INSTANCE),table).getOrThrow().getAsJsonObject();
            h.assertValueEqual(json.getAsJsonArray("pools").size(),3,"All source nested pools load"); var counts=new HashSet<Integer>();
            for(int seed=0;seed<512;seed++) {
                var items=table.getRandomItems(MilitaryCrateDrops.params(l,h.absolutePos(BlockPos.ZERO),player,0),RandomSource.create(seed*0x9e3779b97f4a7c15L));
                counts.add(items.size()); h.assertTrue(items.stream().noneMatch(ItemStack::isEmpty),"All nested rewards resolve");
                long guns=items.stream().filter(s->s.getItem() instanceof techguns.modern.GunItem&& !Set.of("rocketlauncher","grenadelauncher").contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath())).count();
                h.assertTrue(guns>=1&&guns<=2,"Gun pool supplies 1..2 weapons; explosives may additionally supply launchers");
                h.assertTrue(items.size()>=7&&items.size()<=14,"All source roll bounds");
            }
            h.assertTrue(counts.contains(7)&&counts.contains(14),"Both supply count endpoints occur");
        } finally { player.discard(); }
        h.succeed();
    }
    private static void encounters(GameTestHelper h) {
        var p=castle(414,1,5); var l=h.getLevel(); load(l,p); place(l,p);
        var pos=p.plan().posts().iterator().next(); var post=(NpcSpawnerBlockEntity)l.getBlockEntity(pos); var spawned=new ArrayList<Mob>();
        Consumer<EntityJoinLevelEvent> observe=e->{if(e.getLevel()==l&&e.getEntity() instanceof Mob m&&m instanceof SpawnerLinked linked&&linked.spawnerLink()!=null&&linked.spawnerLink().origin().pos().equals(pos)) {m.removeFreeWill();spawned.add(m);}};
        NeoForge.EVENT_BUS.addListener(observe);
        try {
            for(int tick=0;tick<400;tick++) NpcSpawnerBlockEntity.serverTick(l,pos,post.getBlockState(),post);
            h.assertValueEqual(spawned.size(),2,"Both active slots fill before any death");
            for(int tick=0;tick<400;tick++) NpcSpawnerBlockEntity.serverTick(l,pos,post.getBlockState(),post);
            h.assertValueEqual(spawned.size(),2,"Active cap blocks extra guards");
            for(var mob:spawned) { h.assertTrue(mob instanceof ZombieSoldier||mob instanceof SkeletonSoldier,"Castle guard types"); mob.hurtServer(l,l.damageSources().genericKill(),10000); }
            NpcSpawnerBlockEntity.serverTick(l,pos,post.getBlockState(),post);
            h.assertTrue(l.getBlockState(pos).isAir(),"Two actual deaths exhaust and remove the post");
            p=restore(l,p); place(l,p); h.assertTrue(l.getBlockState(pos).isAir(),"The exhausted post remains gone after layout save");
        } finally { NeoForge.EVENT_BUS.unregister(observe); spawned.forEach(Entity::discard); }
        h.succeed();
    }
    private static CastleStructure structure(ServerLevel l) { return (CastleStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(TGContent.id("castle")); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c) { var g=l.getChunkSource().getGenerator();return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel();var s=structure(l);h.assertTrue(s!=null,"Native castle registry");
        var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE);h.assertTrue(Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow() instanceof CastleStructure,"Castle codec roundtrip");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,TGContent.id("castle")));var g=l.getChunkSource().getGenerator();
        for(var dimension:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dimension,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(64,64),0,l,b->true).isValid(),"Overworld only");
        boolean enabled=LocationConfig.ENABLED.get();try {LocationConfig.ENABLED.set(false);h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(64,64))).isEmpty(),"SpawnStructures disables new castles");}finally{LocationConfig.ENABLED.set(enabled);}
        h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel();var s=structure(l);ChunkPos chosen=null;
        for(int n=513;n<1200;n++) {
            var c=new ChunkPos(sign*64*9,sign*64*n);var stub=s.findGenerationPoint(context(l,c));
            if(stub.isPresent()&&!((CastlePiece)stub.get().getPiecesBuilder().build().pieces().getFirst()).plan().loot().isEmpty()) {chosen=c;break;}
        }
        h.assertTrue(chosen!=null,"Find Castle in real normal terrain");
        var camp=(MilitaryCampStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(TGContent.id("military_camp"));
        h.assertTrue(camp.findGenerationPoint(context(l,chosen)).isEmpty(),"Camp cannot take a castle LAND ticket");
        var chunk=l.getChunk(chosen.x(),chosen.z());var start=chunk.getStartForStructure(s);h.assertTrue(start!=null&&start.isValid(),"Native castle start persists");
        h.assertTrue(chunk.getStartForStructure(camp)==null||!chunk.getStartForStructure(camp).isValid(),"Both native structures never occupy one site");
        var p=(CastlePiece)start.getPieces().getFirst();load(l,p);verify(h,p);h.assertValueEqual(restore(l,p).plan(),p.plan(),"Natural graph/plan/chest seeds survive save");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,TGContent.id("castle")));
        // The inherited rotation can shift the origin into the previous grid region.
        h.assertTrue(l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),chosen.getWorldPosition(),0,false)!=null,"Native locate finds the castle in its candidate grid");
        var chest=(ChestBlockEntity)l.getBlockEntity(p.plan().loot().keySet().iterator().next());h.assertTrue(!chest.isEmpty(),"Natural provision chest resolves actual rewards");
        var air=p.plan().cells().entrySet().stream().filter(e->e.getValue().isAir()).map(Map.Entry::getKey).findFirst().orElseThrow();
        h.assertTrue(p.protectsDecoration(air)&&!p.protectsDecoration(p.plan().origin().below()),"Protection follows actual cells only");
        h.assertTrue(net.minecraft.world.level.levelgen.feature.TreeFeature.validTreePos(l,air),"Ordinary post-generation growth remains available");
        var stone=p.plan().cells().entrySet().stream().filter(e->e.getValue().is(Blocks.STONE)).map(Map.Entry::getKey).findFirst().orElseThrow();
        h.assertTrue(new PostGenerationOre().placeAt(l,stone),"Ore feature calls remain available after world generation");
        h.assertTrue(p.plan().cells().entrySet().stream().anyMatch(e->e.getValue().is(Blocks.STONE)&&l.getBlockState(e.getKey()).is(Blocks.DIAMOND_ORE)),"The protection does not reserve registered castle blocks after generation");
        boolean ore=LocationConfig.ORE_CLUSTERS.get();try {LocationConfig.ORE_CLUSTERS.set(!ore);h.assertTrue(s.findGenerationPoint(context(l,chosen)).isPresent(),"Ore switch does not affect castles");}finally{LocationConfig.ORE_CLUSTERS.set(ore);}
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural Castle: chunk={}, size={}x{}x{}, segments={}, nodes={}, posts={}, chests={}, cells={}, seed={}",chosen,p.plan().width(),p.plan().height(),p.plan().depth(),p.plan().segments().size(),p.plan().graph().nodes().size(),p.plan().posts().size(),p.plan().loot().size(),p.plan().cells().size(),p.plan().seed());
        h.succeed();
    }
    private static final class PostGenerationOre extends net.minecraft.world.level.levelgen.feature.OreFeature {
        PostGenerationOre() { super(net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration.CODEC); }
        boolean placeAt(ServerLevel level,BlockPos pos) {
            var config=new net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration(new net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest(Blocks.STONE),Blocks.DIAMOND_ORE.defaultBlockState(),32);
            return doPlace(level,RandomSource.create(31),config,pos.getX(),pos.getX(),pos.getZ(),pos.getZ(),pos.getY(),pos.getY(),pos.getX()-6,pos.getY()-6,pos.getZ()-6,12,12);
        }
    }
    private CastleGameTests() {}
}
