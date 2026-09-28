package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.*;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.StructureGrid;
import techguns.core.StructureGrid.Size;
import techguns.modern.world.structure.*;

final class StructureGridGameTests {
    private static final StructureGrid CUSTOM=new StructureGrid(7,11,19),CHANGED=new StructureGrid(9,13,23);
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("grid_native_config_ranges_and_restart",()->StructureGridGameTests::config);
        r.register("grid_all_native_sets_and_codec",()->StructureGridGameTests::registry);
        r.register("grid_native_candidates_priorities_and_limits",()->StructureGridGameTests::candidates);
        r.register("grid_structure_probe_rejects_off_grid_and_reserved",()->StructureGridGameTests::probes);
        if(Boolean.getBoolean("techguns.gridWorldTest")) {
            r.register("structure_grid_small_overworld_natural_chunks",()->h->natural(h,Level.OVERWORLD,Size.SMALL,false,0));
            r.register("structure_grid_medium_overworld_natural_chunks",()->h->natural(h,Level.OVERWORLD,Size.MEDIUM,false,1));
            r.register("structure_grid_big_land_natural_chunks",()->h->natural(h,Level.OVERWORLD,Size.BIG,false,2));
            r.register("structure_grid_big_water_natural_chunks",()->h->natural(h,Level.OVERWORLD,Size.BIG,true,3));
            r.register("structure_grid_small_nether_natural_chunks",()->h->natural(h,Level.NETHER,Size.SMALL,false,4));
            r.register("structure_grid_medium_nether_natural_chunks",()->h->natural(h,Level.NETHER,Size.MEDIUM,false,5));
        }
    }
    private static final class Settings implements AutoCloseable {
        private final StructureGrid before=LocationConfig.grid();
        private final boolean enabled=LocationConfig.ENABLED.get(),ores=LocationConfig.ORE_CLUSTERS.get();
        Settings(StructureGrid grid) {set(grid);LocationConfig.ENABLED.set(true);}
        static void set(StructureGrid grid) {
            LocationConfig.SMALL.set(grid.small());LocationConfig.MEDIUM.set(grid.medium());LocationConfig.BIG.set(grid.big());
            // Test-only simulation of the cache reset performed when a world restarts.
            LocationConfig.SMALL.clearCache();LocationConfig.MEDIUM.clearCache();LocationConfig.BIG.clearCache();
        }
        @Override public void close() {set(before);LocationConfig.ENABLED.set(enabled);LocationConfig.ORE_CLUSTERS.set(ores);}
    }
    private static List<Holder.Reference<StructureSet>> sets(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).listElements().filter(h->h.key().identifier().getNamespace().equals("techguns")).toList();
    }
    private static void config(GameTestHelper h) {
        var values=List.of(LocationConfig.SMALL,LocationConfig.MEDIUM,LocationConfig.BIG);int i=0;
        for(var size:Size.values()) {
            var value=values.get(i++);var spec=value.getSpec();
            h.assertValueEqual(value.getDefault(),size.defaultInterval(),"Original default interval");
            h.assertValueEqual(spec.restartType(),ModConfigSpec.RestartType.WORLD,"Changing the TOML waits for world/server restart");
            h.assertTrue(spec.test(size.minimum())&&spec.test(100000),"Both original bounds accepted by the actual NeoForge config");
            h.assertTrue(!spec.test(size.minimum()-1)&&!spec.test(100001),"Out-of-range config rejected");
            int before=value.get(),changed=before==size.minimum()?size.minimum()+1:size.minimum();
            try {
                value.set(changed);h.assertValueEqual(value.get(),before,"Editing config cannot move the grid in a running world");
                value.clearCache();h.assertValueEqual(value.get(),changed,"World restart applies the edited interval");
            } finally {value.set(before);value.clearCache();}
        }
        h.succeed();
    }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel();var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE);var counts=new EnumMap<Size,Integer>(Size.class);
        for(var holder:sets(l)) {
            var set=holder.value();h.assertTrue(set.placement() instanceof StructureGridPlacement,"All Techguns sets use the configurable placement");
            var p=(StructureGridPlacement)set.placement();counts.merge(p.size(),1,Integer::sum);
            h.assertValueEqual(set.structures().size(),1,"Separate native IDs preserve the original shared candidate roll");
            h.assertValueEqual(set.structures().getFirst().weight(),1,"No invented native reweighting");
            var encoded=StructurePlacement.CODEC.encodeStart(ops,p).getOrThrow().getAsJsonObject();
            var restored=(StructureGridPlacement)StructurePlacement.CODEC.parse(ops,encoded).getOrThrow();
            h.assertValueEqual(restored.size(),p.size(),"Placement size codec roundtrip");
            h.assertTrue(!encoded.has("spacing")&&!encoded.has("separation"),"No 4096-limited copied interval in the placement data");
            var structure=set.structures().getFirst().structure().value();var old=Structure.DIRECT_CODEC.encodeStart(ops,structure).getOrThrow().getAsJsonObject();
            h.assertTrue(!old.has("reserved_medium_grid")&&!old.has("reserved_big_grid"),"Structure codecs no longer own separate reservations");
            old.addProperty("reserved_medium_grid",32);old.addProperty("reserved_big_grid",64);
            h.assertTrue(Structure.DIRECT_CODEC.parse(ops,old).result().isPresent(),"Older structure JSON still loads without overriding the shared config");
            var invalid=encoded.deepCopy();invalid.addProperty("size","huge");h.assertTrue(StructurePlacement.CODEC.parse(ops,invalid).error().isPresent(),"Unknown size is an error");
        }
        h.assertValueEqual(counts,Map.of(Size.SMALL,9,Size.MEDIUM,9,Size.BIG,3),"Every active source candidate migrated");h.succeed();
    }
    private static boolean source(StructureGrid g,Size size,int x,int z) {
        Size selected=x%g.big()==0&&z%g.big()==0?Size.BIG:x%g.medium()==0&&z%g.medium()==0?Size.MEDIUM:x%g.small()==0&&z%g.small()==0?Size.SMALL:null;
        return selected==size;
    }
    private static void candidates(GameTestHelper h) {
        var l=h.getLevel();var state=l.getChunkSource().getGeneratorState();var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        try(var settings=new Settings(StructureGrid.DEFAULT)) {
            for(var g:List.of(StructureGrid.DEFAULT,CUSTOM,new StructureGrid(4,8,16),new StructureGrid(16,16,16),new StructureGrid(4097,50000,100000))) {
                Settings.set(g);
                for(var holder:sets(l)) {
                    var p=(StructureGridPlacement)holder.value().placement();int interval=g.interval(p.size());
                    h.assertValueEqual(p.spacing(),interval,"Native locate reads the configured interval");h.assertValueEqual(p.separation(),interval-1,"No random offset");
                    var vanilla=new RandomSpreadStructurePlacement(interval,interval-1,RandomSpreadType.LINEAR,1337262);
                    for(long seed:new long[]{0,1,-975318642L}) for(int x:new int[]{-100001,-65,-1,0,19,131,100001}) for(int z:new int[]{-117,-1,0,19,227})
                        h.assertValueEqual(p.getPotentialStructureChunk(seed,x,z),vanilla.getPotentialStructureChunk(seed,x,z),"Actual search sectors, all signs and seeds, including interval 100000");
                    for(int x:new int[]{-interval,0,interval,2*interval,3*interval,77,133}) for(int z:new int[]{-interval,0,interval,3*interval,77,209}) {
                        boolean expected=source(g,p.size(),x,z);
                        h.assertValueEqual(p.isStructureChunk(state,x,z),expected,"Native generation shares BIG/MEDIUM/SMALL reservations");
                        h.assertValueEqual(p.applyAdditionalChunkRestrictions(x,z,l.getSeed()),expected,"Locate applies reservations before its terrain cache");
                    }
                    LocationConfig.ENABLED.set(false);h.assertTrue(!p.isStructureChunk(state,0,0)&&!p.applyAdditionalChunkRestrictions(interval,interval,0),"Disabled blocks new generation and unloaded-site probes");LocationConfig.ENABLED.set(true);
                    var json=StructurePlacement.CODEC.encodeStart(ops,p).getOrThrow().getAsJsonObject();json.addProperty("frequency",0);
                    var never=StructurePlacement.CODEC.parse(ops,json).getOrThrow();h.assertTrue(!never.isStructureChunk(state,interval,interval),"Native frequency restriction is still honored");
                }
            }
            Settings.set(new StructureGrid(4,8,25000));var big=(StructureGridPlacement)sets(l).stream().map(Holder::value).map(StructureSet::placement).filter(p->((StructureGridPlacement)p).size()==Size.BIG).findFirst().orElseThrow();
            h.assertTrue(big.applyAdditionalChunkRestrictions(-1875000,0,0),"Negative world boundary remains valid");
            h.assertTrue(!big.applyAdditionalChunkRestrictions(1875000,0,0)&&!big.applyAdditionalChunkRestrictions(-1900000,0,0),"Large intervals never generate beyond the hard world boundary");
        }
        h.succeed();
    }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c) {
        var g=l.getChunkSource().getGenerator();return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),c,l,b->true);
    }
    private static void probes(GameTestHelper h) {
        var l=h.getLevel();
        try(var settings=new Settings(CUSTOM)) {
            for(var holder:sets(l)) {
                var p=(StructureGridPlacement)holder.value().placement();var structure=holder.value().structures().getFirst().structure().value();
                h.assertTrue(structure.findValidGenerationPoint(context(l,new ChunkPos(1,1))).isEmpty(),"Bypassing placement does not bypass configured grid");
                if(p.size()!=Size.BIG) h.assertTrue(structure.findValidGenerationPoint(context(l,new ChunkPos(7*11*19,-7*11*19))).isEmpty(),"BIG reserves its intersections even without a viable big candidate");
            }
        }
        h.succeed();
    }
    private record Site(Holder<Structure> structure,StructureGridPlacement placement,ChunkPos chunk,StructureStart start) {}
    private static boolean nether(Structure s) {return s instanceof SmallNetherStructure||s instanceof MediumNetherStructure;}
    private static Site site(GameTestHelper h,ServerLevel l,Size size,boolean water,int slot,int phase) {
        var grid=LocationConfig.grid();int interval=grid.interval(size),sign=l.getSeed()<0?-1:1;
        var candidates=sets(l).stream().map(Holder::value).filter(s->((StructureGridPlacement)s.placement()).size()==size)
            .filter(s->nether(s.structures().getFirst().structure().value())==l.dimension().equals(Level.NETHER))
            .filter(s->size!=Size.BIG||(s.structures().getFirst().structure().value() instanceof AircraftCarrierStructure)==water).toList();
        Site result=null;
        for(int n=513;n<1300&&result==null;n++) {
            var c=new ChunkPos(sign*interval*(17+slot*6+phase*48),sign*interval*n);
            if(!source(grid,size,c.x(),c.z())) continue;
            for(var set:candidates) {
                var s=set.structures().getFirst().structure();
                if(s.value().findValidGenerationPoint(context(l,c)).isEmpty()) continue;
                var chunk=l.getChunk(c.x(),c.z());var start=chunk.getStartForStructure(s.value());
                h.assertTrue(start!=null&&start.isValid(),"Configured candidate produces a real native start at "+c);
                result=new Site(s,(StructureGridPlacement)set.placement(),c,start);break;
            }
        }
        h.assertTrue(result!=null,"Find a viable "+size+" site with seed "+l.getSeed());
        var c=result.chunk();int starts=0;var chunk=l.getChunk(c.x(),c.z());
        for(var set:sets(l)) for(var entry:set.value().structures()) {var start=chunk.getStartForStructure(entry.structure().value());if(start!=null&&start.isValid()) starts++;}
        h.assertValueEqual(starts,1,"Exactly one source size/candidate at the configured node");
        var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(result.structure()),c.getWorldPosition(),0,false);
        h.assertTrue(found!=null&&ChunkPos.containing(found.getFirst()).equals(c),"Native locate finds actual new-grid start, without the old fixed spacing");
        h.assertTrue(c.x()%(size==Size.SMALL?16:size==Size.MEDIUM?32:64)!=0||c.z()%(size==Size.SMALL?16:size==Size.MEDIUM?32:64)!=0,"Fixture exercises a node outside the old fixed grid");
        com.mojang.logging.LogUtils.getLogger().info("Techguns configured grid: seed={}, dimension={}, intervals={}/{}/{}, group={}, water={}, phase={}, chunk={}, structure={}",l.getSeed(),l.dimension().identifier(),grid.small(),grid.medium(),grid.big(),size,water,phase,c,result.structure().unwrapKey().orElseThrow().identifier());
        return result;
    }
    private static void natural(GameTestHelper h,ResourceKey<Level> dimension,Size size,boolean water,int slot) {
        var l=h.getLevel().getServer().getLevel(dimension);h.assertValueEqual(l.getSeed(),Long.getLong("techguns.worldgenSeed",0),"Real dedicated world uses the requested test seed");
        try(var settings=new Settings(CUSTOM)) {
            var old=site(h,l,size,water,slot,0);var context=StructurePieceSerializationContext.fromLevel(l);
            // Keep the edit inside the already generated start chunk: loading another part of a
            // large structure legitimately advances its PlacedChunks, independently of config.
            var marker=new BlockPos(old.chunk().getMinBlockX()+8,old.start().getBoundingBox().getCenter().getY(),old.chunk().getMinBlockZ()+8);
            l.setBlock(marker,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
            l.getChunkSource().save(true);
            var saved=old.start().createTag(context,old.chunk());
            Settings.set(CHANGED);LocationConfig.ENABLED.set(false);
            var chunk=l.getChunk(old.chunk().x(),old.chunk().z());
            l.getChunkSource().getGenerator().createStructures(l.registryAccess(),l.getChunkSource().getGeneratorState(),l.structureManager(),chunk,l.getServer().getStructureManager(),l.dimension());
            h.assertTrue(chunk.getStartForStructure(old.structure().value()).createTag(context,old.chunk()).equals(saved),"Changed config/disabled generation leaves existing start and piece data intact");
            var restored=StructureStart.loadStaticStart(context,saved,l.getSeed());
            h.assertTrue(restored!=null&&restored.isValid(),"Saved start reloads after changing intervals");h.assertTrue(restored.createTag(context,old.chunk()).equals(saved),"Serialized old start is independent of new grid");
            var generator=l.getChunkSource().getGenerator();
            var reopenedCheck=new StructureCheck(l.getChunkSource().chunkScanner(),l.registryAccess(),l.getServer().getStructureManager(),l.dimension(),
                generator,l.getChunkSource().randomState(),l,generator.getBiomeSource(),l.getSeed(),net.minecraft.util.datafix.DataFixers.getDataFixer());
            h.assertValueEqual(reopenedCheck.checkStart(old.chunk(),old.structure().value(),old.placement(),false),StructureCheckResult.START_PRESENT,"Fresh native check reads the old start from disk even when new generation is disabled");
            h.assertTrue(l.getBlockState(marker).is(Blocks.DIAMOND_BLOCK),"Existing player edits are not regenerated");
            LocationConfig.ENABLED.set(true);site(h,l,size,water,slot,1);
            boolean ores=LocationConfig.ORE_CLUSTERS.get();LocationConfig.ORE_CLUSTERS.set(!ores);
            h.assertTrue(old.placement().isStructureChunk(l.getChunkSource().getGeneratorState(),CHANGED.interval(size),CHANGED.interval(size)),"Ore toggle does not alter the source grids");
            LocationConfig.ORE_CLUSTERS.set(ores);
            if(dimension.equals(Level.NETHER)) {
                var reserved=new ChunkPos(CHANGED.big()*13,CHANGED.big()*17);
                for(var set:sets(l)) {
                    var p=(StructureGridPlacement)set.value().placement();if(p.size()!=Size.BIG) h.assertTrue(!p.isStructureChunk(l.getChunkSource().getGeneratorState(),reserved.x(),reserved.z()),"Empty Nether BIG pool still reserves its node");
                }
            }
        }
        h.succeed();
    }
    private StructureGridGameTests() {}
}
