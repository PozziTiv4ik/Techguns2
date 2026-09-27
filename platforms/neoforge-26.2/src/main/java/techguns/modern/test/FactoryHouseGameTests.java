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
import techguns.modern.npc.spawner.*;
import techguns.modern.world.*;
import techguns.modern.world.structure.*;

final class FactoryHouseGameTests {
    private static final ResourceKey<LootTable> LOOT=ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/factory_building"));
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int t=0;t<4;t++) { int turn=t; r.register("location_factory_rotation_"+t,()->h->rotation(h,turn)); r.register("location_factory_reverse_chunk_save_"+t,()->h->clipping(h,turn)); }
        r.register("location_factory_two_colour_foundation",()->FactoryHouseGameTests::foundation);
        r.register("location_factory_five_finite_miners",()->FactoryHouseGameTests::guards);
        r.register("location_factory_double_chest_and_two_saved_furnaces",()->FactoryHouseGameTests::chests);
        r.register("location_factory_all_thirteen_loot_entries",()->FactoryHouseGameTests::loot);
        r.register("location_factory_rotated_facilities",()->FactoryHouseGameTests::facilities);
        r.register("location_factory_registry_dimension_toggles",()->FactoryHouseGameTests::registry);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_factory_positive_natural_chunks",()->h->natural(h,1)); r.register("structure_factory_negative_natural_chunks",()->h->natural(h,-1));
            r.register("structure_factory_shared_table_natural_chunks",()->FactoryHouseGameTests::selection);
        }
    }
    private static Block grey() { return BuildingContent.BLOCKS.get("concrete_grey").get(); }
    private static Block brown() { return BuildingContent.BLOCKS.get("concrete_brown_light").get(); }
    private static Block lamp() { return FortificationContent.LAMPS.get("lamp_yellow").get(); }
    private static Block ladder() { return BuildingContent.BLOCKS.get("ladder_metal").get(); }
    private static List<Block> palette() { return List.of(grey(),brown(),BuildingContent.BLOCKS.get("metalpanel_steelframe_scaffold").get(),Blocks.BRICKS,Blocks.GLASS_PANE,BuildingContent.BLOCKS.get("metalpanel_container_red").get(),Blocks.CHEST,Blocks.AIR,lamp(),Blocks.TERRACOTTA,Blocks.CRAFTING_TABLE,FortificationContent.DOOR.get(),Blocks.FURNACE,ladder(),Blocks.IRON_BARS,NpcSpawnerContent.BLOCK.get()); }
    private static FactoryHousePiece piece(GameTestHelper h,int slot,int turn) { return new FactoryHousePiece(h.getLevel().getServer().getStructureManager(),new BlockPos(2800014+slot*64,140,-2800002),turn); }
    private static List<StructureTemplate.StructureBlockInfo> cells(FactoryHousePiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static void load(ServerLevel l,FactoryHousePiece p) { var b=p.getBoundingBox(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) l.getChunk(x,z); }
    private static void place(GameTestHelper h,FactoryHousePiece p,BoundingBox clip,long seed) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(seed),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,FactoryHousePiece p) { var b=p.getBoundingBox(); var out=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) out.put(pos.immutable(),l.getBlockState(pos)); return out; }
    private static void fill(GameTestHelper h,FactoryHousePiece p,Block block) { load(h.getLevel(),p); for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,block.defaultBlockState(),2); }
    private static FactoryHousePiece placed(GameTestHelper h,int slot) { var p=piece(h,slot,0); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),87); return p; }
    private static List<StructureTemplate.StructureBlockInfo> floors(FactoryHousePiece p) {
        var out=new ArrayList<StructureTemplate.StructureBlockInfo>(); for(var block:List.of(grey(),brown())) for(var c:cells(p,block)) if(c.pos().getY()==p.templatePosition().getY()) out.add(c); return out;
    }
    private static void verify(GameTestHelper h,ServerLevel l,FactoryHousePiece p) {
        var positions=new HashSet<BlockPos>();
        for(var block:palette()) for(var c:cells(p,block)) { positions.add(c.pos()); var expected=c.state();
            if(block==Blocks.GLASS_PANE || block==Blocks.IRON_BARS) expected=Block.updateFromNeighbourShapes(expected,l,c.pos());
            h.assertValueEqual(l.getBlockState(c.pos()),expected,"Exact factory cell at "+c.pos()); }
        h.assertValueEqual(positions.size(),462,"463 original records resolve to 462 positions");
        for(var floor:floors(p)) for(int y=1;y<=7;y++) if(!positions.contains(floor.pos().above(y))) h.assertTrue(l.getBlockState(floor.pos().above(y)).isAir(),"Seven source clearing layers");
        var posts=cells(p,NpcSpawnerContent.BLOCK.get()); h.assertValueEqual(posts.size(),1,"Final post overwrites the original AIR cell");
        var b=(NpcSpawnerBlockEntity)l.getBlockEntity(posts.getFirst().pos()); h.assertTrue(b!=null,"Real factory post tile");
        h.assertValueEqual(b.remaining(),5,"Five deaths"); h.assertValueEqual(b.maximum(),2,"Two live miners"); h.assertValueEqual(b.interval(),150,"Original interval"); h.assertValueEqual(b.delay(),150,"setParams also sets the first delay"); h.assertValueEqual(b.range(),2d,"Original radius");
        h.assertValueEqual(b.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("zombieminer"),1)),"Only source miner species");
        h.assertValueEqual(cells(p,Blocks.CHEST).size(),2,"Two original chest halves"); for(var c:cells(p,Blocks.CHEST)) h.assertValueEqual(((ChestBlockEntity)l.getBlockEntity(c.pos())).getLootTable(),LOOT,"Shared original factory loot");
    }
    private static void rotation(GameTestHelper h,int turn) {
        var p=piece(h,turn,turn); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox(),turn+1); verify(h,h.getLevel(),p);
        h.assertValueEqual(p.template().getSize(),new Vec3i(9,7,11),"Actual scan differs from registered 11 by 10 size"); h.assertValueEqual(p.getBoundingBox().maxY(),p.templatePosition().getY()+7,"Clearing above the scan is included");
        for(var block:palette()) for(var c:p.template().filterBlocks(BlockPos.ZERO,new StructurePlaceSettings(),block)) {
            int[] r=StructureRules.rotate(c.pos().getX(),c.pos().getZ(),turn,5,5); var pos=p.templatePosition().offset(r[0],c.pos().getY(),r[1]); h.assertTrue(h.getLevel().getBlockState(pos).is(block),"Independent original pivot coordinate "+pos);
        } h.succeed();
    }
    private static void clipping(GameTestHelper h,int turn) {
        var p=piece(h,4+turn,turn); var pair=cells(p,Blocks.CHEST); var a=pair.getFirst().pos(); var other=pair.getLast().pos(); boolean alongX=a.getX()!=other.getX();
        int lowest=alongX?Math.min(a.getX(),other.getX()):Math.min(a.getZ(),other.getZ()),shift=15-Math.floorMod(lowest,16);
        p=new FactoryHousePiece(h.getLevel().getServer().getStructureManager(),p.templatePosition().offset(alongX?shift:0,0,alongX?0:shift),turn);
        pair=cells(p,Blocks.CHEST); a=pair.getFirst().pos(); other=pair.getLast().pos();
        h.assertTrue(!new ChunkPos(a.getX()>>4,a.getZ()>>4).equals(new ChunkPos(other.getX()>>4,other.getZ()>>4)),"Chest halves straddle a real chunk edge in every rotation");
        fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),1); var expected=snapshot(h.getLevel(),p); fill(h,p,Blocks.AIR); var b=p.getBoundingBox(); var clips=new ArrayList<BoundingBox>();
        for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) clips.add(new BoundingBox(x*16,b.minY(),z*16,x*16+15,b.maxY(),z*16+15));
        h.assertTrue(clips.size()>1,"Fixture crosses native chunk edges"); Collections.reverse(clips); var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel());
        for(var clip:clips) { place(h,p,clip,2+31L*clip.minX()+clip.minZ()); p=(FactoryHousePiece)LocationContent.FACTORY_PIECE.get().load(ctx,p.createTag(ctx)); }
        var seeds=new HashSet<Long>(); for(var c:cells(p,Blocks.CHEST)) h.assertTrue(seeds.add(((ChestBlockEntity)h.getLevel().getBlockEntity(c.pos())).getLootTableSeed()),"Each chunk supplies its own deferred reward seed");
        h.assertValueEqual(snapshot(h.getLevel(),p),expected,"Reverse native chunks and saves keep paired doors/chests and connected panes"); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,8,1); fill(h,p,Blocks.AIR); var l=h.getLevel(); var bottom=floors(p); h.assertValueEqual(bottom.size(),93,"Sparse source floor");
        h.assertValueEqual(bottom.stream().filter(c->c.state().is(grey())).count(),58L,"Grey foundation columns"); h.assertValueEqual(bottom.stream().filter(c->c.state().is(brown())).count(),35L,"Light brown foundation columns");
        var stop=bottom.getFirst().pos(); l.setBlock(stop.below(),Blocks.OBSIDIAN.defaultBlockState(),2); l.setBlock(stop.below(2),Blocks.OBSIDIAN.defaultBlockState(),2);
        for(var c:bottom) { l.setBlock(c.pos().below(4),Blocks.AIR.defaultBlockState(),2); l.setBlock(c.pos().above(8),Blocks.OBSIDIAN.defaultBlockState(),2); }
        var ground=new HashSet<BlockPos>(); bottom.forEach(c->ground.add(c.pos())); var gaps=new ArrayList<BlockPos>(); var box=p.getBoundingBox();
        for(var pos:BlockPos.betweenClosed(box.minX(),p.templatePosition().getY(),box.minZ(),box.maxX(),p.templatePosition().getY(),box.maxZ())) if(!ground.contains(pos)) { gaps.add(pos.immutable()); l.setBlock(pos,Blocks.OBSIDIAN.defaultBlockState(),2); l.setBlock(pos.below(),Blocks.OBSIDIAN.defaultBlockState(),2); }
        place(h,p,box,1);
        for(var c:bottom) { for(int d=1;d<=3;d++) h.assertValueEqual(l.getBlockState(c.pos().below(d)),c.pos().equals(stop)&&d<3?Blocks.OBSIDIAN.defaultBlockState():c.state(),"Original colour fills each replaceable layer even below two solids");
            h.assertTrue(l.getBlockState(c.pos().below(4)).isAir(),"No fourth foundation layer"); h.assertTrue(l.getBlockState(c.pos().above(8)).is(Blocks.OBSIDIAN),"Clearing stops below eighth layer"); }
        h.assertValueEqual(gaps.size(),6,"Six source ground omissions"); for(var pos:gaps) h.assertTrue(l.getBlockState(pos).is(Blocks.OBSIDIAN) && l.getBlockState(pos.below()).is(Blocks.OBSIDIAN),"No fill in omitted columns");
        verify(h,l,p); h.succeed();
    }
    private static void guards(GameTestHelper h) {
        var p=placed(h,9); var l=h.getLevel(); var spawned=new LinkedHashMap<UUID,ZombieMiner>();
        Consumer<EntityJoinLevelEvent> observe=e->{ if(e.getLevel()==l && e.getEntity() instanceof ZombieMiner npc && p.getBoundingBox().isInside(npc.blockPosition())) { npc.removeFreeWill(); spawned.put(npc.getUUID(),npc); } };
        NeoForge.EVENT_BUS.addListener(observe);
        try {
            var pos=cells(p,NpcSpawnerContent.BLOCK.get()).getFirst().pos(); var b=(NpcSpawnerBlockEntity)l.getBlockEntity(pos);
            for(int tick=0;tick<149;tick++) NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
            h.assertValueEqual(b.delay(),1,"Initial source countdown lasts 150 ticks"); h.assertValueEqual(b.activeCount(),0,"No spawn before initial countdown");
            for(int death=0;death<5;death++) {
                int wanted=Math.min(2,5-death); for(int tick=0;tick<6000 && b.activeCount()<wanted;tick++) NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
                for(int tick=0;tick<450;tick++) NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
                h.assertValueEqual(b.activeCount(),wanted,"Source live limit remains bounded by remaining quota");
                var npc=spawned.get(b.activeIds().iterator().next()); h.assertTrue(npc!=null && npc.isAlive(),"Actual miner spawned"); h.assertValueEqual(npc.spawnerLink(),b.link(),"Native persistent ownership"); h.assertTrue(!npc.getMainHandItem().isEmpty(),"Native equipment assigned");
                npc.hurtServer(l,l.damageSources().genericKill(),10000); h.assertTrue(!npc.isAlive(),"Actual death"); h.assertValueEqual(b.remaining(),4-death,"One real death spends one quota"); NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
            }
            h.assertTrue(l.getBlockState(pos).isAir(),"Fifth death removes the post"); h.assertValueEqual(spawned.size(),5,"Exactly five real miners"); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); spawned.values().forEach(Entity::discard); }
    }
    private static ChestBlockEntity reload(ServerLevel l,ChestBlockEntity b) { var tag=b.saveWithFullMetadata(l.registryAccess()); var pos=b.getBlockPos(); var state=b.getBlockState(); l.removeBlockEntity(pos); var restored=(ChestBlockEntity)BlockEntity.loadStatic(pos,state,tag,l.registryAccess()); l.setBlockEntity(restored); return restored; }
    private static Map<Item,Integer> items(net.minecraft.world.Container c) { var out=new HashMap<Item,Integer>(); for(int i=0;i<c.getContainerSize();i++) { var s=c.getItem(i); if(!s.isEmpty()) out.merge(s.getItem(),s.getCount(),Integer::sum); } return out; }
    private static LootParams params(ServerLevel l,BlockPos pos) { return new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST); }
    private static void chests(GameTestHelper h) {
        var l=h.getLevel(); var p=placed(h,10); var table=l.getServer().reloadableRegistries().getLootTable(LOOT); var seeds=new HashSet<Long>(); var fuels=new ArrayList<ItemStack>(); long next=1;
        for(var c:cells(p,Blocks.CHEST)) {
            var b=(ChestBlockEntity)l.getBlockEntity(c.pos()); h.assertTrue(seeds.add(b.getLootTableSeed()),"Each half starts with an independent reward seed"); long seed;
            for(seed=next;seed<10000;seed++) if(table.getRandomItems(params(l,c.pos()),seed).stream().anyMatch(stack->stack.is(Items.COAL))) break;
            h.assertTrue(seed<10000,"Reproducible original coal reward"); next=seed+1; b.setLootTableSeed(seed); b=reload(l,b); h.assertValueEqual(b.getLootTableSeed(),seed,"Unopened seed survives loading");
            var expected=new HashMap<Item,Integer>(); table.getRandomItems(params(l,c.pos()),seed).forEach(stack->expected.merge(stack.getItem(),stack.getCount(),Integer::sum));
            var player=WeaponGameTests.player(h); h.assertTrue(b.createMenu(1,player.getInventory(),player)!=null,"Native chest half opens"); h.assertValueEqual(items(b),expected,"Exact factory resource pool per half");
            for(int slot=0;slot<b.getContainerSize();slot++) if(b.getItem(slot).is(Items.COAL)) { fuels.add(b.removeItem(slot,1)); break; }
            var after=items(b); b=reload(l,b); h.assertValueEqual(items(b),after,"Opened inventory persists"); b.clearContent(); b=reload(l,b); h.assertTrue(b.isEmpty() && b.getLootTable()==null,"Empty half cannot reroll");
        }
        var chest=cells(p,Blocks.CHEST).getFirst().pos(); var container=ChestBlock.getContainer((ChestBlock)Blocks.CHEST,l.getBlockState(chest),l,chest,false);
        h.assertTrue(container!=null && container.getContainerSize()==54,"Real double chest opens with 54 slots");
        var player=WeaponGameTests.player(h); var provider=l.getBlockState(chest).getMenuProvider(l,chest); h.assertTrue(provider!=null && provider.createMenu(2,player.getInventory(),player)!=null,"Combined native menu opens");
        h.assertValueEqual(fuels.size(),2,"Both halves supply actual fuel"); var ovens=cells(p,Blocks.FURNACE); h.assertValueEqual(ovens.size(),2,"Both source furnaces");
        for(int i=0;i<ovens.size();i++) {
            var pos=ovens.get(i).pos(); var furnace=(FurnaceBlockEntity)l.getBlockEntity(pos); furnace.setItem(0,new ItemStack(i==0?Items.COBBLESTONE:Items.SAND)); furnace.setItem(1,fuels.get(i));
            for(int tick=0;tick<100;tick++) AbstractFurnaceBlockEntity.serverTick(l,pos,l.getBlockState(pos),furnace);
            h.assertTrue(furnace.getItem(2).isEmpty() && furnace.getItem(1).isEmpty(),"Found coal spent before completion");
            var tag=furnace.saveWithFullMetadata(l.registryAccess()); l.removeBlockEntity(pos); furnace=(FurnaceBlockEntity)BlockEntity.loadStatic(pos,l.getBlockState(pos),tag,l.registryAccess()); l.setBlockEntity(furnace);
            for(int tick=0;tick<105;tick++) AbstractFurnaceBlockEntity.serverTick(l,pos,l.getBlockState(pos),furnace);
            h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(2).is(i==0?Items.STONE:Items.GLASS) && furnace.getItem(2).getCount()==1,"Each saved furnace completes exactly one real recipe");
        } h.succeed();
    }
    private static void loot(GameTestHelper h) {
        var table=h.getLevel().getServer().reloadableRegistries().getLootTable(LOOT); var seen=new HashSet<String>();
        for(long seed=0;seed<1024;seed++) { var drops=table.getRandomItems(params(h.getLevel(),BlockPos.ZERO),seed); h.assertTrue(drops.size()>=1 && drops.size()<=3,"Factory table has one to three rolls per half");
            for(var stack:drops) seen.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()); }
        var expected=new HashSet<String>(); for(var id:List.of("iron_ingot","redstone","coal","gunpowder","gold_ingot","diamond","ender_pearl")) expected.add("minecraft:"+id);
        for(var id:List.of("heavycloth","mechanicalpartsiron","mechanicalpartsobsidiansteel","plasticsheet","rubberbar","ingotobsidiansteel")) expected.add("techguns:"+id);
        h.assertValueEqual(seen,expected,"All thirteen original factory rewards"); h.succeed();
    }
    private static void facilities(GameTestHelper h) {
        for(int turn=0;turn<4;turn++) {
            var p=piece(h,12+turn,turn); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),2); var l=h.getLevel(); var player=WeaponGameTests.player(h);
            int doors=0;
            for(var c:cells(p,FortificationContent.DOOR.get())) if(c.state().getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER) {
                var pos=c.pos(); l.getBlockState(pos).useWithoutItem(l,player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
                h.assertTrue(l.getBlockState(pos).getValue(DoorBlock.OPEN) && l.getBlockState(pos.above()).getValue(DoorBlock.OPEN),"Both factory door halves open");
                l.getBlockState(pos.above()).useWithoutItem(l,player,new BlockHitResult(Vec3.atCenterOf(pos.above()),Direction.UP,pos.above(),false));
                h.assertTrue(!l.getBlockState(pos).getValue(DoorBlock.OPEN) && !l.getBlockState(pos.above()).getValue(DoorBlock.OPEN),"Upper half closes the original bunker door"); doors++;
            }
            h.assertValueEqual(doors,2,"Both original doors"); h.assertValueEqual(cells(p,lamp()).size(),11,"Eleven yellow lamps");
            for(var c:cells(p,lamp())) { var state=l.getBlockState(c.pos()); h.assertTrue(state.canSurvive(l,c.pos()),"Rotated source lamp has its support"); h.assertValueEqual(state.getLightEmission(l,c.pos()),15,"Original lamp light emission"); }
            h.assertValueEqual(cells(p,ladder()).size(),8,"Two four-block metal ladders");
            for(var c:cells(p,ladder())) { player.setPos(Vec3.atBottomCenterOf(c.pos()).add(0,.1,0)); h.assertTrue(player.onClimbable(),"Native climbing recognizes each source metal ladder segment"); }
            var table=cells(p,Blocks.CRAFTING_TABLE).getFirst().pos(); var provider=l.getBlockState(table).getMenuProvider(l,table);
            h.assertTrue(provider!=null && provider.createMenu(3,player.getInventory(),player) instanceof net.minecraft.world.inventory.CraftingMenu,"Original workbench exposes a crafting menu");
        } h.succeed();
    }
    private static FactoryHouseStructure structure(ServerLevel l) { return (FactoryHouseStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(FactoryHousePiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c,long seed) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),seed,c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE); var decoded=(FactoryHouseStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow(); h.assertValueEqual(decoded.bigGrid(),64,"Native codec preserves big grid"); h.assertValueEqual(decoded.mediumGrid(),32,"Native codec preserves medium grid");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,FactoryHousePiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dimension:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dimension,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(32,32),0,l,b->true).isValid(),"Overworld-only guard");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32),0)).isEmpty(),"Global structure switch"); } finally { LocationConfig.ENABLED.set(enabled); }
        for(int n=-5;n<=5;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32*n),0)).isEmpty(),"Medium and big grids remain reserved"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel(); var s=structure(l); ChunkPos chosen=null;
        for(int n=129;n<=2048;n++) { var c=new ChunkPos(sign*208,sign*16*n); if(s.findGenerationPoint(context(l,c,l.getSeed())).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Source factory ticket and surface find native land"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Native factory start saved"); var p=(FactoryHousePiece)start.getPieces().getFirst(); load(l,p); var box=p.getBoundingBox();
        for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++) for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++) l.getChunk(x,z);
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z).postProcessGeneration(l);
        verify(h,l,p);
        for(var id:List.of(GasStationPiece.TEMPLATE,TrainStationPiece.TEMPLATE,PoliceStationPiece.TEMPLATE,SurvivorHideoutPiece.TEMPLATE,DesertOilPiece.TEMPLATE,MeteorPiece.TEMPLATE,OreSpikePiece.TEMPLATE,TGContent.id("alienbug_nest"))) { var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"Other small and medium candidates never overlap factory ticket"); }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,FactoryHousePiece.TEMPLATE)); var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(5,3,5),0,false);
        h.assertTrue(found!=null,"Native locate finds the factory house"); h.assertValueEqual(new ChunkPos(found.getFirst().getX()>>4,found.getFirst().getZ()>>4),chosen,"Locate start chunk");
        var before=snapshot(l,p); var saved=cells(p,Blocks.CHEST).stream().map(c->l.getBlockEntity(c.pos()).saveWithFullMetadata(l.registryAccess())).toList(); l.getChunk(chosen.x(),chosen.z());
        h.assertValueEqual(snapshot(l,p),before,"Repeat chunk request does not regenerate station"); h.assertValueEqual(cells(p,Blocks.CHEST).stream().map(c->l.getBlockEntity(c.pos()).saveWithFullMetadata(l.registryAccess())).toList(),saved,"Original rewards retain seeds");
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural FactoryHouse: chunk={}, origin={}, rotation={}, guards=1, chestTiles=2",chosen,p.templatePosition(),p.getRotation()); h.succeed();
    }
    private static void selection(GameTestHelper h) {
        var l=h.getLevel(); var factory=structure(l); var registry=l.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var train=(TrainStationStructure)registry.getValue(TrainStationPiece.TEMPLATE); var gas=(GasStationStructure)registry.getValue(GasStationPiece.TEMPLATE);
        boolean ores=LocationConfig.ORE_CLUSTERS.get(); int factories=0,trains=0,stations=0,mines=0;
        try {
            for(long seed:new long[]{0,1,42}) for(int n=-64;n<=64;n++) {
                var c=new ChunkPos(208,16*n); int roll=context(l,c,seed).random().nextInt(40);
                LocationConfig.ORE_CLUSTERS.set(true); boolean f=factory.findGenerationPoint(context(l,c,seed)).isPresent(),t=train.findGenerationPoint(context(l,c,seed)).isPresent(),g=gas.findGenerationPoint(context(l,c,seed)).isPresent();
                LocationConfig.ORE_CLUSTERS.set(false); h.assertValueEqual(factory.findGenerationPoint(context(l,c,seed)).isPresent(),f,"Factory ignores ore toggle");
                h.assertValueEqual(train.findGenerationPoint(context(l,c,seed)).isPresent(),t,"Train retains selection"); h.assertValueEqual(gas.findGenerationPoint(context(l,c,seed)).isPresent(),g,"Gas retains selection");
                h.assertTrue((f?1:0)+(t?1:0)+(g?1:0)<=1,"Three native IDs never reroll another candidate into the same site");
                if(roll>=20 && roll<30) { h.assertTrue(!f && !t && !g,"The mine retains all ten tickets"); mines++; }
                if(f) factories++; if(t) trains++; if(g) stations++;
            }
        } finally { LocationConfig.ORE_CLUSTERS.set(ores); }
        h.assertTrue(factories>0 && trains>0 && stations>0 && mines>0,"Three seeds exercise all three ports and the remaining candidate"); h.succeed();
    }
    private FactoryHouseGameTests() {}
}
