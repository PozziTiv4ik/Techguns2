package techguns.modern.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.OreDefinition;
import techguns.core.Ores;
import techguns.modern.TGContent;
import techguns.modern.Techguns;

public final class TGOreContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final Map<String, DeferredBlock<Block>> ORES = registerOres();
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Techguns.MOD_ID);
    public static final DeferredHolder<Feature<?>, TGOreFeature> ORE_FEATURE = FEATURES.register("ores", TGOreFeature::new);
    private static Map<String, DeferredBlock<Block>> registerOres() {
        Map<String, DeferredBlock<Block>> ores = new LinkedHashMap<>();
        for (OreDefinition ore : Ores.ALL) {
            // GenericBlock's setHardness(2) gives all five original variants blast resistance 2.
            // Their per-state hardness override affects mining only.
            var block = BLOCKS.registerBlock(ore.id(), Block::new, properties -> properties.mapColor(MapColor.STONE)
                    .strength(ore.hardness(), 2).sound(SoundType.STONE).requiresCorrectToolForDrops().lightLevel(state -> ore.light()));
            TGContent.ITEMS.registerSimpleBlockItem(block);
            ores.put(ore.id(), block);
        }
        return Collections.unmodifiableMap(ores);
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); FEATURES.register(bus); }
    private TGOreContent() {}
}
