package techguns.modern.world;

import java.util.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import techguns.core.NeonLights;
import techguns.modern.*;

/** GenericBlockMetaEnum uses full opaque cubes, despite Material.GLASS's harvest and redstone rules. */
public final class NeonContent {
    private static final DeferredRegister.Blocks REGISTRY=DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final Map<String,DeferredBlock<Block>> BLOCKS=create();
    private static Map<String,DeferredBlock<Block>> create() {
        var blocks=new LinkedHashMap<String,DeferredBlock<Block>>();
        for(var variant:NeonLights.ALL) {
            var block=REGISTRY.registerBlock(variant.id(),Block::new,p->p.mapColor(MapColor.COLOR_YELLOW).strength(4,4)
                    .sound(SoundType.GLASS).instrument(NoteBlockInstrument.HAT).lightLevel(s->15).isRedstoneConductor((state,level,pos)->false));
            TGContent.ITEMS.registerSimpleBlockItem(block); blocks.put(variant.id(),block);
        }
        return Collections.unmodifiableMap(blocks);
    }
    public static Block fromMetadata(int metadata) { return BLOCKS.get(NeonLights.byMetadata(metadata).id()).get(); }
    public static void register(IEventBus bus) { REGISTRY.register(bus); }
    private NeonContent() {}
}
