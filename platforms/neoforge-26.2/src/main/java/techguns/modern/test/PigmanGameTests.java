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
import net.minecraft.world.inventory.AnvilMenu;
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

final class PigmanGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("pigman_attributes_undead_and_armor", () -> PigmanGameTests::attributes);
        r.register("pigman_weapon_roll_and_fixed_camo", () -> PigmanGameTests::rolls);
        r.register("pigman_egg_and_equipment_save", () -> PigmanGameTests::egg);
        for(int roll:List.of(0,3,5,7)) r.register("pigman_fire_"+PigmanRules.weapon(roll), () -> h -> fire(h,roll));
        r.register("pigman_real_ai_with_armor", () -> PigmanGameTests::ai);
        r.register("pigman_six_original_loot_pools", () -> PigmanGameTests::loot);
        r.register("pigman_death_loot_repairs_armor", () -> PigmanGameTests::deathRepair);
    }
    private static ZombiePigmanSoldier mob(GameTestHelper h,int roll,Vec3 pos) {
        var npc=h.spawnWithNoFreeWill(NpcContent.PIGMAN.get(),pos); npc.setNoGravity(true); npc.equipRoll(roll,.1,.1,.1); return npc;
    }
    private static void attributes(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(4,2,4)); npc.tick();
        ArmorGameTests.near(h,npc.getMaxHealth(),25,"Original health"); ArmorGameTests.near(h,npc.getAttributeValue(Attributes.MOVEMENT_SPEED),.3,"Armor speed bonuses do not apply to NPC");
        ArmorGameTests.near(h,npc.getAttributeValue(Attributes.ATTACK_DAMAGE),5,"Melee"); ArmorGameTests.near(h,npc.getAttributeValue(Attributes.FOLLOW_RANGE),50,"Follow range");
        ArmorGameTests.near(h,npc.getAttributeValue(Attributes.ARMOR_TOUGHNESS),4,"Native armor toughness still applies to NPC");
        ArmorGameTests.near(h,npc.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,"Player-only armor knockback bonus");
        ArmorGameTests.near(h,npc.getAttributeValue(Attributes.ARMOR),10,"No player HUD contribution on NPC");
        h.assertTrue(npc.is(EntityTypeTags.UNDEAD) && npc.isInvertedHealAndHarm(),"Undead behavior");
        h.assertTrue(!npc.addEffect(new MobEffectInstance(MobEffects.POISON,100)) && !npc.addEffect(new MobEffectInstance(MobEffects.REGENERATION,100)),"Undead excludes poison/regeneration");
        h.assertTrue(npc.fireImmune() && !npc.hurtServer(h.getLevel(),h.getLevel().damageSources().lava(),10),"Fire immunity");
        for(var slot:List.of(EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) npc.setItemSlot(slot,ItemStack.EMPTY);
        npc.tick(); ArmorGameTests.near(h,npc.getAttributeValue(Attributes.ARMOR_TOUGHNESS),1,"Only helmet toughness remains");
        var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47")); bullet.setOwner(WeaponGameTests.player(h));
        npc.hurtServer(h.getLevel(),ShotDamage.PLAYER.source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet),9);
        ArmorGameTests.near(h,npc.getHealth(),19.24,"Intrinsic 10 armor and worn helmet toughness, not player special-armor formula");
        h.assertValueEqual(npc.getItemBySlot(EquipmentSlot.HEAD).getDamageValue(),0,"GenericNPC does not run player's equipment-wear stages"); npc.discard(); h.succeed();
    }
    private static void rolls(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(4,2,4));
        for(int n=0;n<9;n++) {
            npc.equipRoll(n,.9,.5,.9); h.assertTrue(npc.getMainHandItem().is(TGContent.GUNS.get(PigmanRules.weapon(n)).get()),"Only reachable original weapon cases");
            h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),Weapons.definition(PigmanRules.weapon(n)).stats().capacity(),"NPC starts with a full weapon");
            h.assertTrue(!npc.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !npc.getItemBySlot(EquipmentSlot.LEGS).isEmpty(),"Unconditional helmet and inclusive half-chance legs");
            h.assertTrue(npc.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && npc.getItemBySlot(EquipmentSlot.FEET).isEmpty(),"Other rolls fail independently");
            h.assertValueEqual(TGArmorItem.camo(npc.getItemBySlot(EquipmentSlot.HEAD)),3,"Original camo index, not random appearance");
        }
        npc.discard(); h.succeed();
    }
    private static void egg(GameTestHelper h) {
        var player=WeaponGameTests.player(h); var egg=NpcContent.PIGMAN_EGG.toStack(2); player.setItemInHand(InteractionHand.MAIN_HAND,egg);
        var pos=new BlockPos(4,1,4); h.setBlock(pos,Blocks.STONE); h.useBlock(pos,player);
        var mobs=h.getLevel().getEntitiesOfClass(ZombiePigmanSoldier.class,new AABB(h.absolutePos(pos)).inflate(4)); h.assertValueEqual(mobs.size(),1,"Egg spawns one soldier");
        var npc=mobs.getFirst(); h.assertValueEqual(egg.getCount(),1,"Survival egg consumed"); h.assertTrue(npc.armed(),"Spawn finalization equips weapon");
        npc.equipRoll(5,.1,.1,.1); npc.setCustomName(Component.literal("Soldier test")); npc.getMainHandItem().set(TGContent.ROUNDS.get(),7); npc.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(400);
        var restored=new ZombiePigmanSoldier(NpcContent.PIGMAN.get(),h.getLevel()); NetherGameTests.load(h,restored,NetherGameTests.save(h,npc));
        h.assertValueEqual(GunItem.rounds(restored.getMainHandItem()),7,"Load does not refill or reroll weapon");
        h.assertValueEqual(restored.getItemBySlot(EquipmentSlot.HEAD).getDamageValue(),400,"Armor damage retained");
        h.assertValueEqual(TGArmorItem.camo(restored.getItemBySlot(EquipmentSlot.HEAD)),3,"Camouflage retained"); h.assertValueEqual(restored.getCustomName(),npc.getCustomName(),"Name retained"); npc.discard(); h.succeed();
    }
    private static void fire(GameTestHelper h,int roll) {
        var npc=mob(h,roll,new Vec3(3,40,3)); var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(9,40,3)); target.setNoGravity(true);
        npc.setYHeadRot(-90); npc.setYRot(0); npc.setXRot(0); h.assertTrue(npc.fireAt(target),"Soldier fires source weapon");
        var bullets=h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(4),b -> b.getOwner()==npc); h.assertValueEqual(bullets.size(),1,"One ballistic projectile");
        h.assertValueEqual(bullets.getFirst().weapon().id(),PigmanRules.weapon(roll),"Source parameters selected");
        for(var bullet:bullets) { for(int i=0;i<4 && !bullet.isRemoved();i++) bullet.tick(); bullet.discard(); }
        h.assertTrue(target.getHealth()<100,"Projectile hits living target"); h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),Weapons.definition(PigmanRules.weapon(roll)).stats().capacity(),"Infinite NPC ammunition");
        npc.discard(); target.discard(); h.succeed();
    }
    private static void ai(GameTestHelper h) {
        for(int x=1;x<11;x++) for(int z=1;z<11;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var npc=new ZombiePigmanSoldier(NpcContent.PIGMAN.get(),h.getLevel()); npc.setPos(h.absoluteVec(new Vec3(3,1,3))); npc.equipRoll(0,.1,.1,.1); h.getLevel().addFreshEntity(npc);
        var target=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(8,1,3)); target.setNoGravity(true); npc.setTarget(target);
        h.runAfterDelay(65,() -> { h.assertTrue(target.getHealth()<100,"Real armed AI acts while wearing T2"); ArmorGameTests.near(h,npc.getAttributeValue(Attributes.MOVEMENT_SPEED),.3,"No player speed bonus leaks to soldier AI");
            h.getLevel().getEntitiesOfClass(Bullet.class,npc.getBoundingBox().inflate(120),b -> b.getOwner()==npc).forEach(Entity::discard); npc.discard(); target.discard(); h.succeed(); });
    }
    private static LootTable table(GameTestHelper h) { return h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("entities/zombiepigmansoldier"))); }
    private static LootParams params(GameTestHelper h,LivingEntity npc,Player player) {
        return new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.THIS_ENTITY,npc).withParameter(LootContextParams.ORIGIN,npc.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE,h.getLevel().damageSources().playerAttack(player)).withParameter(LootContextParams.ATTACKING_ENTITY,player)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER,player).create(LootContextParamSets.ENTITY);
    }
    private static LegacyRandomSource fixed(float value) { return new LegacyRandomSource(1) { @Override public float nextFloat() { return value; } }; }
    private static void loot(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(4,2,4)); var player=WeaponGameTests.player(h); var drops=table(h).getRandomItems(params(h,npc,player),fixed(.19f));
        h.assertValueEqual(drops.size(),6,"All six independent pools at a passing roll");
        h.assertTrue(drops.stream().anyMatch(s -> s.is(TGContent.MATERIALS.get("ingotobsidiansteel").get())),"Original metadata 84 gives obsidian steel");
        h.assertTrue(table(h).getRandomItems(params(h,npc,player),fixed(.21f)).isEmpty(),"Base probability is .2");
        var sword=new ItemStack(Items.DIAMOND_SWORD); sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),3); player.setItemInHand(InteractionHand.MAIN_HAND,sword);
        h.assertValueEqual(table(h).getRandomItems(params(h,npc,player),fixed(.34f)).size(),6,"Looting III raises chance to .35");
        h.assertTrue(table(h).getRandomItems(params(h,npc,player),fixed(.36f)).isEmpty(),"Looting chance boundary"); npc.discard(); h.succeed();
    }
    private static void deathRepair(GameTestHelper h) {
        var npc=mob(h,0,new Vec3(4,2,4)); var player=WeaponGameTests.player(h); var metal=TGContent.MATERIALS.get("ingotobsidiansteel").get(); long seed=1;
        while(seed<10000 && table(h).getRandomItems(params(h,npc,player),seed).stream().noneMatch(s -> s.is(metal))) seed++;
        h.assertTrue(seed<10000,"Deterministic metal-drop seed"); var data=NetherGameTests.save(h,npc); data.putLong("DeathLootTableSeed",seed); NetherGameTests.load(h,npc,data);
        npc.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY); npc.hurtServer(h.getLevel(),h.getLevel().damageSources().playerAttack(player),1000);
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,npc.getBoundingBox().inflate(3));
        var ingot=drops.stream().filter(e -> e.getItem().is(metal)).findFirst().orElseThrow().getItem().copyWithCount(1);
        var helmet=ArmorGameTests.armor(ArmorSlot.HEAD,3); helmet.setDamageValue(600); var menu=new AnvilMenu(1,player.getInventory()); menu.getSlot(0).set(helmet); menu.getSlot(1).set(ingot); menu.createResult();
        h.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(),353,"Actual soldier death loot repairs T2 armor on anvil"); drops.forEach(Entity::discard); npc.discard(); h.succeed();
    }
    private PigmanGameTests() {}
}
