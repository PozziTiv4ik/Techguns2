package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.*;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.modern.*;
import techguns.modern.npc.*;

final class OverworldSpawnGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("zombie_overworld_spawn_biome_tables",() -> OverworldSpawnGameTests::tables);
        r.register("zombie_overworld_selector_reserved_and_disabled",() -> OverworldSpawnGameTests::replacement);
        r.register("zombie_overworld_spawn_darkness_and_ground",() -> OverworldSpawnGameTests::light);
        r.register("zombie_overworld_natural_spawn_and_death",() -> OverworldSpawnGameTests::natural);
        r.register("rural_natural_danger_zero",() -> h -> natural(h,true));
    }
    private static void tables(GameTestHelper h) {
        var registry=h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        for(var biome:registry.listElements().toList()) {
            var entries=biome.value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap();
            var selectors=entries.stream().filter(e -> e.value().type()==OverworldSpawns.SELECTOR.get()).toList();
            if(!biome.is(BiomeTags.IS_NETHER) && !biome.is(BiomeTags.IS_END)) {
                h.assertValueEqual(selectors.size(),1,"One shared Overworld selector per eligible biome");
                h.assertValueEqual(selectors.getFirst().weight(),600,"Original pool weight");
                h.assertValueEqual(selectors.getFirst().value().minCount(),1,"Original min group");
                h.assertValueEqual(selectors.getFirst().value().maxCount(),3,"Original max group");
            } else h.assertTrue(selectors.isEmpty(),"Nether and End excluded");
            h.assertTrue(entries.stream().noneMatch(e -> e.value().type()==NpcContent.ZOMBIE_SOLDIER.get() || e.value().type()==NpcContent.FARMER.get() || e.value().type()==NpcContent.MINER.get()),"No direct NPC entry bypasses danger or duplicates weight");
        }
        h.assertValueEqual(OverworldSpawns.biomeDanger(registry.getOrThrow(Biomes.PLAINS)),0,"Plains danger");
        h.assertValueEqual(OverworldSpawns.biomeDanger(registry.getOrThrow(Biomes.TAIGA)),1,"Cold danger");
        h.assertValueEqual(OverworldSpawns.biomeDanger(registry.getOrThrow(Biomes.SAVANNA)),1,"Savanna danger");
        for(var key:List.of(Biomes.DESERT,Biomes.SWAMP,Biomes.SNOWY_PLAINS,Biomes.DARK_FOREST))
            h.assertValueEqual(OverworldSpawns.biomeDanger(registry.getOrThrow(key)),2,"Source dangerous biome family "+key);
        h.succeed();
    }
    private static List<ModConfigSpec.IntValue> settings() {
        return List.of(NpcSpawnConfig.FARMER_WEIGHT,NpcSpawnConfig.MINER_WEIGHT,NpcSpawnConfig.SOLDIER_WEIGHT,NpcSpawnConfig.SKELETON_WEIGHT,
                NpcSpawnConfig.PSYCHO_WEIGHT,NpcSpawnConfig.BANDIT_WEIGHT,NpcSpawnConfig.DISTANCE_0,NpcSpawnConfig.DISTANCE_1,NpcSpawnConfig.DISTANCE_2,NpcSpawnConfig.OVERWORLD_WEIGHT);
    }
    private static OverworldSpawnSelector selector(ServerLevel level,BlockPos pos) {
        var selector=new OverworldSpawnSelector(OverworldSpawns.SELECTOR.get(),level); selector.snapTo(pos,37,0); level.addFreshEntity(selector); return selector;
    }
    private static void replacement(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(4,2,4)); var values=settings(); var saved=values.stream().map(ModConfigSpec.IntValue::get).toList();
        var blacklist=NpcSpawnConfig.BIOME_BLACKLIST.get(); var area=new AABB(pos).inflate(4); var spawned=new ArrayList<ArmedNpc>();
        Consumer<EntityJoinLevelEvent> record=e -> { if(e.getLevel()==level && e.getEntity() instanceof ArmedNpc npc && area.contains(npc.position())) spawned.add(npc); };
        NeoForge.EVENT_BUS.addListener(record);
        try {
            for(int i=0;i<9;i++) values.get(i).set(0);
            NpcSpawnConfig.SOLDIER_WEIGHT.set(100);
            var dummy=selector(level,pos); h.assertTrue(dummy.isRemoved(),"Selector removed before persistence");
            h.assertValueEqual(spawned.size(),1,"Only implemented entry produces a soldier");
            h.assertTrue(!spawned.getFirst().getMainHandItem().isEmpty(),"Manager equipped soldier");
            ArmorGameTests.near(h,spawned.getFirst().getYRot(),37,"Spawn heading retained");
            NpcSpawnConfig.DISTANCE_0.set(Integer.MAX_VALUE);
            h.assertValueEqual(OverworldSpawns.danger(level,pos.getX(),pos.getZ()),0,"Fixture inside safe plains radius");
            selector(level,pos); h.assertValueEqual(spawned.size(),1,"Danger zero excludes soldier");
            NpcSpawnConfig.DISTANCE_0.set(0); NpcSpawnConfig.SOLDIER_WEIGHT.set(0); NpcSpawnConfig.FARMER_WEIGHT.set(200);
            selector(level,pos); h.assertValueEqual(spawned.size(),2,"Farmer ticket now creates one farmer"); h.assertTrue(spawned.getLast() instanceof ZombieFarmer,"Correct replacement class");
            NpcSpawnConfig.FARMER_WEIGHT.set(0); NpcSpawnConfig.MINER_WEIGHT.set(200); selector(level,pos);
            h.assertValueEqual(spawned.size(),3,"Miner ticket creates one miner"); h.assertTrue(spawned.getLast() instanceof ZombieMiner,"Correct miner replacement");
            NpcSpawnConfig.MINER_WEIGHT.set(0); NpcSpawnConfig.SKELETON_WEIGHT.set(100); selector(level,pos);
            h.assertValueEqual(spawned.size(),3,"Unported skeleton ticket stays empty");
            NpcSpawnConfig.SKELETON_WEIGHT.set(0); selector(level,pos); h.assertValueEqual(spawned.size(),3,"Empty table creates nothing");
            NpcSpawnConfig.SOLDIER_WEIGHT.set(100); NpcSpawnConfig.OVERWORLD_WEIGHT.set(0); selector(level,pos);
            h.assertValueEqual(spawned.size(),3,"Global zero disables replacement"); NpcSpawnConfig.OVERWORLD_WEIGHT.set(600);
            NpcSpawnConfig.BIOME_BLACKLIST.set(List.of(level.getBiome(pos).unwrapKey().orElseThrow().identifier().toString())); selector(level,pos);
            h.assertValueEqual(spawned.size(),3,"Blacklisted biome creates nothing");
            NpcSpawnConfig.BIOME_BLACKLIST.set(blacklist);
            var nether=level.getServer().getLevel(Level.NETHER); nether.getChunk(pos);
            h.assertTrue(selector(nether,pos).isRemoved(),"Overworld selector rejected outside its supported dimension");
        } finally {
            for(int i=0;i<values.size();i++) values.get(i).set(saved.get(i)); NpcSpawnConfig.BIOME_BLACKLIST.set(blacklist);
            NeoForge.EVENT_BUS.unregister(record); spawned.forEach(Entity::discard);
        }
        h.succeed();
    }
    private static LegacyRandomSource lightRoll() { return new LegacyRandomSource(1) { @Override public int nextInt(int bound) { return bound-1; } }; }
    private static void light(GameTestHelper h) {
        for(int x=1;x<11;x++) for(int z=1;z<11;z++) for(int y=0;y<=5;y++)
            h.setBlock(new BlockPos(x,y,z),x==1 || x==10 || z==1 || z==10 || y==0 || y==5 ? Blocks.STONE : Blocks.AIR);
        var pos=new BlockPos(4,1,4); var absolute=h.absolutePos(pos);
        h.runAfterDelay(1,() -> {
            awaitLighting(h,h.absolutePos(new BlockPos(1,0,1)),h.absolutePos(new BlockPos(10,5,10)));
            h.assertTrue(OverworldSpawns.checkSpawnRules(OverworldSpawns.SELECTOR.get(),h.getLevel(),EntitySpawnReason.NATURAL,absolute,lightRoll()),"Dark supported platform passes source light test");
            h.setBlock(pos.below(),Blocks.AIR);
            h.assertTrue(!OverworldSpawns.checkSpawnRules(OverworldSpawns.SELECTOR.get(),h.getLevel(),EntitySpawnReason.NATURAL,absolute,lightRoll()),"No supporting ground prevents spawn");
            h.setBlock(pos.below(),Blocks.STONE); h.setBlock(pos.east(),Blocks.TORCH);
            awaitLighting(h,absolute,absolute.east());
            h.assertTrue(!OverworldSpawns.checkSpawnRules(OverworldSpawns.SELECTOR.get(),h.getLevel(),EntitySpawnReason.NATURAL,absolute,lightRoll()),"Torch brightness rejects even largest source random threshold"); h.succeed();
        });
    }
    private static void natural(GameTestHelper h) { natural(h,false); }
    private static void natural(GameTestHelper h,boolean lowDanger) {
        var level=h.getLevel(); var base=h.absolutePos(new BlockPos(4,0,4)); var pos=new BlockPos(base.getX(),181,base.getZ());
        for(int x=-18;x<=18;x++) for(int z=-18;z<=18;z++) for(int y=-1;y<=5;y++) {
            var at=pos.offset(x,y,z); level.getChunk(at);
            level.setBlock(at,(Math.abs(x)==18 || Math.abs(z)==18 || y==-1 || y==5 ? Blocks.STONE : Blocks.AIR).defaultBlockState(),3);
        }
        awaitLighting(h,pos.offset(-18,0,-18),pos.offset(18,0,18));
        h.assertValueEqual(level.getMaxLocalRawBrightness(pos),0,"Enclosed spawn room is dark");
        naturalInRoom(h,pos,lowDanger);
    }
    static void awaitLighting(GameTestHelper h,BlockPos min,BlockPos max) {
        var level=h.getLevel();
        // GameTest ticks run without sleeping. Pump real server/chunk tasks while waiting for the
        // asynchronous light engine, instead of counting virtual ticks or joining on its own thread.
        var pending=new ArrayList<java.util.concurrent.CompletableFuture<?>>();
        for(int x=min.getX()>>4;x<=max.getX()>>4;x++) for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)
            pending.add(level.getChunkSource().getLightEngine().waitForPendingTasks(x,z));
        var ready=java.util.concurrent.CompletableFuture.allOf(pending.toArray(java.util.concurrent.CompletableFuture[]::new));
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        level.getServer().managedBlock(() -> ready.isDone() || System.nanoTime()>=deadline);
        h.assertTrue(ready.isDone() && !ready.isCompletedExceptionally(),"Lighting task barrier completed within ten seconds");
    }
    private static void naturalInRoom(GameTestHelper h,BlockPos pos,boolean lowDanger) {
        var level=h.getLevel(); var area=new AABB(pos).inflate(70); var values=settings(); var saved=values.stream().map(ModConfigSpec.IntValue::get).toList();
        var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"tg-soldier-test"),false);
        var player=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation()) {
            @Override public GameType gameMode() { return GameType.SURVIVAL; }
            @Override public boolean isClientAuthoritative() { return false; }
        };
        var connection=new Connection(PacketFlow.SERVERBOUND); var channel=new EmbeddedChannel(connection);
        player.connection=new ServerGamePacketListenerImpl(level.getServer(),connection,player,cookie);
        player.snapTo(Vec3.atBottomCenterOf(pos.offset(35,0,0))); level.addNewPlayer(player);
        try {
            // Keep every original weight; isolate the distance bucket for this fixture only.
            NpcSpawnConfig.DISTANCE_0.set(lowDanger?Integer.MAX_VALUE:0); NpcSpawnConfig.DISTANCE_1.set(0); NpcSpawnConfig.DISTANCE_2.set(0);
            h.assertTrue(OverworldSpawns.checkSpawnRules(OverworldSpawns.SELECTOR.get(),level,EntitySpawnReason.NATURAL,pos,lightRoll()),
                    "Fixture meets real spawn placement checks: sky="+level.getBrightness(LightLayer.SKY,pos)+", raw="+level.getMaxLocalRawBrightness(pos)
                    +", ground="+level.getBlockState(pos.below())+", biome="+level.getBiome(pos).unwrapKey()+", difficulty="+level.getDifficulty());
            level.getRandom().setSeed(912262L);
            if(lowDanger) {
                h.assertValueEqual(OverworldSpawns.danger(level,pos.getX(),pos.getZ()),0,"Natural spawn fixture is in danger zero");
                List<ZombieFarmer> farmers=List.of(); List<ZombieMiner> miners=List.of();
                for(int attempt=0;attempt<256 && (farmers.isEmpty() || miners.isEmpty());attempt++) {
                    NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER,level,pos);
                    farmers=level.getEntitiesOfClass(ZombieFarmer.class,area); miners=level.getEntitiesOfClass(ZombieMiner.class,area);
                }
                h.assertTrue(!farmers.isEmpty() && !miners.isEmpty(),"Native spawning produces both original danger-zero entries with default weights");
                h.assertTrue(level.getEntitiesOfClass(ZombieSoldier.class,area).isEmpty(),"Soldier remains excluded from danger zero");
                h.assertTrue(!farmers.getFirst().getMainHandItem().isEmpty() && !farmers.getFirst().getItemBySlot(EquipmentSlot.CHEST).isEmpty(),"Naturally spawned farmer has weapon and jacket");
                h.assertTrue(!miners.getFirst().getMainHandItem().isEmpty() && !miners.getFirst().getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"Naturally spawned miner has weapon and helmet");
            } else {
            List<ZombieSoldier> soldiers=List.of();
            for(int attempt=0;attempt<256 && soldiers.isEmpty();attempt++) {
                NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER,level,pos);
                soldiers=level.getEntitiesOfClass(ZombieSoldier.class,area);
            }
            h.assertTrue(!soldiers.isEmpty(),"Minecraft natural spawning reaches the original soldier ticket with reserved weights intact");
            var npc=soldiers.getFirst(); h.assertTrue(!npc.getMainHandItem().isEmpty(),"Natural spawn initializes equipment");
            var cloth=TGContent.MATERIALS.get("heavycloth").get(); long seed=1;
            while(seed<10000 && ZombieSoldierGameTests.table(h).getRandomItems(ZombieSoldierGameTests.params(h,npc,player),seed).stream().noneMatch(s -> s.is(cloth))) seed++;
            h.assertTrue(seed<10000,"Deterministic source cloth loot seed");
            var data=NetherGameTests.save(h,npc); data.putLong("DeathLootTableSeed",seed); NetherGameTests.load(h,npc,data);
            npc.hurtServer(level,level.damageSources().playerAttack(player),1000);
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,npc.getBoundingBox().inflate(3)).stream().anyMatch(e -> e.getItem().is(cloth)),"Naturally spawned soldier drops source heavy cloth on actual death");
            }
        } finally {
            for(int i=0;i<values.size();i++) values.get(i).set(saved.get(i));
            level.getEntitiesOfClass(Mob.class,area).forEach(Entity::discard); level.getEntitiesOfClass(ItemEntity.class,area).forEach(Entity::discard);
            level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED); channel.finishAndReleaseAll();
        }
        h.succeed();
    }
    private OverworldSpawnGameTests() {}
}
