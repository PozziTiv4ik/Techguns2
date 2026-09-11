package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.npc.*;
import techguns.modern.radiation.RadiationSystem;

final class NpcGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        registry.register("npc_attributes_and_equipment", () -> NpcGameTests::attributes);
        registry.register("npc_spawn_egg", () -> NpcGameTests::egg);
        registry.register("npc_save_preserves_weapon_and_name", () -> NpcGameTests::saveMob);
        for(int roll=0;roll<4;roll++) {
            final int weapon=roll;
            registry.register("npc_fire_"+SuperMutantRules.weapon(roll), () -> h -> fire(h,weapon));
        }
        registry.register("npc_real_ai_fires_and_keeps_ammo", () -> h -> ai(h,false));
        registry.register("npc_real_ai_respects_wall", () -> h -> ai(h,true));
        registry.register("npc_swap_to_melee", () -> NpcGameTests::melee);
        registry.register("npc_cancelled_spawn", () -> NpcGameTests::cancelled);
        registry.register("npc_target_validation", () -> NpcGameTests::invalidTarget);
        registry.register("npc_retaliation_same_faction", () -> NpcGameTests::faction);
        registry.register("npc_damage_scaling_and_player_unchanged", () -> NpcGameTests::scaling);
        registry.register("npc_projectile_save_keeps_difficulty", () -> NpcGameTests::saveShots);
        registry.register("npc_invalid_shot_scale_rejected", () -> NpcGameTests::invalidScale);
        registry.register("npc_typed_bullet_armor", () -> h -> armor(h,"bullet",6.84f));
        registry.register("npc_typed_laser_armor", () -> h -> armor(h,"laser",7.2f));
        registry.register("npc_typed_rocket_armor", () -> h -> armor(h,"rocket",30));
        registry.register("npc_typed_acid_armor", () -> h -> armor(h,"acid",4));
        registry.register("npc_typed_radiation_armor", () -> h -> armor(h,"radiation",4));
        registry.register("npc_unresistable_fall", () -> h -> armor(h,"fall",10));
        registry.register("npc_vanilla_magic_retains_armor_bypass", () -> h -> armor(h,"magic",10));
        registry.register("npc_looting_changes_chance_not_cyber_quantity", () -> NpcGameTests::lootProbability);
        registry.register("npc_secondary_loot_quantities", () -> NpcGameTests::secondaryLoot);
        registry.register("npc_death_loot_to_fabricator_housing", () -> NpcGameTests::deathCraft);
        registry.register("npc_no_default_biome_spawn", () -> NpcGameTests::spawnTables);
    }
    private static void near(GameTestHelper h,double actual,double expected,String label) { h.assertTrue(Math.abs(actual-expected)<.0003,label+": "+actual+" != "+expected); }
    private static SuperMutant mob(GameTestHelper h,int roll,Vec3 pos) {
        var npc=h.spawnWithNoFreeWill(NpcContent.SUPER_MUTANT.get(),pos); npc.setNoGravity(true); npc.equipRoll(roll); return npc;
    }
    private static LivingEntity target(GameTestHelper h,Vec3 pos) {
        var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,pos); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000); return target;
    }
    private static void face(SuperMutant npc,LivingEntity target) {
        Vec3 d=target.getEyePosition().subtract(npc.getEyePosition());
        npc.setYRot(90); // Body yaw must not replace the aimed head yaw.
        npc.setYHeadRot((float)Math.toDegrees(Math.atan2(-d.x,d.z))); npc.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));
        npc.getSensing().tick();
    }
    private static List<Projectile> shots(GameTestHelper h,SuperMutant npc) { return h.getLevel().getEntitiesOfClass(Projectile.class,npc.getBoundingBox().inflate(45),p -> p.getOwner()==npc); }
    private static void cleanup(GameTestHelper h,SuperMutant npc) { shots(h,npc).forEach(Entity::discard); npc.discard(); }
    private static CompoundTag save(GameTestHelper h,Entity entity) {
        var report=new ProblemReporter.Collector(); var out=TagValueOutput.createWithContext(report,h.getLevel().registryAccess()); entity.saveWithoutId(out);
        h.assertTrue(report.isEmpty(),"Clean entity serialization: "+report.getReport()); return out.buildResult();
    }
    private static void load(GameTestHelper h,Entity entity,CompoundTag data) { entity.load(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),data)); }
    private static void attributes(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(3,2,3));
        near(h,npc.getMaxHealth(),35,"Original health"); near(h,npc.getAttributeValue(Attributes.MOVEMENT_SPEED),.3,"Speed");
        near(h,npc.getAttributeValue(Attributes.ATTACK_DAMAGE),7,"Melee"); near(h,npc.getAttributeValue(Attributes.ARMOR_TOUGHNESS),1,"Toughness");
        near(h,npc.getBbWidth(),1,"Width"); near(h,npc.getBbHeight(),2.7,"Height"); near(h,npc.getEyeHeight(),2.295,"Eye height");
        h.assertTrue(!npc.canPickUpLoot(),"Random equipment pickup disabled");
        for(int roll=0;roll<5;roll++) { npc.equipRoll(roll); h.assertTrue(npc.getMainHandItem().is(TGContent.GUNS.get(SuperMutantRules.weapon(roll)).get()),"Original weapon selection");
            h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),((GunItem)npc.getMainHandItem().getItem()).definition().stats().capacity(),"New NPC weapon starts loaded"); }
        cleanup(h,npc); h.succeed();
    }
    private static void egg(GameTestHelper h) {
        var player=WeaponGameTests.player(h); var egg=NpcContent.EGG.toStack(2); player.setItemInHand(InteractionHand.MAIN_HAND,egg);
        h.assertTrue(egg.getItem() instanceof SpawnEggItem,"Actual spawn egg behavior");
        h.assertValueEqual(SpawnEggItem.getType(egg),NpcContent.SUPER_MUTANT.get(),"Typed entity component");
        var pos=new BlockPos(4,1,4); h.setBlock(pos,Blocks.STONE); h.useBlock(pos,player);
        var mobs=h.getLevel().getEntitiesOfClass(SuperMutant.class,new net.minecraft.world.phys.AABB(h.absolutePos(pos)).inflate(4));
        h.assertValueEqual(mobs.size(),1,"Egg creates one real NPC"); h.assertValueEqual(egg.getCount(),1,"Survival egg consumed");
        h.assertTrue(mobs.getFirst().armed(),"Spawn finalization chooses weapon"); mobs.forEach(Entity::discard); h.succeed();
    }
    private static void saveMob(GameTestHelper h) {
        var original=mob(h,1,new Vec3(3,2,3)); original.setCustomName(Component.literal("Mutant test")); original.setHealth(19); original.setLeftHanded(true);
        original.getMainHandItem().set(TGContent.ROUNDS.get(),12);
        var restored=new SuperMutant(NpcContent.SUPER_MUTANT.get(),h.getLevel()); load(h,restored,save(h,original));
        h.assertTrue(restored.getMainHandItem().is(TGContent.GUNS.get("ak47").get()),"Loading does not reroll equipment");
        h.assertValueEqual(GunItem.rounds(restored.getMainHandItem()),12,"Saved weapon components");
        h.assertValueEqual(restored.getCustomName(),original.getCustomName(),"Name"); near(h,restored.getHealth(),19,"Health");
        h.assertTrue(restored.isLeftHanded(),"Handedness persists"); cleanup(h,original); h.succeed();
    }
    private static void fire(GameTestHelper h,int roll) {
        var npc=mob(h,roll,new Vec3(3,40,3)); var target=target(h,new Vec3(9,40,3)); face(npc,target);
        var gun=((GunItem)npc.getMainHandItem().getItem()).definition(); int initial=GunItem.rounds(npc.getMainHandItem());
        try {
            h.assertTrue(npc.fireAt(target),"NPC fires its source weapon");
            var shots=shots(h,npc); h.assertValueEqual(shots.size(),gun.projectileCount(),"Projectile family/count");
            for(var shot:shots) {
                h.assertTrue(shot instanceof Bullet && gun.projectile()==ProjectileKind.BALLISTIC || shot instanceof LaserBeam && gun.projectile()==ProjectileKind.LASER || shot instanceof RocketProjectile && gun.projectile()==ProjectileKind.ROCKET,"No substituted projectile");
                if(shot instanceof RocketProjectile r) h.assertTrue(!r.damagesBlocks() && r.variant()==RocketVariant.DEFAULT,"NPC rockets preserve terrain and default ammunition");
                if(!(shot instanceof LaserBeam)) h.assertTrue(shot.getDeltaMovement().x>0,"Head yaw aims towards target despite different body yaw");
            }
            h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),initial,"NPC shots do not consume player ammo");
            if(roll==3) h.assertTrue(target.getHealth()<1000,"Laser traces in the firing tick");
        } finally { cleanup(h,npc); target.discard(); } h.succeed();
    }
    private static void ai(GameTestHelper h,boolean blocked) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        if(blocked) for(int y=2;y<=5;y++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(5,y,z),Blocks.STONE);
        var npc=h.spawn(NpcContent.SUPER_MUTANT.get(),new Vec3(3,2,3)); npc.equipRoll(1);
        var target=target(h,new Vec3(9,2,3)); npc.setTarget(target);
        int[] count={0}; Consumer<EntityJoinLevelEvent> observed=event -> { if(event.getEntity() instanceof Projectile p && p.getOwner()==npc) count[0]++; };
        NeoForge.EVENT_BUS.addListener(observed);
        h.runAfterDelay(65,() -> {
            try {
                h.assertTrue(blocked ? count[0]==0 : count[0]>=3,"Real server AI respects burst/visibility: "+count[0]);
                h.assertTrue(blocked ? target.getHealth()==1000 : target.getHealth()<1000,"Real projectiles hit visible target");
                h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),30,"AI firing leaves magazine intact");
            } finally { NeoForge.EVENT_BUS.unregister(observed); cleanup(h,npc); target.discard(); }
            h.succeed();
        });
    }
    private static void melee(GameTestHelper h) {
        var npc=mob(h,1,new Vec3(3,2,3)); var target=target(h,new Vec3(4,2,3)); npc.setTarget(target);
        var goal=new NpcRangedGoal(npc); h.assertTrue(goal.canUse(),"Gun enables ranged combat");
        npc.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY); h.assertTrue(!goal.canUse() && !goal.canContinueToUse(),"Removing gun cancels ranged goal");
        npc.doHurtTarget(h.getLevel(),target); near(h,1000-target.getHealth(),7,"Unarmed NPC uses source melee damage");
        npc.equipRoll(3); h.assertTrue(goal.canUse(),"Replacement gun enables ranged goal"); cleanup(h,npc); target.discard(); h.succeed();
    }
    private static void cancelled(GameTestHelper h) {
        var npc=mob(h,3,new Vec3(3,40,3)); var target=target(h,new Vec3(9,40,3)); face(npc,target);
        Consumer<EntityJoinLevelEvent> cancel=event -> { if(event.getEntity() instanceof Projectile p && p.getOwner()==npc) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(cancel);
        try { h.assertTrue(!npc.fireAt(target),"Cancelled beam spawn rejects attack"); near(h,target.getHealth(),1000,"No laser damage before accepted spawn"); }
        finally { NeoForge.EVENT_BUS.unregister(cancel); cleanup(h,npc); target.discard(); } h.succeed();
    }
    private static void invalidTarget(GameTestHelper h) {
        var npc=mob(h,3,new Vec3(3,40,3)); var target=target(h,new Vec3(30,40,3)); face(npc,target);
        h.assertTrue(!npc.fireAt(target),"Out-of-range target rejected");
        target.discard(); h.assertTrue(!npc.fireAt(target),"Removed target rejected"); h.assertTrue(!npc.fireAt(null),"Missing target rejected");
        var creative=h.makeMockPlayer(GameType.CREATIVE); creative.setPos(npc.position().add(3,0,0));
        h.assertTrue(!npc.fireAt(creative),"Creative target ignored");
        cleanup(h,npc); h.succeed();
    }
    private static void faction(GameTestHelper h) {
        var npc=h.spawn(NpcContent.SUPER_MUTANT.get(),new Vec3(3,2,3)); npc.setNoGravity(true); npc.equipRoll(1);
        var ally=mob(h,1,new Vec3(6,2,3));
        var other=target(h,new Vec3(8,2,3));
        // ServerLevel advances tickCount; direct Entity.tick calls alone leave retaliation timestamps at zero.
        h.runAfterDelay(2,() -> npc.hurtServer(h.getLevel(),h.getLevel().damageSources().mobAttack(ally),1));
        boolean[] ignored={false};
        h.runAfterDelay(12,() -> {
            ignored[0]=npc.getTarget()!=ally;
            npc.invulnerableTime=0; npc.hurtServer(h.getLevel(),h.getLevel().damageSources().mobAttack(other),1);
        });
        h.runAfterDelay(24,() -> {
            try {
                h.assertTrue(ignored[0],"Registered retaliation goal ignores the same hostile faction");
                h.assertTrue(npc.getTarget()==other,"Registered retaliation goal accepts a non-faction attacker");
            } finally { cleanup(h,npc); cleanup(h,ally); other.discard(); }
            h.succeed();
        });
    }
    private static void scaling(GameTestHelper h) {
        var npc=mob(h,1,new Vec3(3,40,3)); var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47")); bullet.setOwner(npc); bullet.npcDamage(.6f);
        double old=NpcConfig.DAMAGE_FACTOR.get();
        try {
            NpcConfig.DAMAGE_FACTOR.set(2.0); var player=WeaponGameTests.player(h);
            var source=bullet.shotDamage().source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet);
            h.assertTrue(!source.scalesWithDifficulty(),"NPC penalty is not multiplied again by vanilla difficulty");
            player.hurtServer(h.getLevel(),source,bullet.shotDamage().againstEntity(9));
            near(h,player.getMaxHealth()-player.getHealth(),10.8,"Launch penalty .6 times live server multiplier 2");
            near(h,ShotDamage.PLAYER.againstEntity(9),9,"Player weapons ignore NPC multiplier");
            NpcConfig.DAMAGE_FACTOR.set(0.0); near(h,bullet.shotDamage().againstEntity(9),0,"Server can disable NPC projectile damage");
        } finally { NpcConfig.DAMAGE_FACTOR.set(old); cleanup(h,npc); } h.succeed();
    }
    private static void saveShots(GameTestHelper h) {
        var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47")); bullet.npcDamage(.6f); bullet.setPos(h.absoluteVec(new Vec3(3,40,3)));
        var b=new Bullet(TGContent.BULLET.get(),h.getLevel()); load(h,b,save(h,bullet)); h.assertValueEqual(b.shotDamage(),bullet.shotDamage(),"Bullet penalty persists");
        var rocket=new RocketProjectile(TGContent.ROCKET.get(),h.getLevel()); rocket.npcDamage(.8f); rocket.setPos(bullet.position());
        var r=new RocketProjectile(TGContent.ROCKET.get(),h.getLevel()); load(h,r,save(h,rocket)); h.assertValueEqual(r.shotDamage(),rocket.shotDamage(),"Rocket penalty persists");
        var laser=new LaserBeam(TGContent.LASER_BEAM.get(),h.getLevel()); laser.npcDamage(.8f); laser.setPos(bullet.position()); laser.setDeltaMovement(0,1,0); laser.trace();
        var l=new LaserBeam(TGContent.LASER_BEAM.get(),h.getLevel()); load(h,l,save(h,laser)); h.assertValueEqual(l.shotDamage(),laser.shotDamage(),"Laser visual penalty persists");
        var old=save(h,bullet); old.remove("npc_shot"); old.remove("damage_scale"); load(h,b,old); h.assertValueEqual(b.shotDamage(),ShotDamage.PLAYER,"Older projectile saves retain player damage"); h.succeed();
    }
    private static void invalidScale(GameTestHelper h) {
        var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); var data=save(h,bullet);
        for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,5}) {
            var tag=data.copy(); tag.putFloat("damage_scale",value); var restored=new Bullet(TGContent.BULLET.get(),h.getLevel()); load(h,restored,tag);
            h.assertTrue(restored.isRemoved(),"Invalid serialized NPC scale rejected");
        } h.succeed();
    }
    private static void armor(GameTestHelper h,String kind,float expected) {
        var npc=mob(h,1,new Vec3(6,40,3)); var player=WeaponGameTests.player(h);
        var level=h.getLevel(); net.minecraft.world.damagesource.DamageSource source; float amount;
        switch(kind) {
            case "bullet" -> { var shot=new Bullet(TGContent.BULLET.get(),level); shot.configure(Weapons.definition("ak47")); shot.setOwner(player); source=shot.shotDamage().source(level,ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),shot); amount=9; }
            case "laser" -> { var shot=new LaserBeam(TGContent.LASER_BEAM.get(),level); shot.setOwner(player); source=shot.shotDamage().source(level,LaserBeam.DAMAGE_TYPE,shot); amount=12; }
            case "rocket" -> { var shot=new RocketProjectile(TGContent.ROCKET.get(),level); shot.setOwner(player); source=RocketDamage.source(level,shot); amount=50; }
            case "acid" -> { source=new net.minecraft.world.damagesource.DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE)); amount=10; }
            case "radiation" -> { source=new net.minecraft.world.damagesource.DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(RadiationSystem.DAMAGE)); amount=10; }
            case "magic" -> { source=level.damageSources().magic(); amount=10; }
            default -> { source=level.damageSources().fall(); amount=10; }
        }
        npc.hurtServer(level,source,amount); near(h,35-npc.getHealth(),expected,"Original intrinsic "+kind+" armor"); cleanup(h,npc); h.succeed();
    }
    private static LootTable table(GameTestHelper h) { return h.getLevel().getServer().reloadableRegistries().getLootTable(NpcContent.SUPER_MUTANT.get().getDefaultLootTable().orElseThrow()); }
    private static LootParams params(GameTestHelper h,SuperMutant npc,Player player) {
        return new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.THIS_ENTITY,npc).withParameter(LootContextParams.ORIGIN,npc.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE,h.getLevel().damageSources().playerAttack(player))
                .withParameter(LootContextParams.ATTACKING_ENTITY,player).withParameter(LootContextParams.LAST_DAMAGE_PLAYER,player).create(LootContextParamSets.ENTITY);
    }
    private static RandomSource fixed(float chance) { return new LegacyRandomSource(9) { @Override public float nextFloat() { return chance; } }; }
    private static void looting(GameTestHelper h,Player player,int level) {
        var sword=new ItemStack(Items.DIAMOND_SWORD); if(level>0) sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),level);
        player.setItemInHand(InteractionHand.MAIN_HAND,sword);
    }
    private static int count(List<ItemStack> items,Item item) { return items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private static void lootProbability(GameTestHelper h) {
        var npc=mob(h,1,new Vec3(3,2,3)); var player=WeaponGameTests.player(h); var item=TGContent.MATERIALS.get("cyberneticparts").get();
        try {
            h.assertValueEqual(count(table(h).getRandomItems(params(h,npc,player),fixed(.77f)),item),0,".77 roll fails original .75 base chance");
            looting(h,player,1); int amount=count(table(h).getRandomItems(params(h,npc,player),fixed(.77f)),item);
            h.assertTrue(amount>=1 && amount<=2,"Looting I raises chance to .80 without increasing cyber quantity");
            looting(h,player,3); amount=count(table(h).getRandomItems(params(h,npc,player),fixed(.89f)),item);
            h.assertTrue(amount>=1 && amount<=2,"Looting III .90 chance still returns 1-2 cyber parts");
            h.assertValueEqual(count(table(h).getRandomItems(params(h,npc,player),fixed(.91f)),item),0,"Roll above .90 fails");
        } finally { cleanup(h,npc); } h.succeed();
    }
    private static void secondaryLoot(GameTestHelper h) {
        var npc=mob(h,1,new Vec3(3,2,3)); var player=WeaponGameTests.player(h); looting(h,player,3);
        try {
            var loot=table(h).getRandomItems(params(h,npc,player),fixed(.01f));
            for(var item:List.of(Items.BLAZE_ROD,Items.GHAST_TEAR)) h.assertTrue(count(loot,item)>=4 && count(loot,item)<=5,"Secondary drops retain looting quantity bonus");
        } finally { cleanup(h,npc); } h.succeed();
    }
    private static ItemStack material(String id) { return TGContent.MATERIALS.get(id).toStack(); }
    private static void deathCraft(GameTestHelper h) {
        var npc=mob(h,1,new Vec3(3,2,3)); var player=WeaponGameTests.player(h); var cyber=TGContent.MATERIALS.get("cyberneticparts").get();
        long seed=1; while(seed<10000 && count(table(h).getRandomItems(params(h,npc,player),seed),cyber)!=2) seed++;
        h.assertTrue(seed<10000,"A deterministic two-part drop seed exists");
        var data=save(h,npc); data.putLong("DeathLootTableSeed",seed); load(h,npc,data);
        npc.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY); npc.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(player),1000);
        try {
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,npc.getBoundingBox().inflate(3));
            int count=drops.stream().filter(e -> e.getItem().is(cyber)).mapToInt(e -> e.getItem().getCount()).sum();
            h.assertValueEqual(count,2,"Actual NPC death drops source cybernetic parts");
            var part=drops.stream().filter(e -> e.getItem().is(cyber)).findFirst().orElseThrow().getItem().copyWithCount(1);
            var steel=material("platesteel");
            var input=CraftingInput.of(3,3,List.of(steel,material("mechanicalpartscarbon"),steel,part,material("electricengine"),part.copy(),steel,material("circuitboardelite"),steel));
            var result=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input);
            h.assertTrue(result.is(techguns.modern.machine.fabricator.FabricatorContent.HOUSING_ITEM.get()),"Dropped parts satisfy original Fabricator housing recipe");
            h.assertValueEqual(result.getCount(),4,"Four original housings"); drops.forEach(Entity::discard);
        } finally { cleanup(h,npc); } h.succeed();
    }
    private static void spawnTables(GameTestHelper h) {
        for(var biome:h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).listElements().toList()) {
            var list=biome.value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap();
            h.assertTrue(list.stream().noneMatch(entry -> entry.value().type()==NpcContent.SUPER_MUTANT.get()),"No invented default natural spawn entry");
        } h.succeed();
    }
    private NpcGameTests() {}
}
