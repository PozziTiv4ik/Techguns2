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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.armor.*;
import techguns.modern.npc.*;

final class PolicemanGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("policeman_attributes_undead_and_voice",()->PolicemanGameTests::attributes);
        r.register("policeman_all_32_equipment_combinations",()->PolicemanGameTests::equipment);
        r.register("policeman_egg_equipment_and_camo_save",()->PolicemanGameTests::egg);
        for(boolean equipped:List.of(false,true)) r.register("policeman_intrinsic_armor_"+equipped,()->h->armor(h,equipped));
        for(int w=0;w<2;w++) { int weapon=w; r.register("policeman_projectile_"+w,()->h->fire(h,weapon)); r.register("policeman_native_goal_"+w,()->h->ai(h,weapon)); }
        r.register("policeman_five_loot_pools_and_looting",()->PolicemanGameTests::loot);
        r.register("policeman_real_death_loot",()->PolicemanGameTests::death);
        r.register("policeman_day_night_helmet_roof_spawner",()->RuralDaylightGameTests::policeSunlight);
    }
    private static ZombiePoliceman mob(GameTestHelper h,int weapon,boolean armor,Vec3 pos) {
        var n=new ZombiePoliceman(NpcContent.POLICEMAN.get(),h.getLevel()); double roll=armor?.5:Math.nextUp(.5); n.equipRoll(weapon,roll,roll,roll,roll);
        n.removeFreeWill(); n.setNoGravity(true); n.setPos(h.absoluteVec(pos)); h.getLevel().addFreshEntity(n); return n;
    }
    private static void attributes(GameTestHelper h) {
        var n=mob(h,0,false,new Vec3(4,2,4)); n.tick();
        ArmorGameTests.near(h,n.getMaxHealth(),25,"Source health"); ArmorGameTests.near(h,n.getAttributeValue(Attributes.MOVEMENT_SPEED),.25,"Source speed");
        ArmorGameTests.near(h,n.getAttributeValue(Attributes.ATTACK_DAMAGE),4,"Source melee"); ArmorGameTests.near(h,n.getAttributeValue(Attributes.FOLLOW_RANGE),50,"Source follow range");
        h.assertTrue(n.is(EntityTypeTags.UNDEAD) && !n.fireImmune() && !n.canPickUpLoot(),"Original undead type without fire immunity or pickups");
        h.assertTrue(!n.addEffect(new MobEffectInstance(MobEffects.POISON,100)) && !n.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100)),"Undead native effect tags");
        h.assertValueEqual(n.getAmbientSound(),net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT,"Source voice"); n.discard(); h.succeed();
    }
    private static void equipment(GameTestHelper h) {
        var n=mob(h,0,false,new Vec3(4,2,4));
        for(int w=0;w<2;w++) for(int mask=0;mask<16;mask++) {
            double[] rolls=new double[4]; for(int i=0;i<4;i++) rolls[i]=(mask&(1<<i))!=0?.5:Math.nextUp(.5);
            n.equipRoll(w,rolls[0],rolls[1],rolls[2],rolls[3]); h.assertTrue(n.getMainHandItem().is(TGContent.GUNS.get(w==0?"revolver":"pistol").get()),"Both exact source weapon branches");
            int i=0; for(var slot:ArmorSlot.values()) { var stack=n.getItemBySlot(EquipmentSlot.valueOf(slot.name()));
                if((mask&(1<<i++))==0) h.assertTrue(stack.isEmpty(),"Independent failed armor roll clears old part");
                else { h.assertTrue(stack.is(ArmorContent.ITEMS.get(slot).get()),"Original T2 Combat part"); h.assertValueEqual(TGArmorItem.camo(stack),5,"Police camo five on each successful roll"); }
            }
        } n.discard(); h.succeed();
    }
    private static void egg(GameTestHelper h) {
        var p=WeaponGameTests.player(h); var egg=NpcContent.POLICEMAN_EGG.toStack(2); p.setItemInHand(InteractionHand.MAIN_HAND,egg); var at=new BlockPos(4,1,4); h.setBlock(at,Blocks.STONE); h.useBlock(at,p);
        var all=h.getLevel().getEntitiesOfClass(ZombiePoliceman.class,new AABB(h.absolutePos(at)).inflate(4)); h.assertValueEqual(all.size(),1,"Native spawn egg creates species"); var n=all.getFirst();
        h.assertTrue(n.armed(),"FinalizeSpawn equips handgun"); h.assertValueEqual(egg.getCount(),1,"Survival egg consumed");
        n.equipRoll(1,0,0,0,0); n.getMainHandItem().set(TGContent.ROUNDS.get(),2); n.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(123); n.setHealth(17);
        var copy=new ZombiePoliceman(NpcContent.POLICEMAN.get(),h.getLevel()); NetherGameTests.load(h,copy,NetherGameTests.save(h,n));
        for(var slot:EquipmentSlot.VALUES) h.assertTrue(ItemStack.matches(n.getItemBySlot(slot),copy.getItemBySlot(slot)),"Ammo, camo and wear persist with native entity save");
        ArmorGameTests.near(h,copy.getHealth(),17,"Saved health"); n.discard(); h.succeed();
    }
    private static void armor(GameTestHelper h,boolean equipped) {
        var n=mob(h,0,equipped,new Vec3(4,2,4)); n.tick(); var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47"));
        n.hurtServer(h.getLevel(),ShotDamage.PLAYER.source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet),9);
        ArmorGameTests.near(h,25-n.getHealth(),equipped?7.2:7.92,"Intrinsic five armor, AK penetration and native T2 toughness");
        for(var slot:ArmorSlot.values()) h.assertValueEqual(n.getItemBySlot(EquipmentSlot.valueOf(slot.name())).getDamageValue(),0,"Player armor wear does not apply to NPC"); n.discard(); h.succeed();
    }
    private static void fire(GameTestHelper h,int weapon) {
        var n=mob(h,weapon,false,new Vec3(3,40,3)); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(6,40,3)); target.setNoGravity(true); n.setYHeadRot(-90); n.setXRot(0); n.getMainHandItem().set(TGContent.ROUNDS.get(),0);
        h.assertTrue(!n.fireAt(n) && n.fireAt(target),"Native NPC fire path"); var shots=h.getLevel().getEntitiesOfClass(Bullet.class,n.getBoundingBox().inflate(4),b->b.getOwner()==n); h.assertValueEqual(shots.size(),1,"One actual bullet"); var b=shots.getFirst();
        h.assertValueEqual(b.weapon().id(),weapon==0?"revolver":"pistol","Correct handgun profile"); for(int t=0;t<8 && !b.isRemoved();t++) b.tick(); h.assertTrue(target.getHealth()<100,"Projectile collides with target");
        h.assertValueEqual(GunItem.rounds(n.getMainHandItem()),0,"Source NPC does not consume player ammunition"); b.discard(); target.discard(); n.discard(); h.succeed();
    }
    private static void ai(GameTestHelper h,int weapon) {
        for(int x=1;x<11;x++) for(int z=1;z<11;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var n=new ZombiePoliceman(NpcContent.POLICEMAN.get(),h.getLevel()); n.equipRoll(weapon,0,0,0,0); n.setPos(h.absoluteVec(new Vec3(3,1,3))); h.getLevel().addFreshEntity(n);
        var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(6,1,3)); target.setNoGravity(true); n.setTarget(target);
        h.runAfterDelay(75,()->{ h.assertTrue(target.getHealth()<100,"Native ranged goal uses selected handgun"); h.getLevel().getEntitiesOfClass(Bullet.class,n.getBoundingBox().inflate(120),b->b.getOwner()==n).forEach(Entity::discard); target.discard(); n.discard(); h.succeed(); });
    }
    private static net.minecraft.world.level.storage.loot.LootTable table(GameTestHelper h) { return h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/zombiepoliceman"))); }
    private static LegacyRandomSource fixed(float roll) { return new LegacyRandomSource(1) { @Override public float nextFloat() { return roll; } @Override public int nextInt(int bound) { return bound-1; } }; }
    private static void loot(GameTestHelper h) {
        var n=mob(h,0,false,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); var items=List.of(TGContent.MATERIALS.get("heavycloth").get(),Items.GUNPOWDER,Items.ROTTEN_FLESH,TGContent.AMMO.get("pistolrounds").get(),Items.IRON_INGOT); int[] base={2,2,1,4,2};
        for(int looting:new int[]{0,3}) {
            var sword=new ItemStack(Items.DIAMOND_SWORD); if(looting>0) sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),looting); p.setItemInHand(InteractionHand.MAIN_HAND,sword);
            var drops=table(h).getRandomItems(ZombieSoldierGameTests.params(h,n,p),fixed(0)); h.assertValueEqual(drops.size(),5,"Five original loot pools");
            for(int i=0;i<5;i++) { h.assertTrue(drops.get(i).is(items.get(i)),"Correct original item"); h.assertValueEqual(drops.get(i).getCount(),base[i]+(i==0?0:looting),"Source base quantities and minimum Looting increase"); }
            h.assertTrue(table(h).getRandomItems(ZombieSoldierGameTests.params(h,n,p),fixed(looting==0?.21f:.51f)).isEmpty(),"Above all source chances");
            if(looting>0) { var ammo=table(h).getRandomItems(ZombieSoldierGameTests.params(h,n,p),fixed(.36f)); h.assertValueEqual(ammo.size(),1,"Only ammunition has larger Looting chance"); h.assertTrue(ammo.getFirst().is(items.get(3)),"Pistol ammunition pool"); }
        } n.discard(); h.succeed();
    }
    private static void death(GameTestHelper h) {
        var n=mob(h,0,false,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); long seed=1;
        while(seed<10000 && table(h).getRandomItems(ZombieSoldierGameTests.params(h,n,p),seed).isEmpty()) seed++;
        var expected=table(h).getRandomItems(ZombieSoldierGameTests.params(h,n,p),seed); h.assertTrue(!expected.isEmpty(),"Nonempty original loot seed");
        for(var slot:EquipmentSlot.VALUES) n.setDropChance(slot,0); var tag=NetherGameTests.save(h,n); tag.putLong("DeathLootTableSeed",seed); NetherGameTests.load(h,n,tag); n.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(p),1000);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,n.getBoundingBox().inflate(3)); h.assertValueEqual(drops.size(),expected.size(),"Actual death uses species loot table"); for(var stack:expected) h.assertTrue(drops.stream().anyMatch(e->ItemStack.matches(stack,e.getItem())),"Actual loot content"); drops.forEach(Entity::discard); n.discard(); h.succeed();
    }
    private PolicemanGameTests() {}
}
