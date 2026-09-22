package techguns.modern.world;

import java.util.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import techguns.core.BuildingBlocks;
import techguns.modern.*;

public final class BuildingContent {
    private static final DeferredRegister.Blocks REGISTRY=DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final Map<String,DeferredBlock<Block>> BLOCKS=create();
    private static Map<String,DeferredBlock<Block>> create() {
        var blocks=new LinkedHashMap<String,DeferredBlock<Block>>();
        for(var v:BuildingBlocks.ALL) {
            var block=REGISTRY.registerBlock(v.id(),p->v.ladder()?new MetalLadderBlock(p):new Block(p),p->{
                p.mapColor(v.family().equals("concrete")?MapColor.STONE:MapColor.METAL)
                        .strength(v.hardness(),v.hardness()).sound(v.family().equals("concrete")?SoundType.STONE:SoundType.METAL).requiresCorrectToolForDrops();
                if(v.ladder()) p.noOcclusion().forceSolidOff();
                return p;
            });
            TGContent.ITEMS.registerSimpleBlockItem(block); blocks.put(v.id(),block);
        }
        return Collections.unmodifiableMap(blocks);
    }
    public static void register(IEventBus bus) { REGISTRY.register(bus); }
    private BuildingContent() {}
}
