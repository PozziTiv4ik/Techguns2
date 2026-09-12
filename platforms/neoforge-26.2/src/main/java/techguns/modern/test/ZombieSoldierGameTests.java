package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.npc.*;
import techguns.modern.armor.*;

final class ZombieSoldierGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("zombie_soldier_stats_undead_sun_and_fire",() -> ZombieSoldierGameTests::attributes);
        r.register("zombie_soldier_intrinsic_armor_with_t1",() -> ZombieSoldierGameTests::armor);
        r.register("zombie_soldier_independent_gear_rolls",() -> ZombieSoldierGameTests::rolls);
        r.register("zombie_soldier_egg_and_equipment_save",() -> ZombieSoldierGameTests::egg);
        for(int roll:List.of(0,1)) r.register("zombie_soldier_projectile_"+roll,() -> h -> fire(h,roll));
        for(int roll:List.of(2,3)) r.register("zombie_soldier_shovel_damage_"+roll,() -> h -> melee(h,roll));
        for(int roll=0;roll<4;roll++) { int choice=roll; r.register("zombie_soldier_live_ai_"+roll,() -> h -> ai(h,choice)); }
        r.register("zombie_soldier_live_ai_weapon_switch",() -> ZombieSoldierGameTests::switchWeapon);
        r.register("zombie_soldier_loot_chances_and_quantities",() -> ZombieSoldierGameTests::loot);
        r.register("zombie_soldier_actual_death_loot",() -> ZombieSoldierGameTests::death);
        OverworldSpawnGameTests.register(r);
    }
    private static ZombieSoldier mob(GameTestHelper h,int roll,Vec3 pos) {
        var npc=h.spawnWithNoFreeWill(NpcContent.ZOMBIE_SOLDIER.get(),pos); npc.setNoGravity(true); npc.equipRoll(roll,0,0,0,0); return npc;
    }
    private static void attributes(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(4,2,4)); npc.tick();
        ArmorGameTests.near(h,npc.getMaxHealth(),25,"Source health");
        ArmorGameTests.near(h,npc.getAttributeValue(Attributes.MOVEMENT_SPEED),.25,"Source movement");
        ArmorGameTests.near(h,npc.getAttributeValue(Attributes.ATTACK_DAMAGE),4,"Source base melee");
        ArmorGameTests.near(h,npc.getAttributeValue(Attributes.FOLLOW_RANGE),50,"Source follow range");
        h.assertTrue(npc.is(EntityTypeTags.UNDEAD) && npc.isInvertedHealAndHarm(),"Undead tags");
        h.assertTrue(!npc.addEffect(new MobEffectInstance(MobEffects.POISON,100)) && !npc.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100)),"Poison and regeneration rejected");
        h.assertTrue(!npc.is(EntityTypeTags.BURN_IN_DAYLIGHT) && !npc.fireImmune(),"Daylight exemption does not grant fire immunity");
        npc.equipRoll(0,.9,.9,.9,.9);
        for(int i=0;i<80;i++) npc.aiStep();
        h.assertTrue(!npc.isOnFire(),"Bareheaded soldier does not ignite from daylight AI");
        h.assertTrue(npc.hurtServer(h.getLevel(),h.getLevel().damageSources().lava(),10) && npc.getHealth()<25,"Actual lava damage accepted");
        h.assertTrue(!npc.canPickUpLoot(),"Spawn equipment cannot be replaced by ground items"); npc.discard(); h.succeed();
    }
    private static void armor(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(4,2,4)); npc.tick();
        ArmorGameTests.near(h,npc.getAttributeValue(Attributes.ARMOR),5,"Intrinsic armor, not player T1 HUD");
        ArmorGameTests.near(h,npc.getAttributeValue(Attributes.ARMOR_TOUGHNESS),2,"Four native half-point toughness modifiers");
        ArmorGameTests.near(h,npc.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,"T1 knockback bonus is player-only");
        var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47")); bullet.setOwner(WeaponGameTests.player(h));
        npc.hurtServer(h.getLevel(),ShotDamage.PLAYER.source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet),9);
        ArmorGameTests.near(h,npc.getHealth(),17.8,"Intrinsic five armor offsets AK penetration with T1 toughness");
        for(ArmorSlot slot:ArmorSlot.values()) h.assertValueEqual(npc.getItemBySlot(EquipmentSlot.valueOf(slot.name())).getDamageValue(),0,"Player armor wear does not leak to NPC");
        npc.discard(); h.succeed();
    }
    private static void rolls(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(4,2,4));
        for(int weapon=0;weapon<4;weapon++) {
            npc.equipRoll(weapon,.9,.5,.9,0);
            h.assertTrue(npc.getMainHandItem().is(BuiltInRegistries.ITEM.getValue(Identifier.parse(ZombieSoldierRules.weapon(weapon)))),"Four equal source weapon cases");
            h.assertTrue(npc.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && npc.getItemBySlot(EquipmentSlot.LEGS).isEmpty(),"Helmet and legs can independently fail");
            h.assertTrue(npc.getItemBySlot(EquipmentSlot.CHEST).is(ArmorContent.T1_COMBAT.get(ArmorSlot.CHEST).get()) && npc.getItemBySlot(EquipmentSlot.FEET).is(ArmorContent.T1_COMBAT.get(ArmorSlot.FEET).get()),"Inclusive .5 and zero draws equip T1");
            h.assertValueEqual(npc.armed(),weapon<2,"Shovels select melee goal");
        }
        npc.discard(); h.succeed();
    }
    private static void egg(GameTestHelper h) {
        var player=WeaponGameTests.player(h); var egg=NpcContent.SOLDIER_EGG.toStack(2); player.setItemInHand(InteractionHand.MAIN_HAND,egg);
        var pos=new BlockPos(4,1,4); h.setBlock(pos,Blocks.STONE); h.useBlock(pos,player);
        var mobs=h.getLevel().getEntitiesOfClass(ZombieSoldier.class,new AABB(h.absolutePos(pos)).inflate(4));
        h.assertValueEqual(mobs.size(),1,"Registered egg creates one soldier"); h.assertValueEqual(egg.getCount(),1,"Survival egg consumed");
        var npc=mobs.getFirst(); h.assertTrue(!npc.getMainHandItem().isEmpty(),"Spawn finalization rolls equipment");
        for(int roll=0;roll<4;roll++) {
            npc.equipRoll(roll,0,.9,.5,.9); npc.setCustomName(Component.literal("Soldier save"));
            if(roll<2) npc.getMainHandItem().set(TGContent.ROUNDS.get(),3); else npc.getMainHandItem().setDamageValue(12);
            npc.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(400);
            var restored=new ZombieSoldier(NpcContent.ZOMBIE_SOLDIER.get(),h.getLevel()); NetherGameTests.load(h,restored,NetherGameTests.save(h,npc));
            h.assertTrue(ItemStack.matches(npc.getMainHandItem(),restored.getMainHandItem()),"Load preserves gun rounds or shovel wear without reroll");
            h.assertValueEqual(restored.getItemBySlot(EquipmentSlot.HEAD).getDamageValue(),400,"Armor wear saved");
            h.assertTrue(restored.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),"Missing armor remains missing");
            h.assertValueEqual(restored.getCustomName(),npc.getCustomName(),"Name saved");
        }
        npc.discard(); h.succeed();
    }
    private static void fire(GameTestHelper h,int roll) {
        var npc=mob(h,roll,new Vec3(3,40,3)); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(9,40,3)); target.setNoGravity(true);
        npc.setYHeadRot(-90); npc.setYRot(0); npc.setXRot(0);
        h.assertTrue(npc.fireAt(target),"Soldier fires");
        var bullets=h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(4),b -> b.getOwner()==npc);
        h.assertValueEqual(bullets.size(),1,"One bullet");
        h.assertValueEqual("techguns:"+bullets.getFirst().weapon().id(),ZombieSoldierRules.weapon(roll),"Weapon-specific projectile");
        for(var bullet:bullets) { for(int i=0;i<4 && !bullet.isRemoved();i++) bullet.tick(); bullet.discard(); }
        h.assertTrue(target.getHealth()<100,"Real projectile collision damages target");
        h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),Weapons.definition(roll==0?"revolver":"thompson").stats().capacity(),"NPC ammo remains infinite");
        npc.discard(); target.discard(); h.succeed();
    }
    private static void melee(GameTestHelper h,int roll) {
        var npc=mob(h,roll,new Vec3(3,2,3)); npc.tick();
        var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(4,2,3)); target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        h.assertTrue(!npc.fireAt(target),"Shovel cannot create gun projectiles");
        h.assertTrue(npc.doHurtTarget(h.getLevel(),target),"Native melee attack succeeds");
        ArmorGameTests.near(h,100-target.getHealth(),roll==2?7.5:6.5,"Base four plus vanilla shovel attack modifier");
        npc.discard(); target.discard(); h.succeed();
    }
    private static ZombieSoldier active(GameTestHelper h,int roll) {
        for(int x=1;x<11;x++) for(int z=1;z<11;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var npc=new ZombieSoldier(NpcContent.ZOMBIE_SOLDIER.get(),h.getLevel()); npc.setPos(h.absoluteVec(new Vec3(3,1,3))); npc.equipRoll(roll,0,0,0,0); h.getLevel().addFreshEntity(npc); return npc;
    }
    private static void cleanup(GameTestHelper h,ZombieSoldier npc,LivingEntity target) {
        h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(120),b -> b.getOwner()==npc).forEach(Entity::discard); npc.discard(); target.discard();
    }
    private static void ai(GameTestHelper h,int roll) {
        var npc=active(h,roll); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(8,1,3)); target.setNoGravity(true); npc.setTarget(target);
        h.runAfterDelay(75,() -> {
            h.assertTrue(target.getHealth()<100,"Actual pathfinding/combat goal damages target for weapon "+roll);
            cleanup(h,npc,target); h.succeed();
        });
    }
    private static void switchWeapon(GameTestHelper h) {
        var npc=active(h,1); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(8,1,3)); target.setNoGravity(true); npc.setTarget(target);
        h.runAfterDelay(35,() -> {
            h.assertTrue(target.getHealth()<100,"Ranged AI acted before switching");
            target.setHealth(100); target.invulnerableTime=0;
            npc.equipRoll(2,0,0,0,0); npc.setPos(target.position().add(-1,0,0));
            h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(120),b -> b.getOwner()==npc).forEach(Entity::discard);
        });
        h.runAfterDelay(70,() -> { h.assertTrue(target.getHealth()<100,"Melee goal takes over after held item changes"); cleanup(h,npc,target); h.succeed(); });
    }
    static LootTable table(GameTestHelper h) { return h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/zombiesoldier"))); }
    static LootParams params(GameTestHelper h,LivingEntity npc,Player player) {
        return new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.THIS_ENTITY,npc).withParameter(LootContextParams.ORIGIN,npc.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE,h.getLevel().damageSources().playerAttack(player)).withParameter(LootContextParams.ATTACKING_ENTITY,player)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER,player).create(LootContextParamSets.ENTITY);
    }
    private static LegacyRandomSource fixed(float value) { return new LegacyRandomSource(1) { @Override public float nextFloat() { return value; } }; }
    private static void loot(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(4,2,4)); var player=WeaponGameTests.player(h); var table=table(h);
        var drops=table.getRandomItems(params(h,npc,player),fixed(.19f)); h.assertValueEqual(drops.size(),5,"All five source loot pools");
        h.assertTrue(table.getRandomItems(params(h,npc,player),fixed(.21f)).isEmpty(),"Base chance .2");
        var sword=new ItemStack(Items.DIAMOND_SWORD); sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),3); player.setItemInHand(InteractionHand.MAIN_HAND,sword);
        h.assertValueEqual(table.getRandomItems(params(h,npc,player),fixed(.34f)).size(),5,"Looting III .35 materials and .5 ammo");
        var ammo=table.getRandomItems(params(h,npc,player),fixed(.36f));
        h.assertValueEqual(ammo.size(),2,"Only ammo passes .36");
        h.assertTrue(ammo.stream().allMatch(s -> s.is(TGContent.AMMO.get("pistolrounds").get()) || s.is(TGContent.AMMO.get("shotgunrounds").get())),"Correct translated ammo metadata");
        h.assertTrue(table.getRandomItems(params(h,npc,player),fixed(.51f)).isEmpty(),"No pool passes above .5");
        npc.discard(); h.succeed();
    }
    private static void death(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(4,2,4)); var player=WeaponGameTests.player(h); var cloth=TGContent.MATERIALS.get("heavycloth").get();
        long seed=1; while(seed<10000 && table(h).getRandomItems(params(h,npc,player),seed).stream().noneMatch(s -> s.is(cloth))) seed++;
        h.assertTrue(seed<10000,"Source cloth seed found");
        var data=NetherGameTests.save(h,npc); data.putLong("DeathLootTableSeed",seed); NetherGameTests.load(h,npc,data);
        npc.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(player),1000);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,npc.getBoundingBox().inflate(3));
        h.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(cloth)),"Actual registered entity death uses translated loot table");
        drops.forEach(Entity::discard); npc.discard(); h.succeed();
    }
    private ZombieSoldierGameTests() {}
}
