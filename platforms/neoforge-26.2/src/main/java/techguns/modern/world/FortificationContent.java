package techguns.modern.world;

import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import techguns.modern.*;

public final class FortificationContent {
    private static final DeferredRegister.Blocks REGISTRY=DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final DeferredHolder<SoundEvent,SoundEvent> DOOR_SOUND=TGContent.SOUNDS.register("blocks.metaldooropen",()->SoundEvent.createVariableRangeEvent(TGContent.id("blocks.metaldooropen")));
    public static final DeferredBlock<SandbagBlock> SANDBAGS=REGISTRY.registerBlock("sandbags",SandbagBlock::new,
            p->p.mapColor(MapColor.COLOR_BROWN).strength(6,9).sound(SoundType.WOOL).noOcclusion().forceSolidOff().dynamicShape());
    public static final DeferredBlock<BunkerDoorBlock> DOOR=REGISTRY.registerBlock("bunkerdoor",BunkerDoorBlock::new,
            p->p.mapColor(MapColor.METAL).strength(8,8).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> DOOR_ITEM=TGContent.ITEMS.registerItem("item_bunkerdoor",p->new BlockItem(DOOR.get(),p));
    public static final Map<String,DeferredBlock<IndustrialLampBlock>> LAMPS=createLamps();
    private static Map<String,DeferredBlock<IndustrialLampBlock>> createLamps() {
        var out=new LinkedHashMap<String,DeferredBlock<IndustrialLampBlock>>();
        for(var name:List.of("lamp_yellow","lamp_white","lantern_yellow","lantern_white")) {
            var b=REGISTRY.registerBlock(name,p->new IndustrialLampBlock(name.startsWith("lantern"),p),
                    p->p.mapColor(MapColor.COLOR_YELLOW).strength(4,4).sound(SoundType.GLASS).requiresCorrectToolForDrops().noOcclusion().forceSolidOff().lightLevel(s->15));
            TGContent.ITEMS.registerItem(name,p->new IndustrialLampItem(b.get(),p)); out.put(name,b);
        }
        return Collections.unmodifiableMap(out);
    }
    static { TGContent.ITEMS.registerSimpleBlockItem(SANDBAGS); }
    public static void register(IEventBus bus) { REGISTRY.register(bus); }
    private FortificationContent() {}
}
