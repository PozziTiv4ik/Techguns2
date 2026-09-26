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
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.npc.*;
import techguns.modern.machine.ChemicalRules;
import techguns.modern.machine.drill.*;
import techguns.modern.fluid.TGFluids;
import net.minecraft.world.level.material.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.*;
import techguns.modern.world.structure.*;

final class DesertOilGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        var oils=List.of("minecraft:water","techguns:creeper_acid","minecraft:empty");
        for(int t=0;t<4;t++) {
            int turn=t;
            for(int f=0;f<oils.size();f++) { int fluid=f; r.register("location_desert_oil_rotation_"+t+"_fluid_"+f,()->h->rotation(h,turn,oils.get(fluid),fluid)); }
            r.register("location_desert_oil_reverse_chunk_save_"+t,()->h->clipping(h,turn));
        }
        for(int f=0;f<2;f++) { int fluid=f; r.register("location_desert_oil_flow_"+f,()->h->flow(h,oils.get(fluid),fluid)); r.register("location_desert_oil_bucket_"+f,()->h->bucket(h,oils.get(fluid),fluid)); }
        r.register("location_desert_oil_no_synthetic_foundation",()->DesertOilGameTests::untouched);
        r.register("location_desert_oil_mixture_source_oracle",()->DesertOilGameTests::mixtures);
        r.register("location_desert_oil_twelve_guard_deaths",()->DesertOilGameTests::guards);
        r.register("location_desert_oil_registry_and_absence",()->DesertOilGameTests::registry);
        r.register("location_desert_oil_separate_ordered_config",()->DesertOilGameTests::resolver);
        if(Boolean.getBoolean("techguns.chemistryTest")) r.register("chem_optional_oil_blockless_tag_and_preference",()->DesertOilGameTests::blockless);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_desert_oil_positive_natural_chunks",()->h->natural(h,1)); r.register("structure_desert_oil_negative_natural_chunks",()->h->natural(h,-1));
            r.register("structure_desert_oil_six_candidates_natural_chunks",()->DesertOilGameTests::selection);
        }
    }
    private static Block cluster() { return OreClusterContent.BLOCKS.get("ore_cluster_oil").get(); }
    private static List<Block> palette() { return List.of(Blocks.SANDSTONE,FortificationContent.SANDBAGS.get(),Blocks.AIR,NpcSpawnerContent.SOLDIER_BLOCK.get(),Blocks.STRUCTURE_BLOCK); }
    private static DesertOilPiece piece(GameTestHelper h,int slot,int turn,String fluid,long seed) { return new DesertOilPiece(h.getLevel().getServer().getStructureManager(),new BlockPos(2400014+slot*64,140,-2400002),turn,Identifier.parse(fluid),seed); }
    private static List<StructureTemplate.StructureBlockInfo> cells(DesertOilPiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static void load(ServerLevel l,DesertOilPiece p) { var b=p.getBoundingBox(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) l.getChunk(x,z); }
    private static void place(GameTestHelper h,DesertOilPiece p,BoundingBox clip,long seed) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(seed),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,DesertOilPiece p) { var b=p.getBoundingBox(); var out=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) out.put(pos.immutable(),l.getBlockState(pos)); return out; }
    private static void fill(GameTestHelper h,DesertOilPiece p,Block block) { load(h.getLevel(),p); for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,block.defaultBlockState(),2); }
    private static DesertOilPiece placed(GameTestHelper h,int slot) { var p=piece(h,slot,0,"minecraft:water",87); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),87); return p; }


    private static String marker(StructureTemplate.StructureBlockInfo c) { return c.nbt().getStringOr("metadata",""); }
    private static void verify(GameTestHelper h,ServerLevel l,DesertOilPiece p) {
        int count=0;
        for(var b:palette()) for(var c:cells(p,b)) {
            count++; var expected=b==Blocks.STRUCTURE_BLOCK?p.mixture(marker(c),c.pos()):c.state();
            if(b==FortificationContent.SANDBAGS.get()) expected=Block.updateFromNeighbourShapes(expected,l,c.pos());
            h.assertValueEqual(l.getBlockState(c.pos()),expected,"Exact desert oil source cell at "+c.pos());
            if(b==Blocks.STRUCTURE_BLOCK) h.assertTrue(l.getBlockEntity(c.pos())==null,"No unresolved marker tiles");
        }
        h.assertValueEqual(count,579,"All source cells, including scanned air");
        var posts=cells(p,NpcSpawnerContent.SOLDIER_BLOCK.get()); h.assertValueEqual(posts.size(),4,"Four source posts");
        for(var c:posts) {
            var b=(NpcSpawnerBlockEntity)l.getBlockEntity(c.pos()); h.assertTrue(b!=null,"Native post tile");
            h.assertValueEqual(b.remaining(),3,"Three actual deaths"); h.assertValueEqual(b.maximum(),1,"One live guard"); h.assertValueEqual(b.interval(),200,"Original interval"); h.assertValueEqual(b.range(),1d,"Original radius");
            h.assertValueEqual(b.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("armysoldier"),4),new NpcSpawnerBlockEntity.Entry(TGContent.id("commando"),1)),"Original ArmySoldier/Commando weights");
            h.assertTrue(b.weaponOverride().isEmpty(),"Native NPC loadouts remain in control");
        }
    }
    private static void rotation(GameTestHelper h,int turn,String oil,int index) {
        var p=piece(h,turn*3+index,turn,oil,17+turn); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox(),1); verify(h,h.getLevel(),p);
        h.assertValueEqual(p.template().getSize(),new Vec3i(11,10,11),"Original scan size");
        for(var b:palette()) for(var c:p.template().filterBlocks(BlockPos.ZERO,new StructurePlaceSettings(),b)) {
            var r=StructureRules.rotate(c.pos().getX(),c.pos().getZ(),turn,5,5); var pos=p.templatePosition().offset(r[0],c.pos().getY(),r[1]);
            h.assertTrue(h.getLevel().getBlockState(pos).is(b==Blocks.STRUCTURE_BLOCK?p.mixture(marker(c),pos).getBlock():b),"Independent legacy rotation coordinates");
        }
        h.succeed();
    }
    private static void clipping(GameTestHelper h,int turn) {
        var previous=ChemicalRules.WORLD_OILS.get();
        try {
            var p=piece(h,12+turn,turn,"minecraft:water",Long.MIN_VALUE+turn); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),1); var expected=snapshot(h.getLevel(),p); fill(h,p,Blocks.AIR);
            var b=p.getBoundingBox(); var clips=new ArrayList<BoundingBox>(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) clips.add(new BoundingBox(x*16,b.minY(),z*16,x*16+15,b.maxY(),z*16+15));
            h.assertTrue(clips.size()>1,"Crosses native chunk edges"); Collections.reverse(clips); var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel());
            ChemicalRules.WORLD_OILS.set(List.of("minecraft:lava"));
            for(var clip:clips) { place(h,p,clip,925); p=(DesertOilPiece)LocationContent.DESERT_OIL_PIECE.get().load(ctx,p.createTag(ctx)); h.assertValueEqual(p.oil(),Identifier.parse("minecraft:water"),"Saved source ignores later provider preference"); h.assertValueEqual(p.mixtureSeed(),Long.MIN_VALUE+turn,"Mixture seed survives each chunk save"); }
            h.assertValueEqual(snapshot(h.getLevel(),p),expected,"Reverse native chunk placement retains all mixtures and sandbag connections"); verify(h,h.getLevel(),p); h.succeed();
        } finally { ChemicalRules.WORLD_OILS.set(previous); }
    }
    private static void untouched(GameTestHelper h) {
        var p=piece(h,16,2,"minecraft:water",19); fill(h,p,Blocks.OBSIDIAN); var known=new HashSet<BlockPos>(); for(var b:palette()) for(var c:cells(p,b)) known.add(c.pos());
        var under=p.templatePosition().below(); h.getLevel().setBlock(under,Blocks.AIR.defaultBlockState(),2); place(h,p,p.getBoundingBox(),1); verify(h,h.getLevel(),p);
        for(var entry:snapshot(h.getLevel(),p).entrySet()) if(!known.contains(entry.getKey())) h.assertTrue(entry.getValue().is(Blocks.OBSIDIAN),"No synthetic clearing outside the source scan");
        h.assertTrue(h.getLevel().getBlockState(under).isAir(),"No invented foundation"); h.succeed();
    }
    private static void mixtures(GameTestHelper h) {
        var seen=new HashSet<Block>();
        for(long seed=0;seed<32;seed++) {
            var p=piece(h,17,0,"minecraft:water",seed);
            for(var c:cells(p,Blocks.STRUCTURE_BLOCK)) {
                var pos=c.pos(); long v=(long)(pos.getX()*3129871)^((long)pos.getZ()*116129781L)^pos.getY(); v=v*v*42317861L+v*11L;
                var random=new java.util.Random(seed^(v>>16)); var expected=switch(marker(c)) {
                    case "techguns:desert_oil_rim" -> random.nextInt(3)<=1?Blocks.WATER:Blocks.SAND;
                    case "techguns:desert_oil_cluster_or_oil" -> random.nextFloat()<.5f?Blocks.WATER:cluster();
                    case "techguns:desert_oil_cluster" -> cluster();
                    case "techguns:desert_oil_oil" -> Blocks.WATER;
                    default -> throw new IllegalStateException(marker(c));
                };
                var actual=p.mixture(marker(c),pos); h.assertTrue(actual.is(expected),"Original probabilities with independent Java Random oracle"); seen.add(actual.getBlock());
            }
        }
        h.assertValueEqual(seen,Set.of(Blocks.WATER,Blocks.SAND,cluster()),"Both random branches and fixed oil/cluster cells exercised"); h.succeed();
    }
    private static void flow(GameTestHelper h,String oil,int index) {
        var p=piece(h,18+index,0,oil,44); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),1); var l=h.getLevel(); var pos=p.templatePosition().offset(5,9,5); var fluid=l.getFluidState(pos);
        h.assertTrue(fluid.isSource() && l.getFluidTicks().hasScheduledTick(pos,fluid.getType()),"Provider fluid receives a scheduled update");
        fluid.tick(l,pos,l.getBlockState(pos)); var flowing=l.getFluidState(pos.east()); h.assertTrue(!flowing.isEmpty() && flowing.getType().isSame(fluid.getType()) && !flowing.isSource(),"Real provider physics creates flowing liquid"); h.succeed();
    }
    private static void bucket(GameTestHelper h,String oil,int index) {
        var p=piece(h,20+index,0,oil,44); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),1); var l=h.getLevel(); var pos=p.templatePosition().offset(5,9,5); var block=(BucketPickup)l.getBlockState(pos).getBlock();
        var bucket=block.pickupBlock(WeaponGameTests.player(h),l,pos,l.getBlockState(pos)); h.assertTrue(bucket.is(index==0?Items.WATER_BUCKET:TGFluids.ACID.bucket.get()),"Native provider bucket is collectible");
        h.assertTrue(l.getFluidState(pos).isEmpty(),"Pickup removes the actual source"); l.getChunk(pos); h.assertTrue(l.getFluidState(pos).isEmpty(),"Ready chunk lookup does not regenerate the source"); h.succeed();
    }
    private static void guards(GameTestHelper h) {
        var p=placed(h,22); var l=h.getLevel(); var spawned=new LinkedHashMap<UUID,Mob>();
        Consumer<EntityJoinLevelEvent> observe=e->{ if(e.getLevel()==l && e.getEntity() instanceof Mob npc && (npc instanceof ArmySoldier || npc instanceof Commando) && p.getBoundingBox().isInside(npc.blockPosition())) { npc.removeFreeWill(); spawned.put(npc.getUUID(),npc); } };
        NeoForge.EVENT_BUS.addListener(observe);
        try {
            for(var c:cells(p,NpcSpawnerContent.SOLDIER_BLOCK.get())) {
                var b=(NpcSpawnerBlockEntity)l.getBlockEntity(c.pos());
                for(int death=0;death<3;death++) {
                    for(int tick=0;tick<400;tick++) NpcSpawnerBlockEntity.serverTick(l,c.pos(),b.getBlockState(),b);
                    h.assertValueEqual(b.activeCount(),1,"One live guard per post"); var npc=spawned.get(b.activeIds().iterator().next()); h.assertTrue(npc!=null && npc.isAlive(),"Actual military NPC spawned");
                    h.assertValueEqual(((SpawnerLinked)npc).spawnerLink(),b.link(),"Correct persistent owner"); h.assertTrue(npc.getMainHandItem().getItem() instanceof GunItem,"Actual armed guard");
                    npc.hurtServer(l,l.damageSources().genericKill(),10000); h.assertTrue(!npc.isAlive(),"Actual death"); h.assertValueEqual(b.remaining(),2-death,"One quota per death"); NpcSpawnerBlockEntity.serverTick(l,c.pos(),b.getBlockState(),b);
                }
                h.assertTrue(l.getBlockState(c.pos()).isAir(),"Exhausted post disappears");
            }
            h.assertValueEqual(spawned.size(),12,"Twelve distinct real military guards"); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); spawned.values().forEach(Entity::discard); }
    }
    private static void resolver(GameTestHelper h) {
        var world=ChemicalRules.WORLD_OILS.get(); var chemical=ChemicalRules.OILS.get();
        try {
            ChemicalRules.WORLD_OILS.set(List.of()); ChemicalRules.OILS.set(List.of("minecraft:water"));
            h.assertTrue(ChemicalRules.groupMatches("oils",Fluids.WATER) && !ClusterOutputs.hasWorldOil() && ClusterOutputs.entries("oil").isEmpty(),"Chemical oils alone cannot enable oil fields or drill output");
            ChemicalRules.WORLD_OILS.set(List.of("missing:oil","minecraft:flowing_water")); h.assertTrue(!ClusterOutputs.hasWorldOil(),"Unknown and flowing-only aliases cannot enable a source field");
            ChemicalRules.WORLD_OILS.set(List.of("minecraft:lava","water","minecraft:water")); h.assertTrue(ClusterOutputs.worldOil()==Fluids.LAVA,"First block-backed configured source wins");
            ChemicalRules.WORLD_OILS.set(List.of("water","minecraft:lava")); h.assertTrue(ClusterOutputs.worldOil()==Fluids.WATER,"Original short names and configured order work");
            var entries=ClusterOutputs.entries("oil"); h.assertValueEqual(entries.size(),1,"Only one original output, no duplicated aliases"); h.assertTrue(entries.getFirst().fluid().is(Fluids.WATER),"Drill agrees with world selection"); h.assertValueEqual(entries.getFirst().fluid().getAmount(),1000,"One source bucket"); h.assertValueEqual(entries.getFirst().weight(),10,"Original oil weight"); h.succeed();
        } finally { ChemicalRules.WORLD_OILS.set(world); ChemicalRules.OILS.set(chemical); }
    }
    private static void blockless(GameTestHelper h) {
        var world=ChemicalRules.WORLD_OILS.get();
        try {
            h.assertTrue(NeoForgeMod.MILK.isBound(),"Opt-in NeoForge milk fixture"); ChemicalRules.WORLD_OILS.set(List.of());
            h.assertTrue(!ClusterOutputs.hasWorldOil(),"A tagged blockless fluid supplies a drill but never world generation");
            var fallback=ClusterOutputs.entries("oil"); h.assertValueEqual(fallback.size(),1,"Dedicated worldgen_oils tag contributes its fallback"); h.assertTrue(fallback.getFirst().fluid().is(NeoForgeMod.MILK.get()),"Original no-block fallback");
            ChemicalRules.WORLD_OILS.set(List.of(NeoForgeMod.MILK.getId().toString(),"minecraft:water")); h.assertTrue(ClusterOutputs.worldOil()==Fluids.WATER && ClusterOutputs.entries("oil").getFirst().fluid().is(Fluids.WATER),"Later block-backed oil takes priority over earlier blockless oil");
            ChemicalRules.WORLD_OILS.set(List.of("minecraft:flowing_water")); h.assertTrue(!ClusterOutputs.hasWorldOil(),"Flowing alias cannot defeat blockless-only gate"); h.succeed();
        } finally { ChemicalRules.WORLD_OILS.set(world); }
    }
    private static DesertOilStructure structure(ServerLevel l) { return (DesertOilStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(DesertOilPiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c,long seed) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),seed,c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE); var decoded=(DesertOilStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow(); h.assertValueEqual(decoded.bigGrid(),64,"Native codec keeps the large-grid reservation");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,DesertOilPiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dimension:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dimension,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(96,32),0,l,b->true).isValid(),"Overworld dimension guard");
        var previous=ChemicalRules.WORLD_OILS.get();
        try { ChemicalRules.WORLD_OILS.set(List.of()); for(int n=-10;n<=10;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(96,32*n),0)).isEmpty(),"No generated lava substitute when oil is absent");
            ChemicalRules.WORLD_OILS.set(List.of("minecraft:water")); for(int n=-4;n<=4;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(64,64*n),0)).isEmpty(),"Large sites remain reserved");
        } finally { ChemicalRules.WORLD_OILS.set(previous); } h.succeed();
    }
    private static ChunkPos candidate(ServerLevel l,int sign) {
        var s=structure(l); for(int n=257;n<=4096;n++) { var pos=new ChunkPos(sign*96,sign*32*n); if(s.findGenerationPoint(context(l,pos,l.getSeed())).isPresent()) return pos; }
        throw new IllegalStateException("No desert oil candidate in source grid search");
    }
    private static void natural(GameTestHelper h,int sign) {
        var world=ChemicalRules.WORLD_OILS.get(); boolean enabled=LocationConfig.ENABLED.get(),ores=LocationConfig.ORE_CLUSTERS.get();
        try {
            ChemicalRules.WORLD_OILS.set(List.of("minecraft:water")); LocationConfig.ENABLED.set(true); LocationConfig.ORE_CLUSTERS.set(true);
            var l=h.getLevel(); var s=structure(l); var chosen=candidate(l,sign);
            ChemicalRules.WORLD_OILS.set(List.of()); h.assertTrue(s.findGenerationPoint(context(l,chosen,l.getSeed())).isEmpty(),"Actual eligible site is disabled without world oil"); ChemicalRules.WORLD_OILS.set(List.of("minecraft:water"));
            LocationConfig.ORE_CLUSTERS.set(false); h.assertTrue(s.findGenerationPoint(context(l,chosen,l.getSeed())).isEmpty(),"Actual eligible site respects ore toggle"); LocationConfig.ORE_CLUSTERS.set(true);
            LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,chosen,l.getSeed())).isEmpty(),"Actual eligible site respects global toggle"); LocationConfig.ENABLED.set(true);
            var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Native desert oil start saved"); var p=(DesertOilPiece)start.getPieces().getFirst(); load(l,p); var b=p.getBoundingBox();
            for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++) for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++) l.getChunk(x,z);
            for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) l.getChunk(x,z).postProcessGeneration(l);
            verify(h,l,p); h.assertValueEqual(p.oil(),Identifier.parse("minecraft:water"),"Test provider identity saved in real world piece");
            for(var id:List.of(PoliceStationPiece.TEMPLATE,SurvivorHideoutPiece.TEMPLATE,MeteorPiece.TEMPLATE,OreSpikePiece.TEMPLATE,TGContent.id("alienbug_nest"))) { var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"All five other medium candidates remain exclusive"); }
            var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,DesertOilPiece.TEMPLATE)); var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(5,4,5),0,false);
            h.assertTrue(found!=null,"Native locate finds the conditional field"); h.assertValueEqual(ChunkPos.containing(found.getFirst()),chosen,"Locate identifies the original start chunk");
            var before=snapshot(l,p); var owners=cells(p,NpcSpawnerContent.SOLDIER_BLOCK.get()).stream().map(c->((NpcSpawnerBlockEntity)l.getBlockEntity(c.pos())).instance()).toList(); l.getChunk(chosen.x(),chosen.z());
            h.assertValueEqual(snapshot(l,p),before,"Ready chunk lookup cannot reroll petroleum or clusters"); h.assertValueEqual(cells(p,NpcSpawnerContent.SOLDIER_BLOCK.get()).stream().map(c->((NpcSpawnerBlockEntity)l.getBlockEntity(c.pos())).instance()).toList(),owners,"Guard owners are not recreated");
            if(sign>0) drill(h,l,p);
            com.mojang.logging.LogUtils.getLogger().info("Techguns natural DesertOilCluster: chunk={}, origin={}, rotation={}, oil={}, mixtureSeed={}, guards=4, drill={}",chosen,p.templatePosition(),p.getRotation(),p.oil(),p.mixtureSeed(),sign>0); h.succeed();
        } finally { ChemicalRules.WORLD_OILS.set(world); LocationConfig.ENABLED.set(enabled); LocationConfig.ORE_CLUSTERS.set(ores); }
    }
    private static void drill(GameTestHelper h,ServerLevel l,DesertOilPiece p) {
        var target=cells(p,Blocks.STRUCTURE_BLOCK).stream().filter(c->marker(c).equals("techguns:desert_oil_cluster")).min(Comparator.comparingInt(c->c.pos().getY())).orElseThrow().pos();
        var origin=target.west(2); l.setBlock(origin,OreDrillContent.BLOCKS.get("controller").get().defaultBlockState(),3); l.setBlock(origin.east(),OreDrillContent.BLOCKS.get("rod").get().defaultBlockState(),3);
        var machine=(OreDrillBlockEntity)l.getBlockEntity(origin); var player=WeaponGameTests.player(h); player.setPos(Vec3.atCenterOf(origin).add(0,2,0));
        h.assertTrue(machine.form(player),"Real drill forms against naturally generated oil cluster"); machine.setItem(0,TGContent.MATERIALS.get("oredrillsmall_obsidiansteel").toStack()); machine.tanks().set(0,FluidResource.of(Fluids.LAVA),1000);
        for(int tick=0;tick<2001;tick++) OreDrillBlockEntity.tick(l,origin,machine.getBlockState(),machine);
        h.assertValueEqual(machine.data.get(3),96,"Original oil power multiplier"); var tag=machine.saveWithFullMetadata(l.registryAccess()); var state=machine.getBlockState(); l.removeBlockEntity(origin);
        ChemicalRules.WORLD_OILS.set(List.of("minecraft:lava")); machine=(OreDrillBlockEntity)BlockEntity.loadStatic(origin,state,tag,l.registryAccess()); l.setBlockEntity(machine);
        for(int tick=0;tick<4000;tick++) OreDrillBlockEntity.tick(l,origin,machine.getBlockState(),machine);
        h.assertTrue(machine.tanks().stack(1).is(Fluids.WATER),"Saved operation retains selected oil after provider preference changes"); h.assertValueEqual(machine.tanks().stack(1).getAmount(),1000,"One original bucket extracted from actual generated cluster");
        h.assertValueEqual(machine.data.get(4),14000,"Exactly six thousand fuel ticks paid"); h.assertTrue(l.getBlockState(target).is(cluster()),"Infinite source survives production");
        for(int slot=2;slot<11;slot++) h.assertTrue(machine.getItem(slot).isEmpty(),"Fluid output does not duplicate an item reward");
    }
    private static void selection(GameTestHelper h) {
        var world=ChemicalRules.WORLD_OILS.get(); boolean ores=LocationConfig.ORE_CLUSTERS.get();
        try {
            ChemicalRules.WORLD_OILS.set(List.of("minecraft:water")); LocationConfig.ORE_CLUSTERS.set(true); var l=h.getLevel(); var r=l.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            var sites=new ArrayList<ChunkPos>(); for(int sign:new int[]{1,-1}) { var center=candidate(l,sign); for(int dz=-16;dz<=16;dz+=4) sites.add(new ChunkPos(center.x(),center.z()+32*dz)); }
            List<java.util.function.Function<Structure.GenerationContext,Optional<Structure.GenerationStub>>> candidates=List.of(structure(l)::findGenerationPoint,
                    ((PoliceStationStructure)r.getValue(PoliceStationPiece.TEMPLATE))::findGenerationPoint,((SurvivorHideoutStructure)r.getValue(SurvivorHideoutPiece.TEMPLATE))::findGenerationPoint,
                    ((OreSpikeStructure)r.getValue(OreSpikePiece.TEMPLATE))::findGenerationPoint,((MeteorStructure)r.getValue(MeteorPiece.TEMPLATE))::findGenerationPoint,((BugNestStructure)r.getValue(TGContent.id("alienbug_nest")))::findGenerationPoint);
            for(boolean provider:List.of(false,true)) for(boolean clusters:List.of(false,true)) {
                ChemicalRules.WORLD_OILS.set(provider?List.of("minecraft:water"):List.of()); LocationConfig.ORE_CLUSTERS.set(clusters); int hits=0;
                for(long seed:new long[]{l.getSeed(),1,42}) for(var pos:sites) { int count=0;
                    for(int i=0;i<candidates.size();i++) if(candidates.get(i).apply(context(l,pos,seed)).isPresent()) { count++; if(i==0) hits++; }
                    h.assertTrue(count<=1,"Six native medium candidates share one mutually exclusive source roll");
                }
                h.assertTrue(provider && clusters?hits>0:hits==0,"Oil field appears only with both prerequisites");
            }
            h.succeed();
        } finally { ChemicalRules.WORLD_OILS.set(world); LocationConfig.ORE_CLUSTERS.set(ores); }
    }
    private DesertOilGameTests() {}
}
