package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.DamageKind;
import techguns.modern.TGContent;
import techguns.modern.npc.*;
import techguns.modern.npc.spawner.*;

final class AlienBugGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("alienbug_attributes_and_spider_physiology",()->AlienBugGameTests::attributes);
        r.register("alienbug_egg_and_transient_bite_save",()->AlienBugGameTests::save);
        r.register("alienbug_typed_armor_and_fire",()->AlienBugGameTests::armor);
        r.register("alienbug_source_melee_range_and_cooldown",()->AlienBugGameTests::melee);
        r.register("alienbug_daylight_golem_target",()->AlienBugGameTests::daylight);
        r.register("alienbug_real_goal_selector_bites",()->AlienBugGameTests::realAi);
        r.register("alienbug_no_invented_spider_loot",()->AlienBugGameTests::loot);
        r.register("alienbug_spawner_reload_and_death",()->AlienBugGameTests::linkedSave);
        r.register("alienbug_spawner_discard_and_dimension_departure",()->AlienBugGameTests::depart);
        r.register("alienbug_shared_faction_is_not_gun_ai",()->AlienBugGameTests::faction);
        r.register("alienbug_spawner_keeps_finalized_jockey",()->AlienBugGameTests::jockey);
    }
    private static AlienBug mob(GameTestHelper h,Vec3 pos) {
        var g=new AlienBug(NpcContent.ALIEN_BUG.get(),h.getLevel()); g.setPos(h.absoluteVec(pos)); g.removeFreeWill(); g.setNoGravity(true); h.getLevel().addFreshEntity(g); return g;
    }
    private static void near(GameTestHelper h,double a,double b,String message) { h.assertTrue(Math.abs(a-b)<.001,message+": "+a+" vs "+b); }
    private static void attributes(GameTestHelper h) {
        var g=mob(h,new Vec3(3,2,3)); near(h,g.getMaxHealth(),20,"Health"); near(h,g.getAttributeValue(Attributes.MOVEMENT_SPEED),1,"Speed"); near(h,g.getAttributeValue(Attributes.ATTACK_DAMAGE),4,"Melee");
        near(h,g.getBbWidth(),1.1,"Width"); near(h,g.getBbHeight(),1.2,"Height"); near(h,g.getEyeHeight(),.65,"Inherited spider eye height");
        h.assertTrue(g.is(EntityTypeTags.ARTHROPOD),"Bane of Arthropods type"); h.assertTrue(!g.canBeAffected(new MobEffectInstance(MobEffects.POISON,100)),"Inherited poison immunity");
        h.assertTrue(g.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.WallClimberNavigation,"Real spider wall navigation");
        g.setClimbing(true); h.assertTrue(g.onClimbable() && g.getEntityData().getNonDefaultValues()!=null,"Wall state is synchronized");
        h.assertTrue(!g.fireImmune() && !g.isInvertedHealAndHarm(),"Living non-fireproof arthropod"); g.discard(); h.succeed();
    }
    private static void save(GameTestHelper h) {
        var p=WeaponGameTests.player(h); p.setItemInHand(InteractionHand.MAIN_HAND,NpcContent.BUG_EGG.toStack()); h.setBlock(4,1,4,Blocks.STONE); h.useBlock(new BlockPos(4,1,4),p);
        var mobs=h.getLevel().getEntitiesOfClass(AlienBug.class,new AABB(h.absolutePos(new BlockPos(4,2,4))).inflate(4)); h.assertValueEqual(mobs.size(),1,"Egg creates registered AlienBug"); var g=mobs.getFirst();
        g.handleEntityEvent((byte)4); h.assertValueEqual(g.attackTimer(),10,"Client bite event"); g.setHealth(13); var restored=new AlienBug(NpcContent.ALIEN_BUG.get(),h.getLevel()); NetherGameTests.load(h,restored,NetherGameTests.save(h,g));
        near(h,restored.getHealth(),13,"Health persists"); h.assertValueEqual(restored.attackTimer(),0,"Transient bite is not persisted"); g.discard(); restored.discard(); h.succeed();
    }
    private static void armor(GameTestHelper h) {
        var g=mob(h,new Vec3(3,2,3));
        for(var kind:DamageKind.values()) { float expected=switch(kind) { case PHYSICAL,PROJECTILE->10; case ENERGY,EXPLOSION,ICE,LIGHTNING->5; case POISON,RADIATION->20; default->0; }; near(h,g.armorAgainst(kind),expected,"Typed armor "+kind); }
        g.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(WeaponGameTests.player(h)),10); near(h,g.getHealth(),14,"Original ten armor gives 40 percent physical reduction");
        g.invulnerableTime=0; g.hurtServer(h.getLevel(),h.getLevel().damageSources().inFire(),4); near(h,g.getHealth(),10,"No fire resistance"); g.discard(); h.succeed();
    }
    private static void melee(GameTestHelper h) {
        var g=mob(h,new Vec3(3,2,3)); var t=GhastlingGameTests.target(h,new Vec3(5,2,3)); g.setTarget(t);
        h.assertTrue(g.isWithinMeleeAttackRange(t),"Two-block bite within original sqrt(4+width) reach");
        var goal=new AlienBug.AttackGoal(g); goal.start(); for(int n=0;n<=20;n++) { t.invulnerableTime=0; goal.tick(); }
        near(h,t.getHealth(),992,"Two four-damage bites at twenty tick interval"); h.assertValueEqual(g.attackTimer(),10,"Bite animation starts on attack");
        t.setPos(h.absoluteVec(new Vec3(6,2,3))); for(int n=0;n<20;n++) { t.invulnerableTime=0; goal.tick(); } near(h,t.getHealth(),992,"Outside source reach");
        for(int n=0;n<10;n++) g.tick(); h.assertValueEqual(g.attackTimer(),0,"Bite timer expires"); g.discard(); t.discard(); h.succeed();
    }
    private static void daylight(GameTestHelper h) {
        var g=mob(h,new Vec3(3,2,3)); var t=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(3,2,7)); t.setNoGravity(true);
        var goal=new AlienBug.TargetGoal<>(g,IronGolem.class); boolean found=false; for(int n=0;n<200 && !found;n++) found=goal.canUse();
        h.assertTrue(found,"Unfiltered daylight target acquisition"); goal.start(); h.assertTrue(g.getTarget()==t,"Golem target selected"); g.discard(); t.discard(); h.succeed();
    }
    private static void realAi(GameTestHelper h) {
        var g=new AlienBug(NpcContent.ALIEN_BUG.get(),h.getLevel()); g.setPos(h.absoluteVec(new Vec3(3,2,3))); g.setNoGravity(true); h.getLevel().addFreshEntity(g);
        var t=GhastlingGameTests.target(h,new Vec3(3,2,4.5)); g.setTarget(t);
        h.runAfterDelay(25,()->{ h.assertTrue(t.getHealth()<1000,"Registered goal selector performs actual bite"); g.discard(); t.discard(); h.succeed(); });
    }
    private static void loot(GameTestHelper h) {
        var g=mob(h,new Vec3(3,2,3)); var table=h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/alienbug")));
        var params=new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.THIS_ENTITY,g).withParameter(LootContextParams.ORIGIN,g.position()).withParameter(LootContextParams.DAMAGE_SOURCE,h.getLevel().damageSources().genericKill()).create(LootContextParamSets.ENTITY);
        h.assertTrue(table.getRandomItems(params).isEmpty(),"Source null loot table does not inherit spider drops"); g.discard(); h.succeed();
    }
    private static NpcSpawnerBlockEntity spawner(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(5,2,5)); h.getLevel().setBlock(pos,NpcSpawnerContent.BLOCK.get().defaultBlockState(),2); var b=(NpcSpawnerBlockEntity)h.getLevel().getBlockEntity(pos);
        b.configure(4,2,1,0,0,List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("alienbug"),1)),ItemStack.EMPTY); tick(h,b); return b;
    }
    private static void tick(GameTestHelper h,NpcSpawnerBlockEntity b) { NpcSpawnerBlockEntity.serverTick(h.getLevel(),b.getBlockPos(),b.getBlockState(),b); }
    private static AlienBug first(GameTestHelper h,NpcSpawnerBlockEntity b) { var g=(AlienBug)h.getLevel().getEntity(b.activeIds().iterator().next()); g.removeFreeWill(); return g; }
    private static void linkedSave(GameTestHelper h) {
        var b=spawner(h); var g=first(h,b); var saved=NetherGameTests.save(h,g); var id=g.getUUID(); g.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.assertValueEqual(b.activeCount(),1,"Unload preserves reservation"); var restored=new AlienBug(NpcContent.ALIEN_BUG.get(),h.getLevel()); NetherGameTests.load(h,restored,saved); restored.removeFreeWill();
        h.assertValueEqual(restored.spawnerLink(),b.link(),"Spider-derived owner persisted"); restored.spawnerLink().relink(restored); h.assertValueEqual(b.activeIds(),Set.of(id),"Reload does not duplicate reservation");
        restored.hurtServer(h.getLevel(),h.getLevel().damageSources().genericKill(),1000); b.finish(id,true); h.assertValueEqual(b.remaining(),3,"Death counted once"); h.assertValueEqual(b.activeCount(),0,"Reservation released"); h.getLevel().setBlock(b.getBlockPos(),Blocks.AIR.defaultBlockState(),2); h.succeed();
    }
    private static void depart(GameTestHelper h) {
        var b=spawner(h); first(h,b).discard(); h.assertValueEqual(b.remaining(),4,"Discard costs no life"); h.assertValueEqual(b.activeCount(),0,"Discard frees slot");
        tick(h,b); first(h,b).remove(Entity.RemovalReason.CHANGED_DIMENSION); h.assertValueEqual(b.remaining(),4,"Departure costs no life"); h.assertValueEqual(b.activeCount(),0,"Departure frees slot"); h.getLevel().setBlock(b.getBlockPos(),Blocks.AIR.defaultBlockState(),2); h.succeed();
    }
    private static void faction(GameTestHelper h) {
        var g=mob(h,new Vec3(3,2,3)); var ally=new ArmySoldier(NpcContent.ARMY.get(),h.getLevel()); ally.setPos(h.absoluteVec(new Vec3(3,2,5))); ally.setNoGravity(true); h.getLevel().addFreshEntity(ally); ally.setLastHurtByMob(g);
        h.runAfterDelay(10,()->{ h.assertTrue(ally.getTarget()!=g,"GenericNPC retaliation ignores HOSTILE AlienBug"); h.assertTrue(!ArmedNpc.class.isInstance(g) && g.getMainHandItem().isEmpty(),"AlienBug keeps spider AI"); g.discard(); ally.discard(); h.succeed(); });
    }
    private static void jockey(GameTestHelper h) {
        var rider=EntityTypes.SKELETON.create(h.getLevel(),EntitySpawnReason.JOCKEY);
        Consumer<net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent> attach=e->{
            if(e.getEntity() instanceof AlienBug && e.getSpawner()!=null) { rider.setPos(e.getEntity().position()); rider.startRiding(e.getEntity(),true,false); }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(attach);
        NpcSpawnerBlockEntity b;
        try { b=spawner(h); } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(attach); }
        var g=first(h,b); h.assertTrue(rider.getVehicle()==g && h.getLevel().getEntity(rider.getUUID())==rider,"Finalized passenger is registered in the actual level");
        h.assertValueEqual(b.activeCount(),1,"Only the AlienBug occupies the source reservation"); rider.discard(); g.discard(); h.getLevel().setBlock(b.getBlockPos(),Blocks.AIR.defaultBlockState(),2); h.succeed();
    }
    private AlienBugGameTests() {}
}
