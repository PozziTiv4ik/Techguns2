package techguns.modern.world;

import java.util.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import techguns.core.NetherMetal;
import techguns.modern.*;

public final class NetherMetalContent {
    private static final DeferredRegister.Blocks REGISTRY=DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final Map<String,DeferredBlock<Block>> BLOCKS=create();
    private static Map<String,DeferredBlock<Block>> create() {
        var blocks=new LinkedHashMap<String,DeferredBlock<Block>>();
        for(var variant:NetherMetal.ALL) {
            var block=REGISTRY.registerBlock(variant.id(),Block::new,p->p.mapColor(MapColor.METAL).strength(8,8)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(s->variant.light()));
            TGContent.ITEMS.registerSimpleBlockItem(block); blocks.put(variant.id(),block);
        }
        return Collections.unmodifiableMap(blocks);
    }
    public static void register(IEventBus bus) { REGISTRY.register(bus); }
    private NetherMetalContent() {}
}
