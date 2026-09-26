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
import techguns.modern.npc.Bandit;
import techguns.modern.machine.repair.*;
import techguns.modern.armor.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.biome.Biomes;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.*;
import techguns.modern.world.structure.*;

final class SurvivorHideoutGameTests {
    private static final ResourceKey<LootTable> LOOT=ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/survivor_hideout"));
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int t=0;t<4;t++) for(int c=0;c<3;c++) { int turn=t,camo=c;
            r.register("location_survivor_rotation_"+t+"_camo_"+c,()->h->rotation(h,turn,camo));
            r.register("location_survivor_reverse_chunk_save_"+t+"_camo_"+c,()->h->clipping(h,turn,camo));
        }
        r.register("location_survivor_sparse_foundation",()->SurvivorHideoutGameTests::foundation);
        r.register("location_survivor_eight_finite_bandits",()->SurvivorHideoutGameTests::guards);
        r.register("location_survivor_chests_save_and_ammo",()->SurvivorHideoutGameTests::chests);
        r.register("location_survivor_all_loot_entries",()->SurvivorHideoutGameTests::loot);
        r.register("location_survivor_rotated_facilities",()->SurvivorHideoutGameTests::facilities);
        r.register("location_survivor_registry_dimension_toggles",()->SurvivorHideoutGameTests::registry);
        r.register("location_survivor_native_biome_colours",()->SurvivorHideoutGameTests::biomes);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_survivor_positive_natural_chunks",()->h->natural(h,1)); r.register("structure_survivor_negative_natural_chunks",()->h->natural(h,-1));
            r.register("structure_survivor_shared_table_natural_chunks",()->SurvivorHideoutGameTests::selection);
        }
    }
    private static Block basePanel() { return BuildingContent.BLOCKS.get("metalpanel_container_red").get(); }
    private static Block baseCanopy() { return CamouflageNetContent.BLOCKS.get("camonet_top_wood").get(); }
    private static Block lantern() { return FortificationContent.LAMPS.get("lantern_yellow").get(); }
    private static List<Block> palette() { return List.of(Blocks.COARSE_DIRT,Blocks.OAK_FENCE,Blocks.AIR,Blocks.IRON_BLOCK,FortificationContent.SANDBAGS.get(),lantern(),Blocks.CHEST,Blocks.STONE_BRICKS,NpcSpawnerContent.BLOCK.get(),Blocks.OAK_PLANKS,Blocks.OAK_STAIRS,basePanel(),Blocks.SPRUCE_PLANKS,Blocks.COBBLESTONE_SLAB,baseCanopy(),Blocks.BED.pick(DyeColor.RED),Blocks.WALL_TORCH,Blocks.CRAFTING_TABLE,Blocks.FURNACE,BuildingContent.BLOCKS.get("ladder_metal").get(),RepairBenchContent.BLOCK.get()); }
    private static SurvivorHideoutPiece piece(GameTestHelper h,int slot,int turn,int panel,int camo) { return new SurvivorHideoutPiece(h.getLevel().getServer().getStructureManager(),new BlockPos(2200014+slot*64,140,-2200002),turn,panel,camo); }
    private static List<StructureTemplate.StructureBlockInfo> cells(SurvivorHideoutPiece p,Block b) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),b); }
    private static void load(ServerLevel l,SurvivorHideoutPiece p) { var b=p.getBoundingBox(); for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) l.getChunk(x,z); }
    private static void place(GameTestHelper h,SurvivorHideoutPiece p,BoundingBox clip,long seed) { p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(seed),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition()); }
    private static Map<BlockPos,BlockState> snapshot(ServerLevel l,SurvivorHideoutPiece p) { var b=p.getBoundingBox(); var out=new HashMap<BlockPos,BlockState>(); for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())) out.put(pos.immutable(),l.getBlockState(pos)); return out; }
    private static void fill(GameTestHelper h,SurvivorHideoutPiece p,Block block) { load(h.getLevel(),p); for(var pos:snapshot(h.getLevel(),p).keySet()) h.getLevel().setBlock(pos,block.defaultBlockState(),2); }
    private static SurvivorHideoutPiece placed(GameTestHelper h,int slot) { var p=piece(h,slot,0,0,0); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),87); return p; }

    private static void verify(GameTestHelper h,ServerLevel l,SurvivorHideoutPiece p) {
        var positions=new HashSet<BlockPos>();
        for(var block:palette()) for(var c:cells(p,block)) {
            positions.add(c.pos()); var expected=c.state();
            if(block==basePanel()) expected=p.panelBlock().defaultBlockState();
            if(block==baseCanopy()) expected=p.canopyBlock().defaultBlockState();
            if(block==Blocks.OAK_FENCE || block==FortificationContent.SANDBAGS.get() || block==baseCanopy() || block==lantern()) expected=Block.updateFromNeighbourShapes(expected,l,c.pos());
            h.assertValueEqual(l.getBlockState(c.pos()),expected,"Exact survivor source cell at "+c.pos());
        }
        h.assertValueEqual(positions.size(),1277,"All source cells mapped");
        for(var floor:cells(p,Blocks.COARSE_DIRT)) if(floor.pos().getY()==p.templatePosition().getY()) for(int y=1;y<=7;y++) {
            var pos=floor.pos().above(y); if(!positions.contains(pos)) h.assertTrue(l.getBlockState(pos).isAir(),"Sparse source clearing includes unrecorded cells");
        }
        var posts=cells(p,NpcSpawnerContent.BLOCK.get()); h.assertValueEqual(posts.size(),3,"Three bandit holes");
        for(var c:posts) {
            var b=(NpcSpawnerBlockEntity)l.getBlockEntity(c.pos()); boolean rifle=c.nbt().contains("weapon"); h.assertTrue(b!=null,"Post tile placed");
            h.assertValueEqual(b.remaining(),rifle?2:3,"Source death quota"); h.assertValueEqual(b.maximum(),rifle?1:2,"Source active limit");
            h.assertValueEqual(b.interval(),200,"Source timer"); h.assertValueEqual(b.range(),1d,"Source radius");
            h.assertValueEqual(b.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("bandit"),1)),"Correct species");
            h.assertTrue(rifle?b.weaponOverride().is(TGContent.GUNS.get("boltaction").get()):b.weaponOverride().isEmpty(),"Rooftop rifle override only");
        }
        h.assertValueEqual(cells(p,Blocks.CHEST).size(),7,"Seven original chest tiles");
        for(var c:cells(p,Blocks.CHEST)) h.assertValueEqual(((ChestBlockEntity)l.getBlockEntity(c.pos())).getLootTable(),LOOT,"Deferred complete loot");
    }
    private static void rotation(GameTestHelper h,int turn,int camo) {
        var p=piece(h,turn*3+camo,turn,turn,camo); fill(h,p,Blocks.OBSIDIAN); place(h,p,p.getBoundingBox(),turn+1); verify(h,h.getLevel(),p);
        h.assertValueEqual(p.template().getSize(),new Vec3i(11,11,19),"Original rectangular size");
        // Independent coordinate oracle for every source cell; native state rotations alone would miss a bad pivot.
        for(var block:palette()) for(var c:p.template().filterBlocks(BlockPos.ZERO,new StructurePlaceSettings(),block)) {
            int[] r=StructureRules.rotate(c.pos().getX(),c.pos().getZ(),turn,5,9); var actual=p.templatePosition().offset(r[0],c.pos().getY(),r[1]);
            h.assertTrue(h.getLevel().getBlockState(actual).is(block==basePanel()?p.panelBlock():block==baseCanopy()?p.canopyBlock():block),"Source rotation coordinate "+actual);
        }
        h.succeed();
    }
    private static void clipping(GameTestHelper h,int turn,int camo) {
        var p=piece(h,12+turn*3+camo,turn,3-turn,camo); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),1); var expected=snapshot(h.getLevel(),p);
        fill(h,p,Blocks.AIR); var b=p.getBoundingBox(); var clips=new ArrayList<BoundingBox>();
        for(int x=b.minX()>>4;x<=b.maxX()>>4;x++) for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++) clips.add(new BoundingBox(x*16,b.minY(),z*16,x*16+15,b.maxY(),z*16+15));
        h.assertTrue(clips.size()>1,"Fixture crosses native chunk edges"); Collections.reverse(clips); var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel());
        for(var clip:clips) {
            place(h,p,clip,2); p=(SurvivorHideoutPiece)LocationContent.SURVIVOR_PIECE.get().load(ctx,p.createTag(ctx));
            h.assertValueEqual(p.panel(),3-turn,"Shared panel survives each chunk save"); h.assertValueEqual(p.canopy(),camo,"Biome canopy survives each chunk save");
        }
        h.assertValueEqual(snapshot(h.getLevel(),p),expected,"Reverse chunks preserve all connections, pairs and shared colours"); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,24,1,1,1); fill(h,p,Blocks.AIR); var bottom=cells(p,Blocks.COARSE_DIRT).stream().filter(c->c.pos().getY()==p.templatePosition().getY()).toList(); h.assertValueEqual(bottom.size(),149,"Sparse footprint");
        var stop=bottom.getFirst().pos(); h.getLevel().setBlock(stop.below(),Blocks.OBSIDIAN.defaultBlockState(),2); h.getLevel().setBlock(stop.below(2),Blocks.OBSIDIAN.defaultBlockState(),2);
        var occupied=new HashSet<BlockPos>(); for(var block:palette()) for(var c:cells(p,block)) occupied.add(c.pos());
        for(var c:bottom) { h.getLevel().setBlock(c.pos().below(4),Blocks.AIR.defaultBlockState(),2); if(!occupied.contains(c.pos().above(8))) h.getLevel().setBlock(c.pos().above(8),Blocks.OBSIDIAN.defaultBlockState(),2); }
        var box=p.getBoundingBox(); var untouched=new HashSet<BlockPos>();
        for(var pos:BlockPos.betweenClosed(box.minX(),p.templatePosition().getY(),box.minZ(),box.maxX(),p.templatePosition().getY(),box.maxZ()))
            if(!occupied.contains(pos)) { untouched.add(pos.immutable()); h.getLevel().setBlock(pos,Blocks.OBSIDIAN.defaultBlockState(),2); h.getLevel().setBlock(pos.below(),Blocks.OBSIDIAN.defaultBlockState(),2); }
        place(h,p,box,1);
        for(var c:bottom) for(int d=1;d<=4;d++) h.assertTrue(h.getLevel().getBlockState(c.pos().below(d)).is(d==4?Blocks.AIR:c.pos().equals(stop)&&d<3?Blocks.OBSIDIAN:Blocks.COARSE_DIRT),"Fill stops at depth three, skips each solid independently");
        for(var c:bottom) if(!occupied.contains(c.pos().above(8))) h.assertTrue(h.getLevel().getBlockState(c.pos().above(8)).is(Blocks.OBSIDIAN),"Cleanup stops below layer eight");
        for(var pos:untouched) h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.OBSIDIAN) && h.getLevel().getBlockState(pos.below()).is(Blocks.OBSIDIAN),"No rectangular foundation outside source columns");
        h.assertTrue(!untouched.isEmpty(),"Fixture includes omitted ground columns"); verify(h,h.getLevel(),p); h.succeed();
    }
    private static void guards(GameTestHelper h) {
        var p=placed(h,25); var l=h.getLevel(); var spawned=new LinkedHashMap<UUID,Bandit>();
        Consumer<EntityJoinLevelEvent> observe=e->{ if(e.getLevel()==l && e.getEntity() instanceof Bandit npc && p.getBoundingBox().isInside(npc.blockPosition())) { npc.removeFreeWill(); spawned.put(npc.getUUID(),npc); } };
        NeoForge.EVENT_BUS.addListener(observe);
        try {
            for(var c:cells(p,NpcSpawnerContent.BLOCK.get())) {
                var b=(NpcSpawnerBlockEntity)l.getBlockEntity(c.pos()); int quota=b.remaining(),maximum=b.maximum(); boolean rifle=!b.weaponOverride().isEmpty();
                for(int death=0;death<quota;death++) {
                    for(int tick=0;tick<600;tick++) NpcSpawnerBlockEntity.serverTick(l,c.pos(),b.getBlockState(),b);
                    h.assertValueEqual(b.activeCount(),Math.min(maximum,quota-death),"Native live quota at the original post");
                    var npc=spawned.get(b.activeIds().iterator().next()); h.assertTrue(npc!=null && npc.isAlive(),"Actual bandit spawned");
                    h.assertValueEqual(npc.spawnerLink(),b.link(),"Persistent ownership"); h.assertTrue(npc.armed(),"Native bandit equipment");
                    if(rifle) h.assertTrue(npc.getMainHandItem().is(TGContent.GUNS.get("boltaction").get()),"Rooftop guard has source rifle");
                    npc.hurtServer(l,l.damageSources().genericKill(),10000); h.assertTrue(!npc.isAlive(),"Actual death"); h.assertValueEqual(b.remaining(),quota-death-1,"Only real death spends quota");
                    NpcSpawnerBlockEntity.serverTick(l,c.pos(),b.getBlockState(),b);
                }
                h.assertTrue(l.getBlockState(c.pos()).isAir(),"Exhausted post disappears");
            }
            h.assertValueEqual(spawned.size(),8,"Eight distinct bandits across all source posts"); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); spawned.values().forEach(Entity::discard); }
    }
    private static ChestBlockEntity reload(ServerLevel l,ChestBlockEntity b) { var tag=b.saveWithFullMetadata(l.registryAccess()); var pos=b.getBlockPos(); var state=b.getBlockState(); l.removeBlockEntity(pos); var restored=(ChestBlockEntity)BlockEntity.loadStatic(pos,state,tag,l.registryAccess()); l.setBlockEntity(restored); return restored; }
    private static Map<Item,Integer> items(net.minecraft.world.Container c) { var out=new HashMap<Item,Integer>(); for(int i=0;i<c.getContainerSize();i++) { var s=c.getItem(i); if(!s.isEmpty()) out.merge(s.getItem(),s.getCount(),Integer::sum); } return out; }
    private static LootParams params(ServerLevel l,BlockPos pos) { return new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST); }
    private static void chests(GameTestHelper h) {
        var l=h.getLevel(); var p=placed(h,26); var table=l.getServer().reloadableRegistries().getLootTable(LOOT); var seeds=new HashSet<Long>(); int i=0;
        var ammo=List.of("pistolrounds_incendiary","shotgunrounds_incendiary","riflerounds_incendiary","sniperrounds_incendiary","smgmagazine_incendiary","pistolmagazine_incendiary","assaultriflemagazine_incendiary");
        var guns=List.of("revolver","combatshotgun","boltaction","as50","thompson","pistol","m4");
        for(var c:cells(p,Blocks.CHEST)) {
            var b=(ChestBlockEntity)l.getBlockEntity(c.pos()); h.assertTrue(seeds.add(b.getLootTableSeed()),"Independent tile reward seed"); var wanted=TGContent.AMMO.get(ammo.get(i)).get(); long seed;
            int needed=i==3?2:1;
            for(seed=1;seed<10000;seed++) if(table.getRandomItems(params(l,c.pos()),seed).stream().filter(s->s.is(wanted)).mapToInt(ItemStack::getCount).sum()>=needed) break;
            h.assertTrue(seed<10000,"Reproducible incendiary reward"); b.setLootTableSeed(seed); b=reload(l,b); h.assertValueEqual(b.getLootTableSeed(),seed,"Unopened seed persists");
            var expected=new HashMap<Item,Integer>(); table.getRandomItems(params(l,c.pos()),seed).forEach(s->expected.merge(s.getItem(),s.getCount(),Integer::sum));
            var player=WeaponGameTests.player(h); h.assertTrue(b.createMenu(1,player.getInventory(),player)!=null,"Native chest opens"); h.assertValueEqual(items(b),expected,"Exact deferred reward contents");
            var reward=java.util.stream.IntStream.range(0,b.getContainerSize()).mapToObj(b::getItem).filter(s->s.is(wanted)).findFirst().orElseThrow().copyWithCount(1);
            int left=needed; for(int slot=0;slot<b.getContainerSize() && left>0;slot++) if(b.getItem(slot).is(wanted)) left-=b.removeItem(slot,left).getCount();
            h.assertValueEqual(left,0,"Crafting consumes actual loot");
            if(i==3) {
                var packed=CraftingInput.of(3,1,List.of(TGContent.AMMO.get("as50magazineempty").toStack(),reward,reward.copy()));
                reward=l.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,packed,l).orElseThrow(()->new IllegalStateException("Missing sniper loot packing recipe")).value().assemble(packed);
            }
            var input=CraftingInput.of(2,1,List.of(TGContent.GUNS.get(guns.get(i++)).toStack(),reward));
            var recipe=l.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,l).orElseThrow(()->new IllegalStateException("Missing switch recipe for loot "+wanted)).value(); var gun=recipe.assemble(input);
            h.assertValueEqual(BallisticAmmo.variant(gun),BallisticVariant.INCENDIARY,"Actual loot selects the native ammo variant");
            player.setItemInHand(InteractionHand.MAIN_HAND,gun); player.setData(SafeMode.SAFE,true); int rounds=GunItem.rounds(gun);
            h.assertTrue(GunItem.fire(l,player,gun),"Every incendiary loot family produces a usable shot"); h.assertValueEqual(GunItem.rounds(gun),rounds-1,"Reward shot spends ammunition");
            l.getEntitiesOfClass(Bullet.class,new AABB(player.blockPosition()).inflate(8),shot->shot.getOwner()==player).forEach(Entity::discard);
            var after=items(b); b=reload(l,b); h.assertValueEqual(items(b),after,"Opened inventory persists"); for(int j=0;j<b.getContainerSize();j++) b.removeItemNoUpdate(j); b=reload(l,b); h.assertTrue(b.isEmpty() && b.getLootTable()==null,"Empty chest never rerolls");
        }
        int pairs=0,singles=0;
        for(var c:cells(p,Blocks.CHEST)) {
            var container=ChestBlock.getContainer((ChestBlock)Blocks.CHEST,l.getBlockState(c.pos()),l,c.pos(),true);
            h.assertTrue(container!=null,"Native reward container"); boolean single=c.state().getValue(ChestBlock.TYPE)==ChestType.SINGLE;
            h.assertValueEqual(container.getContainerSize(),single?27:54,"Paired and single container capacities"); if(single) singles++; else pairs++;
        }
        h.assertTrue(pairs==4 && singles==3,"Two double chests and three singles"); h.succeed();
    }
    private static void loot(GameTestHelper h) {
        var table=h.getLevel().getServer().reloadableRegistries().getLootTable(LOOT); var seen=new HashSet<String>();
        for(long seed=0;seed<2048;seed++) for(var s:table.getRandomItems(params(h.getLevel(),BlockPos.ZERO),seed)) seen.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
        var expected=new HashSet<String>();
        for(var id:List.of("iron_ingot","redstone","coal","gunpowder","gold_ingot","diamond","ender_pearl")) expected.add("minecraft:"+id);
        for(var id:List.of("heavycloth","mechanicalpartsiron","mechanicalpartsobsidiansteel","plasticsheet","rubberbar","ingotobsidiansteel","radaway","radpills")) expected.add("techguns:"+id);
        for(var id:List.of("pistolrounds","shotgunrounds","riflerounds","sniperrounds","smgmagazine","pistolmagazine","assaultriflemagazine")) { expected.add("techguns:"+id); expected.add("techguns:"+id+"_incendiary"); }
        for(var kind:List.of("t1_combat","t2_combat","t1_scout")) for(var part:List.of("helmet","chestplate","leggings","boots")) expected.add("techguns:"+kind+"_"+part);
        h.assertValueEqual(seen,expected,"All 41 original entries across four loaded pools"); h.succeed();
    }
    private static void facilities(GameTestHelper h) {
        for(int turn=0;turn<4;turn++) {
            var p=piece(h,28+turn,turn,turn,turn%3); fill(h,p,Blocks.AIR); place(h,p,p.getBoundingBox(),3); var l=h.getLevel();
            for(var block:List.of(lantern(),Blocks.BED.pick(DyeColor.RED),Blocks.WALL_TORCH,BuildingContent.BLOCKS.get("ladder_metal").get()))
                for(var c:cells(p,block)) h.assertTrue(l.getBlockState(c.pos()).canSurvive(l,c.pos()),"Rotated fixture retains its support/pair");
            var bed=cells(p,Blocks.BED.pick(DyeColor.RED)).stream().filter(c->c.state().getValue(BedBlock.PART)==BedPart.FOOT).findFirst().orElseThrow();
            var head=bed.pos().relative(l.getBlockState(bed.pos()).getValue(BedBlock.FACING)); h.assertValueEqual(l.getBlockState(head).getValue(BedBlock.PART),BedPart.HEAD,"Native bed joins in every rotation");
            var furnace=cells(p,Blocks.FURNACE).getFirst(); h.assertTrue(l.getBlockEntity(furnace.pos()) instanceof FurnaceBlockEntity,"Actual furnace tile created");
            var pos=cells(p,RepairBenchContent.BLOCK.get()).getFirst().pos(); var bench=(RepairBenchBlockEntity)l.getBlockEntity(pos); var player=WeaponGameTests.player(h); player.setPos(Vec3.atCenterOf(pos));
            h.assertTrue(bench!=null && bench.canOpen(player) && !bench.isOwner(player),"Generated repair bench starts accessible and unowned");
            var menu=(RepairBenchMenu)bench.createMenu(1,player.getInventory(),player); player.containerMenu=menu; h.assertTrue(bench.isOwner(player),"First user claims the actual generated bench");
            var armor=ArmorContent.ITEMS.get(ArmorSlot.HEAD).toStack(); armor.setDamageValue(200); player.setItemSlot(EquipmentSlot.HEAD,armor);
            bench.setItem(0,TGContent.MATERIALS.get("ingotobsidiansteel").toStack(8)); bench.setItem(1,TGContent.MATERIALS.get("heavycloth").toStack(8));
            h.assertTrue(menu.clickMenuButton(player,1) && armor.getDamageValue()==0,"Original repair bench repairs worn gear after world placement");
        }
        h.succeed();
    }
    private static void biomes(GameTestHelper h) {
        var r=h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        for(var key:List.of(Biomes.PLAINS,Biomes.FOREST,Biomes.SAVANNA,Biomes.SAVANNA_PLATEAU)) h.assertValueEqual(SurvivorHideoutStructure.canopy(r.getOrThrow(key)),0,"Woodland, including original SAVANNA.BEACH static-field quirk");
        for(var key:List.of(Biomes.DESERT,Biomes.BEACH,Biomes.BADLANDS,Biomes.WOODED_BADLANDS)) h.assertValueEqual(SurvivorHideoutStructure.canopy(r.getOrThrow(key)),1,"Sandy, beach and mesa canopy");
        for(var key:List.of(Biomes.SNOWY_PLAINS,Biomes.SNOWY_BEACH,Biomes.SNOWY_TAIGA,Biomes.ICE_SPIKES)) h.assertValueEqual(SurvivorHideoutStructure.canopy(r.getOrThrow(key)),2,"Cold/snowy wins over beach");
        h.succeed();
    }
    private static SurvivorHideoutStructure structure(ServerLevel l) { return (SurvivorHideoutStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(SurvivorHideoutPiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos c,long seed) { var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),seed,c,l,b->true); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE); var decoded=(SurvivorHideoutStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow(); h.assertValueEqual(decoded.bigGrid(),64,"Native codec preserves reserved grid");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,SurvivorHideoutPiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dimension:List.of(Level.NETHER,Level.END)) h.assertTrue(!s.generate(holder,dimension,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(32,32),0,l,b->true).isValid(),"Overworld-only guard");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(32,32),0)).isEmpty(),"Global structure switch"); } finally { LocationConfig.ENABLED.set(enabled); }
        for(int n=-5;n<=5;n++) h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(64,64*n),0)).isEmpty(),"Big grid remains reserved"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel(); var s=structure(l); ChunkPos chosen=null;
        for(int n=129;n<=2048;n++) { var c=new ChunkPos(sign*32,sign*32*n); if(s.findGenerationPoint(context(l,c,l.getSeed())).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Source hideout ticket and surface find native land"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s); h.assertTrue(start!=null && start.isValid(),"Native hideout start saved"); var p=(SurvivorHideoutPiece)start.getPieces().getFirst(); load(l,p); var box=p.getBoundingBox();
        for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++) for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++) l.getChunk(x,z);
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z).postProcessGeneration(l);
        verify(h,l,p);
        for(var id:List.of(PoliceStationPiece.TEMPLATE,MeteorPiece.TEMPLATE,OreSpikePiece.TEMPLATE,TGContent.id("alienbug_nest"))) { var other=chunk.getStartForStructure(l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id)); h.assertTrue(other==null || !other.isValid(),"Medium candidates never overlap hideout ticket"); }
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,SurvivorHideoutPiece.TEMPLATE)); var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),p.templatePosition().offset(5,5,9),0,false);
        h.assertTrue(found!=null,"Native locate finds actual hideout station"); h.assertValueEqual(new ChunkPos(found.getFirst().getX()>>4,found.getFirst().getZ()>>4),chosen,"Locate start chunk");
        var before=snapshot(l,p); var saved=cells(p,Blocks.CHEST).stream().map(c->l.getBlockEntity(c.pos()).saveWithFullMetadata(l.registryAccess())).toList(); l.getChunk(chosen.x(),chosen.z());
        h.assertValueEqual(snapshot(l,p),before,"Repeat chunk request does not regenerate station"); h.assertValueEqual(cells(p,Blocks.CHEST).stream().map(c->l.getBlockEntity(c.pos()).saveWithFullMetadata(l.registryAccess())).toList(),saved,"Original rewards retain seeds");
        var shrub=cells(p,Blocks.AIR).stream().map(StructureTemplate.StructureBlockInfo::pos).filter(pos->Blocks.DEAD_BUSH.defaultBlockState().canSurvive(l,pos)).findFirst().orElseThrow();
        var config=new net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration(net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(Blocks.DEAD_BUSH));
        h.assertTrue(net.minecraft.world.level.levelgen.feature.Feature.SIMPLE_BLOCK.place(config,l,l.getChunkSource().getGenerator(),RandomSource.create(1),shrub),"Decoration guard allows feature use after terrain generation");
        h.assertTrue(l.getBlockState(shrub).is(Blocks.DEAD_BUSH),"An actual block can still be placed inside the generated hideout"); l.setBlock(shrub,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural SurvivorHideout: chunk={}, origin={}, rotation={}, guards=3, chestTiles=7, panel={}, canopy={}",chosen,p.templatePosition(),p.getRotation(),p.panel(),p.canopy()); h.succeed();
    }
    private static void selection(GameTestHelper h) {
        var l=h.getLevel(); var registry=l.registryAccess().lookupOrThrow(Registries.STRUCTURE); var hideout=structure(l); var police=(PoliceStationStructure)registry.getValue(PoliceStationPiece.TEMPLATE); var meteor=(MeteorStructure)registry.getValue(MeteorPiece.TEMPLATE); var spike=(OreSpikeStructure)registry.getValue(OreSpikePiece.TEMPLATE); var bug=(BugNestStructure)registry.getValue(TGContent.id("alienbug_nest")); boolean ores=LocationConfig.ORE_CLUSTERS.get();
        try { for(boolean enabled:List.of(false,true)) { LocationConfig.ORE_CLUSTERS.set(enabled); int hit=0;
            for(long seed:new long[]{0,1,42}) for(int n=-96;n<=96;n++) {
                var c=new ChunkPos(32,32*n); boolean p=hideout.findGenerationPoint(context(l,c,seed)).isPresent(),m=meteor.findGenerationPoint(context(l,c,seed)).isPresent(),s=spike.findGenerationPoint(context(l,c,seed)).isPresent(),b=bug.findGenerationPoint(context(l,c,seed)).isPresent(),station=police.findGenerationPoint(context(l,c,seed)).isPresent();
                h.assertTrue((p?1:0)+(m?1:0)+(s?1:0)+(b?1:0)+(station?1:0)<=1,"Shared source selection never overlaps five implemented candidates"); if(p) hit++;
            }
            h.assertTrue(hit>0,"Survivor hideouts remain possible with ore structures disabled");
        } } finally { LocationConfig.ORE_CLUSTERS.set(ores); } h.succeed();
    }
    private SurvivorHideoutGameTests() {}
}
