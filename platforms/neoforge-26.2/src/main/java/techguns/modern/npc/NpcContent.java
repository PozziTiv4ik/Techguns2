package techguns.modern.npc;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import techguns.modern.TGContent;

public final class NpcContent {
    public static final DeferredHolder<EntityType<?>, EntityType<SuperMutant>> SUPER_MUTANT = TGContent.ENTITIES.register("supermutantbasic", () ->
            EntityType.Builder.<SuperMutant>of(SuperMutant::new, MobCategory.MONSTER).sized(1, 2.7f).eyeHeight(2.295f).clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, TGContent.id("supermutantbasic"))));
    public static final DeferredItem<SpawnEggItem> EGG = TGContent.ITEMS.registerItem("supermutantbasic_spawn_egg", SpawnEggItem::new,
            props -> props.spawnEgg(SUPER_MUTANT.get()));
    public static final DeferredHolder<SoundEvent, SoundEvent> IDLE = sound("npcs.cyberdemonidle"), HURT = sound("npcs.cyberdemonhurt"),
            DEATH = sound("npcs.cyberdemondeath"), STEP = sound("npcs.cyberdemonstep");
    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) { return TGContent.SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(TGContent.id(id))); }
    public static void register(IEventBus bus, ModContainer container) {
        NpcConfig.register(container);
        bus.addListener(NpcContent::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event) { event.put(SUPER_MUTANT.get(), SuperMutant.attributes().build()); }
    private NpcContent() {}
}
