package techguns.modern;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TGContent {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Techguns.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ROUNDS =
            COMPONENTS.registerComponentType("rounds", builder -> builder
                    .persistent(Codec.intRange(0, 6)).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Techguns.MOD_ID);
    public static final DeferredItem<Item> PISTOL_ROUNDS = ITEMS.registerSimpleItem("pistolrounds");
    public static final DeferredItem<RevolverItem> REVOLVER = ITEMS.registerItem("revolver", RevolverItem::new,
            props -> props.stacksTo(1).component(ROUNDS.get(), 0));
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Techguns.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> REVOLVER_FIRE = SOUNDS.register(
            "guns.revolverfire", () -> SoundEvent.createVariableRangeEvent(id("guns.revolverfire")));
    public static final DeferredHolder<SoundEvent, SoundEvent> REVOLVER_RELOAD = SOUNDS.register(
            "guns.revolverreload", () -> SoundEvent.createVariableRangeEvent(id("guns.revolverreload")));
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Techguns.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<Bullet>> BULLET = ENTITIES.register("bullet", () ->
            EntityType.Builder.<Bullet>of(Bullet::new, MobCategory.MISC).sized(0.1f, 0.1f)
                    .clientTrackingRange(8).updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("bullet"))));
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Techguns.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("techguns", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.techguns"))
                    .icon(() -> REVOLVER.toStack())
                    .displayItems((parameters, output) -> {
                        output.accept(REVOLVER.get());
                        output.accept(PISTOL_ROUNDS.get());
                    }).build());

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Techguns.MOD_ID, path); }
    private TGContent() {}
}
