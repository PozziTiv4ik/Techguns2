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
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.feature.DeltaFeature;
import net.minecraft.world.level.levelgen.feature.BasaltColumnsFeature;
import net.minecraft.world.level.levelgen.feature.configurations.DeltaFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ColumnFeatureConfiguration;
import techguns.modern.*;
import techguns.modern.npc.CyberDemon;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.NetherMetalContent;
import techguns.modern.world.OreClusterContent;
import techguns.modern.world.structure.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

final class MediumAltarGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int turn=0;turn<4;turn++) {
            int t=turn; r.register("location_medium_altar_rotation_"+turn,()->h->rotation(h,t));
            r.register("location_medium_altar_reverse_chunk_save_"+turn,()->h->clipping(h,t));
        }
        r.register("location_medium_altar_sixteen_foundation_columns",()->MediumAltarGameTests::foundation);
        r.register("location_medium_altar_four_finite_cyberdemon_encounters",()->MediumAltarGameTests::encounters);
        r.register("location_medium_altar_registry_and_toggles",()->MediumAltarGameTests::registry);
        r.register("location_medium_altar_native_delta_protection",()->MediumAltarGameTests::delta);
        r.register("location_medium_altar_native_basalt_protection",()->MediumAltarGameTests::basalt);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_medium_altar_positive_natural_chunks",()->h->natural(h,1));
            r.register("structure_medium_altar_negative_natural_chunks",()->h->natural(h,-1));
        }
    }
    private static List<Block> palette() {
        var list=new ArrayList<Block>();
        for(var name:List.of("panel","border_red","grate2","plate_red","grate1","plate_black","border_lava")) list.add(NetherMetalContent.BLOCKS.get("nethermetal_"+name).get());
        list.addAll(List.of(Blocks.NETHER_BRICKS,Blocks.NETHER_BRICK_STAIRS,Blocks.NETHER_BRICK_FENCE,Blocks.AIR,NpcSpawnerContent.BLOCK.get())); return list;
    }
    private static NetherMediumAltarPiece piece(GameTestHelper h,int slot,int turn) { return new NetherMediumAltarPiece(h.getLevel().getServer().getStructureManager(),new BlockPos(1600000+slot*64,140,-1600000),turn); }
    private static List<StructureTemplate.StructureBlockInfo> cells(NetherMediumAltarPiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static void load(ServerLevel l,NetherMediumAltarPiece p) { var b=p.getBoundingBox(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) l.getChunk(x,z); }
    private static void place(GameTestHelper h,NetherMediumAltarPiece p,BoundingBox clip) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(78),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,NetherMediumAltarPiece p) { var b=p.getBoundingBox(); var out=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) out.put(pos.immutable(),l.getBlockState(pos)); return out; }
    private static void fill(GameTestHelper h,NetherMediumAltarPiece p,Block block) { load(h.getLevel(),p); for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,block.defaultBlockState(),2); }
    private static void verify(GameTestHelper h,ServerLevel l,NetherMediumAltarPiece p) {
        int count=0;
        for(var block:palette()) for(var c:cells(p,block)) {
            count++; var expected=c.state();
            if(block==Blocks.NETHER_BRICK_STAIRS || block==Blocks.NETHER_BRICK_FENCE) expected=Block.updateFromNeighbourShapes(expected,l,c.pos());
            h.assertValueEqual(l.getBlockState(c.pos()),expected,"Exact medium altar cell/facing/connection at "+c.pos());
        }
        h.assertValueEqual(count,1668,"All source cells including 932 air cells");
        var holes=cells(p,NpcSpawnerContent.BLOCK.get()); h.assertValueEqual(holes.size(),4,"Four separate source encounters");
        for(var cell:holes) {
            var be=(NpcSpawnerBlockEntity)l.getBlockEntity(cell.pos()); h.assertTrue(be!=null,"Placed source tile");
            h.assertValueEqual(be.remaining(),3,"Three actual deaths per hole"); h.assertValueEqual(be.maximum(),1,"One live CyberDemon per hole");
            h.assertValueEqual(be.interval(),200,"Source interval"); h.assertValueEqual(be.range(),1d,"Source range");
            h.assertValueEqual(be.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("cyberdemon"),1)),"Only source CyberDemon entry");
            h.assertTrue(be.weaponOverride().isEmpty(),"No invented weapon override");
        }
    }
    private static void rotation(GameTestHelper h,int turn) {
        var p=piece(h,turn,turn); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox()); verify(h,h.getLevel(),p);
        h.assertValueEqual(p.template().getSize(),new Vec3i(16,8,16),"Actual scan height eight, source declared nine");
        var b=p.getBoundingBox(); int xShift=turn>=2?1:0,zShift=turn==1 || turn==2?1:0;
        h.assertValueEqual(b.minX(),p.templatePosition().getX()+xShift,"Even-width pivot retains shifted rotated footprint");
        h.assertValueEqual(b.minZ(),p.templatePosition().getZ()+zShift,"Rotated scan keeps original 0..15 versus 1..16 bounds"); h.succeed();
    }
    private static void clipping(GameTestHelper h,int turn) {
        var p=piece(h,4+turn,turn); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox()); var expected=snapshot(h.getLevel(),p); fill(h,p,Blocks.AIR);
        var b=p.getBoundingBox(); var clips=new ArrayList<BoundingBox>();
        for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) clips.add(new BoundingBox(x*16,b.minY(),z*16,x*16+15,b.maxY(),z*16+15));
        Collections.reverse(clips); var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel());
        for(var clip:clips) { place(h,p,clip); p=(NetherMediumAltarPiece)LocationContent.MEDIUM_ALTAR_PIECE.get().load(ctx,p.createTag(ctx)); }
        h.assertValueEqual(snapshot(h.getLevel(),p),expected,"Opposite native chunk order with saves keeps stairs, railings, foundation and spawners"); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,8,1); fill(h,p,Blocks.AIR); var block=NetherMetalContent.BLOCKS.get("nethermetal_plate_black").get();
        var bottoms=cells(p,block).stream().filter(c->c.pos().getY()==p.templatePosition().getY()).toList(); h.assertValueEqual(bottoms.size(),16,"Sixteen source black-plate supports");
        var blocked=bottoms.getFirst().pos(); for(int depth:List.of(2,4,5)) h.getLevel().setBlock(blocked.below(depth),Blocks.OBSIDIAN.defaultBlockState(),2);
        for(var c:bottoms) h.getLevel().setBlock(c.pos().below(17),Blocks.AIR.defaultBlockState(),2); place(h,p,p.getBoundingBox());
        for(var c:bottoms) for(int depth=1;depth<=17;depth++) {
            var expected=depth==17?Blocks.AIR:block;
            if(c.pos().equals(blocked)) expected=List.of(2,4,5).contains(depth)?Blocks.OBSIDIAN:depth>=6?Blocks.AIR:block;
            h.assertTrue(h.getLevel().getBlockState(c.pos().below(depth)).is(expected),"Source replaceable-only foundation and two-solid stop");
        }
        var columns=new HashSet<BlockPos>(); bottoms.forEach(c->columns.add(c.pos())); var b=p.getBoundingBox();
        for(int x=b.minX();x<=b.maxX();x++) for(int z=b.minZ();z<=b.maxZ();z++) {
            var pos=new BlockPos(x,p.templatePosition().getY(),z); if(!columns.contains(pos)) h.assertTrue(h.getLevel().getBlockState(pos.below()).isAir(),"Foundation is not a filled rectangle");
        } h.succeed();
    }
    private static void encounters(GameTestHelper h) {
        var p=piece(h,9,0); var l=h.getLevel(); load(l,p); place(h,p,p.getBoundingBox());
        var spawned=new LinkedHashMap<UUID,CyberDemon>();
        // Observe actual accepted spawns without depending on when a remote chunk's entity
        // section becomes visible to the UUID lookup during another GameTest batch.
        Consumer<EntityJoinLevelEvent> observe=e->{
            if(e.getLevel()==l && e.getEntity() instanceof CyberDemon npc && p.getBoundingBox().isInside(npc.blockPosition())) spawned.put(npc.getUUID(),npc);
        };
        NeoForge.EVENT_BUS.addListener(observe);
        try {
            for(var cell:cells(p,NpcSpawnerContent.BLOCK.get())) {
                var pos=cell.pos(); var be=(NpcSpawnerBlockEntity)l.getBlockEntity(pos);
                for(int deaths=0;deaths<3;deaths++) {
                    for(int tick=0;tick<400;tick++) NpcSpawnerBlockEntity.serverTick(l,pos,be.getBlockState(),be);
                    h.assertValueEqual(be.activeCount(),1,"One accepted source CyberDemon per post");
                    var npc=spawned.get(be.activeIds().iterator().next());
                    h.assertTrue(npc!=null && npc.isAlive(),"Accepted native spawn observed through EntityJoinLevelEvent"); npc.removeFreeWill();
                    h.assertValueEqual(npc.spawnerLink(),be.link(),"Each actual NPC belongs to this independent post");
                    h.assertTrue(npc.getMainHandItem().getItem() instanceof GunItem gun && gun.definition().id().equals("netherblaster"),"Source NPC equips Nether Blaster");
                    npc.hurtServer(l,l.damageSources().genericKill(),10000);
                    h.assertTrue(!npc.isAlive(),"Real CyberDemon died"); h.assertValueEqual(be.remaining(),2-deaths,"Actual death charged exactly once");
                    NpcSpawnerBlockEntity.serverTick(l,pos,be.getBlockState(),be);
                }
                h.assertTrue(l.getBlockState(pos).isAir(),"Third death removes only its own finite post");
            }
            h.assertValueEqual(spawned.size(),12,"All twelve independent native spawns observed"); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); spawned.values().forEach(npc->{ if(!npc.isRemoved()) npc.discard(); }); }
    }
    private static NetherMediumAltarStructure structure(ServerLevel l) { return (NetherMediumAltarStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherMediumAltarPiece.TEMPLATE); }
    private static void delta(GameTestHelper h) {
        var l=h.getLevel(); var pos=h.absolutePos(new BlockPos(3,0,3)).atY(180);
        for(var direction:Direction.values()) l.setBlock(pos.relative(direction),(direction==Direction.UP?Blocks.AIR:Blocks.NETHERRACK).defaultBlockState(),2);
        var protectedBlocks=new ArrayList<Block>(); NetherMetalContent.BLOCKS.values().forEach(b->protectedBlocks.add(b.get()));
        protectedBlocks.add(NpcSpawnerContent.BLOCK.get()); OreClusterContent.BLOCKS.values().forEach(b->protectedBlocks.add(b.get()));
        var feature=new DeltaFeature(DeltaFeatureConfiguration.CODEC);
        var config=new DeltaFeatureConfiguration(Blocks.LAVA.defaultBlockState(),Blocks.MAGMA_BLOCK.defaultBlockState(),ConstantInt.of(0),ConstantInt.of(0));
        for(var block:protectedBlocks) {
            l.setBlock(pos,block.defaultBlockState(),2);
            h.assertTrue(!feature.place(config,l,l.getChunkSource().getGenerator(),RandomSource.create(23),pos),"Native delta rejects TG structure block: "+block);
            h.assertTrue(l.getBlockState(pos).is(block),"Later decoration preserves original material");
        }
        l.setBlock(pos,Blocks.NETHERRACK.defaultBlockState(),2);
        h.assertTrue(feature.place(config,l,l.getChunkSource().getGenerator(),RandomSource.create(23),pos),"Native delta still generates in ordinary terrain");
        h.assertTrue(l.getBlockState(pos).is(Blocks.LAVA),"Control really generated lava"); l.setBlock(pos,Blocks.AIR.defaultBlockState(),2); h.succeed();
    }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),c,l,b->true); }
    private static void basalt(GameTestHelper h) {
        var l=h.getLevel(); var pos=h.absolutePos(new BlockPos(3,0,3)).atY(180);
        var protectedBlocks=new ArrayList<Block>(); NetherMetalContent.BLOCKS.values().forEach(b->protectedBlocks.add(b.get()));
        protectedBlocks.add(NpcSpawnerContent.BLOCK.get()); OreClusterContent.BLOCKS.values().forEach(b->protectedBlocks.add(b.get()));
        var feature=new BasaltColumnsFeature(ColumnFeatureConfiguration.CODEC); var config=new ColumnFeatureConfiguration(ConstantInt.of(1),ConstantInt.of(1));
        for(var block:protectedBlocks) {
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) { l.setBlock(pos.offset(x,0,z),block.defaultBlockState(),2); l.setBlock(pos.offset(x,1,z),Blocks.AIR.defaultBlockState(),2); }
            h.assertTrue(!feature.place(config,l,l.getChunkSource().getGenerator(),RandomSource.create(23),pos.above()),"Basalt cannot grow on source TG blocks");
            h.assertTrue(l.getBlockState(pos.above()).isAir(),"Authored air above structure remains clear");
            try {
                var find=BasaltColumnsFeature.class.getDeclaredMethod("findAir",LevelAccessor.class,BlockPos.MutableBlockPos.class,int.class); find.setAccessible(true);
                // Starting one layer below also tests the protected block in the middle of the loop.
                l.setBlock(pos.below(),Blocks.NETHERRACK.defaultBlockState(),2);
                h.assertTrue(find.invoke(null,l,pos.below().mutable(),3)==null,"Upward search stops at protected structure material");
            } catch(ReflectiveOperationException e) { throw new IllegalStateException("Cannot exercise native basalt upward search",e); }
        }
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) l.setBlock(pos.offset(x,0,z),Blocks.NETHERRACK.defaultBlockState(),2);
        h.assertTrue(feature.place(config,l,l.getChunkSource().getGenerator(),RandomSource.create(23),pos.above()),"Basalt columns still form on ordinary terrain");
        boolean basalt=false; for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) basalt|=l.getBlockState(pos.offset(x,1,z)).is(Blocks.BASALT);
        h.assertTrue(basalt,"Control really placed a basalt column"); h.succeed();
    }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var restored=(NetherMediumAltarStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow(); h.assertValueEqual(restored.bigGrid(),64,"Reserved big grid survives native codec");
        var set=l.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(NetherMediumAltarPiece.TEMPLATE); var placement=(RandomSpreadStructurePlacement)set.placement();
        h.assertValueEqual(placement.spacing(),32,"Medium grid"); h.assertValueEqual(placement.separation(),31,"No placement jitter");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherMediumAltarPiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dim:List.of(Level.OVERWORLD,Level.END)) h.assertTrue(!s.generate(holder,dim,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(32,32),0,l,b->true).isValid(),"Explicit Nether guard");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32))).isEmpty(),"Global structure switch"); }
        finally { LocationConfig.ENABLED.set(enabled); }
        for(int n=-5;n<=5;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(64,64*n))).isEmpty(),"Big sites reserved"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel().getServer().getLevel(Level.NETHER); var s=structure(l); ChunkPos chosen=null;
        for(int n=257;n<=8192;n++) { var c=new ChunkPos(sign*32,sign*32*n); if(s.findGenerationPoint(context(l,c)).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Original rare medium-altar ticket finds a valid native cave"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s);
        h.assertTrue(start!=null && start.isValid(),"Native medium altar start persisted"); var p=(NetherMediumAltarPiece)start.getPieces().getFirst(); load(l,p);
        // Match ChunkMap.prepareTickingChunk: finish the surrounding FULL chunks before
        // applying queued native shape updates. A remote getChunk alone does not activate it.
        var box=p.getBoundingBox();
        for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++) for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++) l.getChunk(x,z);
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z).postProcessGeneration(l);
        verify(h,l,p);
        for(var id:List.of(NetherGhastPiece.TEMPLATE,NetherCastlePiece.TEMPLATE,NetherAltarPiece.TEMPLATE,NetherLootPiece.TEMPLATE,NetherAcidPiece.TEMPLATE,NetherSoulPiece.TEMPLATE,NetherClusterPiece.TEMPLATE)) {
            var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"Castle and small structures cannot replace this medium ticket");
        }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherMediumAltarPiece.TEMPLATE));
        var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(8,4,8),0,false);
        h.assertTrue(found!=null,"Native locate finds medium altar"); h.assertValueEqual(new ChunkPos(found.getFirst().getX()>>4,found.getFirst().getZ()>>4),chosen,"Locate returns saved start chunk");
        var ids=cells(p,NpcSpawnerContent.BLOCK.get()).stream().map(c->((NpcSpawnerBlockEntity)l.getBlockEntity(c.pos())).instance()).toList(); var before=snapshot(l,p);
        l.getChunk(chosen.x(),chosen.z()); h.assertValueEqual(snapshot(l,p),before,"Already generated altar is unchanged");
        h.assertValueEqual(cells(p,NpcSpawnerContent.BLOCK.get()).stream().map(c->((NpcSpawnerBlockEntity)l.getBlockEntity(c.pos())).instance()).toList(),ids,"Guard instances retained");
        boolean ores=LocationConfig.ORE_CLUSTERS.get(); try {
            LocationConfig.ORE_CLUSTERS.set(false); boolean foundWithoutOres=false;
            for(int n=257;n<=512 && !foundWithoutOres;n++) foundWithoutOres=s.findGenerationPoint(context(l,new ChunkPos(sign*32,sign*32*n))).isPresent();
            h.assertTrue(foundWithoutOres,"Altar still selected with ore-cluster tickets removed");
        } finally { LocationConfig.ORE_CLUSTERS.set(ores); }
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural NetherAltarMedium: chunk={}, origin={}, rotation={}, guards={}",chosen,p.templatePosition(),p.getRotation(),ids.size()); h.succeed();
    }
    private MediumAltarGameTests() {}
}
