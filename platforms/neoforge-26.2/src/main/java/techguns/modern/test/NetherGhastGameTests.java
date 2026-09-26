package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.NetherCastleRules;
import techguns.modern.TGContent;
import techguns.modern.npc.Ghastling;
import techguns.modern.world.NetherMetalContent;
import techguns.modern.world.structure.*;

final class NetherGhastGameTests {
    private static final ResourceKey<LootTable> LOOT=ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/factory_building"));
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int turn=0;turn<4;turn++) {
            int t=turn; r.register("location_ghast_cage_rotation_"+turn,()->h->rotation(h,t));
            r.register("location_ghast_cage_reverse_chunk_save_"+turn,()->h->clipping(h,t));
        }
        r.register("location_ghast_cage_thirteen_foundation_columns",()->NetherGhastGameTests::foundation);
        r.register("location_ghast_cage_vanilla_spawner_saved_defaults",()->NetherGhastGameTests::spawnerSave);
        r.register("location_ghast_cage_native_spawn_and_player_range",()->NetherGhastGameTests::spawning);
        r.register("location_ghast_cage_chest_survives_open_and_reload",()->NetherGhastGameTests::chest);
        r.register("location_ghast_cage_registry_and_dimension",()->NetherGhastGameTests::registry);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_ghast_cage_positive_natural_chunks",()->h->natural(h,1));
            r.register("structure_ghast_cage_negative_natural_chunks",()->h->natural(h,-1));
            r.register("structure_medium_nether_table_natural_chunks",()->NetherGhastGameTests::table);
        }
    }
    private static Block metal() { return NetherMetalContent.BLOCKS.get("nethermetal_grey_dark").get(); }
    private static NetherGhastPiece piece(GameTestHelper h,int slot,int turn) { return new NetherGhastPiece(h.getLevel().getServer().getStructureManager(),new BlockPos(1800014+slot*64,140,-1800002),turn); }
    private static List<StructureTemplate.StructureBlockInfo> cells(NetherGhastPiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static BlockPos pos(NetherGhastPiece p,Block b) { return cells(p,b).getFirst().pos(); }
    private static void load(ServerLevel l,NetherGhastPiece p) { var b=p.getBoundingBox(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) l.getChunk(x,z); }
    private static void place(GameTestHelper h,NetherGhastPiece p,BoundingBox clip,long seed) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(seed),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,NetherGhastPiece p) { var b=p.getBoundingBox(); var out=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) out.put(pos.immutable(),l.getBlockState(pos)); return out; }
    private static void fill(GameTestHelper h,NetherGhastPiece p,Block block) { load(h.getLevel(),p); for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,block.defaultBlockState(),2); }
    private static NetherGhastPiece placed(GameTestHelper h,int slot) { var p=piece(h,slot,0); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),87); return p; }
    private static void verify(GameTestHelper h,ServerLevel l,NetherGhastPiece p) {
        int count=0;
        for(var block:List.of(Blocks.NETHERRACK,Blocks.AIR,Blocks.GLOWSTONE,metal(),Blocks.IRON_BARS,Blocks.SKELETON_SKULL,Blocks.SOUL_SAND,Blocks.SPAWNER,Blocks.CHEST)) for(var c:cells(p,block)) {
            count++; var expected=c.state();
            if(block==Blocks.IRON_BARS) expected=Block.updateFromNeighbourShapes(expected,l,c.pos());
            if(block==Blocks.SKELETON_SKULL) expected=Blocks.SKELETON_SKULL.defaultBlockState();
            h.assertValueEqual(l.getBlockState(c.pos()),expected,"Exact original ghast cage cell at "+c.pos());
        }
        h.assertValueEqual(count,738,"All source cells, including 473 air cells");
        var spawner=(SpawnerBlockEntity)l.getBlockEntity(pos(p,Blocks.SPAWNER)); h.assertTrue(spawner!=null,"Native vanilla spawner tile"); defaults(h,l,spawner);
        h.assertValueEqual(((ChestBlockEntity)l.getBlockEntity(pos(p,Blocks.CHEST))).getLootTable(),LOOT,"Original unopened factory loot table");
    }
    private static void defaults(GameTestHelper h,ServerLevel l,SpawnerBlockEntity b) {
        var tag=b.saveWithFullMetadata(l.registryAccess());
        h.assertValueEqual(tag.getCompoundOrEmpty("SpawnData").getCompoundOrEmpty("entity").getStringOr("id",""),"techguns:ghastling","Original species in modern SpawnData codec");
        String[] keys={"Delay","MinSpawnDelay","MaxSpawnDelay","SpawnCount","MaxNearbyEntities","RequiredPlayerRange","SpawnRange"}; int[] values={20,200,800,4,6,16,4};
        for(int i=0;i<keys.length;i++) h.assertValueEqual(tag.getIntOr(keys[i],-1),values[i],"Vanilla default "+keys[i]);
        h.assertTrue(!tag.contains("mobsLeft") && !tag.contains("maxActive"),"No finite Techguns quota added to the source vanilla spawner");
        h.assertTrue(b.getSpawner().getOrCreateDisplayEntity(l,b.getBlockPos()) instanceof Ghastling,"Native display entity also decodes the species");
    }
    private static BlockEntity reload(ServerLevel l,BlockEntity b) { var tag=b.saveWithFullMetadata(l.registryAccess()); var pos=b.getBlockPos(); var state=b.getBlockState(); l.removeBlockEntity(pos); var restored=BlockEntity.loadStatic(pos,state,tag,l.registryAccess()); l.setBlockEntity(restored); return restored; }
    private static void rotation(GameTestHelper h,int turn) {
        var p=piece(h,turn,turn); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox(),turn+1); verify(h,h.getLevel(),p);
        h.assertValueEqual(p.template().getSize(),new Vec3i(10,14,10),"Source bounds"); var b=p.getBoundingBox();
        h.assertValueEqual(b.minX(),p.templatePosition().getX()+(turn>=2?1:0),"Even-width pivot X");
        h.assertValueEqual(b.minZ(),p.templatePosition().getZ()+(turn==1 || turn==2?1:0),"Even-width pivot Z"); h.succeed();
    }
    private static void clipping(GameTestHelper h,int turn) {
        var p=piece(h,4+turn,turn); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),1); var expected=snapshot(h.getLevel(),p); fill(h,p,Blocks.AIR);
        var b=p.getBoundingBox(); var clips=new ArrayList<BoundingBox>();
        for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) clips.add(new BoundingBox(x*16,b.minY(),z*16,x*16+15,b.maxY(),z*16+15));
        h.assertTrue(clips.size()>1,"Fixture really spans native chunk boundaries"); Collections.reverse(clips); var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel());
        for(var clip:clips) { place(h,p,clip,2); p=(NetherGhastPiece)LocationContent.GHAST_PIECE.get().load(ctx,p.createTag(ctx)); }
        h.assertValueEqual(snapshot(h.getLevel(),p),expected,"Reverse chunk order and piece reload preserve all blocks/shapes"); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,8,1); fill(h,p,Blocks.AIR); var bottoms=cells(p,Blocks.NETHERRACK).stream().filter(c->c.pos().getY()==p.templatePosition().getY()).toList();
        h.assertValueEqual(bottoms.size(),13,"Thirteen original irregular supports"); var stop=bottoms.getFirst().pos();
        for(int depth:List.of(2,4,5)) h.getLevel().setBlock(stop.below(depth),Blocks.OBSIDIAN.defaultBlockState(),2);
        for(var c:bottoms) h.getLevel().setBlock(c.pos().below(17),Blocks.AIR.defaultBlockState(),2); place(h,p,p.getBoundingBox(),1);
        for(var c:bottoms) for(int d=1;d<=17;d++) {
            var expected=d==17?Blocks.AIR:Blocks.NETHERRACK;
            if(c.pos().equals(stop)) expected=List.of(2,4,5).contains(d)?Blocks.OBSIDIAN:d>=6?Blocks.AIR:Blocks.NETHERRACK;
            h.assertTrue(h.getLevel().getBlockState(c.pos().below(d)).is(expected),"Original depth and two-solid stop");
        }
        var columns=new HashSet<BlockPos>(); bottoms.forEach(c->columns.add(c.pos())); var b=p.getBoundingBox();
        for(int x=b.minX();x<=b.maxX();x++) for(int z=b.minZ();z<=b.maxZ();z++) { var pos=new BlockPos(x,p.templatePosition().getY(),z); if(!columns.contains(pos)) h.assertTrue(h.getLevel().getBlockState(pos.below()).isAir(),"No invented foundation outside scan"); }
        h.succeed();
    }
    private static void spawnerSave(GameTestHelper h) {
        var l=h.getLevel(); var p=placed(h,9); var b=(SpawnerBlockEntity)l.getBlockEntity(pos(p,Blocks.SPAWNER)); defaults(h,l,b);
        var tag=b.saveWithFullMetadata(l.registryAccess()); tag.putShort("Delay",(short)317); b.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,l.registryAccess(),tag));
        b=(SpawnerBlockEntity)reload(l,b); var saved=b.saveWithFullMetadata(l.registryAccess()); h.assertValueEqual(saved.getIntOr("Delay",-1),317,"Actual remaining timer persists");
        var potentials=saved.getListOrEmpty("SpawnPotentials"); h.assertValueEqual(potentials.size(),1,"Single-species native potential survives");
        h.assertValueEqual(potentials.getCompoundOrEmpty(0).getCompoundOrEmpty("data").getCompoundOrEmpty("entity").getStringOr("id",""),"techguns:ghastling","Future cycles retain species"); h.succeed();
    }
    private static void spawning(GameTestHelper h) {
        // A Nether fixture preserves Monster/PathfinderMob light checks; open Overworld daylight rejects this species.
        var l=h.getLevel().getServer().getLevel(Level.NETHER); var p=piece(h,10,0); load(l,p);
        for(var cell:snapshot(l,p).keySet()) l.setBlock(cell,Blocks.AIR.defaultBlockState(),2);
        p.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(87),p.getBoundingBox(),new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition());
        var pos=pos(p,Blocks.SPAWNER); var b=(SpawnerBlockEntity)l.getBlockEntity(pos);
        var spawned=new ArrayList<Ghastling>(); Consumer<EntityJoinLevelEvent> observe=e->{ if(e.getLevel()==l && e.getEntity() instanceof Ghastling g && g.distanceToSqr(Vec3.atCenterOf(pos))<100) { g.removeFreeWill(); spawned.add(g); } };
        var cookie=CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"tg-cage-test"),false);
        var player=new ServerPlayer(l.getServer(),l,cookie.gameProfile(),cookie.clientInformation()) {
            @Override public GameType gameMode() { return GameType.SURVIVAL; }
            @Override public boolean isClientAuthoritative() { return false; }
        };
        var connection=new Connection(PacketFlow.SERVERBOUND); var channel=new EmbeddedChannel(connection); player.connection=new ServerGamePacketListenerImpl(l.getServer(),connection,player,cookie);
        for(int x=(pos.getX()-32)>>4;x<=(pos.getX()+32)>>4;x++) for(int z=(pos.getZ()-32)>>4;z<=(pos.getZ()+32)>>4;z++) l.getChunk(x,z);
        player.snapTo(Vec3.atCenterOf(pos.east(17))); l.addNewPlayer(player); NeoForge.EVENT_BUS.addListener(observe);
        try {
            for(int i=0;i<40;i++) SpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
            h.assertValueEqual(b.saveWithFullMetadata(l.registryAccess()).getIntOr("Delay",-1),20,"Player outside sixteen blocks does not advance delay");
            player.snapTo(Vec3.atCenterOf(pos.east(8))); l.getRandom().setSeed(73826);
            h.assertTrue(l.players().contains(player),"Mock native player is tracked by level");
            h.assertTrue(l.hasNearbyAlivePlayer(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,16),"Native activation sees nearby player");
            h.assertTrue(l.isSpawnerBlockEnabled(),"Native spawner gamerule enabled");
            h.assertTrue(l.getDifficulty()!=net.minecraft.world.Difficulty.PEACEFUL,"Hostile fixture difficulty");
            for(int i=0;i<20;i++) SpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
            h.assertTrue(spawned.isEmpty(),"Original initial delay of twenty ticks");
            for(int i=0;i<2500 && spawned.size()<6;i++) SpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
            h.assertValueEqual(spawned.size(),6,"Native nearby cap stops at six Ghastlings");
            for(int i=0;i<1000;i++) SpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
            h.assertValueEqual(spawned.size(),6,"Full nearby population suppresses later attempts");
            for(var g:List.copyOf(spawned)) { h.assertTrue(g.spawnerLink()==null && !g.hasSpawnerOrigin(),"Vanilla spawn has no finite owner link"); g.hurtServer(l,l.damageSources().genericKill(),10000); h.assertTrue(!g.isAlive(),"Real source mob death"); g.discard(); }
            int deaths=spawned.size(); for(int i=0;i<1000 && spawned.size()==deaths;i++) SpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
            h.assertTrue(spawned.size()>deaths,"Vanilla cage resumes after more than three deaths");
            h.assertTrue(l.getBlockState(pos).is(Blocks.SPAWNER),"No finite death budget destroys vanilla cage");
            int delay=b.saveWithFullMetadata(l.registryAccess()).getIntOr("Delay",-1); h.assertTrue(delay>=200 && delay<800,"Original vanilla retry interval");
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); spawned.forEach(Entity::discard); l.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED); channel.finishAndReleaseAll(); }
    }
    private static Map<Item,Integer> items(net.minecraft.world.Container c) { var out=new HashMap<Item,Integer>(); for(int i=0;i<c.getContainerSize();i++) { var s=c.getItem(i); if(!s.isEmpty()) out.merge(s.getItem(),s.getCount(),Integer::sum); } return out; }
    private static void chest(GameTestHelper h) {
        var l=h.getLevel(); var p=placed(h,11); var b=(ChestBlockEntity)l.getBlockEntity(pos(p,Blocks.CHEST)); var seed=b.getLootTableSeed(); h.assertTrue(seed!=0,"Native placement assigns deferred loot seed");
        var params=new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(b.getBlockPos())).create(LootContextParamSets.CHEST);
        var expected=new HashMap<Item,Integer>(); l.getServer().reloadableRegistries().getLootTable(LOOT).getRandomItems(params,seed).forEach(s->expected.merge(s.getItem(),s.getCount(),Integer::sum));
        b=(ChestBlockEntity)reload(l,b); h.assertValueEqual(b.getLootTable(),LOOT,"Unopened chest retains source table"); h.assertValueEqual(b.getLootTableSeed(),seed,"Seed persists");
        h.assertTrue(b.createMenu(1,WeaponGameTests.player(h).getInventory(),WeaponGameTests.player(h))!=null,"Native chest menu opens");
        h.assertValueEqual(items(b),expected,"Original loot rolled once on real open"); h.assertTrue(!expected.isEmpty(),"Source pool produces a reward");
        b=(ChestBlockEntity)reload(l,b); h.assertValueEqual(items(b),expected,"Opened inventory persists"); for(int i=0;i<b.getContainerSize();i++) b.removeItemNoUpdate(i);
        b=(ChestBlockEntity)reload(l,b); h.assertTrue(b.isEmpty() && b.getLootTable()==null,"Consumed chest cannot reroll after reload"); h.succeed();
    }
    private static NetherGhastStructure structure(ServerLevel l) { return (NetherGhastStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherGhastPiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c,long seed) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),seed,c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var restored=(NetherGhastStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow(); h.assertValueEqual(restored.bigGrid(),64,"Reserved big grid in native codec");
        var placement=(RandomSpreadStructurePlacement)l.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(NetherGhastPiece.TEMPLATE).placement();
        h.assertValueEqual(placement.spacing(),32,"Medium spacing"); h.assertValueEqual(placement.separation(),31,"Zero jitter");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherGhastPiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dim:List.of(Level.OVERWORLD,Level.END)) h.assertTrue(!s.generate(holder,dim,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(32,32),0,l,b->true).isValid(),"Explicit Nether dimension guard");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32),0)).isEmpty(),"Global structure switch"); } finally { LocationConfig.ENABLED.set(enabled); }
        for(int n=-5;n<=5;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(64,64*n),0)).isEmpty(),"Big sites reserved"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel().getServer().getLevel(Level.NETHER); var s=structure(l); ChunkPos chosen=null;
        for(int n=513;n<=8192;n++) { var c=new ChunkPos(sign*32,sign*32*n); if(s.findGenerationPoint(context(l,c,l.getSeed())).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Rare source cage ticket finds a native cave"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s);
        h.assertTrue(start!=null && start.isValid(),"Native cage start persisted"); var p=(NetherGhastPiece)start.getPieces().getFirst(); load(l,p); var box=p.getBoundingBox();
        for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++) for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++) l.getChunk(x,z);
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z).postProcessGeneration(l);
        verify(h,l,p);
        for(var id:List.of(NetherCastlePiece.TEMPLATE,NetherMediumAltarPiece.TEMPLATE,NetherAltarPiece.TEMPLATE,NetherLootPiece.TEMPLATE,NetherAcidPiece.TEMPLATE,NetherSoulPiece.TEMPLATE,NetherClusterPiece.TEMPLATE)) {
            var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"Other medium/small candidates cannot replace cage ticket");
        }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherGhastPiece.TEMPLATE));
        var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(5,4,5),0,false);
        h.assertTrue(found!=null,"Native locate finds cage"); h.assertValueEqual(new ChunkPos(found.getFirst().getX()>>4,found.getFirst().getZ()>>4),chosen,"Locate returns saved start chunk");
        var chest=(ChestBlockEntity)l.getBlockEntity(pos(p,Blocks.CHEST)); var saved=chest.saveWithFullMetadata(l.registryAccess()); var before=snapshot(l,p); l.getChunk(chosen.x(),chosen.z());
        h.assertValueEqual(snapshot(l,p),before,"Already generated structure stays unchanged"); h.assertValueEqual(chest.saveWithFullMetadata(l.registryAccess()),saved,"Chest is not reset on repeat chunk request");
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural NetherGhastSpawner: chunk={}, origin={}, rotation={}, chest={}, spawner={}",chosen,p.templatePosition(),p.getRotation(),pos(p,Blocks.CHEST),pos(p,Blocks.SPAWNER)); h.succeed();
    }
    private static void table(GameTestHelper h) {
        var l=h.getLevel().getServer().getLevel(Level.NETHER); var registry=l.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var candidates=List.of(registry.getValue(NetherMediumAltarPiece.TEMPLATE),registry.getValue(NetherGhastPiece.TEMPLATE),registry.getValue(NetherCastlePiece.TEMPLATE));
        boolean ores=LocationConfig.ORE_CLUSTERS.get();
        try { for(boolean enabled:List.of(false,true)) { LocationConfig.ORE_CLUSTERS.set(enabled); int[] seen=new int[3];
            for(long seed:new long[]{0,1,42}) for(int n=-256;n<=256;n++) {
                var c=new ChunkPos(32,32*n); int selected=NetherCastleRules.candidate(context(l,c,seed).random().nextInt(NetherCastleRules.total(enabled)),enabled); int placed=0;
                for(int i=0;i<3;i++) if(((MediumNetherStructure)candidates.get(i)).findGenerationPoint(context(l,c,seed)).isPresent()) { seen[i]++; placed++; h.assertValueEqual(i,selected,"Every candidate uses the same original weighted ticket"); }
                h.assertTrue(placed<=1,"No duplicate structures at one medium site");
            }
            h.assertTrue(seen[0]>0 && seen[1]>0,"Both non-ore candidates find caves even when ore structures are off"); h.assertTrue(enabled?seen[2]>0:seen[2]==0,"Castle follows only ore toggle");
        } } finally { LocationConfig.ORE_CLUSTERS.set(ores); } h.succeed();
    }
    private NetherGhastGameTests() {}
}
