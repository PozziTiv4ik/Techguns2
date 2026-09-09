package techguns.modern.machine.charging;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeSerializer;
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

public final class ChargingStationContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final DeferredBlock<ChargingStationBlock> BLOCK = BLOCKS.registerBlock("charging_station", ChargingStationBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(4).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> ITEM = TGContent.ITEMS.registerSimpleBlockItem(BLOCK);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Techguns.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargingStationBlockEntity>> ENTITY = ENTITIES.register("charging_station", () -> new BlockEntityType<>(ChargingStationBlockEntity::new, BLOCK.get()));
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Techguns.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<ChargingStationMenu>> MENU = MENUS.register("charging_station", () -> new MenuType<>(ChargingStationMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    private static final DeferredRegister<RecipeType<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_TYPE, Techguns.MOD_ID);
    public static final DeferredHolder<RecipeType<?>, RecipeType<ChargingStationRecipe>> RECIPE = RECIPES.register("charging_station", () -> new RecipeType<>() { @Override public String toString() { return "techguns:charging_station"; } });
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Techguns.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ChargingStationRecipe>> SERIALIZER = SERIALIZERS.register("charging_station", () -> new RecipeSerializer<>(ChargingStationRecipe.CODEC, ChargingStationRecipe.STREAM_CODEC));
    public static final DeferredHolder<SoundEvent, SoundEvent> WORK = TGContent.SOUNDS.register("machines.chargingstationwork", () -> SoundEvent.createVariableRangeEvent(TGContent.id("machines.chargingstationwork")));
    public static void register(IEventBus bus) { BLOCKS.register(bus); ENTITIES.register(bus); MENUS.register(bus); RECIPES.register(bus); SERIALIZERS.register(bus); bus.addListener(ChargingStationContent::capabilities); }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ENTITY.get(), (m, side) -> m.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ENTITY.get(), (m, side) -> m.automation());
    }
    private ChargingStationContent() {}
}
