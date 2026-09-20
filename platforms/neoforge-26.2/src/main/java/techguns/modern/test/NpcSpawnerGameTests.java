package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.npc.*;
import techguns.modern.npc.spawner.*;

final class NpcSpawnerGameTests {
    private static final BlockPos POS=new BlockPos(4,2,4);
    private static final List<String> NPCS=List.of("supermutantbasic","cyberdemon","zombiepigmansoldier","zombiesoldier","zombiefarmer","zombieminer","skeletonsoldier","bandit","psychosteve");
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("spawner_default_placement_shape_and_permissions",()->NpcSpawnerGameTests::placement);
        r.register("spawner_delay_capacity_and_death_budget",()->NpcSpawnerGameTests::budget);
        r.register("spawner_despawn_releases_without_kill",()->NpcSpawnerGameTests::despawn);
        r.register("spawner_canceled_death_keeps_reservation",()->NpcSpawnerGameTests::canceledDeath);
        for(String npc:NPCS) r.register("spawner_npc_"+npc,()->h->npc(h,npc));
        r.register("spawner_weapon_override_is_copied",()->NpcSpawnerGameTests::override);
        r.register("spawner_zero_budget_removes_without_drops",()->NpcSpawnerGameTests::zero);
        r.register("spawner_peaceful_and_empty_pool_pause",()->NpcSpawnerGameTests::peaceful);
        r.register("spawner_unported_and_vanilla_entries_not_replaced",()->NpcSpawnerGameTests::unsupported);
        r.register("spawner_finalize_veto_and_retry",()->h->veto(h,0));
        r.register("spawner_spawn_cancellation_and_retry",()->h->veto(h,1));
        r.register("spawner_join_veto_and_retry",()->h->veto(h,2));
        r.register("spawner_save_block_before_npc",()->h->reload(h,false));
        r.register("spawner_save_npc_before_block",()->h->reload(h,true));
        r.register("spawner_mailbox_codec_dedup_and_generation",()->NpcSpawnerGameTests::mailbox);
        r.register("spawner_death_with_unloaded_owner_chunk",()->NpcSpawnerGameTests::unloadedDeath);
        r.register("spawner_replacement_does_not_adopt_old_npc",()->NpcSpawnerGameTests::replacement);
        r.register("spawner_dimension_departure_releases_slot",()->NpcSpawnerGameTests::dimension);
        r.register("spawner_dimension_copy_never_reclaims_old_owner",()->NpcSpawnerGameTests::dimensionCopy);
        r.register("spawner_sun_exemption_is_techguns_origin_only",()->NpcSpawnerGameTests::sunlight);
        r.register("spawner_range_height_and_no_natural_spawn_checks",()->NpcSpawnerGameTests::range);
        r.register("spawner_bad_persisted_limits_are_bounded",()->NpcSpawnerGameTests::badData);
    }
    private static NpcSpawnerBlockEntity place(GameTestHelper h,int remaining,int cap,int interval,String id) {
        h.setBlock(POS,NpcSpawnerContent.BLOCK.get()); var block=h.getBlockEntity(POS,NpcSpawnerBlockEntity.class);
        block.configure(remaining,cap,interval,0,0,id.isEmpty()?List.of():List.of(entry(id,1)),ItemStack.EMPTY); return block;
    }
    private static NpcSpawnerBlockEntity.Entry entry(String id,int weight) { return new NpcSpawnerBlockEntity.Entry(net.minecraft.resources.Identifier.parse(id.contains(":")?id:"techguns:"+id),weight); }
    private static void tick(GameTestHelper h,NpcSpawnerBlockEntity b,int count) {
        for(int i=0;i<count && !b.isRemoved();i++) NpcSpawnerBlockEntity.serverTick(h.getLevel(),b.getBlockPos(),b.getBlockState(),b);
    }
    private static ArmedNpc first(GameTestHelper h,NpcSpawnerBlockEntity b) {
        var npc=(ArmedNpc)h.getLevel().getEntity(b.activeIds().iterator().next()); npc.removeFreeWill(); npc.setNoGravity(true); return npc;
    }
    private static void cleanup(GameTestHelper h,NpcSpawnerBlockEntity b) {
        for(UUID id:b.activeIds()) { var entity=h.getLevel().getEntity(id); if(entity!=null) entity.discard(); }
        h.getLevel().setBlock(b.getBlockPos(),Blocks.AIR.defaultBlockState(),3);
    }
    private static void kill(GameTestHelper h,ArmedNpc npc) { npc.invulnerableTime=0; npc.hurtServer(h.getLevel(),h.getLevel().damageSources().genericKill(),10000); }
    private static CompoundTag save(GameTestHelper h,NpcSpawnerBlockEntity b) { return b.saveWithFullMetadata(h.getLevel().registryAccess()); }
    private static void load(GameTestHelper h,NpcSpawnerBlockEntity b,CompoundTag tag) { b.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),tag)); }
    private static void placement(GameTestHelper h) {
        var p=WeaponGameTests.player(h); p.setItemInHand(InteractionHand.MAIN_HAND,NpcSpawnerContent.ITEM.toStack(2)); h.setBlock(POS.below(),Blocks.STONE);
        h.useBlock(POS.below(),p,new BlockHitResult(Vec3.atCenterOf(h.absolutePos(POS.below())),Direction.UP,h.absolutePos(POS.below()),false));
        var b=h.getBlockEntity(POS,NpcSpawnerBlockEntity.class);
        h.assertValueEqual(p.getMainHandItem().getCount(),1,"Placed source block consumes one item");
        h.assertValueEqual(b.remaining(),5,"Default death budget"); h.assertValueEqual(b.maximum(),3,"Default active cap"); h.assertValueEqual(b.delay(),200,"First spawn delay");
        h.assertValueEqual(b.entries(),List.of(entry("zombiesoldier",1)),"HOLE placement uses ZombieSoldier");
        var state=b.getBlockState(); h.assertTrue(state.getCollisionShape(h.getLevel(),b.getBlockPos()).isEmpty(),"Source has no collision");
        h.assertValueEqual(state.getShape(h.getLevel(),b.getBlockPos(),CollisionContext.empty()).bounds(),new AABB(.125,0,.125,.875,.125,.875),"Exact source selection box");
        h.assertTrue(state.getDestroySpeed(h.getLevel(),b.getBlockPos())<0,"Cannot mine in survival");
        h.assertTrue(NpcSpawnerContent.ENTITY.get().onlyOpCanSetNbt(),"Only operators can place custom NPC block data");
        h.assertTrue(b.getUpdatePacket()==null && b.getUpdateTag(h.getLevel().registryAccess()).isEmpty(),"No private live UUIDs in client update tag"); cleanup(h,b); h.succeed();
    }
    private static void budget(GameTestHelper h) {
        var b=place(h,3,2,3,"zombiesoldier"); tick(h,b,2); h.assertValueEqual(b.activeCount(),0,"No early spawn"); tick(h,b,1);
        h.assertValueEqual(b.activeCount(),1,"One spawn at deadline"); h.assertValueEqual(b.remaining(),3,"Spawning does not consume kill budget");
        tick(h,b,3); h.assertValueEqual(b.activeCount(),2,"Second active slot"); tick(h,b,9); h.assertValueEqual(b.activeCount(),2,"Cap holds");
        kill(h,first(h,b)); h.assertValueEqual(b.remaining(),2,"Death costs one"); tick(h,b,3); h.assertValueEqual(b.activeCount(),2,"Replacement allowed below remaining cap");
        kill(h,first(h,b)); h.assertValueEqual(b.remaining(),1,"Second death"); tick(h,b,3); h.assertValueEqual(b.activeCount(),1,"Remaining budget lowers active cap");
        var last=first(h,b); UUID id=last.getUUID(); kill(h,last); b.finish(id,true); h.assertValueEqual(b.remaining(),0,"Duplicate death notification is harmless");
        tick(h,b,1); h.assertTrue(h.getLevel().getBlockState(b.getBlockPos()).isAir(),"Exhausted source spawner disappears"); h.succeed();
    }
    private static void despawn(GameTestHelper h) {
        var b=place(h,2,1,1,"zombiesoldier"); tick(h,b,1); first(h,b).discard();
        h.assertValueEqual(b.activeCount(),0,"Discard frees slot"); h.assertValueEqual(b.remaining(),2,"Discard is not a kill");
        tick(h,b,1); h.assertValueEqual(b.activeCount(),1,"Replacement after despawn"); cleanup(h,b); h.succeed();
    }
    private static void canceledDeath(GameTestHelper h) {
        var b=place(h,1,1,1,"zombiesoldier"); tick(h,b,1); var npc=first(h,b);
        Consumer<LivingDeathEvent> cancel=e->{ if(e.getEntity()==npc) { e.setCanceled(true); npc.setHealth(1); } }; NeoForge.EVENT_BUS.addListener(cancel);
        try { kill(h,npc); h.assertValueEqual(b.remaining(),1,"Canceled death does not consume quota"); h.assertValueEqual(b.activeCount(),1,"Canceled death retains slot"); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); }
        kill(h,npc); h.assertValueEqual(b.remaining(),0,"Later real death consumes quota once"); cleanup(h,b); h.succeed();
    }
    private static void npc(GameTestHelper h,String id) {
        var b=place(h,5,1,1,id); tick(h,b,1); var npc=first(h,b);
        h.assertValueEqual(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(npc.getType()),TGContent.id(id),"Configured original NPC type");
        h.assertTrue(!npc.getMainHandItem().isEmpty(),"Native finalizeSpawn initializes source equipment");
        h.assertValueEqual(npc.spawnerLink(),b.link(),"NPC receives this instance's persistent origin");
        h.assertValueEqual(npc.getHomePosition(),b.getBlockPos(),"Original home center"); h.assertValueEqual(npc.getHomeRadius(),10,"Original home radius");
        cleanup(h,b); h.succeed();
    }
    private static void override(GameTestHelper h) {
        var b=place(h,5,2,1,"zombiesoldier"); var saw=TGContent.GUNS.get("chainsaw").toStack(); saw.set(TGContent.ROUNDS.get(),17); saw.set(TGContent.MINING_HEAD.get(),2);
        b.configure(5,2,1,0,0,List.of(entry("zombiesoldier",1)),saw); saw.set(TGContent.ROUNDS.get(),0); tick(h,b,1);
        var first=first(h,b); h.assertValueEqual(GunItem.rounds(first.getMainHandItem()),17,"Configuration copied caller stack");
        first.getMainHandItem().set(TGContent.ROUNDS.get(),3); tick(h,b,1);
        var other=b.activeIds().stream().filter(id->!id.equals(first.getUUID())).map(id->(ArmedNpc)h.getLevel().getEntity(id)).findFirst().orElseThrow();
        h.assertValueEqual(GunItem.rounds(other.getMainHandItem()),17,"Each NPC receives an independent copy"); h.assertValueEqual(ChainsawItem.head(other.getMainHandItem()),2,"Override components retained");
        var saved=save(h,b); load(h,b,saved); h.assertValueEqual(GunItem.rounds(b.weaponOverride()),17,"Override survives block save"); cleanup(h,b); h.succeed();
    }
    private static void zero(GameTestHelper h) {
        var b=place(h,0,1,1,"zombiesoldier"); var drops=net.minecraft.world.level.block.Block.getDrops(b.getBlockState(),h.getLevel(),b.getBlockPos(),b);
        h.assertTrue(drops.isEmpty(),"No block loot or invented recipe output"); tick(h,b,1); h.assertTrue(h.getLevel().getBlockState(b.getBlockPos()).isAir(),"Zero budget removes even before first spawn"); h.succeed();
    }
    private static void peaceful(GameTestHelper h) {
        var b=place(h,5,1,1,"zombiesoldier"); var before=h.getLevel().getDifficulty();
        try { h.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL,true); tick(h,b,1); h.assertValueEqual(b.activeCount(),0,"Peaceful pauses source spawning"); h.assertValueEqual(b.remaining(),5,"No quota consumed"); }
        finally { h.getLevel().getServer().setDifficulty(before,true); }
        b.configure(5,1,1,0,0,List.of(),ItemStack.EMPTY); tick(h,b,5); h.assertValueEqual(b.activeCount(),0,"Empty configured pool remains empty"); cleanup(h,b); h.succeed();
    }
    private static void unsupported(GameTestHelper h) {
        var b=place(h,5,1,1,"armysoldier"); tick(h,b,1); h.assertValueEqual(b.activeCount(),0,"Pending ArmySoldier is never replaced with ZombieSoldier");
        h.assertValueEqual(b.entries(),List.of(entry("armysoldier",1)),"Unported ID retained in configuration"); h.assertValueEqual(b.delay(),1,"Invalid source choice resets interval");
        b.configure(5,1,1,0,0,List.of(entry("minecraft:zombie",1)),ItemStack.EMPTY); tick(h,b,1); h.assertValueEqual(b.activeCount(),0,"Vanilla mobs do not implement Techguns lifecycle"); cleanup(h,b); h.succeed();
    }
    private static void veto(GameTestHelper h,int kind) {
        var b=place(h,2,1,1,"zombiesoldier");
        Consumer<FinalizeSpawnEvent> prepare=e->{ if(e.getSpawner()!=null && e.getSpawner().left().orElse(null)==b) { if(kind==0) e.setCanceled(true); else e.setSpawnCancelled(true); } };
        Consumer<EntityJoinLevelEvent> join=e->{ if(e.getEntity() instanceof ArmedNpc npc && b.link().equals(npc.spawnerLink())) e.setCanceled(true); };
        if(kind<2) NeoForge.EVENT_BUS.addListener(prepare); else NeoForge.EVENT_BUS.addListener(join);
        try { tick(h,b,1); h.assertValueEqual(b.activeCount(),0,"Vetoed spawn cannot reserve a phantom slot"); h.assertValueEqual(b.remaining(),2,"Veto never spends budget"); }
        finally { NeoForge.EVENT_BUS.unregister(kind<2?prepare:join); }
        tick(h,b,1); h.assertValueEqual(b.activeCount(),1,"Next-tick retry works after veto removed"); cleanup(h,b); h.succeed();
    }
    private static void reload(GameTestHelper h,boolean npcFirst) {
        var b=place(h,3,1,1,"zombiesoldier"); tick(h,b,1); var npc=first(h,b); var mobTag=NetherGameTests.save(h,npc); var blockTag=save(h,b); UUID id=npc.getUUID();
        npc.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); h.assertValueEqual(b.activeCount(),1,"Chunk unload retains reservation"); h.getLevel().removeBlockEntity(b.getBlockPos());
        var restored=new ZombieSoldier(NpcContent.ZOMBIE_SOLDIER.get(),h.getLevel()); NetherGameTests.load(h,restored,mobTag);
        if(npcFirst) restored.tick(); // No block yet: relink must retry later, without loading chunks.
        var block=new NpcSpawnerBlockEntity(b.getBlockPos(),b.getBlockState()); load(h,block,blockTag); h.getLevel().setBlockEntity(block);
        tick(h,block,2); h.assertValueEqual(block.activeCount(),1,"Block loaded before NPC cannot overspawn");
        for(int i=0;i<21;i++) restored.tick(); h.assertValueEqual(restored.spawnerLink(),block.link(),"Origin survives and finds the restored instance");
        h.assertValueEqual(block.activeIds(),Set.of(id),"Relink is idempotent"); h.assertValueEqual(restored.getHomeRadius(),10,"Home survived native NPC save");
        kill(h,restored); h.assertValueEqual(block.remaining(),2,"Restored NPC death reconciles exactly once"); h.assertValueEqual(block.activeCount(),0,"Restored death releases reservation");
        restored.discard(); cleanup(h,block); h.succeed();
    }
    private static void mailbox(GameTestHelper h) {
        var b=place(h,3,1,1,"zombiesoldier"); tick(h,b,1); var npc=first(h,b); var mailbox=new SpawnerMailbox();
        mailbox.enqueue(b.link(),npc.getUUID(),false); mailbox.enqueue(b.link(),npc.getUUID(),true); mailbox.enqueue(b.link(),npc.getUUID(),true);
        h.assertValueEqual(mailbox.size(),1,"Deferred notifications deduplicate and death takes precedence");
        var saved=SpawnerMailbox.CODEC.encodeStart(NbtOps.INSTANCE,mailbox).getOrThrow(); var restored=SpawnerMailbox.CODEC.parse(NbtOps.INSTANCE,saved).getOrThrow();
        var notices=restored.drain(b.link()); h.assertValueEqual(notices.size(),1,"SavedData codec retains pending death"); h.assertTrue(notices.getFirst().killed(),"Death state survived");
        h.assertTrue(restored.drain(b.link()).isEmpty(),"Mailbox drains once");
        mailbox.enqueue(new SpawnerLink(b.link().origin(),UUID.randomUUID()),UUID.randomUUID(),true);
        mailbox.drain(b.link()); h.assertValueEqual(mailbox.size(),0,"Old instances at this position are not delivered to the new owner"); cleanup(h,b); h.succeed();
    }
    private static void unloadedDeath(GameTestHelper h) {
        var level=h.getLevel(); var far=new BlockPos(20000000,100,20000000); h.assertTrue(!level.hasChunkAt(far),"Owner fixture starts outside loaded chunks");
        var state=NpcSpawnerContent.BLOCK.get().defaultBlockState(); var offline=new NpcSpawnerBlockEntity(far,state); offline.setLevel(level);
        offline.configure(3,1,200,0,0,List.of(entry("zombiesoldier",1)),ItemStack.EMPTY);
        var npc=new ZombieSoldier(NpcContent.ZOMBIE_SOLDIER.get(),level); npc.setPos(h.absoluteVec(new Vec3(4,3,4))); npc.bindSpawner(offline.link()); offline.relink(npc); level.addFreshEntity(npc);
        var saved=save(h,offline); kill(h,npc); h.assertTrue(!level.hasChunkAt(far),"Death notification never loads owner chunk");
        var mailbox=SpawnerMailbox.get(level); h.assertValueEqual(mailbox.size(),1,"Unloaded death queued");
        var encoded=SpawnerMailbox.CODEC.encodeStart(NbtOps.INSTANCE,mailbox).getOrThrow(); level.getDataStorage().set(SpawnerMailbox.TYPE,SpawnerMailbox.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow());
        level.getChunk(far); level.setBlock(far,state,3); var loaded=(NpcSpawnerBlockEntity)level.getBlockEntity(far); load(h,loaded,saved); tick(h,loaded,1);
        h.assertValueEqual(loaded.remaining(),2,"Deferred death applied after owner restore"); h.assertValueEqual(loaded.activeCount(),0,"Offline reservation released");
        tick(h,loaded,1); h.assertValueEqual(loaded.remaining(),2,"Queued kill never counted twice"); npc.discard(); cleanup(h,loaded); h.succeed();
    }
    private static void replacement(GameTestHelper h) {
        var old=place(h,3,1,1,"zombiesoldier"); tick(h,old,1); var npc=first(h,old); var oldLink=old.link(); var copied=save(h,old); h.setBlock(POS,Blocks.AIR);
        var next=place(h,5,1,100,"zombiesoldier"); load(h,next,copied); next.defaultHole();
        h.assertTrue(!next.link().equals(oldLink),"Even copied block NBT receives a new generation on placement"); npc.tick(); kill(h,npc);
        h.assertValueEqual(next.remaining(),3,"Old NPC cannot spend copied block quota"); h.assertValueEqual(next.activeCount(),0,"Old NPC cannot be adopted by replacement");
        h.assertTrue(npc.hasSpawnerOrigin(),"Original source origin marker persists after block removal"); npc.discard(); cleanup(h,next); h.succeed();
    }
    private static void dimension(GameTestHelper h) {
        var b=place(h,3,1,1,"zombiesoldier"); tick(h,b,1); var npc=first(h,b); npc.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        h.assertValueEqual(b.remaining(),3,"Dimension departure is not a kill"); h.assertValueEqual(b.activeCount(),0,"Dimension departure frees slot"); cleanup(h,b); h.succeed();
    }
    private static void dimensionCopy(GameTestHelper h) {
        var b=place(h,3,1,1,"zombiesoldier"); tick(h,b,1); var npc=first(h,b); var tag=NetherGameTests.save(h,npc);
        var other=h.getLevel().getServer().getLevel(Level.NETHER); var copied=new ZombieSoldier(NpcContent.ZOMBIE_SOLDIER.get(),other);
        NetherGameTests.load(h,copied,tag); npc.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        h.assertValueEqual(copied.getHomeRadius(),-1,"Foreign dimension clears old home restriction");
        var returned=new ZombieSoldier(NpcContent.ZOMBIE_SOLDIER.get(),h.getLevel()); NetherGameTests.load(h,returned,NetherGameTests.save(h,copied)); returned.tick();
        h.assertValueEqual(b.activeCount(),0,"Returning copy cannot re-adopt old slot"); kill(h,returned); h.assertValueEqual(b.remaining(),3,"Transferred NPC death never spends old quota");
        copied.discard(); returned.discard(); cleanup(h,b); h.succeed();
    }
    private static void sunlight(GameTestHelper h) {
        var level=h.getLevel(); var clock=level.dimensionType().defaultClock().orElseThrow(); long time=level.clockManager().getTotalTicks(clock);
        var at=h.absolutePos(new BlockPos(4,0,4)); at=new BlockPos(at.getX(),181,at.getZ()); level.getChunk(at);
        var linked=new ZombieMiner(NpcContent.MINER.get(),level); var ordinary=new ZombieMiner(NpcContent.MINER.get(),level);
        linked.bindSpawner(new SpawnerLink(GlobalPos.of(level.dimension(),at.below()),UUID.randomUUID()));
        try {
            h.setTime(6000); level.updateSkyBrightness(); OverworldSpawnGameTests.awaitLighting(h,at,at);
            long seed=1; while(seed<10000 && !UndeadRules.sunIgnites(1,RandomSource.create(seed).nextFloat())) seed++;
            for(var npc:List.of(linked,ordinary)) { npc.setPos(Vec3.atBottomCenterOf(at)); npc.setNoGravity(true); npc.removeFreeWill(); npc.getRandom().setSeed(seed); npc.aiStep(); }
            h.assertTrue(!linked.isOnFire() && ordinary.isOnFire(),"Only Techguns origin marker prevents source sunlight ignition");
            var restored=new ZombieMiner(NpcContent.MINER.get(),level); NetherGameTests.load(h,restored,NetherGameTests.save(h,linked)); restored.getRandom().setSeed(seed); restored.aiStep();
            h.assertTrue(!restored.isOnFire(),"Source daylight exemption survives NPC save, even with absent original block"); restored.discard();
        } finally { h.setTime(time); level.updateSkyBrightness(); linked.discard(); ordinary.discard(); }
        h.succeed();
    }
    private static void range(GameTestHelper h) {
        var b=place(h,20,20,1,"zombiesoldier"); b.configure(20,20,1,2,3,List.of(entry("zombiesoldier",1)),ItemStack.EMPTY); tick(h,b,20);
        h.assertValueEqual(b.activeCount(),20,"No player proximity, darkness, supporting ground or collision requirement");
        for(UUID id:b.activeIds()) {
            var npc=h.getLevel().getEntity(id); var p=b.getBlockPos();
            h.assertTrue(Math.abs(npc.getX()-(p.getX()+.5))<2 && Math.abs(npc.getZ()-(p.getZ()+.5))<2,"Original triangular horizontal range");
            ArmorGameTests.near(h,npc.getY(),p.getY()+4,"Source height is block Y + 1 + offset");
        }
        cleanup(h,b); h.succeed();
    }
    private static void badData(GameTestHelper h) {
        var b=place(h,5,3,200,"zombiesoldier"); var saved=save(h,b); saved.putInt("spawnDelay",0); saved.putInt("maxActive",-3); saved.putDouble("spawnRange",Double.NaN); saved.putInt("delay",Integer.MIN_VALUE);
        load(h,b,saved); h.assertValueEqual(b.interval(),200,"Invalid source delay falls back to 200"); h.assertValueEqual(b.maximum(),1,"Invalid active cap becomes one");
        ArmorGameTests.near(h,b.range(),2,"Non-finite range uses default"); h.assertValueEqual(b.delay(),0,"Expired timer is bounded"); cleanup(h,b); h.succeed();
    }
}
