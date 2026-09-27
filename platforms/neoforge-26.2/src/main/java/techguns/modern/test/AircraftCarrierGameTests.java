package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.modern.*;
import techguns.modern.npc.*;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.*;
import techguns.modern.world.structure.*;

final class AircraftCarrierGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int sign:List.of(-1,1)) for(int turn=0;turn<4;turn++) {int turns=turn;
            r.register("carrier_full_scan_saved_chunks_"+name(sign)+"_"+turn,()->h->complete(h,sign,turns));}
        r.register("carrier_consumed_supplies_and_posts_stay_spent",()->AircraftCarrierGameTests::spent);
        r.register("carrier_nested_loot_and_real_crate_rewards",()->AircraftCarrierGameTests::loot);
        r.register("carrier_finite_soldier_commando_helicopter",()->AircraftCarrierGameTests::encounters);
        r.register("carrier_water_registry_config_dimensions",()->AircraftCarrierGameTests::registry);
        r.register("carrier_metal_stair_shapes_recipes_and_water",()->AircraftCarrierGameTests::stairs);
        if(Boolean.getBoolean("techguns.worldgenTest")) for(int sign:List.of(-1,1))
            r.register("structure_aircraft_carrier_"+name(sign)+"_natural_chunks",()->h->natural(h,sign));
    }
    private static String name(int sign) {return sign<0?"negative":"positive";}
    private static AircraftCarrierPiece carrier(int slot,int sign,int turns) {
        return new AircraftCarrierPiece(AircraftCarrierPlan.create(new BlockPos(sign*(5800013+slot*96),120,sign*5800013),turns,31L+slot));
    }
    private static List<ChunkPos> chunks(AircraftCarrierPiece p) {
        var b=p.getBoundingBox();var result=new ArrayList<ChunkPos>();
        for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) result.add(new ChunkPos(x,z));return result;
    }
    private static void load(ServerLevel l,AircraftCarrierPiece p) {for(var c:chunks(p)) l.getChunk(c.x(),c.z());}
    private static void place(ServerLevel l,AircraftCarrierPiece p,ChunkPos c) {
        p.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(c.pack()),
            new BoundingBox(c.getMinBlockX(),l.getMinY(),c.getMinBlockZ(),c.getMaxBlockX(),l.getMaxY()-1,c.getMaxBlockZ()),c,p.plan().origin());
    }
    private static void place(ServerLevel l,AircraftCarrierPiece p) {for(var c:chunks(p)) place(l,p,c);}
    private static AircraftCarrierPiece restore(ServerLevel l,AircraftCarrierPiece p) {
        var context=StructurePieceSerializationContext.fromLevel(l);return (AircraftCarrierPiece)LocationContent.AIRCRAFT_CARRIER_PIECE.get().load(context,p.createTag(context));
    }
    private static void verify(GameTestHelper h,AircraftCarrierPiece p) {
        var l=h.getLevel();var plan=p.plan();var view=PlannedBlocks.view(plan.cells(),l);int guards=0,helis=0,doors=0,crates=0;
        h.assertValueEqual(plan.cells().size(),3929,"Every unique source position is saved, including air");
        for(var e:plan.cells().entrySet()) {
            var actual=l.getBlockState(e.getKey());h.assertValueEqual(actual,PlannedBlocks.connected(e.getValue(),view,e.getKey()),"Whole carrier at "+e.getKey());
            if(actual.getBlock() instanceof StairBlock||actual.getBlock() instanceof IronBarsBlock)
                h.assertValueEqual(actual,Block.updateFromNeighbourShapes(actual,l,e.getKey()),"Native connection across chunk seams");
            if(actual.getBlock() instanceof DoorBlock) {
                doors++;var other=l.getBlockState(actual.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER?e.getKey().above():e.getKey().below());
                h.assertTrue(other.is(actual.getBlock()),"Door pair exists");
                h.assertValueEqual(other.getValue(DoorBlock.FACING),actual.getValue(DoorBlock.FACING),"Door facing");
                h.assertValueEqual(other.getValue(DoorBlock.HINGE),actual.getValue(DoorBlock.HINGE),"Door hinge");
            }
            if(actual.getBlock() instanceof MilitaryCrateBlock) crates++;
            if(l.getBlockEntity(e.getKey()) instanceof NpcSpawnerBlockEntity post) {
                boolean heli=actual.is(NpcSpawnerContent.SOLDIER_BLOCK.get());if(heli) helis++;else guards++;
                h.assertTrue(heli||actual.is(NpcSpawnerContent.BLOCK.get()),"Actual source HOLE/SOLDIER_SPAWN block");
                h.assertValueEqual(post.remaining(),heli?1:6,"Source finite quota");h.assertValueEqual(post.maximum(),heli?1:2,"Source active cap");
                h.assertValueEqual(post.interval(),heli?200:150,"Source interval");h.assertValueEqual(post.range(),heli?0.0:2.0,"Source range");
                h.assertValueEqual(post.heightOffset(),0,"MBlockTGSpawner never adds MilitaryCamp's height 64");
                h.assertValueEqual(post.entries(),heli?List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("attackhelicopter"),1)):
                    List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("armysoldier"),1),new NpcSpawnerBlockEntity.Entry(TGContent.id("commando"),1)),"Unchanged source weights");
            }
        }
        h.assertValueEqual(guards,9,"Nine HOLE posts");h.assertValueEqual(helis,1,"One helicopter");h.assertValueEqual(doors,58,"Twenty-nine full doors");
        h.assertTrue(crates>=24&&crates<=48,"24 guaranteed and 24 optional supplies");h.assertValueEqual(plan.loot().size(),14,"All fourteen chest halves/singles");
        for(var e:plan.loot().entrySet()) {
            var chest=(ChestBlockEntity)l.getBlockEntity(e.getKey());h.assertTrue(chest!=null,"Native chest entity");
            h.assertValueEqual(chest.getLootTable(),ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/aircraftcarrier")),"Carrier nested supplies");
            h.assertValueEqual(chest.getLootTableSeed(),e.getValue(),"Persistent loot seed");
            var state=l.getBlockState(e.getKey());var container=ChestBlock.getContainer((ChestBlock)Blocks.CHEST,state,l,e.getKey(),true);
            h.assertTrue(container!=null&&container.getContainerSize()==(state.getValue(ChestBlock.TYPE)==ChestType.SINGLE?27:54),"Usable single/double chest");
        }
    }
    private static void complete(GameTestHelper h,int sign,int turns) {
        var l=h.getLevel();var p=carrier(turns,sign,turns);var expected=p.plan();load(l,p);var cs=chunks(p);Collections.reverse(cs);
        var first=cs.getFirst();var outside=expected.cells().keySet().stream().filter(q->!ChunkPos.containing(q).equals(first)).findFirst().orElseThrow();
        l.setBlock(outside,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);place(l,p,first);
        h.assertTrue(l.getBlockState(outside).is(Blocks.DIAMOND_BLOCK),"Placement does not write outside current chunk");p=restore(l,p);
        for(var c:cs) {place(l,p,c);p=restore(l,p);}
        h.assertValueEqual(p.plan(),expected,"Full plan survives save after every chunk");verify(h,p);
        h.assertValueEqual(expected,carrier(turns,sign,turns).plan(),"Complete ordered RNG is reproducible");
        // Independent source anchors exercise the 27/10 pivot, not just saved-plan self-consistency.
        int[][] heli={{17,9},{26,20},{37,11},{28,0}};var pos=expected.origin().offset(heli[turns][0],8,heli[turns][1]);
        h.assertTrue(l.getBlockState(pos).is(NpcSpawnerContent.SOLDIER_BLOCK.get()),"Source helicopter anchor in all four directions");
        h.assertTrue(l.getBlockState(expected.position(35,8,3)).is(BuildingContent.BLOCKS.get("ladder_metal").get()),"Source ladder is real climbable metal");
        h.succeed();
    }
    private static void spent(GameTestHelper h) {
        var l=h.getLevel();var p=carrier(10,1,0);load(l,p);place(l,p);
        var chest=(ChestBlockEntity)l.getBlockEntity(p.plan().position(14,4,9));h.assertTrue(!chest.isEmpty(),"Chest contains rewards");chest.clearContent();
        var guard=p.plan().position(18,1,9);var crate=p.plan().position(17,4,7);l.setBlock(guard,Blocks.AIR.defaultBlockState(),2);l.setBlock(crate,Blocks.AIR.defaultBlockState(),2);
        var wall=p.plan().position(0,0,0);l.setBlock(wall,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);p=restore(l,p);place(l,p);
        h.assertTrue(chest.isEmpty()&&chest.getLootTable()==null,"Opened chest never refills");
        h.assertTrue(l.getBlockState(guard).isAir()&&l.getBlockState(crate).isAir(),"Spent post/crate never reappears");
        h.assertTrue(l.getBlockState(wall).is(Blocks.DIAMOND_BLOCK),"Player edit survives saved piece reentry");h.succeed();
    }
    private static void loot(GameTestHelper h) {
        var l=h.getLevel();var player=WeaponGameTests.player(h);
        var table=l.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/aircraftcarrier")));
        var counts=new HashSet<Integer>();
        try {
            for(int seed=0;seed<512;seed++) {
                var items=table.getRandomItems(MilitaryCrateDrops.params(l,h.absolutePos(BlockPos.ZERO),player,0),RandomSource.create(seed*0x9e3779b97f4a7c15L));
                long guns=items.stream().filter(s->s.getItem() instanceof GunItem).count();
                h.assertTrue(guns>=1&&guns<=3,"Gun pool 1..3");h.assertTrue(items.size()-guns>=3&&items.size()-guns<=7,"Generic pool 3..7");counts.add(items.size());
                for(var stack:items) if(stack.getItem() instanceof GunItem gun) h.assertValueEqual(GunItem.rounds(stack),gun.definition().stats().capacity(),"Source guns start loaded");
            }
            h.assertTrue(counts.contains(4)&&counts.contains(10),"Both source roll endpoints");
            var p=carrier(11,1,1);load(l,p);place(l,p);var drops=new ArrayList<ItemEntity>();
            Consumer<EntityJoinLevelEvent> capture=e->{if(e.getLevel()==l&&e.getEntity() instanceof ItemEntity item) drops.add(item);};NeoForge.EVENT_BUS.addListener(capture);
            try {
                int mined=0;for(var e:p.plan().cells().entrySet()) if(e.getValue().getBlock() instanceof MilitaryCrateBlock) {
                    var state=l.getBlockState(e.getKey());l.setBlock(e.getKey(),Blocks.AIR.defaultBlockState(),2);
                    Block.dropResources(state,l,e.getKey(),null,player,ItemStack.EMPTY);mined++;
                }
                h.assertValueEqual(drops.size(),mined,"Each placed crate produces a real harvest reward");
                h.assertTrue(drops.stream().noneMatch(i->i.getItem().getItem() instanceof BlockItem b&&b.getBlock() instanceof MilitaryCrateBlock),"Normal player harvest yields supplies");
            } finally {NeoForge.EVENT_BUS.unregister(capture);drops.forEach(Entity::discard);}
        } finally {player.discard();}
        h.succeed();
    }
    private static void encounters(GameTestHelper h) {
        var l=h.getLevel();var p=carrier(12,1,0);load(l,p);place(l,p);
        for(boolean heli:List.of(false,true)) {
            var pos=heli?p.plan().position(17,8,9):p.plan().position(22,8,9);var post=(NpcSpawnerBlockEntity)l.getBlockEntity(pos);var spawned=new ArrayList<Mob>();
            Consumer<EntityJoinLevelEvent> observe=e->{if(e.getLevel()==l&&e.getEntity() instanceof Mob m&&m instanceof SpawnerLinked linked&&linked.spawnerLink()!=null&&linked.spawnerLink().origin().pos().equals(pos)){m.removeFreeWill();spawned.add(m);}};
            NeoForge.EVENT_BUS.addListener(observe);
            try {
                int quota=heli?1:6,cap=heli?1:2;
                for(int killed=0;killed<quota;killed+=cap) {
                    for(int tick=0;tick<600;tick++) NpcSpawnerBlockEntity.serverTick(l,pos,post.getBlockState(),post);
                    h.assertValueEqual(post.activeCount(),cap,"Both guards can be active; helicopter has one slot");
                    h.assertValueEqual(spawned.size(),killed+cap,"Cap prevents extra spawns");
                    for(var mob:spawned.subList(killed,killed+cap)) {
                        h.assertTrue(heli?mob instanceof AttackHelicopter:mob instanceof ArmySoldier||mob instanceof Commando,"Source NPC candidates");
                        h.assertValueEqual(mob.getY(),(double)pos.getY()+1,"No invented helicopter elevation");
                        mob.hurtServer(l,l.damageSources().genericKill(),10000);if(heli) for(int tick=0;tick<100;tick++) mob.tick();
                    }
                    NpcSpawnerBlockEntity.serverTick(l,pos,post.getBlockState(),post);
                }
                h.assertTrue(l.getBlockState(pos).isAir(),"Actual deaths exhaust and remove source post");
                p=restore(l,p);place(l,p);h.assertTrue(l.getBlockState(pos).isAir(),"Saved chunk does not recreate dead encounter");
            } finally {NeoForge.EVENT_BUS.unregister(observe);spawned.forEach(Entity::discard);}
        }
        h.succeed();
    }
    private static AircraftCarrierStructure structure(ServerLevel l) {return (AircraftCarrierStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(TGContent.id("aircraft_carrier"));}
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c) {var g=l.getChunkSource().getGenerator();return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),c,l,b->true);}
    private static void registry(GameTestHelper h) {
        var l=h.getLevel();var s=structure(l);var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        h.assertTrue(Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow() instanceof AircraftCarrierStructure,"Native carrier codec");
        for(var block:List.of(Blocks.WATER,Blocks.STONE,Blocks.ICE,Blocks.LAVA,Blocks.AIR))
            h.assertValueEqual(AircraftCarrierStructure.waterHeight(new NoiseColumn(62,new BlockState[]{block.defaultBlockState()}),63),block==Blocks.WATER?63:-1,"Only surface water is eligible");
        var waterlogged=Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED,true);
        h.assertValueEqual(AircraftCarrierStructure.waterHeight(new NoiseColumn(62,new BlockState[]{waterlogged}),63),-1,"Waterlogged land is not open water");
        h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(64,64))).isEmpty(),"Flat dry world rejects the ocean candidate");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,TGContent.id("aircraft_carrier")));var g=l.getChunkSource().getGenerator();
        for(var dim:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dim,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(64,64),0,l,b->true).isValid(),"Overworld only");
        h.succeed();
    }
    private static void stairs(GameTestHelper h) {
        var l=h.getLevel();var pos=h.absolutePos(new BlockPos(3,2,3));
        for(int i=0;i<2;i++) {
            var b=MetalStairContent.BLOCKS.get(i).get();var s=b.defaultBlockState();
            l.setBlock(pos,Blocks.STONE.defaultBlockState(),2);l.setBlock(pos.above(),s,3);
            h.assertTrue(!s.isCollisionShapeFullBlock(l,pos.above()),"Real stair collision");h.assertValueEqual(s.getDestroySpeed(l,pos.above()),8f,"Source metal hardness");
            for(var facing:Direction.Plane.HORIZONTAL) for(var half:Half.values()) for(var shape:StairsShape.values()) {
                var v=s.setValue(StairBlock.FACING,facing).setValue(StairBlock.HALF,half).setValue(StairBlock.SHAPE,shape);
                h.assertTrue(!v.getCollisionShape(l,pos.above()).isEmpty(),"Every native stair shape exists");
                h.assertValueEqual(v.rotate(Rotation.CLOCKWISE_90).rotate(Rotation.COUNTERCLOCKWISE_90),v,"Stair rotations invert");
            }
            var panel=BuildingContent.BLOCKS.get(i==0?"metalpanel_panel_large_border":"metalpanel_steelframe_dark").get();
            var input=CraftingInput.of(3,3,List.of(new ItemStack(panel),ItemStack.EMPTY,ItemStack.EMPTY,new ItemStack(panel),new ItemStack(panel),ItemStack.EMPTY,new ItemStack(panel),new ItemStack(panel),new ItemStack(panel)));
            var recipe=l.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,l).orElseThrow();var out=recipe.value().assemble(input);
            h.assertTrue(out.is(b.asItem())&&out.getCount()==6,"Six source stairs from six panels");
            var reverse=CraftingInput.of(1,1,List.of(new ItemStack(b)));var returned=l.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,reverse,l).orElseThrow().value().assemble(reverse);
            h.assertTrue(returned.is(panel.asItem())&&returned.getCount()==1,"One stair returns its matching panel");
            var wet=s.setValue(StairBlock.WATERLOGGED,true);l.setBlock(pos.above(),wet,3);h.assertTrue(!l.getFluidState(pos.above()).isEmpty(),"Modern native waterlogging");
        }
        h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel();var s=structure(l);ChunkPos chosen=null;
        for(int n=513;n<900;n++) {var c=new ChunkPos(sign*64*11,sign*64*n);if(s.findGenerationPoint(context(l,c)).isPresent()){chosen=c;break;}}
        h.assertTrue(chosen!=null,"Find carrier on real normal ocean terrain");var chunk=l.getChunk(chosen.x(),chosen.z());var start=chunk.getStartForStructure(s);
        h.assertTrue(start!=null&&start.isValid(),"Native water candidate creates a saved structure start");
        for(String name:List.of("castle","military_camp")) {
            var land=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(TGContent.id(name));
            var point=land instanceof CastleStructure castle?castle.findGenerationPoint(context(l,chosen)):((MilitaryCampStructure)land).findGenerationPoint(context(l,chosen));
            h.assertTrue(point.isEmpty(),"LAND cannot take a WATER site");
            var other=chunk.getStartForStructure(land);h.assertTrue(other==null||!other.isValid(),"No overlapping native LAND start");
        }
        var p=(AircraftCarrierPiece)start.getPieces().getFirst();load(l,p);verify(h,p);h.assertValueEqual(restore(l,p).plan(),p.plan(),"Native saved ocean plan");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,TGContent.id("aircraft_carrier")));
        h.assertTrue(l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),chosen.getWorldPosition(),0,false)!=null,"Locate uses original candidate grid");
        boolean enabled=LocationConfig.ENABLED.get(),ore=LocationConfig.ORE_CLUSTERS.get();
        try {LocationConfig.ORE_CLUSTERS.set(!ore);h.assertTrue(s.findGenerationPoint(context(l,chosen)).isPresent(),"Ore toggle does not disable carrier");LocationConfig.ENABLED.set(false);h.assertTrue(s.findGenerationPoint(context(l,chosen)).isEmpty(),"Main structure toggle disables carrier");}
        finally {LocationConfig.ORE_CLUSTERS.set(ore);LocationConfig.ENABLED.set(enabled);}
        var chest=(ChestBlockEntity)l.getBlockEntity(p.plan().loot().keySet().iterator().next());h.assertTrue(!chest.isEmpty(),"Natural carrier rewards resolve");
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural AircraftCarrier: chunk={}, origin={}, rotation={}, cells={}, chests={}, seed={}",chosen,p.plan().origin(),p.plan().turns(),p.plan().cells().size(),p.plan().loot().size(),p.plan().seed());h.succeed();
    }
    private AircraftCarrierGameTests() {}
}
