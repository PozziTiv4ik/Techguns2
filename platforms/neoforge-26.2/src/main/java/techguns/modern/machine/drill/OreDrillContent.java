package techguns.modern.machine.drill;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.registries.*;
import techguns.modern.*;

public final class OreDrillContent {
    private static final DeferredRegister.Blocks REGISTRY=DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final Map<String,DeferredBlock<OreDrillBlock>> BLOCKS=create();
    private static Map<String,DeferredBlock<OreDrillBlock>> create() {
        var blocks=new LinkedHashMap<String,DeferredBlock<OreDrillBlock>>();
        for(String kind:List.of("frame","scaffold","rod","engine","controller")) {
            var block=REGISTRY.registerBlock("oredrill_"+kind,p->new OreDrillBlock(kind,p),p->p.mapColor(MapColor.METAL).strength(4).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
            TGContent.ITEMS.registerSimpleBlockItem(block); blocks.put(kind,block);
        }
        return Collections.unmodifiableMap(blocks);
    }
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,Techguns.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<OreDrillBlockEntity>> CONTROLLER=ENTITIES.register("ore_drill",()->new BlockEntityType<>(OreDrillBlockEntity::new,BLOCKS.get("controller").get()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<OreDrillPart>> PART=ENTITIES.register("ore_drill_part",()->new BlockEntityType<>(OreDrillPart::new,BLOCKS.entrySet().stream().filter(e->!e.getKey().equals("controller")).map(e->e.getValue().get()).toArray(OreDrillBlock[]::new)));
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,Techguns.MOD_ID);
    public static final DeferredHolder<MenuType<?>,MenuType<OreDrillMenu>> MENU=MENUS.register("ore_drill",()->new MenuType<>(OreDrillMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final List<DeferredHolder<SoundEvent,SoundEvent>> SOUNDS=List.of("small","medium","large").stream().map(size->TGContent.SOUNDS.register("machines.oredrill"+size+"work",()->SoundEvent.createVariableRangeEvent(TGContent.id("machines.oredrill"+size+"work")))).toList();
    public static void register(IEventBus bus) { REGISTRY.register(bus); ENTITIES.register(bus); MENUS.register(bus); bus.addListener(OreDrillContent::capabilities); }
    private static void capabilities(RegisterCapabilitiesEvent e) {
        e.registerBlockEntity(Capabilities.Energy.BLOCK,CONTROLLER.get(),(m,side)->m.energy());
        e.registerBlockEntity(Capabilities.Item.BLOCK,CONTROLLER.get(),(m,side)->m.automation());
        e.registerBlockEntity(Capabilities.Fluid.BLOCK,CONTROLLER.get(),(m,side)->m.fluidAutomation());
        // Original slave tile has no resource capabilities. All ports belong to the controller.
    }
    private OreDrillContent() {}
}
