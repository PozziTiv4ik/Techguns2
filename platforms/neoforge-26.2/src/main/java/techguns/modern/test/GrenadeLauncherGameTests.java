package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.npc.*;
import techguns.modern.network.*;

final class GrenadeLauncherGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(String mode:List.of("full","partial","creative","removed","swap"))r.register("launcher_reload_"+mode,()->h->reload(h,mode));
        r.register("launcher_fire_cooldown_and_cancelled_spawn",()->GrenadeLauncherGameTests::fire);
        r.register("launcher_native_recipe_and_item_sync",()->GrenadeLauncherGameTests::recipe);
        r.register("launcher_flight_chunk_save_and_water",()->GrenadeLauncherGameTests::flight);
        for(var face:Direction.values())r.register("launcher_bounce_"+face.getName(),()->h->bounce(h,face));
        r.register("launcher_two_bounces_then_explosion_and_ttl",()->GrenadeLauncherGameTests::lifetime);
        r.register("launcher_direct_hit_falloff_and_armor",()->GrenadeLauncherGameTests::direct);
        r.register("launcher_blast_bands_armor_and_walls",()->GrenadeLauncherGameTests::blast);
        for(String mode:List.of("impact","damage","explosion","lists"))r.register("launcher_protection_"+mode,()->h->protect(h,mode));
        r.register("launcher_invalid_saved_profile",()->GrenadeLauncherGameTests::invalid);
        r.register("launcher_npc_equipped_weapon_and_damage",()->GrenadeLauncherGameTests::npc);
        r.register("launcher_real_server_flight",()->GrenadeLauncherGameTests::nativeFlight);
    }
    private static void near(GameTestHelper h,double a,double b,String text){h.assertTrue(Math.abs(a-b)<.001,text+": "+a+" != "+b);}
    private static Player player(GameTestHelper h,int rounds){var p=WeaponGameTests.player(h);p.setPos(h.absoluteVec(new Vec3(2,70,2)));p.getInventory().clearContent();var s=TGContent.GUNS.get("grenadelauncher").toStack();s.set(TGContent.ROUNDS.get(),rounds);p.setItemInHand(InteractionHand.MAIN_HAND,s);return p;}
    private static Grenade40mmProjectile shot(GameTestHelper h,Vec3 pos){var p=new Grenade40mmProjectile(TGContent.GRENADE_40MM.get(),h.getLevel());p.setPos(h.absoluteVec(pos));return p;}
    private static LivingEntity target(GameTestHelper h,Vec3 pos){var v=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,pos);v.setNoGravity(true);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);v.setHealth(1000);return v;}
    private static void reload(GameTestHelper h,String mode){
        var p=player(h,2);var stack=p.getMainHandItem();boolean creative=mode.equals("creative");p.getAbilities().instabuild=creative;
        p.getInventory().setItem(1,TGContent.AMMO.get("40mmgrenade").toStack(mode.equals("partial")?1:2));p.getInventory().setItem(2,mode.equals("partial")?ItemStack.EMPTY:TGContent.AMMO.get("40mmgrenade").toStack(5));
        int before=GunItem.availableAmmo(p,stack);h.assertTrue(GunNetwork.handle(p,new GunActionPayload(true)),"R packet starts 100-tick reload");
        for(int i=0;i<99;i++)p.tick();h.assertValueEqual(GunItem.rounds(stack),2,"No early reload");h.assertValueEqual(GunItem.availableAmmo(p,stack),before,"Ammo not consumed early");
        if(mode.equals("removed")) {p.getInventory().setItem(1,ItemStack.EMPTY);p.getInventory().setItem(2,ItemStack.EMPTY);}
        if(mode.equals("swap"))p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));p.tick();
        int expected=mode.equals("partial")?3:mode.equals("removed")||mode.equals("swap")?2:6;
        h.assertValueEqual(GunItem.rounds(stack),expected,"Atomic completion keeps old shells and loads available ones");
        if(!mode.equals("removed"))h.assertValueEqual(GunItem.availableAmmo(p,Weapons.definition("grenadelauncher")),before-(creative?0:expected-2),"Exact individual consumption across stacks");
        h.assertTrue(!ReloadSessions.active(p) && !stack.has(TGContent.RELOAD_TICKS.get()),"Transient reload clears");p.discard();h.succeed();
    }
    private static void fire(GameTestHelper h){
        var p=player(h,6);var stack=p.getMainHandItem();List<Grenade40mmProjectile> shots=new ArrayList<>();
        Consumer<EntityJoinLevelEvent> capture=e->{if(e.getEntity() instanceof Grenade40mmProjectile s && s.getOwner()==p)shots.add(s);};NeoForge.EVENT_BUS.addListener(capture);
        try {
            h.assertTrue(GunNetwork.handle(p,new GunActionPayload(false)),"Fire packet spawns actual 40mm projectile");h.assertValueEqual(shots.size(),1,"One shell");h.assertValueEqual(GunItem.rounds(stack),5,"One charge consumed");
            h.assertValueEqual(stack.get(TGContent.LAUNCHER_SHOT_TIME.get()),h.getLevel().getGameTime(),"Accepted shot starts drum animation");
            var s=shots.getFirst();h.assertValueEqual(s.bounces(),2,"40mm factory bounce count");near(h,s.gravity(),.01,"Constant launcher gravity");h.assertTrue(s.getDeltaMovement().length()>.68 && s.getDeltaMovement().length()<.82,"Source 1.5 × .5 velocity");
            for(int i=0;i<5;i++){h.assertTrue(!GunNetwork.handle(p,new GunActionPayload(false)),"Spam cannot bypass cooldown");p.getCooldowns().tick();}
            p.getAbilities().instabuild=true;h.assertTrue(GunNetwork.handle(p,new GunActionPayload(false)),"Creative can shoot");h.assertValueEqual(GunItem.rounds(stack),4,"GenericGun still consumes loaded round in Creative");
        } finally {NeoForge.EVENT_BUS.unregister(capture);shots.forEach(Entity::discard);}
        for(int i=0;i<5;i++)p.getCooldowns().tick();stack.remove(TGContent.LAUNCHER_SHOT_TIME.get());
        Consumer<EntityJoinLevelEvent> cancel=e->{if(e.getEntity() instanceof Grenade40mmProjectile s && s.getOwner()==p)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(cancel);
        try {h.assertTrue(!GunItem.fire(h.getLevel(),p,stack),"Cancelled spawn rejects shot");h.assertValueEqual(GunItem.rounds(stack),4,"Cancelled shot preserves ammunition");h.assertTrue(!stack.has(TGContent.LAUNCHER_SHOT_TIME.get()) && !p.getCooldowns().isOnCooldown(stack),"Rejected shot has no animation or cooldown");}
        finally {NeoForge.EVENT_BUS.unregister(cancel);p.discard();}h.succeed();
    }
    private static void recipe(GameTestHelper h){
        var manager=h.getLevel().getServer().getRecipeManager();var recipe=manager.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,TGContent.id("grenadelauncher"))).orElseThrow().value();
        h.assertTrue(recipe instanceof ShapedRecipe,"Original shaped recipe");var shaped=(ShapedRecipe)recipe;
        // Use the actual registry ingredients in the source m/r/s + steel-plate pattern.
        var input=CraftingInput.of(3,2,List.of(TGContent.MATERIALS.get("obsidiansteelbarrel").toStack(),TGContent.MATERIALS.get("steelreceiver").toStack(),TGContent.MATERIALS.get("plasticstock").toStack(),ItemStack.EMPTY,TGContent.MATERIALS.get("platesteel").toStack(),ItemStack.EMPTY));
        h.assertTrue(shaped.matches(input,h.getLevel()),"Source components craft launcher");var result=shaped.assemble(input);h.assertTrue(result.is(TGContent.GUNS.get("grenadelauncher").get()),"Working GunItem result");h.assertValueEqual(GunItem.rounds(result),0,"Source metadata 6 is empty");
        result.set(TGContent.ROUNDS.get(),4);result.set(TGContent.LAUNCHER_SHOT_TIME.get(),123L);
        var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);var saved=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,result).getOrThrow()).getOrThrow();
        h.assertValueEqual(GunItem.rounds(saved),4,"Ammo survives save");h.assertTrue(!saved.has(TGContent.LAUNCHER_SHOT_TIME.get()),"Transient recoil never persists");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {ItemStack.STREAM_CODEC.encode(buffer,result);var copy=ItemStack.STREAM_CODEC.decode(buffer);h.assertValueEqual(copy.get(TGContent.LAUNCHER_SHOT_TIME.get()),123L,"Drum time syncs to observing clients");}
        finally{buffer.release();}h.succeed();
    }
    private static void flight(GameTestHelper h){
        var owner=target(h,new Vec3(2,75,2));var p=shot(h,new Vec3(4,80,4));p.setOwner(owner);p.npcDamage(.8f);p.setPos((Math.floor(p.getX()/16)+1)*16-.2,p.getY(),p.getZ());p.setDeltaMovement(.75,.2,0);var start=p.position();p.tick();
        near(h,p.getX()-start.x,.75,"Crosses chunk using original velocity");near(h,p.getDeltaMovement().y,.2*(double).99f-.01,"Air drag then fixed gravity");h.assertTrue((int)Math.floor(start.x/16)!=(int)Math.floor(p.getX()/16),"Actual chunk edge crossed");
        var copy=shot(h,Vec3.ZERO);NetherGameTests.load(h,copy,NetherGameTests.save(h,p));h.assertValueEqual(copy.getOwner(),owner,"Owner restored");h.assertValueEqual(copy.shotDamage(),p.shotDamage(),"NPC scale persists");h.assertValueEqual(copy.weapon(),p.weapon(),"Correct gun profile persists");
        p.tick();copy.tick();near(h,p.position().distanceTo(copy.position()),0,"Saved trajectory resumes");p.discard();copy.discard();owner.discard();
        var block=new BlockPos(4,80,4);h.setBlock(block,Blocks.WATER);p=shot(h,new Vec3(4.5,80.4,4.5));p.setDeltaMovement(.1,0,0);
        try{p.tick();near(h,p.getDeltaMovement().x,.1*(double).85f,"Source water drag");h.assertValueEqual(p.bounces(),2,"Water is not a bounce surface");}finally{p.discard();h.setBlock(block,Blocks.AIR);}h.succeed();
    }
    private static void bounce(GameTestHelper h,Direction face){
        var block=new BlockPos(4,80,4);h.setBlock(block,Blocks.STONE);var p=shot(h,Vec3.atCenterOf(block).add(face.getUnitVec3().scale(1.2)));p.setDeltaMovement(face.getUnitVec3().scale(-1));
        try{p.tick();h.assertValueEqual(p.bounces(),1,"One bounce consumed");near(h,p.getDeltaMovement().distanceTo(face.getUnitVec3().scale(.5)),0,"Reflected and halved");var copy=shot(h,Vec3.ZERO);NetherGameTests.load(h,copy,NetherGameTests.save(h,p));h.assertValueEqual(copy.bounces(),1,"Bounce save");p.tick();copy.tick();near(h,p.position().distanceTo(copy.position()),0,"Bounce resumes after load");copy.discard();}
        finally{p.discard();h.setBlock(block,Blocks.AIR);}h.succeed();
    }
    private static void lifetime(GameTestHelper h){
        var block=new BlockPos(4,80,4);h.setBlock(block,Blocks.STONE);var p=shot(h,new Vec3(2.5,80.5,4.5));int[] explosions={0};Consumer<ExplosionEvent.Start> count=e->{if(e.getExplosion().getDirectSourceEntity()==p)explosions[0]++;};NeoForge.EVENT_BUS.addListener(count);
        try{for(int i=0;i<3;i++){p.setPos(h.absoluteVec(new Vec3(2.5,80.5,4.5)));p.setDeltaMovement(2,0,0);p.tick();h.assertTrue(p.isRemoved()==(i==2),"Third block contact explodes");}h.assertValueEqual(explosions[0],1,"One explosion");p.explode();h.assertValueEqual(explosions[0],1,"No repeat explosion");}
        finally{NeoForge.EVENT_BUS.unregister(count);p.discard();h.setBlock(block,Blocks.AIR);}
        var expired=shot(h,new Vec3(4,80,4));var tag=NetherGameTests.save(h,expired);tag.putInt("age",158);NetherGameTests.load(h,expired,tag);
        Consumer<ExplosionEvent.Start> forbidden=e->{if(e.getExplosion().getDirectSourceEntity()==expired)throw new AssertionError("Expiry must not explode");};NeoForge.EVENT_BUS.addListener(forbidden);
        try{expired.tick();h.assertTrue(!expired.isRemoved(),"Alive at 159");expired.tick();h.assertTrue(expired.isRemoved(),"Silent expiry at 160");}finally{NeoForge.EVENT_BUS.unregister(forbidden);expired.discard();}h.succeed();
    }
    private static void direct(GameTestHelper h){
        for(double distance:new double[]{0,6,30}){
            var victim=target(h,new Vec3(6,80,3));var p=shot(h,new Vec3(4,81,3));var tag=NetherGameTests.save(h,p);tag.put("origin",Vec3.CODEC.encodeStart(NbtOps.INSTANCE,p.position().add(-distance,0,0)).getOrThrow());NetherGameTests.load(h,p,tag);p.setDeltaMovement(4,0,0);
            Consumer<ExplosionEvent.Start> cancel=e->{if(e.getExplosion().getDirectSourceEntity()==p)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(cancel);
            try{p.tick();near(h,1000-victim.getHealth(),Weapons.definition("grenadelauncher").stats().damageAt(distance)+.01,"Inherited direct projectile falloff");}finally{NeoForge.EVENT_BUS.unregister(cancel);p.discard();victim.discard();}
        }
        var victim=target(h,new Vec3(6,80,3));var p=shot(h,new Vec3(4,81,3));p.setDeltaMovement(4,0,0);try{p.tick();near(h,1000-victim.getHealth(),30.01,"Same-tick blast does not duplicate direct damage");}finally{p.discard();victim.discard();}h.succeed();
    }
    private static void blast(GameTestHelper h){
        for(boolean armored:new boolean[]{false,true}){
            var p=shot(h,new Vec3(3,80,3));List<LivingEntity> victims=new ArrayList<>();
            try{for(double d:new double[]{4,4.01,6,8,8.01}){var v=target(h,new Vec3(3+d,80,3));v.setPos(p.position().add(d,-v.getEyeHeight(),0));if(armored)v.getAttribute(Attributes.ARMOR).setBaseValue(20);victims.add(v);}p.explode();for(var v:victims){float raw=p.blastDamage(p.position().distanceTo(v.getEyePosition()));near(h,1000-v.getHealth(),armored?ArmorMath.afterArmor(raw,10,0,0):raw,"Original band and half armor");}}
            finally{victims.forEach(Entity::discard);p.discard();}
        }
        var blocks=new ArrayList<BlockPos>();for(int y=79;y<84;y++)for(int z=1;z<6;z++){var b=new BlockPos(5,y,z);h.setBlock(b,Blocks.STONE);blocks.add(b);}
        var p=shot(h,new Vec3(3.5,81,3));var owner=player(h,1);owner.setData(SafeMode.SAFE,false);p.setOwner(owner);var target=target(h,new Vec3(6.5,80,3));
        try{p.explode();near(h,target.getHealth(),1000,"Wall blocks blast even with unsafe mode");h.assertTrue(blocks.stream().allMatch(b->h.getBlockState(b).is(Blocks.STONE)),"Source zero terrain factor remains authoritative");}finally{blocks.forEach(b->h.setBlock(b,Blocks.AIR));target.discard();p.discard();owner.discard();}h.succeed();
    }
    private static void protect(GameTestHelper h,String mode){
        var p=shot(h,new Vec3(4,81,3));var victim=target(h,new Vec3(6,80,3));var bystander=target(h,new Vec3(4,80,5));p.setDeltaMovement(4,0,0);var block=new BlockPos(4,78,3);h.setBlock(block,Blocks.TNT);int[] blasts={0};
        Consumer<ProjectileImpactEvent> impact=e->{if(e.getProjectile()==p && mode.equals("impact"))e.setCanceled(true);};
        Consumer<LivingIncomingDamageEvent> damage=e->{if(e.getSource().getDirectEntity()==p && mode.equals("damage"))e.setCanceled(true);};
        Consumer<ExplosionEvent.Start> explosion=e->{if(e.getExplosion().getDirectSourceEntity()==p){blasts[0]++;if(mode.equals("explosion"))e.setCanceled(true);}};
        Consumer<ExplosionEvent.Detonate> lists=e->{if(e.getExplosion().getDirectSourceEntity()==p && mode.equals("lists")){e.getAffectedEntities().clear();e.getAffectedBlocks().add(h.absolutePos(block));}};
        NeoForge.EVENT_BUS.addListener(impact);NeoForge.EVENT_BUS.addListener(damage);NeoForge.EVENT_BUS.addListener(explosion);NeoForge.EVENT_BUS.addListener(lists);
        try{p.tick();h.assertValueEqual(blasts[0],mode.equals("impact")||mode.equals("damage")?0:1,"Only successful living hit attempts blast");near(h,bystander.getHealth(),1000,"Protection suppresses blast damage");h.assertTrue(h.getBlockState(block).is(Blocks.TNT),"Event-added TNT is never primed");h.assertTrue(p.isRemoved()!=mode.equals("impact"),"Cancelled impact keeps flight");}
        finally{NeoForge.EVENT_BUS.unregister(impact);NeoForge.EVENT_BUS.unregister(damage);NeoForge.EVENT_BUS.unregister(explosion);NeoForge.EVENT_BUS.unregister(lists);p.discard();victim.discard();bystander.discard();h.setBlock(block,Blocks.AIR);}h.succeed();
    }
    private static void invalid(GameTestHelper h){for(String mode:List.of("weapon","gravity","age","bounces","scale")){var p=shot(h,new Vec3(4,80,4));var tag=NetherGameTests.save(h,p);switch(mode){case "weapon"->tag.putString("weapon","revolver");case "gravity"->tag.putFloat("gravity",.015f);case "age"->tag.putInt("age",160);case "bounces"->tag.putInt("bounces",3);case "scale"->tag.putFloat("damage_scale",Float.NaN);}NetherGameTests.load(h,p,tag);h.assertTrue(p.isRemoved(),"Invalid "+mode+" rejected");}h.succeed();}
    private static void npc(GameTestHelper h){
        var npc=h.spawnWithNoFreeWill(NpcContent.ARMY.get(),new Vec3(3,80,3));var target=target(h,new Vec3(9,80,3));npc.setItemInHand(InteractionHand.MAIN_HAND,TGContent.GUNS.get("grenadelauncher").toStack());npc.setYHeadRot(-90);npc.setXRot(0);List<Grenade40mmProjectile> shots=new ArrayList<>();
        Consumer<EntityJoinLevelEvent> capture=e->{if(e.getEntity() instanceof Grenade40mmProjectile p && p.getOwner()==npc)shots.add(p);};NeoForge.EVENT_BUS.addListener(capture);
        try{h.assertTrue(npc.fireAt(target),"Equipped NPC fires real launcher");h.assertValueEqual(shots.size(),1,"One 40mm projectile");var p=shots.getFirst();h.assertTrue(p.shotDamage().npc(),"NPC difficulty policy attached");h.assertValueEqual(GunItem.rounds(npc.getMainHandItem()),0,"NPC does not use player ammunition");h.assertTrue(npc.getMainHandItem().has(TGContent.LAUNCHER_SHOT_TIME.get()),"NPC drum sync starts");}
        finally{NeoForge.EVENT_BUS.unregister(capture);shots.forEach(Entity::discard);npc.discard();target.discard();}
        var p=shot(h,new Vec3(3,80,3));var victim=target(h,new Vec3(4,79,3));double old=NpcConfig.DAMAGE_FACTOR.get();
        try{NpcConfig.DAMAGE_FACTOR.set(1.5);p.npcDamage(.8f);p.explode();near(h,1000-victim.getHealth(),30*.8*1.5,"NPC launch scale and server damage factor each applied once");}
        finally{NpcConfig.DAMAGE_FACTOR.set(old);p.discard();victim.discard();}
        var zero=shot(h,new Vec3(4,81,3));var immune=target(h,new Vec3(6,80,3));zero.npcDamage(0);zero.setDeltaMovement(4,0,0);
        try{zero.tick();near(h,immune.getHealth(),1000,"Zero NPC damage never creates a dummy impulse or explosion");h.assertTrue(zero.isRemoved(),"Harmless impact still ends flight");}
        finally{zero.discard();immune.discard();}h.succeed();
    }
    private static void nativeFlight(GameTestHelper h){var p=shot(h,new Vec3(4,80,4));p.setDeltaMovement(.2,0,0);var pos=p.position();h.getLevel().addFreshEntity(p);h.runAfterDelay(4,()->{try{h.assertTrue(p.age()>=3 && p.getX()>pos.x && p.getY()<pos.y,"Real server advances 40mm physics");}finally{p.discard();}h.succeed();});}
}
