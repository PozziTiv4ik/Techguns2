package techguns.modern;

import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.UseCooldown;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.WeaponDefinition;
import techguns.core.Weapons;
import techguns.core.CraftingContent;

public final class TGContent {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Techguns.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ROUNDS = COMPONENTS.registerComponentType(
            "rounds", builder -> builder.persistent(Codec.intRange(0, 10000)).networkSynchronized(ByteBufCodecs.VAR_INT).ignoreSwapAnimation());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> AIMING = COMPONENTS.registerComponentType(
            "aiming", builder -> builder.networkSynchronized(ByteBufCodecs.BOOL).ignoreSwapAnimation());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RELOAD_TICKS = COMPONENTS.registerComponentType(
            "reload_ticks", builder -> builder.networkSynchronized(ByteBufCodecs.VAR_INT).ignoreSwapAnimation());
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Techguns.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Techguns.MOD_ID);
    public static final Map<String, DeferredItem<Item>> AMMO = registerAmmo();
    public static final Map<String, DeferredItem<Item>> MATERIALS = registerMaterials();
    public static final Map<String, DeferredItem<GunItem>> GUNS = registerGuns();
    public static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> SOUND_EVENTS = registerSounds();
    public static final DeferredItem<Item> PISTOL_ROUNDS = AMMO.get("pistolrounds");
    public static final DeferredItem<GunItem> REVOLVER = GUNS.get("revolver");
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Techguns.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<Bullet>> BULLET = ENTITIES.register("bullet", () ->
            EntityType.Builder.<Bullet>of(Bullet::new, MobCategory.MISC).sized(0.1f, 0.1f).clientTrackingRange(8).updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("bullet"))));
    public static final DeferredHolder<EntityType<?>, EntityType<LaserBeam>> LASER_BEAM = ENTITIES.register("laser_beam", () ->
            EntityType.Builder.<LaserBeam>of(LaserBeam::new, MobCategory.MISC).sized(0.1f, 0.1f).clientTrackingRange(12).updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("laser_beam"))));
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Techguns.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("techguns", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.techguns")).icon(REVOLVER::toStack)
                    .displayItems((parameters, output) -> {
                        GUNS.values().forEach(item -> output.accept(item.get()));
                        AMMO.values().forEach(item -> output.accept(item.get()));
                        MATERIALS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> output.accept(entry.getValue().get()));
                        output.accept(techguns.modern.machine.TGMachineContent.AMMO_PRESS_ITEM.get());
                        output.accept(techguns.modern.machine.TGMachineContent.METAL_PRESS_ITEM.get());
                        output.accept(techguns.modern.machine.TGMachineContent.BLAST_FURNACE_ITEM.get());
                        output.accept(techguns.modern.machine.TGMachineContent.CHEM_LAB_ITEM.get());
                        output.accept(techguns.modern.machine.reaction.ReactionContent.HOUSING_ITEM.get());
                        output.accept(techguns.modern.machine.reaction.ReactionContent.GLASS_ITEM.get());
                        output.accept(techguns.modern.machine.reaction.ReactionContent.CONTROLLER_ITEM.get());
                        output.accept(techguns.modern.machine.fabricator.FabricatorContent.HOUSING_ITEM.get());
                        output.accept(techguns.modern.machine.fabricator.FabricatorContent.GLASS_ITEM.get());
                        output.accept(techguns.modern.machine.fabricator.FabricatorContent.CONTROLLER_ITEM.get());
                        techguns.core.Ores.ALL.stream().sorted(java.util.Comparator.comparingInt(techguns.core.OreDefinition::legacyMetadata))
                                .forEach(ore -> output.accept(techguns.modern.world.TGOreContent.ORES.get(ore.id()).get()));
                        techguns.modern.fluid.TGFluids.ALL.forEach(fluid -> output.accept(fluid.bucket.get()));
                    }).build());

    private static Map<String, DeferredItem<GunItem>> registerGuns() {
        Map<String, DeferredItem<GunItem>> items = new LinkedHashMap<>();
        for (WeaponDefinition gun : Weapons.ALL) items.put(gun.id(), ITEMS.registerItem(gun.id(), props -> new GunItem(props, gun),
                props -> props.stacksTo(1).component(ROUNDS.get(), 0).component(DataComponents.USE_COOLDOWN,
                        new UseCooldown(gun.stats().fireDelay() / 20f, Optional.of(id("firearms"))))));
        return Collections.unmodifiableMap(items);
    }
    private static Map<String, DeferredItem<Item>> registerAmmo() {
        TreeSet<String> ids = new TreeSet<>();
        ids.addAll(CraftingContent.EXTRA_AMMO);
        for (WeaponDefinition gun : Weapons.ALL) {
            ids.add(gun.ammo().item());
            if (!gun.ammo().emptyItem().isEmpty()) ids.add(gun.ammo().emptyItem());
            if (!gun.ammo().looseItem().isEmpty()) ids.add(gun.ammo().looseItem());
        }
        Map<String, DeferredItem<Item>> items = new LinkedHashMap<>();
        ids.forEach(id -> items.put(id, ITEMS.registerSimpleItem(id)));
        return Collections.unmodifiableMap(items);
    }
    private static Map<String, DeferredItem<Item>> registerMaterials() {
        Map<String, DeferredItem<Item>> items = new LinkedHashMap<>();
        CraftingContent.MATERIALS.forEach(id -> items.put(id, ITEMS.registerItem(id, props -> id.equals("radpills") || id.equals("radaway")
                ? new techguns.modern.radiation.RadiationMedicine(props,id.equals("radpills")) : new Item(props),
                props -> props.stacksTo(id.equals("machinestackupgrade") ? 7 : id.equals("rcheatray") || id.equals("rcuvemitter") ? 1 : 64))));
        return Collections.unmodifiableMap(items);
    }
    private static Map<String, DeferredHolder<SoundEvent, SoundEvent>> registerSounds() {
        Map<String, DeferredHolder<SoundEvent, SoundEvent>> sounds = new LinkedHashMap<>();
        for (WeaponDefinition gun : Weapons.ALL) for (String name : new String[]{gun.fireSound(), gun.reloadSound()})
            sounds.computeIfAbsent(name, key -> SOUNDS.register(key, () -> SoundEvent.createVariableRangeEvent(id(key))));
        return Collections.unmodifiableMap(sounds);
    }
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Techguns.MOD_ID, path); }
    private TGContent() {}
}
