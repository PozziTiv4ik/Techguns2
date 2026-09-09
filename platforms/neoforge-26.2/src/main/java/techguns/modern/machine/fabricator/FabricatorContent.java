package techguns.modern.machine.fabricator;

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

public final class FabricatorContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final DeferredBlock<FabricatorBlock> HOUSING=part("fabricator_housing",FabricatorBlock.Part.HOUSING);
    public static final DeferredBlock<FabricatorBlock> GLASS=part("fabricator_glass",FabricatorBlock.Part.GLASS);
    public static final DeferredBlock<FabricatorBlock> CONTROLLER=part("fabricator_controller",FabricatorBlock.Part.CONTROLLER);
    public static final DeferredItem<BlockItem> HOUSING_ITEM=TGContent.ITEMS.registerSimpleBlockItem(HOUSING), GLASS_ITEM=TGContent.ITEMS.registerSimpleBlockItem(GLASS), CONTROLLER_ITEM=TGContent.ITEMS.registerSimpleBlockItem(CONTROLLER);
    private static DeferredBlock<FabricatorBlock> part(String id,FabricatorBlock.Part kind) {
        return BLOCKS.registerBlock(id,p -> new FabricatorBlock(kind,p),p -> p.mapColor(MapColor.METAL).strength(4).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    }
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,Techguns.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<FabricatorBlockEntity>> CONTROLLER_ENTITY=ENTITIES.register("fabricator",() -> new BlockEntityType<>(FabricatorBlockEntity::new,CONTROLLER.get()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<FabricatorPartBlockEntity>> PART_ENTITY=ENTITIES.register("fabricator_part",() -> new BlockEntityType<>(FabricatorPartBlockEntity::new,HOUSING.get(),GLASS.get()));
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,Techguns.MOD_ID);
    public static final DeferredHolder<MenuType<?>,MenuType<FabricatorMenu>> MENU=MENUS.register("fabricator",() -> new MenuType<>(FabricatorMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    private static final DeferredRegister<RecipeType<?>> RECIPES=DeferredRegister.create(Registries.RECIPE_TYPE,Techguns.MOD_ID);
    public static final DeferredHolder<RecipeType<?>,RecipeType<FabricatorRecipe>> RECIPE=RECIPES.register("fabricator",() -> new RecipeType<>() {
        @Override public String toString() { return "techguns:fabricator"; }
    });
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,Techguns.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<FabricatorRecipe>> SERIALIZER=SERIALIZERS.register("fabricator",() -> new RecipeSerializer<>(FabricatorRecipe.CODEC,FabricatorRecipe.STREAM_CODEC));
    public static final DeferredHolder<SoundEvent,SoundEvent> WORK=TGContent.SOUNDS.register("machines.fabricatorwork",() -> SoundEvent.createVariableRangeEvent(TGContent.id("machines.fabricatorwork")));
    public static void register(IEventBus bus) { BLOCKS.register(bus); ENTITIES.register(bus); MENUS.register(bus); RECIPES.register(bus); SERIALIZERS.register(bus); bus.addListener(FabricatorContent::capabilities); }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK,CONTROLLER_ENTITY.get(),(m,side) -> m.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK,CONTROLLER_ENTITY.get(),(m,side) -> m.automation());
        event.registerBlockEntity(Capabilities.Energy.BLOCK,PART_ENTITY.get(),(p,side) -> p.connector()==2 ? p.energy() : null);
        event.registerBlockEntity(Capabilities.Item.BLOCK,PART_ENTITY.get(),(p,side) -> p.connector()==2 ? p.items() : null);
    }
    private FabricatorContent() {}
}
