package techguns.modern.machine.reaction;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
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

public final class ReactionContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final DeferredBlock<ReactionChamberBlock> HOUSING=part("reactionchamber_housing",ReactionChamberBlock.Part.HOUSING);
    public static final DeferredBlock<ReactionChamberBlock> GLASS=part("reactionchamber_glass",ReactionChamberBlock.Part.GLASS);
    public static final DeferredBlock<ReactionChamberBlock> CONTROLLER=part("reactionchamber_controller",ReactionChamberBlock.Part.CONTROLLER);
    public static final DeferredItem<BlockItem> HOUSING_ITEM=TGContent.ITEMS.registerSimpleBlockItem(HOUSING);
    public static final DeferredItem<BlockItem> GLASS_ITEM=TGContent.ITEMS.registerSimpleBlockItem(GLASS);
    public static final DeferredItem<BlockItem> CONTROLLER_ITEM=TGContent.ITEMS.registerSimpleBlockItem(CONTROLLER);
    private static DeferredBlock<ReactionChamberBlock> part(String id,ReactionChamberBlock.Part kind) {
        return BLOCKS.registerBlock(id,p -> new ReactionChamberBlock(kind,p),p -> p.mapColor(MapColor.METAL).strength(4).sound(SoundType.METAL).noOcclusion().dynamicShape().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    }
    public static Block block(ReactionChamberBlock.Part kind) { return switch(kind) { case HOUSING -> HOUSING.get(); case GLASS -> GLASS.get(); case CONTROLLER -> CONTROLLER.get(); }; }
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,Techguns.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ReactionChamberBlockEntity>> CONTROLLER_ENTITY=ENTITIES.register("reaction_chamber",() -> new BlockEntityType<>(ReactionChamberBlockEntity::new,CONTROLLER.get()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ReactionPartBlockEntity>> PART_ENTITY=ENTITIES.register("reaction_chamber_part",() -> new BlockEntityType<>(ReactionPartBlockEntity::new,HOUSING.get(),GLASS.get()));
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,Techguns.MOD_ID);
    public static final DeferredHolder<MenuType<?>,MenuType<ReactionChamberMenu>> MENU=MENUS.register("reaction_chamber",() -> new MenuType<>(ReactionChamberMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    private static final DeferredRegister<RecipeType<?>> RECIPES=DeferredRegister.create(Registries.RECIPE_TYPE,Techguns.MOD_ID);
    public static final DeferredHolder<RecipeType<?>,RecipeType<ReactionChamberRecipe>> RECIPE=RECIPES.register("reaction_chamber",() -> new RecipeType<>() {
        @Override public String toString() { return "techguns:reaction_chamber"; }
    });
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,Techguns.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<ReactionChamberRecipe>> SERIALIZER=SERIALIZERS.register("reaction_chamber",() -> new RecipeSerializer<>(ReactionChamberRecipe.CODEC,ReactionChamberRecipe.STREAM_CODEC));
    public static final DeferredHolder<SoundEvent,SoundEvent> HEAT_WORK=sound("machines.rc_heatraywork"), BEEP=sound("machines.rc_beep"), WARNING=sound("machines.rc_warning");
    private static DeferredHolder<SoundEvent,SoundEvent> sound(String id) { return TGContent.SOUNDS.register(id,() -> SoundEvent.createVariableRangeEvent(TGContent.id(id))); }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ENTITIES.register(bus); MENUS.register(bus); RECIPES.register(bus); SERIALIZERS.register(bus); bus.addListener(ReactionContent::capabilities); }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK,CONTROLLER_ENTITY.get(),(m,side) -> m.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK,CONTROLLER_ENTITY.get(),(m,side) -> m.automation());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK,CONTROLLER_ENTITY.get(),(m,side) -> m.tank());
        event.registerBlockEntity(Capabilities.Energy.BLOCK,PART_ENTITY.get(),(p,side) -> p.connector()==3 ? p.energy() : null);
        event.registerBlockEntity(Capabilities.Item.BLOCK,PART_ENTITY.get(),(p,side) -> p.connector()==2 ? p.items() : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK,PART_ENTITY.get(),(p,side) -> p.connector()==2 ? p.fluids() : null);
    }
    private ReactionContent() {}
}
