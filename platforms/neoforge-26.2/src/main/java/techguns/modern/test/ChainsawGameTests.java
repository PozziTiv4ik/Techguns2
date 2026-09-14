package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.crafting.MiningHeadRecipe;
import techguns.modern.network.*;

final class ChainsawGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(int head=0;head<3;head++) { int n=head; r.register("chainsaw_mining_head_"+n,()->h->mining(h,n)); }
        for(boolean fueled:new boolean[]{false,true}) { boolean b=fueled;
            r.register("chainsaw_craft_"+b,()->h->craft(h,b));
            r.register("chainsaw_native_break_"+b,()->h->breaking(h,b)); }
        for(int fuel:new int[]{0,1,173}) { int n=fuel; r.register("chainsaw_upgrades_preserve_fuel_"+n,()->h->upgrades(h,n)); }
        r.register("chainsaw_save_and_recipe_network_codec",()->ChainsawGameTests::codecs);
        r.register("chainsaw_chemical_fuel_reload_attack_chain",()->ChainsawGameTests::chemical);
        r.register("chainsaw_attack_guards_last_fuel_and_creative",()->ChainsawGameTests::guards);
        r.register("chainsaw_melee_native_damage_and_last_fuel",()->ChainsawGameTests::melee);
        r.register("chainsaw_melee_rejected_and_creative",()->ChainsawGameTests::meleeRejected);
        r.register("chainsaw_native_melee_armor",()->ChainsawGameTests::meleeArmor);
        r.register("chainsaw_physical_armor_and_hurt_cooldown",()->ChainsawGameTests::impact);
        r.register("chainsaw_wall_and_two_tick_lifetime",()->ChainsawGameTests::wall);
        r.register("chainsaw_attack_save_and_water",()->ChainsawGameTests::savedAttack);
        r.register("chainsaw_canceled_spawn_keeps_fuel",()->ChainsawGameTests::cancel);
    }
    private static ItemStack saw(int fuel,int head) { var s=TGContent.GUNS.get("chainsaw").toStack(); s.set(TGContent.ROUNDS.get(),fuel); s.set(TGContent.MINING_HEAD.get(),head); return s; }
    private static Player player(GameTestHelper h,ItemStack stack) { var p=WeaponGameTests.player(h); p.setItemInHand(InteractionHand.MAIN_HAND,stack); return p; }
    private static void near(GameTestHelper h,double actual,double expected,String name) { ArmorGameTests.near(h,actual,expected,name); }
    private static ItemStack material(String id) { return TGContent.MATERIALS.get(id).toStack(); }
    private static void mining(GameTestHelper h,int head) {
        var stack=saw(1,head); var item=(ChainsawItem)stack.getItem();
        near(h,item.getDestroySpeed(stack,Blocks.OAK_LOG.defaultBlockState()),14+3*head,"Axe speed from source");
        near(h,item.getDestroySpeed(stack,Blocks.STONE.defaultBlockState()),1,"No pickaxe bonus");
        near(h,item.getDestroySpeed(stack,Blocks.DIRT.defaultBlockState()),1,"No shovel bonus");
        h.assertTrue(item.isCorrectToolForDrops(stack,Blocks.OAK_LOG.defaultBlockState()),"Fueled axe harvests wood");
        h.assertTrue(!item.isCorrectToolForDrops(stack,Blocks.DIAMOND_ORE.defaultBlockState()),"Upgrades never become pickaxes");
        stack.set(TGContent.ROUNDS.get(),0);
        near(h,item.getDestroySpeed(stack,Blocks.OAK_LOG.defaultBlockState()),1,"Last fuel removes speed");
        h.assertTrue(!item.canPerformAction(stack,net.neoforged.neoforge.common.ItemAbilities.SWORD_SWEEP),"Empty saw has no sweep"); h.succeed();
    }
    private static void craft(GameTestHelper h,boolean fueled) {
        var p=material("mechanicalpartsiron"); var plate=material("plateiron");
        var stack=CraftingGameTests.craft(h,3,3,p,p,material("plasticsheet"),plate,plate,material("ironreceiver"),p,p,TGContent.AMMO.get(fueled?"fueltank":"fueltankempty").toStack());
        h.assertTrue(stack.getItem() instanceof ChainsawItem,"Source workbench produces functional tool");
        h.assertValueEqual(GunItem.rounds(stack),fueled?300:0,"Crafted tank state"); h.assertValueEqual(ChainsawItem.head(stack),0,"Default blade"); h.succeed();
    }
    private static ItemStack upgrade(GameTestHelper h,ItemStack stack,String blade) { return CraftingGameTests.craft(h,2,1,stack,material(blade)); }
    private static boolean matches(GameTestHelper h,ItemStack stack,String blade) {
        return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,CraftingInput.of(2,1,List.of(stack,material(blade))),h.getLevel()).isPresent();
    }
    private static void upgrades(GameTestHelper h,int fuel) {
        var stack=saw(fuel,0); stack.set(DataComponents.CUSTOM_NAME,Component.literal("Saved saw"));
        stack.set(TGContent.RELOAD_TICKS.get(),30); stack.set(TGContent.AIMING.get(),true);
        h.assertTrue(!matches(h,stack,"chainsawblades_carbon"),"Cannot skip obsidian upgrade");
        var obsidian=upgrade(h,stack,"chainsawblades_obsidian");
        h.assertValueEqual(ChainsawItem.head(obsidian),1,"First upgrade"); h.assertValueEqual(GunItem.rounds(obsidian),fuel,"Fuel copied, never refilled");
        h.assertTrue(!obsidian.has(TGContent.RELOAD_TICKS.get()) && !obsidian.has(TGContent.AIMING.get()),"No stale player action copied");
        h.assertTrue(!matches(h,obsidian,"chainsawblades_obsidian"),"No duplicate upgrade");
        var carbon=upgrade(h,obsidian,"chainsawblades_carbon");
        h.assertValueEqual(ChainsawItem.head(carbon),2,"Second upgrade"); h.assertValueEqual(GunItem.rounds(carbon),fuel,"Carbon preserves fuel");
        h.assertTrue(carbon.getHoverName().getString().equals("Saved saw"),"Custom name survives");
        h.assertTrue(!matches(h,carbon,"chainsawblades_obsidian") && !matches(h,carbon,"chainsawblades_carbon"),"Cannot downgrade or reuse final upgrade");
        h.assertValueEqual(ChainsawItem.head(stack),0,"Recipe preview does not mutate ingredient"); h.succeed();
    }
    private static void codecs(GameTestHelper h) {
        var stack=saw(172,2); var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var loaded=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,stack).getOrThrow()).getOrThrow();
        h.assertValueEqual(ChainsawItem.head(loaded),2,"Saved mining head"); h.assertValueEqual(GunItem.rounds(loaded),172,"Saved fuel");
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buf,loaded); var synced=ItemStack.STREAM_CODEC.decode(buf);
            h.assertValueEqual(ChainsawItem.head(synced),2,"Head synchronized to client renderer");
            var input=CraftingInput.of(2,1,List.of(saw(91,0),material("chainsawblades_obsidian")));
            var recipe=(MiningHeadRecipe)h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value();
            MiningHeadRecipe.STREAM_CODEC.encode(buf,recipe); var decoded=MiningHeadRecipe.STREAM_CODEC.decode(buf);
            h.assertTrue(decoded.matches(input,h.getLevel()),"Recipe works after network codec"); h.assertValueEqual(GunItem.rounds(decoded.assemble(input)),91,"Network recipe retains fuel");
        } finally { buf.release(); } h.succeed();
    }
    private static void chemical(GameTestHelper h) {
        var lab=ChemLabGameTests.place(h,new BlockPos(4,2,4)); lab.setItem(0,TGContent.AMMO.get("fueltankempty").toStack());
        ChemLabGameTests.fill(lab,"minecraft:lava",500); ChemLabGameTests.charge(lab,100);
        h.runAfterDelay(105,()-> {
            h.assertTrue(lab.getItem(3).is(TGContent.AMMO.get("fueltank").get()),"Real lab supplies tank");
            h.assertValueEqual(lab.energy().getAmountAsLong(),0L,"100 FE paid"); h.assertTrue(lab.tanks().stack(0).isEmpty(),"500 mB lava paid");
            var stack=saw(0,1); var p=player(h,stack); p.getInventory().setItem(1,lab.removeItem(3,1));
            h.assertTrue(ReloadSessions.begin(p),"Lab tank starts refueling");
            for(int t=0;t<44;t++) p.tick(); h.assertValueEqual(GunItem.rounds(stack),0,"Not ready before 45 ticks");
            p.tick(); h.assertValueEqual(GunItem.rounds(stack),300,"Tank fills 300 units");
            h.assertValueEqual(p.getInventory().countItem(TGContent.AMMO.get("fueltankempty").get()),1,"Empty tank returned once");
            h.assertValueEqual(p.getInventory().countItem(TGContent.AMMO.get("fueltank").get()),0,"Full tank consumed");
            h.assertTrue(GunNetwork.handle(p,new GunActionPayload(false)),"Chemistry product powers actual attack");
            h.assertValueEqual(GunItem.rounds(stack),299,"One unit per attack"); h.assertValueEqual(ChainsawItem.head(stack),1,"Refuel retains upgrade"); h.succeed();
        });
    }
    private static void guards(GameTestHelper h) {
        var stack=saw(1,0); var p=player(h,stack);
        h.assertTrue(GunNetwork.handle(p,new GunActionPayload(false)),"Last unit fires"); h.assertValueEqual(GunItem.rounds(stack),0,"Last unit consumed");
        h.assertTrue(!GunNetwork.handle(p,new GunActionPayload(false)),"Spam rejected");
        for(int t=0;t<3;t++) p.tick(); h.assertTrue(!GunItem.fire(h.getLevel(),p,stack),"Empty tool cannot chain attack");
        p.getAbilities().instabuild=true; stack.set(TGContent.ROUNDS.get(),1);
        h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"Fueled creative attack"); h.assertValueEqual(GunItem.rounds(stack),1,"Creative conserves fuel");
        for(int t=0;t<3;t++) p.tick(); h.assertTrue(!GunItem.fire(h.getLevel(),p,stack.copy()),"Detached stack rejected");
        p.getAbilities().instabuild=false; p.getInventory().setItem(1,TGContent.AMMO.get("fueltank").toStack());
        h.assertTrue(ReloadSessions.begin(p),"Partial tank can refuel"); h.assertTrue(!GunItem.fire(h.getLevel(),p,stack),"Refueling blocks powered attack"); h.succeed();
    }
    private static LivingEntity target(GameTestHelper h) {
        var entity=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,new Vec3(6,2,6)); entity.setNoGravity(true); return entity;
    }
    private static void melee(GameTestHelper h) {
        var stack=saw(1,2); var p=player(h,stack); var target=target(h); p.setOnGround(false);
        for(int t=0;t<20;t++) p.tick();
        near(h,p.getAttributeValue(Attributes.ATTACK_DAMAGE),13,"12 modifier plus native player base 1");
        near(h,p.getAttributeValue(Attributes.ATTACK_SPEED),1.6,"Original attack speed modifier");
        float before=target.getHealth(); p.attack(target); near(h,before-target.getHealth(),13,"Native full-strength powered melee");
        h.assertValueEqual(GunItem.rounds(stack),0,"Accepted hit consumes last fuel");
        for(int t=0;t<20;t++) p.tick(); target.invulnerableTime=0;
        near(h,p.getAttributeValue(Attributes.ATTACK_DAMAGE),3,"Empty tool refreshes native attributes");
        before=target.getHealth(); p.attack(target); near(h,before-target.getHealth(),3,"Native empty melee still works");
        h.assertValueEqual(GunItem.rounds(stack),0,"Empty hit does not underflow"); h.succeed();
    }
    private static void meleeRejected(GameTestHelper h) {
        var stack=saw(5,0); var p=player(h,stack); var target=target(h); target.setInvulnerable(true);
        for(int t=0;t<20;t++) p.tick(); p.attack(target); h.assertValueEqual(GunItem.rounds(stack),5,"Rejected hit consumes no fuel");
        target.setInvulnerable(false); p.getAbilities().instabuild=true; for(int t=0;t<20;t++) p.tick();
        p.attack(target); h.assertValueEqual(GunItem.rounds(stack),5,"Creative melee conserves fuel"); h.succeed();
    }
    private static void meleeArmor(GameTestHelper h) {
        var stack=saw(1,0); var p=player(h,stack); var target=target(h); target.getAttribute(Attributes.ARMOR).setBaseValue(10); p.setOnGround(false);
        for(int t=0;t<20;t++) p.tick(); float before=target.getHealth(); p.attack(target);
        near(h,before-target.getHealth(),9.88,"Native melee gets physical armor and source penetration");
        for(int t=0;t<20;t++) p.tick(); target.invulnerableTime=0; before=target.getHealth(); p.attack(target);
        near(h,before-target.getHealth(),1.8,"Empty melee retains armor but loses penetration"); h.succeed();
    }
    private static ChainsawAttack shot(GameTestHelper h,Vec3 position,Vec3 motion) {
        var attack=new ChainsawAttack(TGContent.CHAINSAW_ATTACK.get(),h.getLevel()); attack.configure(Weapons.definition("chainsaw"));
        attack.setPos(h.absoluteVec(position)); attack.setDeltaMovement(motion); h.getLevel().addFreshEntity(attack); return attack;
    }
    private static void impact(GameTestHelper h) {
        var target=target(h); target.getAttribute(Attributes.ARMOR).setBaseValue(10);
        var a=shot(h,new Vec3(5.1,3,6),new Vec3(1,0,0)); float before=target.getHealth(); a.tick();
        near(h,before-target.getHealth(),7.6,"Physical armor 10 minus penetration 4");
        var b=shot(h,new Vec3(5.1,3,6),new Vec3(1,0,0)); b.tick(); near(h,before-target.getHealth(),7.6,"Source chainsaw respects hurt cooldown");
        target.invulnerableTime=0; var c=shot(h,new Vec3(5.1,3,6),new Vec3(1,0,0)); c.tick(); near(h,before-target.getHealth(),15.2,"Attack lands after immunity expires"); h.succeed();
    }
    private static void wall(GameTestHelper h) {
        h.setBlock(new BlockPos(4,3,5),Blocks.STONE); var a=shot(h,new Vec3(3,3.5,5.5),new Vec3(3,0,0)); a.tick();
        h.assertTrue(a.isRemoved(),"Solid blocks intercept attack"); h.assertBlockPresent(Blocks.STONE,new BlockPos(4,3,5));
        var b=shot(h,new Vec3(2,5,2),new Vec3(0,0,3)); b.tick(); h.assertTrue(!b.isRemoved(),"First movement tick"); b.tick();
        h.assertTrue(b.isRemoved(),"Expires after second movement tick"); near(h,b.position().distanceTo(h.absoluteVec(new Vec3(2,5,2))),6,"Two-tick range, no hits beyond it"); h.succeed();
    }
    private static void savedAttack(GameTestHelper h) {
        h.setBlock(new BlockPos(3,4,3),Blocks.WATER); var a=shot(h,new Vec3(3.2,4.2,3.2),new Vec3(.2,0,0)); a.npcDamage(.6f); a.tick();
        near(h,a.getDeltaMovement().x,.2,"Water does not slow chain attack");
        var saved=NetherGameTests.save(h,a); a.discard(); var b=new ChainsawAttack(TGContent.CHAINSAW_ATTACK.get(),h.getLevel()); NetherGameTests.load(h,b,saved);
        h.assertValueEqual(b.weapon().id(),"chainsaw","Projectile family survives load"); near(h,b.shotDamage().scale(),.6,"NPC difficulty survives load");
        b.tick(); h.assertTrue(b.isRemoved(),"Remaining lifetime survives load"); h.succeed();
    }
    private static void cancel(GameTestHelper h) {
        var stack=saw(8,0); var p=player(h,stack);
        Consumer<EntityJoinLevelEvent> listener=e->{ if(e.getEntity() instanceof ChainsawAttack && e.getEntity().level()==h.getLevel()) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(listener);
        try { h.assertTrue(!GunItem.fire(h.getLevel(),p,stack),"Canceled projectile reports failure"); h.assertValueEqual(GunItem.rounds(stack),8,"No fuel consumed for canceled spawn"); }
        finally { NeoForge.EVENT_BUS.unregister(listener); } h.succeed();
    }
    private static void breaking(GameTestHelper h,boolean fueled) {
        var level=h.getLevel(); var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"chainsaw-test"),false);
        var p=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation()) {
            @Override public GameType gameMode() { return GameType.SURVIVAL; }
            @Override public boolean isClientAuthoritative() { return false; }
        };
        var connection=new Connection(PacketFlow.SERVERBOUND); var channel=new EmbeddedChannel(connection);
        p.connection=new ServerGamePacketListenerImpl(level.getServer(),connection,p,cookie);
        p.snapTo(h.absoluteVec(new Vec3(4,2,2))); var stack=saw(fueled?1:0,0); p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var mode=new ServerPlayerGameMode(p); var pos=new BlockPos(4,2,3); h.setBlock(pos,Blocks.OAK_LOG);
        try {
            mode.changeGameModeForPlayer(GameType.SURVIVAL); GameType.SURVIVAL.updatePlayerAbilities(p.getAbilities());
            h.assertTrue(mode.destroyBlock(h.absolutePos(pos)),"Native server block destruction succeeds"); h.assertValueEqual(GunItem.rounds(stack),0,"Exactly one fuel for a block, never below zero");
            int drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(h.absolutePos(pos)).inflate(1),e->e.getItem().is(Items.OAK_LOG)).stream().mapToInt(e->e.getItem().getCount()).sum();
            h.assertValueEqual(drops,1,"Native block drop survives last fuel and empty tool");
            near(h,stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()),1,"Next block loses powered speed");
            h.setBlock(pos,Blocks.CHEST); p.setShiftKeyDown(true);
            var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(h.absolutePos(pos)),net.minecraft.core.Direction.NORTH,h.absolutePos(pos),false);
            h.assertTrue(mode.useItemOn(p,level,stack,InteractionHand.MAIN_HAND,hit).consumesAction(),"Sneak use reaches native chest interaction");
            h.assertTrue(p.containerMenu!=p.inventoryMenu,"Chest menu opens with chainsaw and empty offhand");
            h.assertValueEqual(GunItem.rounds(stack),0,"Opening chest spends no fuel"); p.closeContainer();
        } finally { channel.finishAndReleaseAll(); } h.succeed();
    }
}
