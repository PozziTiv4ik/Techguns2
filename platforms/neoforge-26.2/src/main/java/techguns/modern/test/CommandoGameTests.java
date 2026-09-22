package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
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

final class CommandoGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("commando_source_attributes_and_fixed_loadout",()->CommandoGameTests::attributes);
        r.register("commando_egg_and_native_equipment_save",()->CommandoGameTests::egg);
        for(boolean equipped:new boolean[]{false,true}) r.register("commando_intrinsic_armor_"+equipped,()->h->armor(h,equipped));
        r.register("commando_infiltrator_actual_projectile",()->CommandoGameTests::fire);
        r.register("commando_native_ranged_goal",()->CommandoGameTests::ai);
        r.register("commando_source_loot_chances_and_looting",()->CommandoGameTests::loot);
        r.register("commando_real_death_uses_own_loot",()->CommandoGameTests::death);
    }
    private static Commando mob(GameTestHelper h,Vec3 at) { var n=new Commando(NpcContent.COMMANDO.get(),h.getLevel()); n.equipForSpawn(); n.removeFreeWill(); n.setNoGravity(true); n.setPos(h.absoluteVec(at)); h.getLevel().addFreshEntity(n); return n; }
    private static void equipment(GameTestHelper h,Commando n) {
        h.assertTrue(n.getMainHandItem().is(TGContent.GUNS.get("m4_infiltrator").get()),"Fixed original Infiltrator");
        for(var slot:ArmorSlot.values()) h.assertTrue(n.getItemBySlot(EquipmentSlot.valueOf(slot.name())).is(ArmorContent.COMMANDO.get(slot).get()),"Guaranteed full T2 Commando suit");
    }
    private static void attributes(GameTestHelper h) {
        var n=mob(h,new Vec3(4,2,4)); equipment(h,n); n.tick();
        ArmorGameTests.near(h,n.getMaxHealth(),30,"Health"); ArmorGameTests.near(h,n.getAttributeValue(Attributes.MOVEMENT_SPEED),.35,"No player equipment speed on NPC");
        ArmorGameTests.near(h,n.getAttributeValue(Attributes.ATTACK_DAMAGE),5,"Melee"); ArmorGameTests.near(h,n.getAttributeValue(Attributes.FOLLOW_RANGE),75,"Follow range");
        ArmorGameTests.near(h,n.getAttributeValue(Attributes.ARMOR),8,"Intrinsic armor"); ArmorGameTests.near(h,n.getAttributeValue(techguns.modern.radiation.RadiationSystem.RESISTANCE),1,"Source equipment radiation attribute applies to all wearers");
        h.assertTrue(!n.is(EntityTypeTags.UNDEAD) && !n.is(EntityTypeTags.BURN_IN_DAYLIGHT) && !n.fireImmune() && !n.canPickUpLoot(),"Living GenericNPC policy");
        h.assertValueEqual(n.getAmbientSound(),net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT,"Source voice"); n.discard(); h.succeed();
    }
    private static void egg(GameTestHelper h) {
        var p=WeaponGameTests.player(h); var egg=NpcContent.COMMANDO_EGG.toStack(2); p.setItemInHand(InteractionHand.MAIN_HAND,egg); var at=new BlockPos(4,1,4); h.setBlock(at,Blocks.STONE); h.useBlock(at,p);
        var all=h.getLevel().getEntitiesOfClass(Commando.class,new AABB(h.absolutePos(at)).inflate(4)); h.assertValueEqual(all.size(),1,"Registered egg runs finalizeSpawn"); var n=all.getFirst(); equipment(h,n); h.assertValueEqual(egg.getCount(),1,"Survival egg consumption");
        h.assertValueEqual(GunItem.rounds(n.getMainHandItem()),Weapons.definition("m4_infiltrator").stats().capacity(),"Spawned gun is full");
        n.setCustomName(Component.literal("Saved commando")); n.getMainHandItem().set(TGContent.ROUNDS.get(),2); n.getItemBySlot(EquipmentSlot.CHEST).setDamageValue(700); n.setHealth(17);
        var copy=new Commando(NpcContent.COMMANDO.get(),h.getLevel()); NetherGameTests.load(h,copy,NetherGameTests.save(h,n));
        for(var slot:EquipmentSlot.VALUES) h.assertTrue(ItemStack.matches(n.getItemBySlot(slot),copy.getItemBySlot(slot)),"Native equipment save keeps ammo and wear");
        ArmorGameTests.near(h,copy.getHealth(),17,"Health save"); h.assertValueEqual(copy.getCustomName(),n.getCustomName(),"Name save"); n.discard(); h.succeed();
    }
    private static void armor(GameTestHelper h,boolean equipped) {
        var n=mob(h,new Vec3(4,2,4)); if(!equipped) for(var slot:ArmorSlot.values()) n.setItemSlot(EquipmentSlot.valueOf(slot.name()),ItemStack.EMPTY); n.tick();
        var b=new Bullet(TGContent.BULLET.get(),h.getLevel()); b.configure(Weapons.definition("ak47"));
        var source=ShotDamage.PLAYER.source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),b);
        n.hurtServer(h.getLevel(),source,9); ArmorGameTests.near(h,30-n.getHealth(),equipped?6.12:6.84,"Intrinsic eight armor with equipment toughness and source penetration");
        for(var slot:ArmorSlot.values()) h.assertValueEqual(n.getItemBySlot(EquipmentSlot.valueOf(slot.name())).getDamageValue(),0,"NPC does not use player armor wear"); n.discard(); h.succeed();
    }
    private static void fire(GameTestHelper h) {
        var n=mob(h,new Vec3(3,40,3)); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(6,40,3)); target.setNoGravity(true); n.setYHeadRot(-90); n.setXRot(0); n.getMainHandItem().set(TGContent.ROUNDS.get(),0);
        h.assertTrue(!n.fireAt(n) && n.fireAt(target),"NPC bypasses empty stored magazine, rejects self"); var shots=h.getLevel().getEntitiesOfClass(Bullet.class,n.getBoundingBox().inflate(4),b->b.getOwner()==n); h.assertValueEqual(shots.size(),1,"One Infiltrator bullet");
        var b=shots.getFirst(); h.assertValueEqual(b.weapon().id(),"m4_infiltrator","Correct bullet profile"); for(int t=0;t<8 && !b.isRemoved();t++) b.tick(); h.assertTrue(target.getHealth()<100,"Actual projectile hits");
        h.assertValueEqual(GunItem.rounds(n.getMainHandItem()),0,"NPC ammo remains unchanged"); b.discard(); target.discard(); n.discard(); h.succeed();
    }
    private static void ai(GameTestHelper h) {
        for(int x=1;x<11;x++) for(int z=1;z<11;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var n=new Commando(NpcContent.COMMANDO.get(),h.getLevel()); n.equipForSpawn(); n.setPos(h.absoluteVec(new Vec3(3,1,3))); h.getLevel().addFreshEntity(n); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(6,1,3)); target.setNoGravity(true); n.setTarget(target);
        h.runAfterDelay(75,()->{ h.assertTrue(target.getHealth()<100,"Native ranged goal fires Infiltrator"); h.getLevel().getEntitiesOfClass(Bullet.class,n.getBoundingBox().inflate(120),b->b.getOwner()==n).forEach(Entity::discard); target.discard(); n.discard(); h.succeed(); });
    }
    private static net.minecraft.world.level.storage.loot.LootTable table(GameTestHelper h) { return h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/commando"))); }
    private static LegacyRandomSource fixed(float roll) { return new LegacyRandomSource(1) { @Override public float nextFloat() { return roll; } @Override public int nextInt(int bound) { return bound-1; } }; }
    private static void loot(GameTestHelper h) {
        var n=mob(h,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); var items=List.of(TGContent.MATERIALS.get("ingotobsidiansteel").get(),TGContent.MATERIALS.get("rubberbar").get(),Items.GUNPOWDER,TGContent.AMMO.get("riflerounds").get(),TGContent.AMMO.get("shotgunrounds").get(),TGContent.AMMO.get("pistolrounds").get()); int[] base={2,2,2,7,3,3};
        for(int looting:new int[]{0,3}) {
            var sword=new ItemStack(Items.DIAMOND_SWORD); if(looting>0) sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),looting); p.setItemInHand(InteractionHand.MAIN_HAND,sword);
            var drops=table(h).getRandomItems(ZombieSoldierGameTests.params(h,n,p),fixed(0)); h.assertValueEqual(drops.size(),6,"Six source pools");
            for(int i=0;i<6;i++) { h.assertTrue(drops.get(i).is(items.get(i)),"Source item order"); h.assertValueEqual(drops.get(i).getCount(),base[i]+(i==0?0:looting),"Minimum roll adds one per Looting level to only five pools"); }
            h.assertTrue(table(h).getRandomItems(ZombieSoldierGameTests.params(h,n,p),fixed(looting==0?.21f:.51f)).isEmpty(),"Above all source chances");
            h.assertValueEqual(table(h).getRandomItems(ZombieSoldierGameTests.params(h,n,p),fixed(looting==0?.11f:.26f)).size(),5,"Lower chance of obsidian steel");
            if(looting>0) {
                h.assertValueEqual(table(h).getRandomItems(ZombieSoldierGameTests.params(h,n,p),fixed(.36f)).size(),3,"Only three ammunition pools have the .1 Looting chance increase");
                var max=new LegacyRandomSource(1) { private int draw; @Override public int nextInt(int bound) { return bound-1; } @Override public float nextFloat() { int n=draw++; return n>=2 && (n&1)==0?.999999f:0; } };
                var maximum=table(h).getRandomItems(ZombieSoldierGameTests.params(h,n,p),max); h.assertValueEqual(maximum.size(),6,"All chances pass while five bonus quantities roll high");
                for(int i=0;i<6;i++) h.assertValueEqual(maximum.get(i).getCount(),base[i]+(i==0?0:2*looting),"Maximum source Looting increase");
            }
        } n.discard(); h.succeed();
    }
    private static void death(GameTestHelper h) {
        var n=mob(h,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); long seed=1;
        while(seed<10000 && table(h).getRandomItems(ZombieSoldierGameTests.params(h,n,p),seed).isEmpty()) seed++;
        var expected=table(h).getRandomItems(ZombieSoldierGameTests.params(h,n,p),seed); h.assertTrue(!expected.isEmpty(),"Reproducible nonempty source loot");
        for(var slot:EquipmentSlot.VALUES) n.setDropChance(slot,0); var tag=NetherGameTests.save(h,n); tag.putLong("DeathLootTableSeed",seed); NetherGameTests.load(h,n,tag); n.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(p),1000);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,n.getBoundingBox().inflate(3)); h.assertValueEqual(drops.size(),expected.size(),"Actual death dispatches Commando table");
        for(var stack:expected) h.assertTrue(drops.stream().anyMatch(e->ItemStack.matches(stack,e.getItem())),"Actual source death drop"); drops.forEach(Entity::discard); n.discard(); h.succeed();
    }
    private CommandoGameTests() {}
}
