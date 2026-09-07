package techguns.modern.machine;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.modern.TGContent;
import techguns.modern.Techguns;

public final class TGMachineContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final DeferredBlock<AmmoPressBlock> AMMO_PRESS = BLOCKS.registerBlock("ammo_press", AmmoPressBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(4).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> AMMO_PRESS_ITEM = TGContent.ITEMS.registerSimpleBlockItem(AMMO_PRESS);
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESS_WORK1 = TGContent.SOUNDS.register("machines.ammopresswork1", () -> SoundEvent.createVariableRangeEvent(TGContent.id("machines.ammopresswork1")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESS_WORK2 = TGContent.SOUNDS.register("machines.ammopresswork2", () -> SoundEvent.createVariableRangeEvent(TGContent.id("machines.ammopresswork2")));
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Techguns.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AmmoPressBlockEntity>> AMMO_PRESS_ENTITY = BLOCK_ENTITIES.register("ammo_press",
            () -> new BlockEntityType<>(AmmoPressBlockEntity::new, AMMO_PRESS.get()));
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Techguns.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<AmmoPressMenu>> AMMO_PRESS_MENU = MENUS.register("ammo_press",
            () -> new MenuType<>(AmmoPressMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Techguns.MOD_ID);
    public static final DeferredHolder<RecipeType<?>, RecipeType<AmmoPressRecipe>> AMMO_PRESS_RECIPE = RECIPE_TYPES.register("ammo_press", () -> new RecipeType<>() {
        @Override public String toString() { return "techguns:ammo_press"; }
    });
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Techguns.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AmmoPressRecipe>> AMMO_PRESS_SERIALIZER = SERIALIZERS.register("ammo_press",
            () -> new RecipeSerializer<>(AmmoPressRecipe.CODEC, AmmoPressRecipe.STREAM_CODEC));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus); BLOCK_ENTITIES.register(bus); MENUS.register(bus); RECIPE_TYPES.register(bus); SERIALIZERS.register(bus);
        bus.addListener(TGMachineContent::capabilities);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, AMMO_PRESS_ENTITY.get(), (machine, side) -> machine.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, AMMO_PRESS_ENTITY.get(), (machine, side) -> machine.automation());
    }
    private TGMachineContent() {}
}
