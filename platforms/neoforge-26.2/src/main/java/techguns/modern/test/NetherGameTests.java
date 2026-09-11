package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.npc.*;

final class NetherGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("netherblaster_damage_and_no_ignition", () -> h -> hitDamage(h, "plain", 14));
        r.register("netherblaster_ordinary_fire_armor", () -> h -> hitDamage(h, "armor", 12.32f));
        r.register("netherblaster_cyber_fire_armor", () -> h -> hitDamage(h, "cyber", 3.36f));
        r.register("netherblaster_mutant_fire_armor", () -> h -> hitDamage(h, "mutant", 8.96f));
        r.register("netherblaster_fire_resistance_not_immunity", () -> h -> hitDamage(h, "resistance", 14));
        r.register("netherblaster_witch_magic_resistance", () -> h -> hitDamage(h, "witch", 2.1f));
        r.register("netherblaster_repeated_hits", () -> NetherGameTests::repeated);
        r.register("netherblaster_npc_damage_factor", () -> NetherGameTests::npcDamage);
        r.register("netherblaster_drag_and_exact_ttl", () -> NetherGameTests::flight);
        r.register("netherblaster_water_drag", () -> NetherGameTests::water);
        r.register("netherblaster_falloff_from_saved_origin", () -> NetherGameTests::falloff);
        r.register("netherblaster_first_tick_damage", () -> NetherGameTests::firstTick);
        r.register("netherblaster_solid_wall", () -> NetherGameTests::wall);
        r.register("netherblaster_cancelled_impact", () -> NetherGameTests::cancelImpact);
        r.register("netherblaster_cancelled_spawn", () -> NetherGameTests::cancelSpawn);
        r.register("netherblaster_save_remaining_life", () -> NetherGameTests::persistence);
        r.register("netherblaster_invalid_save", () -> NetherGameTests::invalidSave);
        r.register("cyberdemon_undead_attributes", () -> NetherGameTests::attributes);
        r.register("cyberdemon_egg_and_save", () -> NetherGameTests::egg);
        r.register("cyberdemon_muzzle_and_ammo", () -> NetherGameTests::muzzle);
        r.register("cyberdemon_real_ai", () -> NetherGameTests::ai);
        r.register("cyberdemon_shared_hostile_faction", () -> NetherGameTests::faction);
        r.register("cyberdemon_loot_chance_and_quantity", () -> NetherGameTests::loot);
        NetherSpawnGameTests.register(r);
    }
    static void near(GameTestHelper h, double actual, double expected, String label) { h.assertTrue(Math.abs(actual-expected)<.0005,label+": "+actual+" != "+expected); }
    static CompoundTag save(GameTestHelper h, Entity entity) {
        var report=new ProblemReporter.Collector(); var out=TagValueOutput.createWithContext(report,h.getLevel().registryAccess()); entity.saveWithoutId(out);
        h.assertTrue(report.isEmpty(),"Clean serialization: "+report.getReport()); return out.buildResult();
    }
    static void load(GameTestHelper h, Entity entity, CompoundTag tag) { entity.load(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),tag)); }
    private static NetherBlasterProjectile shot(GameTestHelper h, Vec3 position, Vec3 velocity) {
        var shot=new NetherBlasterProjectile(TGContent.NETHER_BLAST.get(),h.getLevel()); shot.setOwner(WeaponGameTests.player(h));
        shot.setPos(h.absoluteVec(position)); shot.setDeltaMovement(velocity); h.getLevel().addFreshEntity(shot); return shot;
    }
    private static LivingEntity target(GameTestHelper h, Vec3 position) {
        var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,position); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000); return target;
    }
    private static void strike(GameTestHelper h, LivingEntity target) {
        var shot=new NetherBlasterProjectile(TGContent.NETHER_BLAST.get(),h.getLevel()); shot.setOwner(WeaponGameTests.player(h));
        shot.setPos(target.position().add(-.9,.9,0)); shot.setDeltaMovement(1,0,0); h.getLevel().addFreshEntity(shot); shot.tick();
        h.assertTrue(shot.isRemoved(),"Charge removed on first impact");
    }
    private static void hitDamage(GameTestHelper h, String mode, float damage) {
        LivingEntity target=switch(mode) {
            case "cyber" -> h.spawnWithNoFreeWill(NpcContent.CYBER_DEMON.get(),new Vec3(5,2,5));
            case "mutant" -> h.spawnWithNoFreeWill(NpcContent.SUPER_MUTANT.get(),new Vec3(5,2,5));
            case "witch" -> h.spawnWithNoFreeWill(EntityTypes.WITCH,new Vec3(5,2,5));
            default -> target(h,new Vec3(5,2,5));
        };
        target.setNoGravity(true);
        if(mode.equals("armor")) target.getAttribute(Attributes.ARMOR).setBaseValue(10);
        if(mode.equals("resistance")) target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,100));
        float before=target.getHealth(); strike(h,target); near(h,before-target.getHealth(),damage,"Original FIRE handling");
        h.assertTrue(!target.isOnFire(),"Source charge does not set its target on fire"); target.discard(); h.succeed();
    }
    private static void repeated(GameTestHelper h) {
        var target=target(h,new Vec3(5,2,5)); strike(h,target); strike(h,target);
        near(h,target.getHealth(),972,"Both same-tick charges bypass hurt cooldown"); target.discard(); h.succeed();
    }
    private static void npcDamage(GameTestHelper h) {
        var target=target(h,new Vec3(5,2,5)); var npc=h.spawnWithNoFreeWill(NpcContent.CYBER_DEMON.get(),new Vec3(2,2,2));
        double before=NpcConfig.DAMAGE_FACTOR.get();
        try {
            for(double factor:new double[]{2,0}) {
                NpcConfig.DAMAGE_FACTOR.set(factor); float health=target.getHealth();
                var blast=shot(h,new Vec3(4.1,2.9,5),new Vec3(1,0,0)); blast.setOwner(npc); blast.npcDamage(.6f); blast.tick();
                near(h,health-target.getHealth(),14*.6*factor,"Launch difficulty and live NPC factor apply at collision");
            }
        } finally { NpcConfig.DAMAGE_FACTOR.set(before); npc.discard(); target.discard(); }
        h.succeed();
    }
    private static void flight(GameTestHelper h) {
        var shot=shot(h,new Vec3(4,80,4),new Vec3(0,2,0)); var start=shot.position(); shot.tick();
        near(h,shot.getY()-start.y,2,"First movement precedes drag"); near(h,shot.getDeltaMovement().y,2*(double).99f,"Air drag");
        for(int n=1;n<59;n++) shot.tick(); h.assertTrue(!shot.isRemoved(),"Alive after 59 ticks"); shot.tick();
        h.assertTrue(shot.isRemoved(),"Lifetime is 60 movements, not 61"); h.succeed();
    }
    private static void water(GameTestHelper h) {
        h.setBlock(new BlockPos(4,2,4),Blocks.WATER);
        var shot=shot(h,new Vec3(4.5,2.2,4.5),new Vec3(.1,0,0)); shot.tick();
        h.assertTrue(shot.isInWater(),"Water fixture submerges projectile"); near(h,shot.getDeltaMovement().x,.1*(double).85f,"Source water drag");
        shot.discard(); h.succeed();
    }
    private static void falloff(GameTestHelper h) {
        var target=target(h,new Vec3(5,2,5)); var shot=shot(h,new Vec3(4.1,2.9,5),new Vec3(1,0,0));
        var data=save(h,shot); data.putDouble("origin_x",shot.getX()-20); data.putDouble("origin_y",shot.getY()); data.putDouble("origin_z",shot.getZ()); load(h,shot,data);
        shot.tick(); near(h,target.getHealth(),988,"Straight displacement 20 gives damage 12 before movement"); target.discard(); h.succeed();
    }
    private static void firstTick(GameTestHelper h) {
        var target=target(h,new Vec3(35,65,5)); var shot=shot(h,new Vec3(3,65.9,5),new Vec3(34,0,0)); shot.tick();
        near(h,target.getHealth(),986,"Legacy damage uses start-of-tick distance, not the far impact point"); target.discard(); h.succeed();
    }
    private static void wall(GameTestHelper h) {
        var target=target(h,new Vec3(8,2,4)); h.setBlock(new BlockPos(5,2,4),Blocks.STONE);
        var shot=shot(h,new Vec3(3,2.5,4.5),new Vec3(8,0,0)); shot.tick();
        h.assertTrue(shot.isRemoved(),"Solid wall intercepts"); near(h,target.getHealth(),1000,"No damage through wall");
        h.assertBlockPresent(Blocks.STONE,new BlockPos(5,2,4)); target.discard(); h.succeed();
    }
    private static void cancelImpact(GameTestHelper h) {
        var target=target(h,new Vec3(5,2,5)); var shot=shot(h,new Vec3(3,2.9,5),new Vec3(5,0,0));
        Consumer<ProjectileImpactEvent> cancel=e -> { if(e.getProjectile()==shot) e.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(cancel);
        try { shot.tick(); } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        h.assertTrue(!shot.isRemoved(),"Cancelled hit continues flight"); near(h,target.getHealth(),1000,"Cancelled hit does no damage");
        shot.discard(); target.discard(); h.succeed();
    }
    private static void cancelSpawn(GameTestHelper h) {
        var player=WeaponGameTests.player(h); var gun=TGContent.GUNS.get("netherblaster").toStack(); gun.set(TGContent.ROUNDS.get(),10); player.setItemInHand(InteractionHand.MAIN_HAND,gun);
        Consumer<EntityJoinLevelEvent> cancel=e -> { if(e.getEntity() instanceof NetherBlasterProjectile shot && shot.getOwner()==player) e.setCanceled(true); }; NeoForge.EVENT_BUS.addListener(cancel);
        try { h.assertTrue(!GunItem.fire(h.getLevel(),player,gun),"Cancelled charge spawn rejects shot"); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); }
        h.assertValueEqual(GunItem.rounds(gun),10,"No ammunition consumed"); h.assertTrue(!player.getCooldowns().isOnCooldown(gun),"No cooldown"); h.succeed();
    }
    private static void persistence(GameTestHelper h) {
        var original=shot(h,new Vec3(4,90,4),new Vec3(0,.1,0)); original.npcDamage(.8f); for(int i=0;i<5;i++) original.tick();
        var data=save(h,original); var restored=new NetherBlasterProjectile(TGContent.NETHER_BLAST.get(),h.getLevel()); load(h,restored,data); original.discard();
        h.assertValueEqual(restored.shotDamage(),new ShotDamage(true,.8f),"NPC difficulty context survives save");
        near(h,save(h,restored).getDoubleOr("origin_y",0),data.getDoubleOr("origin_y",1),"Original trajectory origin retained");
        for(int i=0;i<54;i++) restored.tick(); h.assertTrue(!restored.isRemoved(),"Save does not reset TTL"); restored.tick();
        h.assertTrue(restored.isRemoved(),"Remaining lifetime expires on time"); h.succeed();
    }
    private static void invalidSave(GameTestHelper h) {
        var original=shot(h,new Vec3(4,90,4),new Vec3(0,.1,0)); original.tick(); var valid=save(h,original); original.discard();
        var cases=new ArrayList<CompoundTag>();
        for(String id:List.of("revolver","missing")) { var tag=valid.copy(); tag.putString("weapon",id); cases.add(tag); }
        for(double x:new double[]{Double.NaN,Double.POSITIVE_INFINITY,1e100,-1e100}) { var tag=valid.copy(); tag.putDouble("origin_x",x); cases.add(tag); }
        var partial=valid.copy(); partial.remove("origin_z"); cases.add(partial);
        var expired=valid.copy(); expired.putInt("age",60); cases.add(expired);
        var scale=valid.copy(); scale.putFloat("damage_scale",5); cases.add(scale);
        for(var tag:cases) { var restored=new NetherBlasterProjectile(TGContent.NETHER_BLAST.get(),h.getLevel()); load(h,restored,tag); h.assertTrue(restored.isRemoved(),"Malformed/expired charge discarded"); }
        h.succeed();
    }
    private static void attributes(GameTestHelper h) {
        var npc=h.spawnWithNoFreeWill(NpcContent.CYBER_DEMON.get(),new Vec3(4,2,4));
        near(h,npc.getMaxHealth(),30,"Health"); near(h,npc.getAttributeValue(Attributes.MOVEMENT_SPEED),.35,"Speed"); near(h,npc.getAttributeValue(Attributes.ATTACK_DAMAGE),7,"Melee");
        near(h,npc.getBbHeight(),1.8,"Height"); near(h,npc.getEyeHeight(),1.53,"Eye"); near(h,npc.getBbWidth(),.6,"Width");
        h.assertTrue(npc.fireImmune() && !npc.hurtServer(h.getLevel(),h.getLevel().damageSources().lava(),10),"Vanilla fire immunity");
        h.assertTrue(npc.is(EntityTypeTags.UNDEAD) && npc.is(EntityTypeTags.SENSITIVE_TO_SMITE) && npc.isInvertedHealAndHarm(),"Undead tags");
        h.assertTrue(!npc.addEffect(new MobEffectInstance(MobEffects.POISON,100)) && !npc.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100)),"Undead effect exclusions");
        h.assertTrue(!npc.canPickUpLoot(),"No random equipment pickup"); npc.discard(); h.succeed();
    }
    private static void egg(GameTestHelper h) {
        var player=WeaponGameTests.player(h); var egg=NpcContent.CYBER_EGG.toStack(2); player.setItemInHand(InteractionHand.MAIN_HAND,egg);
        var pos=new BlockPos(4,1,4); h.setBlock(pos,Blocks.STONE); h.useBlock(pos,player);
        var mobs=h.getLevel().getEntitiesOfClass(CyberDemon.class,new net.minecraft.world.phys.AABB(h.absolutePos(pos)).inflate(4));
        h.assertValueEqual(mobs.size(),1,"Spawn egg creates CyberDemon"); h.assertValueEqual(egg.getCount(),1,"Egg consumed"); var npc=mobs.getFirst();
        h.assertTrue(npc.getMainHandItem().is(TGContent.GUNS.get("netherblaster").get()),"Original weapon equipped"); h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),10,"Starts loaded");
        npc.setCustomName(Component.literal("Cyber test")); npc.setHealth(19); npc.setLeftHanded(true); npc.getMainHandItem().set(TGContent.ROUNDS.get(),3);
        var restored=new CyberDemon(NpcContent.CYBER_DEMON.get(),h.getLevel()); load(h,restored,save(h,npc));
        h.assertTrue(restored.isLeftHanded(),"Hand saved"); h.assertValueEqual(restored.getCustomName(),npc.getCustomName(),"Name saved");
        near(h,restored.getHealth(),19,"Health saved"); h.assertValueEqual(GunItem.rounds(restored.getMainHandItem()),3,"Loading does not refill NPC weapon"); npc.discard(); h.succeed();
    }
    private static void muzzle(GameTestHelper h) {
        var npc=h.spawnWithNoFreeWill(NpcContent.CYBER_DEMON.get(),new Vec3(4,40,4)); npc.equipBlaster(); npc.setNoGravity(true); npc.setLeftHanded(true); npc.setYRot(90); npc.setYHeadRot(0); npc.setXRot(0);
        var target=target(h,new Vec3(4,40,10)); h.assertTrue(npc.fireAt(target),"Armed NPC fires");
        var shots=h.getLevel().getEntitiesOfClass(NetherBlasterProjectile.class,npc.getBoundingBox().inflate(3),s -> s.getOwner()==npc); h.assertValueEqual(shots.size(),1,"Correct projectile");
        var shot=shots.getFirst(); near(h,shot.getX()-npc.getX(),-.46,"Original right muzzle, including extra .3"); near(h,shot.getY()-npc.getEyeY(),-.69,"Original extra height offset");
        h.assertTrue(shot.getDeltaMovement().z>2.1,"Head yaw and source 1.5 times speed"); h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),10,"NPC keeps ammunition");
        shot.discard(); npc.discard(); target.discard(); h.succeed();
    }
    private static void ai(GameTestHelper h) {
        for(int x=1;x<11;x++) for(int z=1;z<11;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var npc=new CyberDemon(NpcContent.CYBER_DEMON.get(),h.getLevel()); npc.setPos(h.absoluteVec(new Vec3(3,1,3))); npc.equipBlaster(); h.getLevel().addFreshEntity(npc);
        var target=target(h,new Vec3(8,1,3)); npc.setTarget(target);
        h.runAfterDelay(65,() -> { h.assertTrue(target.getHealth()<1000,"Actual AI fires moving blaster charges"); h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),10,"AI does not consume player ammo");
            h.getLevel().getEntitiesOfClass(NetherBlasterProjectile.class,npc.getBoundingBox().inflate(120),s -> s.getOwner()==npc).forEach(Entity::discard); npc.discard(); target.discard(); h.succeed(); });
    }
    private static void faction(GameTestHelper h) {
        var cyber=new CyberDemon(NpcContent.CYBER_DEMON.get(),h.getLevel()); cyber.setPos(h.absoluteVec(new Vec3(3,2,3))); cyber.setNoGravity(true); h.getLevel().addFreshEntity(cyber);
        var mutant=new SuperMutant(NpcContent.SUPER_MUTANT.get(),h.getLevel()); mutant.setPos(h.absoluteVec(new Vec3(8,2,3))); mutant.setNoGravity(true); h.getLevel().addFreshEntity(mutant);
        h.runAfterDelay(2,() -> { cyber.hurtServer(h.getLevel(),h.getLevel().damageSources().mobAttack(mutant),1); mutant.hurtServer(h.getLevel(),h.getLevel().damageSources().mobAttack(cyber),1); });
        h.runAfterDelay(13,() -> { h.assertTrue(cyber.getTarget()==null && mutant.getTarget()==null,"Both original HOSTILE members ignore friendly retaliation"); cyber.discard(); mutant.discard(); h.succeed(); });
    }
    static LootTable table(GameTestHelper h) { return h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/cyberdemon"))); }
    static LootParams params(GameTestHelper h, LivingEntity npc, Player player) {
        return new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.THIS_ENTITY,npc).withParameter(LootContextParams.ORIGIN,npc.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE,h.getLevel().damageSources().playerAttack(player)).withParameter(LootContextParams.ATTACKING_ENTITY,player)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER,player).create(LootContextParamSets.ENTITY);
    }
    private static void loot(GameTestHelper h) {
        var npc=h.spawnWithNoFreeWill(NpcContent.CYBER_DEMON.get(),new Vec3(4,2,4)); var player=WeaponGameTests.player(h); var item=TGContent.MATERIALS.get("cyberneticparts").get();
        var sword=new ItemStack(Items.DIAMOND_SWORD); player.setItemInHand(InteractionHand.MAIN_HAND,sword);
        var enchant=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
        for(int looting=0;looting<=3;looting++) {
            if(looting>0) sword.enchant(enchant,looting);
            float yes=.5f+looting*.05f-.01f, no=.5f+looting*.05f+.01f;
            var drops=table(h).getRandomItems(params(h,npc,player),fixed(yes));
            int count=drops.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum(); h.assertTrue(count>=1 && count<=3,"Looting chance does not inflate cyber quantity");
            h.assertTrue(table(h).getRandomItems(params(h,npc,player),fixed(no)).stream().noneMatch(s -> s.is(item)),"Chance threshold follows .5 + .05 per level");
        }
        var secondary=table(h).getRandomItems(params(h,npc,player),fixed(.01f));
        for(var drop:List.of(Items.BLAZE_ROD,Items.GHAST_TEAR)) { int count=secondary.stream().filter(s -> s.is(drop)).mapToInt(ItemStack::getCount).sum(); h.assertTrue(count>=4 && count<=5,"Secondary Looting III bonus retained"); }
        npc.discard(); h.succeed();
    }
    private static LegacyRandomSource fixed(float value) { return new LegacyRandomSource(1) { @Override public float nextFloat() { return value; } }; }
    private NetherGameTests() {}
}
