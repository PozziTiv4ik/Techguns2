package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.machine.camo.*;
import techguns.modern.npc.CyberDemon;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.NetherMetalContent;
import techguns.modern.world.structure.*;

final class LocationGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var v:NetherMetal.ALL) r.register("location_metal_"+v.metadata(),()->h->metal(h,v));
        r.register("location_metal_original_craft",()->LocationGameTests::recipe);
        r.register("location_metal_camo_bench_ten_variants",()->LocationGameTests::camo);
        r.register("location_altar_template_and_registry_codecs",()->LocationGameTests::registry);
        for(int i=0;i<4;i++) { int turn=i; r.register("location_altar_rotation_"+i,()->h->rotation(h,turn)); }
        r.register("location_altar_foundation_depth_and_two_solids",()->LocationGameTests::foundation);
        r.register("location_altar_clipping_and_saved_piece",()->LocationGameTests::clipping);
        r.register("location_altar_cyberdemon_death_budget",()->LocationGameTests::spawner);
        if(Boolean.getBoolean("techguns.worldgenTest")) r.register("structure_natural_chunks",()->LocationGameTests::natural);
    }
    private static void metal(GameTestHelper h,NetherMetal.Variant v) {
        var block=NetherMetalContent.BLOCKS.get(v.id()).get(); var pos=h.absolutePos(new BlockPos(4,2,4)); var s=block.defaultBlockState();
        h.assertValueEqual(s.getDestroySpeed(h.getLevel(),pos),8f,"Original hardness"); h.assertValueEqual(block.getExplosionResistance(),8f,"Original setHardness-derived resistance");
        h.assertValueEqual(s.getLightEmission(h.getLevel(),pos),v.light(),"Only border_lava emits fifteen");
        var p=WeaponGameTests.player(h); p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); h.assertTrue(!p.hasCorrectToolForDrops(s),"Metal needs a tool");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WOODEN_PICKAXE)); h.assertTrue(p.hasCorrectToolForDrops(s),"No invented stone/iron mining tier");
        var drops=Block.getDrops(s,h.getLevel(),pos,null,p,p.getMainHandItem()); h.assertValueEqual(drops.size(),1,"One source block drop"); h.assertTrue(drops.getFirst().is(block.asItem()),"Variant retained on mining");
        h.assertTrue(s.isCollisionShapeFullBlock(h.getLevel(),pos),"Full cube including glowing variant"); h.succeed();
    }
    private static void recipe(GameTestHelper h) {
        var n=new ItemStack(Items.NETHERRACK); var s=new ItemStack(Items.STONE); var i=new ItemStack(Items.IRON_INGOT);
        var input=CraftingInput.of(3,3,List.of(n,s,n,s,i,s,n,s,n));
        var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow(); var out=recipe.value().assemble(input);
        h.assertTrue(out.is(NetherMetalContent.BLOCKS.get("nethermetal_panel").get().asItem()),"Original metadata-zero panel recipe"); h.assertValueEqual(out.getCount(),16,"Original sixteen-block yield"); h.succeed();
    }
    private static void camo(GameTestHelper h) {
        var pos=new BlockPos(4,2,4); h.setBlock(pos,CamoBenchContent.BLOCK.get()); var bench=h.getBlockEntity(pos,CamoBenchBlockEntity.class); var p=WeaponGameTests.player(h); bench.setOwner(p);
        var menu=new CamoBenchMenu(45,p.getInventory(),bench); p.containerMenu=menu; var stack=new ItemStack(NetherMetalContent.BLOCKS.get("nethermetal_panel").get(),32); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Original metal")); bench.setItem(0,stack);
        for(int button:new int[]{1,2}) for(int step=1;step<=10;step++) {
            h.assertTrue(menu.clickMenuButton(p,button),"Server changes full decorative stack"); var actual=bench.getItem(0); int index=Math.floorMod(button==1?step:-step,10);
            h.assertTrue(actual.is(NetherMetalContent.BLOCKS.get(NetherMetal.ALL.get(index).id()).get().asItem()),"Original enum order in both directions");
            h.assertValueEqual(actual.getCount(),32,"Whole stack preserved"); h.assertValueEqual(actual.getHoverName().getString(),"Original metal","Custom name retained");
            h.assertValueEqual(CamoCycling.count(actual),10,"Ten variants, separate from vanilla dyes");
            h.assertValueEqual(CamoCycling.variantName(actual),Component.translatable("block.techguns."+NetherMetal.ALL.get(index).id()),"Original variant label");
            h.assertValueEqual(actual.get(DataComponents.ITEM_MODEL),actual.getItem().getDefaultInstance().get(DataComponents.ITEM_MODEL),"Native default model follows transmuted item");
        }
        h.succeed();
    }
    private static NetherAltarStructure structure(ServerLevel level) { return (NetherAltarStructure)level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherAltarPiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel level,ChunkPos chunk) {
        var generator=level.getChunkSource().getGenerator(); return new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),level.getChunkSource().randomState(),level.getServer().getStructureManager(),level.getSeed(),chunk,level,b->true);
    }
    private static NetherAltarPiece piece(GameTestHelper h,int rotation) { return new NetherAltarPiece(h.getLevel().getServer().getStructureManager(),h.absolutePos(new BlockPos(1,0,1)).atY(120),rotation); }
    private static BoundingBox full(NetherAltarPiece p) { var b=p.getBoundingBox(); return new BoundingBox(b.minX(),1,b.minZ(),b.maxX(),250,b.maxZ()); }
    private static void place(GameTestHelper h,NetherAltarPiece p,BoundingBox clip) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(1),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static void registry(GameTestHelper h) {
        var level=h.getLevel(); var structure=structure(level); h.assertTrue(structure!=null,"Native structure registry loaded");
        var ops=level.registryAccess().createSerializationContext(JsonOps.INSTANCE); var encoded=Structure.DIRECT_CODEC.encodeStart(ops,structure).getOrThrow(); var decoded=(NetherAltarStructure)Structure.DIRECT_CODEC.parse(ops,encoded).getOrThrow();
        h.assertValueEqual(decoded.mediumGrid(),32,"Medium sites remain reserved"); h.assertValueEqual(decoded.bigGrid(),64,"Big sites remain reserved");
        var set=level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(NetherAltarPiece.TEMPLATE); var placement=(RandomSpreadStructurePlacement)set.placement();
        h.assertValueEqual(placement.spacing(),16,"Source small lattice"); h.assertValueEqual(placement.separation(),15,"Zero randomized cell offset");
        for(int sign:new int[]{-1,1}) { var at=placement.getPotentialStructureChunk(level.getSeed(),sign*16,sign*32); h.assertValueEqual(at,new ChunkPos(sign*16,sign*32),"Vanilla candidate exactly matches signed modulo lattice"); }
        h.assertTrue(structure.findGenerationPoint(context(level,new ChunkPos(0,0))).isEmpty(),"Origin belongs to BIG and is never replaced by altar");
        var holder=level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherAltarPiece.TEMPLATE)); var generator=level.getChunkSource().getGenerator();
        h.assertTrue(!structure.generate(holder,Level.OVERWORLD,level.registryAccess(),generator,generator.getBiomeSource(),level.getChunkSource().randomState(),level.getServer().getStructureManager(),level.getSeed(),new ChunkPos(16,16),0,level,b->true).isValid(),"Original dimension guard holds even if biome predicate is bypassed");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(structure.findGenerationPoint(context(level,new ChunkPos(16,16))).isEmpty(),"Source global toggle rejects generation"); } finally { LocationConfig.ENABLED.set(enabled); }
        var template=level.getServer().getStructureManager().get(NetherAltarPiece.TEMPLATE).orElseThrow(); h.assertValueEqual(template.getSize(),new Vec3i(11,9,11),"Exact scanned bounds, source declared cave clearance remains ten");
        var tag=template.save(new CompoundTag()); h.assertValueEqual(tag.getListOrEmpty("blocks").size(),769,"All source cells including air loaded");
        var spawners=template.filterBlocks(BlockPos.ZERO,new StructurePlaceSettings(),NpcSpawnerContent.BLOCK.get()); h.assertValueEqual(spawners.size(),1,"Exactly one source spawner");
        h.assertValueEqual(spawners.getFirst().pos(),new BlockPos(5,7,5),"Original local spawner coordinate"); h.succeed();
    }
    private static List<Block> palette() {
        var blocks=new ArrayList<Block>(); NetherMetalContent.BLOCKS.values().forEach(b->blocks.add(b.get())); blocks.addAll(List.of(Blocks.AIR,Blocks.NETHER_BRICK_STAIRS,Blocks.NETHER_BRICK_FENCE,NpcSpawnerContent.BLOCK.get())); return blocks;
    }
    private static void rotation(GameTestHelper h,int turns) {
        var p=piece(h,turns); var b=p.getBoundingBox();
        for(BlockPos pos:BlockPos.betweenClosed(b.minX(),120,b.minZ(),b.maxX(),128,b.maxZ())) h.getLevel().setBlock(pos,Blocks.NETHERRACK.defaultBlockState(),2);
        place(h,p,full(p)); int count=0;
        for(var block:palette()) for(var cell:p.template().filterBlocks(p.templatePosition(),p.placeSettings(),block)) {
            var actual=h.getLevel().getBlockState(cell.pos()); h.assertTrue(actual.is(cell.state().getBlock()),"Every rotated source cell keeps its block, including carved air");
            if(cell.state().hasProperty(BlockStateProperties.HORIZONTAL_FACING)) h.assertValueEqual(actual.getValue(BlockStateProperties.HORIZONTAL_FACING),cell.state().getValue(BlockStateProperties.HORIZONTAL_FACING),"Stair direction turns with geometry"); count++;
        }
        h.assertValueEqual(count,769,"All source cells checked after native placement"); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,0); var c=p.templatePosition().offset(4,0,4); var metal=NetherMetalContent.BLOCKS.get("nethermetal_plate_black").get();
        for(int d=1;d<=17;d++) { h.getLevel().setBlock(c.below(d),Blocks.AIR.defaultBlockState(),2); h.getLevel().setBlock(c.south().below(d),Blocks.AIR.defaultBlockState(),2); }
        for(int d:new int[]{2,4,5}) h.getLevel().setBlock(c.below(d),Blocks.OBSIDIAN.defaultBlockState(),2);
        place(h,p,full(p));
        for(int d:new int[]{1,3}) h.assertTrue(h.getLevel().getBlockState(c.below(d)).is(metal),"Single intervening solid does not end foundation");
        for(int d:new int[]{2,4,5}) h.assertTrue(h.getLevel().getBlockState(c.below(d)).is(Blocks.OBSIDIAN),"Solid support is not replaced");
        h.assertTrue(h.getLevel().getBlockState(c.below(6)).isAir(),"Two consecutive solids stop fill");
        for(int d=1;d<=16;d++) h.assertTrue(h.getLevel().getBlockState(c.south().below(d)).is(metal),"Sixteen-cell maximum foundation depth");
        h.assertTrue(h.getLevel().getBlockState(c.south().below(17)).isAir(),"No write below original foundation range"); h.succeed();
    }
    private static void clipping(GameTestHelper h) {
        var p=piece(h,1); var o=p.templatePosition(); var bounds=full(p); var left=new BoundingBox(bounds.minX(),1,bounds.minZ(),o.getX()+4,250,bounds.maxZ());
        place(h,p,left); var spawn=o.offset(5,7,5); h.assertTrue(h.getLevel().getBlockEntity(spawn)==null,"Other chunk slice cannot place the spawner");
        var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel()); var tag=p.createTag(ctx);
        var restored=(NetherAltarPiece)LocationContent.ALTAR_PIECE.get().load(ctx,tag); h.assertValueEqual(restored.templatePosition(),o,"Native piece NBT retains origin"); h.assertValueEqual(restored.getRotation(),p.getRotation(),"Native piece NBT retains rotation");
        h.assertValueEqual(restored.getBoundingBox(),p.getBoundingBox(),"Reload restores foundation bounds");
        place(h,restored,new BoundingBox(o.getX()+5,1,bounds.minZ(),bounds.maxX(),250,bounds.maxZ()));
        var block=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(spawn); h.assertTrue(block!=null,"Second slice after reload places owner"); h.assertValueEqual(block.remaining(),3,"Source NBT initializes quota"); h.assertValueEqual(block.activeCount(),0,"Template carries no live UUIDs"); h.succeed();
    }
    private static void spawner(GameTestHelper h) {
        var p=piece(h,0); place(h,p,full(p)); var pos=p.templatePosition().offset(5,7,5); var b=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(pos);
        h.assertValueEqual(b.remaining(),3,"Source three deaths"); h.assertValueEqual(b.maximum(),2,"Source two active"); h.assertValueEqual(b.interval(),200,"Source interval"); h.assertValueEqual(b.range(),1d,"Source range");
        h.assertValueEqual(b.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("cyberdemon"),1)),"Altar never defaults to ZombieSoldier");
        for(int tick=0;tick<400;tick++) NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,b.getBlockState(),b);
        h.assertValueEqual(b.activeCount(),2,"Two initialized CyberDemons appear from placed template");
        for(int deaths=1;deaths<=3;deaths++) {
            var mob=(CyberDemon)h.getLevel().getEntity(b.activeIds().iterator().next()); h.assertTrue(mob.armed(),"Native spawn equips Nether Blaster"); mob.removeFreeWill();
            mob.hurtServer(h.getLevel(),h.getLevel().damageSources().genericKill(),10000); h.assertValueEqual(b.remaining(),3-deaths,"Actual spawned death charged once");
            if(deaths==1) for(int tick=0;tick<200;tick++) NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,b.getBlockState(),b);
        }
        NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,b.getBlockState(),b); h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Finite original encounter exhausts and disappears"); h.succeed();
    }
    private static void natural(GameTestHelper h) {
        var level=h.getLevel().getServer().getLevel(Level.NETHER); h.assertTrue(level!=null,"Real Nether exists in normal-terrain test pack"); var s=structure(level);
        h.assertTrue(level.getServer().getWorldGenSettings().options().generateStructures(),"Opt-in worldgen harness must enable native structures");
        h.assertTrue(level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator,"Test uses real Nether noise generation");
        ChunkPos chosen=null;
        for(int n=1;n<=256;n++) { var candidate=new ChunkPos(16,16*n); if(s.findGenerationPoint(context(level,candidate)).isPresent()) { chosen=candidate; break; } }
        h.assertTrue(chosen!=null,"Original weighted selection and four-corner cave condition find a real site");
        var chunk=level.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Fresh native chunk stores registered structure start");
        h.assertValueEqual(start.getPieces().size(),1,"One saved template piece"); var p=(NetherAltarPiece)start.getPieces().getFirst(); var bb=p.getBoundingBox();
        for(int x=bb.minX()>>4;x<=bb.maxX()>>4;x++) for(int z=bb.minZ()>>4;z<=bb.maxZ()>>4;z++) level.getChunk(x,z);
        var pos=p.templatePosition().offset(5,7,5); var b=(NpcSpawnerBlockEntity)level.getBlockEntity(pos); h.assertTrue(b!=null,"Actual feature stage places source spawner NBT");
        h.assertValueEqual(b.remaining(),3,"Naturally generated quota"); h.assertValueEqual(b.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("cyberdemon"),1)),"Naturally generated CyberDemon encounter");
        var holder=level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherAltarPiece.TEMPLATE));
        var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),pos,0,false); h.assertTrue(found!=null,"Native locate resolves the generated structure");
        var id=b.instance(); var same=level.getChunk(chosen.x(),chosen.z()); h.assertValueEqual(((NpcSpawnerBlockEntity)level.getBlockEntity(pos)).instance(),id,"Already generated chunk is not retrofitted on another request");
        h.assertTrue(same.getStartForStructure(s)==start,"Native start reused");
        com.mojang.logging.LogUtils.getLogger().info("Techguns native Nether altar: chunk={}, origin={}, rotation={}, spawner={}",chosen,p.templatePosition(),p.getRotation(),pos);
        h.succeed();
    }
    private LocationGameTests() {}
}
