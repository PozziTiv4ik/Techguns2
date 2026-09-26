package techguns.modern.world;

import java.util.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import techguns.core.CamouflageNets;
import techguns.modern.*;

public final class CamouflageNetContent {
    private static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final Map<String, DeferredBlock<CamouflageNetBlock>> BLOCKS = createBlocks();
    private static Map<String, DeferredBlock<CamouflageNetBlock>> createBlocks() {
        var blocks = new LinkedHashMap<String, DeferredBlock<CamouflageNetBlock>>();
        for (var variant : CamouflageNets.ALL) {
            var block = REGISTRY.registerBlock(variant.id(), p -> new CamouflageNetBlock(variant.canopy(), p),
                    p -> p.mapColor(MapColor.COLOR_GREEN).strength(2, 2).sound(SoundType.WOOL).noOcclusion().forceSolidOff().dynamicShape());
            blocks.put(variant.id(), block);
            TGContent.ITEMS.registerSimpleBlockItem(block);
        }
        return Collections.unmodifiableMap(blocks);
    }
    public static void register(IEventBus bus) { REGISTRY.register(bus); }
    private CamouflageNetContent() {}
}
