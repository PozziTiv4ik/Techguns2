package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.armor.*;
import techguns.modern.machine.camo.*;
import techguns.modern.machine.repair.*;

final class BeretGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("beret_native_equip_camo_and_save_packet",()->BeretGameTests::equip);
        r.register("beret_walk_sprint_and_wear_boundary",()->BeretGameTests::speed);
        for(String type:List.of("physical","bullet","fire","acid","fall")) r.register("beret_protection_"+type,()->h->damage(h,type));
        r.register("beret_original_workbench_recipe",()->BeretGameTests::recipe);
        r.register("beret_anvil_cloth_repair_and_reject_steel",()->BeretGameTests::anvil);
        r.register("beret_repair_bench_cloth_and_camo_bench",()->BeretGameTests::benches);
    }
    private static ItemStack beret() { var s=ArmorContent.BERET.toStack(); TGArmorItem.setCamo(s,2); s.set(DataComponents.CUSTOM_NAME,Component.literal("Field beret")); return s; }
    private static void equip(GameTestHelper h) {
        var p=ArmorGameTests.player(h); var s=beret(); s.setDamageValue(123); p.setItemInHand(InteractionHand.MAIN_HAND,s); p.setShiftKeyDown(true);
        for(int i=0;i<6;i++) { s.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND); h.assertValueEqual(TGArmorItem.camo(s),i%3,"Three source camos wrap");
            h.assertValueEqual(s.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier(),TGContent.id(Armors.T2_BERET.getFirst().camos().get(i%3)),"Equipment asset follows camo"); }
        h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"Sneak-use does not equip"); p.setShiftKeyDown(false); s.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
        var worn=p.getItemBySlot(EquipmentSlot.HEAD); h.assertTrue(worn.is(ArmorContent.BERET.get()),"Native use equips beret"); h.assertValueEqual(worn.getMaxDamage(),825,"Source durability"); h.assertTrue(!worn.isEnchantable(),"Source enchanting-table value zero");
        var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE); var restored=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,worn).getOrThrow()).getOrThrow();
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try { ItemStack.STREAM_CODEC.encode(buf,restored); var decoded=ItemStack.STREAM_CODEC.decode(buf); h.assertTrue(ItemStack.matches(decoded,worn),"Color, equipment asset, wear and name survive save and wire"); } finally { buf.release(); }
        h.succeed();
    }
    private static void speed(GameTestHelper h) {
        var p=ArmorGameTests.player(h); var s=beret(); p.setItemSlot(EquipmentSlot.HEAD,s); p.tick(); TGArmorSystem.refresh(p);
        ArmorGameTests.near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.11,"Beret grants ten percent walking speed"); h.assertValueEqual(p.getArmorValue(),2,"Rounded source HUD");
        p.setSprinting(true); TGArmorSystem.refresh(p); ArmorGameTests.near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.156,"Twenty percent sprint bonus times native sprint");
        s.setDamageValue(823); TGArmorSystem.refresh(p); ArmorGameTests.near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.156,"Bonus until final usable durability");
        s.setDamageValue(824); TGArmorSystem.refresh(p); ArmorGameTests.near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.13,"Worn beret removes speed"); h.assertValueEqual(p.getArmorValue(),0,"Worn HUD removed");
        s.setDamageValue(0); TGArmorSystem.refresh(p); ArmorGameTests.near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.156,"Repair restores bonus");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY); p.setItemSlot(EquipmentSlot.OFFHAND,s); TGArmorSystem.refresh(p); ArmorGameTests.near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.13,"Offhand does not grant armor speed"); h.succeed();
    }
    private static void damage(GameTestHelper h,String type) {
        var p=ArmorGameTests.player(h); var s=beret(); p.setItemSlot(EquipmentSlot.HEAD,s); p.tick(); DamageSource source; float amount=10,expected;
        switch(type) {
            case "bullet" -> { var bullet=new Bullet(TGContent.BULLET.get(),h.getLevel()); bullet.configure(Weapons.definition("ak47")); source=ShotDamage.PLAYER.source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),bullet); amount=9; expected=8.46f; }
            case "fire" -> { source=new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(NetherBlasterProjectile.DAMAGE_TYPE)); expected=9.4f; }
            case "acid" -> { source=new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE)); expected=9.4f; }
            case "fall" -> { source=h.getLevel().damageSources().fall(); expected=10; }
            default -> { source=h.getLevel().damageSources().playerAttack(WeaponGameTests.player(h)); expected=9.2f; }
        }
        p.hurtServer(h.getLevel(),source,amount); ArmorGameTests.near(h,1000-p.getHealth(),expected,"Source per-piece fractional absorption");
        h.assertTrue(type.equals("fall")?s.getDamageValue()==0:s.getDamageValue()>0,"Native/special durability wear policy"); h.succeed();
    }
    private static void recipe(GameTestHelper h) {
        var c=TGContent.MATERIALS.get("heavycloth"); var input=CraftingInput.of(3,2,List.of(ItemStack.EMPTY,c.toStack(),c.toStack(),c.toStack(),ItemStack.EMPTY,c.toStack()));
        var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();
        h.assertValueEqual(recipe.id().identifier(),TGContent.id("t2_beret"),"Original asymmetric four-cloth recipe"); var output=recipe.value().assemble(input);
        h.assertTrue(output.is(ArmorContent.BERET.get()) && output.getCount()==1,"One real wearable beret"); h.assertValueEqual(TGArmorItem.camo(output),0,"Crafted source red variant"); h.succeed();
    }
    private static void anvil(GameTestHelper h) {
        var s=beret(); s.setDamageValue(824); var m=new AnvilMenu(1,ArmorGameTests.player(h).getInventory()); m.getSlot(0).set(s); m.getSlot(1).set(TGContent.MATERIALS.get("heavycloth").toStack()); m.setItemName(s.getHoverName().getString()); m.createResult(); var result=m.getSlot(2).getItem();
        h.assertTrue(result.is(ArmorContent.BERET.get()),"Cloth repairs on native anvil"); h.assertValueEqual(result.getDamageValue(),618,"One cloth restores floor(825/4)");
        h.assertValueEqual(TGArmorItem.camo(result),2,"Green camo retained"); h.assertValueEqual(result.get(DataComponents.CUSTOM_NAME),s.get(DataComponents.CUSTOM_NAME),"Name retained");
        m.getSlot(1).set(TGContent.MATERIALS.get("ingotobsidiansteel").toStack()); m.createResult(); h.assertTrue(m.getSlot(2).getItem().isEmpty(),"T2 label does not make metal a beret repair ingredient"); h.succeed();
    }
    private static void benches(GameTestHelper h) {
        var pos=new BlockPos(4,2,4); h.setBlock(pos,RepairBenchContent.BLOCK.get()); var b=h.getBlockEntity(pos,RepairBenchBlockEntity.class); var p=ArmorGameTests.player(h); b.setOwner(p);
        var m=new RepairBenchMenu(31,p.getInventory(),b); p.containerMenu=m; var s=beret(); s.setDamageValue(824); p.setItemSlot(EquipmentSlot.HEAD,s); b.setItem(0,TGContent.MATERIALS.get("heavycloth").toStack());
        h.assertTrue(!m.clickMenuButton(p,1),"One cloth insufficient for fully worn beret"); h.assertValueEqual(s.getDamageValue(),824,"Failed repair leaves condition"); h.assertValueEqual(b.getItem(0).getCount(),1,"Failed repair retains cloth");
        b.setItem(1,TGContent.MATERIALS.get("heavycloth").toStack()); h.assertTrue(m.clickMenuButton(p,1),"Two cloth across inventory slots repair fully");
        h.assertValueEqual(s.getDamageValue(),0,"Beret restored"); h.assertTrue(b.getItem(0).isEmpty() && b.getItem(1).isEmpty(),"Exactly two heavy cloth consumed"); h.assertValueEqual(TGArmorItem.camo(s),2,"Repair keeps color");
        h.setBlock(pos,CamoBenchContent.BLOCK.get()); var cb=h.getBlockEntity(pos,CamoBenchBlockEntity.class); cb.setOwner(p); var cm=new CamoBenchMenu(32,p.getInventory(),cb); p.containerMenu=cm;
        h.assertTrue(cm.clickMenuButton(p,3),"Equipped beret changes color on bench"); h.assertValueEqual(TGArmorItem.camo(p.getItemBySlot(EquipmentSlot.HEAD)),0,"Green wraps to red");
        h.assertTrue(cm.clickMenuButton(p,4),"Reverse color direction"); h.assertValueEqual(TGArmorItem.camo(p.getItemBySlot(EquipmentSlot.HEAD)),2,"Red wraps to green"); h.succeed();
    }
    private BeretGameTests() {}
}
