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
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import techguns.modern.*;
import techguns.modern.npc.*;

final class NetherSpawnGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("nether_spawn_table_and_blacklist", () -> NetherSpawnGameTests::tables);
        r.register("nether_spawn_selector_replacement_and_disabled_entries", () -> NetherSpawnGameTests::replacement);
        r.register("nether_natural_spawn_loot_to_fabricator", () -> NetherSpawnGameTests::naturalLoot);
    }
    private static void tables(GameTestHelper h) {
        int netherBiomes=0;
        for(var biome:h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList()) {
            var entries=biome.value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap();
            var selectors=entries.stream().filter(e -> e.value().type()==NetherSpawns.SELECTOR.get()).toList();
            if(biome.is(BiomeTags.IS_NETHER)) {
                netherBiomes++; h.assertValueEqual(selectors.size(),1,"One shared Nether pool entry"); var entry=selectors.getFirst();
                h.assertValueEqual(entry.weight(),300,"Original shared pool weight");
                h.assertValueEqual(entry.value().minCount(),1,"Original min group"); h.assertValueEqual(entry.value().maxCount(),3,"Original max group");
            } else h.assertTrue(selectors.isEmpty(),"No Nether pool in other biomes");
            h.assertTrue(entries.stream().noneMatch(e -> e.value().type()==NpcContent.CYBER_DEMON.get()),"CyberDemon uses original two-stage selection");
        }
        h.assertValueEqual(netherBiomes,5,"Five modern Nether biomes");
        var blacklist=NpcSpawnConfig.BIOME_BLACKLIST.get();
        try {
            var biome=h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.NETHER_WASTES);
            NpcSpawnConfig.BIOME_BLACKLIST.set(List.of("minecraft:nether_wastes")); h.assertTrue(!NetherSpawns.eligibleBiome(biome),"Configured biome is excluded");
        } finally { NpcSpawnConfig.BIOME_BLACKLIST.set(blacklist); }
        h.succeed();
    }
    private static ServerLevel nether(GameTestHelper h) {
        var level=h.getLevel().getServer().getLevel(Level.NETHER); h.assertTrue(level!=null,"Dedicated test server has Nether"); return level;
    }
    private static BlockPos location(GameTestHelper h,ServerLevel level) {
        var base=h.absolutePos(new BlockPos(4,0,4)); var pos=new BlockPos(base.getX(),181,base.getZ()); level.getChunk(pos); return pos;
    }
    private static NetherSpawnSelector selector(ServerLevel level,BlockPos pos) {
        var selector=new NetherSpawnSelector(NetherSpawns.SELECTOR.get(),level); selector.snapTo(pos,37,0); level.addFreshEntity(selector); return selector;
    }
    private static void replacement(GameTestHelper h) {
        var level=nether(h); var pos=location(h,level); var area=new AABB(pos).inflate(5);
        int pig=NpcSpawnConfig.PIGMAN_WEIGHT.get(), cyber=NpcSpawnConfig.CYBER_WEIGHT.get();
        // Unwatched Nether chunks can contain hidden entities, absent from spatial queries until ticking.
        var spawned=new ArrayList<CyberDemon>();
        Consumer<EntityJoinLevelEvent> record=e -> { if(e.getLevel()==level && e.getEntity() instanceof CyberDemon npc && area.contains(npc.position())) spawned.add(npc); };
        NeoForge.EVENT_BUS.addListener(record);
        try {
            NpcSpawnConfig.PIGMAN_WEIGHT.set(0); NpcSpawnConfig.CYBER_WEIGHT.set(30);
            var dummy=selector(level,pos); h.assertTrue(dummy.isRemoved(),"Selector never persists as an NPC");
            h.assertValueEqual(spawned.size(),1,"Selected entry becomes a real CyberDemon");
            var npc=spawned.getFirst(); h.assertTrue(npc.getMainHandItem().is(TGContent.GUNS.get("netherblaster").get()),"Manager equips source weapon");
            h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),10,"Manager equips full weapon"); NetherGameTests.near(h,npc.getYRot(),37,"Spawn heading preserved"); npc.discard();
            NpcSpawnConfig.PIGMAN_WEIGHT.set(100); NpcSpawnConfig.CYBER_WEIGHT.set(0); selector(level,pos);
            h.assertValueEqual(spawned.size(),1,"Unported pigman selection is not replaced with another demon");
            NpcSpawnConfig.PIGMAN_WEIGHT.set(0); selector(level,pos);
            h.assertValueEqual(spawned.size(),1,"Empty table spawns nothing");
            NpcSpawnConfig.CYBER_WEIGHT.set(30); var overworld=selector(h.getLevel(),h.absolutePos(new BlockPos(4,2,4)));
            h.assertTrue(overworld.isRemoved(),"Nether selector rejected in Overworld");
        } finally {
            NeoForge.EVENT_BUS.unregister(record); spawned.forEach(Entity::discard);
            NpcSpawnConfig.PIGMAN_WEIGHT.set(pig); NpcSpawnConfig.CYBER_WEIGHT.set(cyber);
            level.getEntitiesOfClass(CyberDemon.class,area).forEach(Entity::discard);
        }
        h.succeed();
    }
    private static void naturalLoot(GameTestHelper h) {
        var level=nether(h); var pos=location(h,level); var area=new AABB(pos).inflate(70);
        // A deterministic dark platform above the Nether roof exercises Minecraft's actual natural spawning path.
        for(int x=-18;x<=18;x++) for(int z=-18;z<=18;z++) {
            level.setBlock(pos.offset(x,-1,z),Blocks.NETHERRACK.defaultBlockState(),3);
            for(int y=0;y<3;y++) level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"tg-nether-test"),false);
        var player=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation()) {
            @Override public GameType gameMode() { return GameType.SURVIVAL; }
            @Override public boolean isClientAuthoritative() { return false; }
        };
        var connection=new Connection(PacketFlow.SERVERBOUND); var channel=new EmbeddedChannel(connection);
        player.connection=new ServerGamePacketListenerImpl(level.getServer(),connection,player,cookie);
        player.snapTo(Vec3.atBottomCenterOf(pos.offset(35,0,0))); level.addNewPlayer(player);
        try {
            h.assertTrue(NetherSpawns.checkSpawnRules(NetherSpawns.SELECTOR.get(),level,EntitySpawnReason.NATURAL,pos,net.minecraft.util.RandomSource.create(5)),"Source light/ground/dimension rule accepts fixture");
            level.getRandom().setSeed(9126262L);
            List<CyberDemon> demons=List.of();
            for(int attempt=0;attempt<128 && demons.isEmpty();attempt++) {
                NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER,level,pos);
                demons=level.getEntitiesOfClass(CyberDemon.class,area);
            }
            h.assertTrue(!demons.isEmpty(),"Minecraft natural spawning creates CyberDemon with default 100/30 weights");
            var npc=demons.getFirst(); h.assertTrue(npc.armed(),"Naturally spawned NPC is armed");
            var parts=TGContent.MATERIALS.get("cyberneticparts").get();
            var params=new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY,npc).withParameter(LootContextParams.ORIGIN,npc.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE,level.damageSources().playerAttack(player)).withParameter(LootContextParams.ATTACKING_ENTITY,player)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER,player).create(LootContextParamSets.ENTITY);
            long seed=1; while(seed<10000 && NetherGameTests.table(h).getRandomItems(params,seed).stream().filter(s -> s.is(parts)).mapToInt(ItemStack::getCount).sum()!=2) seed++;
            h.assertTrue(seed<10000,"Deterministic source two-part drop seed");
            var data=NetherGameTests.save(h,npc); data.putLong("DeathLootTableSeed",seed); NetherGameTests.load(h,npc,data);
            npc.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY); npc.hurtServer(level,level.damageSources().playerAttack(player),1000);
            var drops=level.getEntitiesOfClass(ItemEntity.class,npc.getBoundingBox().inflate(3));
            h.assertValueEqual(drops.stream().filter(e -> e.getItem().is(parts)).mapToInt(e -> e.getItem().getCount()).sum(),2,"Real natural NPC death yields two cybernetic parts");
            var part=drops.stream().filter(e -> e.getItem().is(parts)).findFirst().orElseThrow().getItem().copyWithCount(1); var steel=material("platesteel");
            var input=CraftingInput.of(3,3,List.of(steel,material("mechanicalpartscarbon"),steel,part,material("electricengine"),part.copy(),steel,material("circuitboardelite"),steel));
            var result=level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow().value().assemble(input);
            h.assertTrue(result.is(techguns.modern.machine.fabricator.FabricatorContent.HOUSING_ITEM.get()),"Actual drop feeds original Fabricator housing recipe");
            h.assertValueEqual(result.getCount(),4,"Four housings");
        } finally {
            level.getEntitiesOfClass(Mob.class,area).forEach(Entity::discard); level.getEntitiesOfClass(ItemEntity.class,area).forEach(Entity::discard);
            level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED); channel.finishAndReleaseAll();
        }
        h.succeed();
    }
    private static ItemStack material(String id) { return TGContent.MATERIALS.get(id).toStack(); }
    private NetherSpawnGameTests() {}
}
