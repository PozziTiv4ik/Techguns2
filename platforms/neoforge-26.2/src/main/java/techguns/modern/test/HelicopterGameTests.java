package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.npc.*;
import techguns.modern.npc.spawner.*;

final class HelicopterGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("helicopter_attributes_faction_and_fire_immunity",()->HelicopterGameTests::attributes);
        r.register("helicopter_real_egg_and_synced_state",()->HelicopterGameTests::egg);
        r.register("helicopter_typed_armor_ignores_unused_resistance",()->HelicopterGameTests::armor);
        r.register("helicopter_air_travel_without_gravity_or_climbing",()->HelicopterGameTests::travel);
        r.register("helicopter_waypoint_collision_and_saved_flight",()->HelicopterGameTests::movement);
        r.register("helicopter_random_flight_current_column_height",()->HelicopterGameTests::randomFlight);
        r.register("helicopter_original_player_acquisition_band_and_stealth",()->HelicopterGameTests::targets);
        r.register("helicopter_original_look_pitch_and_yaw",()->HelicopterGameTests::look);
        r.register("helicopter_five_real_bullets_and_one_rocket",()->HelicopterGameTests::burst);
        r.register("helicopter_occluded_clock_and_saved_attack",()->HelicopterGameTests::occluded);
        r.register("helicopter_both_rotated_rocket_muzzles",()->HelicopterGameTests::muzzles);
        r.register("helicopter_registered_server_ai_hits_target",()->HelicopterGameTests::realAi);
        r.register("helicopter_bullet_saved_flight_and_100_movements",()->h->savedShot(h,false));
        r.register("helicopter_rocket_saved_flight_and_100_movements",()->h->savedShot(h,true));
        r.register("helicopter_bullet_native_water_drag",()->HelicopterGameTests::water);
        r.register("helicopter_bullet_never_rehits_its_shooter",()->HelicopterGameTests::owner);
        r.register("helicopter_real_rocket_source_30_40_blast",()->h->blast(h,false));
        r.register("helicopter_rocket_wall_visibility_no_block_damage",()->h->blast(h,true));
        r.register("helicopter_projectile_impact_veto",()->HelicopterGameTests::cancel);
        r.register("helicopter_live_damage_factor_no_difficulty_penalty",()->HelicopterGameTests::factor);
        r.register("helicopter_four_loot_pools_with_looting",()->HelicopterGameTests::loot);
        r.register("helicopter_actual_death_saved_corpse_delayed_xp",()->HelicopterGameTests::death);
        r.register("helicopter_delayed_xp_uses_rule_at_animation_end",()->h->experience(h,true));
        r.register("helicopter_expired_player_credit_cannot_drop_xp",()->h->experience(h,false));
        r.register("helicopter_one_finite_camp_post_saved_owner",()->HelicopterGameTests::spawner);
        r.register("helicopter_peaceful_removal_preserves_death_quota",()->HelicopterGameTests::peaceful);
    }
    private static void near(GameTestHelper h,double a,double b,String message) { h.assertTrue(Math.abs(a-b)<.001,message+": "+a+" != "+b); }
    private static int experienceValue(GameTestHelper h,List<ExperienceOrb> orbs) {
        // Vanilla award() can merge equally valued orbs immediately; Value alone omits their stacked Count.
        return orbs.stream().filter(e->!e.isRemoved()).mapToInt(e->e.getValue()*NetherGameTests.save(h,e).getIntOr("Count",1)).sum();
    }
    private static AttackHelicopter mob(GameTestHelper h,Vec3 at) {
        var m=new AttackHelicopter(NpcContent.HELICOPTER.get(),h.getLevel()); m.setPos(h.absoluteVec(at)); m.removeFreeWill(); h.getLevel().addFreshEntity(m); return m;
    }
    private static LivingEntity target(GameTestHelper h,Vec3 at) { return GhastlingGameTests.target(h,at); }
    private static void load(ServerLevel l,BlockPos center,int radius) { for(int x=(center.getX()-radius)>>4;x<=(center.getX()+radius)>>4;x++) for(int z=(center.getZ()-radius)>>4;z<=(center.getZ()+radius)>>4;z++) l.getChunk(x,z); }
    private static void cleanup(GameTestHelper h,AttackHelicopter m) {
        h.getLevel().getEntitiesOfClass(Projectile.class,m.getBoundingBox().inflate(180),p->p.getOwner()==m).forEach(Entity::discard); m.discard();
    }
    private static void attributes(GameTestHelper h) {
        var m=mob(h,new Vec3(5,80,5)); near(h,m.getMaxHealth(),100,"Health"); near(h,m.getAttributeValue(Attributes.FOLLOW_RANGE),128,"Follow range");
        near(h,m.getBbWidth(),4,"Width"); near(h,m.getBbHeight(),4,"Height"); near(h,m.getEyeHeight(),.5,"Eye height"); near(h,m.getAttributeValue(Attributes.ARMOR),16,"Ordinary armor attribute"); near(h,m.getAttributeValue(Attributes.ARMOR_TOUGHNESS),5,"Toughness");
        h.assertTrue(!HostileNpc.class.isInstance(m) && !ArmedNpc.class.isInstance(m) && m.getMainHandItem().isEmpty(),"Separate flying NPC without GenericNPC faction or invented handheld gun");
        h.assertTrue(m.fireImmune() && !m.isInvertedHealAndHarm(),"Living fire-immune NPC"); m.hurtServer(h.getLevel(),h.getLevel().damageSources().lava(),10); near(h,m.getHealth(),100,"Lava immunity");
        h.assertValueEqual(m.getMaxSpawnClusterSize(),1,"Original spawn group bound"); near(h,m.getSoundVolume(),10,"Original hurt volume"); near(h,m.getExperienceReward(h.getLevel(),null),5,"Original XP reward");
        int accepted=0; m.getRandom().setSeed(42); for(int i=0;i<2000;i++) if(m.checkSpawnRules(h.getLevel(),EntitySpawnReason.NATURAL)) accepted++;
        h.assertTrue(accepted>=65 && accepted<=140,"One in twenty optional native spawn-rule check; no natural table registration"); cleanup(h,m); h.succeed();
    }
    private static void egg(GameTestHelper h) {
        var player=WeaponGameTests.player(h); player.setItemInHand(InteractionHand.MAIN_HAND,NpcContent.HELICOPTER_EGG.toStack()); h.setBlock(5,1,5,Blocks.STONE);
        var seen=new ArrayList<AttackHelicopter>(); Consumer<EntityJoinLevelEvent> observe=e->{if(e.getLevel()==h.getLevel() && e.getEntity() instanceof AttackHelicopter m) seen.add(m);}; NeoForge.EVENT_BUS.addListener(observe);
        try { h.useBlock(new BlockPos(5,1,5),player); h.assertValueEqual(seen.size(),1,"Real spawn egg creates the registered helicopter"); var m=seen.getFirst(); m.setAttacking(true);
            h.assertTrue(m.getEntityData().getNonDefaultValues()!=null,"Attack state is synchronized"); m.setHealth(67); var copy=new AttackHelicopter(NpcContent.HELICOPTER.get(),h.getLevel()); NetherGameTests.load(h,copy,NetherGameTests.save(h,m)); near(h,copy.getHealth(),67,"Native health survives save"); copy.discard(); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); seen.forEach(Entity::discard); }
    }
    private static void armor(GameTestHelper h) {
        var m=mob(h,new Vec3(5,80,5)); var l=h.getLevel();
        m.hurtServer(l,l.damageSources().playerAttack(WeaponGameTests.player(h)),10); near(h,m.getHealth(),98,"Physical typed armor 20");
        var b=new Bullet(TGContent.BULLET.get(),l); b.configure(Weapons.definition("as50"));
        var type=ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")); m.invulnerableTime=0;
        float before=m.getHealth(); m.hurtServer(l,b.shotDamage().source(l,type,b),20);
        near(h,before-m.getHealth(),ArmorMath.afterArmor(20,20,5,(float)b.weapon().penetration()),"Actual damage formula uses toughness, not inactive getPenetrationResistance");
        var rocket=new RocketProjectile(TGContent.ROCKET.get(),l); rocket.configure(HelicopterWeapons.ROCKET,RocketVariant.DEFAULT,false); m.invulnerableTime=0; before=m.getHealth();
        m.hurtServer(l,rocket.shotDamage().source(l,ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("rocket")),rocket),12); near(h,before-m.getHealth(),12,"Explosion typed armor is zero despite attribute 16"); cleanup(h,m); h.succeed();
    }
    private static void travel(GameTestHelper h) {
        var m=mob(h,new Vec3(5,80,5)); h.assertTrue(!m.isNoGravity(),"Flight does not rely on fixture no-gravity flag"); var start=m.position();
        for(int i=0;i<40;i++) m.travel(Vec3.ZERO); h.assertValueEqual(m.position(),start,"No falling acceleration while hovering");
        m.setDeltaMovement(1,.5,0); m.travel(Vec3.ZERO); near(h,m.getX(),start.x+1,"Native flight movement"); near(h,m.getDeltaMovement().x,(double).91f,"Original air friction"); near(h,m.getDeltaMovement().y,.5*(double).91f,"No vertical gravity"); h.assertTrue(!m.onClimbable(),"Flying entity cannot climb"); cleanup(h,m); h.succeed();
    }
    private static void movement(GameTestHelper h) {
        var m=mob(h,new Vec3(5,80,5)); var l=h.getLevel(); load(l,m.blockPosition(),32); var origin=m.position(); var move=m.getMoveControl();
        move.setWantedPosition(origin.x+12,origin.y,origin.z,1); move.tick(); near(h,m.getDeltaMovement().x,.1,"Original course acceleration");
        var copy=new AttackHelicopter(NpcContent.HELICOPTER.get(),l); NetherGameTests.load(h,copy,NetherGameTests.save(h,m));
        h.assertTrue(copy.getMoveControl().hasWanted(),"Waypoint persists"); near(h,copy.getMoveControl().getWantedX(),origin.x+12,"Saved destination"); h.assertValueEqual(copy.getDeltaMovement(),m.getDeltaMovement(),"Momentum survives");
        h.assertValueEqual(NetherGameTests.save(h,copy).getIntOr("flight_cooldown",-1),NetherGameTests.save(h,m).getIntOr("flight_cooldown",-2),"Saved course cooldown is retained");
        m.setDeltaMovement(Vec3.ZERO); var wall=m.blockPosition().east(5); for(int y=-1;y<=5;y++) for(int z=-3;z<=3;z++) l.setBlock(wall.offset(0,y,z),Blocks.STONE.defaultBlockState(),2);
        for(int i=0;i<8;i++) move.tick(); h.assertTrue(!move.hasWanted() && m.getDeltaMovement().equals(Vec3.ZERO),"Entire four-wide hull route is collision-tested before acceleration");
        for(int y=-1;y<=5;y++) for(int z=-3;z<=3;z++) l.setBlock(wall.offset(0,y,z),Blocks.AIR.defaultBlockState(),2); copy.discard(); cleanup(h,m); h.succeed();
    }
    private static void randomFlight(GameTestHelper h) {
        var m=mob(h,new Vec3(5,80,5)); var l=h.getLevel(); var floor=m.blockPosition().below(20); l.setBlock(floor,Blocks.STONE.defaultBlockState(),2);
        var goal=new HelicopterFlight.RandomFly(m); h.assertTrue(goal.canUse(),"Idle control requests a waypoint"); goal.start();
        near(h,m.getMoveControl().getWantedY(),l.getHeight(Heightmap.Types.MOTION_BLOCKING,(int)m.getX(),(int)m.getZ())+24,"Altitude samples current column plus 24");
        h.assertTrue(Math.abs(m.getMoveControl().getWantedX()-m.getX())<=16 && Math.abs(m.getMoveControl().getWantedZ()-m.getZ())<=16,"Source horizontal selection bounds");
        h.assertTrue(!goal.canContinueToUse(),"Waypoint selection is a one-shot goal"); l.setBlock(floor,Blocks.AIR.defaultBlockState(),2); cleanup(h,m); h.succeed();
    }
    private static void targets(GameTestHelper h) {
        var m=mob(h,new Vec3(5,80,5)); var l=h.getLevel(); load(l,m.blockPosition(),140);
        var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"heli-target-test"),false);
        var p=new ServerPlayer(l.getServer(),l,cookie.gameProfile(),cookie.clientInformation()) { @Override public GameType gameMode() { return GameType.SURVIVAL; } @Override public boolean isClientAuthoritative() { return false; } };
        var connection=new Connection(PacketFlow.SERVERBOUND); var channel=new EmbeddedChannel(connection); p.connection=new ServerGamePacketListenerImpl(l.getServer(),connection,p,cookie); p.setNoGravity(true); GameType.SURVIVAL.updatePlayerAbilities(p.getAbilities()); p.snapTo(m.position().add(6,0,0)); l.addNewPlayer(p);
        h.runAfterDelay(1,()->{ try {
            var goal=new HelicopterFlight.Target(m); h.assertTrue(goal.canUse(),"Nearby native survival player is acquired: eligible="+goal.acceptable(p)+", sight="+m.hasLineOfSight(p)+", distance="+m.distanceToSqr(p)+", invulnerable="+p.getAbilities().invulnerable+", listed="+l.players().contains(p)+", nearby="+l.getEntitiesOfClass(Player.class,m.getBoundingBox().inflate(128,4,128)).size()); goal.start(); h.assertTrue(m.getTarget()==p,"Nearest player becomes real target");
            m.setPos(m.position().add(0,24,0)); h.assertTrue(goal.canContinueToUse(),"Existing target can remain 24 below helicopter"); goal.stop(); h.assertTrue(!goal.canUse(),"New acquisition retains old four-block vertical expansion"); m.setPos(m.position().add(0,-24,0));
            p.snapTo(m.position().add(110,0,0)); h.assertTrue(goal.acceptable(p),"Visible player inside follow range"); p.setShiftKeyDown(true); h.assertTrue(!goal.acceptable(p),"Sneaking reduces detection range to 80 percent"); p.setShiftKeyDown(false);
            p.snapTo(m.position().add(10,0,0)); p.setInvisible(true); h.assertTrue(!goal.acceptable(p),"Unarmored invisibility has minimum ten-percent coverage");
            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET)); p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE)); p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS)); p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS)); h.assertTrue(goal.acceptable(p),"Visible armor increases invisible-player detection");
            p.getAbilities().invulnerable=true; h.assertTrue(!goal.acceptable(p),"Invulnerable players excluded");
        } finally { l.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED); channel.finishAndReleaseAll(); cleanup(h,m); } h.succeed(); });
    }
    private static void look(GameTestHelper h) {
        var m=mob(h,new Vec3(5,80,5)); var t=target(h,new Vec3(5,70,15)); m.setTarget(t); var goal=new HelicopterFlight.Look(m); goal.tick(); m.getLookControl().tick();
        near(h,m.getYRot(),0,"Body points south"); near(h,m.getXRot(),45,"Old negative look limit becomes downward 45-degree pitch");
        t.setPos(m.position().add(-10,-10,0)); goal.tick(); m.getLookControl().tick(); near(h,m.getYRot(),90,"Body turns toward west");
        m.setTarget(null); m.setDeltaMovement(1,0,0); goal.tick(); m.getLookControl().tick(); near(h,m.getYRot(),-90,"Idle body follows flight"); near(h,m.getXRot(),0,"Idle pitch resets"); t.discard(); cleanup(h,m); h.succeed();
    }
    private static void burst(GameTestHelper h) {
        var m=mob(h,new Vec3(5,80,5)); var t=target(h,new Vec3(5,80,20)); m.setTarget(t); m.setYHeadRot(0); m.setXRot(0);
        var bullets=new ArrayList<Integer>(); var rockets=new ArrayList<Integer>(); var shots=new ArrayList<Projectile>(); int[] tick={0};
        Consumer<EntityJoinLevelEvent> observe=e->{if(e.getEntity() instanceof Projectile p && p.getOwner()==m) { shots.add(p); if(p instanceof Bullet) bullets.add(tick[0]); if(p instanceof RocketProjectile) rockets.add(tick[0]); }}; NeoForge.EVENT_BUS.addListener(observe);
        try {
            var goal=m.attackGoal(); goal.start(); for(tick[0]=1;tick[0]<=102;tick[0]++) goal.tick();
            h.assertValueEqual(bullets,List.of(14,16,18,20,22,80,82,84,86,88),"Two real five-round source bursts"); h.assertValueEqual(rockets,List.of(35,101),"Real rockets on tick 35 of each cycle"); h.assertValueEqual(goal.timer(),-30,"Original rest timer"); h.assertTrue(!m.isAttacking(),"Rest clears synced attack flag");
            for(var shot:shots) { near(h,shot.getY(),m.getEyeY()-.1,"Source muzzle height"); h.assertTrue(shot.getDeltaMovement().length()>1.4 && shot.getDeltaMovement().length()<1.6,"Source 1.5 launch-speed multiplier");
                if(shot instanceof Bullet b) { h.assertValueEqual(b.weapon(),HelicopterWeapons.BULLET,"Dedicated bullet profile"); near(h,b.getX(),m.getX(),"Centered bullet muzzle"); }
                if(shot instanceof RocketProjectile r) { h.assertValueEqual(r.weapon(),HelicopterWeapons.ROCKET,"Dedicated rocket profile"); h.assertTrue(!r.damagesBlocks(),"Original combat rocket cannot damage blocks"); } }
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); shots.forEach(Entity::discard); t.discard(); cleanup(h,m); }
    }
    private static void occluded(GameTestHelper h) {
        var m=mob(h,new Vec3(5,80,5)); var t=target(h,new Vec3(5,80,20)); m.setTarget(t); var goal=m.attackGoal(); goal.start(); for(int i=0;i<13;i++) goal.tick();
        var wall=m.blockPosition().south(6); for(int x=-3;x<=3;x++) for(int y=-1;y<=5;y++) h.getLevel().setBlock(wall.offset(x,y,0),Blocks.STONE.defaultBlockState(),2);
        h.assertTrue(!m.hasLineOfSight(t),"Actual solid wall occludes target"); goal.tick(); h.assertValueEqual(goal.timer(),12,"Hidden target winds warmup backwards");
        var copy=new AttackHelicopter(NpcContent.HELICOPTER.get(),h.getLevel()); NetherGameTests.load(h,copy,NetherGameTests.save(h,m)); copy.setTarget(t); copy.attackGoal().start(); h.assertValueEqual(copy.attackGoal().timer(),12,"Reload resumes partial attack clock");
        copy.attackGoal().tick(); h.assertValueEqual(copy.attackGoal().timer(),11,"Reload also respects the real wall");
        for(int x=-3;x<=3;x++) for(int y=-1;y<=5;y++) h.getLevel().setBlock(wall.offset(x,y,0),Blocks.AIR.defaultBlockState(),2); copy.discard(); t.discard(); cleanup(h,m); h.succeed();
    }
    private static void muzzles(GameTestHelper h) {
        var m=mob(h,new Vec3(5,80,5)); for(int yaw:new int[]{0,90,180,270}) for(int side:new int[]{-1,1}) {
            m.setYHeadRot(yaw); m.setXRot(0); var r=new RocketProjectile(TGContent.ROCKET.get(),h.getLevel()); r.configure(HelicopterWeapons.ROCKET,RocketVariant.DEFAULT,false); r.shootLegacy(m,0,side);
            near(h,r.getX()-m.getX(),Math.cos(Math.toRadians(yaw))*side*2.16,"Rotated launcher side X"); near(h,r.getZ()-m.getZ(),Math.sin(Math.toRadians(yaw))*side*2.16,"Rotated launcher side Z"); r.discard();
        } cleanup(h,m); h.succeed();
    }
    private static void realAi(GameTestHelper h) {
        // Stay outside neighboring players' acquisition band and prevent distance-based despawning from those fixtures.
        var level=h.getLevel(); var center=h.absolutePos(new BlockPos(10,175,10));
        var required=new ArrayList<ChunkPos>(); var pinned=new ArrayList<ChunkPos>();
        for(int x=(center.getX()-48)>>4;x<=(center.getX()+48)>>4;x++) for(int z=(center.getZ()-48)>>4;z<=(center.getZ()+48)>>4;z++) {
            var chunk=new ChunkPos(x,z); required.add(chunk);
            if(level.setChunkForced(x,z,true)) pinned.add(chunk);
            level.getChunk(x,z);
        }
        for(int x=-20;x<=32;x++) for(int z=-20;z<=32;z++) h.getLevel().setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
        var m=new AttackHelicopter(NpcContent.HELICOPTER.get(),level); m.setPersistenceRequired(); m.setNoAi(true); m.setPos(h.absoluteVec(new Vec3(10,200,10)));
        var t=EntityTypes.IRON_GOLEM.create(level,EntitySpawnReason.STRUCTURE); t.setPersistenceRequired(); t.removeFreeWill(); t.setPos(h.absoluteVec(new Vec3(10,200,18)));
        t.setNoGravity(true); t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); t.setHealth(1000); var start=m.position();
        var fired=new ArrayList<Bullet>(); float[] bulletDamage={0};
        Consumer<EntityJoinLevelEvent> shots=e->{ if(e.getEntity() instanceof Bullet b && b.getOwner()==m) fired.add(b); };
        Consumer<LivingDamageEvent.Post> hits=e->{ if(e.getEntity()==t && e.getSource().getDirectEntity() instanceof Bullet b && b.getOwner()==m) bulletDamage[0]+=e.getHealthDamage(); };
        NeoForge.EVENT_BUS.addListener(shots); NeoForge.EVENT_BUS.addListener(hits);
        h.testInfo.addListener(new GameTestListener() {
            private void release() {
                NeoForge.EVENT_BUS.unregister(shots); NeoForge.EVENT_BUS.unregister(hits);
                fired.forEach(Entity::discard); cleanup(h,m); t.discard();
                for(int x=-20;x<=32;x++) for(int z=-20;z<=32;z++) level.setBlock(center.offset(x,0,z),Blocks.AIR.defaultBlockState(),2);
                pinned.forEach(c->level.setChunkForced(c.x(),c.z(),false));
            }
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testPassed(GameTestInfo info,GameTestRunner runner) { release(); }
            @Override public void testFailed(GameTestInfo info,GameTestRunner runner) { release(); }
            @Override public void testAddedForRerun(GameTestInfo original,GameTestInfo copy,GameTestRunner runner) {}
        });
        h.startSequence().thenWaitUntil(()->{
            // A FULL terrain chunk alone does not guarantee that native projectile queries can see the target.
            for(var chunk:required) h.assertTrue(level.areEntitiesActuallyLoadedAndTicking(chunk),"Flight chunk entities ready: "+chunk);
        }).thenExecute(()->{
            h.assertTrue(level.addFreshEntity(m) && level.addFreshEntity(t),"Both native entities spawn");
        }).thenWaitUntil(()->{
            h.assertTrue(level.getEntity(m.getUUID())==m && level.getEntity(t.getUUID())==t,"Both combatants registered before the combat clock");
        }).thenExecute(()->{
            // Keep the first segment straight, with the normal attack and look goals and original dispersion.
            m.setYHeadRot(0); m.getRandom().setSeed(42); m.setTarget(t);
            m.getMoveControl().setWantedPosition(m.getX(),m.getY(),m.getZ()-12,1); m.setNoAi(false);
        }).thenExecuteAfter(30,()->{
            h.assertTrue(!m.isRemoved() && m.position().distanceToSqr(start)>.05,"Registered flight control moves the persistent helicopter");
            h.assertValueEqual(fired.size(),5,"Registered AI emits the entire first five-round burst");
            h.assertTrue(bulletDamage[0]>0 && t.getHealth()<1000,"Owned bullet flight inflicts real health damage: timer="+m.attackGoal().timer()+", targetRegistered="+(level.getEntity(t.getUUID())==t)+", targetTicking="+level.areEntitiesActuallyLoadedAndTicking(t.chunkPosition())+", targetTicks="+t.tickCount);
        }).thenSucceed();
    }
    private static void savedShot(GameTestHelper h,boolean rocket) {
        Projectile p=rocket?new RocketProjectile(TGContent.ROCKET.get(),h.getLevel()):new Bullet(TGContent.BULLET.get(),h.getLevel());
        if(p instanceof RocketProjectile r) { r.configure(HelicopterWeapons.ROCKET,RocketVariant.DEFAULT,false); r.npcDamage(1); } else { ((Bullet)p).configure(HelicopterWeapons.BULLET); ((Bullet)p).npcDamage(1); }
        p.setPos(h.absoluteVec(new Vec3(5,180,5))); p.setDeltaMovement(.2,.1,.05); p.tick(); near(h,p.getDeltaMovement().x,.2*(double).99f,"Original drag");
        Projectile copy=rocket?new RocketProjectile(TGContent.ROCKET.get(),h.getLevel()):new Bullet(TGContent.BULLET.get(),h.getLevel()); NetherGameTests.load(h,copy,NetherGameTests.save(h,p));
        h.assertValueEqual(copy.getDeltaMovement(),p.getDeltaMovement(),"Motion survives save"); h.assertValueEqual(copy instanceof RocketProjectile r?r.weapon():((Bullet)copy).weapon(),rocket?HelicopterWeapons.ROCKET:HelicopterWeapons.BULLET,"NPC-only profile survives reload");
        for(int i=1;i<99;i++) copy.tick(); h.assertTrue(!copy.isRemoved(),"Alive after 99 movements"); copy.tick(); h.assertTrue(copy.isRemoved(),"Exactly 100 movements"); p.discard(); h.succeed();
    }
    private static void water(GameTestHelper h) {
        h.setBlock(5,80,5,Blocks.WATER); var b=new Bullet(TGContent.BULLET.get(),h.getLevel()); b.configure(HelicopterWeapons.BULLET); b.setPos(h.absoluteVec(new Vec3(5.5,80.2,5.5))); b.setDeltaMovement(.1,0,0); b.tick(); h.assertTrue(b.isInWater(),"Native water contact"); near(h,b.getDeltaMovement().x,.1*(double).85f,"Original water drag"); b.discard(); h.succeed();
    }
    private static void owner(GameTestHelper h) {
        var m=mob(h,new Vec3(5,80,5)); var b=new Bullet(TGContent.BULLET.get(),h.getLevel()); b.configure(HelicopterWeapons.BULLET); b.setOwner(m);
        b.setPos(m.position().add(-4,.6,0)); b.setDeltaMovement(8,0,0); var data=NetherGameTests.save(h,b); data.putBoolean("LeftOwner",true); NetherGameTests.load(h,b,data);
        h.runAfterDelay(1,()->{ try { b.tick(); near(h,m.getHealth(),100,"Original shooter exclusion holds even after leaving its hull"); h.assertTrue(!b.isRemoved(),"Owner hull cannot consume its projectile"); h.succeed(); } finally { b.discard(); cleanup(h,m); } });
    }
    private static void blast(GameTestHelper h,boolean wall) {
        var l=h.getLevel(); var center=new Vec3(wall?3400160:3400000,140,-3400000); load(l,BlockPos.containing(center),64);
        var r=new RocketProjectile(TGContent.ROCKET.get(),l); r.configure(HelicopterWeapons.ROCKET,RocketVariant.DEFAULT,false); r.npcDamage(1); r.setPos(center);
        var distances=new double[]{6,30,30.1,35,40,41}; var victims=new ArrayList<LivingEntity>();
        for(double distance:distances) { var t=EntityTypes.COW.create(l,EntitySpawnReason.COMMAND); t.removeFreeWill(); t.setNoGravity(true); t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); t.setHealth(1000); t.setPos(center.add(distance,-t.getEyeHeight(),0)); l.addFreshEntity(t); victims.add(t); }
        var block=BlockPos.containing(center).east(3); if(wall) for(int y=-3;y<=3;y++) for(int z=-3;z<=3;z++) l.setBlock(block.offset(0,y,z),Blocks.OAK_PLANKS.defaultBlockState(),2);
        h.runAfterDelay(1,()->{ try { h.assertTrue(r.explode(),"Real helicopter rocket explosion");
            for(int i=0;i<distances.length;i++) near(h,1000-victims.get(i).getHealth(),wall?0:RocketVariant.DEFAULT.blastDamage(HelicopterWeapons.ROCKET.stats(),distances[i]),"Original blast band at "+distances[i]);
            if(wall) h.assertTrue(l.getBlockState(block).is(Blocks.OAK_PLANKS),"Rocket never destroys blocking terrain"); h.succeed();
        } finally { victims.forEach(Entity::discard); if(wall) for(int y=-3;y<=3;y++) for(int z=-3;z<=3;z++) l.setBlock(block.offset(0,y,z),Blocks.AIR.defaultBlockState(),2); } });
    }
    private static void cancel(GameTestHelper h) {
        var t=target(h,new Vec3(8,80,5)); var b=new Bullet(TGContent.BULLET.get(),h.getLevel()); b.configure(HelicopterWeapons.BULLET); b.setPos(h.absoluteVec(new Vec3(4,80.6,5))); b.setDeltaMovement(6,0,0);
        Consumer<ProjectileImpactEvent> veto=e->{if(e.getProjectile()==b)e.setCanceled(true);}; NeoForge.EVENT_BUS.addListener(veto);
        h.runAfterDelay(1,()->{ try { b.tick(); near(h,t.getHealth(),1000,"Vetoed impact causes no damage"); h.assertTrue(!b.isRemoved(),"Vetoed projectile continues flying"); h.succeed(); } finally { NeoForge.EVENT_BUS.unregister(veto); b.discard(); t.discard(); } });
    }
    private static void factor(GameTestHelper h) {
        var m=mob(h,new Vec3(3,80,3)); var t=target(h,new Vec3(8,80,5));
        h.runAfterDelay(1,()->{ double old=NpcConfig.DAMAGE_FACTOR.get(); var difficulty=h.getLevel().getDifficulty(); try {
            for(var d:List.of(Difficulty.EASY,Difficulty.NORMAL,Difficulty.HARD)) { h.getLevel().getServer().setDifficulty(d,true);
                for(double f:new double[]{0,2}) { NpcConfig.DAMAGE_FACTOR.set(f); t.invulnerableTime=0; float health=t.getHealth();
                    var b=new Bullet(TGContent.BULLET.get(),h.getLevel()); b.configure(HelicopterWeapons.BULLET); b.npcDamage(1); b.setOwner(m); b.setPos(t.position().add(-1, .6,0)); b.setDeltaMovement(2,0,0); b.tick();
                    near(h,health-t.getHealth(),12*f,"Live NPC multiplier without GenericNPC gun difficulty penalty"); b.discard(); } }
            h.succeed();
        } finally { NpcConfig.DAMAGE_FACTOR.set(old); h.getLevel().getServer().setDifficulty(difficulty,true); cleanup(h,m); t.discard(); } });
    }
    private static LootTable table(GameTestHelper h) { return h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/attackhelicopter"))); }
    private static void loot(GameTestHelper h) {
        var m=mob(h,new Vec3(5,80,5)); var p=WeaponGameTests.player(h); var sword=new ItemStack(Items.DIAMOND_SWORD); sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),3); p.setItemInHand(InteractionHand.MAIN_HAND,sword);
        var max=new LegacyRandomSource(1) { @Override public int nextInt(int bound) { return bound-1; } @Override public float nextFloat() { return 1; } };
        var drops=table(h).getRandomItems(ZombieSoldierGameTests.params(h,m,p),max); h.assertValueEqual(drops.size(),4,"All four unconditional source loot pools");
        var items=List.of(TGContent.MATERIALS.get("cyberneticparts").get(),TGContent.AMMO.get("riflerounds").get(),TGContent.AMMO.get("rocket").get(),Items.IRON_BLOCK); int[] counts={10,40,20,10};
        for(int i=0;i<4;i++) { h.assertTrue(drops.get(i).is(items.get(i)),"Original flattened loot item"); h.assertValueEqual(drops.get(i).getCount(),counts[i],"Original upper count plus Looting III"); } cleanup(h,m); h.succeed();
    }
    private static void death(GameTestHelper h) {
        var l=h.getLevel(); var m=mob(h,new Vec3(5,80,5)); var p=WeaponGameTests.player(h); var expected=table(h).getRandomItems(ZombieSoldierGameTests.params(h,m,p),17); var tag=NetherGameTests.save(h,m); tag.putLong("DeathLootTableSeed",17); NetherGameTests.load(h,m,tag);
        var drops=new ArrayList<ItemEntity>(); var xp=new ArrayList<ExperienceOrb>(); var area=m.getBoundingBox().inflate(4);
        Consumer<EntityJoinLevelEvent> observe=e->{if(e.getLevel()==l && area.contains(e.getEntity().position())) { if(e.getEntity() instanceof ItemEntity item)drops.add(item); if(e.getEntity() instanceof ExperienceOrb orb)xp.add(orb); }}; NeoForge.EVENT_BUS.addListener(observe);
        AttackHelicopter copy=null;
        try {
            m.hurtServer(l,l.damageSources().playerAttack(p),10000); h.assertTrue(!m.isAlive() && !m.isRemoved(),"Real death leaves the animated corpse"); h.assertValueEqual(drops.size(),4,"Death creates original four loot stacks once");
            for(var s:expected) h.assertTrue(drops.stream().anyMatch(e->ItemStack.matches(s,e.getItem())),"Actual seeded death loot matches source table"); h.assertTrue(xp.isEmpty(),"XP is delayed until animation finishes");
            for(int i=0;i<47;i++) m.tick(); h.assertValueEqual(m.deathTime,47,"Source corpse survives past vanilla twenty ticks");
            var visual=new AttackHelicopter(NpcContent.HELICOPTER.get(),l); visual.getEntityData().assignValues(m.getEntityData().getNonDefaultValues()); h.assertValueEqual(visual.deathTime,47,"New tracking client receives the current death-animation frame"); visual.discard();
            var saved=NetherGameTests.save(h,m); h.assertValueEqual(saved.getIntOr("experience_credit_ticks",-1),53,"Remaining player credit is saved with the corpse"); m.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            copy=new AttackHelicopter(NpcContent.HELICOPTER.get(),l); NetherGameTests.load(h,copy,saved); copy.removeFreeWill();
            for(int i=47;i<99;i++) copy.tick(); h.assertTrue(!copy.isRemoved() && xp.isEmpty(),"Saved corpse waits through tick 99"); copy.tick(); h.assertTrue(copy.isRemoved(),"Saved corpse finishes on tick 100");
            h.assertValueEqual(experienceValue(h,xp),5,"Five XP emitted exactly once at completion"); h.assertValueEqual(drops.size(),4,"Saved corpse cannot reroll loot"); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); drops.forEach(Entity::discard); xp.forEach(Entity::discard); m.discard(); if(copy!=null)copy.discard(); }
    }
    private static void experience(GameTestHelper h,boolean enableAfterDeath) {
        var l=h.getLevel(); var m=mob(h,new Vec3(5,80,5)); var p=WeaponGameTests.player(h); var area=m.getBoundingBox().inflate(12);
        var drops=new ArrayList<ItemEntity>(); var xp=new ArrayList<ExperienceOrb>(); boolean before=l.getGameRules().get(GameRules.MOB_DROPS);
        Consumer<EntityJoinLevelEvent> observe=e->{if(e.getLevel()==l && area.contains(e.getEntity().position())) { if(e.getEntity() instanceof ItemEntity item)drops.add(item); if(e.getEntity() instanceof ExperienceOrb orb)xp.add(orb); }}; NeoForge.EVENT_BUS.addListener(observe);
        try {
            l.getGameRules().set(GameRules.MOB_DROPS,!enableAfterDeath,l.getServer());
            if(enableAfterDeath) {
                m.hurtServer(l,l.damageSources().playerAttack(p),10000); h.assertTrue(drops.isEmpty(),"Loot disabled at actual death");
                l.getGameRules().set(GameRules.MOB_DROPS,true,l.getServer());
            } else {
                m.hurtServer(l,l.damageSources().playerAttack(p),1); m.setDeltaMovement(Vec3.ZERO);
                for(int i=0;i<80;i++) m.tick(); m.hurtServer(l,l.damageSources().genericKill(),10000);
                h.assertValueEqual(NetherGameTests.save(h,m).getIntOr("experience_credit_ticks",-1),20,"Player credit still exists at death");
            }
            h.assertTrue(!m.isAlive() && xp.isEmpty(),"No experience before animation completes");
            for(int i=0;i<99;i++) m.tick(); h.assertTrue(xp.isEmpty(),"No early experience"); m.tick(); h.assertTrue(m.isRemoved(),"Corpse removed at tick 100");
            h.assertValueEqual(experienceValue(h,xp),enableAfterDeath?5:0,"Experience uses current gamerule and unexpired player credit at tick 100"); h.succeed();
        } finally { l.getGameRules().set(GameRules.MOB_DROPS,before,l.getServer()); NeoForge.EVENT_BUS.unregister(observe); drops.forEach(Entity::discard); xp.forEach(Entity::discard); m.discard(); }
    }
    private static void spawner(GameTestHelper h) {
        var l=h.getLevel(); var pos=h.absolutePos(new BlockPos(5,40,5)); load(l,pos,24); l.setBlock(pos,NpcSpawnerContent.SOLDIER_BLOCK.get().defaultBlockState(),2);
        var b=(NpcSpawnerBlockEntity)l.getBlockEntity(pos); b.configure(1,1,200,0,64,List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("attackhelicopter"),1)),ItemStack.EMPTY);
        var spawned=new ArrayList<AttackHelicopter>(); Consumer<EntityJoinLevelEvent> observe=e->{if(e.getLevel()==l && e.getEntity() instanceof AttackHelicopter m && m.spawnerLink()!=null && m.spawnerLink().origin().pos().equals(pos)) { m.removeFreeWill(); spawned.add(m); }}; NeoForge.EVENT_BUS.addListener(observe);
        try {
            for(int i=0;i<199;i++) NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b); h.assertTrue(spawned.isEmpty(),"Initial 200-tick delay"); NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b); h.assertValueEqual(spawned.size(),1,"Actual helicopter from camp's SOLDIER_SPAWN post");
            var m=spawned.getFirst(); near(h,m.getY(),pos.getY()+65,"Source height offset plus post's one-block spawn lift"); h.assertValueEqual(m.spawnerLink(),b.link(),"Native owner attached");
            var data=NetherGameTests.save(h,m); var block=b.saveWithFullMetadata(l.registryAccess()); m.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); l.removeBlockEntity(pos); b=(NpcSpawnerBlockEntity)BlockEntity.loadStatic(pos,l.getBlockState(pos),block,l.registryAccess()); l.setBlockEntity(b);
            for(int i=0;i<600;i++) NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b); h.assertValueEqual(spawned.size(),1,"Unloaded reservation prevents duplicate spawns");
            var restored=new AttackHelicopter(NpcContent.HELICOPTER.get(),l); NetherGameTests.load(h,restored,data); restored.removeFreeWill(); for(int i=0;i<21;i++) restored.tick();
            h.assertValueEqual(restored.spawnerLink(),b.link(),"Saved owner finds restored post"); h.assertValueEqual(b.activeIds(),Set.of(restored.getUUID()),"Restored reservation remains singular");
            restored.hurtServer(l,l.damageSources().genericKill(),10000); h.assertValueEqual(b.remaining(),0,"One real death spends the camp quota"); NpcSpawnerBlockEntity.serverTick(l,pos,b.getBlockState(),b); h.assertTrue(l.getBlockState(pos).isAir(),"Final death removes post before corpse animation");
            for(int i=0;i<100;i++) restored.tick(); h.assertTrue(restored.isRemoved(),"Linked corpse finishes normally"); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observe); spawned.forEach(Entity::discard); l.setBlock(pos,Blocks.AIR.defaultBlockState(),2); }
    }
    private static void peaceful(GameTestHelper h) {
        var l=h.getLevel(); var m=mob(h,new Vec3(5,80,5)); var pos=m.blockPosition().below(20); l.setBlock(pos,NpcSpawnerContent.SOLDIER_BLOCK.get().defaultBlockState(),2); var b=(NpcSpawnerBlockEntity)l.getBlockEntity(pos); b.configure(1,1,200,0,64,List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("attackhelicopter"),1)),ItemStack.EMPTY); m.bindSpawner(b.link()); b.relink(m); var before=l.getDifficulty();
        try { l.getServer().setDifficulty(Difficulty.PEACEFUL,true); m.tick(); h.assertTrue(m.isRemoved(),"Peaceful removes the hostile flyer"); h.assertValueEqual(b.remaining(),1,"Removal is not a kill"); h.assertValueEqual(b.activeCount(),0,"Reservation released"); h.succeed(); }
        finally { l.getServer().setDifficulty(before,true); m.discard(); l.setBlock(pos,Blocks.AIR.defaultBlockState(),2); }
    }
    private HelicopterGameTests() {}
}
