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
import net.neoforged.neoforge.common.*;
import net.neoforged.neoforge.common.world.*;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.registries.*;
import techguns.core.OverworldSpawnRules;
import techguns.modern.TGContent;

public final class OverworldSpawns {
    public static final DeferredHolder<EntityType<?>,EntityType<OverworldSpawnSelector>> SELECTOR = TGContent.ENTITIES.register("overworld_spawn_selector", () ->
            EntityType.Builder.<OverworldSpawnSelector>of(OverworldSpawnSelector::new,MobCategory.MONSTER).sized(.6f,1.8f).noSave().clientTrackingRange(0)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,TGContent.id("overworld_spawn_selector"))));
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> MODIFIERS = DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS,"techguns");
    private static final DeferredHolder<MapCodec<? extends BiomeModifier>,MapCodec<Modifier>> MODIFIER = MODIFIERS.register("overworld_npcs",() -> MapCodec.unit(Modifier::new));
    public static void register(IEventBus bus) {
        MODIFIERS.register(bus);
        bus.addListener((EntityAttributeCreationEvent event) -> event.put(SELECTOR.get(),Monster.createMonsterAttributes().build()));
        bus.addListener(OverworldSpawns::placements);
        NeoForge.EVENT_BUS.addListener(OverworldSpawns::replaceSelector);
    }
    public record Modifier() implements BiomeModifier {
        @Override public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if (phase == Phase.ADD && eligibleBiome(biome) && NpcSpawnConfig.OVERWORLD_WEIGHT.get() > 0)
                builder.getMobSpawnSettings().addSpawn(MobCategory.MONSTER,NpcSpawnConfig.OVERWORLD_WEIGHT.get(),new MobSpawnSettings.SpawnerData(SELECTOR.get(),1,3));
        }
        @Override public MapCodec<? extends BiomeModifier> codec() { return MODIFIER.get(); }
    }
    public static boolean eligibleBiome(Holder<Biome> biome) {
        return !biome.is(BiomeTags.IS_NETHER) && !biome.is(BiomeTags.IS_END)
                && biome.unwrapKey().map(key -> !NpcSpawnConfig.BIOME_BLACKLIST.get().contains(key.identifier().toString())).orElse(false);
    }
    /** BiomeDictionary categories mapped to NeoForge's common biome tags, including modern biomes. */
    public static int biomeDanger(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_NETHER) || biome.is(BiomeTags.IS_END)) return 0;
        if (biome.is(Tags.Biomes.IS_WASTELAND) || biome.is(Tags.Biomes.IS_SANDY) || biome.is(Tags.Biomes.IS_SNOWY)
                || biome.is(Tags.Biomes.IS_SWAMP) || biome.is(Tags.Biomes.IS_SPOOKY) || biome.is(Tags.Biomes.IS_DEAD)) return 2;
        if (biome.is(Tags.Biomes.IS_COLD) || biome.is(Tags.Biomes.IS_SPARSE_VEGETATION) || biome.is(Tags.Biomes.IS_SAVANNA) || biome.is(Tags.Biomes.IS_DRY)) return 1;
        return 0;
    }
    public static int danger(ServerLevel level, double x, double z) {
        var spawn = level.getRespawnData().pos();
        // Legacy samples the surface biome at rounded X/Z and Y=64, even for an underground spawn.
        var biome = level.getBiome(new BlockPos((int)Math.round(x),64,(int)Math.round(z)));
        return OverworldSpawnRules.distanceDanger(x,z,spawn.getX(),spawn.getZ(),NpcSpawnConfig.DISTANCE_0.get(),NpcSpawnConfig.DISTANCE_1.get(),NpcSpawnConfig.DISTANCE_2.get()) + biomeDanger(biome);
    }
    private static void placements(RegisterSpawnPlacementsEvent event) {
        event.register(SELECTOR.get(),SpawnPlacementTypes.ON_GROUND,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                OverworldSpawns::checkSpawnRules,RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
    public static boolean checkSpawnRules(EntityType<OverworldSpawnSelector> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        // This first Overworld slice is deliberately limited to the vanilla dimension.
        if (!level.getLevel().dimension().equals(Level.OVERWORLD) || level.getLevel().getDifficulty() == Difficulty.PEACEFUL
                || NpcSpawnConfig.OVERWORLD_WEIGHT.get() <= 0 || !eligibleBiome(level.getBiome(pos))) return false;
        return level.getBrightness(LightLayer.SKY,pos) <= random.nextInt(32)
                && (level.getLevel().isThundering() ? level.getMaxLocalRawBrightness(pos,10) : level.getMaxLocalRawBrightness(pos)) <= random.nextInt(8)
                && Mob.checkMobSpawnRules(type,level,reason,pos,random);
    }
    private static void replaceSelector(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof OverworldSpawnSelector selector) || !(event.getLevel() instanceof ServerLevel level)) return;
        event.setCanceled(true); selector.discard();
        if (!level.dimension().equals(Level.OVERWORLD) || level.getDifficulty() == Difficulty.PEACEFUL || NpcSpawnConfig.OVERWORLD_WEIGHT.get() <= 0
                || !eligibleBiome(level.getBiome(selector.blockPosition()))) return;
        int danger = danger(level,selector.getX(),selector.getZ());
        var weights = NpcSpawnConfig.overworldWeights();
        int total = weights.total(danger);
        if (total == 0) return;
        ArmedNpc npc=switch(weights.choose(danger,level.getRandom().nextInt(total))) {
            case ZOMBIE_FARMER -> new ZombieFarmer(NpcContent.FARMER.get(),level);
            case ZOMBIE_MINER -> new ZombieMiner(NpcContent.MINER.get(),level);
            case ZOMBIE_SOLDIER -> new ZombieSoldier(NpcContent.ZOMBIE_SOLDIER.get(),level);
            case SKELETON_SOLDIER -> new SkeletonSoldier(NpcContent.SKELETON.get(),level);
            default -> null;
        };
        // PsychoSteve and Bandit remain empty; their tickets are not redistributed.
        if(npc==null) return;
        if(npc instanceof RuralZombie rural) rural.equipForSpawn();
        else if(npc instanceof SkeletonSoldier skeleton) skeleton.equipForSpawn();
        else ((ZombieSoldier)npc).equipForSpawn();
        npc.snapTo(selector.getX(),selector.getY(),selector.getZ(),selector.getYRot(),0);
        npc.setYHeadRot(selector.getYRot()); level.addFreshEntity(npc);
    }
    private OverworldSpawns() {}
}
