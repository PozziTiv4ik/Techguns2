package techguns.modern.world;

import java.util.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import techguns.modern.*;

/** Source TYPE2 becomes two persistent item/block identities; native stair shapes and waterlogging. */
public final class MetalStairContent {
    private static final DeferredRegister.Blocks REGISTRY=DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final List<DeferredBlock<StairBlock>> BLOCKS=new ArrayList<>();
    static {
        for(String name:List.of("stairs_metal","stairs_metal_dark")) {
            var block=REGISTRY.registerBlock(name,p->new StairBlock(Blocks.IRON_BLOCK.defaultBlockState(),p),
                p->p.mapColor(MapColor.METAL).strength(8,8).sound(SoundType.METAL).requiresCorrectToolForDrops());
            TGContent.ITEMS.registerSimpleBlockItem(block);BLOCKS.add(block);
        }
    }
    public static void register(IEventBus bus) { REGISTRY.register(bus); }
    private MetalStairContent() {}
}
