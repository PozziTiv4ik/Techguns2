package techguns.modern.machine.grinder;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.registries.*;
import techguns.modern.*;

public final class GrinderContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final DeferredBlock<GrinderBlock> BLOCK = BLOCKS.registerBlock("grinder",GrinderBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(4).sound(SoundType.METAL).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> ITEM = TGContent.ITEMS.registerSimpleBlockItem(BLOCK);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,Techguns.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<GrinderBlockEntity>> ENTITY = ENTITIES.register("grinder",() -> new BlockEntityType<>(GrinderBlockEntity::new,BLOCK.get()));
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU,Techguns.MOD_ID);
    public static final DeferredHolder<MenuType<?>,MenuType<GrinderMenu>> MENU = MENUS.register("grinder",() -> new MenuType<>(GrinderMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    private static final DeferredRegister<RecipeType<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_TYPE,Techguns.MOD_ID);
    public static final DeferredHolder<RecipeType<?>,RecipeType<GrinderRecipe>> RECIPE = RECIPES.register("grinder",() -> new RecipeType<>() { @Override public String toString() { return "techguns:grinder"; } });
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER,Techguns.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<GrinderRecipe>> SERIALIZER = SERIALIZERS.register("grinder",() -> new RecipeSerializer<>(GrinderRecipe.CODEC,GrinderRecipe.STREAM_CODEC));
    public static final DeferredHolder<SoundEvent,SoundEvent> START = TGContent.SOUNDS.register("machines.grinder.start",() -> SoundEvent.createVariableRangeEvent(TGContent.id("machines.grinder.start")));
    public static final DeferredHolder<SoundEvent,SoundEvent> WORK = TGContent.SOUNDS.register("machines.grinder.work",() -> SoundEvent.createVariableRangeEvent(TGContent.id("machines.grinder.work")));
    public static void register(IEventBus bus) { BLOCKS.register(bus); ENTITIES.register(bus); MENUS.register(bus); RECIPES.register(bus); SERIALIZERS.register(bus); bus.addListener(GrinderContent::capabilities); }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK,ENTITY.get(),(m,side) -> m.automation());
        event.registerBlockEntity(Capabilities.Energy.BLOCK,ENTITY.get(),(m,side) -> m.energy());
    }
    private GrinderContent() {}
}
