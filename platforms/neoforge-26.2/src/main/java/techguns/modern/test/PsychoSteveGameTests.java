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

final class PsychoSteveGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("psycho_living_attributes_and_equipment_bonus_isolation",()->PsychoSteveGameTests::attributes);
        r.register("psycho_original_experience_base",()->PsychoSteveGameTests::experience);
        for(int camo=0;camo<4;camo++) { int c=camo; r.register("psycho_miner_camo_"+c,()->h->equipment(h,c)); }
        r.register("psycho_egg_and_save",()->PsychoSteveGameTests::egg);
        for(String kind:List.of("bullet","chainsaw","fire","acid")) r.register("psycho_armor_"+kind,()->h->armor(h,kind));
        r.register("psycho_physical_attack_and_range_guards",()->PsychoSteveGameTests::fire);
        r.register("psycho_native_chase_and_attack",()->PsychoSteveGameTests::ai);
        r.register("psycho_daylight_with_and_without_helmet",()->RuralDaylightGameTests::psychoSunlight);
        r.register("psycho_loot_chance_count_split_and_fuel",()->PsychoSteveGameTests::loot);
        r.register("psycho_death_fueled_saw_and_tank_cycle",()->PsychoSteveGameTests::death);
    }
    private static PsychoSteve mob(GameTestHelper h,int camo,Vec3 pos) {
        var npc=new PsychoSteve(NpcContent.PSYCHO.get(),h.getLevel()); npc.equipCamo(camo);
        npc.removeFreeWill(); npc.setNoGravity(true); npc.setPos(h.absoluteVec(pos)); h.getLevel().addFreshEntity(npc); return npc;
    }
    private static void near(GameTestHelper h,double actual,double expected,String message) { ArmorGameTests.near(h,actual,expected,message); }
    private static void attributes(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(4,2,4)); npc.tick();
        near(h,npc.getMaxHealth(),75,"Source health"); near(h,npc.getAttributeValue(Attributes.MOVEMENT_SPEED),.6,"Miner player speed bonus must not affect NPC");
        near(h,npc.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),7,"Source base melee damage"); near(h,npc.getAttributeValue(Attributes.ATTACK_DAMAGE),19,"Fueled saw adds its original 12 modifier");
        near(h,npc.getAttributeValue(Attributes.FOLLOW_RANGE),60,"Source follow range"); near(h,npc.getAttributeValue(Attributes.ARMOR_TOUGHNESS),1,"Own toughness survives equipment");
        h.assertTrue(!npc.is(EntityTypeTags.UNDEAD) && !npc.isInvertedHealAndHarm() && !npc.is(EntityTypeTags.SENSITIVE_TO_SMITE),"PsychoSteve is a living GenericNPC");
        h.assertTrue(npc.addEffect(new MobEffectInstance(MobEffects.POISON,100)) && npc.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100)),"Native living effects work");
        npc.removeAllEffects(); h.assertTrue(!npc.fireImmune() && !npc.canPickUpLoot(),"Source fire vulnerability and fixed equipment");
        h.assertValueEqual(npc.getAmbientSound(),net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT,"Source villager ambient voice"); npc.discard(); h.succeed();
    }
    private static void experience(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(4,2,4)); int equipped=npc.getExperienceReward(h.getLevel(),null);
        h.assertTrue(equipped>=30 && equipped<=40,"25 declared experience plus native five-item equipment bonus");
        for(var slot:EquipmentSlot.VALUES) npc.setItemSlot(slot,ItemStack.EMPTY);
        h.assertValueEqual(npc.getExperienceReward(h.getLevel(),null),25,"Unmodified source base experience"); npc.discard(); h.succeed();
    }
    private static void checkEquipment(GameTestHelper h,PsychoSteve npc,int camo) {
        h.assertTrue(npc.getMainHandItem().is(TGContent.GUNS.get("chainsaw").get()),"Always a Chainsaw");
        h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),300,"Fresh equipped saw is fueled"); h.assertValueEqual(ChainsawItem.head(npc.getMainHandItem()),0,"No invented blade upgrade");
        for(var slot:ArmorSlot.values()) {
            var stack=npc.getItemBySlot(EquipmentSlot.valueOf(slot.name())); h.assertTrue(stack.is(ArmorContent.T1_MINER.get(slot).get()),"Every original miner armor part equipped");
            h.assertValueEqual(TGArmorItem.camo(stack),camo,"One shared camo across all four parts");
        }
    }
    private static void equipment(GameTestHelper h,int camo) {
        var npc=mob(h,camo,new Vec3(4,2,4)); checkEquipment(h,npc,camo);
        try { npc.equipCamo(4); h.fail("Unknown camo must be rejected"); } catch(IllegalArgumentException expected) {}
        npc.discard(); h.succeed();
    }
    private static void egg(GameTestHelper h) {
        var p=WeaponGameTests.player(h); var egg=NpcContent.PSYCHO_EGG.toStack(2); p.setItemInHand(InteractionHand.MAIN_HAND,egg);
        var pos=new BlockPos(4,1,4); h.setBlock(pos,Blocks.STONE); h.useBlock(pos,p);
        var mobs=h.getLevel().getEntitiesOfClass(PsychoSteve.class,new AABB(h.absolutePos(pos)).inflate(4)); h.assertValueEqual(mobs.size(),1,"Spawn egg creates PsychoSteve");
        var npc=mobs.getFirst(); h.assertValueEqual(egg.getCount(),1,"Survival egg consumed"); checkEquipment(h,npc,TGArmorItem.camo(npc.getItemBySlot(EquipmentSlot.CHEST)));
        for(int camo=0;camo<4;camo++) {
            npc.equipCamo(camo); npc.getMainHandItem().set(TGContent.ROUNDS.get(),17); npc.getMainHandItem().set(TGContent.MINING_HEAD.get(),2); npc.setCustomName(Component.literal("Saved Psycho"));
            for(var slot:ArmorSlot.values()) npc.getItemBySlot(EquipmentSlot.valueOf(slot.name())).setDamageValue(700);
            var restored=new PsychoSteve(NpcContent.PSYCHO.get(),h.getLevel()); NetherGameTests.load(h,restored,NetherGameTests.save(h,npc));
            for(var slot:List.of(EquipmentSlot.MAINHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))
                h.assertTrue(ItemStack.matches(npc.getItemBySlot(slot),restored.getItemBySlot(slot)),"Save keeps fuel, upgraded blade, camouflage and wear");
            h.assertValueEqual(restored.getCustomName(),npc.getCustomName(),"Name survives reload");
        }
        npc.discard(); h.succeed();
    }
    private static void armor(GameTestHelper h,String kind) {
        var npc=mob(h,0,new Vec3(4,2,4)); npc.tick(); DamageSource source;
        if(kind.equals("bullet")) {
            var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47"));
            source=ShotDamage.PLAYER.source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet);
        } else if(kind.equals("chainsaw")) {
            var attack=new ChainsawAttack(TGContent.CHAINSAW_ATTACK.get(),h.getLevel()); attack.configure(Weapons.definition("chainsaw"));
            source=ShotDamage.PLAYER.source(h.getLevel(),ChainsawItem.DAMAGE,attack);
        } else {
            var key=kind.equals("fire")?NetherBlasterProjectile.DAMAGE_TYPE:techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE;
            source=new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key));
        }
        npc.hurtServer(h.getLevel(),source,kind.equals("bullet")?9:10);
        near(h,75-npc.getHealth(),switch(kind) { case "bullet"->7.56; case "chainsaw"->9.2; case "fire"->9; default->10; },"Source intrinsic armor and native toughness, without player suit defense");
        for(var slot:ArmorSlot.values()) h.assertValueEqual(npc.getItemBySlot(EquipmentSlot.valueOf(slot.name())).getDamageValue(),0,"NPC does not receive player special armor wear");
        npc.discard(); h.succeed();
    }
    private static void fire(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(3,40,3)); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(5.5,40,3)); target.setNoGravity(true);
        npc.setYHeadRot(-90); npc.setXRot(0); npc.getMainHandItem().set(TGContent.ROUNDS.get(),0);
        h.assertTrue(!npc.fireAt(npc),"Self target rejected"); h.assertTrue(npc.fireAt(target),"NPC still attacks without stored fuel, as in GenericNPC");
        var attacks=h.getLevel().getEntitiesOfClass(ChainsawAttack.class,npc.getBoundingBox().inflate(4),a->a.getOwner()==npc);
        h.assertValueEqual(attacks.size(),1,"Correct physical attack entity"); var attack=attacks.getFirst(); attack.tick();
        near(h,100-target.getHealth(),10*SuperMutantRules.damage(h.getLevel().getDifficulty().getId())*NpcConfig.DAMAGE_FACTOR.get(),"Source NPC damage scaling at real impact");
        h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),0,"No invented NPC fuel use or reload");
        target.setPos(h.absoluteVec(new Vec3(6.1,40,3))); npc.getSensing().tick(); h.assertTrue(!npc.fireAt(target),"Source AI range is three blocks");
        target.setPos(h.absoluteVec(new Vec3(5.5,40,3))); h.setBlock(new BlockPos(4,41,3),Blocks.STONE); h.setBlock(new BlockPos(4,42,3),Blocks.STONE); npc.getSensing().tick();
        h.assertTrue(!npc.fireAt(target),"Opaque obstruction blocks scheduled attack"); attacks.forEach(Entity::discard); npc.discard(); target.discard(); h.succeed();
    }
    private static void ai(GameTestHelper h) {
        for(int x=0;x<13;x++) for(int z=0;z<9;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var npc=new PsychoSteve(NpcContent.PSYCHO.get(),h.getLevel()); npc.equipCamo(0); npc.setPos(h.absoluteVec(new Vec3(2,1,4))); h.getLevel().addFreshEntity(npc);
        var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(10,1,4)); target.setNoGravity(true); npc.setTarget(target);
        h.runAfterDelay(75,()-> {
            h.assertTrue(target.getHealth()<100,"Native goal approaches from outside range and attacks with Chainsaw");
            h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),300,"Repeated NPC attacks keep source fuel policy");
            h.getLevel().getEntitiesOfClass(ChainsawAttack.class,npc.getBoundingBox().inflate(20),a->a.getOwner()==npc).forEach(Entity::discard); npc.discard(); target.discard(); h.succeed();
        });
    }
    private static LootTable table(GameTestHelper h) { return h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/psychosteve"))); }
    private static LootParams params(GameTestHelper h,PsychoSteve npc,Player player) { return ZombieSoldierGameTests.params(h,npc,player); }
    private static LegacyRandomSource fixed(float roll) { return new LegacyRandomSource(1) { @Override public float nextFloat() { return roll; } @Override public int nextInt(int bound) { return bound-1; } }; }
    private static void loot(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); var loot=table(h);
        for(int looting:new int[]{0,3}) {
            var sword=new ItemStack(Items.DIAMOND_SWORD); if(looting>0) sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),looting); p.setItemInHand(InteractionHand.MAIN_HAND,sword);
            h.assertTrue(loot.getRandomItems(params(h,npc,p),fixed(looting==0?.51f:.66f)).isEmpty(),"Both source pools stop above the correct chance");
            h.assertValueEqual(loot.getRandomItems(params(h,npc,p),fixed(looting==0?.49f:.64f)).size(),3,"Both source pools pass below threshold; two nonstackable saws plus fuel");
            var drops=loot.getRandomItems(params(h,npc,p),fixed(0)); var saws=drops.stream().filter(s->s.getItem() instanceof ChainsawItem).toList();
            h.assertValueEqual(saws.size(),2,"Loot count two splits into separate saw stacks");
            for(var s:saws) { h.assertValueEqual(s.getCount(),1,"Nonstackable saw"); h.assertValueEqual(GunItem.rounds(s),300,"Source metadata-zero loot is fully fueled"); h.assertValueEqual(ChainsawItem.head(s),0,"Loot does not copy equipped upgrades"); }
            int fuel=drops.stream().filter(s->s.is(TGContent.AMMO.get("fueltank").get())).mapToInt(ItemStack::getCount).sum();
            h.assertValueEqual(fuel,4+looting,"Only fuel quantity receives Looting's minimum 1 per level");
        }
        npc.discard(); h.succeed();
    }
    static void deathChain(GameTestHelper h,PsychoSteve npc,Player p) {
        npc.removeFreeWill(); p.setNoGravity(true); p.getInventory().clearContent(); long seed=1;
        for(;seed<10000;seed++) {
            var drops=table(h).getRandomItems(params(h,npc,p),seed);
            if(drops.stream().anyMatch(s->s.getItem() instanceof ChainsawItem) && drops.stream().anyMatch(s->s.is(TGContent.AMMO.get("fueltank").get()))) break;
        }
        h.assertTrue(seed<10000,"Seed for both independent original loot pools");
        // Isolate the loot table from vanilla random equipment drops; the NPC's held state must not leak into fresh loot.
        for(var slot:EquipmentSlot.VALUES) npc.setDropChance(slot,0); npc.getMainHandItem().set(TGContent.ROUNDS.get(),17); npc.getMainHandItem().set(TGContent.MINING_HEAD.get(),2);
        var data=NetherGameTests.save(h,npc); data.putLong("DeathLootTableSeed",seed); NetherGameTests.load(h,npc,data);
        npc.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(p),1000);
        var entities=h.getLevel().getEntitiesOfClass(ItemEntity.class,npc.getBoundingBox().inflate(3));
        var sawDrop=entities.stream().filter(e->e.getItem().getItem() instanceof ChainsawItem).findFirst().orElseThrow();
        var fuelDrop=entities.stream().filter(e->e.getItem().is(TGContent.AMMO.get("fueltank").get())).findFirst().orElseThrow();
        var saw=sawDrop.getItem().copy(); var fuel=fuelDrop.getItem().copy(); sawDrop.discard(); fuelDrop.discard(); int tanks=fuel.getCount();
        h.assertValueEqual(GunItem.rounds(saw),300,"Actual death drops a fueled saw"); h.assertValueEqual(ChainsawItem.head(saw),0,"Loot does not clone modified equipment");
        p.setItemInHand(InteractionHand.MAIN_HAND,saw); p.getInventory().setItem(1,fuel);
        h.assertTrue(GunItem.fire(h.getLevel(),p,saw),"Player uses dropped saw immediately"); h.assertValueEqual(GunItem.rounds(saw),299,"Player pays fuel for attack");
        for(int tick=0;tick<3;tick++) tickPlayer(p); h.assertTrue(ReloadSessions.begin(p),"Dropped tank starts refueling");
        for(int tick=0;tick<45;tick++) tickPlayer(p); h.assertValueEqual(GunItem.rounds(saw),300,"Refuel returns to full");
        h.assertValueEqual(fuel.getCount(),tanks-1,"Exactly one full tank consumed"); h.assertValueEqual(p.getInventory().countItem(TGContent.AMMO.get("fueltankempty").get()),1,"One empty tank returned");
        h.assertTrue(GunItem.fire(h.getLevel(),p,saw),"Refueled loot saw fires again");
        h.getLevel().getEntitiesOfClass(ChainsawAttack.class,p.getBoundingBox().inflate(10),a->a.getOwner()==p).forEach(Entity::discard); entities.forEach(Entity::discard); npc.discard();
    }
    private static void death(GameTestHelper h) { deathChain(h,mob(h,0,new Vec3(4,2,4)),WeaponGameTests.player(h)); h.succeed(); }
    private static void tickPlayer(Player p) {
        p.tick();
        // Connected ServerPlayer separates entity/network ticking from Player.tick; the
        // latter (doTick) advances item cooldowns and fires the real reload tick event.
        if(p instanceof net.minecraft.server.level.ServerPlayer server) server.doTick();
    }
    private PsychoSteveGameTests() {}
}
