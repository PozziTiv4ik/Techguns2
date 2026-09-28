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
import net.minecraft.util.Mth;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.item.FallingBlockEntity;
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

final class TrainStationGameTests {
    private static final ResourceKey<LootTable> LOOT=ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/small_trainstation"));
    private static final long[] SEEDS={37L,0x6345678ab0ff0191L};
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int t=0;t<4;t++) for(int n=0;n<SEEDS.length;n++) { int turn=t,layout=n;
            r.register("location_train_rotation_"+t+"_layout_"+n,()->h->rotation(h,turn,layout));
            r.register("location_train_reverse_chunk_save_"+t+"_layout_"+n,()->h->clipping(h,turn,layout));
        }
        r.register("location_train_sparse_foundation",()->TrainStationGameTests::foundation);
        r.register("location_train_three_finite_miners",()->TrainStationGameTests::guards);
        r.register("location_train_saved_chest_fuel_and_furnace",()->TrainStationGameTests::chests);
        r.register("location_train_all_thirteen_loot_entries",()->TrainStationGameTests::loot);
        r.register("location_train_rotated_facilities",()->TrainStationGameTests::facilities);
        r.register("location_train_damage_seed_and_independent_foundation",()->TrainStationGameTests::damage);
        r.register("location_train_native_gravel_and_rail_physics",()->TrainStationGameTests::gravity);
        r.register("location_train_registry_dimension_toggles",()->TrainStationGameTests::registry);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_train_positive_natural_chunks",()->h->natural(h,1)); r.register("structure_train_negative_natural_chunks",()->h->natural(h,-1));
            r.register("structure_train_shared_table_natural_chunks",()->TrainStationGameTests::selection);
        }
    }
    private static List<Block> palette() { return List.of(Blocks.GRAVEL,Blocks.STRUCTURE_BLOCK,Blocks.STONE,Blocks.BRICKS,Blocks.AIR,Blocks.OAK_FENCE,Blocks.OAK_PRESSURE_PLATE,Blocks.OAK_SLAB,Blocks.LADDER,Blocks.CHEST,Blocks.STONE_BRICK_STAIRS,Blocks.FURNACE,Blocks.CRAFTING_TABLE,NpcSpawnerContent.BLOCK.get()); }
    private static TrainStationPiece piece(GameTestHelper h,int slot,int turn,long seed) { return new TrainStationPiece(h.getLevel().getServer().getStructureManager(),new BlockPos(2600014+slot*64,140,-2600002),turn,seed); }
    private static List<StructureTemplate.StructureBlockInfo> cells(TrainStationPiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static void load(ServerLevel l,TrainStationPiece p) { var b=p.getBoundingBox(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) l.getChunk(x,z); }
    private static void place(GameTestHelper h,TrainStationPiece p,BoundingBox clip,long seed) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(seed),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,TrainStationPiece p) { var b=p.getBoundingBox(); var out=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) out.put(pos.immutable(),l.getBlockState(pos)); return out; }
    private static void fill(GameTestHelper h,TrainStationPiece p,Block block) { load(h.getLevel(),p); for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,block.defaultBlockState(),2); }
    private static TrainStationPiece placed(GameTestHelper h,int slot) { var p=piece(h,slot,0,SEEDS[0]); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),87); return p; }
    private static BlockState expected(TrainStationPiece p,StructureTemplate.StructureBlockInfo c,BlockPos pos,ServerLevel l) {
        if(!c.state().is(Blocks.STRUCTURE_BLOCK)) return c.state();
        var weights=c.nbt().getListOrEmpty("Weights"); var states=c.nbt().getListOrEmpty("Variants");
        int total=1; for(int i=0;i<weights.size();i++) total+=weights.getIntOr(i,0);
        int roll=RandomSource.create(p.damageSeed()^Mth.getSeed(pos)).nextInt(total),choice=0;
        // Independent ticket intervals: [0,weight0], then exactly weightN tickets per later variant.
        int left=roll-weights.getIntOr(0,0)-1;
        while(left>=0) { choice++; left-=weights.getIntOr(choice,0); }
        return NbtUtils.readBlockState(l.registryAccess().lookupOrThrow(Registries.BLOCK),states.getCompoundOrEmpty(choice)).rotate(p.getRotation());
    }
    private static List<StructureTemplate.StructureBlockInfo> floors(TrainStationPiece p) {
        var out=new ArrayList<StructureTemplate.StructureBlockInfo>();
        for(var b:List.of(Blocks.GRAVEL,Blocks.STONE,Blocks.STRUCTURE_BLOCK)) for(var c:cells(p,b)) if(c.pos().getY()==p.templatePosition().getY()) out.add(c);
        return out;
    }
    private static void verify(GameTestHelper h,ServerLevel l,TrainStationPiece p) {
        var positions=new HashSet<BlockPos>();
        for(var block:palette()) for(var c:cells(p,block)) {
            positions.add(c.pos()); var state=expected(p,c,c.pos(),l);
            if(state.getBlock() instanceof StairBlock || state.is(Blocks.OAK_FENCE) || state.is(Blocks.GLASS_PANE)) state=Block.updateFromNeighbourShapes(state,l,c.pos());
            h.assertValueEqual(l.getBlockState(c.pos()),state,"Source train cell at "+c.pos());
            h.assertTrue(!l.getBlockState(c.pos()).is(Blocks.STRUCTURE_BLOCK),"No unresolved damage markers");
        }
        h.assertValueEqual(positions.size(),327,"328 source records resolve to 327 cells");
        for(var c:floors(p)) for(int y=1;y<=7;y++) if(!positions.contains(c.pos().above(y))) h.assertTrue(l.getBlockState(c.pos().above(y)).isAir(),"Only source floor columns are cleared");
        var posts=cells(p,NpcSpawnerContent.BLOCK.get()); h.assertValueEqual(posts.size(),1,"Final post overwrites the source AIR record");
        var b=(NpcSpawnerBlockEntity)l.getBlockEntity(posts.getFirst().pos()); h.assertTrue(b!=null,"Post tile loaded");
        h.assertValueEqual(b.remaining(),3,"Three deaths"); h.assertValueEqual(b.maximum(),2,"Two live miners"); h.assertValueEqual(b.interval(),200,"Source timer"); h.assertValueEqual(b.range(),1d,"Source radius");
        h.assertValueEqual(b.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("zombieminer"),1)),"Only the source miner species");
        h.assertValueEqual(cells(p,Blocks.CHEST).size(),1,"Single upstairs reward"); h.assertValueEqual(((ChestBlockEntity)l.getBlockEntity(cells(p,Blocks.CHEST).getFirst().pos())).getLootTable(),LOOT,"Original deferred loot");
    }
    private static void rotation(GameTestHelper h,int turn,int layout) {
        var p=piece(h,turn*2+layout,turn,SEEDS[layout]); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox(),turn+1); verify(h,h.getLevel(),p);
        h.assertValueEqual(p.template().getSize(),new Vec3i(11,7,11),"Actual scan, distinct from registered 11 by 12 size");
        h.assertValueEqual(p.getBoundingBox().maxY(),p.templatePosition().getY()+7,"Seventh clearing layer included");
        for(var block:palette()) for(var c:p.template().filterBlocks(BlockPos.ZERO,new StructurePlaceSettings(),block)) {
            int[] r=StructureRules.rotate(c.pos().getX(),c.pos().getZ(),turn,5,6); var pos=p.templatePosition().offset(r[0],c.pos().getY(),r[1]);
            h.assertTrue(h.getLevel().getBlockState(pos).is(expected(p,c,pos,h.getLevel()).getBlock()),"Independent original pivot coordinate "+pos);
        } h.succeed();
    }
    private static void clipping(GameTestHelper h,int turn,int layout) {
        var p=piece(h,8+turn*2+layout,turn,SEEDS[layout]); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),1); var before=snapshot(h.getLevel(),p); fill(h,p,Blocks.AIR);
        var b=p.getBoundingBox(); var clips=new ArrayList<BoundingBox>();
        for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) clips.add(new BoundingBox(x*16,b.minY(),z*16,x*16+15,b.maxY(),z*16+15));
        h.assertTrue(clips.size()>1,"Native chunk edges exercised"); Collections.reverse(clips); var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel());
        for(var clip:clips) { place(h,p,clip,2); p=(TrainStationPiece)LocationContent.TRAIN_PIECE.get().load(ctx,p.createTag(ctx)); h.assertValueEqual(p.damageSeed(),SEEDS[layout],"Full 64-bit damage seed survives each save"); }
        h.assertValueEqual(snapshot(h.getLevel(),p),before,"Reverse chunks preserve damage, foundation, stairs and pane connections"); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,16,1,SEEDS[1]); fill(h,p,Blocks.AIR); var l=h.getLevel(); var bottom=floors(p); h.assertValueEqual(bottom.size(),74,"Sparse source foundation");
        var stop=bottom.getFirst().pos(); l.setBlock(stop.below(),Blocks.OBSIDIAN.defaultBlockState(),2);
        for(var c:bottom) { l.setBlock(c.pos().below(2),Blocks.AIR.defaultBlockState(),2); l.setBlock(c.pos().above(8),Blocks.OBSIDIAN.defaultBlockState(),2); }
        var ground=new HashSet<BlockPos>(); bottom.forEach(c->ground.add(c.pos())); var gaps=new ArrayList<BlockPos>(); var box=p.getBoundingBox();
        for(var pos:BlockPos.betweenClosed(box.minX(),p.templatePosition().getY(),box.minZ(),box.maxX(),p.templatePosition().getY(),box.maxZ()))
            if(!ground.contains(pos)) { gaps.add(pos.immutable()); l.setBlock(pos,Blocks.OBSIDIAN.defaultBlockState(),2); l.setBlock(pos.below(),Blocks.OBSIDIAN.defaultBlockState(),2); }
        place(h,p,box,1);
        for(var c:bottom) { h.assertValueEqual(l.getBlockState(c.pos().below()),c.pos().equals(stop)?Blocks.OBSIDIAN.defaultBlockState():expected(p,c,c.pos().below(),l),"Only replaceable source depth one, independent random cobble");
            h.assertTrue(l.getBlockState(c.pos().below(2)).isAir(),"Never fills a second layer"); h.assertTrue(l.getBlockState(c.pos().above(8)).is(Blocks.OBSIDIAN),"Clearing stops below layer eight"); }
        h.assertTrue(!gaps.isEmpty(),"Fixture contains omitted ground cells"); for(var pos:gaps) h.assertTrue(l.getBlockState(pos).is(Blocks.OBSIDIAN) && l.getBlockState(pos.below()).is(Blocks.OBSIDIAN),"No rectangular fill across source gaps");
        verify(h,l,p); h.succeed();
    }
    private static void guards(GameTestHelper h) {
        var p=placed(h,17); var l=h.getLevel(); var spawned=new LinkedHashMap<UUID,ArmedNpc>();
        Consumer<EntityJoinLevelEvent> observe=e->{ if(e.getLevel()==l && e.getEntity() instanceof ArmedNpc npc && p.getBoundingBox().isInside(npc.blockPosition())) { npc.removeFreeWill(); spawned.put(npc.getUUID(),npc); } };
        NeoForge.EVENT_BUS.addListener(observe);
        try {
            var pos=cells(p,NpcSpawnerContent.BLOCK.get()).getFirst().pos(); var b=(NpcSpawnerBlockEntity)l.getBlockEntity(pos);
            for(int death=0;death<3;death++) {
                for(int tick=0;tick<600;tick++) NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b);
                h.assertValueEqual(b.activeCount(),Math.min(2,3-death),"Two alive at most, bounded by remaining deaths");
                var npc=spawned.get(b.activeIds().iterator().next()); h.assertTrue(npc instanceof ZombieMiner,"Original zombie miner");
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
        var l=h.getLevel(); var p=placed(h,18); var pos=cells(p,Blocks.CHEST).getFirst().pos(); var b=(ChestBlockEntity)l.getBlockEntity(pos); var table=l.getServer().reloadableRegistries().getLootTable(LOOT); long seed;
        for(seed=1;seed<10000;seed++) if(table.getRandomItems(params(l,pos),seed).stream().anyMatch(s->s.is(Items.COAL))) break;
        h.assertTrue(seed<10000,"Reproducible coal reward"); b.setLootTableSeed(seed); b=reload(l,b); h.assertValueEqual(b.getLootTableSeed(),seed,"Unopened seed persists");
        var expected=new HashMap<Item,Integer>(); table.getRandomItems(params(l,pos),seed).forEach(s->expected.merge(s.getItem(),s.getCount(),Integer::sum));
        var player=WeaponGameTests.player(h); h.assertTrue(b.createMenu(1,player.getInventory(),player)!=null,"Upstairs chest opens"); h.assertValueEqual(items(b),expected,"Exact native source rewards");
        ItemStack coal=ItemStack.EMPTY; for(int slot=0;slot<b.getContainerSize();slot++) if(b.getItem(slot).is(Items.COAL)) { coal=b.removeItem(slot,1); break; }
        var after=items(b); b=reload(l,b); h.assertValueEqual(items(b),after,"Opened inventory persists"); b.clearContent(); b=reload(l,b); h.assertTrue(b.isEmpty() && b.getLootTable()==null,"Empty chest cannot reroll");
        var oven=cells(p,Blocks.FURNACE).getFirst().pos(); var furnace=(FurnaceBlockEntity)l.getBlockEntity(oven); furnace.setItem(0,new ItemStack(Items.COBBLESTONE)); furnace.setItem(1,coal);
        for(int tick=0;tick<100;tick++) AbstractFurnaceBlockEntity.serverTick(l,oven,l.getBlockState(oven),furnace);
        h.assertTrue(furnace.getItem(2).isEmpty() && furnace.getItem(1).isEmpty(),"Real loot coal is spent before smelting completes");
        var tag=furnace.saveWithFullMetadata(l.registryAccess()); l.removeBlockEntity(oven); furnace=(FurnaceBlockEntity)BlockEntity.loadStatic(oven,l.getBlockState(oven),tag,l.registryAccess()); l.setBlockEntity(furnace);
        for(int tick=0;tick<105;tick++) AbstractFurnaceBlockEntity.serverTick(l,oven,l.getBlockState(oven),furnace);
        h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(2).is(Items.STONE) && furnace.getItem(2).getCount()==1,"Station furnace completes its saved cycle using found fuel"); h.succeed();
    }
    private static void loot(GameTestHelper h) {
        var table=h.getLevel().getServer().reloadableRegistries().getLootTable(LOOT); var seen=new HashSet<String>();
        for(long seed=0;seed<1024;seed++) { var drops=table.getRandomItems(params(h.getLevel(),BlockPos.ZERO),seed); h.assertTrue(drops.size()>=1 && drops.size()<=4,"One to four original resource rolls");
            for(var stack:drops) seen.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()); }
        var expected=new HashSet<String>(); for(var id:List.of("iron_ingot","redstone","coal","gunpowder","gold_ingot","diamond","ender_pearl")) expected.add("minecraft:"+id);
        for(var id:List.of("heavycloth","mechanicalpartsiron","mechanicalpartsobsidiansteel","plasticsheet","rubberbar","ingotobsidiansteel")) expected.add("techguns:"+id);
        h.assertValueEqual(seen,expected,"All thirteen source rewards resolve"); h.succeed();
    }
    private static void facilities(GameTestHelper h) {
        for(int turn=0;turn<4;turn++) {
            var p=piece(h,19+turn,turn,SEEDS[0]); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),2); var l=h.getLevel();
            for(var c:cells(p,Blocks.OAK_PRESSURE_PLATE)) h.assertTrue(c.state().canSurvive(l,c.pos()),"Original fence supports a pressure-plate table");
            for(var c:cells(p,Blocks.LADDER)) {
                var state=l.getBlockState(c.pos()); h.assertTrue(state.is(Blocks.LADDER),"All source ladder sections placed");
                if(!state.canSurvive(l,c.pos())) h.assertTrue(Block.updateFromNeighbourShapes(state,l,c.pos()).isAir(),"Unsupported top ladder follows vanilla break rules if the random wall is missing");
            }
            var table=cells(p,Blocks.CRAFTING_TABLE).getFirst().pos(); var provider=l.getBlockState(table).getMenuProvider(l,table); var player=WeaponGameTests.player(h);
            h.assertTrue(provider!=null && provider.createMenu(1,player.getInventory(),player) instanceof net.minecraft.world.inventory.CraftingMenu,"Original crafting table exposes a real crafting menu");
        } h.succeed();
    }
    private static void damage(GameTestHelper h) {
        var p=piece(h,23,0,SEEDS[1]); var l=h.getLevel(); var ctx=StructurePieceSerializationContext.fromLevel(l); var restored=(TrainStationPiece)LocationContent.TRAIN_PIECE.get().load(ctx,p.createTag(ctx));
        var other=new TrainStationPiece(l.getServer().getStructureManager(),p.templatePosition(),0,SEEDS[0]); boolean different=false,independent=false;
        for(var c:cells(p,Blocks.STRUCTURE_BLOCK)) {
            var expected=expected(p,c,c.pos(),l); h.assertValueEqual(restored.damaged(c.nbt(),c.pos(),l.registryAccess()),expected,"Restored source roll independent of placement random");
            different|=!other.damaged(c.nbt(),c.pos(),l.registryAccess()).equals(expected);
            if(c.pos().getY()==p.templatePosition().getY()) independent|=!expected.equals(p.damaged(c.nbt(),c.pos().below(),l.registryAccess()));
        }
        h.assertTrue(different && independent,"Damage differs between seeds and foundation/floor rolls"); h.succeed();
    }
    private static void gravity(GameTestHelper h) {
        var p=placed(h,24); var l=h.getLevel();
        var rail=cells(p,Blocks.STRUCTURE_BLOCK).stream().map(StructureTemplate.StructureBlockInfo::pos).filter(pos->l.getBlockState(pos).is(Blocks.RAIL)).findFirst().orElseThrow();
        var floor=rail.below(); h.assertTrue(l.getBlockTicks().hasScheduledTick(floor,Blocks.GRAVEL) && l.getBlockTicks().hasScheduledTick(floor.below(),Blocks.GRAVEL),"Both original gravel layers receive vanilla physics ticks");
        l.setBlock(floor.below(3),Blocks.STONE.defaultBlockState(),2);
        for(var pos:List.of(floor.below(),floor)) {
            l.getBlockState(pos).tick(l,pos,RandomSource.create(1));
            var falling=l.getEntitiesOfClass(FallingBlockEntity.class,new AABB(pos).inflate(2)); h.assertValueEqual(falling.size(),1,"Real falling gravel entity created");
            for(int tick=0;tick<40 && !falling.getFirst().isRemoved();tick++) falling.getFirst().tick();
            h.assertTrue(falling.getFirst().isRemoved(),"Falling block settles");
        }
        h.assertTrue(l.getBlockState(floor.below(2)).is(Blocks.GRAVEL) && l.getBlockState(floor.below()).is(Blocks.GRAVEL),"Source gravel falls onto the lower support");
        h.assertTrue(l.getBlockState(rail).isAir(),"Rail drops when its gravel support falls"); h.succeed();
    }
    private static TrainStationStructure structure(ServerLevel l) { return (TrainStationStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(TrainStationPiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c,long seed) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),seed,c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE); var decoded=(TrainStationStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow(); h.assertTrue(!Structure.DIRECT_CODEC.encodeStart(ops,decoded).getOrThrow().getAsJsonObject().has("reserved_big_grid"),"Grid reservation belongs to the shared placement"); h.assertTrue(!Structure.DIRECT_CODEC.encodeStart(ops,decoded).getOrThrow().getAsJsonObject().has("reserved_medium_grid"),"Grid reservation belongs to the shared placement");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,TrainStationPiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dimension:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dimension,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(32,32),0,l,b->true).isValid(),"Overworld-only guard");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32),0)).isEmpty(),"Global structure switch"); } finally { LocationConfig.ENABLED.set(enabled); }
        for(int n=-5;n<=5;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32*n),0)).isEmpty(),"Medium and big grids remain reserved"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel(); var s=structure(l); ChunkPos chosen=null;
        for(int n=129;n<=2048;n++) { var c=new ChunkPos(sign*144,sign*16*n); if(s.findGenerationPoint(context(l,c,l.getSeed())).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Source train ticket and surface find native land"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Native train start saved"); var p=(TrainStationPiece)start.getPieces().getFirst(); load(l,p); var box=p.getBoundingBox();
        for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++) for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++) l.getChunk(x,z);
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z).postProcessGeneration(l);
        verify(h,l,p);
        for(var id:List.of(GasStationPiece.TEMPLATE,PoliceStationPiece.TEMPLATE,SurvivorHideoutPiece.TEMPLATE,DesertOilPiece.TEMPLATE,MeteorPiece.TEMPLATE,OreSpikePiece.TEMPLATE,TGContent.id("alienbug_nest"))) { var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"Other small and medium candidates never overlap the train ticket"); }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,TrainStationPiece.TEMPLATE)); var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(5,3,6),0,false);
        h.assertTrue(found!=null,"Native locate finds the actual train station"); h.assertValueEqual(new ChunkPos(found.getFirst().getX()>>4,found.getFirst().getZ()>>4),chosen,"Locate start chunk");
        var before=snapshot(l,p); var saved=cells(p,Blocks.CHEST).stream().map(c->l.getBlockEntity(c.pos()).saveWithFullMetadata(l.registryAccess())).toList(); l.getChunk(chosen.x(),chosen.z());
        h.assertValueEqual(snapshot(l,p),before,"Repeat chunk request does not regenerate station"); h.assertValueEqual(cells(p,Blocks.CHEST).stream().map(c->l.getBlockEntity(c.pos()).saveWithFullMetadata(l.registryAccess())).toList(),saved,"Original rewards retain seeds");
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural TrainStation: chunk={}, origin={}, rotation={}, guards=1, chestTiles=1, damageSeed={}",chosen,p.templatePosition(),p.getRotation(),p.damageSeed()); h.succeed();
    }
    private static void selection(GameTestHelper h) {
        var l=h.getLevel(); var train=structure(l); var gas=(GasStationStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(GasStationPiece.TEMPLATE);
        boolean ores=LocationConfig.ORE_CLUSTERS.get(); int trains=0,stations=0,reserved=0;
        try {
            for(long seed:new long[]{0,1,42}) for(int n=-64;n<=64;n++) {
                var c=new ChunkPos(144,16*n); int roll=context(l,c,seed).random().nextInt(40);
                LocationConfig.ORE_CLUSTERS.set(true); boolean t=train.findGenerationPoint(context(l,c,seed)).isPresent(),g=gas.findGenerationPoint(context(l,c,seed)).isPresent();
                LocationConfig.ORE_CLUSTERS.set(false); h.assertValueEqual(train.findGenerationPoint(context(l,c,seed)).isPresent(),t,"Station is independent of the ore switch");
                h.assertValueEqual(gas.findGenerationPoint(context(l,c,seed)).isPresent(),g,"Gas remains independent of the ore switch");
                h.assertTrue(!(t && g),"Both native IDs share one source candidate roll");
                if(roll<10 || roll>=20 && roll<30) { h.assertTrue(!t && !g,"Factory house and mine retain their twenty tickets"); reserved++; }
                if(t) trains++; if(g) stations++;
            }
        } finally { LocationConfig.ORE_CLUSTERS.set(ores); }
        h.assertTrue(trains>0 && stations>0 && reserved>0,"Three seeds exercise both ports and reserved candidates"); h.succeed();
    }
    private TrainStationGameTests() {}
}
