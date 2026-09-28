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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.core.*;
import techguns.modern.TGContent;
import techguns.modern.npc.*;
import techguns.modern.npc.spawner.*;
import techguns.modern.machine.drill.*;
import techguns.modern.world.*;
import techguns.modern.world.structure.*;

final class SmallMineGameTests {
    private static final long SEED=Long.MIN_VALUE+17031;
    private static List<Block> palette() { return List.of(Blocks.AIR,Blocks.STONE,Blocks.OAK_LOG,Blocks.STRUCTURE_BLOCK,Blocks.RAIL,Blocks.COBBLESTONE_STAIRS,Blocks.WALL_TORCH,NpcSpawnerContent.BLOCK.get()); }
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int type=0;type<7;type++) { int c=type;
            for(int t=0;t<4;t++) { int turn=t; r.register("location_mine_type_"+type+"_rotation_"+t,()->h->rotation(h,c,turn)); }
            r.register("location_mine_paid_saved_drill_type_"+type,()->h->{ var p=piece(h,40+c,0,c,0); fill(h,p,Blocks.STONE); place(h,p,p.getBoundingBox(),1); drill(h,h.getLevel(),p); h.succeed(); });
        }
        for(int t=0;t<4;t++) { int turn=t; r.register("location_mine_reverse_chunk_save_"+t,()->h->clipping(h,turn)); }
        r.register("location_mine_one_surface_clear_column_no_foundation",()->SmallMineGameTests::clearing);
        r.register("location_mine_all_nested_mixture_probabilities",()->SmallMineGameTests::mixtures);
        r.register("location_mine_biome_cover_priority",()->SmallMineGameTests::biomes);
        r.register("location_mine_native_sand_physics",()->SmallMineGameTests::sand);
        r.register("location_mine_two_saved_posts_four_actual_deaths",()->SmallMineGameTests::guards);
        r.register("location_mine_registry_dimension_toggles",()->SmallMineGameTests::registry);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_mine_positive_natural_chunks",()->h->natural(h,1)); r.register("structure_mine_negative_natural_chunks",()->h->natural(h,-1));
            r.register("structure_mine_all_four_candidates_natural_chunks",()->SmallMineGameTests::selection);
        }
    }
    private static SmallMinePiece piece(GameTestHelper h,int slot,int turn,int type,int cover) {
        return new SmallMinePiece(h.getLevel().getServer().getStructureManager(),new BlockPos(3000014+slot*64,140,-3000002),turn,type,cover,SEED);
    }
    private static List<StructureTemplate.StructureBlockInfo> cells(SmallMinePiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static void load(ServerLevel l,SmallMinePiece p) { var b=p.getBoundingBox(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) l.getChunk(x,z); }
    private static void place(GameTestHelper h,SmallMinePiece p,BoundingBox clip,long seed) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(seed),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,SmallMinePiece p) { var b=p.getBoundingBox(); var out=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) out.put(pos.immutable(),l.getBlockState(pos)); return out; }
    private static void fill(GameTestHelper h,SmallMinePiece p,Block block) { load(h.getLevel(),p); for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,block.defaultBlockState(),2); }
    private static BlockState expected(SmallMinePiece p,StructureTemplate.StructureBlockInfo c) { return c.state().is(Blocks.STRUCTURE_BLOCK)?p.mixture(c.nbt().getStringOr("metadata",""),c.pos()):c.state(); }
    private static void verify(GameTestHelper h,ServerLevel l,SmallMinePiece p) {
        int count=0,clusters=0;
        for(var b:palette()) for(var c:cells(p,b)) {
            var expected=expected(p,c); h.assertValueEqual(l.getBlockState(c.pos()),expected,"Original mine cell at "+c.pos()); count++;
            h.assertTrue(p.protectsDecoration(c.pos()),"Source cell is protected during adjacent chunk decoration");
            if(OreClusterContent.BLOCKS.values().stream().anyMatch(v->expected.is(v.get()))) { clusters++; h.assertTrue(expected.is(OreClusterContent.BLOCKS.get("ore_cluster_"+SmallMinePiece.TYPES.get(p.clusterType())).get()),"All clusters share one material"); }
            h.assertTrue(l.getBlockEntity(c.pos())==null || b==NpcSpawnerContent.BLOCK.get(),"No marker tile remains");
        }
        h.assertValueEqual(count,972,"All unique source positions"); h.assertTrue(clusters>=1 && clusters<=7,"Guaranteed center plus six mixed neighbors");
        for(var c:cells(p,Blocks.WALL_TORCH)) { h.assertTrue(c.state().canSurvive(l,c.pos()),"Original rotated torch support"); h.assertValueEqual(c.state().getLightEmission(l,c.pos()),14,"Real torch emission"); }
        for(var c:cells(p,Blocks.RAIL)) h.assertTrue(c.state().canSurvive(l,c.pos()),"Original slope and straight rails have support");
        var posts=cells(p,NpcSpawnerContent.BLOCK.get()); h.assertValueEqual(posts.size(),2,"Two source holes");
        for(var c:posts) { var s=(NpcSpawnerBlockEntity)l.getBlockEntity(c.pos()); h.assertTrue(s!=null,"Mine post tile");
            h.assertValueEqual(s.remaining(),2,"Two-death quota per post"); h.assertValueEqual(s.maximum(),1,"Single active miner per post"); h.assertValueEqual(s.interval(),200,"Original spawn interval");
            h.assertValueEqual(s.range(),1d,"Original radius"); h.assertValueEqual(s.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("zombieminer"),1)),"Original miner species");
        }
    }
    private static void rotation(GameTestHelper h,int type,int turn) {
        var p=piece(h,type*4+turn,turn,type,(type+turn)%4); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox(),1); verify(h,h.getLevel(),p);
        h.assertValueEqual(p.template().getSize(),new Vec3i(17,11,11),"Original scan dimensions");
        for(var b:palette()) for(var c:p.template().filterBlocks(BlockPos.ZERO,new StructurePlaceSettings(),b)) {
            int[] q=StructureRules.rotate(c.pos().getX(),c.pos().getZ(),turn,8,5); var at=p.templatePosition().offset(q[0],c.pos().getY(),q[1]);
            var expected=b==Blocks.STRUCTURE_BLOCK?p.mixture(c.nbt().getStringOr("metadata",""),at):c.state().rotate(p.getRotation());
            h.assertValueEqual(h.getLevel().getBlockState(at),expected,"Independent legacy pivot coordinate");
        } h.succeed();
    }
    private static void clipping(GameTestHelper h,int turn) {
        var p=piece(h,28+turn,turn,6,turn); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox(),1); var expected=snapshot(h.getLevel(),p); fill(h,p,Blocks.OBSIDIAN);
        var box=p.getBoundingBox(); var clips=new ArrayList<BoundingBox>();
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) clips.add(new BoundingBox(x*16,box.minY(),z*16,x*16+15,box.maxY(),z*16+15));
        h.assertTrue(clips.size()>1,"Fixture crosses real chunk borders"); Collections.reverse(clips); var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel());
        for(var clip:clips) { place(h,p,clip,31L*clip.minX()+clip.minZ()); p=(SmallMinePiece)LocationContent.MINE_PIECE.get().load(ctx,p.createTag(ctx));
            h.assertValueEqual(p.clusterType(),6,"Shared type persists"); h.assertValueEqual(p.cover(),turn,"Biome choice persists"); h.assertValueEqual(p.mixtureSeed(),SEED,"Full signed 64-bit mixture seed persists"); }
        h.assertValueEqual(snapshot(h.getLevel(),p),expected,"Reverse chunk order with saves retains exact geometry, cover, ore and attachments"); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void clearing(GameTestHelper h) {
        var p=piece(h,32,1,0,0); fill(h,p,Blocks.OBSIDIAN); var l=h.getLevel(); var original=snapshot(l,p); var bottom=cells(p,Blocks.STRUCTURE_BLOCK).stream().filter(c->c.pos().getY()==140).toList();
        h.assertValueEqual(bottom.size(),1,"Only one y=0 record"); var clear=bottom.getFirst().pos(); l.setBlock(clear.below(),Blocks.AIR.defaultBlockState(),2); place(h,p,p.getBoundingBox(),1);
        var written=new HashSet<BlockPos>(); for(var b:palette()) for(var c:cells(p,b)) written.add(c.pos());
        for(var pos:original.keySet()) if(!written.contains(pos)) {
            boolean cleared=pos.getX()==clear.getX() && pos.getZ()==clear.getZ() && pos.getY()>=146 && pos.getY()<=149;
            h.assertTrue(l.getBlockState(pos).is(cleared?Blocks.AIR:Blocks.OBSIDIAN),"Only four cells above surface can clear omitted scan positions");
            h.assertValueEqual(p.protectsDecoration(pos),cleared,"Omitted terrain outside the cleared column remains decoratable");
        }
        h.assertTrue(l.getBlockState(clear.below()).isAir(),"No foundation beneath the buried cluster"); verify(h,l,p); h.succeed();
    }
    private static void mixtures(GameTestHelper h) {
        var l=h.getLevel();
        List<Map<Block,Double>> ores=List.of(Map.of(Blocks.COAL_ORE,1d),Map.of(Blocks.IRON_ORE,.5,TGOreContent.ORES.get("ore_copper").get(),.25,TGOreContent.ORES.get("ore_tin").get(),.25),
                Map.of(Blocks.REDSTONE_ORE,2d/3,Blocks.LAPIS_ORE,1d/3),Map.of(TGOreContent.ORES.get("ore_lead").get(),1d),
                Map.of(Blocks.GOLD_ORE,2d/3,TGOreContent.ORES.get("ore_titanium").get(),1d/3),Map.of(Blocks.DIAMOND_ORE,3d/7,Blocks.EMERALD_ORE,1d/7,Blocks.STONE,3d/7),Map.of(TGOreContent.ORES.get("ore_uranium").get(),2d/3,Blocks.STONE,1d/3));
        for(int type=0;type<7;type++) {
            var p=piece(h,33,0,type,0); var cluster=OreClusterContent.BLOCKS.get("ore_cluster_"+SmallMinePiece.TYPES.get(type)).get();
            for(String marker:List.of("mine_stone_ore","mine_cluster_ore","mine_patch")) {
                var counts=new HashMap<Block,Integer>(); int n=8192;
                for(int x=0;x<n;x++) counts.merge(p.mixture("techguns:"+marker,new BlockPos(x,137,-x)).getBlock(),1,Integer::sum);
                var expected=new HashMap<Block,Double>();
                if(marker.equals("mine_patch")) { expected.put(Blocks.GRASS_BLOCK,5d/6); expected.put(Blocks.AIR,1d/6); }
                else { double chance=marker.equals("mine_stone_ore")?.25:.5; ores.get(type).forEach((b,w)->expected.merge(b,w*chance,Double::sum)); expected.merge(marker.equals("mine_stone_ore")?Blocks.STONE:cluster,1-chance,Double::sum); }
                h.assertValueEqual(counts.keySet(),expected.keySet(),"All and only source mixture outcomes");
                expected.forEach((b,w)->h.assertTrue(Math.abs(counts.get(b)/(double)n-w)<.025,"Source probability for "+marker+" / "+b));
            }
        }
        var ctx=StructurePieceSerializationContext.fromLevel(l); var p=piece(h,33,0,1,1); var restored=(SmallMinePiece)LocationContent.MINE_PIECE.get().load(ctx,p.createTag(ctx));
        var other=new SmallMinePiece(l.getServer().getStructureManager(),p.templatePosition(),0,1,1,SEED+1); boolean different=false;
        for(var c:cells(p,Blocks.STRUCTURE_BLOCK)) { String m=c.nbt().getStringOr("metadata",""); h.assertValueEqual(restored.mixture(m,c.pos()),p.mixture(m,c.pos()),"Saved mixture does not reroll"); different|=!other.mixture(m,c.pos()).equals(p.mixture(m,c.pos())); }
        h.assertTrue(different,"Distinct seeds produce different mine layouts"); h.succeed();
    }
    private static void biomes(GameTestHelper h) {
        var r=h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        h.assertValueEqual(SmallMineStructure.cover(r.getOrThrow(Biomes.PLAINS)),0,"Grassland");
        h.assertValueEqual(SmallMineStructure.cover(r.getOrThrow(Biomes.SNOWY_BEACH)),1,"Cold overrides beach");
        h.assertValueEqual(SmallMineStructure.cover(r.getOrThrow(Biomes.DESERT)),2,"Sandy");
        h.assertValueEqual(SmallMineStructure.cover(r.getOrThrow(Biomes.BEACH)),2,"Beach");
        h.assertValueEqual(SmallMineStructure.cover(r.getOrThrow(Biomes.BADLANDS)),2,"Mesa");
        h.assertValueEqual(SmallMineStructure.cover(r.getOrThrow(Biomes.NETHER_WASTES)),3,"Original Nether cover fallback"); h.succeed();
    }
    private static void sand(GameTestHelper h) {
        var p=piece(h,35,0,0,2); fill(h,p,Blocks.STONE); place(h,p,p.getBoundingBox(),1); var l=h.getLevel();
        var pos=cells(p,Blocks.STRUCTURE_BLOCK).stream().map(StructureTemplate.StructureBlockInfo::pos).filter(at->l.getBlockState(at).is(Blocks.SAND)).max(Comparator.comparingInt(BlockPos::getY)).orElseThrow();
        h.assertTrue(l.getBlockTicks().hasScheduledTick(pos,Blocks.SAND),"Generated sand receives vanilla falling physics");
        l.setBlock(pos.below(),Blocks.AIR.defaultBlockState(),2); l.setBlock(pos.below(2),Blocks.STONE.defaultBlockState(),2);
        // Entities added inside a test callback may not enter the level lookup until the next tick.
        var falling=new ArrayList<net.minecraft.world.entity.item.FallingBlockEntity>();
        Consumer<EntityJoinLevelEvent> observe=e->{ if(e.getLevel()==l && e.getEntity() instanceof net.minecraft.world.entity.item.FallingBlockEntity entity && entity.blockPosition().equals(pos)) falling.add(entity); };
        NeoForge.EVENT_BUS.addListener(observe);
        try {
            l.getBlockState(pos).tick(l,pos,RandomSource.create(1)); h.assertValueEqual(falling.size(),1,"Real falling sand entity");
            for(int tick=0;tick<40 && !falling.getFirst().isRemoved();tick++) falling.getFirst().tick();
            h.assertTrue(falling.getFirst().isRemoved() && l.getBlockState(pos).isAir() && l.getBlockState(pos.below()).is(Blocks.SAND),"Sand settles on native terrain"); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); falling.forEach(Entity::discard); }
    }
    private static NpcSpawnerBlockEntity reload(ServerLevel l,NpcSpawnerBlockEntity b) {
        var tag=b.saveWithFullMetadata(l.registryAccess()); var pos=b.getBlockPos(); var state=b.getBlockState(); l.removeBlockEntity(pos);
        var restored=(NpcSpawnerBlockEntity)BlockEntity.loadStatic(pos,state,tag,l.registryAccess()); l.setBlockEntity(restored); return restored;
    }
    private static void guards(GameTestHelper h) {
        var p=piece(h,34,0,0,0); fill(h,p,Blocks.STONE); place(h,p,p.getBoundingBox(),1); var l=h.getLevel(); var spawned=new LinkedHashMap<UUID,ZombieMiner>();
        Consumer<EntityJoinLevelEvent> observe=e->{ if(e.getLevel()==l && e.getEntity() instanceof ZombieMiner npc && p.getBoundingBox().isInside(npc.blockPosition())) { npc.removeFreeWill(); spawned.put(npc.getUUID(),npc); } };
        NeoForge.EVENT_BUS.addListener(observe);
        try {
            var posts=cells(p,NpcSpawnerContent.BLOCK.get()).stream().map(c->(NpcSpawnerBlockEntity)l.getBlockEntity(c.pos())).toList();
            h.assertTrue(!posts.getFirst().link().equals(posts.getLast().link()),"Separate saved post owners");
            for(var initial:posts) {
                var b=initial; var pos=b.getBlockPos(); for(int tick=0;tick<199;tick++) NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
                h.assertValueEqual(b.activeCount(),0,"No early spawn"); h.assertValueEqual(b.delay(),1,"Original first delay");
                for(int death=0;death<2;death++) {
                    for(int tick=0;tick<600;tick++) NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
                    h.assertValueEqual(b.activeCount(),1,"One miner after several complete intervals");
                    var npc=spawned.get(b.activeIds().iterator().next()); h.assertTrue(npc!=null && npc.isAlive(),"Actual spawned ZombieMiner"); h.assertValueEqual(npc.spawnerLink(),b.link(),"Native miner owner");
                    b=reload(l,b); h.assertValueEqual(b.activeIds(),Set.of(npc.getUUID()),"Live reservation survives block reload");
                    var copy=new ZombieMiner(NpcContent.MINER.get(),l); NetherGameTests.load(h,copy,NetherGameTests.save(h,npc));
                    h.assertValueEqual(copy.spawnerLink(),b.link(),"NPC saved owner reconnects to the saved post"); h.assertTrue(!npc.getMainHandItem().isEmpty(),"Source equipment initialized");
                    npc.hurtServer(l,l.damageSources().genericKill(),10000); h.assertTrue(!npc.isAlive(),"Real miner death"); h.assertValueEqual(b.remaining(),1-death,"Exactly one death consumed");
                    NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
                }
                h.assertTrue(l.getBlockState(pos).isAir(),"Second death removes this post");
            }
            h.assertValueEqual(spawned.size(),4,"Exactly four real miners across two posts"); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); spawned.values().forEach(Entity::discard); }
    }
    private static SmallMineStructure structure(ServerLevel l) { return (SmallMineStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(SmallMinePiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c,long seed) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),seed,c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE); var decoded=(SmallMineStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow();
        h.assertTrue(!Structure.DIRECT_CODEC.encodeStart(ops,decoded).getOrThrow().getAsJsonObject().has("reserved_medium_grid"),"Grid reservation belongs to the shared placement"); h.assertTrue(!Structure.DIRECT_CODEC.encodeStart(ops,decoded).getOrThrow().getAsJsonObject().has("reserved_big_grid"),"Grid reservation belongs to the shared placement");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,SmallMinePiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dimension:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dimension,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(240,16),0,l,b->true).isValid(),"Explicit Overworld guard");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(240,16),0)).isEmpty(),"Global switch"); } finally { LocationConfig.ENABLED.set(enabled); }
        for(int n=-5;n<=5;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32*n),0)).isEmpty(),"Medium and big grids remain reserved"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel(); var s=structure(l); ChunkPos chosen=null;
        for(int n=129;n<=2048;n++) { var c=new ChunkPos(sign*240,sign*16*n); if(s.findGenerationPoint(context(l,c,l.getSeed())).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Native land search finds the mine"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Native mine start persisted"); var p=(SmallMinePiece)start.getPieces().getFirst(); load(l,p); var box=p.getBoundingBox();
        for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++) for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++) l.getChunk(x,z);
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z).postProcessGeneration(l);
        verify(h,l,p);
        oreDecoration(h,l,p);
        for(var id:List.of(FactoryHousePiece.TEMPLATE,TrainStationPiece.TEMPLATE,GasStationPiece.TEMPLATE,PoliceStationPiece.TEMPLATE,SurvivorHideoutPiece.TEMPLATE,DesertOilPiece.TEMPLATE,MeteorPiece.TEMPLATE,OreSpikePiece.TEMPLATE,TGContent.id("alienbug_nest"))) {
            var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"Other small/medium candidates never overlap mine ticket"); }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,SmallMinePiece.TEMPLATE)); var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(8,5,5),0,false);
        h.assertTrue(found!=null,"Native locate finds the mine"); h.assertValueEqual(new ChunkPos(found.getFirst().getX()>>4,found.getFirst().getZ()>>4),chosen,"Locate returns source chunk");
        var before=snapshot(l,p); var owners=cells(p,NpcSpawnerContent.BLOCK.get()).stream().map(c->((NpcSpawnerBlockEntity)l.getBlockEntity(c.pos())).instance()).toList(); l.getChunk(chosen.x(),chosen.z());
        h.assertValueEqual(snapshot(l,p),before,"Repeat chunk request cannot reroll the mine"); h.assertValueEqual(cells(p,NpcSpawnerContent.BLOCK.get()).stream().map(c->((NpcSpawnerBlockEntity)l.getBlockEntity(c.pos())).instance()).toList(),owners,"Post owners persist");
        var grass=cells(p,Blocks.AIR).stream().map(StructureTemplate.StructureBlockInfo::pos).filter(pos->Blocks.SHORT_GRASS.defaultBlockState().canSurvive(l,pos)).findFirst().orElseThrow();
        var config=new net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration(net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(Blocks.SHORT_GRASS));
        h.assertTrue(net.minecraft.world.level.levelgen.feature.Feature.SIMPLE_BLOCK.place(config,l,l.getChunkSource().getGenerator(),RandomSource.create(1),grass),"Post-generation feature use remains allowed");
        h.assertTrue(l.getBlockState(grass).is(Blocks.SHORT_GRASS),"Players can still change the mine's cleared cells"); l.setBlock(grass,Blocks.AIR.defaultBlockState(),2);
        drill(h,l,p);
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural SmallMine: chunk={}, origin={}, rotation={}, type={}, cover={}, mixtureSeed={}, drill=true",chosen,p.templatePosition(),p.getRotation(),p.clusterType(),p.cover(),p.mixtureSeed()); h.succeed();
    }
    private static void drill(GameTestHelper h,ServerLevel l,SmallMinePiece p) {
        var target=cells(p,Blocks.STRUCTURE_BLOCK).stream().filter(c->c.nbt().getStringOr("metadata","").equals("techguns:mine_cluster")).findFirst().orElseThrow().pos();
        var cluster=l.getBlockState(target); var origin=target.west(2); l.setBlock(origin,OreDrillContent.BLOCKS.get("controller").get().defaultBlockState(),3); l.setBlock(origin.east(),OreDrillContent.BLOCKS.get("rod").get().defaultBlockState(),3);
        var d=(OreDrillBlockEntity)l.getBlockEntity(origin); var player=FakePlayerFactory.getMinecraft(l); player.setPos(Vec3.atCenterOf(origin).add(0,2,0));
        h.assertTrue(d.form(player),"Real drill forms against the mine's guaranteed cluster"); d.setItem(0,TGContent.MATERIALS.get("oredrillsmall_carbon").toStack()); OreDrillBlockEntity.tick(l,origin,d.getBlockState(),d);
        int duration=d.data.get(2),power=d.data.get(3); h.assertTrue(duration>0 && power>0,"Original paid cycle starts");
        for(int paid=0;paid<duration;) {
            int ticks=Math.min(duration-paid,OreDrillBlockEntity.CAPACITY/power); try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(d.energy().insert(ticks*power,tx),ticks*power,"Exact cycle energy inserted"); tx.commit(); }
            for(int i=0;i<ticks;i++) OreDrillBlockEntity.tick(l,origin,d.getBlockState(),d); paid+=ticks;
            if(paid<duration) h.assertTrue(d.getItem(2).isEmpty(),"No partial production");
            var tag=d.saveWithFullMetadata(l.registryAccess()); l.removeBlockEntity(origin); d=(OreDrillBlockEntity)BlockEntity.loadStatic(origin,l.getBlockState(origin),tag,l.registryAccess()); l.setBlockEntity(d);
        }
        var out=d.getItem(2); h.assertTrue(out.getCount()==1 && ClusterOutputs.entries(SmallMinePiece.TYPES.get(p.clusterType())).stream().anyMatch(e->e.item().is(out.getItem())),"One actual source resource from this mine");
        h.assertValueEqual(d.energy().getAmountAsInt(),0,"All and only paid energy consumed"); h.assertValueEqual(l.getBlockState(target),cluster,"Cluster survives real production");
    }
    private static void oreDecoration(GameTestHelper h,ServerLevel level,SmallMinePiece piece) {
        var stone=cells(piece,Blocks.STONE).getFirst().pos(); var before=snapshot(level,piece);
        // Exercise native OreFeature's section writer deterministically, including structure references.
        // The region fixture reads the already-generated chunks instead of launching another generation task.
        var step=net.minecraft.world.level.chunk.status.ChunkPyramid.GENERATION_PYRAMID.getStepTo(net.minecraft.world.level.chunk.status.ChunkStatus.FEATURES);
        var region=new net.minecraft.server.level.WorldGenRegion(level,null,step,level.getChunkAt(stone)) {
            @Override public net.minecraft.world.level.chunk.ChunkAccess getChunk(int x,int z,net.minecraft.world.level.chunk.status.ChunkStatus status,boolean load) {
                return level.getChunk(x,z,status,load);
            }
        };
        var feature=new OreProbe(); feature.placeAt(region,stone);
        verify(h,level,piece);
        h.assertTrue(feature.placeAt(level,stone),"Ordinary ore placement remains available after world generation");
        h.assertTrue(cells(piece,Blocks.STONE).stream().anyMatch(c->level.getBlockState(c.pos()).is(Blocks.DIAMOND_ORE)),"The same native vein reaches authored stone when outside WorldGenRegion");
        before.forEach((pos,state)->{if(!level.getBlockState(pos).equals(state))level.setBlock(pos,state,2);});
        verify(h,level,piece);
    }
    private static final class OreProbe extends net.minecraft.world.level.levelgen.feature.OreFeature {
        OreProbe() { super(net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration.CODEC); }
        boolean placeAt(WorldGenLevel level,BlockPos pos) {
            var config=new net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration(new BlockMatchTest(Blocks.STONE),Blocks.DIAMOND_ORE.defaultBlockState(),32);
            return doPlace(level,RandomSource.create(31),config,pos.getX(),pos.getX(),pos.getZ(),pos.getZ(),pos.getY(),pos.getY(),pos.getX()-6,pos.getY()-6,pos.getZ()-6,12,12);
        }
    }
    private static void selection(GameTestHelper h) {
        var l=h.getLevel(); var r=l.registryAccess().lookupOrThrow(Registries.STRUCTURE); var candidates=List.of(FactoryHousePiece.TEMPLATE,TrainStationPiece.TEMPLATE,SmallMinePiece.TEMPLATE,GasStationPiece.TEMPLATE).stream().map(id->(SmallOverworldStructure)r.getValue(id)).toList();
        int[] counts=new int[4]; boolean ores=LocationConfig.ORE_CLUSTERS.get();
        try {
            for(long seed:new long[]{0,1,42}) for(int n=-64;n<=64;n++) {
                var c=new ChunkPos(240,16*n); int selected=SmallOverworldRules.candidate(context(l,c,seed).random().nextInt(40)),found=0;
                for(int i=0;i<4;i++) { var s=candidates.get(i); LocationConfig.ORE_CLUSTERS.set(true); boolean yes=s.findGenerationPoint(context(l,c,seed)).isPresent();
                    LocationConfig.ORE_CLUSTERS.set(false); h.assertValueEqual(s.findGenerationPoint(context(l,c,seed)).isPresent(),yes,"All four source small entries ignore ore toggle");
                    if(yes) { h.assertValueEqual(i,selected,"Only original ticket owner generates"); counts[i]++; found++; } }
                h.assertTrue(found<=1,"Four native structure IDs share one choice without overlap");
            }
        } finally { LocationConfig.ORE_CLUSTERS.set(ores); }
        for(int n:counts) h.assertTrue(n>0,"Three seeds exercise every small candidate"); h.succeed();
    }
    private SmallMineGameTests() {}
}
