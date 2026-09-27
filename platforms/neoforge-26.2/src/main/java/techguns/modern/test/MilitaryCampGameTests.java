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
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.modern.TGContent;
import techguns.modern.npc.*;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.*;
import techguns.modern.world.structure.*;
import techguns.modern.world.structure.camp.*;
import techguns.modern.world.structure.camp.CampWorld.*;
import techguns.modern.world.structure.camp.CampPart.BiomeColorType;

final class MilitaryCampGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        var groups=List.of("inside","border","corner"); int slot=0;
        for(int g=0;g<3;g++) for(int i=0;i<new int[]{13,7,2}[g];i++) { String group=groups.get(g); int index=i,site=slot++; r.register("camp_component_"+group+"_"+i,()->h->component(h,group,index,site)); }
        for(int i=0;i<7;i++) { int seed=i; r.register("camp_complete_layout_"+i,()->h->complete(h,seed)); }
        for(int sign:List.of(-1,1)) r.register("camp_saved_reverse_chunks_"+signName(sign),()->h->clipping(h,sign));
        r.register("camp_consumed_loot_and_posts_never_reset",()->MilitaryCampGameTests::spent);
        r.register("camp_nested_bunker_and_barracks_loot",()->MilitaryCampGameTests::loot);
        r.register("camp_terrain_median_filter_and_clearing",()->MilitaryCampGameTests::terrain);
        r.register("camp_source_finite_soldier_and_helicopter",()->MilitaryCampGameTests::encounters);
        r.register("camp_registry_config_and_other_dimensions",()->MilitaryCampGameTests::registry);
        r.register("camp_render_connections_and_double_chest",()->MilitaryCampGameTests::connections);
        r.register("camp_connections_predict_next_chunk_clearing",()->MilitaryCampGameTests::clearingEdge);
        if(Boolean.getBoolean("techguns.worldgenTest")) for(int sign:List.of(-1,1))
            r.register("structure_military_camp_"+signName(sign)+"_natural_chunks",()->h->natural(h,sign));
    }
    private static String signName(int sign) { return sign<0?"negative":"positive"; }
    private static BlockPos origin(int slot,int sign) { return new BlockPos(sign*(4200077+slot*256),121,sign*4200083); }
    private static CampWorld.Terrain flat() { return new CampWorld.Terrain() {
        public BlockState state(BlockPos p) { return (p.getY()<=120?Blocks.STONE:Blocks.AIR).defaultBlockState(); }
        public int top(int x,int z) { return 121; }
    }; }
    private static CampWorld world(int slot,int sign,long seed,BiomeColorType color) { return new CampWorld(origin(slot,sign),32,32,0,320,color,seed,flat()); }
    private static MilitaryCampPiece camp(int slot,int sign,int seed) {
        var plan=CampWorld.create(origin(slot,sign),seed==6?79:32+seed*7,seed==6?79:79-seed*8,0,320,BiomeColorType.values()[seed%3],seed,seed*79+13,flat());
        return new MilitaryCampPiece(plan,seed,seed*79+13);
    }
    private static void load(ServerLevel l,MilitaryCampPiece piece) {
        var b=piece.getBoundingBox(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) l.getChunk(x,z);
    }
    private static void ground(ServerLevel l,MilitaryCampPiece p) {
        load(l,p); var b=p.getBoundingBox(); int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_ON_PLACE;
        for(int x=b.minX();x<=b.maxX();x++) for(int z=b.minZ();z<=b.maxZ();z++) for(int y=119;y<=120;y++) l.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),flags);
    }
    private static List<ChunkPos> chunks(MilitaryCampPiece piece) {
        var out=new ArrayList<ChunkPos>(); var b=piece.getBoundingBox(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) out.add(new ChunkPos(x,z)); return out;
    }
    private static void place(ServerLevel l,MilitaryCampPiece piece,ChunkPos c) {
        var clip=new BoundingBox(c.getMinBlockX(),l.getMinY(),c.getMinBlockZ(),c.getMaxBlockX(),l.getMaxY()-1,c.getMaxBlockZ());
        piece.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(c.pack()),clip,c,piece.plan().origin());
    }
    private static void place(ServerLevel l,MilitaryCampPiece piece) { for(var c:chunks(piece)) place(l,piece,c); }
    private static MilitaryCampPiece restore(ServerLevel l,MilitaryCampPiece p) { var ctx=StructurePieceSerializationContext.fromLevel(l); return (MilitaryCampPiece)LocationContent.MILITARY_CAMP_PIECE.get().load(ctx,p.createTag(ctx)); }
    private static void verify(GameTestHelper h,MilitaryCampPiece p) {
        var l=h.getLevel(); for(var e:p.plan().cells().entrySet()) {
            var actual=l.getBlockState(e.getKey()); var authored=e.getValue().state();
            // Source getActualState connections depend on the real surface. They are checked separately below.
            for(var prop:authored.getProperties()) if(prop instanceof BooleanProperty b&&Set.of("north","east","south","west","up","down","corner_ne","corner_es","corner_sw","corner_wn").contains(b.getName())&&actual.hasProperty(b)) authored=authored.setValue(b,actual.getValue(b));
            if(e.getValue().copyY()==Integer.MIN_VALUE) h.assertValueEqual(actual,authored,"Complete authored cell at "+e.getKey());
            else h.assertTrue(CampTerrain.ground(l.getBlockState(e.getKey())),"Raised native ground material at "+e.getKey());
            var s=l.getBlockState(e.getKey());
            if(s.getBlock() instanceof CamouflageNetBlock b) h.assertValueEqual(s,b.connected(s,l,e.getKey()),"Saved camouflage render connections");
            if(s.getBlock() instanceof SandbagBlock b) h.assertValueEqual(s,b.connected(s,l,e.getKey()),"Saved sandbag at "+e.getKey()+" north="+l.getBlockState(e.getKey().north())+" planned north="+p.plan().cells().get(e.getKey().north()));
            if(s.getBlock() instanceof IndustrialLampBlock b) for(var d:Direction.values()) h.assertValueEqual(s.getValue(IndustrialLampBlock.CONNECTIONS.get(d)),b.lantern()&&IndustrialLampBlock.canAttach(l,e.getKey(),d),"Saved lantern attachment "+d);
            if(s.getBlock() instanceof FenceBlock||s.getBlock() instanceof IronBarsBlock) h.assertValueEqual(s,Block.updateFromNeighbourShapes(s,l,e.getKey()),"Native fence/pane at "+e.getKey()+" south="+l.getBlockState(e.getKey().south())+" planned south="+p.plan().cells().get(e.getKey().south()));
        }
        for(var e:p.plan().posts().entrySet()) {
            var be=(NpcSpawnerBlockEntity)l.getBlockEntity(e.getKey()); h.assertTrue(be!=null,"Native soldier post exists");
            h.assertValueEqual(be.remaining(),e.getValue().quota(),"Source finite quota"); h.assertValueEqual(be.maximum(),1,"One active guard per post");
            h.assertValueEqual(be.interval(),200,"200-tick interval"); h.assertValueEqual(be.range(),0.0,"Exact post position");
            h.assertValueEqual(be.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id(e.getValue().mob()),1)),"Source NPC and weight");
        }
        for(var e:p.plan().loot().entrySet()) {
            var be=(ChestBlockEntity)l.getBlockEntity(e.getKey()); h.assertTrue(be!=null,"Native chest exists");
            h.assertValueEqual(be.getLootTable(),ResourceKey.create(Registries.LOOT_TABLE,e.getValue().table()),"Nested source loot table");
            h.assertValueEqual(be.getLootTableSeed(),e.getValue().seed(),"Loot seed was planned once");
        }
    }
    private static long count(Plan plan,java.util.function.Predicate<BlockState> test) { return plan.cells().values().stream().filter(c->test.test(c.state())).count(); }
    private static void component(GameTestHelper h,String group,int index,int slot) {
        var part=CampLayout.parts(group).get(index);
        for(int direction=0;direction<4;direction++) {
            var color=BiomeColorType.values()[direction%3]; var w=world(slot*4+direction,1,81+index,color); var at=origin(slot*4+direction,1).offset(2,-1,2);
            int sx=part.maxX<0?13:part.maxX,sz=part.maxZ<0?13:part.maxZ,sy=part.maxY<0?part.minY:part.maxY;
            part.setBlocks(w,at.getX(),at.getY(),at.getZ(),sx,sy,sz,direction,color,new Random(39));
            var plan=w.freeze(); h.assertTrue(!plan.cells().isEmpty(),"Source component produces geometry: "+part.key());
            var p=new MilitaryCampPiece(plan,39,81+index); ground(h.getLevel(),p); place(h.getLevel(),p); verify(h,p);
            h.assertValueEqual(restore(h.getLevel(),p).plan(),plan,"Component properties and chest seeds survive native save");
            if(part instanceof Tent||part instanceof WatchTowerSmall) h.assertTrue(plan.cells().values().stream().filter(c->c.state().getBlock() instanceof CamouflageNetBlock).allMatch(c->c.state().is(CamouflageNetContent.BLOCKS.get("camonet_wood").get())||c.state().is(CamouflageNetContent.BLOCKS.get("camonet_top_wood").get())),"Unused source camo variable leaves tents/towers woodland");
            if(part instanceof Helipad) { h.assertValueEqual(count(plan,s->s.is(NeonContent.fromMetadata(4))),7L,"Seven lamps form the H"); h.assertTrue(NeonContent.fromMetadata(4).defaultBlockState().getLightEmission()==15,"Helipad is illuminated"); }
            if(part instanceof Tanks) h.assertTrue(count(plan,s->s.is(Blocks.LAVA))>0,"Tanks contain real lava");
            if(part instanceof Bunker) h.assertValueEqual(count(plan,s->s.is(FortificationContent.DOOR.get())),2L,"Bunker has both native door halves");
            if(part instanceof Barracks) { h.assertTrue(!plan.loot().isEmpty(),"Barracks provision chests"); h.assertTrue(count(plan,s->s.getBlock() instanceof BedBlock)>0,"Barracks have paired beds"); }
            if(part instanceof WatchTowerSmall) { h.assertValueEqual(count(plan,s->s.is(Blocks.OAK_FENCE_GATE)),1L,"Tower hatch"); h.assertValueEqual(count(plan,s->s.is(Blocks.LADDER)),(long)sy-4,"Full ladder reaches tower platform"); }
        }
        h.succeed();
    }
    private static void complete(GameTestHelper h,int seed) {
        var p=camp(100+seed,1,seed); ground(h.getLevel(),p); place(h.getLevel(),p); verify(h,p);
        h.assertTrue(p.plan().components().size()>1,"Camp subdivides into real components");
        h.assertValueEqual(p.plan().posts().values().stream().filter(v->v.mob().equals("attackhelicopter")).count(),1L,"Central helicopter post");
        h.assertTrue(p.plan().posts().values().stream().anyMatch(v->v.mob().equals("armysoldier")),"Road segments have army encounters");
        h.assertTrue(count(p.plan(),s->s.is(Blocks.IRON_BARS))>40,"Perimeter fence");
        h.assertTrue(count(p.plan(),s->s.is(BuildingContent.BLOCKS.get("concrete_grey").get()))>0,"Wide roads have concrete middle lanes");
        h.assertValueEqual(p.plan(),camp(100+seed,1,seed).plan(),"Rebuilding with both seeds is deterministic");
        h.succeed();
    }
    private static void clipping(GameTestHelper h,int sign) {
        var p=camp(110,sign,3); var expected=p.plan(); ground(h.getLevel(),p); var cs=chunks(p); Collections.reverse(cs);
        for(var c:cs) { place(h.getLevel(),p,c); p=restore(h.getLevel(),p); }
        verify(h,p); h.assertValueEqual(p.plan(),expected,"Every saved chunk preserves the resolved plan");
        h.assertValueEqual(p.layoutSeed(),3L,"Layout seed survives"); h.assertValueEqual(p.decorationSeed(),250L,"Separate world decoration seed survives"); h.succeed();
    }
    private static void spent(GameTestHelper h) {
        var w=world(112,1,381,BiomeColorType.WOODLAND); var o=origin(112,1); var part=CampLayout.parts("inside").get(7);
        part.setBlocks(w,o.getX()+2,120,o.getZ()+2,15,7,15,0,BiomeColorType.WOODLAND,new Random(18));
        var guard=o.offset(25,0,25); w.post(guard,"armysoldier",3,0); var p=new MilitaryCampPiece(w.freeze(),18,381); ground(h.getLevel(),p); place(h.getLevel(),p);
        var chestPos=p.plan().loot().keySet().iterator().next(); var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(chestPos); boolean found=false;
        for(int i=0;i<chest.getContainerSize();i++) found|=!chest.getItem(i).isEmpty(); h.assertTrue(found,"Opening native generated chest resolves rewards"); chest.clearContent();
        h.getLevel().setBlock(guard,Blocks.AIR.defaultBlockState(),2); p=restore(h.getLevel(),p); place(h.getLevel(),p);
        h.assertTrue(h.getLevel().getBlockState(guard).isAir(),"Spent encounter stays removed across chunk save/re-entry");
        h.assertTrue(chest.isEmpty()&&chest.getLootTable()==null,"Consumed loot cannot be rerolled"); h.succeed();
    }
    private static void loot(GameTestHelper h) {
        var player=WeaponGameTests.player(h);
        try {
            for(var name:List.of("bunker","barracks")) {
                var t=h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/militarybase_"+name)));
                var json=LootTable.DIRECT_CODEC.encodeStart(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),t).getOrThrow().getAsJsonObject(); var pools=json.getAsJsonArray("pools");
                h.assertValueEqual(pools.size(),name.equals("bunker")?3:2,"All nested pools loaded"); var counts=new HashSet<Integer>();
                for(int seed=0;seed<512;seed++) {
                    var result=t.getRandomItems(MilitaryCrateDrops.params(h.getLevel(),h.absolutePos(BlockPos.ZERO),player,0),RandomSource.create(seed*0x9e3779b97f4a7c15L));
                    counts.add(result.size()); h.assertTrue(result.stream().noneMatch(ItemStack::isEmpty),"Nested rewards resolve actual items");
                    if(name.equals("bunker")) h.assertTrue(result.stream().anyMatch(s->s.getItem() instanceof techguns.modern.GunItem),"Every bunker roll supplies a gun");
                    else h.assertTrue(result.stream().anyMatch(s->s.getItem() instanceof techguns.modern.armor.TGArmorItem),"Every barracks roll supplies armor");
                }
                h.assertTrue(counts.contains(name.equals("bunker")?5:3)&&counts.contains(name.equals("bunker")?11:10),"Both source roll count endpoints occur");
            }
        } finally { player.discard(); }
        h.succeed();
    }
    private static void terrain(GameTestHelper h) {
        var o=origin(114,1); var terrain=new CampWorld.Terrain() {
            public int top(int x,int z) { return 121+(x-o.getX()>=16?4:0); }
            public BlockState state(BlockPos p) { return (p.getY()<top(p.getX(),p.getZ())?Blocks.STONE:Blocks.AIR).defaultBlockState(); }
        };
        var w=new CampWorld(o,32,32,0,320,BiomeColorType.DESERT,1,terrain);
        CampTerrain.flattenArea(w,o.getX(),o.getZ(),32,32,0);
        h.assertValueEqual(w.solidHeight(o.getX()+20,o.getZ()+20),121,"Source lower median determines the plateau");
        var p=camp(114,1,0); ground(h.getLevel(),p); var tree=o.offset(3,70,3);
        h.assertTrue(!p.plan().cells().containsKey(tree)&&!p.plan().cells().containsKey(tree.above()),"Clearing fixture is above all authored buildings");
        h.getLevel().setBlock(tree,Blocks.OAK_LOG.defaultBlockState(),2); h.getLevel().setBlock(tree.above(),Blocks.OAK_LEAVES.defaultBlockState(),2);
        var outside=new BlockPos(p.getBoundingBox().maxX()+1,130,p.getBoundingBox().maxZ()); h.getLevel().setBlock(outside,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
        place(h.getLevel(),p); h.assertTrue(h.getLevel().getBlockState(tree).isAir()&&h.getLevel().getBlockState(tree.above()).isAir(),"Native late vegetation is cleared");
        h.assertTrue(h.getLevel().getBlockState(outside).is(Blocks.DIAMOND_BLOCK),"No writes outside the camp margin"); h.succeed();
    }
    private static void encounters(GameTestHelper h) {
        var p=camp(116,1,0); ground(h.getLevel(),p); place(h.getLevel(),p); var l=h.getLevel();
        for(var name:List.of("armysoldier","attackhelicopter")) {
            var entry=p.plan().posts().entrySet().stream().filter(e->e.getValue().mob().equals(name)).findFirst().orElseThrow(); var pos=entry.getKey();
            var be=(NpcSpawnerBlockEntity)l.getBlockEntity(pos); var spawned=new ArrayList<Mob>();
            Consumer<EntityJoinLevelEvent> observe=e->{if(e.getLevel()==l && e.getEntity() instanceof Mob m && m instanceof SpawnerLinked linked && linked.spawnerLink()!=null && linked.spawnerLink().origin().pos().equals(pos)) { m.removeFreeWill(); spawned.add(m); }};
            NeoForge.EVENT_BUS.addListener(observe);
            try {
                for(int kill=0;kill<entry.getValue().quota();kill++) {
                    for(int tick=0;tick<200;tick++) NpcSpawnerBlockEntity.serverTick(l,pos,be.getBlockState(),be);
                    h.assertValueEqual(spawned.size(),kill+1,"Finite source guard materializes"); var mob=spawned.getLast();
                    h.assertTrue(name.equals("armysoldier")?mob instanceof ArmySoldier:mob instanceof AttackHelicopter,"Correct camp NPC");
                    h.assertValueEqual(mob.getY(),(double)pos.getY()+1+entry.getValue().height(),"Source elevation offset");
                    mob.hurtServer(l,l.damageSources().genericKill(),10000);
                    if(mob instanceof AttackHelicopter) for(int tick=0;tick<100;tick++) mob.tick();
                    NpcSpawnerBlockEntity.serverTick(l,pos,be.getBlockState(),be);
                }
                h.assertTrue(l.getBlockState(pos).isAir(),"All real deaths exhaust and remove the camp post");
            } finally { NeoForge.EVENT_BUS.unregister(observe); spawned.forEach(Entity::discard); }
        }
        h.succeed();
    }
    private static MilitaryCampStructure structure(ServerLevel l) { return (MilitaryCampStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(MilitaryCampPiece.ID); }
    private static void connections(GameTestHelper h) {
        var w=world(118,1,131,BiomeColorType.WOODLAND); var a=new BlockPos((origin(118,1).getX()&~15)+15,121,origin(118,1).getZ()+8); var b=a.east();
        w.setBlockState(a,Blocks.CHEST.defaultBlockState()); w.setBlockState(b,Blocks.CHEST.defaultBlockState());
        w.loot(a,TGContent.id("chests/militarybase_bunker"),31); w.loot(b,TGContent.id("chests/militarybase_barracks"),57);
        var p=new MilitaryCampPiece(w.freeze(),7,131); ground(h.getLevel(),p); var cs=chunks(p); Collections.reverse(cs); for(var c:cs) { place(h.getLevel(),p,c); p=restore(h.getLevel(),p); }
        verify(h,p); var container=ChestBlock.getContainer((ChestBlock)Blocks.CHEST,h.getLevel().getBlockState(a),h.getLevel(),a,true);
        h.assertTrue(container!=null&&container.getContainerSize()==54,"Two saved halves combine across the chunk seam");
        boolean found=false; for(int i=0;i<54;i++) found|=!container.getItem(i).isEmpty(); h.assertTrue(found,"Both independent nested tables are usable in the double inventory"); h.succeed();
    }
    private static void clearingEdge(GameTestHelper h) {
        var w=world(119,1,41,BiomeColorType.WOODLAND); var a=new BlockPos((origin(119,1).getX()&~15)+15,121,origin(119,1).getZ()+8); var b=a.east();
        w.setBlockState(a,Blocks.IRON_BARS.defaultBlockState()); w.clearColumn(b.getX(),b.getZ());
        var p=new MilitaryCampPiece(w.freeze(),41,41); ground(h.getLevel(),p); h.getLevel().setBlock(b,Blocks.OAK_LOG.defaultBlockState(),2);
        place(h.getLevel(),p,ChunkPos.containing(a));
        h.assertTrue(!h.getLevel().getBlockState(a).getValue(BlockStateProperties.EAST),"Fence must not connect to a log scheduled for clearing in the next chunk");
        place(h.getLevel(),p,ChunkPos.containing(b)); h.assertTrue(h.getLevel().getBlockState(b).isAir(),"Neighbouring chunk clears its own log"); verify(h,p); h.succeed();
    }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); h.assertTrue(s!=null,"MilitaryCamp native registry");
        var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE); h.assertTrue(Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow() instanceof MilitaryCampStructure,"Native structure codec");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,MilitaryCampPiece.ID)); var g=l.getChunkSource().getGenerator();
        for(var dim:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dim,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(64,64),0,l,b->true).isValid(),"Overworld only");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(64,64))).isEmpty(),"SpawnStructures stops new camps"); } finally { LocationConfig.ENABLED.set(enabled); }
        h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel(); var s=structure(l); ChunkPos chosen=null;
        for(int n=513;n<1200;n++) {
            var c=new ChunkPos(sign*64*7,sign*64*n); var stub=s.findGenerationPoint(context(l,c));
            if(stub.isPresent()) {
                var candidate=(MilitaryCampPiece)stub.get().getPiecesBuilder().build().pieces().getFirst();
                if(sign<0||!candidate.plan().loot().isEmpty()) { chosen=c; break; }
            }
        }
        h.assertTrue(chosen!=null,"Find big LAND camp in actual normal terrain");
        var start=l.getChunk(chosen.x(),chosen.z()).getStartForStructure(s); h.assertTrue(start!=null&&start.isValid(),"Native camp start persisted");
        var p=(MilitaryCampPiece)start.getPieces().getFirst(); load(l,p); verify(h,p);
        h.assertValueEqual(restore(l,p).plan(),p.plan(),"Natural camp save keeps full geometry");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,MilitaryCampPiece.ID));
        h.assertTrue(l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.plan().origin(),0,false)!=null,"Native locate finds the camp");
        if(sign>0) {
            var at=p.plan().loot().keySet().iterator().next(); var chest=(ChestBlockEntity)l.getBlockEntity(at); boolean found=false;
            for(int i=0;i<chest.getContainerSize();i++) found|=!chest.getItem(i).isEmpty(); h.assertTrue(found,"Real natural camp chest produces nested rewards");
        }
        var surface=p.plan().cells().entrySet().stream().filter(e->e.getValue().state().isAir()&&e.getKey().getY()>p.plan().origin().getY()-5).map(Map.Entry::getKey).findFirst().orElseThrow();
        h.assertTrue(p.protectsDecoration(surface,l)&&!p.protectsDecoration(surface.atY(p.plan().minY()+1),l),"Protection covers the surface without reserving the underground column");
        h.assertTrue(net.minecraft.world.level.levelgen.feature.TreeFeature.validTreePos(l,surface),"Tree growth remains possible after world generation");
        var config=new net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration(net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(Blocks.STONE));
        h.assertTrue(net.minecraft.world.level.levelgen.feature.Feature.SIMPLE_BLOCK.place(config,l,l.getChunkSource().getGenerator(),RandomSource.create(1),surface),"Ordinary feature use after generation is permitted");
        l.setBlock(surface,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        boolean ore=LocationConfig.ORE_CLUSTERS.get(); try { LocationConfig.ORE_CLUSTERS.set(!ore); h.assertTrue(s.findGenerationPoint(context(l,chosen)).isPresent(),"Big camp selection is independent of ore-cluster switch"); } finally { LocationConfig.ORE_CLUSTERS.set(ore); }
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural MilitaryCamp: chunk={}, size={}x{}, components={}, posts={}, chests={}, cells={}, layoutSeed={}, decorSeed={}",chosen,p.plan().width(),p.plan().depth(),p.plan().components().size(),p.plan().posts().size(),p.plan().loot().size(),p.plan().cells().size(),p.layoutSeed(),p.decorationSeed());
        h.succeed();
    }
    private MilitaryCampGameTests() {}
}
