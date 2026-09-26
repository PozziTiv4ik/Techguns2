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
    public static final DeferredHolder<EntityType<?>, EntityType<CyberDemon>> CYBER_DEMON = TGContent.ENTITIES.register("cyberdemon", () ->
            EntityType.Builder.<CyberDemon>of(CyberDemon::new, MobCategory.MONSTER).sized(.6f, 1.8f).eyeHeight(1.53f).fireImmune().clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, TGContent.id("cyberdemon"))));
    public static final DeferredItem<SpawnEggItem> CYBER_EGG = TGContent.ITEMS.registerItem("cyberdemon_spawn_egg", SpawnEggItem::new,
            props -> props.spawnEgg(CYBER_DEMON.get()));
    public static final DeferredHolder<EntityType<?>,EntityType<ZombiePigmanSoldier>> PIGMAN = TGContent.ENTITIES.register("zombiepigmansoldier", () ->
            EntityType.Builder.<ZombiePigmanSoldier>of(ZombiePigmanSoldier::new,MobCategory.MONSTER).sized(.6f,1.8f).eyeHeight(1.53f).fireImmune().clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("zombiepigmansoldier"))));
    public static final DeferredItem<SpawnEggItem> PIGMAN_EGG = TGContent.ITEMS.registerItem("zombiepigmansoldier_spawn_egg",SpawnEggItem::new,props -> props.spawnEgg(PIGMAN.get()));
    public static final DeferredHolder<EntityType<?>,EntityType<ZombieSoldier>> ZOMBIE_SOLDIER = TGContent.ENTITIES.register("zombiesoldier", () ->
            EntityType.Builder.<ZombieSoldier>of(ZombieSoldier::new,MobCategory.MONSTER).sized(.6f,1.8f).eyeHeight(1.53f).clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("zombiesoldier"))));
    public static final DeferredItem<SpawnEggItem> SOLDIER_EGG = TGContent.ITEMS.registerItem("zombiesoldier_spawn_egg",SpawnEggItem::new,props -> props.spawnEgg(ZOMBIE_SOLDIER.get()));
    public static final DeferredHolder<EntityType<?>,EntityType<ZombieFarmer>> FARMER = TGContent.ENTITIES.register("zombiefarmer", () ->
            EntityType.Builder.<ZombieFarmer>of(ZombieFarmer::new,MobCategory.MONSTER).sized(.6f,1.8f).eyeHeight(1.53f).clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("zombiefarmer"))));
    public static final DeferredHolder<EntityType<?>,EntityType<ZombieMiner>> MINER = TGContent.ENTITIES.register("zombieminer", () ->
            EntityType.Builder.<ZombieMiner>of(ZombieMiner::new,MobCategory.MONSTER).sized(.6f,1.8f).eyeHeight(1.53f).clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("zombieminer"))));
    public static final DeferredItem<SpawnEggItem> FARMER_EGG = TGContent.ITEMS.registerItem("zombiefarmer_spawn_egg",SpawnEggItem::new,props -> props.spawnEgg(FARMER.get()));
    public static final DeferredItem<SpawnEggItem> MINER_EGG = TGContent.ITEMS.registerItem("zombieminer_spawn_egg",SpawnEggItem::new,props -> props.spawnEgg(MINER.get()));
    public static final DeferredHolder<EntityType<?>,EntityType<SkeletonSoldier>> SKELETON = TGContent.ENTITIES.register("skeletonsoldier", () ->
            EntityType.Builder.<SkeletonSoldier>of(SkeletonSoldier::new,MobCategory.MONSTER).sized(.6f,1.95f).eyeHeight(1.6575f).clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("skeletonsoldier"))));
    public static final DeferredItem<SpawnEggItem> SKELETON_EGG = TGContent.ITEMS.registerItem("skeletonsoldier_spawn_egg",SpawnEggItem::new,props -> props.spawnEgg(SKELETON.get()));
    public static final DeferredHolder<EntityType<?>,EntityType<Bandit>> BANDIT = TGContent.ENTITIES.register("bandit", () ->
            EntityType.Builder.<Bandit>of(Bandit::new,MobCategory.MONSTER).sized(.6f,1.8f).eyeHeight(1.53f).clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("bandit"))));
    public static final DeferredItem<SpawnEggItem> BANDIT_EGG = TGContent.ITEMS.registerItem("bandit_spawn_egg",SpawnEggItem::new,props -> props.spawnEgg(BANDIT.get()));
    public static final DeferredHolder<EntityType<?>,EntityType<PsychoSteve>> PSYCHO = TGContent.ENTITIES.register("psychosteve", () ->
            EntityType.Builder.<PsychoSteve>of(PsychoSteve::new,MobCategory.MONSTER).sized(.6f,1.8f).eyeHeight(1.53f).clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("psychosteve"))));
    public static final DeferredItem<SpawnEggItem> PSYCHO_EGG = TGContent.ITEMS.registerItem("psychosteve_spawn_egg",SpawnEggItem::new,props -> props.spawnEgg(PSYCHO.get()));
    public static final DeferredHolder<EntityType<?>,EntityType<ArmySoldier>> ARMY = TGContent.ENTITIES.register("armysoldier", () ->
            EntityType.Builder.<ArmySoldier>of(ArmySoldier::new,MobCategory.MONSTER).sized(.6f,1.8f).eyeHeight(1.53f).clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("armysoldier"))));
    public static final DeferredItem<SpawnEggItem> ARMY_EGG = TGContent.ITEMS.registerItem("armysoldier_spawn_egg",SpawnEggItem::new,props -> props.spawnEgg(ARMY.get()));
    public static final DeferredHolder<EntityType<?>,EntityType<Ghastling>> GHASTLING=TGContent.ENTITIES.register("ghastling",()->
            EntityType.Builder.<Ghastling>of(Ghastling::new,MobCategory.MONSTER).sized(1,2.1f).eyeHeight(1.5f).fireImmune().clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("ghastling"))));
    public static final DeferredItem<SpawnEggItem> GHASTLING_EGG=TGContent.ITEMS.registerItem("ghastling_spawn_egg",SpawnEggItem::new,props->props.spawnEgg(GHASTLING.get()));
    public static final DeferredHolder<SoundEvent, SoundEvent> IDLE = sound("npcs.cyberdemonidle"), HURT = sound("npcs.cyberdemonhurt"),
            DEATH = sound("npcs.cyberdemondeath"), STEP = sound("npcs.cyberdemonstep");
    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) { return TGContent.SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(TGContent.id(id))); }
    public static void register(IEventBus bus, ModContainer container) {
        NpcConfig.register(container);
        NpcSpawnConfig.register(container);
        NetherSpawns.register(bus);
        OverworldSpawns.register(bus);
        bus.addListener(NpcContent::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(SUPER_MUTANT.get(), SuperMutant.attributes().build());
        event.put(CYBER_DEMON.get(), CyberDemon.attributes().build());
        event.put(PIGMAN.get(),ZombiePigmanSoldier.attributes().build());
        event.put(ZOMBIE_SOLDIER.get(),ZombieSoldier.attributes().build());
        event.put(FARMER.get(),RuralZombie.attributes(techguns.core.RuralZombieRules.Kind.FARMER).build());
        event.put(MINER.get(),RuralZombie.attributes(techguns.core.RuralZombieRules.Kind.MINER).build());
        event.put(SKELETON.get(),SkeletonSoldier.attributes().build());
        event.put(BANDIT.get(),Bandit.attributes().build());
        event.put(PSYCHO.get(),PsychoSteve.attributes().build());
        event.put(ARMY.get(),ArmySoldier.attributes().build());
        event.put(GHASTLING.get(),Ghastling.attributes().build());
        event.put(ALIEN_BUG.get(),AlienBug.attributes().build());
        event.put(COMMANDO.get(),Commando.attributes().build());
        event.put(POLICEMAN.get(),ZombiePoliceman.attributes().build());
    }
    public static final DeferredHolder<EntityType<?>,EntityType<AlienBug>> ALIEN_BUG=TGContent.ENTITIES.register("alienbug",()->
            EntityType.Builder.<AlienBug>of(AlienBug::new,MobCategory.MONSTER).sized(1.1f,1.2f).eyeHeight(.65f).clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("alienbug"))));
    public static final DeferredItem<SpawnEggItem> BUG_EGG=TGContent.ITEMS.registerItem("alienbug_spawn_egg",SpawnEggItem::new,props->props.spawnEgg(ALIEN_BUG.get()));
    public static final DeferredHolder<SoundEvent,SoundEvent> BUG_IDLE=sound("npcs.alienbugidle"), BUG_HURT=sound("npcs.alienbughurt"),
            BUG_DEATH=sound("npcs.alienbugdeath"), BUG_STEP=sound("npcs.alienbugstep"), BUG_AGGRO=bugRangeSound("npcs.alienbugaggro"), BUG_BITE=bugRangeSound("npcs.alienbugbite");
    private static DeferredHolder<SoundEvent,SoundEvent> bugRangeSound(String id) { return TGContent.SOUNDS.register(id,()->SoundEvent.createFixedRangeEvent(TGContent.id(id),24)); }
    public static final DeferredHolder<EntityType<?>,EntityType<Commando>> COMMANDO=TGContent.ENTITIES.register("commando",()->
            EntityType.Builder.<Commando>of(Commando::new,MobCategory.MONSTER).sized(.6f,1.8f).eyeHeight(1.53f).clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("commando"))));
    public static final DeferredItem<SpawnEggItem> COMMANDO_EGG=TGContent.ITEMS.registerItem("commando_spawn_egg",SpawnEggItem::new,props->props.spawnEgg(COMMANDO.get()));
    public static final DeferredHolder<EntityType<?>,EntityType<ZombiePoliceman>> POLICEMAN=TGContent.ENTITIES.register("zombiepoliceman",()->
            EntityType.Builder.<ZombiePoliceman>of(ZombiePoliceman::new,MobCategory.MONSTER).sized(.6f,1.8f).eyeHeight(1.53f).clientTrackingRange(5).updateInterval(3)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("zombiepoliceman"))));
    public static final DeferredItem<SpawnEggItem> POLICEMAN_EGG=TGContent.ITEMS.registerItem("zombiepoliceman_spawn_egg",SpawnEggItem::new,props->props.spawnEgg(POLICEMAN.get()));
    private NpcContent() {}
}
