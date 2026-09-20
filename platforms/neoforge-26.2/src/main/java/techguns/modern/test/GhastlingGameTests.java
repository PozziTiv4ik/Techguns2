package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.npc.*;
import techguns.modern.npc.spawner.*;

final class GhastlingGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("ghastling_source_attributes_and_living_effects",()->GhastlingGameTests::attributes);
        r.register("ghastling_zero_typed_armor_despite_attribute",()->GhastlingGameTests::armor);
        r.register("ghastling_native_fire_immunity",()->GhastlingGameTests::fireImmunity);
        r.register("ghastling_egg_save_and_charge_sync",()->GhastlingGameTests::save);
        r.register("ghastling_ground_gravity",()->GhastlingGameTests::ground);
        r.register("ghastling_registered_burst_three_real_projectiles",()->h->burst(h,false));
        r.register("ghastling_source_goal_does_not_require_visibility",()->h->burst(h,true));
        r.register("ghastling_source_melee_interval",()->GhastlingGameTests::melee);
        r.register("ghastling_real_server_ai_hits_target",()->GhastlingGameTests::realAi);
        r.register("ghastling_loot_and_looting",()->GhastlingGameTests::loot);
        r.register("ghastling_spawner_reload_and_death",()->GhastlingGameTests::linkedSave);
        r.register("ghastling_projectile_damage_and_three_second_ignition",()->h->impact(h,"plain",6));
        r.register("ghastling_projectile_ordinary_fire_armor",()->h->impact(h,"armor",4.8f));
        r.register("ghastling_projectile_fire_resistance_is_not_immunity",()->h->impact(h,"resistance",6));
        r.register("ghastling_projectile_witch_magic_resistance",()->h->impact(h,"witch",.9f));
        r.register("ghastling_projectile_typed_zero_armor",()->h->impact(h,"ghastling",6));
        r.register("ghastling_projectile_wall_and_no_block_fire",()->GhastlingGameTests::wall);
        r.register("ghastling_projectile_cancelled_hit_no_ignition",()->h->cancel(h,true));
        r.register("ghastling_projectile_cancelled_damage_no_ignition",()->h->cancel(h,false));
        r.register("ghastling_projectile_saved_flight_and_lifetime",()->GhastlingGameTests::flight);
        r.register("ghastling_projectile_water_drag",()->GhastlingGameTests::water);
        r.register("ghastling_projectile_never_hits_its_shooter",()->GhastlingGameTests::owner);
        r.register("ghastling_projectile_npc_factor_without_gun_difficulty_penalty",()->GhastlingGameTests::factor);
    }
    static Ghastling mob(GameTestHelper h,Vec3 at) {
        var g=new Ghastling(NpcContent.GHASTLING.get(),h.getLevel()); g.setPos(h.absoluteVec(at)); g.removeFreeWill(); g.setNoGravity(true); h.getLevel().addFreshEntity(g); return g;
    }
    static LivingEntity target(GameTestHelper h,Vec3 at) {
        var t=h.spawnWithNoFreeWill(EntityTypes.COW,at); t.setNoGravity(true); t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); t.setHealth(1000); return t;
    }
    private static void near(GameTestHelper h,double actual,double expected,String message) { h.assertTrue(Math.abs(actual-expected)<.001,message+": "+actual+" vs "+expected); }
    private static void attributes(GameTestHelper h) {
        var g=mob(h,new Vec3(3,2,3)); near(h,g.getMaxHealth(),20,"Original health"); near(h,g.getAttributeValue(Attributes.MOVEMENT_SPEED),.7,"Inherited EntityMob speed"); near(h,g.getAttributeValue(Attributes.ATTACK_DAMAGE),2,"Inherited melee damage"); near(h,g.getAttributeValue(Attributes.FOLLOW_RANGE),64,"Follow range");
        near(h,g.getBbWidth(),1,"Width"); near(h,g.getBbHeight(),2.1,"Height"); near(h,g.getEyeHeight(),1.5,"Eye height"); near(h,g.getSoundVolume(),10,"Original ghast volume");
        h.assertTrue(g.getMainHandItem().isEmpty() && !ArmedNpc.class.isInstance(g),"No GenericNPC faction or invented handheld gun");
        h.assertTrue(g.canBeAffected(new MobEffectInstance(MobEffects.POISON,100)),"Living entity can be poisoned"); h.assertTrue(!g.isInvertedHealAndHarm(),"No undead healing inversion");
        h.assertTrue(g.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation,"Original ground pathing");
        near(h,g.getPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.LAVA),8,"Source lava preference");
        g.discard(); h.succeed();
    }
    private static void armor(GameTestHelper h) {
        var g=mob(h,new Vec3(3,2,3)); near(h,g.getAttributeValue(Attributes.ARMOR),10,"Armor attribute retained");
        for(var kind:DamageKind.values()) near(h,g.armorAgainst(kind),0,"Source custom armor returns zero for "+kind);
        g.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(WeaponGameTests.player(h)),10); near(h,g.getHealth(),10,"Zero source armor takes full physical hit"); g.discard(); h.succeed();
    }
    private static void fireImmunity(GameTestHelper h) {
        var g=mob(h,new Vec3(3,2,3)); h.assertTrue(g.fireImmune(),"Source fire-immune flag");
        g.hurtServer(h.getLevel(),h.getLevel().damageSources().inFire(),10); g.hurtServer(h.getLevel(),h.getLevel().damageSources().lava(),10); near(h,g.getHealth(),20,"Ordinary fire and lava immunity"); g.discard(); h.succeed();
    }
    private static void save(GameTestHelper h) {
        var p=WeaponGameTests.player(h); p.setItemInHand(InteractionHand.MAIN_HAND,NpcContent.GHASTLING_EGG.toStack()); h.setBlock(4,1,4,Blocks.STONE); h.useBlock(new BlockPos(4,1,4),p);
        var area=new AABB(h.absolutePos(new BlockPos(3,1,3))).inflate(4); var list=h.getLevel().getEntitiesOfClass(Ghastling.class,area); h.assertValueEqual(list.size(),1,"Spawn egg creates real registered mob"); var g=list.getFirst();
        g.setAttacking(true); h.assertTrue(g.getEntityData().getNonDefaultValues()!=null,"Attack expression is synchronized data");
        g.setHealth(13); var data=NetherGameTests.save(h,g); var restored=new Ghastling(NpcContent.GHASTLING.get(),h.getLevel()); NetherGameTests.load(h,restored,data);
        near(h,restored.getHealth(),13,"Native health save"); h.assertTrue(!restored.isAttacking(),"Transient source attack state resets on reload"); h.assertTrue(restored.getMainHandItem().isEmpty(),"No gun invented by reload");
        g.discard(); restored.discard(); h.succeed();
    }
    private static void ground(GameTestHelper h) {
        for(int x=1;x<=5;x++) for(int z=1;z<=5;z++) h.setBlock(x,1,z,Blocks.STONE);
        var g=mob(h,new Vec3(3,6,3)); g.setNoGravity(false); double before=g.getY();
        h.runAfterDelay(15,()->{ h.assertTrue(g.getY()<before-2,"Ghast-shaped mob falls normally, not a flying vanilla ghast"); g.discard(); h.succeed(); });
    }
    private static void burst(GameTestHelper h,boolean wall) {
        var g=mob(h,new Vec3(3,2,2)); var t=target(h,new Vec3(3,2,12)); g.setTarget(t); g.setYHeadRot(0); g.setXRot(0);
        if(wall) for(int x=1;x<=5;x++) for(int y=1;y<=5;y++) h.setBlock(x,y,6,Blocks.STONE);
        var shots=new ArrayList<AlienBlasterProjectile>(); var times=new ArrayList<Integer>(); int[] now={0};
        Consumer<EntityJoinLevelEvent> observe=e->{ if(e.getEntity() instanceof AlienBlasterProjectile shot && shot.getOwner()==g) { shots.add(shot); times.add(now[0]); } }; NeoForge.EVENT_BUS.addListener(observe);
        var goal=new GhastlingAttackGoal(g); h.assertTrue(goal.canUse(),"Registered source attack can start"); goal.start();
        try { for(int tick=0;tick<=48;tick++) { now[0]=tick; goal.tick(); if(tick==0) h.assertTrue(g.isAttacking(),"Charge expression during warmup"); } } finally { NeoForge.EVENT_BUS.unregister(observe); }
        h.assertValueEqual(times,List.of(30,36,42),"Three actual entities at original burst ticks, including behind walls"); h.assertTrue(!g.isAttacking(),"Expression clears in rest");
        for(var s:shots) { near(h,s.getX(),g.getX(),"Centered muzzle"); near(h,s.getY(),g.getEyeY()-.1,"Source height offset"); h.assertTrue(s.getDeltaMovement().length()>2.1 && s.getDeltaMovement().length()<2.4,"Original unnormalized 1.5 speed multiplier"); s.discard(); }
        goal.stop(); g.discard(); t.discard(); h.succeed();
    }
    private static void melee(GameTestHelper h) {
        var g=mob(h,new Vec3(3,2,3)); var t=target(h,new Vec3(3,2,4)); g.setTarget(t); var goal=new GhastlingAttackGoal(g); goal.start();
        for(int tick=0;tick<=20;tick++) { t.invulnerableTime=0; goal.tick(); }
        near(h,t.getHealth(),996,"Two original vanilla melee hits over twenty ticks"); g.discard(); t.discard(); h.succeed();
    }
    private static void realAi(GameTestHelper h) {
        var g=new Ghastling(NpcContent.GHASTLING.get(),h.getLevel()); g.setPos(h.absoluteVec(new Vec3(3,2,2))); g.setNoGravity(true); g.setYHeadRot(0); h.getLevel().addFreshEntity(g);
        var t=target(h,new Vec3(3,2,10)); g.setTarget(t);
        h.runAfterDelay(45,()->{ h.assertTrue(t.getHealth()<1000,"Goal selector, aiming and moving projectile deal real damage"); h.getLevel().getEntitiesOfClass(AlienBlasterProjectile.class,g.getBoundingBox().inflate(150),p->p.getOwner()==g).forEach(Entity::discard); g.discard(); t.discard(); h.succeed(); });
    }
    private static void loot(GameTestHelper h) {
        var g=mob(h,new Vec3(3,2,3)); var p=WeaponGameTests.player(h);
        var table=h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/ghastling")));
        var sword=new ItemStack(Items.DIAMOND_SWORD); sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),3); p.setItemInHand(InteractionHand.MAIN_HAND,sword);
        var params=new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.THIS_ENTITY,g).withParameter(LootContextParams.ORIGIN,g.position()).withParameter(LootContextParams.DAMAGE_SOURCE,h.getLevel().damageSources().playerAttack(p)).withParameter(LootContextParams.ATTACKING_ENTITY,p).withParameter(LootContextParams.LAST_DAMAGE_PLAYER,p).create(LootContextParamSets.ENTITY);
        var random=new LegacyRandomSource(1) { @Override public int nextInt(int bound) { return bound-1; } @Override public float nextFloat() { return 1; } };
        var drops=table.getRandomItems(params,random); h.assertValueEqual(drops.size(),2,"Both unconditional original loot pools");
        h.assertTrue(drops.stream().anyMatch(s->s.is(Items.GHAST_TEAR) && s.getCount()==4),"One tear plus original Looting III increment"); h.assertTrue(drops.stream().anyMatch(s->s.is(Items.GUNPOWDER) && s.getCount()==5),"Two powder plus original Looting III increment"); g.discard(); h.succeed();
    }
    private static void linkedSave(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(5,2,5)); h.getLevel().setBlock(pos,NpcSpawnerContent.BLOCK.get().defaultBlockState(),2); var b=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(pos);
        b.configure(3,2,1,0,0,List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("ghastling"),1)),ItemStack.EMPTY); NpcSpawnerBlockEntity.serverTick(h.getLevel(),pos,b.getBlockState(),b);
        var g=(Ghastling)h.getLevel().getEntity(b.activeIds().iterator().next()); var saved=NetherGameTests.save(h,g); var id=g.getUUID(); g.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.assertValueEqual(b.activeCount(),1,"Unload retains slot"); var restored=new Ghastling(NpcContent.GHASTLING.get(),h.getLevel()); NetherGameTests.load(h,restored,saved); restored.removeFreeWill();
        h.assertValueEqual(restored.spawnerLink(),b.link(),"Unarmed NPC origin survives native save"); restored.spawnerLink().relink(restored); h.assertValueEqual(b.activeIds(),Set.of(id),"Reload does not duplicate reservation");
        restored.hurtServer(h.getLevel(),h.getLevel().damageSources().genericKill(),1000); h.assertValueEqual(b.remaining(),2,"Restored death charges one original life"); h.assertValueEqual(b.activeCount(),0,"Slot released"); h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2); h.succeed();
    }
    private static AlienBlasterProjectile shot(GameTestHelper h,Vec3 at,Vec3 velocity) {
        var s=new AlienBlasterProjectile(TGContent.ALIEN_BLAST.get(),h.getLevel()); s.setPos(h.absoluteVec(at)); s.setDeltaMovement(velocity); h.getLevel().addFreshEntity(s); return s;
    }
    private static void impact(GameTestHelper h,String mode,float amount) {
        LivingEntity t=mode.equals("ghastling")?mob(h,new Vec3(5,2,5)):mode.equals("witch")?h.spawnWithNoFreeWill(EntityTypes.WITCH,new Vec3(5,2,5)):target(h,new Vec3(5,2,5)); t.setNoGravity(true);
        if(mode.equals("armor")) t.getAttribute(Attributes.ARMOR).setBaseValue(10);
        if(mode.equals("resistance")) t.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,100));
        float before=t.getHealth(); var s=shot(h,new Vec3(4.1,2.9,5),new Vec3(1,0,0)); s.tick(); near(h,before-t.getHealth(),amount,"Original FIRE typed damage");
        if(mode.equals("plain")) h.assertTrue(t.getRemainingFireTicks()>=59 && t.getRemainingFireTicks()<=60,"Successful impact ignites for three seconds");
        h.assertTrue(s.isRemoved(),"Projectile expires on entity impact"); t.discard(); h.succeed();
    }
    private static void wall(GameTestHelper h) {
        var t=target(h,new Vec3(8,2,4)); h.setBlock(5,2,4,Blocks.OAK_PLANKS); var s=shot(h,new Vec3(3,2.5,4.5),new Vec3(8,0,0)); s.tick();
        near(h,t.getHealth(),1000,"Wall stops projectile"); h.assertBlockPresent(Blocks.OAK_PLANKS,new BlockPos(5,2,4)); h.assertTrue(s.isRemoved(),"Wall consumes projectile");
        h.assertBlockNotPresent(Blocks.FIRE,new BlockPos(4,2,4)); t.discard(); h.succeed();
    }
    private static void cancel(GameTestHelper h,boolean impact) {
        var t=target(h,new Vec3(5,2,5)); var s=shot(h,new Vec3(3,2.9,5),new Vec3(5,0,0));
        Consumer<ProjectileImpactEvent> hit=e->{ if(e.getProjectile()==s) e.setCanceled(true); }; Consumer<LivingIncomingDamageEvent> damage=e->{ if(e.getSource().getDirectEntity()==s) e.setCanceled(true); };
        if(impact) NeoForge.EVENT_BUS.addListener(hit); else NeoForge.EVENT_BUS.addListener(damage);
        try { s.tick(); } finally { NeoForge.EVENT_BUS.unregister(impact?hit:damage); }
        near(h,t.getHealth(),1000,"Cancelled event causes no damage"); h.assertTrue(!t.isOnFire(),"Cancelled damage cannot ignite"); h.assertTrue(s.isRemoved()!=impact,"Impact veto continues flight; damage veto still hits"); s.discard(); t.discard(); h.succeed();
    }
    private static void flight(GameTestHelper h) {
        var s=shot(h,new Vec3(4,80,4),new Vec3(0,1,0)); s.tick(); near(h,s.getDeltaMovement().y,(double).99f,"Air drag"); var saved=NetherGameTests.save(h,s); s.discard();
        var restored=new AlienBlasterProjectile(TGContent.ALIEN_BLAST.get(),h.getLevel()); NetherGameTests.load(h,restored,saved); for(int n=1;n<199;n++) restored.tick(); h.assertTrue(!restored.isRemoved(),"Saved projectile lives through movement 199"); restored.tick(); h.assertTrue(restored.isRemoved(),"Exactly 200 movements");
        saved.putInt("age",200); var expired=new AlienBlasterProjectile(TGContent.ALIEN_BLAST.get(),h.getLevel()); NetherGameTests.load(h,expired,saved); h.assertTrue(expired.isRemoved(),"Expired saves cannot resurrect shots"); h.succeed();
    }
    private static void water(GameTestHelper h) {
        h.setBlock(4,2,4,Blocks.WATER); var s=shot(h,new Vec3(4.5,2.2,4.5),new Vec3(.1,0,0)); s.tick(); h.assertTrue(s.isInWater(),"Water fixture"); near(h,s.getDeltaMovement().x,.1*(double).85f,"Source water drag"); s.discard(); h.succeed();
    }
    private static void owner(GameTestHelper h) {
        var t=target(h,new Vec3(5,2,5)); var s=shot(h,new Vec3(3,2.9,5),new Vec3(5,0,0)); s.setOwner(t);
        var saved=NetherGameTests.save(h,s); saved.putBoolean("LeftOwner",true); NetherGameTests.load(h,s,saved); s.tick();
        near(h,t.getHealth(),1000,"Original shooter remains excluded even after leaving its bounding box"); h.assertTrue(!t.isOnFire() && !s.isRemoved(),"No self-ignition or owner collision"); s.discard(); t.discard(); h.succeed();
    }
    private static void factor(GameTestHelper h) {
        var t=target(h,new Vec3(5,2,5)); var g=mob(h,new Vec3(2,2,2)); double old=NpcConfig.DAMAGE_FACTOR.get(); var difficulty=h.getLevel().getDifficulty();
        try { for(var d:List.of(Difficulty.EASY,Difficulty.NORMAL,Difficulty.HARD)) { h.getLevel().getServer().setDifficulty(d,true);
            for(double factor:new double[]{0,2}) { NpcConfig.DAMAGE_FACTOR.set(factor); t.clearFire(); float before=t.getHealth(); var s=shot(h,new Vec3(4.1,2.9,5),new Vec3(1,0,0)); s.setOwner(g); s.tick(); near(h,before-t.getHealth(),6*factor,"Direct Ghastling profile has no GenericNPC gun difficulty penalty"); if(factor==0) h.assertTrue(!t.isOnFire(),"Zero NPC damage does not ignite"); }
        } } finally { NpcConfig.DAMAGE_FACTOR.set(old); h.getLevel().getServer().setDifficulty(difficulty,true); g.discard(); t.discard(); }
        h.succeed();
    }
    private GhastlingGameTests() {}
}
