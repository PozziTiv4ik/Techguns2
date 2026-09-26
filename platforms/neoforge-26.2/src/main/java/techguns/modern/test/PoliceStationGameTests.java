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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.npc.ZombiePoliceman;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.*;
import techguns.modern.world.structure.*;

final class PoliceStationGameTests {
    private static final ResourceKey<LootTable> LOOT=ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/policestation"));
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int t=0;t<4;t++) { int turn=t; r.register("location_police_rotation_"+t,()->h->rotation(h,turn)); r.register("location_police_reverse_chunk_save_"+t,()->h->clipping(h,turn)); }
        r.register("location_police_clearing_and_foundation",()->PoliceStationGameTests::foundation);
        r.register("location_police_three_finite_posts",()->PoliceStationGameTests::guards);
        r.register("location_police_double_chest_save_and_fire",()->PoliceStationGameTests::chests);
        r.register("location_police_all_four_loot_pools",()->PoliceStationGameTests::loot);
        r.register("location_police_rotated_doors_and_lamps",()->PoliceStationGameTests::doors);
        r.register("location_police_registry_dimension_toggles",()->PoliceStationGameTests::registry);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_police_positive_natural_chunks",()->h->natural(h,1)); r.register("structure_police_negative_natural_chunks",()->h->natural(h,-1));
            r.register("structure_police_shared_table_natural_chunks",()->PoliceStationGameTests::selection);
        }
    }
    private static Block concrete() { return BuildingContent.BLOCKS.get("concrete_brown").get(); }
    private static List<Block> palette() { return List.of(concrete(),Blocks.IRON_BLOCK,Blocks.IRON_BARS,Blocks.AIR,FortificationContent.SANDBAGS.get(),FortificationContent.LAMPS.get("lamp_white").get(),Blocks.STONE_BRICKS,Blocks.CONCRETE.pick(DyeColor.BLUE),FortificationContent.DOOR.get(),Blocks.GLASS_PANE,Blocks.OAK_PLANKS,Blocks.CHEST,Blocks.POLISHED_ANDESITE,NpcSpawnerContent.SOLDIER_BLOCK.get(),Blocks.RAIL,BuildingContent.BLOCKS.get("ladder_metal").get(),Blocks.SMOOTH_STONE_SLAB,Blocks.CRAFTING_TABLE,Blocks.OAK_FENCE_GATE); }
    private static PoliceStationPiece piece(GameTestHelper h,int slot,int turn) { return new PoliceStationPiece(h.getLevel().getServer().getStructureManager(),new BlockPos(2000014+slot*64,140,-2000002),turn); }
    private static List<StructureTemplate.StructureBlockInfo> cells(PoliceStationPiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static void load(ServerLevel l,PoliceStationPiece p) { var b=p.getBoundingBox(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) l.getChunk(x,z); }
    private static void place(GameTestHelper h,PoliceStationPiece p,BoundingBox clip,long seed) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(seed),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,PoliceStationPiece p) { var b=p.getBoundingBox(); var out=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) out.put(pos.immutable(),l.getBlockState(pos)); return out; }
    private static void fill(GameTestHelper h,PoliceStationPiece p,Block block) { load(h.getLevel(),p); for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,block.defaultBlockState(),2); }
    private static PoliceStationPiece placed(GameTestHelper h,int slot) { var p=piece(h,slot,0); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),87); return p; }
    private static void verify(GameTestHelper h,ServerLevel l,PoliceStationPiece p) {
        var positions=new HashSet<BlockPos>();
        for(var block:palette()) for(var c:cells(p,block)) {
            positions.add(c.pos()); var expected=c.state();
            if(block==Blocks.IRON_BARS || block==Blocks.GLASS_PANE || block==FortificationContent.SANDBAGS.get()) expected=Block.updateFromNeighbourShapes(expected,l,c.pos());
            h.assertValueEqual(l.getBlockState(c.pos()),expected,"Exact police source cell at "+c.pos());
        }
        h.assertValueEqual(positions.size(),1212,"All source cells mapped");
        var box=p.getBoundingBox(); for(int x=box.minX();x<=box.maxX();x++) for(int z=box.minZ();z<=box.maxZ();z++) for(int y=1;y<=7;y++) {
            var pos=new BlockPos(x,p.templatePosition().getY()+y,z); if(!positions.contains(pos)) h.assertTrue(l.getBlockState(pos).isAir(),"Source cleanup also clears unrecorded scan cells");
        }
        var posts=cells(p,NpcSpawnerContent.SOLDIER_BLOCK.get()); h.assertValueEqual(posts.size(),3,"Three military-style source posts");
        for(var c:posts) {
            var b=(NpcSpawnerBlockEntity)l.getBlockEntity(c.pos()); h.assertTrue(b!=null,"Post tile placed"); h.assertValueEqual(b.remaining(),3,"Three deaths per post"); h.assertValueEqual(b.maximum(),1,"One active per post");
            h.assertValueEqual(b.interval(),200,"Source timer"); h.assertValueEqual(b.range(),1d,"Source radius"); h.assertValueEqual(b.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("zombiepoliceman"),1)),"Correct source species");
        }
        h.assertValueEqual(cells(p,Blocks.CHEST).size(),3,"Three source chest tiles"); for(var c:cells(p,Blocks.CHEST)) h.assertValueEqual(((ChestBlockEntity)l.getBlockEntity(c.pos())).getLootTable(),LOOT,"Deferred original police loot");
    }
    private static void rotation(GameTestHelper h,int turn) { var p=piece(h,turn,turn); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox(),turn+1); verify(h,h.getLevel(),p); h.assertValueEqual(p.template().getSize(),new Vec3i(13,8,13),"Original bounds"); h.succeed(); }
    private static void clipping(GameTestHelper h,int turn) {
        var p=piece(h,4+turn,turn); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),1); var expected=snapshot(h.getLevel(),p); fill(h,p,Blocks.AIR); var b=p.getBoundingBox(); var clips=new ArrayList<BoundingBox>();
        for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) clips.add(new BoundingBox(x*16,b.minY(),z*16,x*16+15,b.maxY(),z*16+15));
        h.assertTrue(clips.size()>1,"Fixture crosses native chunk edges"); Collections.reverse(clips); var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel());
        for(var clip:clips) { place(h,p,clip,2); p=(PoliceStationPiece)LocationContent.POLICE_PIECE.get().load(ctx,p.createTag(ctx)); }
        h.assertValueEqual(snapshot(h.getLevel(),p),expected,"Reverse native chunks and saves keep paired doors/chests/rail curves"); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,8,1); fill(h,p,Blocks.AIR); var bottom=cells(p,concrete()).stream().filter(c->c.pos().getY()==p.templatePosition().getY()).toList(); h.assertValueEqual(bottom.size(),169,"Every original floor column");
        var stop=bottom.getFirst().pos(); h.getLevel().setBlock(stop.below(),Blocks.OBSIDIAN.defaultBlockState(),2); h.getLevel().setBlock(stop.below(2),Blocks.OBSIDIAN.defaultBlockState(),2);
        for(var c:bottom) { h.getLevel().setBlock(c.pos().below(4),Blocks.AIR.defaultBlockState(),2); h.getLevel().setBlock(c.pos().above(8),Blocks.OBSIDIAN.defaultBlockState(),2); }
        place(h,p,p.getBoundingBox(),1);
        for(var c:bottom) for(int d=1;d<=4;d++) h.assertTrue(h.getLevel().getBlockState(c.pos().below(d)).is(d==4?Blocks.AIR:c.pos().equals(stop)&&d<3?Blocks.OBSIDIAN:concrete()),"Three-deep replaceable fill continues under two solids");
        for(var c:bottom) h.assertTrue(h.getLevel().getBlockState(c.pos().above(8)).is(Blocks.OBSIDIAN),"Seven-high cleanup does not reach eighth layer"); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void guards(GameTestHelper h) {
        var p=placed(h,9); var l=h.getLevel(); var spawned=new LinkedHashMap<UUID,ZombiePoliceman>(); Consumer<EntityJoinLevelEvent> observe=e->{ if(e.getLevel()==l && e.getEntity() instanceof ZombiePoliceman npc && p.getBoundingBox().isInside(npc.blockPosition())) { npc.removeFreeWill(); spawned.put(npc.getUUID(),npc); } };
        NeoForge.EVENT_BUS.addListener(observe);
        try {
            for(var c:cells(p,NpcSpawnerContent.SOLDIER_BLOCK.get())) {
                var b=(NpcSpawnerBlockEntity)l.getBlockEntity(c.pos());
                for(int death=0;death<3;death++) {
                    for(int tick=0;tick<400;tick++) NpcSpawnerBlockEntity.serverTick(l,c.pos(),b.getBlockState(),b);
                    h.assertValueEqual(b.activeCount(),1,"Only one live guard at each post"); var npc=spawned.get(b.activeIds().iterator().next()); h.assertTrue(npc!=null && npc.isAlive(),"Actual police NPC accepted");
                    h.assertValueEqual(npc.spawnerLink(),b.link(),"Guard retains source daylight exemption and ownership"); h.assertTrue(npc.armed(),"Real guard has a handgun"); npc.hurtServer(l,l.damageSources().genericKill(),10000);
                    h.assertTrue(!npc.isAlive(),"Actual guard died"); h.assertValueEqual(b.remaining(),2-death,"One quota per real death"); NpcSpawnerBlockEntity.serverTick(l,c.pos(),b.getBlockState(),b);
                }
                h.assertTrue(l.getBlockState(c.pos()).isAir(),"Third death removes its own post");
            }
            h.assertValueEqual(spawned.size(),9,"Nine distinct real police spawns"); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); spawned.values().forEach(Entity::discard); }
    }
    private static ChestBlockEntity reload(ServerLevel l,ChestBlockEntity b) { var tag=b.saveWithFullMetadata(l.registryAccess()); var pos=b.getBlockPos(); var state=b.getBlockState(); l.removeBlockEntity(pos); var restored=(ChestBlockEntity)BlockEntity.loadStatic(pos,state,tag,l.registryAccess()); l.setBlockEntity(restored); return restored; }
    private static Map<Item,Integer> items(net.minecraft.world.Container c) { var out=new HashMap<Item,Integer>(); for(int i=0;i<c.getContainerSize();i++) { var s=c.getItem(i); if(!s.isEmpty()) out.merge(s.getItem(),s.getCount(),Integer::sum); } return out; }
    private static LootParams params(ServerLevel l,BlockPos pos) { return new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST); }
    private static void chests(GameTestHelper h) {
        var l=h.getLevel(); var p=placed(h,10); var table=l.getServer().reloadableRegistries().getLootTable(LOOT); var seeds=new HashSet<Long>();
        for(var c:cells(p,Blocks.CHEST)) {
            var b=(ChestBlockEntity)l.getBlockEntity(c.pos()); h.assertTrue(seeds.add(b.getLootTableSeed()),"Independent seed per original chest tile"); long seed;
            for(seed=1;seed<10000;seed++) if(table.getRandomItems(params(l,c.pos()),seed).stream().anyMatch(s->s.getItem() instanceof GunItem)) break;
            h.assertTrue(seed<10000,"Reproducible gun-bearing original reward"); b.setLootTableSeed(seed); b=reload(l,b); h.assertValueEqual(b.getLootTableSeed(),seed,"Unopened seed persists");
            var expected=new HashMap<Item,Integer>(); table.getRandomItems(params(l,c.pos()),seed).forEach(s->expected.merge(s.getItem(),s.getCount(),Integer::sum));
            var menu=b.createMenu(1,WeaponGameTests.player(h).getInventory(),WeaponGameTests.player(h)); h.assertTrue(menu!=null,"Real reward tile opens"); h.assertValueEqual(items(b),expected,"Exact original pool contents");
            var gun=java.util.stream.IntStream.range(0,b.getContainerSize()).mapToObj(b::getItem).filter(s->s.getItem() instanceof GunItem).findFirst().orElseThrow(); var player=WeaponGameTests.player(h); player.setItemInHand(InteractionHand.MAIN_HAND,gun);
            int ammo=GunItem.rounds(gun); h.assertValueEqual(ammo,((GunItem)gun.getItem()).definition().stats().capacity(),"Legacy metadata-zero gun reward is loaded"); h.assertTrue(GunItem.fire(l,player,gun),"Actual reward can fire immediately"); h.assertValueEqual(GunItem.rounds(gun),ammo-1,"One shot consumes reward ammo");
            l.getEntitiesOfClass(Bullet.class,new AABB(player.blockPosition()).inflate(8),shot->shot.getOwner()==player).forEach(Entity::discard);
            var after=items(b); b=reload(l,b); h.assertValueEqual(items(b),after,"Opened inventory persists"); for(int i=0;i<b.getContainerSize();i++) b.removeItemNoUpdate(i); b=reload(l,b); h.assertTrue(b.isEmpty() && b.getLootTable()==null,"Empty chest cannot reroll");
        }
        var paired=cells(p,Blocks.CHEST).stream().filter(c->c.state().getValue(ChestBlock.TYPE)!=ChestType.SINGLE).toList(); h.assertValueEqual(paired.size(),2,"Original double chest halves");
        var c=paired.getFirst(); var container=ChestBlock.getContainer((ChestBlock)Blocks.CHEST,l.getBlockState(c.pos()),l,c.pos(),true); h.assertTrue(container!=null && container.getContainerSize()==54,"Native connection exposes full double inventory"); h.succeed();
    }
    private static void loot(GameTestHelper h) {
        var table=h.getLevel().getServer().reloadableRegistries().getLootTable(LOOT); var seen=new HashSet<String>();
        for(long seed=0;seed<1024;seed++) for(var s:table.getRandomItems(params(h.getLevel(),BlockPos.ZERO),seed)) {
            seen.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            if(s.getItem() instanceof GunItem gun) h.assertValueEqual(GunItem.rounds(s),gun.definition().stats().capacity(),"Every source gun entry has its full native component");
        }
        var expected=new HashSet<String>();
        for(var id:List.of("minecraft:iron_ingot","minecraft:gunpowder","techguns:heavycloth","techguns:plasticsheet","techguns:rubberbar","techguns:ingotobsidiansteel")) expected.add(id);
        for(var id:List.of("revolver","thompson","boltaction","m4","pistol","combatshotgun","mac10","aug","pistolrounds","shotgunrounds","riflerounds","smgmagazine","pistolmagazine","assaultriflemagazine")) expected.add("techguns:"+id);
        for(var kind:List.of("t1_combat","t2_combat","t2_commando")) for(var part:List.of("helmet","chestplate","leggings","boots")) expected.add("techguns:"+kind+"_"+part);
        h.assertValueEqual(seen,expected,"All 32 original entries across four loaded pools, no missing rewards"); h.succeed();
    }
    private static void doors(GameTestHelper h) {
        for(int turn=0;turn<4;turn++) {
            var p=piece(h,12+turn,turn); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),2); var l=h.getLevel(); var player=WeaponGameTests.player(h);
            for(var c:cells(p,FortificationContent.DOOR.get())) if(c.state().getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER) {
                var pos=c.pos(); l.getBlockState(pos).useWithoutItem(l,player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
                h.assertTrue(l.getBlockState(pos).getValue(DoorBlock.OPEN) && l.getBlockState(pos.above()).getValue(DoorBlock.OPEN),"Both rotated bunker-door halves open");
                l.getBlockState(pos.above()).useWithoutItem(l,player,new BlockHitResult(Vec3.atCenterOf(pos.above()),Direction.UP,pos.above(),false));
                h.assertTrue(!l.getBlockState(pos).getValue(DoorBlock.OPEN) && !l.getBlockState(pos.above()).getValue(DoorBlock.OPEN),"Upper half closes original door");
            }
            for(var c:cells(p,FortificationContent.LAMPS.get("lamp_white").get())) h.assertTrue(l.getBlockState(c.pos()).canSurvive(l,c.pos()),"All rotated lamps retain original supports");
        } h.succeed();
    }
    private static PoliceStationStructure structure(ServerLevel l) { return (PoliceStationStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(PoliceStationPiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c,long seed) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),seed,c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE); var decoded=(PoliceStationStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow(); h.assertValueEqual(decoded.bigGrid(),64,"Native codec preserves reserved grid");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,PoliceStationPiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dimension:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dimension,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(32,32),0,l,b->true).isValid(),"Overworld-only guard");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32),0)).isEmpty(),"Global structure switch"); } finally { LocationConfig.ENABLED.set(enabled); }
        for(int n=-5;n<=5;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(64,64*n),0)).isEmpty(),"Big grid remains reserved"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel(); var s=structure(l); ChunkPos chosen=null;
        for(int n=129;n<=2048;n++) { var c=new ChunkPos(sign*32,sign*32*n); if(s.findGenerationPoint(context(l,c,l.getSeed())).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Source police ticket and surface find native land"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Native police start saved"); var p=(PoliceStationPiece)start.getPieces().getFirst(); load(l,p); var box=p.getBoundingBox();
        for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++) for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++) l.getChunk(x,z);
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z).postProcessGeneration(l);
        verify(h,l,p);
        for(var id:List.of(MeteorPiece.TEMPLATE,OreSpikePiece.TEMPLATE,TGContent.id("alienbug_nest"))) { var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"Medium candidates never overlap police ticket"); }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,PoliceStationPiece.TEMPLATE)); var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(6,3,6),0,false);
        h.assertTrue(found!=null,"Native locate finds actual police station"); h.assertValueEqual(new ChunkPos(found.getFirst().getX()>>4,found.getFirst().getZ()>>4),chosen,"Locate start chunk");
        var before=snapshot(l,p); var saved=cells(p,Blocks.CHEST).stream().map(c->l.getBlockEntity(c.pos()).saveWithFullMetadata(l.registryAccess())).toList(); l.getChunk(chosen.x(),chosen.z());
        h.assertValueEqual(snapshot(l,p),before,"Repeat chunk request does not regenerate station"); h.assertValueEqual(cells(p,Blocks.CHEST).stream().map(c->l.getBlockEntity(c.pos()).saveWithFullMetadata(l.registryAccess())).toList(),saved,"Original rewards retain seeds");
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural PoliceStation: chunk={}, origin={}, rotation={}, guards=3, chestTiles=3",chosen,p.templatePosition(),p.getRotation()); h.succeed();
    }
    private static void selection(GameTestHelper h) {
        var l=h.getLevel(); var registry=l.registryAccess().lookupOrThrow(Registries.STRUCTURE); var police=structure(l); var meteor=(MeteorStructure)registry.getValue(MeteorPiece.TEMPLATE); var spike=(OreSpikeStructure)registry.getValue(OreSpikePiece.TEMPLATE); var bug=(BugNestStructure)registry.getValue(TGContent.id("alienbug_nest")); boolean ores=LocationConfig.ORE_CLUSTERS.get();
        try { for(boolean enabled:List.of(false,true)) { LocationConfig.ORE_CLUSTERS.set(enabled); int hit=0;
            for(long seed:new long[]{0,1,42}) for(int n=-96;n<=96;n++) {
                var c=new ChunkPos(32,32*n); boolean p=police.findGenerationPoint(context(l,c,seed)).isPresent(),m=meteor.findGenerationPoint(context(l,c,seed)).isPresent(),s=spike.findGenerationPoint(context(l,c,seed)).isPresent(),b=bug.findGenerationPoint(context(l,c,seed)).isPresent();
                h.assertTrue((p?1:0)+(m?1:0)+(s?1:0)+(b?1:0)<=1,"Shared source selection never overlaps four implemented candidates"); if(p) hit++;
            }
            h.assertTrue(hit>0,"Police stations remain possible with ore structures disabled");
        } } finally { LocationConfig.ORE_CLUSTERS.set(ores); } h.succeed();
    }
    private PoliceStationGameTests() {}
}
