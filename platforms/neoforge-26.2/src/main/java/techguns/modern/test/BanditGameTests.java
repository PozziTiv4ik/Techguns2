package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.npc.*;
import techguns.modern.armor.*;

final class BanditGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("bandit_living_attributes_and_sounds",() -> BanditGameTests::attributes);
        r.register("bandit_intrinsic_armor_and_player_bonus_isolation",() -> BanditGameTests::armor);
        r.register("bandit_all_equipment_rolls",() -> BanditGameTests::equipment);
        r.register("bandit_egg_and_save",() -> BanditGameTests::egg);
        for(int roll=0;roll<6;roll++) {
            int choice=roll; r.register("bandit_projectile_"+roll,() -> h -> fire(h,choice));
            r.register("bandit_live_ai_"+roll,() -> h -> ai(h,choice));
        }
        r.register("bandit_loot_chance_and_cloth_exception",() -> BanditGameTests::loot);
        for(int ammo=0;ammo<3;ammo++) { int choice=ammo; r.register("bandit_death_reload_and_fire_"+ammo,() -> h -> death(h,choice)); }
        r.register("bandit_daylight_with_and_without_mask",() -> RuralDaylightGameTests::banditSunlight);
    }
    private static Bandit mob(GameTestHelper h,int weapon,double helmet,Vec3 pos) {
        var npc=new Bandit(NpcContent.BANDIT.get(),h.getLevel()); npc.equipRoll(weapon,helmet);
        npc.removeFreeWill(); npc.setNoGravity(true); npc.setPos(h.absoluteVec(pos)); h.getLevel().addFreshEntity(npc); return npc;
    }
    private static void near(GameTestHelper h,double actual,double expected,String message) { ArmorGameTests.near(h,actual,expected,message); }
    private static void attributes(GameTestHelper h) {
        var npc=mob(h,0,0,new Vec3(4,2,4)); npc.tick();
        near(h,npc.getMaxHealth(),20,"Source health"); near(h,npc.getAttributeValue(Attributes.MOVEMENT_SPEED),.3,"Source speed without player Scout bonus");
        near(h,npc.getAttributeValue(Attributes.ATTACK_DAMAGE),5,"Source melee attribute"); near(h,npc.getAttributeValue(Attributes.FOLLOW_RANGE),40,"Source target range");
        h.assertTrue(!npc.is(EntityTypeTags.UNDEAD) && !npc.isInvertedHealAndHarm() && !npc.is(EntityTypeTags.SENSITIVE_TO_SMITE),"Bandit retains living physiology");
        h.assertTrue(npc.addEffect(new MobEffectInstance(MobEffects.POISON,100)) && npc.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100)),"Poison and regeneration work on a living bandit"); npc.removeAllEffects();
        h.assertValueEqual(npc.getAmbientSound(),SoundEvents.VILLAGER_AMBIENT,"Source GenericNPC ambient sound");
        h.assertTrue(!npc.canPickUpLoot() && !npc.fireImmune(),"No pickup or invented fire immunity");
        h.assertTrue(npc.hurtServer(h.getLevel(),h.getLevel().damageSources().lava(),10) && npc.getHealth()<20,"Bandit still takes actual lava damage"); npc.discard(); h.succeed();
    }
    private static void armor(GameTestHelper h) {
        for(double helmet:new double[]{0,.9}) for(String kind:List.of("bullet","fire","acid")) {
            var npc=mob(h,0,helmet,new Vec3(4,2,4)); npc.tick();
            near(h,npc.getAttributeValue(Attributes.ARMOR),5,"Intrinsic armor is independent of the Scout outfit");
            near(h,npc.getAttributeValue(Attributes.ARMOR_TOUGHNESS),0,"Scout has no native toughness");
            near(h,npc.getAttributeValue(Attributes.MOVEMENT_SPEED),.3,"Three or four Scout pieces do not accelerate the NPC");
            near(h,npc.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,"No player knockback modifier");
            near(h,npc.armorAgainst(DamageKind.ENERGY),2.5,"Source half armor against energy");
            DamageSource source;
            if(kind.equals("bullet")) {
                var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47")); bullet.setOwner(WeaponGameTests.player(h));
                source=ShotDamage.PLAYER.source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet);
            } else {
                var key=kind.equals("fire")?NetherBlasterProjectile.DAMAGE_TYPE:techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE;
                source=new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key));
            }
            npc.hurtServer(h.getLevel(),source,kind.equals("bullet")?9:10);
            near(h,20-npc.getHealth(),kind.equals("bullet")?7.92:kind.equals("fire")?9:10,"Actual incoming damage follows source intrinsic protection");
            for(var slot:ArmorSlot.values()) h.assertValueEqual(npc.getItemBySlot(EquipmentSlot.valueOf(slot.name())).getDamageValue(),0,"No player special armor wear on NPC"); npc.discard();
        }
        h.succeed();
    }
    private static void equipment(GameTestHelper h) {
        var npc=mob(h,0,0,new Vec3(4,2,4));
        for(int weapon=0;weapon<6;weapon++) for(boolean mask:new boolean[]{true,false}) {
            npc.equipRoll(weapon,mask?.5:Math.nextUp(.5));
            h.assertTrue(npc.armed() && npc.getMainHandItem().is(TGContent.GUNS.get(BanditRules.weapon(weapon)).get()),"All six reachable source weapons");
            for(var slot:ArmorSlot.values()) {
                var part=npc.getItemBySlot(EquipmentSlot.valueOf(slot.name()));
                if(slot==ArmorSlot.HEAD && !mask) h.assertTrue(part.isEmpty(),"Failed mask roll clears the previous helmet");
                else { h.assertTrue(part.is(ArmorContent.T1_SCOUT.get(slot).get()),"Correct mandatory Scout part"); h.assertValueEqual(TGArmorItem.camo(part),0,"Source bandit uses default camouflage"); }
            }
        }
        npc.discard(); h.succeed();
    }
    private static void egg(GameTestHelper h) {
        var p=WeaponGameTests.player(h); var egg=NpcContent.BANDIT_EGG.toStack(2); p.setItemInHand(InteractionHand.MAIN_HAND,egg);
        var pos=new BlockPos(4,1,4); h.setBlock(pos,Blocks.STONE); h.useBlock(pos,p);
        var mobs=h.getLevel().getEntitiesOfClass(Bandit.class,new AABB(h.absolutePos(pos)).inflate(4));
        h.assertValueEqual(mobs.size(),1,"Egg creates a bandit"); h.assertValueEqual(egg.getCount(),1,"Survival egg consumed"); var npc=mobs.getFirst();
        h.assertTrue(npc.armed() && !npc.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && !npc.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && !npc.getItemBySlot(EquipmentSlot.FEET).isEmpty(),"FinalizeSpawn equips gun and mandatory clothing");
        for(int weapon=0;weapon<6;weapon++) for(boolean mask:new boolean[]{true,false}) {
            npc.equipRoll(weapon,mask?0:.9); npc.getMainHandItem().set(TGContent.ROUNDS.get(),0); npc.setCustomName(Component.literal("Bandit save"));
            for(var slot:ArmorSlot.values()) {
                var part=npc.getItemBySlot(EquipmentSlot.valueOf(slot.name())); if(!part.isEmpty()) { part.setDamageValue(700); TGArmorItem.setCamo(part,3); }
            }
            var restored=new Bandit(NpcContent.BANDIT.get(),h.getLevel()); NetherGameTests.load(h,restored,NetherGameTests.save(h,npc));
            for(var slot:List.of(EquipmentSlot.MAINHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))
                h.assertTrue(ItemStack.matches(npc.getItemBySlot(slot),restored.getItemBySlot(slot)),"Save retains gun, empty ammo, missing mask, wear and modified camouflage");
            h.assertValueEqual(restored.getCustomName(),npc.getCustomName(),"Name survives reload");
        }
        npc.discard(); h.succeed();
    }
    private static void fire(GameTestHelper h,int weapon) {
        var npc=mob(h,weapon,0,new Vec3(3,40,3)); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(6,40,3)); target.setNoGravity(true);
        npc.setYHeadRot(-90); npc.setYRot(0); npc.setXRot(0); npc.getMainHandItem().set(TGContent.ROUNDS.get(),0);
        h.assertTrue(!npc.fireAt(npc),"Self-target rejected"); h.assertTrue(npc.fireAt(target),"Source NPC fire bypasses player ammo consumption");
        var definition=Weapons.definition(BanditRules.weapon(weapon));
        var bullets=h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(4),b -> b.getOwner()==npc);
        h.assertValueEqual(bullets.size(),definition.projectileCount(),"Correct projectile count, including all eight sawed-off pellets");
        for(var bullet:bullets) { h.assertValueEqual(bullet.weapon().id(),definition.id(),"Correct ballistic profile"); for(int i=0;i<8 && !bullet.isRemoved();i++) bullet.tick(); bullet.discard(); }
        h.assertTrue(target.getHealth()<100,"Actual projectile hits the target"); h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),0,"NPC fire leaves stored ammo unchanged"); npc.discard(); target.discard(); h.succeed();
    }
    private static void ai(GameTestHelper h,int weapon) {
        for(int x=1;x<11;x++) for(int z=1;z<11;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var npc=new Bandit(NpcContent.BANDIT.get(),h.getLevel()); npc.setPos(h.absoluteVec(new Vec3(3,1,3))); npc.equipRoll(weapon,.9); h.getLevel().addFreshEntity(npc);
        var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(6,1,3)); target.setNoGravity(true); npc.setTarget(target);
        h.runAfterDelay(75,() -> {
            h.assertTrue(target.getHealth()<100,"Real ranged goal damages target with "+BanditRules.weapon(weapon));
            h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(120),b -> b.getOwner()==npc).forEach(Entity::discard); npc.discard(); target.discard(); h.succeed();
        });
    }
    private static LootTable table(GameTestHelper h) { return h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/bandit"))); }
    private static LootParams params(GameTestHelper h,Bandit npc,Player p) { return ZombieSoldierGameTests.params(h,npc,p); }
    private static LegacyRandomSource fixed(float value) { return new LegacyRandomSource(1) { @Override public float nextFloat() { return value; } @Override public int nextInt(int bound) { return bound-1; } }; }
    private static void loot(GameTestHelper h) {
        var npc=mob(h,0,0,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); var table=table(h);
        List<Item> expected=List.of(TGContent.MATERIALS.get("heavycloth").get(),Items.IRON_INGOT,Items.GUNPOWDER,TGContent.AMMO.get("shotgunrounds").get(),TGContent.AMMO.get("pistolrounds").get(),TGContent.AMMO.get("riflerounds").get()); int[] counts={2,2,2,7,7,3};
        var drops=table.getRandomItems(params(h,npc,p),fixed(.19f)); h.assertValueEqual(drops.size(),6,"Six independent source pools");
        for(int i=0;i<6;i++) h.assertTrue(drops.get(i).is(expected.get(i)) && drops.get(i).getCount()==counts[i],"Source loot IDs and base quantities");
        h.assertTrue(table.getRandomItems(params(h,npc,p),fixed(.21f)).isEmpty(),"Base chance is .2");
        var sword=new ItemStack(Items.DIAMOND_SWORD); sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),3); p.setItemInHand(InteractionHand.MAIN_HAND,sword);
        h.assertValueEqual(table.getRandomItems(params(h,npc,p),fixed(.34f)).size(),6,"Looting III raises materials to .35 and ammo to .5");
        var ammo=table.getRandomItems(params(h,npc,p),fixed(.36f)); h.assertValueEqual(ammo.size(),3,"Only ammunition passes .36");
        for(int i=0;i<3;i++) h.assertTrue(ammo.get(i).is(expected.get(i+3)),"Correct three ammunition pools");
        h.assertTrue(table.getRandomItems(params(h,npc,p),fixed(.51f)).isEmpty(),"No pool passes above .5");
        var enchanted=table.getRandomItems(params(h,npc,p),fixed(0));
        for(int i=0;i<6;i++) h.assertValueEqual(enchanted.get(i).getCount(),counts[i]+(i==0?0:3),"Cloth has no Looting quantity bonus; other pools gain three at the minimum draw"); npc.discard(); h.succeed();
    }
    private static void death(GameTestHelper h,int choice) {
        String ammoId=new String[]{"pistolrounds","shotgunrounds","riflerounds"}[choice],gunId=new String[]{"revolver","sawedoff","boltaction"}[choice];
        var npc=mob(h,0,0,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); p.getInventory().clearContent(); var ammoItem=TGContent.AMMO.get(ammoId).get(); int needed=choice==1?2:1; long seed=1;
        while(seed<10000 && table(h).getRandomItems(params(h,npc,p),seed).stream().noneMatch(s -> s.is(ammoItem) && s.getCount()>=needed)) seed++;
        h.assertTrue(seed<10000,"Seed for sufficient actual ammunition loot"); var data=NetherGameTests.save(h,npc); data.putLong("DeathLootTableSeed",seed); NetherGameTests.load(h,npc,data);
        npc.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(p),1000);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,npc.getBoundingBox().inflate(3)); var dropped=drops.stream().filter(e -> e.getItem().is(ammoItem) && e.getItem().getCount()>=needed).findFirst().orElseThrow();
        var ammo=dropped.getItem().copy(); dropped.discard(); int before=ammo.getCount(); p.getInventory().setItem(1,ammo);
        var gun=TGContent.GUNS.get(gunId).toStack(); gun.set(TGContent.ROUNDS.get(),0); p.setItemInHand(InteractionHand.MAIN_HAND,gun); var definition=Weapons.definition(gunId);
        h.assertTrue(ReloadSessions.begin(p),"Actual bandit ammunition starts player reload");
        for(int tick=0;tick<definition.stats().reloadTicks();tick++) p.tick();
        h.assertValueEqual(GunItem.rounds(gun),definition.stats().capacity(),"Full source reload completes"); h.assertValueEqual(ammo.getCount(),before-needed,"Exact ammunition quantity consumed");
        h.assertTrue(GunItem.fire(h.getLevel(),p,gun),"Player fires the gun loaded from bandit loot"); h.assertValueEqual(GunItem.rounds(gun),definition.stats().capacity()-1,"Player firing consumes one loaded round");
        var bullets=h.getLevel().getEntitiesOfClass(Bullet.class,p.getBoundingBox().inflate(4),b -> b.getOwner()==p); h.assertValueEqual(bullets.size(),definition.projectileCount(),"Player shot produces the actual projectiles");
        bullets.forEach(Entity::discard); drops.forEach(Entity::discard); npc.discard(); h.succeed();
    }
    private BanditGameTests() {}
}
