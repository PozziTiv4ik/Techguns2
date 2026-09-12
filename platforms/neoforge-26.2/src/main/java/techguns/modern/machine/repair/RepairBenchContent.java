package techguns.modern.machine.repair;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;
import techguns.modern.TGContent;
import techguns.modern.Techguns;

public final class RepairBenchContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final DeferredBlock<RepairBenchBlock> BLOCK = BLOCKS.registerBlock("repair_bench", RepairBenchBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(4).sound(SoundType.METAL).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> ITEM = TGContent.ITEMS.registerSimpleBlockItem(BLOCK);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Techguns.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RepairBenchBlockEntity>> ENTITY = ENTITIES.register("repair_bench", () -> new BlockEntityType<>(RepairBenchBlockEntity::new, BLOCK.get()));
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Techguns.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<RepairBenchMenu>> MENU = MENUS.register("repair_bench", () -> new MenuType<>(RepairBenchMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static void register(IEventBus bus) { BLOCKS.register(bus); ENTITIES.register(bus); MENUS.register(bus); bus.addListener(RepairBenchContent::capabilities); }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ENTITY.get(), (bench, side) -> bench.automation());
    }
    private RepairBenchContent() {}
}
