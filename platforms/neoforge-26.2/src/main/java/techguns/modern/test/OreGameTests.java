package techguns.modern.test;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.OreDefinition;
import techguns.core.Ores;
import techguns.modern.TGContent;
import techguns.modern.world.TGOreConfig;
import techguns.modern.world.TGOreContent;

final class OreGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        for (OreDefinition ore : Ores.ALL) registry.register("ore_mining_"+ore.id(), () -> h -> mining(h,ore));
        registry.register("ore_worldgen_registry_and_codec", () -> OreGameTests::registries);
        registry.register("ore_generation_and_controls", () -> OreGameTests::generation);
        registry.register("ore_dimension_guard", () -> OreGameTests::dimensions);
        if (Boolean.getBoolean("techguns.worldgenTest")) registry.register("ore_natural_chunks", () -> OreGameTests::naturalChunks);
    }
    private static void mining(GameTestHelper h, OreDefinition ore) {
        Block block = TGOreContent.ORES.get(ore.id()).get(); var state = block.defaultBlockState();
        BlockPos pos = h.absolutePos(new BlockPos(3,2,3));
        h.assertValueEqual(state.getDestroySpeed(h.getLevel(),pos),ore.hardness(),"Original mining hardness");
        h.assertValueEqual(block.getExplosionResistance(),2f,"GenericBlock resistance is independent of ore hardness");
        h.assertValueEqual(state.getLightEmission(h.getLevel(),pos),ore.light(),"Uranium alone emits level-four light");
        List<Item> picks = List.of(Items.WOODEN_PICKAXE, Items.GOLDEN_PICKAXE, Items.STONE_PICKAXE, Items.COPPER_PICKAXE,
                                  Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, Items.DIAMOND_SHOVEL);
        int[] levels = {0,0,1,1,2,3,4,0};
        var player = WeaponGameTests.player(h);
        for (int i=0;i<picks.size();i++) {
            ItemStack tool = new ItemStack(picks.get(i)); player.setItemInHand(InteractionHand.MAIN_HAND,tool);
            h.assertValueEqual(player.hasCorrectToolForDrops(state),levels[i]>=ore.miningLevel(),"Harvest tier "+picks.get(i));
        }
        for (int mode=0;mode<3;mode++) {
            var tool = new ItemStack(Items.DIAMOND_PICKAXE);
            if (mode>0) tool.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(mode==1 ? Enchantments.FORTUNE : Enchantments.SILK_TOUCH),mode==1 ? 3 : 1);
            for (int trial=0;trial<12;trial++) {
                var drops=Block.getDrops(state,h.getLevel(),pos,null,player,tool);
                h.assertValueEqual(drops.size(),1,"One ore block with ordinary, Fortune or Silk Touch pick");
                h.assertTrue(drops.getFirst().is(block.asItem()),"No raw-metal substitution");
                h.assertValueEqual(drops.getFirst().getCount(),1,"Fortune does not multiply original self-drops");
            }
        }
        var ops=h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var codec=net.minecraft.world.level.block.state.BlockState.CODEC;
        h.assertTrue(codec.parse(ops,codec.encodeStart(ops,state).getOrThrow()).getOrThrow().equals(state),"Split metadata block survives state codec");
        h.succeed();
    }
    private static PlacedFeature feature(GameTestHelper h) {
        return h.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE,TGContent.id("ores"))).value();
    }
    private static void registries(GameTestHelper h) {
        PlacedFeature feature=feature(h); int checked=0;
        h.assertTrue(feature.feature().value().feature()==TGOreContent.ORE_FEATURE.get(),"Data pack resolves registered ore feature");
        h.assertTrue(feature.placement().isEmpty(),"One feature call, no extra count or scatter modifier");
        for (var biome : h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList()) {
            var steps=biome.value().getGenerationSettings().features();
            long count=steps.size()<=GenerationStep.Decoration.UNDERGROUND_ORES.ordinal() ? 0 :
                    steps.get(GenerationStep.Decoration.UNDERGROUND_ORES.ordinal()).stream().filter(f -> f.value()==feature).count();
            h.assertValueEqual(count,biome.is(BiomeTags.IS_OVERWORLD) ? 1L : 0L,"Biome modifier appears exactly once: "+biome.key().identifier());
            checked++;
        }
        h.assertTrue(checked>50,"Real biome registry checked, not an empty fixture");
        var ops=h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json=PlacedFeature.DIRECT_CODEC.encodeStart(ops,feature).getOrThrow();
        h.assertTrue(PlacedFeature.DIRECT_CODEC.parse(ops,json).getOrThrow().feature().value()==feature.feature().value(),"Configured reference round trip");
        var titanium = new ItemStack(TGOreContent.ORES.get("ore_titanium").get());
        var processed = TGContent.MATERIALS.get("oretitanium").toStack();
        var ilmenite=TagKey.create(Registries.ITEM,net.minecraft.resources.Identifier.parse("c:ores/ilmenite"));
        var pure=TagKey.create(Registries.ITEM,net.minecraft.resources.Identifier.parse("c:ores/titanium"));
        h.assertTrue(titanium.is(ilmenite) && !titanium.is(pure) && processed.is(pure),"Ilmenite and processed titanium remain distinct");
        h.succeed();
    }
    private static final class TraceRandom extends LegacyRandomSource {
        final List<Integer> bounds=new ArrayList<>();
        TraceRandom(long seed) { super(seed); }
        @Override public int nextInt(int bound) { bounds.add(bound); return super.nextInt(bound); }
    }
    private static void generation(GameTestHelper h) {
        // This volume belongs only to the dedicated GameTest world, above all flat test structures.
        var level=h.getLevel(); BlockPos base=new BlockPos(-160,0,-160);
        BlockPos low=base.offset(-4,0,-4), high=base.offset(19,84,19);
        Map<String,Boolean> saved=new LinkedHashMap<>();
        TGOreConfig.ORE_GENERATION.forEach((id,value) -> saved.put(id,value.get()));
        try {
            for (BlockPos pos:BlockPos.betweenClosed(low,high)) level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            // Finished flat-world chunks do not retain the heightmap normally prepared before feature generation.
            for (int x=Math.floorDiv(low.getX(),16);x<=Math.floorDiv(high.getX(),16);x++)
                for (int z=Math.floorDiv(low.getZ(),16);z<=Math.floorDiv(high.getZ(),16);z++)
                    net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(level.getChunk(x,z),
                            java.util.Set.of(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG));
            PlacedFeature feature=feature(h);
            TGOreConfig.ORE_GENERATION.values().forEach(v -> v.set(false));
            var disabled=new TraceRandom(12);
            h.assertTrue(!feature.place(level,level.getChunkSource().getGenerator(),disabled,base),"All disabled generates nothing");
            h.assertTrue(disabled.bounds.isEmpty(),"Disabled generation consumes no randomness");
            // Each legacy flag independently controls exactly its original attempts.
            int[][] rules={{3,12,75},{3,10,55},{2,8,45},{2,4,20},{4,5,28}};
            for (int index=0;index<Ores.ALL.size();index++) {
                OreDefinition ore=Ores.ALL.get(index);
                TGOreConfig.ORE_GENERATION.get(ore.id()).set(true);
                var random=new TraceRandom(1234+index);
                feature.place(level,level.getChunkSource().getGenerator(),random,base.offset(7,230,9));
                TGOreConfig.ORE_GENERATION.get(ore.id()).set(false);
                List<Integer> expected=new ArrayList<>(); expected.add(rules[index][0]);
                for (int n=0;n<rules[index][1];n++) expected.addAll(List.of(16,rules[index][2],16,3,3));
                h.assertValueEqual(random.bounds,expected,"One size draw per chunk and original X/Y/Z draw sequence: "+ore.id());
                int count=0;
                for (BlockPos pos:BlockPos.betweenClosed(low,high)) if (level.getBlockState(pos).is(TGOreContent.ORES.get(ore.id()).get())) {
                    count++;
                    h.assertTrue(pos.getY()>=ore.minY()-4 && pos.getY()<=ore.maxY()+1,"Absolute heights, independent of feature origin Y");
                }
                h.assertTrue(count>0,"Feature places actual "+ore.id()+" into stone at a negative chunk coordinate");
            }
            // No tier of deepslate or dirt is silently made a legacy stone host.
            for (BlockPos pos:BlockPos.betweenClosed(low,high)) level.setBlock(pos,
                    (pos.getY()%2==0 ? Blocks.DEEPSLATE : Blocks.DIRT).defaultBlockState(),2);
            TGOreConfig.ORE_GENERATION.values().forEach(v -> v.set(true));
            var all=new TraceRandom(57);
            h.assertTrue(!feature.place(level,level.getChunkSource().getGenerator(),all,base),"Ore does not replace unsupported hosts");
            List<Integer> ordered=new ArrayList<>();
            for (int[] rule:rules) {
                ordered.add(rule[0]);
                for (int n=0;n<rule[1];n++) ordered.addAll(List.of(16,rule[2],16,3,3));
            }
            h.assertValueEqual(all.bounds,ordered,"All-enabled ore generation retains original type order and random size boundaries");
        } finally {
            saved.forEach((id,value) -> TGOreConfig.ORE_GENERATION.get(id).set(value));
            for (BlockPos pos:BlockPos.betweenClosed(low,high)) level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        }
        h.succeed();
    }
    private static void dimensions(GameTestHelper h) {
        for (var key:List.of(Level.NETHER,Level.END)) {
            var level=h.getLevel().getServer().getLevel(key); h.assertTrue(level!=null,"Test dimension exists");
            var random=new TraceRandom(123);
            h.assertTrue(!feature(h).place(level,level.getChunkSource().getGenerator(),random,new BlockPos(0,10,0)),"No ore outside Overworld: "+key);
            h.assertTrue(random.bounds.isEmpty(),"Dimension guard runs before random draws");
        }
        h.succeed();
    }
    private static void naturalChunks(GameTestHelper h) {
        var level=h.getLevel();
        h.assertTrue(level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator,
                "Separate test pack must create real normal terrain, not the default flat GameTest world");
        Map<Block,Integer> counts=new LinkedHashMap<>();
        TGOreContent.ORES.values().forEach(block -> counts.put(block.get(),0));
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for (int x=-4;x<0;x++) for (int z=-4;z<0;z++) {
            // Loading a fresh chunk executes the actual noise, carver and biome-decoration pipeline.
            var chunk=level.getChunk(x,z);
            for (int dx=0;dx<16;dx++) for (int dz=0;dz<16;dz++) for (int y=level.getMinY();y<100;y++) {
                pos.set(x*16+dx,y,z*16+dz);
                Block block=chunk.getBlockState(pos).getBlock();
                if (counts.containsKey(block)) {
                    counts.put(block,counts.get(block)+1);
                    h.assertTrue(y>=0 && y<=80,"Natural ore stays near the legacy absolute height envelope");
                }
            }
        }
        counts.forEach((block,count) -> h.assertTrue(count>0,"Real generated chunks contain "+BuiltInRegistries.BLOCK.getKey(block)));
        com.mojang.logging.LogUtils.getLogger().info("Techguns natural ore counts across 16 new chunks: {}",counts);
        h.succeed();
    }
}
