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
import techguns.modern.npc.*;
import techguns.modern.network.*;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.*;
import techguns.modern.world.structure.*;

final class GasStationGameTests {
    private static final ResourceKey<LootTable> LOOT=ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/gasstation"));
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int t=0;t<4;t++) { int turn=t; r.register("location_gas_rotation_"+t,()->h->rotation(h,turn)); r.register("location_gas_reverse_chunk_save_"+t,()->h->clipping(h,turn)); }
        r.register("location_gas_clearing_and_foundation",()->GasStationGameTests::foundation);
        r.register("location_gas_three_finite_zombies",()->GasStationGameTests::guards);
        r.register("location_gas_three_double_chests_save_and_fuel",()->GasStationGameTests::chests);
        r.register("location_gas_all_fifteen_loot_entries",()->GasStationGameTests::loot);
        r.register("location_gas_rotated_facilities",()->GasStationGameTests::doors);
        r.register("location_gas_registry_dimension_toggles",()->GasStationGameTests::registry);
        r.register("location_gas_fuel_bonus_luck",()->GasStationGameTests::luck);
        r.register("location_gas_tree_after_generation",()->GasStationGameTests::tree);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_gas_positive_natural_chunks",()->h->natural(h,1)); r.register("structure_gas_negative_natural_chunks",()->h->natural(h,-1));
            r.register("structure_gas_shared_table_natural_chunks",()->GasStationGameTests::selection);
        }
    }
    private static Block concrete() { return BuildingContent.BLOCKS.get("concrete_grey_dark").get(); }
    private static List<Block> palette() { return List.of(concrete(),Blocks.AIR,Blocks.QUARTZ_BLOCK,Blocks.LEVER,Blocks.QUARTZ_STAIRS,FortificationContent.LAMPS.get("lamp_white").get(),Blocks.STONE_BRICKS,Blocks.POLISHED_ANDESITE,Blocks.BRICKS,Blocks.OAK_DOOR,Blocks.GLASS_PANE,Blocks.SMOOTH_STONE_SLAB,Blocks.OAK_SLAB,NpcSpawnerContent.BLOCK.get(),Blocks.OAK_TRAPDOOR,Blocks.OAK_PLANKS,Blocks.CHEST); }
    private static GasStationPiece piece(GameTestHelper h,int slot,int turn) { return new GasStationPiece(h.getLevel().getServer().getStructureManager(),new BlockPos(2400014+slot*64,140,-2400002),turn); }
    private static List<StructureTemplate.StructureBlockInfo> cells(GasStationPiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static void load(ServerLevel l,GasStationPiece p) { var b=p.getBoundingBox(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) l.getChunk(x,z); }
    private static void place(GameTestHelper h,GasStationPiece p,BoundingBox clip,long seed) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(seed),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,GasStationPiece p) { var b=p.getBoundingBox(); var out=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) out.put(pos.immutable(),l.getBlockState(pos)); return out; }
    private static void fill(GameTestHelper h,GasStationPiece p,Block block) { load(h.getLevel(),p); for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,block.defaultBlockState(),2); }
    private static GasStationPiece placed(GameTestHelper h,int slot) { var p=piece(h,slot,0); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),87); return p; }
    private static void verify(GameTestHelper h,ServerLevel l,GasStationPiece p) {
        var positions=new HashSet<BlockPos>();
        for(var block:palette()) for(var c:cells(p,block)) {
            positions.add(c.pos()); var expected=c.state();
            if(block==Blocks.GLASS_PANE) expected=Block.updateFromNeighbourShapes(expected,l,c.pos());
            h.assertValueEqual(l.getBlockState(c.pos()),expected,"Exact gas source cell at "+c.pos());
        }
        h.assertValueEqual(positions.size(),719,"All source cells mapped");
        var box=p.getBoundingBox(); for(int x=box.minX();x<=box.maxX();x++) for(int z=box.minZ();z<=box.maxZ();z++) for(int y=1;y<=7;y++) {
            var pos=new BlockPos(x,p.templatePosition().getY()+y,z); if(!positions.contains(pos)) h.assertTrue(l.getBlockState(pos).isAir(),"Source cleanup also clears unrecorded scan cells");
        }
        var posts=cells(p,NpcSpawnerContent.BLOCK.get()); h.assertValueEqual(posts.size(),1,"One original zombie hole");
        for(var c:posts) {
            var b=(NpcSpawnerBlockEntity)l.getBlockEntity(c.pos()); h.assertTrue(b!=null,"Post tile placed"); h.assertValueEqual(b.remaining(),3,"Three deaths per post"); h.assertValueEqual(b.maximum(),2,"Two active zombies");
            h.assertValueEqual(b.interval(),200,"Source timer"); h.assertValueEqual(b.range(),1d,"Source radius");
            h.assertValueEqual(b.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("zombiesoldier"),1),new NpcSpawnerBlockEntity.Entry(TGContent.id("zombiefarmer"),1),new NpcSpawnerBlockEntity.Entry(TGContent.id("zombieminer"),1)),"Three equally weighted source species");
        }
        h.assertValueEqual(cells(p,Blocks.CHEST).size(),6,"Six source chest tiles"); for(var c:cells(p,Blocks.CHEST)) h.assertValueEqual(((ChestBlockEntity)l.getBlockEntity(c.pos())).getLootTable(),LOOT,"Deferred original gas loot");
    }
    private static void rotation(GameTestHelper h,int turn) {
        var p=piece(h,turn,turn); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox(),turn+1); verify(h,h.getLevel(),p);
        h.assertValueEqual(p.template().getSize(),new Vec3i(9,7,12),"Original rectangular bounds");
        h.assertValueEqual(p.getBoundingBox().maxY(),p.templatePosition().getY()+7,"Clearing extends above the scan");
        for(var block:palette()) for(var c:p.template().filterBlocks(BlockPos.ZERO,new StructurePlaceSettings(),block)) {
            int[] r=StructureRules.rotate(c.pos().getX(),c.pos().getZ(),turn,4,6); var pos=p.templatePosition().offset(r[0],c.pos().getY(),r[1]);
            h.assertTrue(h.getLevel().getBlockState(pos).is(block),"Independent source rotation coordinate "+pos);
        } h.succeed();
    }
    private static void clipping(GameTestHelper h,int turn) {
        var p=piece(h,4+turn,turn); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),1); var expected=snapshot(h.getLevel(),p); fill(h,p,Blocks.AIR); var b=p.getBoundingBox(); var clips=new ArrayList<BoundingBox>();
        for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) clips.add(new BoundingBox(x*16,b.minY(),z*16,x*16+15,b.maxY(),z*16+15));
        h.assertTrue(clips.size()>1,"Fixture crosses native chunk edges"); Collections.reverse(clips); var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel());
        for(var clip:clips) { place(h,p,clip,2); p=(GasStationPiece)LocationContent.GAS_PIECE.get().load(ctx,p.createTag(ctx)); }
        h.assertValueEqual(snapshot(h.getLevel(),p),expected,"Reverse native chunks and saves keep paired doors/chests and connected panes"); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,8,1); fill(h,p,Blocks.AIR); var bottom=cells(p,concrete()).stream().filter(c->c.pos().getY()==p.templatePosition().getY()).toList(); h.assertValueEqual(bottom.size(),108,"Every original floor column");
        var stop=bottom.getFirst().pos(); h.getLevel().setBlock(stop.below(),Blocks.OBSIDIAN.defaultBlockState(),2); h.getLevel().setBlock(stop.below(2),Blocks.OBSIDIAN.defaultBlockState(),2);
        for(var c:bottom) { h.getLevel().setBlock(c.pos().below(4),Blocks.AIR.defaultBlockState(),2); h.getLevel().setBlock(c.pos().above(8),Blocks.OBSIDIAN.defaultBlockState(),2); }
        place(h,p,p.getBoundingBox(),1);
        for(var c:bottom) for(int d=1;d<=4;d++) h.assertTrue(h.getLevel().getBlockState(c.pos().below(d)).is(d==4?Blocks.AIR:c.pos().equals(stop)&&d<3?Blocks.OBSIDIAN:concrete()),"Three-deep replaceable fill continues under two solids");
        for(var c:bottom) h.assertTrue(h.getLevel().getBlockState(c.pos().above(8)).is(Blocks.OBSIDIAN),"Seven-high cleanup does not reach eighth layer"); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void guards(GameTestHelper h) {
        var p=placed(h,9); var l=h.getLevel(); var spawned=new LinkedHashMap<UUID,ArmedNpc>();
        Consumer<EntityJoinLevelEvent> observe=e->{ if(e.getLevel()==l && e.getEntity() instanceof ArmedNpc npc && p.getBoundingBox().isInside(npc.blockPosition())) { npc.removeFreeWill(); spawned.put(npc.getUUID(),npc); } };
        NeoForge.EVENT_BUS.addListener(observe);
        try {
            var pos=cells(p,NpcSpawnerContent.BLOCK.get()).getFirst().pos(); var b=(NpcSpawnerBlockEntity)l.getBlockEntity(pos);
            for(int death=0;death<3;death++) {
                for(int tick=0;tick<600;tick++) NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
                h.assertValueEqual(b.activeCount(),Math.min(2,3-death),"Two alive at most, bounded by remaining deaths");
                var npc=spawned.get(b.activeIds().iterator().next()); h.assertTrue(npc instanceof ZombieSoldier || npc instanceof ZombieFarmer || npc instanceof ZombieMiner,"Only original zombie species");
                h.assertValueEqual(npc.spawnerLink(),b.link(),"Native ownership survives spawning"); h.assertTrue(!npc.getMainHandItem().isEmpty(),"Native weapon or tool equipped");
                npc.hurtServer(l,l.damageSources().genericKill(),10000); h.assertTrue(!npc.isAlive(),"Actual death"); h.assertValueEqual(b.remaining(),2-death,"Only real deaths spend quota");
                NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
            }
            h.assertTrue(l.getBlockState(pos).isAir(),"Third death removes the post"); h.assertValueEqual(spawned.size(),3,"Exactly three real zombies"); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); spawned.values().forEach(Entity::discard); }
    }
    private static ChestBlockEntity reload(ServerLevel l,ChestBlockEntity b) { var tag=b.saveWithFullMetadata(l.registryAccess()); var pos=b.getBlockPos(); var state=b.getBlockState(); l.removeBlockEntity(pos); var restored=(ChestBlockEntity)BlockEntity.loadStatic(pos,state,tag,l.registryAccess()); l.setBlockEntity(restored); return restored; }
    private static Map<Item,Integer> items(net.minecraft.world.Container c) { var out=new HashMap<Item,Integer>(); for(int i=0;i<c.getContainerSize();i++) { var s=c.getItem(i); if(!s.isEmpty()) out.merge(s.getItem(),s.getCount(),Integer::sum); } return out; }
    private static LootParams params(ServerLevel l,BlockPos pos) { return new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST); }
    private static void chests(GameTestHelper h) {
        var l=h.getLevel(); var p=placed(h,10); var table=l.getServer().reloadableRegistries().getLootTable(LOOT); var seeds=new HashSet<Long>(); ItemStack reward=ItemStack.EMPTY;
        for(var c:cells(p,Blocks.CHEST)) {
            var b=(ChestBlockEntity)l.getBlockEntity(c.pos()); h.assertTrue(seeds.add(b.getLootTableSeed()),"Every half has its own reward seed"); long seed;
            for(seed=1;seed<10000;seed++) if(table.getRandomItems(params(l,c.pos()),seed).stream().anyMatch(s->s.is(TGContent.AMMO.get("fueltank").get()))) break;
            h.assertTrue(seed<10000,"Reproducible full fuel reward"); b.setLootTableSeed(seed); b=reload(l,b); h.assertValueEqual(b.getLootTableSeed(),seed,"Unopened seed persists");
            var expected=new HashMap<Item,Integer>(); table.getRandomItems(params(l,c.pos()),seed).forEach(s->expected.merge(s.getItem(),s.getCount(),Integer::sum));
            var player=WeaponGameTests.player(h); h.assertTrue(b.createMenu(1,player.getInventory(),player)!=null,"Native reward opens"); h.assertValueEqual(items(b),expected,"Exact deferred contents");
            if(reward.isEmpty()) for(int slot=0;slot<b.getContainerSize();slot++) if(b.getItem(slot).is(TGContent.AMMO.get("fueltank").get())) { reward=b.removeItem(slot,1); break; }
            var after=items(b); b=reload(l,b); h.assertValueEqual(items(b),after,"Opened inventory persists"); b.clearContent(); b=reload(l,b); h.assertTrue(b.isEmpty() && b.getLootTable()==null,"Empty rewards cannot reroll");
        }
        int pairs=0;
        for(var c:cells(p,Blocks.CHEST)) if(c.state().getValue(ChestBlock.TYPE)==ChestType.LEFT) {
            var container=ChestBlock.getContainer((ChestBlock)Blocks.CHEST,l.getBlockState(c.pos()),l,c.pos(),false);
            h.assertTrue(container!=null && container.getContainerSize()==54,"Stacked native double chest can open"); pairs++;
        }
        h.assertValueEqual(pairs,3,"Three full 54-slot inventories");
        var player=WeaponGameTests.player(h); player.getInventory().clearContent(); var saw=TGContent.GUNS.get("chainsaw").toStack(); saw.set(TGContent.ROUNDS.get(),0);
        player.setItemInHand(InteractionHand.MAIN_HAND,saw); player.getInventory().setItem(1,reward);
        h.assertTrue(ReloadSessions.begin(player),"Actual found tank starts refueling"); for(int tick=0;tick<45;tick++) player.tick();
        h.assertValueEqual(GunItem.rounds(saw),300,"Found tank fuels native chainsaw"); h.assertValueEqual(player.getInventory().countItem(TGContent.AMMO.get("fueltankempty").get()),1,"Spent tank returned once");
        h.assertValueEqual(player.getInventory().countItem(TGContent.AMMO.get("fueltank").get()),0,"Reward consumed");
        h.assertTrue(GunNetwork.handle(player,new GunActionPayload(false)),"Loot powers an actual chainsaw attack"); h.assertValueEqual(GunItem.rounds(saw),299,"Attack spends fuel"); h.succeed();
    }
    private static boolean fuel(ItemStack s) { return s.is(TGContent.AMMO.get("fueltank").get()) || s.is(TGContent.AMMO.get("fueltankempty").get()); }
    private static void loot(GameTestHelper h) {
        var table=h.getLevel().getServer().reloadableRegistries().getLootTable(LOOT); var seen=new HashSet<String>();
        for(long seed=0;seed<1024;seed++) {
            var drops=table.getRandomItems(params(h.getLevel(),BlockPos.ZERO),seed); int fuels=0,resources=0;
            for(var s:drops) { seen.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                if(fuel(s)) { fuels++; h.assertTrue(s.getCount()>=1 && s.getCount()<=2,"One or two tanks per roll"); } else resources++;
            }
            h.assertValueEqual(fuels,2,"Exactly two fuel rolls at zero luck"); h.assertTrue(resources>=1 && resources<=3,"One to three resource rolls");
        }
        var expected=new HashSet<String>(); for(var id:List.of("iron_ingot","redstone","coal","gunpowder","gold_ingot","diamond","ender_pearl")) expected.add("minecraft:"+id);
        for(var id:List.of("heavycloth","mechanicalpartsiron","mechanicalpartsobsidiansteel","plasticsheet","rubberbar","ingotobsidiansteel","fueltank","fueltankempty")) expected.add("techguns:"+id);
        h.assertValueEqual(seen,expected,"All fifteen original entries in loaded loot tables"); h.succeed();
    }
    private static void luck(GameTestHelper h) {
        var l=h.getLevel(); var table=l.getServer().reloadableRegistries().getLootTable(LOOT); boolean bonus=false;
        var lucky=new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).withLuck(2).create(LootContextParamSets.CHEST);
        for(long seed=1;seed<=128;seed++) { long count=table.getRandomItems(lucky,seed).stream().filter(GasStationGameTests::fuel).count();
            h.assertTrue(count>=2 && count<=6,"Original 0..2 bonus provider multiplied by luck"); bonus|=count>2;
        }
        h.assertTrue(bonus,"Native luck produces extra fuel rolls"); h.succeed();
    }
    private static void doors(GameTestHelper h) {
        for(int turn=0;turn<4;turn++) {
            var p=piece(h,12+turn,turn); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),2); var l=h.getLevel(); var player=WeaponGameTests.player(h);
            for(var c:cells(p,Blocks.OAK_DOOR)) if(c.state().getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER) {
                var pos=c.pos(); l.getBlockState(pos).useWithoutItem(l,player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
                h.assertTrue(l.getBlockState(pos).getValue(DoorBlock.OPEN) && l.getBlockState(pos.above()).getValue(DoorBlock.OPEN),"Both rotated oak-door halves open");
                l.getBlockState(pos.above()).useWithoutItem(l,player,new BlockHitResult(Vec3.atCenterOf(pos.above()),Direction.UP,pos.above(),false));
                h.assertTrue(!l.getBlockState(pos).getValue(DoorBlock.OPEN) && !l.getBlockState(pos.above()).getValue(DoorBlock.OPEN),"Upper half closes original door");
            }
            for(var c:cells(p,Blocks.OAK_TRAPDOOR)) {
                var pos=c.pos(); boolean open=l.getBlockState(pos).getValue(TrapDoorBlock.OPEN);
                l.getBlockState(pos).useWithoutItem(l,player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
                h.assertValueEqual(l.getBlockState(pos).getValue(TrapDoorBlock.OPEN),!open,"Rotated trapdoor toggles");
                l.getBlockState(pos).useWithoutItem(l,player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
            }
            var lever=cells(p,Blocks.LEVER).getFirst().pos(); h.assertTrue(l.getBlockState(lever).canSurvive(l,lever),"Legacy south-facing lever has quartz support");
            l.getBlockState(lever).useWithoutItem(l,player,new BlockHitResult(Vec3.atCenterOf(lever),Direction.UP,lever,false));
            h.assertTrue(l.getBlockState(lever).getValue(LeverBlock.POWERED),"Native lever sends redstone power");
            for(var c:cells(p,FortificationContent.LAMPS.get("lamp_white").get())) h.assertTrue(l.getBlockState(c.pos()).canSurvive(l,c.pos()),"All rotated lamps retain original supports");
        } h.succeed();
    }
    private static GasStationStructure structure(ServerLevel l) { return (GasStationStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(GasStationPiece.TEMPLATE); }
    private static void tree(GameTestHelper h) {
        var l=h.getLevel(); var p=placed(h,16); var pos=p.templatePosition().offset(4,1,3);
        for(var air:BlockPos.betweenClosed(pos.offset(-2,0,-2),pos.offset(2,6,2))) l.setBlock(air,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        l.setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        var config=new net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration.TreeConfigurationBuilder(
                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(Blocks.OAK_LOG),
                new net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer(3,0,0),
                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(Blocks.OAK_LEAVES),
                new net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer(net.minecraft.util.valueproviders.ConstantInt.of(1),net.minecraft.util.valueproviders.ConstantInt.of(0),2),
                new net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize(1,0,1),
                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(Blocks.DIRT)).build();
        h.assertTrue(net.minecraft.world.level.levelgen.feature.Feature.TREE.place(config,l,l.getChunkSource().getGenerator(),RandomSource.create(1),pos),"Native tree can grow after world generation inside a cleared station");
        h.assertTrue(l.getBlockState(pos).is(Blocks.OAK_LOG),"Real trunk placed"); h.succeed();
    }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c,long seed) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),seed,c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE); var decoded=(GasStationStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow(); h.assertTrue(!Structure.DIRECT_CODEC.encodeStart(ops,decoded).getOrThrow().getAsJsonObject().has("reserved_big_grid"),"Grid reservation belongs to the shared placement"); h.assertTrue(!Structure.DIRECT_CODEC.encodeStart(ops,decoded).getOrThrow().getAsJsonObject().has("reserved_medium_grid"),"Grid reservation belongs to the shared placement");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,GasStationPiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dimension:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dimension,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(32,32),0,l,b->true).isValid(),"Overworld-only guard");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32),0)).isEmpty(),"Global structure switch"); } finally { LocationConfig.ENABLED.set(enabled); }
        for(int n=-5;n<=5;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32*n),0)).isEmpty(),"Medium and big grids remain reserved"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel(); var s=structure(l); ChunkPos chosen=null;
        for(int n=129;n<=2048;n++) { var c=new ChunkPos(sign*80,sign*16*n); if(s.findGenerationPoint(context(l,c,l.getSeed())).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Source gas ticket and surface find native land"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Native gas start saved"); var p=(GasStationPiece)start.getPieces().getFirst(); load(l,p); var box=p.getBoundingBox();
        for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++) for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++) l.getChunk(x,z);
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z).postProcessGeneration(l);
        verify(h,l,p);
        if(sign>0) {
            boolean neighbouringTree=false;
            for(var pos:BlockPos.betweenClosed(box.minX()-8,box.minY(),box.minZ()-8,box.maxX()+8,box.maxY()+8,box.maxZ()+8))
                if(!box.isInside(pos) && l.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)) { neighbouringTree=true; break; }
            h.assertTrue(neighbouringTree,"Original neighbouring trees still generate outside the station");
        }
        for(var id:List.of(PoliceStationPiece.TEMPLATE,SurvivorHideoutPiece.TEMPLATE,DesertOilPiece.TEMPLATE,MeteorPiece.TEMPLATE,OreSpikePiece.TEMPLATE,TGContent.id("alienbug_nest"))) { var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"Medium candidates never overlap gas ticket"); }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,GasStationPiece.TEMPLATE)); var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(4,3,6),0,false);
        h.assertTrue(found!=null,"Native locate finds actual gas station"); h.assertValueEqual(new ChunkPos(found.getFirst().getX()>>4,found.getFirst().getZ()>>4),chosen,"Locate start chunk");
        var before=snapshot(l,p); var saved=cells(p,Blocks.CHEST).stream().map(c->l.getBlockEntity(c.pos()).saveWithFullMetadata(l.registryAccess())).toList(); l.getChunk(chosen.x(),chosen.z());
        h.assertValueEqual(snapshot(l,p),before,"Repeat chunk request does not regenerate station"); h.assertValueEqual(cells(p,Blocks.CHEST).stream().map(c->l.getBlockEntity(c.pos()).saveWithFullMetadata(l.registryAccess())).toList(),saved,"Original rewards retain seeds");
        h.assertTrue(net.minecraft.world.level.levelgen.feature.TreeFeature.validTreePos(l,cells(p,Blocks.AIR).getFirst().pos()),"Tree growth is allowed after generation even inside a registered station");
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural GasStation: chunk={}, origin={}, rotation={}, guards=1, chestTiles=6",chosen,p.templatePosition(),p.getRotation()); h.succeed();
    }
    private static void selection(GameTestHelper h) {
        var l=h.getLevel(); var gas=structure(l); boolean ores=LocationConfig.ORE_CLUSTERS.get(); int hits=0,otherTickets=0;
        try {
            for(long seed:new long[]{0,1,42}) for(int n=-64;n<=64;n++) {
                var c=new ChunkPos(80,16*n); var probe=context(l,c,seed); int roll=probe.random().nextInt(40);
                LocationConfig.ORE_CLUSTERS.set(true); boolean withOres=gas.findGenerationPoint(context(l,c,seed)).isPresent();
                LocationConfig.ORE_CLUSTERS.set(false); boolean withoutOres=gas.findGenerationPoint(context(l,c,seed)).isPresent();
                h.assertValueEqual(withOres,withoutOres,"Small LAND selection is independent of the ore switch");
                if(roll<30) { h.assertTrue(!withOres,"Other thirty source tickets are never reassigned"); otherTickets++; }
                if(withOres) hits++;
            }
        } finally { LocationConfig.ORE_CLUSTERS.set(ores); }
        h.assertTrue(hits>0 && otherTickets>0,"Three seeds exercise successful gas stations and reserved candidates"); h.succeed();
    }
    private GasStationGameTests() {}
}
