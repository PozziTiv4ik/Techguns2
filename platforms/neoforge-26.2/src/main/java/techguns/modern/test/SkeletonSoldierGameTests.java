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
import techguns.modern.machine.repair.*;

final class SkeletonSoldierGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("skeleton_attributes_armor_and_undead",() -> SkeletonSoldierGameTests::attributes);
        r.register("skeleton_all_equipment_rolls",() -> SkeletonSoldierGameTests::equipment);
        r.register("skeleton_egg_and_save",() -> SkeletonSoldierGameTests::egg);
        for(int roll=0;roll<3;roll++) {
            int choice=roll; r.register("skeleton_projectile_"+roll,() -> h -> fire(h,choice));
            r.register("skeleton_live_ai_"+roll,() -> h -> ai(h,choice));
        }
        r.register("skeleton_visibility_and_self_target",() -> SkeletonSoldierGameTests::visibility);
        r.register("skeleton_loot_chance_and_coal_exception",() -> SkeletonSoldierGameTests::loot);
        r.register("skeleton_death_loot_repairs_scout",() -> SkeletonSoldierGameTests::death);
        r.register("skeleton_sun_night_roof_and_both_helmets",() -> RuralDaylightGameTests::skeletonSunlight);
    }
    private static SkeletonSoldier mob(GameTestHelper h,int weapon,double helmet,double boots,Vec3 pos) {
        var npc=new SkeletonSoldier(NpcContent.SKELETON.get(),h.getLevel()); npc.equipRoll(weapon,helmet,boots);
        npc.removeFreeWill(); npc.setNoGravity(true); npc.setPos(h.absoluteVec(pos)); h.getLevel().addFreshEntity(npc); return npc;
    }
    private static void near(GameTestHelper h,double actual,double expected,String message) { ArmorGameTests.near(h,actual,expected,message); }
    private static void attributes(GameTestHelper h) {
        for(int gear=0;gear<4;gear++) {
            var npc=mob(h,0,(gear&1)==0?0:.9,(gear&2)==0?0:.9,new Vec3(4,2,4)); npc.tick();
            near(h,npc.getMaxHealth(),25,"Source health"); near(h,npc.getAttributeValue(Attributes.MOVEMENT_SPEED),.25,"No player-only Scout speed bonus");
            near(h,npc.getAttributeValue(Attributes.ATTACK_DAMAGE),4,"Source melee attribute"); near(h,npc.getAttributeValue(Attributes.FOLLOW_RANGE),50,"Source target range");
            near(h,npc.getBbWidth(),.6,"Source mob width"); near(h,npc.getBbHeight(),1.95,"Registered skeleton height"); near(h,npc.getEyeHeight(),1.6575,"Living eye-height factor");
            near(h,npc.getAttributeValue(Attributes.ARMOR_TOUGHNESS),Integer.bitCount(gear)*.5,"Only Combat pieces add native toughness");
            near(h,npc.getAttributeValue(Attributes.ARMOR),0,"No native armor from Scout or Combat");
            h.assertTrue(npc.is(EntityTypeTags.UNDEAD) && npc.isInvertedHealAndHarm() && npc.is(EntityTypeTags.SENSITIVE_TO_SMITE),"Source undead rules");
            h.assertTrue(!npc.addEffect(new MobEffectInstance(MobEffects.POISON,100)) && !npc.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100)),"Undead rejects poison and regeneration");
            h.assertTrue(!npc.canPickUpLoot() && !npc.fireImmune(),"No native skeleton pickup or fire immunity");
            var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47")); bullet.setOwner(WeaponGameTests.player(h));
            npc.hurtServer(h.getLevel(),ShotDamage.PLAYER.source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet),9);
            near(h,npc.getHealth(),16,"Actual bullet sees source zero intrinsic armor with either outfit");
            for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.FEET)) h.assertValueEqual(npc.getItemBySlot(slot).getDamageValue(),0,"NPC armor does not receive player special wear"); npc.discard();
        }
        h.succeed();
    }
    private static void equipment(GameTestHelper h) {
        var npc=mob(h,0,0,0,new Vec3(4,2,4));
        for(int weapon=0;weapon<3;weapon++) for(int gear=0;gear<4;gear++) {
            npc.setItemSlot(EquipmentSlot.CHEST,ArmorContent.T1_SCOUT.get(ArmorSlot.CHEST).toStack()); npc.setItemSlot(EquipmentSlot.LEGS,ArmorContent.T1_SCOUT.get(ArmorSlot.LEGS).toStack());
            npc.equipRoll(weapon,(gear&1)==0?.5:Math.nextUp(.5),(gear&2)==0?.5:Math.nextUp(.5));
            h.assertTrue(npc.armed() && npc.getMainHandItem().is(TGContent.GUNS.get(SkeletonSoldierRules.weapon(weapon)).get()),"Only the three reachable source guns");
            for(var slot:List.of(ArmorSlot.HEAD,ArmorSlot.FEET)) {
                var part=npc.getItemBySlot(EquipmentSlot.valueOf(slot.name())); boolean scout=(gear&(slot==ArmorSlot.HEAD?1:2))==0;
                h.assertTrue(part.is((scout?ArmorContent.T1_SCOUT:ArmorContent.T1_COMBAT).get(slot).get()),"Mandatory pieces use independent Scout/Combat draws");
                h.assertValueEqual(TGArmorItem.camo(part),0,"Source skeleton does not randomize camouflage");
            }
            h.assertTrue(npc.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && npc.getItemBySlot(EquipmentSlot.LEGS).isEmpty(),"Source leaves chest and legs empty");
        }
        near(h,npc.bulletSideOffset(),0,"Held-item translation does not affect bullet side offset"); near(h,npc.bulletHeightOffset(),0,"Held-item translation does not affect bullet height"); npc.discard(); h.succeed();
    }
    private static void egg(GameTestHelper h) {
        var p=WeaponGameTests.player(h); var egg=NpcContent.SKELETON_EGG.toStack(2); p.setItemInHand(InteractionHand.MAIN_HAND,egg);
        var pos=new BlockPos(4,1,4); h.setBlock(pos,Blocks.STONE); h.useBlock(pos,p);
        var mobs=h.getLevel().getEntitiesOfClass(SkeletonSoldier.class,new AABB(h.absolutePos(pos)).inflate(4));
        h.assertValueEqual(mobs.size(),1,"Egg creates correct skeleton"); h.assertValueEqual(egg.getCount(),1,"Survival egg consumed"); var npc=mobs.getFirst();
        h.assertTrue(npc.armed() && !npc.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !npc.getItemBySlot(EquipmentSlot.FEET).isEmpty(),"FinalizeSpawn equips gun and both parts");
        for(int weapon=0;weapon<3;weapon++) for(int gear=0;gear<4;gear++) {
            npc.equipRoll(weapon,(gear&1)==0?0:.9,(gear&2)==0?0:.9); npc.getMainHandItem().set(TGContent.ROUNDS.get(),0); npc.setCustomName(Component.literal("Skeleton save"));
            for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.FEET)) {
                var part=npc.getItemBySlot(slot); part.setDamageValue(700); if(((TGArmorItem)part.getItem()).spec().canChangeCamo()) TGArmorItem.setCamo(part,3);
            }
            var restored=new SkeletonSoldier(NpcContent.SKELETON.get(),h.getLevel()); NetherGameTests.load(h,restored,NetherGameTests.save(h,npc));
            for(var slot:List.of(EquipmentSlot.MAINHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))
                h.assertTrue(ItemStack.matches(npc.getItemBySlot(slot),restored.getItemBySlot(slot)),"Save retains weapon, empty ammo, empty slots, wear and changed camouflage");
            h.assertValueEqual(restored.getCustomName(),npc.getCustomName(),"Name survives reload");
        }
        npc.discard(); h.succeed();
    }
    private static void fire(GameTestHelper h,int weapon) {
        var npc=mob(h,weapon,0,0,new Vec3(3,40,3)); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(9,40,3)); target.setNoGravity(true);
        npc.setYHeadRot(-90); npc.setYRot(0); npc.setXRot(0); npc.getMainHandItem().set(TGContent.ROUNDS.get(),0);
        h.assertTrue(npc.fireAt(target),"Source NPC can fire without player ammunition");
        var bullets=h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(4),b -> b.getOwner()==npc); h.assertValueEqual(bullets.size(),1,"One source projectile");
        h.assertValueEqual(bullets.getFirst().weapon().id(),SkeletonSoldierRules.weapon(weapon),"Correct ballistic profile");
        for(var bullet:bullets) { for(int i=0;i<8 && !bullet.isRemoved();i++) bullet.tick(); bullet.discard(); }
        h.assertTrue(target.getHealth()<100,"Actual bullet collides with target"); h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),0,"NPC fire does not mutate saved ammo"); npc.discard(); target.discard(); h.succeed();
    }
    private static void ai(GameTestHelper h,int weapon) {
        for(int x=1;x<11;x++) for(int z=1;z<11;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var npc=new SkeletonSoldier(NpcContent.SKELETON.get(),h.getLevel()); npc.setPos(h.absoluteVec(new Vec3(3,1,3))); npc.equipRoll(weapon,0,.9); h.getLevel().addFreshEntity(npc);
        var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(8,1,3)); target.setNoGravity(true); npc.setTarget(target);
        h.runAfterDelay(75,() -> {
            h.assertTrue(target.getHealth()<100,"Actual ranged goal damages target with "+SkeletonSoldierRules.weapon(weapon));
            h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(120),b -> b.getOwner()==npc).forEach(Entity::discard); npc.discard(); target.discard(); h.succeed();
        });
    }
    private static void visibility(GameTestHelper h) {
        var npc=mob(h,0,0,0,new Vec3(3,2,3)); h.assertTrue(!npc.fireAt(npc),"Self cannot be a target");
        for(int y=2;y<6;y++) for(int z=2;z<5;z++) h.setBlock(new BlockPos(6,y,z),Blocks.STONE);
        var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(9,2,3));
        h.assertTrue(!npc.fireAt(target),"Opaque wall prevents firing"); npc.discard(); target.discard(); h.succeed();
    }
    static LootTable table(GameTestHelper h) { return h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/skeletonsoldier"))); }
    private static LootParams params(GameTestHelper h,SkeletonSoldier npc,Player p) { return ZombieSoldierGameTests.params(h,npc,p); }
    private static LegacyRandomSource fixed(float value) { return new LegacyRandomSource(1) { @Override public float nextFloat() { return value; } @Override public int nextInt(int bound) { return bound-1; } }; }
    private static void loot(GameTestHelper h) {
        var npc=mob(h,0,0,0,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); var table=table(h);
        var drops=table.getRandomItems(params(h,npc,p),fixed(.19f)); h.assertValueEqual(drops.size(),4,"Four independent source loot pools");
        List<Item> expected=List.of(Items.COAL,Items.GUNPOWDER,Items.BONE,TGContent.MATERIALS.get("heavycloth").get());
        for(int i=0;i<4;i++) h.assertTrue(drops.get(i).is(expected.get(i)) && drops.get(i).getCount()==2,"Source items and maximum base counts");
        h.assertTrue(table.getRandomItems(params(h,npc,p),fixed(.21f)).isEmpty(),"Base chance is twenty percent");
        var sword=new ItemStack(Items.DIAMOND_SWORD); sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),3); p.setItemInHand(InteractionHand.MAIN_HAND,sword);
        h.assertValueEqual(table.getRandomItems(params(h,npc,p),fixed(.34f)).size(),4,"Looting III raises every chance to .35");
        h.assertTrue(table.getRandomItems(params(h,npc,p),fixed(.36f)).isEmpty(),"No pool passes beyond .35");
        var enchanted=table.getRandomItems(params(h,npc,p),fixed(0));
        for(int i=0;i<4;i++) h.assertValueEqual(enchanted.get(i).getCount(),i==0?2:5,"Coal has no Looting quantity bonus; other pools gain three at the minimum draw"); npc.discard(); h.succeed();
    }
    private static void death(GameTestHelper h) {
        var npc=mob(h,0,0,0,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); var cloth=TGContent.MATERIALS.get("heavycloth").get(); long seed=1;
        while(seed<10000 && table(h).getRandomItems(params(h,npc,p),seed).stream().noneMatch(s -> s.is(cloth) && s.getCount()==2)) seed++;
        h.assertTrue(seed<10000,"Source two-cloth seed found"); var data=NetherGameTests.save(h,npc); data.putLong("DeathLootTableSeed",seed); NetherGameTests.load(h,npc,data);
        npc.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(p),1000);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,npc.getBoundingBox().inflate(3)); var materials=drops.stream().filter(e -> e.getItem().is(cloth) && e.getItem().getCount()==2).findFirst().orElseThrow().getItem().copy();
        var pos=new BlockPos(4,1,4); h.setBlock(pos,RepairBenchContent.BLOCK.get()); var bench=h.getBlockEntity(pos,RepairBenchBlockEntity.class); bench.setOwner(p);
        p.containerMenu=new RepairBenchMenu(98,p.getInventory(),bench); var mask=ArmorContent.T1_SCOUT.get(ArmorSlot.HEAD).toStack(); mask.setDamageValue(824); p.setItemSlot(EquipmentSlot.HEAD,mask); bench.setItem(0,materials);
        h.assertTrue(p.containerMenu.clickMenuButton(p,1),"Actual skeleton death loot repairs Scout mask"); h.assertTrue(mask.getDamageValue()==0 && bench.isEmpty(),"Exact source cloth repair cost consumed"); drops.forEach(Entity::discard); npc.discard(); h.succeed();
    }
    private SkeletonSoldierGameTests() {}
}
