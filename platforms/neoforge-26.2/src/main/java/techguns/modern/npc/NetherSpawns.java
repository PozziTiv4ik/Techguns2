package techguns.modern.npc;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.*;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.registries.*;
import techguns.core.NetherSpawnRules;
import techguns.modern.TGContent;

public final class NetherSpawns {
    public static final DeferredHolder<EntityType<?>, EntityType<NetherSpawnSelector>> SELECTOR = TGContent.ENTITIES.register("nether_spawn_selector", () ->
            EntityType.Builder.<NetherSpawnSelector>of(NetherSpawnSelector::new, MobCategory.MONSTER).sized(.6f, 1.8f).noSave().clientTrackingRange(0)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, TGContent.id("nether_spawn_selector"))));
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> MODIFIERS = DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "techguns");
    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<Modifier>> MODIFIER = MODIFIERS.register("nether_npcs", () -> MapCodec.unit(Modifier::new));
    public static void register(IEventBus bus) {
        MODIFIERS.register(bus);
        bus.addListener((EntityAttributeCreationEvent event) -> event.put(SELECTOR.get(), Monster.createMonsterAttributes().build()));
        bus.addListener(NetherSpawns::placements);
        NeoForge.EVENT_BUS.addListener(NetherSpawns::replaceSelector);
    }
    public record Modifier() implements BiomeModifier {
        @Override public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if (phase == Phase.ADD && eligibleBiome(biome) && NpcSpawnConfig.NETHER_WEIGHT.get() > 0)
                builder.getMobSpawnSettings().addSpawn(MobCategory.MONSTER, NpcSpawnConfig.NETHER_WEIGHT.get(), new MobSpawnSettings.SpawnerData(SELECTOR.get(), 1, 3));
        }
        @Override public MapCodec<? extends BiomeModifier> codec() { return MODIFIER.get(); }
    }
    public static boolean eligibleBiome(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_NETHER) && biome.unwrapKey().map(key -> !NpcSpawnConfig.BIOME_BLACKLIST.get().contains(key.identifier().toString())).orElse(false);
    }
    private static void placements(RegisterSpawnPlacementsEvent event) {
        event.register(SELECTOR.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                NetherSpawns::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
    public static boolean checkSpawnRules(EntityType<NetherSpawnSelector> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        if (level.getLevel().getDifficulty() == Difficulty.PEACEFUL || !level.getLevel().dimension().equals(Level.NETHER)
                || !eligibleBiome(level.getBiome(pos)) || NpcSpawnConfig.NETHER_WEIGHT.get() <= 0) return false;
        // Original EntityMob light test, including its random 0..7 threshold (modern Nether rules differ).
        return level.getBrightness(LightLayer.SKY, pos) <= random.nextInt(32)
                && (level.getLevel().isThundering() ? level.getMaxLocalRawBrightness(pos, 10) : level.getMaxLocalRawBrightness(pos)) <= random.nextInt(8)
                && Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }
    private static void replaceSelector(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof NetherSpawnSelector selector) || !(event.getLevel() instanceof ServerLevel level)) return;
        event.setCanceled(true); selector.discard();
        if (!level.dimension().equals(Level.NETHER) || level.getDifficulty() == Difficulty.PEACEFUL || NpcSpawnConfig.NETHER_WEIGHT.get() <= 0
                || !eligibleBiome(level.getBiome(selector.blockPosition()))) return;
        int pigman = NpcSpawnConfig.PIGMAN_WEIGHT.get(), cyber = NpcSpawnConfig.CYBER_WEIGHT.get();
        if (pigman + cyber == 0) return;
        ArmedNpc npc;
        if (NetherSpawnRules.choose(pigman,cyber,level.getRandom().nextInt(pigman+cyber)) == NetherSpawnRules.Choice.CYBER_DEMON) {
            var demon=new CyberDemon(NpcContent.CYBER_DEMON.get(),level); demon.equipBlaster(); npc=demon;
        } else {
            var soldier=new ZombiePigmanSoldier(NpcContent.PIGMAN.get(),level); soldier.equipForSpawn(); npc=soldier;
        }
        npc.snapTo(selector.getX(), selector.getY(), selector.getZ(), selector.getYRot(), 0);
        npc.setYHeadRot(selector.getYRot());
        // onSpawnByManager equips directly; it does not reroll vanilla spawn modifiers or handedness.
        level.addFreshEntity(npc);
    }
    private NetherSpawns() {}
}
