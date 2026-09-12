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
import techguns.core.RuralZombieRules.Kind;
import techguns.modern.*;
import techguns.modern.npc.*;
import techguns.modern.armor.*;
import techguns.modern.machine.repair.*;

final class RuralZombieGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var kind:Kind.values()) {
            String id="rural_"+kind.name().toLowerCase(Locale.ROOT)+"_";
            r.register(id+"attributes_and_undead",() -> h -> attributes(h,kind));
            r.register(id+"equipment_rolls",() -> h -> rolls(h,kind));
            r.register(id+"egg_and_save",() -> h -> egg(h,kind));
            r.register(id+"handcannon_projectile",() -> h -> fire(h,kind));
            for(int roll=0;roll<kind.weaponCount();roll++) {
                int choice=roll; r.register(id+"live_ai_"+roll,() -> h -> ai(h,kind,choice));
                if(roll<kind.weaponCount()-1) r.register(id+"melee_"+roll,() -> h -> melee(h,kind,choice));
            }
            r.register(id+"loot_chance_and_count",() -> h -> loot(h,kind));
            r.register(id+"death_loot_use",() -> h -> death(h,kind));
        }
        RuralDaylightGameTests.register(r);
    }
    static RuralZombie create(GameTestHelper h,Kind kind) {
        return kind==Kind.FARMER?new ZombieFarmer(NpcContent.FARMER.get(),h.getLevel()):new ZombieMiner(NpcContent.MINER.get(),h.getLevel());
    }
    private static RuralZombie mob(GameTestHelper h,Kind kind,int roll,Vec3 pos) {
        var npc=create(h,kind); npc.removeFreeWill(); npc.setPos(h.absoluteVec(pos)); npc.setNoGravity(true); npc.equipRoll(roll,0,0,0,0); h.getLevel().addFreshEntity(npc); return npc;
    }
    private static void near(GameTestHelper h,double actual,double expected,String message) { ArmorGameTests.near(h,actual,expected,message); }
    private static void attributes(GameTestHelper h,Kind kind) {
        var npc=mob(h,kind,kind.weaponCount()-1,new Vec3(4,2,4)); npc.tick();
        near(h,npc.getMaxHealth(),kind.health(),"Source health"); near(h,npc.getAttributeValue(Attributes.ATTACK_DAMAGE),kind.attack(),"Source base melee");
        near(h,npc.getAttributeValue(Attributes.MOVEMENT_SPEED),.2,"T1 Miner player speed does not affect NPC"); near(h,npc.getAttributeValue(Attributes.FOLLOW_RANGE),50,"Source follow distance");
        near(h,npc.getAttributeValue(Attributes.ARMOR),0,"Clothing does not replace source intrinsic zero armor"); near(h,npc.getAttributeValue(Attributes.ARMOR_TOUGHNESS),0,"No toughness");
        h.assertTrue(npc.is(EntityTypeTags.UNDEAD) && npc.isInvertedHealAndHarm() && npc.is(EntityTypeTags.SENSITIVE_TO_SMITE),"Undead behavior and Smite vulnerability");
        h.assertTrue(!npc.addEffect(new MobEffectInstance(MobEffects.POISON,100)) && !npc.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100)),"Undead rejects poison and regeneration");
        h.assertTrue(!npc.canPickUpLoot() && !npc.fireImmune(),"No item pickup or fire immunity");
        var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47")); bullet.setOwner(WeaponGameTests.player(h));
        npc.hurtServer(h.getLevel(),ShotDamage.PLAYER.source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet),9);
        near(h,npc.getHealth(),kind.health()-9,"Source zero intrinsic protection against actual bullet damage");
        for(var slot:ArmorSlot.values()) h.assertValueEqual(npc.getItemBySlot(EquipmentSlot.valueOf(slot.name())).getDamageValue(),0,"No player-only armor wear on NPC"); npc.discard(); h.succeed();
    }
    private static void rolls(GameTestHelper h,Kind kind) {
        var npc=mob(h,kind,0,new Vec3(4,2,4));
        for(int weapon=0;weapon<kind.weaponCount();weapon++) for(int camo=0;camo<4;camo++) {
            npc.equipRoll(weapon,camo,.9,.5,.9);
            h.assertTrue(npc.getMainHandItem().is(BuiltInRegistries.ITEM.getValue(Identifier.parse(kind.weapon(weapon)))),"All source weapon cases");
            h.assertValueEqual(npc.armed(),weapon==kind.weaponCount()-1,"Only handcannon selects ranged combat");
            for(var slot:ArmorSlot.values()) {
                var stack=npc.getItemBySlot(EquipmentSlot.valueOf(slot.name())); boolean present=slot==ArmorSlot.LEGS || slot==(kind==Kind.FARMER?ArmorSlot.CHEST:ArmorSlot.HEAD);
                h.assertValueEqual(!stack.isEmpty(),present,"Independent optional armor and distinct guaranteed part");
                if(present) { h.assertTrue(stack.is(ArmorContent.T1_MINER.get(slot).get()),"Correct T1 Miner part"); h.assertValueEqual(TGArmorItem.camo(stack),camo,"One shared color index across equipped parts"); }
            }
        }
        npc.discard(); h.succeed();
    }
    private static void egg(GameTestHelper h,Kind kind) {
        var p=WeaponGameTests.player(h); var egg=(kind==Kind.FARMER?NpcContent.FARMER_EGG:NpcContent.MINER_EGG).toStack(2); p.setItemInHand(InteractionHand.MAIN_HAND,egg);
        var pos=new BlockPos(4,1,4); h.setBlock(pos,Blocks.STONE); h.useBlock(pos,p);
        var mobs=h.getLevel().getEntitiesOfClass(RuralZombie.class,new AABB(h.absolutePos(pos)).inflate(4),n -> n.kind()==kind);
        h.assertValueEqual(mobs.size(),1,"Egg spawns one correct NPC"); h.assertValueEqual(egg.getCount(),1,"Survival egg consumed"); var npc=mobs.getFirst();
        h.assertTrue(!npc.getMainHandItem().isEmpty(),"FinalizeSpawn equips a weapon");
        for(int roll=0;roll<kind.weaponCount();roll++) {
            npc.equipRoll(roll,3,.9,.5,.9); npc.setCustomName(Component.literal("Rural save"));
            if(npc.armed()) npc.getMainHandItem().set(TGContent.ROUNDS.get(),0); else npc.getMainHandItem().setDamageValue(7);
            var slot=kind==Kind.FARMER?EquipmentSlot.CHEST:EquipmentSlot.HEAD; npc.getItemBySlot(slot).setDamageValue(700);
            var restored=create(h,kind); NetherGameTests.load(h,restored,NetherGameTests.save(h,npc));
            h.assertTrue(ItemStack.matches(npc.getMainHandItem(),restored.getMainHandItem()),"No weapon reroll/refill on reload");
            for(var equipment:ArmorSlot.values()) h.assertTrue(ItemStack.matches(npc.getItemBySlot(EquipmentSlot.valueOf(equipment.name())),restored.getItemBySlot(EquipmentSlot.valueOf(equipment.name()))),"Missing pieces, color and damage survive reload");
            h.assertValueEqual(restored.getCustomName(),npc.getCustomName(),"Name retained");
        }
        npc.discard(); h.succeed();
    }
    private static void fire(GameTestHelper h,Kind kind) {
        var npc=mob(h,kind,kind.weaponCount()-1,new Vec3(3,40,3)); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(9,40,3)); target.setNoGravity(true);
        npc.setYHeadRot(-90); npc.setYRot(0); npc.setXRot(0); h.assertTrue(npc.fireAt(target),"Rural NPC fires handcannon");
        var bullets=h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(4),b -> b.getOwner()==npc); h.assertValueEqual(bullets.size(),1,"One source projectile");
        h.assertValueEqual(bullets.getFirst().weapon().id(),"handcannon","Correct ballistic profile");
        for(var bullet:bullets) { for(int i=0;i<8 && !bullet.isRemoved();i++) bullet.tick(); bullet.discard(); }
        h.assertTrue(target.getHealth()<100,"Real projectile reaches target"); h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),1,"Infinite NPC ammunition remains loaded");
        npc.discard(); target.discard(); h.succeed();
    }
    private static void melee(GameTestHelper h,Kind kind,int roll) {
        var npc=mob(h,kind,roll,new Vec3(3,2,3)); npc.tick(); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(4,2,3)); target.getAttribute(Attributes.ARMOR).setBaseValue(0);
        h.assertTrue(!npc.fireAt(target),"Tools do not fire gun projectiles"); h.assertTrue(npc.doHurtTarget(h.getLevel(),target),"Native tool melee hits");
        near(h,100-target.getHealth(),kind==Kind.FARMER?3:roll==0?6:7,"Source base damage plus native tool modifier"); npc.discard(); target.discard(); h.succeed();
    }
    private static void ai(GameTestHelper h,Kind kind,int roll) {
        for(int x=1;x<11;x++) for(int z=1;z<11;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var npc=create(h,kind); npc.setPos(h.absoluteVec(new Vec3(3,1,3))); npc.equipRoll(roll,2,0,0,0); h.getLevel().addFreshEntity(npc);
        var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(8,1,3)); target.setNoGravity(true); npc.setTarget(target);
        h.runAfterDelay(75,() -> {
            h.assertTrue(target.getHealth()<100,"Real ranged/melee goal damages target with source weapon "+kind.weapon(roll));
            h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(120),b -> b.getOwner()==npc).forEach(Entity::discard);
            npc.discard(); target.discard(); h.succeed();
        });
    }
    static LootTable table(GameTestHelper h,Kind kind) { return h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/"+kind.id()))); }
    static LootParams params(GameTestHelper h,LivingEntity npc,Player p) {
        return new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.THIS_ENTITY,npc).withParameter(LootContextParams.ORIGIN,npc.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE,h.getLevel().damageSources().playerAttack(p)).withParameter(LootContextParams.ATTACKING_ENTITY,p)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER,p).create(LootContextParamSets.ENTITY);
    }
    private static LegacyRandomSource fixed(float value) { return new LegacyRandomSource(1) { @Override public float nextFloat() { return value; } @Override public int nextInt(int bound) { return bound-1; } }; }
    private static void loot(GameTestHelper h,Kind kind) {
        var npc=mob(h,kind,0,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); var table=table(h,kind); int count=kind==Kind.FARMER?7:5;
        var drops=table.getRandomItems(params(h,npc,p),fixed(0)); h.assertValueEqual(drops.size(),count,"All independent source pools");
        int[] maxima=kind==Kind.FARMER?new int[]{2,2,2,5,2,3,4}:new int[]{2,2,2,5,1};
        for(int i=0;i<count;i++) h.assertValueEqual(drops.get(i).getCount(),maxima[i],"Original quantity ceiling");
        h.assertTrue(table.getRandomItems(params(h,npc,p),fixed(.21f)).isEmpty(),"All base probabilities are .2");
        var sword=new ItemStack(Items.DIAMOND_SWORD); sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),3); p.setItemInHand(InteractionHand.MAIN_HAND,sword);
        h.assertValueEqual(table.getRandomItems(params(h,npc,p),fixed(.34f)).size(),count,"Looting III raises chance to .35");
        h.assertTrue(table.getRandomItems(params(h,npc,p),fixed(.36f)).isEmpty(),"Looting chance boundary");
        var enchanted=table.getRandomItems(params(h,npc,p),fixed(0));
        for(int i=0;i<count;i++) h.assertValueEqual(enchanted.get(i).getCount(),maxima[i]+3,"Source looting quantity increase at fixed minimum bonus draw");
        npc.discard(); h.succeed();
    }
    private static void death(GameTestHelper h,Kind kind) {
        var npc=mob(h,kind,0,new Vec3(4,2,4)); var p=WeaponGameTests.player(h); var wanted=kind==Kind.FARMER?Items.BREAD:Items.IRON_INGOT; var cloth=TGContent.MATERIALS.get("heavycloth").get();
        long seed=1;
        while(seed<10000) {
            var drops=table(h,kind).getRandomItems(params(h,npc,p),seed);
            if(drops.stream().anyMatch(s -> s.is(wanted)) && drops.stream().anyMatch(s -> s.is(cloth))) break;
            seed++;
        }
        h.assertTrue(seed<10000,"Deterministic source food/material seed found"); var data=NetherGameTests.save(h,npc); data.putLong("DeathLootTableSeed",seed); NetherGameTests.load(h,npc,data);
        npc.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(p),1000);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,npc.getBoundingBox().inflate(3));
        var output=drops.stream().filter(e -> e.getItem().is(wanted)).findFirst().orElseThrow().getItem().copyWithCount(1);
        if(kind==Kind.FARMER) {
            p.getFoodData().setFoodLevel(10); output.finishUsingItem(h.getLevel(),p); h.assertValueEqual(p.getFoodData().getFoodLevel(),15,"Actual farmer death loot feeds the player");
        } else {
            var fiber=drops.stream().filter(e -> e.getItem().is(cloth)).findFirst().orElseThrow().getItem().copyWithCount(1);
            var pos=new BlockPos(4,1,4); h.setBlock(pos,RepairBenchContent.BLOCK.get()); var bench=h.getBlockEntity(pos,RepairBenchBlockEntity.class); bench.setOwner(p);
            p.containerMenu=new RepairBenchMenu(97,p.getInventory(),bench); var helmet=ArmorContent.T1_MINER.get(ArmorSlot.HEAD).toStack(); helmet.setDamageValue(824); p.setItemSlot(EquipmentSlot.HEAD,helmet);
            bench.setItem(0,output); bench.setItem(1,fiber); h.assertTrue(p.containerMenu.clickMenuButton(p,1),"Actual miner iron and cloth repair T1 Miner");
            h.assertTrue(helmet.getDamageValue()==0 && bench.isEmpty(),"Exact source repair cost consumed");
        }
        drops.forEach(Entity::discard); npc.discard(); h.succeed();
    }
    private RuralZombieGameTests() {}
}
