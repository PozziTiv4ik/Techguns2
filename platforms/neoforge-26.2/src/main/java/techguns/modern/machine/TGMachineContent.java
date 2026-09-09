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
    public static final DeferredBlock<MetalPressBlock> METAL_PRESS = BLOCKS.registerBlock("metal_press", MetalPressBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(4).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> METAL_PRESS_ITEM = TGContent.ITEMS.registerSimpleBlockItem(METAL_PRESS);
    public static final DeferredBlock<BlastFurnaceBlock> BLAST_FURNACE = BLOCKS.registerBlock("blast_furnace", BlastFurnaceBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(4).sound(SoundType.METAL).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> BLAST_FURNACE_ITEM = TGContent.ITEMS.registerSimpleBlockItem(BLAST_FURNACE);
    public static final DeferredBlock<ChemLabBlock> CHEM_LAB = BLOCKS.registerBlock("chem_lab",ChemLabBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(4).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> CHEM_LAB_ITEM = TGContent.ITEMS.registerSimpleBlockItem(CHEM_LAB);
    public static final DeferredHolder<SoundEvent,SoundEvent> CHEM_WORK = TGContent.SOUNDS.register("machines.chemlabwork", () -> SoundEvent.createVariableRangeEvent(TGContent.id("machines.chemlabwork")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESS_WORK1 = TGContent.SOUNDS.register("machines.ammopresswork1", () -> SoundEvent.createVariableRangeEvent(TGContent.id("machines.ammopresswork1")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESS_WORK2 = TGContent.SOUNDS.register("machines.ammopresswork2", () -> SoundEvent.createVariableRangeEvent(TGContent.id("machines.ammopresswork2")));
    public static final DeferredHolder<SoundEvent, SoundEvent> METAL_WORK = TGContent.SOUNDS.register("machines.metalpresswork", () -> SoundEvent.createVariableRangeEvent(TGContent.id("machines.metalpresswork")));
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Techguns.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AmmoPressBlockEntity>> AMMO_PRESS_ENTITY = BLOCK_ENTITIES.register("ammo_press",
            () -> new BlockEntityType<>(AmmoPressBlockEntity::new, AMMO_PRESS.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MetalPressBlockEntity>> METAL_PRESS_ENTITY = BLOCK_ENTITIES.register("metal_press",
            () -> new BlockEntityType<>(MetalPressBlockEntity::new, METAL_PRESS.get()));
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Techguns.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlastFurnaceBlockEntity>> BLAST_FURNACE_ENTITY = BLOCK_ENTITIES.register("blast_furnace",
            () -> new BlockEntityType<>(BlastFurnaceBlockEntity::new, BLAST_FURNACE.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<AmmoPressMenu>> AMMO_PRESS_MENU = MENUS.register("ammo_press",
            () -> new MenuType<>(AmmoPressMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<MetalPressMenu>> METAL_PRESS_MENU = MENUS.register("metal_press",
            () -> new MenuType<>(MetalPressMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Techguns.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<BlastFurnaceMenu>> BLAST_FURNACE_MENU = MENUS.register("blast_furnace",
            () -> new MenuType<>(BlastFurnaceMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<RecipeType<?>, RecipeType<AmmoPressRecipe>> AMMO_PRESS_RECIPE = RECIPE_TYPES.register("ammo_press", () -> new RecipeType<>() {
        @Override public String toString() { return "techguns:ammo_press"; }
    });
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Techguns.MOD_ID);
    public static final DeferredHolder<RecipeType<?>, RecipeType<MetalPressRecipe>> METAL_PRESS_RECIPE = RECIPE_TYPES.register("metal_press", () -> new RecipeType<>() {
        @Override public String toString() { return "techguns:metal_press"; }
    });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AmmoPressRecipe>> AMMO_PRESS_SERIALIZER = SERIALIZERS.register("ammo_press",
            () -> new RecipeSerializer<>(AmmoPressRecipe.CODEC, AmmoPressRecipe.STREAM_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MetalPressRecipe>> METAL_PRESS_SERIALIZER = SERIALIZERS.register("metal_press",
            () -> new RecipeSerializer<>(MetalPressRecipe.CODEC, MetalPressRecipe.STREAM_CODEC));
    public static final DeferredHolder<RecipeType<?>, RecipeType<BlastFurnaceRecipe>> BLAST_FURNACE_RECIPE = RECIPE_TYPES.register("blast_furnace", () -> new RecipeType<>() {
        @Override public String toString() { return "techguns:blast_furnace"; }
    });
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BlastFurnaceRecipe>> BLAST_FURNACE_SERIALIZER = SERIALIZERS.register("blast_furnace",
            () -> new RecipeSerializer<>(BlastFurnaceRecipe.CODEC, BlastFurnaceRecipe.STREAM_CODEC));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ChemLabBlockEntity>> CHEM_LAB_ENTITY=BLOCK_ENTITIES.register("chem_lab",
            () -> new BlockEntityType<>(ChemLabBlockEntity::new,CHEM_LAB.get()));
    public static final DeferredHolder<MenuType<?>,MenuType<ChemLabMenu>> CHEM_LAB_MENU=MENUS.register("chem_lab", () -> new MenuType<>(ChemLabMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<RecipeType<?>,RecipeType<ChemLabRecipe>> CHEM_LAB_RECIPE=RECIPE_TYPES.register("chem_lab", () -> new RecipeType<>() {
        @Override public String toString() { return "techguns:chem_lab"; }
    });
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<ChemLabRecipe>> CHEM_LAB_SERIALIZER=SERIALIZERS.register("chem_lab",
            () -> new RecipeSerializer<>(ChemLabRecipe.CODEC,ChemLabRecipe.STREAM_CODEC));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus); BLOCK_ENTITIES.register(bus); MENUS.register(bus); RECIPE_TYPES.register(bus); SERIALIZERS.register(bus);
        bus.addListener(TGMachineContent::capabilities);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, AMMO_PRESS_ENTITY.get(), (machine, side) -> machine.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, AMMO_PRESS_ENTITY.get(), (machine, side) -> machine.automation());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, METAL_PRESS_ENTITY.get(), (machine, side) -> machine.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, METAL_PRESS_ENTITY.get(), (machine, side) -> machine.automation());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BLAST_FURNACE_ENTITY.get(), (machine, side) -> machine.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, BLAST_FURNACE_ENTITY.get(), (machine, side) -> machine.automation());
        event.registerBlockEntity(Capabilities.Energy.BLOCK,CHEM_LAB_ENTITY.get(),(machine,side) -> machine.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK,CHEM_LAB_ENTITY.get(),(machine,side) -> machine.automation());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK,CHEM_LAB_ENTITY.get(),(machine,side) -> machine.fluids());
    }
    private TGMachineContent() {}
}
