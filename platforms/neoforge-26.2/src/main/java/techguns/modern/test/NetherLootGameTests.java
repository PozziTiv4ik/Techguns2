package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.StructureRules;
import techguns.modern.*;
import techguns.modern.armor.ArmorContent;
import techguns.modern.npc.ZombiePigmanSoldier;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.NetherMetalContent;
import techguns.modern.world.structure.*;

final class NetherLootGameTests {
    private static final ResourceKey<LootTable> LOOT=ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/factory_building"));
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("location_loot_registry_and_original_nbt",()->NetherLootGameTests::registry);
        for(int i=0;i<4;i++) { int turn=i; r.register("location_loot_rotation_"+i,()->h->rotation(h,turn)); }
        r.register("location_loot_clipping_saved_piece",()->NetherLootGameTests::clipping);
        r.register("location_loot_ten_foundation_columns",()->NetherLootGameTests::foundation);
        r.register("location_loot_unopened_chest_save",()->NetherLootGameTests::unopened);
        r.register("location_loot_consumed_chest_save",()->NetherLootGameTests::consumed);
        r.register("location_loot_native_chest_seeds",()->NetherLootGameTests::seeds);
        r.register("structure_loot_hopper_conserves_contents",()->NetherLootGameTests::hopper);
        r.register("location_loot_original_weight_boundaries",()->NetherLootGameTests::weights);
        r.register("location_loot_chest_to_beret_craft",()->NetherLootGameTests::craft);
        r.register("location_loot_two_pigman_encounter",()->NetherLootGameTests::encounter);
        if(Boolean.getBoolean("techguns.worldgenTest")) {
            r.register("structure_loot_positive_natural_chunks",()->h->natural(h,1));
            r.register("structure_loot_negative_natural_chunks",()->h->natural(h,-1));
            r.register("structure_loot_exclusive_natural_chunks",()->NetherLootGameTests::exclusive);
        }
    }
    private static NetherLootStructure structure(ServerLevel l) { return (NetherLootStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherLootPiece.TEMPLATE); }
    private static Structure.GenerationContext context(ServerLevel l,ChunkPos chunk,long seed) {
        var g=l.getChunkSource().getGenerator(); return new Structure.GenerationContext(l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),seed,chunk,l,b->true);
    }
    private static NetherLootPiece piece(GameTestHelper h,int turn) { return new NetherLootPiece(h.getLevel().getServer().getStructureManager(),h.absolutePos(new BlockPos(1,0,1)).atY(120),turn); }
    private static void place(GameTestHelper h,NetherLootPiece p,BoundingBox clip,long seed) {
        p.postProcess(h.getLevel(),h.getLevel().structureManager(),h.getLevel().getChunkSource().getGenerator(),RandomSource.create(seed),clip,new ChunkPos(p.templatePosition().getX()>>4,p.templatePosition().getZ()>>4),p.templatePosition());
    }
    private static NetherLootPiece placed(GameTestHelper h) { var p=piece(h,0); place(h,p,p.getBoundingBox(),1); return p; }
    private static BlockPos position(NetherLootPiece p,Block block) { return p.template().filterBlocks(p.templatePosition(),p.placeSettings().copy().setBoundingBox(null),block).getFirst().pos(); }
    private static ChestBlockEntity chest(GameTestHelper h,NetherLootPiece p) { return (ChestBlockEntity)h.getLevel().getBlockEntity(position(p,Blocks.CHEST)); }
    private static ChestBlockEntity reload(GameTestHelper h,ChestBlockEntity b) {
        var tag=b.saveWithFullMetadata(h.getLevel().registryAccess()); var pos=b.getBlockPos(); var state=b.getBlockState(); h.getLevel().removeBlockEntity(pos);
        var restored=(ChestBlockEntity)BlockEntity.loadStatic(pos,state,tag,h.getLevel().registryAccess()); h.getLevel().setBlockEntity(restored); return restored;
    }
    private static Map<Item,Integer> items(net.minecraft.world.Container c) {
        Map<Item,Integer> result=new HashMap<>(); for(int i=0;i<c.getContainerSize();i++) { var s=c.getItem(i); if(!s.isEmpty()) result.merge(s.getItem(),s.getCount(),Integer::sum); } return result;
    }
    private static Map<Item,Integer> items(List<ItemStack> stacks) {
        Map<Item,Integer> result=new HashMap<>(); for(var s:stacks) result.merge(s.getItem(),s.getCount(),Integer::sum); return result;
    }
    private static LootParams params(GameTestHelper h,BlockPos pos) { return new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST); }
    private static LootTable table(GameTestHelper h) { return h.getLevel().getServer().reloadableRegistries().getLootTable(LOOT); }
    private static void registry(GameTestHelper h) {
        var l=h.getLevel(); var s=structure(l); var ops=l.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var decoded=(NetherLootStructure)Structure.DIRECT_CODEC.parse(ops,Structure.DIRECT_CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow();
        h.assertValueEqual(decoded.mediumGrid(),32,"Medium sites reserved"); h.assertValueEqual(decoded.bigGrid(),64,"Big sites reserved");
        var t=l.getServer().getStructureManager().get(NetherLootPiece.TEMPLATE).orElseThrow();
        h.assertValueEqual(t.getSize(),new Vec3i(6,10,6),"All original scanned bounds"); h.assertValueEqual(t.save(new CompoundTag()).getListOrEmpty("blocks").size(),146,"All source cells including air");
        var c=t.filterBlocks(BlockPos.ZERO,new StructurePlaceSettings(),Blocks.CHEST).getFirst();
        h.assertValueEqual(c.pos(),new BlockPos(2,2,2),"Original chest cell"); h.assertValueEqual(c.nbt().getStringOr("LootTable",""),LOOT.identifier().toString(),"Deferred original loot");
        h.assertTrue(!c.nbt().contains("Items") && !c.nbt().contains("LootTableSeed"),"No prefilled or shared-seed chest");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherLootPiece.TEMPLATE)); var g=l.getChunkSource().getGenerator();
        for(var dimension:List.of(Level.OVERWORLD,Level.END)) h.assertTrue(!s.generate(holder,dimension,l.registryAccess(),g,g.getBiomeSource(),l.getChunkSource().randomState(),l.getServer().getStructureManager(),l.getSeed(),new ChunkPos(16,16),0,l,b->true).isValid(),"Nether-only even with bypassed biome predicate");
        boolean enabled=LocationConfig.ENABLED.get(); try { LocationConfig.ENABLED.set(false); h.assertTrue(s.findGenerationPoint(context(l,new ChunkPos(16,16),0)).isEmpty(),"Global structures toggle"); } finally { LocationConfig.ENABLED.set(enabled); }
        h.succeed();
    }
    private static void rotation(GameTestHelper h,int turn) {
        var p=piece(h,turn); var box=p.getBoundingBox(); var l=h.getLevel();
        for(var pos:BlockPos.betweenClosed(box.minX(),118,box.minZ(),box.maxX(),130,box.maxZ())) l.setBlock(pos,Blocks.NETHERRACK.defaultBlockState(),2);
        l.setBlock(p.templatePosition().below(),Blocks.AIR.defaultBlockState(),2);
        place(h,p,box,1); int count=0;
        for(var block:List.of(Blocks.NETHERRACK,Blocks.AIR,Blocks.NETHER_BRICK_FENCE,Blocks.SKELETON_SKULL,Blocks.CHEST,NetherMetalContent.BLOCKS.get("nethermetal_grey_dark").get(),NpcSpawnerContent.BLOCK.get()))
            for(var cell:p.template().filterBlocks(p.templatePosition(),p.placeSettings(),block)) {
                var actual=l.getBlockState(cell.pos()); h.assertTrue(actual.is(block),"Every original cell after native rotation");
                if(block==Blocks.CHEST) h.assertValueEqual(actual.getValue(ChestBlock.FACING),cell.state().getValue(ChestBlock.FACING),"Chest faces original rotated east");
                if(block==Blocks.SKELETON_SKULL) h.assertValueEqual(actual.getValue(SkullBlock.ROTATION),0,"Original tile-entity yaw is unchanged by structure rotation");
                count++;
            }
        h.assertValueEqual(count,146,"No cell discarded"); h.assertTrue(l.getBlockState(p.templatePosition().below()).isAir(),"No invented foundation below y=0");
        h.assertValueEqual(chest(h,p).getLootTable(),LOOT,"Rotated chest keeps original table"); h.succeed();
    }
    private static void clipping(GameTestHelper h) {
        var p=piece(h,2); var o=p.templatePosition(); var box=p.getBoundingBox();
        place(h,p,new BoundingBox(box.minX(),1,box.minZ(),o.getX()+3,250,box.maxZ()),1);
        h.assertTrue(chest(h,p)==null,"Unprocessed slice cannot create the chest at rotated x=4");
        var ctx=StructurePieceSerializationContext.fromLevel(h.getLevel()); var restored=(NetherLootPiece)LocationContent.LOOT_PIECE.get().load(ctx,p.createTag(ctx));
        h.assertValueEqual(restored.templatePosition(),o,"Saved origin"); h.assertValueEqual(restored.getRotation(),p.getRotation(),"Saved rotation");
        h.assertValueEqual(restored.getBoundingBox(),box,"Saved native bounds");
        place(h,restored,new BoundingBox(o.getX()+4,1,box.minZ(),box.maxX(),250,box.maxZ()),2);
        h.assertValueEqual(chest(h,restored).getLootTable(),LOOT,"Later chunk slice after piece reload gets deferred loot");
        var skull=position(restored,Blocks.SKELETON_SKULL); h.assertValueEqual(h.getLevel().getBlockState(skull).getValue(SkullBlock.ROTATION),0,"Skull processor restored with piece"); h.succeed();
    }
    private static void foundation(GameTestHelper h) {
        var p=piece(h,1); int count=0;
        var cells=p.template().filterBlocks(p.templatePosition(),p.placeSettings(),Blocks.NETHERRACK).stream().filter(c->c.pos().getY()==p.templatePosition().getY()).toList();
        for(var cell:cells) for(int d=1;d<=17;d++) h.getLevel().setBlock(cell.pos().below(d),Blocks.AIR.defaultBlockState(),2);
        var stop=cells.getFirst().pos(); for(int d:new int[]{2,4,5}) h.getLevel().setBlock(stop.below(d),Blocks.OBSIDIAN.defaultBlockState(),2);
        place(h,p,p.getBoundingBox(),1);
        for(var cell:cells) {
            count++; var pos=cell.pos();
            for(int d=1;d<=16;d++) {
                if(pos.equals(stop)) {
                    if(d==2 || d==4 || d==5) h.assertTrue(h.getLevel().getBlockState(pos.below(d)).is(Blocks.OBSIDIAN),"Original solid support retained");
                    else if(d>=6) h.assertTrue(h.getLevel().getBlockState(pos.below(d)).isAir(),"Two solids stop this column");
                    else h.assertTrue(h.getLevel().getBlockState(pos.below(d)).is(Blocks.NETHERRACK),"Single solid permits deeper fill");
                } else h.assertTrue(h.getLevel().getBlockState(pos.below(d)).is(Blocks.NETHERRACK),"All sixteen original foundation layers");
            }
            h.assertTrue(h.getLevel().getBlockState(pos.below(17)).isAir(),"Depth limit does not leak beyond native bounds");
        }
        h.assertValueEqual(count,10,"Ten original rotated bottom cells"); h.succeed();
    }
    private static void unopened(GameTestHelper h) {
        var b=chest(h,placed(h)); long seed=b.getLootTableSeed(); h.assertTrue(seed!=0,"Native structure assigns a seed at placement");
        var expected=items(table(h).getRandomItems(params(h,b.getBlockPos()),seed)); b=reload(h,b);
        h.assertValueEqual(b.getLootTable(),LOOT,"Unopened chest retains table across block-entity reload"); h.assertValueEqual(b.getLootTableSeed(),seed,"Unopened seed survives");
        h.assertTrue(b.createMenu(1,WeaponGameTests.player(h).getInventory(),WeaponGameTests.player(h))!=null,"Real chest menu opens");
        h.assertValueEqual(items(b),expected,"First open rolls the original table once"); h.assertTrue(b.getLootTable()==null,"Loot-table pointer consumed"); h.succeed();
    }
    private static void consumed(GameTestHelper h) {
        var b=chest(h,placed(h)); var initial=items(b); h.assertTrue(!initial.isEmpty(),"Source pool always yields loot");
        b=reload(h,b); h.assertValueEqual(items(b),initial,"Opened inventory survives reload");
        for(int i=0;i<b.getContainerSize();i++) b.removeItemNoUpdate(i);
        b=reload(h,b); h.assertTrue(b.getLootTable()==null && b.isEmpty(),"Empty chest stays empty; no reroll after reload"); h.succeed();
    }
    private static void seeds(GameTestHelper h) {
        var p=placed(h); long first=chest(h,p).getLootTableSeed();
        var other=new NetherLootPiece(h.getLevel().getServer().getStructureManager(),p.templatePosition().east(9),0); place(h,other,other.getBoundingBox(),2);
        h.assertTrue(chest(h,other).getLootTableSeed()!=first,"Two structures receive independent native loot seeds");
        h.assertTrue(!p.template().filterBlocks(BlockPos.ZERO,new StructurePlaceSettings(),Blocks.CHEST).getFirst().nbt().contains("LootTableSeed"),"Placement never mutates the shared template NBT"); h.succeed();
    }
    private static void hopper(GameTestHelper h) {
        var p=placed(h); var b=chest(h,p); var pos=b.getBlockPos(); var expected=items(table(h).getRandomItems(params(h,pos),b.getLootTableSeed()));
        h.getLevel().setBlock(pos.below(),Blocks.HOPPER.defaultBlockState(),3); var hopper=(HopperBlockEntity)h.getLevel().getBlockEntity(pos.below());
        h.runAfterDelay(32,()->{
            var taken=items(hopper); h.assertTrue(!taken.isEmpty(),"Real hopper tick opens generated chest without a player");
            Map<Item,Integer> actual=new HashMap<>(items(b)); taken.forEach((item,n)->actual.merge(item,n,Integer::sum));
            h.assertValueEqual(actual,expected,"Automation conserves the rolled items"); h.assertTrue(b.getLootTable()==null,"Automation consumes the deferred table once");
            var partial=items(b); h.assertValueEqual(items(reload(h,b)),partial,"Partly looted chest survives block reload"); h.succeed();
        });
    }
    private static final String[] ITEMS={"minecraft:iron_ingot","minecraft:redstone","minecraft:coal","minecraft:gunpowder","minecraft:gold_ingot","minecraft:diamond","minecraft:ender_pearl","techguns:heavycloth","techguns:mechanicalpartsiron","techguns:mechanicalpartsobsidiansteel","techguns:plasticsheet","techguns:rubberbar","techguns:ingotobsidiansteel"};
    private static final int[] WEIGHTS={15,15,10,10,5,1,1,10,10,5,5,5,5}, MIN={4,4,4,4,2,1,1,4,2,1,3,2,1}, MAX={12,12,8,8,4,1,1,8,4,2,8,5,2};
    private static void weights(GameTestHelper h) {
        int start=0;
        for(int i=0;i<ITEMS.length;i++) {
            for(int edge:new int[]{start,start+WEIGHTS[i]-1}) for(boolean max:new boolean[]{false,true}) {
                var random=new LegacyRandomSource(1) { @Override public int nextInt(int bound) { return bound==97?edge:max?bound-1:0; } };
                var drops=table(h).getRandomItems(params(h,BlockPos.ZERO),random);
                h.assertValueEqual(drops.size(),max?3:1,"Original inclusive one-to-three rolls");
                for(var s:drops) { h.assertValueEqual(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),ITEMS[i],"Every original weight boundary maps to its own item"); h.assertValueEqual(s.getCount(),max?MAX[i]:MIN[i],"Original inclusive count bounds"); }
            }
            start+=WEIGHTS[i];
        }
        h.assertValueEqual(start,97,"Original total weight; no entries silently dropped"); h.succeed();
    }
    private static void craft(GameTestHelper h) {
        var b=chest(h,placed(h)); var cloth=TGContent.MATERIALS.get("heavycloth").get(); long seed;
        for(seed=1;seed<10000;seed++) if(items(table(h).getRandomItems(params(h,b.getBlockPos()),seed)).getOrDefault(cloth,0)>=4) break;
        h.assertTrue(seed<10000,"Deterministic source cloth roll"); b.setLootTableSeed(seed); int amount=items(b).getOrDefault(cloth,0); h.assertTrue(amount>=4,"Real generated chest supplies four cloth");
        var units=new ArrayList<ItemStack>(); for(int slot=0;slot<b.getContainerSize() && units.size()<4;slot++) while(b.getItem(slot).is(cloth) && units.size()<4) units.add(b.removeItem(slot,1));
        var input=CraftingInput.of(3,2,List.of(ItemStack.EMPTY,units.get(0),units.get(1),units.get(2),ItemStack.EMPTY,units.get(3)));
        var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow(); var output=recipe.value().assemble(input);
        h.assertTrue(output.is(ArmorContent.BERET.get()),"Exploration loot crafts wearable original beret"); h.assertValueEqual(items(b).getOrDefault(cloth,0),amount-4,"Craft materials removed from chest"); h.succeed();
    }
    private static void encounter(GameTestHelper h) {
        var p=placed(h); var pos=position(p,NpcSpawnerContent.BLOCK.get()); var b=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(pos);
        h.assertValueEqual(pos,p.templatePosition().offset(3,2,3),"Source guard cell"); h.assertValueEqual(b.remaining(),2,"Two deaths"); h.assertValueEqual(b.maximum(),1,"One live guard"); h.assertValueEqual(b.interval(),200,"Original timer"); h.assertValueEqual(b.range(),1d,"Original radius");
        for(int death=1;death<=2;death++) {
            for(int i=0;i<200;i++) NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,b.getBlockState(),b);
            h.assertValueEqual(b.activeCount(),1,"Only one guarded slot"); var mob=(ZombiePigmanSoldier)h.getLevel().getEntity(b.activeIds().iterator().next());
            h.assertTrue(mob.armed(),"Source soldier equips a working gun"); h.assertValueEqual(mob.spawnerLink(),b.link(),"Finite owner survives native placement"); mob.removeFreeWill(); mob.hurtServer(h.getLevel(),h.getLevel().damageSources().genericKill(),10000);
            h.assertValueEqual(b.remaining(),2-death,"Actual death charged once");
        }
        NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,b.getBlockState(),b); h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Encounter exhausts after two kills"); h.assertValueEqual(chest(h,p).getLootTable(),LOOT,"Combat does not consume unopened reward"); h.succeed();
    }
    private static void natural(GameTestHelper h,int sign) {
        var l=h.getLevel().getServer().getLevel(Level.NETHER); var s=structure(l); ChunkPos chosen=null;
        for(int n=1;n<=256;n++) { var c=new ChunkPos(sign*16,sign*16*n); if(s.findGenerationPoint(context(l,c,l.getSeed())).isPresent()) { chosen=c; break; } }
        h.assertTrue(chosen!=null,"Real Nether cave for source weighted loot candidate"); var chunk=l.getChunk(chosen.x(),chosen.z()); var start=chunk.getStartForStructure(s);
        h.assertTrue(start!=null && start.isValid(),"Native chunk generation saves location start"); var p=(NetherLootPiece)start.getPieces().getFirst(); var box=p.getBoundingBox();
        for(int x=box.minX()>>4;x<=box.maxX()>>4;x++) for(int z=box.minZ()>>4;z<=box.maxZ()>>4;z++) l.getChunk(x,z);
        var pos=position(p,Blocks.CHEST); var chest=(ChestBlockEntity)l.getBlockEntity(pos); h.assertTrue(chest!=null,"Actual terrain stage creates chest"); h.assertValueEqual(chest.getLootTable(),LOOT,"Natural deferred table");
        var guard=(NpcSpawnerBlockEntity)l.getBlockEntity(position(p,NpcSpawnerContent.BLOCK.get())); h.assertValueEqual(guard.remaining(),2,"Natural guard quota"); h.assertValueEqual(guard.entries(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("zombiepigmansoldier"),1)),"Correct natural guard species");
        var altar=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherAltarPiece.TEMPLATE); var other=chunk.getStartForStructure(altar); h.assertTrue(other==null || !other.isValid(),"Shared weighted ticket cannot also generate an altar");
        var holder=l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE,NetherLootPiece.TEMPLATE));
        var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,HolderSet.direct(holder),pos,0,false); h.assertTrue(found!=null,"Native locate finds reward site");
        var data=chest.saveWithFullMetadata(l.registryAccess()); l.getChunk(chosen.x(),chosen.z()); h.assertValueEqual(chest.saveWithFullMetadata(l.registryAccess()),data,"Repeated chunk request does not reset chest");
        com.mojang.logging.LogUtils.getLogger().info("Techguns native NetherLoot01: chunk={}, origin={}, rotation={}, chest={}",chosen,p.templatePosition(),p.getRotation(),pos); h.succeed();
    }
    private static void exclusive(GameTestHelper h) {
        var l=h.getLevel().getServer().getLevel(Level.NETHER); var loot=structure(l); var altar=(NetherAltarStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherAltarPiece.TEMPLATE);
        var acid=(NetherAcidStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherAcidPiece.TEMPLATE);
        var soul=(NetherSoulStructure)l.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(NetherSoulPiece.TEMPLATE);
        boolean original=LocationConfig.ORE_CLUSTERS.get(); int[] hits=new int[5];
        try { for(boolean ores:new boolean[]{false,true}) { LocationConfig.ORE_CLUSTERS.set(ores);
            for(long seed:new long[]{0,1,42}) for(int n=1;n<=64;n++) {
                var pos=new ChunkPos(16,16*n); int roll=context(l,pos,seed).random().nextInt(StructureRules.smallNetherTotal(ores)); int candidate=StructureRules.smallNetherCandidate(roll,ores); hits[candidate]++;
                boolean a=altar.findGenerationPoint(context(l,pos,seed)).isPresent(),b=loot.findGenerationPoint(context(l,pos,seed)).isPresent(),c=acid.findGenerationPoint(context(l,pos,seed)).isPresent();
                boolean d=soul.findGenerationPoint(context(l,pos,seed)).isPresent();
                h.assertTrue((a?1:0)+(b?1:0)+(c?1:0)+(d?1:0)<=1,"Native context restarts the same RNG for all four IDs; tickets are mutually exclusive");
                h.assertTrue(!a || candidate==0,"Altar only consumes original tickets"); h.assertTrue(!b || candidate==2,"Loot only consumes original tickets");
                h.assertTrue(!c || candidate==3,"Acid hole only consumes original tickets");
                h.assertTrue(!d || candidate==1,"Soul platform only consumes its original tickets");
                if(candidate==4) h.assertTrue(!a && !b && !c && !d,"Unported ore cluster stays a skipped site");
            }
        } } finally { LocationConfig.ORE_CLUSTERS.set(original); }
        for(int hit:hits) h.assertTrue(hit>0,"All original candidate ranges exercised across native seeds"); h.succeed();
    }
    private NetherLootGameTests() {}
}
