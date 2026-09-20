package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
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
import techguns.modern.npc.*;
import techguns.modern.armor.*;

final class ArmyGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("army_living_attributes_and_no_player_bonuses",()->ArmyGameTests::attributes);
        r.register("army_all_48_equipment_combinations",()->ArmyGameTests::equipment);
        r.register("army_random_equipment_independent_armor_and_weapons",()->ArmyGameTests::randomEquipment);
        r.register("army_egg_and_equipment_save",()->ArmyGameTests::egg);
        for(boolean armored:new boolean[]{false,true}) r.register("army_bullet_armor_"+armored,()->h->armor(h,armored));
        for(int i=0;i<3;i++) { int w=i; r.register("army_fire_"+w,()->h->fire(h,w)); r.register("army_native_ranged_goal_"+w,()->h->ai(h,w)); }
        r.register("army_daylight_with_and_without_beret",()->RuralDaylightGameTests::armySunlight);
        r.register("army_original_loot_chances_and_quantities",()->ArmyGameTests::loot);
        r.register("army_actual_death_uses_own_loot_table",()->ArmyGameTests::death);
    }
    private static ArmySoldier mob(GameTestHelper h,int weapon,boolean armored,Vec3 pos) {
        var npc=new ArmySoldier(NpcContent.ARMY.get(),h.getLevel()); npc.equipLoadout(weapon,armored,armored,armored,armored);
        npc.removeFreeWill(); npc.setNoGravity(true); npc.setPos(h.absoluteVec(pos)); h.getLevel().addFreshEntity(npc); return npc;
    }
    static void checkEquipment(GameTestHelper h,ArmySoldier npc) {
        h.assertTrue(ArmySoldier.WEAPONS.stream().anyMatch(id->npc.getMainHandItem().is(TGContent.GUNS.get(id).get())),"Source weapon choice");
        var gun=(GunItem)npc.getMainHandItem().getItem(); h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),gun.definition().stats().capacity(),"Fresh gun has full magazine");
        for(var slot:ArmorSlot.values()) {
            var item=npc.getItemBySlot(EquipmentSlot.valueOf(slot.name()));
            h.assertTrue(item.is(ArmorContent.ITEMS.get(slot).get()) || (slot==ArmorSlot.HEAD?item.is(ArmorContent.BERET.get()):item.isEmpty()),"Source armor pool and guaranteed headgear");
            if(!item.isEmpty()) h.assertValueEqual(TGArmorItem.camo(item),0,"Source default camouflage");
        }
    }
    private static void attributes(GameTestHelper h) {
        var npc=mob(h,0,true,new Vec3(4,2,4)); npc.tick();
        ArmorGameTests.near(h,npc.getMaxHealth(),25,"Original health"); ArmorGameTests.near(h,npc.getAttributeValue(Attributes.MOVEMENT_SPEED),.3,"Player armor speed does not apply to soldier");
        ArmorGameTests.near(h,npc.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),4,"Original melee"); ArmorGameTests.near(h,npc.getAttributeValue(Attributes.FOLLOW_RANGE),75,"Original follow range");
        ArmorGameTests.near(h,npc.getAttributeValue(Attributes.ARMOR),8,"Intrinsic armor, no player suit display modifier");
        h.assertTrue(!npc.is(EntityTypeTags.UNDEAD) && !npc.isInvertedHealAndHarm() && !npc.is(EntityTypeTags.SENSITIVE_TO_SMITE),"Living GenericNPC");
        h.assertTrue(npc.addEffect(new MobEffectInstance(MobEffects.POISON,100)) && npc.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100)),"Living effects accepted");
        h.assertTrue(!npc.fireImmune() && !npc.canPickUpLoot(),"Source fire vulnerability and equipment policy");
        h.assertValueEqual(npc.getAmbientSound(),net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT,"Source voice");
        for(var slot:EquipmentSlot.VALUES) npc.setItemSlot(slot,ItemStack.EMPTY);
        h.assertValueEqual(npc.getExperienceReward(h.getLevel(),null),5,"Inherited Monster base experience"); npc.discard(); h.succeed();
    }
    private static void equipment(GameTestHelper h) {
        var npc=mob(h,0,false,new Vec3(4,2,4));
        for(int weapon=0;weapon<3;weapon++) for(int mask=0;mask<16;mask++) {
            npc.equipLoadout(weapon,(mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0); checkEquipment(h,npc);
            h.assertTrue(npc.getMainHandItem().is(TGContent.GUNS.get(ArmySoldier.WEAPONS.get(weapon)).get()),"Exact selected gun");
            for(int n=0;n<4;n++) { var slot=ArmorSlot.values()[n]; var stack=npc.getItemBySlot(EquipmentSlot.valueOf(slot.name()));
                boolean present=(mask&(1<<n))!=0;
                h.assertTrue(present?stack.is(ArmorContent.ITEMS.get(slot).get()):slot==ArmorSlot.HEAD?stack.is(ArmorContent.BERET.get()):stack.isEmpty(),"Failed armor roll clears previous equipment");
            }
        }
        npc.discard(); h.succeed();
    }
    private static void randomEquipment(GameTestHelper h) {
        var npc=mob(h,0,false,new Vec3(4,2,4)); npc.getRandom().setSeed(271828); int[] armor=new int[4], weapons=new int[3]; var combinations=new HashSet<Integer>();
        for(int i=0;i<1024;i++) {
            npc.equipForSpawn(); checkEquipment(h,npc); int mask=0;
            for(int n=0;n<4;n++) { var s=ArmorSlot.values()[n]; if(npc.getItemBySlot(EquipmentSlot.valueOf(s.name())).is(ArmorContent.ITEMS.get(s).get())) { armor[n]++; mask|=1<<n; } }
            combinations.add(mask); for(int n=0;n<3;n++) if(npc.getMainHandItem().is(TGContent.GUNS.get(ArmySoldier.WEAPONS.get(n)).get())) weapons[n]++;
        }
        h.assertValueEqual(combinations.size(),16,"Independent rolls can produce every armor subset");
        for(int count:armor) h.assertTrue(count>400 && count<624,"Deterministic seeded half-chance distribution");
        for(int count:weapons) h.assertTrue(count>250 && count<430,"Deterministic seeded equal three-way weapon choice"); npc.discard(); h.succeed();
    }
    private static void egg(GameTestHelper h) {
        var p=WeaponGameTests.player(h); var egg=NpcContent.ARMY_EGG.toStack(2); p.setItemInHand(InteractionHand.MAIN_HAND,egg); var pos=new BlockPos(4,1,4); h.setBlock(pos,Blocks.STONE); h.useBlock(pos,p);
        var mobs=h.getLevel().getEntitiesOfClass(ArmySoldier.class,new AABB(h.absolutePos(pos)).inflate(4)); h.assertValueEqual(mobs.size(),1,"Actual soldier spawn egg");
        var npc=mobs.getFirst(); checkEquipment(h,npc); h.assertValueEqual(egg.getCount(),1,"Survival egg consumed");
        for(boolean helmet:new boolean[]{false,true}) {
            npc.equipLoadout(2,helmet,true,false,true); npc.setCustomName(Component.literal("Saved soldier")); npc.getMainHandItem().set(TGContent.ROUNDS.get(),0);
            var head=npc.getItemBySlot(EquipmentSlot.HEAD); TGArmorItem.setCamo(head,2); head.setDamageValue(700);
            var restored=new ArmySoldier(NpcContent.ARMY.get(),h.getLevel()); NetherGameTests.load(h,restored,NetherGameTests.save(h,npc));
            for(var slot:EquipmentSlot.VALUES) h.assertTrue(ItemStack.matches(npc.getItemBySlot(slot),restored.getItemBySlot(slot)),"NBT retains ammo, missing legs, helmet/beret color and wear");
            h.assertValueEqual(restored.getCustomName(),npc.getCustomName(),"Name restored");
        }
        npc.discard(); h.succeed();
    }
    private static void armor(GameTestHelper h,boolean armored) {
        var npc=mob(h,0,armored,new Vec3(4,2,4)); npc.tick();
        ArmorGameTests.near(h,npc.getAttributeValue(Attributes.ARMOR_TOUGHNESS),armored?4:0,"Native equipped T2 toughness");
        var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47"));
        var source=ShotDamage.PLAYER.source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet);
        npc.hurtServer(h.getLevel(),source,9); ArmorGameTests.near(h,25-npc.getHealth(),armored?6.12:6.84,"Intrinsic eight armor with caller penetration x4");
        for(var slot:ArmorSlot.values()) h.assertValueEqual(npc.getItemBySlot(EquipmentSlot.valueOf(slot.name())).getDamageValue(),0,"No player special armor wear on NPC");
        npc.invulnerableTime=0; npc.setHealth(25); var acid=new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE));
        npc.hurtServer(h.getLevel(),acid,10); ArmorGameTests.near(h,25-npc.getHealth(),10,"Default NPC armor does not block poison"); npc.discard(); h.succeed();
    }
    private static void fire(GameTestHelper h,int weapon) {
        var npc=mob(h,weapon,false,new Vec3(3,40,3)); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(6,40,3)); target.setNoGravity(true);
        npc.setYHeadRot(-90); npc.setYRot(0); npc.setXRot(0); npc.getMainHandItem().set(TGContent.ROUNDS.get(),0);
        h.assertTrue(!npc.fireAt(npc) && npc.fireAt(target),"Self rejected, empty stored NPC magazine still fires"); var definition=Weapons.definition(ArmySoldier.WEAPONS.get(weapon));
        var bullets=h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(4),b->b.getOwner()==npc); h.assertValueEqual(bullets.size(),definition.projectileCount(),"Source pellet count");
        for(var bullet:bullets) { h.assertValueEqual(bullet.weapon().id(),definition.id(),"Original ballistic profile"); for(int i=0;i<8 && !bullet.isRemoved();i++) bullet.tick(); bullet.discard(); }
        h.assertTrue(target.getHealth()<100,"Real projectile damage"); h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),0,"NPC does not consume stored ammo"); npc.discard(); target.discard(); h.succeed();
    }
    private static void ai(GameTestHelper h,int weapon) {
        for(int x=1;x<11;x++) for(int z=1;z<11;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var npc=new ArmySoldier(NpcContent.ARMY.get(),h.getLevel()); npc.equipLoadout(weapon,false,false,false,false); npc.setPos(h.absoluteVec(new Vec3(3,1,3))); h.getLevel().addFreshEntity(npc);
        var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(6,1,3)); target.setNoGravity(true); npc.setTarget(target);
        h.runAfterDelay(75,()->{ h.assertTrue(target.getHealth()<100,"Native ranged goal attacks with "+ArmySoldier.WEAPONS.get(weapon));
            h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(120),b->b.getOwner()==npc).forEach(Entity::discard); npc.discard(); target.discard(); h.succeed(); });
    }
    private static net.minecraft.world.level.storage.loot.LootTable table(GameTestHelper h) { return h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/armysoldier"))); }
    private static LegacyRandomSource fixed(float roll) { return new LegacyRandomSource(1) { @Override public float nextFloat() { return roll; } @Override public int nextInt(int bound) { return bound-1; } }; }
    private static void loot(GameTestHelper h) {
        var npc=mob(h,0,false,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); var table=table(h);
        List<Item> items=List.of(TGContent.MATERIALS.get("heavycloth").get(),Items.IRON_INGOT,Items.GUNPOWDER,TGContent.AMMO.get("shotgunrounds").get(),TGContent.AMMO.get("riflerounds").get()); int[] counts={2,2,2,7,3};
        for(int looting:new int[]{0,3}) {
            var sword=new ItemStack(Items.DIAMOND_SWORD); if(looting>0) sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),looting); p.setItemInHand(InteractionHand.MAIN_HAND,sword);
            var drops=table.getRandomItems(ZombieSoldierGameTests.params(h,npc,p),fixed(0)); h.assertValueEqual(drops.size(),5,"Five independent source pools");
            for(int i=0;i<5;i++) { h.assertTrue(drops.get(i).is(items.get(i)),"Original translated loot ID"); h.assertValueEqual(drops.get(i).getCount(),counts[i]+(i==0?0:looting),"Only non-cloth quantity grows with Looting"); }
            h.assertTrue(table.getRandomItems(ZombieSoldierGameTests.params(h,npc,p),fixed(looting==0?.21f:.51f)).isEmpty(),"All pools reject above chance");
            h.assertValueEqual(table.getRandomItems(ZombieSoldierGameTests.params(h,npc,p),fixed(looting==0?.19f:.34f)).size(),5,"All pools pass below material chance");
            if(looting>0) { var ammo=table.getRandomItems(ZombieSoldierGameTests.params(h,npc,p),fixed(.36f)); h.assertValueEqual(ammo.size(),2,"Only the two ammo pools have .1 Looting multiplier"); }
        }
        npc.discard(); h.succeed();
    }
    private static void death(GameTestHelper h) {
        var npc=mob(h,0,false,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); long seed=1;
        while(seed<10000 && table(h).getRandomItems(ZombieSoldierGameTests.params(h,npc,p),seed).isEmpty()) seed++;
        h.assertTrue(seed<10000,"Reproducible original loot seed"); var expected=table(h).getRandomItems(ZombieSoldierGameTests.params(h,npc,p),seed);
        for(var slot:EquipmentSlot.VALUES) npc.setDropChance(slot,0);
        var tag=NetherGameTests.save(h,npc); tag.putLong("DeathLootTableSeed",seed); NetherGameTests.load(h,npc,tag); npc.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(p),1000);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,npc.getBoundingBox().inflate(3));
        h.assertValueEqual(drops.size(),expected.size(),"Actual death dispatches ArmySoldier's table");
        for(var stack:expected) h.assertTrue(drops.stream().anyMatch(e->ItemStack.matches(stack,e.getItem())),"Death drop matches source pool"); drops.forEach(Entity::discard); npc.discard(); h.succeed();
    }
    private ArmyGameTests() {}
}
