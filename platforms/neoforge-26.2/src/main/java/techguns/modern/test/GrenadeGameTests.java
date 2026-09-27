package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;

final class GrenadeGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var g:HandGrenade.values()) {
            for(int ticks:new int[]{0,1,15,30,90}) r.register("grenade_release_"+g.id()+"_"+ticks,()->h->release(h,g,ticks));
            for(var face:Direction.values()) r.register("grenade_bounce_"+g.id()+"_"+face.getName(),()->h->bounce(h,g,face));
            r.register("grenade_last_bounce_"+g.id(),()->h->lastBounce(h,g));
            r.register("grenade_flight_save_"+g.id(),()->h->flight(h,g));
            r.register("grenade_blast_band_"+g.id(),()->h->band(h,g));
            r.register("grenade_direct_hit_"+g.id(),()->h->direct(h,g));
            r.register("grenade_expiry_"+g.id(),()->h->expiry(h,g));
            r.register("grenade_wall_permissions_"+g.id(),()->h->wall(h,g));
            r.register("grenade_armor_"+g.id(),()->h->armor(h,g));
            r.register("grenade_crafting_"+g.id(),()->h->crafting(h,g));
        }
        r.register("grenade_cancel_spawn_start_stop_and_replacement",()->GrenadeGameTests::cancelUse);
        r.register("grenade_cancel_impact_damage_and_blast",()->GrenadeGameTests::cancelImpact);
        r.register("grenade_detonation_lists_cannot_break_blocks",()->GrenadeGameTests::detonation);
        r.register("grenade_water_and_slow_floor_bounce",()->GrenadeGameTests::water);
        r.register("grenade_invalid_save_and_entity_sync",()->GrenadeGameTests::invalidAndSync);
        r.register("grenade_owner_and_friendly_player",()->GrenadeGameTests::friendly);
        r.register("grenade_actual_server_flight",()->GrenadeGameTests::serverFlight);
    }
    private static void near(GameTestHelper h,double actual,double expected,String reason) {
        h.assertTrue(Math.abs(actual-expected)<.001,reason+": "+actual+" != "+expected);
    }
    private static GrenadeProjectile projectile(GameTestHelper h,HandGrenade g,Vec3 relative) {
        var p=new GrenadeProjectile(TGContent.HAND_GRENADE.get(),h.getLevel()); p.configure(g,30); p.setPos(h.absoluteVec(relative)); return p;
    }
    private static Player player(GameTestHelper h,HandGrenade g,int count) {
        var p=WeaponGameTests.player(h); p.getInventory().clearContent(); p.setPos(h.absoluteVec(new Vec3(2,70,2)));
        p.setItemInHand(InteractionHand.MAIN_HAND,TGContent.GRENADES.get(g.id()).toStack(count)); return p;
    }
    private static LivingEntity target(GameTestHelper h,Vec3 relative) {
        var p=h.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM,relative); p.setNoGravity(true);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); p.setHealth(1000); return p;
    }
    private static void release(GameTestHelper h,HandGrenade g,int held) {
        for(var hand:InteractionHand.values()) for(var arm:HumanoidArm.values()) for(boolean creative:new boolean[]{false,true}) {
            int count=held==0?1:2;
            var player=player(h,g,count); player.setMainArm(arm); player.getAbilities().instabuild=creative;
            var stack=player.getMainHandItem(); player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); player.setItemInHand(hand,stack);
            List<GrenadeProjectile> shots=new ArrayList<>();
            Consumer<EntityJoinLevelEvent> capture=e->{if(e.getEntity() instanceof GrenadeProjectile p && p.getOwner()==player) shots.add(p);};
            NeoForge.EVENT_BUS.addListener(capture);
            try {
                var item=(GrenadeItem)stack.getItem(); near(h,stack.getMaxStackSize(),16,"Original stack size");
                item.use(h.getLevel(),player,hand); h.assertTrue(player.isUsingItem(),"Real item use starts");
                for(int tick=0;tick<held;tick++) player.tick();
                h.assertValueEqual(shots.size(),0,"Holding never throws or cooks grenade");
                player.releaseUsingItem();
                h.assertValueEqual(shots.size(),1,"Native release throws exactly once");
                var shot=shots.getFirst(); h.assertValueEqual(shot.grenade(),g,"Correct entity variant");
                near(h,shot.gravity(),g.gravity(held),"Charge controls gravity");
                h.assertTrue(shot.getDeltaMovement().length()>.99 && shot.getDeltaMovement().length()<1.25,"Charge does not scale the source 1.5 × .75 speed");
                boolean left=(hand==InteractionHand.OFF_HAND)!=(arm==HumanoidArm.LEFT);
                h.assertTrue((shot.getX()-player.getX()>0)==left,"Main/off hand respects dominant arm");
                h.assertValueEqual(stack.getCount(),creative?count:count-1,"Survival consumes one, including the last grenade; creative preserves stack");
                player.releaseUsingItem(); h.assertValueEqual(shots.size(),1,"Repeated stop cannot duplicate throw");
            } finally { NeoForge.EVENT_BUS.unregister(capture); shots.forEach(Entity::discard); player.discard(); }
        }
        h.succeed();
    }
    private static void bounce(GameTestHelper h,HandGrenade g,Direction face) {
        var block=new BlockPos(4,80,4); h.setBlock(block,Blocks.STONE);
        Vec3 normal=face.getUnitVec3(), center=Vec3.atCenterOf(block);
        var p=projectile(h,g,center.add(normal.scale(1.2))); p.setDeltaMovement(normal.scale(-1));
        try {
            p.tick(); h.assertTrue(!p.isRemoved(),"Allowed block bounce survives");
            h.assertValueEqual(p.bounces(),g.bounces-1,"Exactly one bounce consumed");
            near(h,p.getDeltaMovement().distanceTo(normal.scale(.5)),0,"Face reflection halves every component before drag");
            h.assertValueEqual(p.age(),1,"Bounce consumes lifetime");
            var copy=projectile(h,g,Vec3.ZERO); NetherGameTests.load(h,copy,NetherGameTests.save(h,p));
            h.assertValueEqual(copy.bounces(),p.bounces(),"Bounce count survives save");
            p.tick(); copy.tick(); near(h,p.position().distanceTo(copy.position()),0,"Restored bounce resumes identical flight"); copy.discard();
        } finally {p.discard();h.setBlock(block,Blocks.AIR);}
        h.succeed();
    }
    private static void lastBounce(GameTestHelper h,HandGrenade g) {
        var block=new BlockPos(4,80,4);h.setBlock(block,Blocks.STONE);
        var p=projectile(h,g,new Vec3(2.5,80.5,4.5));
        int[] blasts={0};Consumer<ExplosionEvent.Start> listen=e->{if(e.getExplosion().getDirectSourceEntity()==p) blasts[0]++;};
        NeoForge.EVENT_BUS.addListener(listen);
        try {
            for(int i=0;i<g.bounces;i++) {
                p.setPos(h.absoluteVec(new Vec3(2.5,80.5,4.5)));p.setDeltaMovement(2,0,0);p.tick();
                h.assertTrue(!p.isRemoved(),"Survives bounce "+i); h.assertValueEqual(blasts[0],0,"No early explosion");
            }
            p.setPos(h.absoluteVec(new Vec3(2.5,80.5,4.5)));p.setDeltaMovement(2,0,0);p.tick();
            h.assertTrue(p.isRemoved(),"Next collision explodes"); h.assertValueEqual(blasts[0],1,"Only one terminal explosion");
            p.explode();h.assertValueEqual(blasts[0],1,"Removed projectile cannot explode twice");
        } finally {NeoForge.EVENT_BUS.unregister(listen);h.setBlock(block,Blocks.AIR);p.discard();}
        h.succeed();
    }
    private static void flight(GameTestHelper h,HandGrenade g) {
        var owner=target(h,new Vec3(2,75,2)); var p=projectile(h,g,new Vec3(4,80,4));p.setOwner(owner);p.configure(g,15);
        double x=(Math.floor(p.getX()/16)+1)*16-.4;p.setPos(x,p.getY(),p.getZ());p.setDeltaMovement(1,.2,.1);
        try {
            var start=p.position();p.tick();near(h,p.getX()-start.x,1,"Position uses pre-drag motion");
            near(h,p.getDeltaMovement().y,.2*(double).99f-g.gravity(15),"Drag then charge gravity");
            h.assertTrue((int)Math.floor(start.x/16)!=(int)Math.floor(p.getX()/16),"Flight actually crossed chunk boundary");
            var copy=projectile(h,g,Vec3.ZERO);NetherGameTests.load(h,copy,NetherGameTests.save(h,p));
            h.assertValueEqual(copy.getOwner(),owner,"Owner UUID resolves after save/load");h.assertValueEqual(copy.age(),1,"Age persists");
            near(h,copy.gravity(),p.gravity(),"Charge persists");p.tick();copy.tick();near(h,p.position().distanceTo(copy.position()),0,"Trajectory persists");copy.discard();
        } finally {owner.discard();p.discard();}
        h.succeed();
    }
    private static void band(GameTestHelper h,HandGrenade g) {
        var p=projectile(h,g,new Vec3(3,80,3)); List<LivingEntity> victims=new ArrayList<>();
        try {
            for(double d:new double[]{g.innerRadius,g.innerRadius+.01,(g.innerRadius+g.outerRadius)/2,g.outerRadius,g.outerRadius+.01}) {
                var target=target(h,new Vec3(3+d,80,3));target.setPos(p.position().add(d,-target.getEyeHeight(),0));victims.add(target);
            }
            h.assertTrue(p.explode(),"Native explosion accepted");
            for(var victim:victims)near(h,1000-victim.getHealth(),g.blastDamage(p.position().distanceTo(victim.getEyePosition())),"Source rising outer band at eye distance");
        } finally {victims.forEach(Entity::discard);p.discard();}
        h.succeed();
    }
    private static void direct(GameTestHelper h,HandGrenade g) {
        var target=target(h,new Vec3(6,80,3));var p=projectile(h,g,new Vec3(4,81,3));p.setDeltaMovement(4,0,0);
        int[] blasts={0}; Consumer<ExplosionEvent.Start> listen=e->{if(e.getExplosion().getDirectSourceEntity()==p)blasts[0]++;}; NeoForge.EVENT_BUS.addListener(listen);
        try {p.tick();near(h,1000-target.getHealth(),g.damage+.01,"Inherited bullet hit plus tiny physical impulse; blast respects hurt cooldown");h.assertValueEqual(blasts[0],1,"Successful living hit triggers blast");}
        finally {NeoForge.EVENT_BUS.unregister(listen);target.discard();p.discard();}
        // Direct falloff uses displacement from this bounce's start, not accumulated path length.
        target=target(h,new Vec3(6,80,3));var longShot=projectile(h,g,new Vec3(4,81,3));
        var tag=NetherGameTests.save(h,longShot);tag.put("origin",Vec3.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,longShot.position().add(-30,0,0)).getOrThrow());
        NetherGameTests.load(h,longShot,tag);longShot.setDeltaMovement(4,0,0);
        Consumer<ExplosionEvent.Start> cancel=e->{if(e.getExplosion().getDirectSourceEntity()==longShot)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(cancel);
        try {longShot.tick();near(h,1000-target.getHealth(),g.minimumDamage+.01,"Saved origin controls impact minimum");}
        finally {NeoForge.EVENT_BUS.unregister(cancel);target.discard();longShot.discard();}
        h.succeed();
    }
    private static void expiry(GameTestHelper h,HandGrenade g) {
        var p=projectile(h,g,new Vec3(4,80,4));var tag=NetherGameTests.save(h,p);tag.putInt("age",198);NetherGameTests.load(h,p,tag);
        int[] blasts={0};Consumer<ExplosionEvent.Start> listen=e->{if(e.getExplosion().getDirectSourceEntity()==p)blasts[0]++;};NeoForge.EVENT_BUS.addListener(listen);
        try {p.tick();h.assertTrue(!p.isRemoved(),"Alive at 199");p.tick();h.assertTrue(p.isRemoved(),"Expires at 200");h.assertValueEqual(blasts[0],0,"TTL is not a fuse");}
        finally {NeoForge.EVENT_BUS.unregister(listen);p.discard();} h.succeed();
    }
    private static void wall(GameTestHelper h,HandGrenade g) {
        List<BlockPos> blocks=new ArrayList<>();for(int y=79;y<=83;y++)for(int z=1;z<=5;z++){var pos=new BlockPos(5,y,z);h.setBlock(pos,Blocks.STONE);blocks.add(pos);}
        boolean previous=SafeMode.OP_ONLY.get();
        try {
            for(boolean restricted:new boolean[]{false,true})for(boolean safe:new boolean[]{false,true}) {
                SafeMode.OP_ONLY.set(restricted);var owner=player(h,g,1);owner.setData(SafeMode.SAFE,safe);
                var p=projectile(h,g,new Vec3(3.5,81,3));p.setOwner(owner);var target=target(h,new Vec3(6.5,80,3));
                var tag=NetherGameTests.save(h,p);tag.putInt("bounces",0);NetherGameTests.load(h,p,tag);p.setOwner(owner);p.setDeltaMovement(4,0,0);
                try {p.tick();near(h,target.getHealth(),1000,"Solid wall shields both direct hit and blast");h.assertTrue(!h.getBlockState(new BlockPos(5,81,3)).isAir(),"No terrain damage in any B/permission mode");}
                finally {p.discard();target.discard();owner.discard();}
            }
        } finally {SafeMode.OP_ONLY.set(previous);blocks.forEach(pos->h.setBlock(pos,Blocks.AIR));}h.succeed();
    }
    private static void armor(GameTestHelper h,HandGrenade g) {
        var victim=target(h,new Vec3(4,80,4));victim.getAttribute(Attributes.ARMOR).setBaseValue(20);
        var p=projectile(h,g,new Vec3(3,81,4));
        try {
            victim.hurtServer(h.getLevel(),ShotDamage.PLAYER.source(h.getLevel(),GrenadeProjectile.IMPACT,p),g.damage);
            near(h,1000-victim.getHealth(),ArmorMath.afterArmor(g.damage,20,0,0),"Direct inherited bullet uses full ordinary armor");
            victim.invulnerableTime=0;victim.setHealth(1000);
            victim.hurtServer(h.getLevel(),ShotDamage.PLAYER.source(h.getLevel(),GrenadeProjectile.BLAST,p),g.damage);
            near(h,1000-victim.getHealth(),ArmorMath.afterArmor(g.damage,10,0,0),"Explosion uses half ordinary armor");
        } finally {victim.discard();p.discard();}h.succeed();
    }
    private static void crafting(GameTestHelper h,HandGrenade g) {
        boolean frag=g==HandGrenade.FRAGGRENADE;var iron=frag?TGContent.MATERIALS.get("ingotsteel").toStack():new ItemStack(Items.IRON_INGOT);
        var grid=new ArrayList<ItemStack>(Collections.nCopies(9,ItemStack.EMPTY));grid.set(1,iron.copy());grid.set(2,new ItemStack(frag?Items.FLINT_AND_STEEL:Items.TNT));
        grid.set(4,new ItemStack(frag?Items.TNT:Items.OAK_PLANKS));grid.set(5,iron.copy());grid.set(frag?3:6,iron.copy());if(frag)grid.set(7,iron.copy());
        var input=CraftingInput.of(3,3,grid);var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value();
        var result=recipe.assemble(input);h.assertTrue(result.is(TGContent.GRENADES.get(g.id()).get()),"Native recipe yields working grenade");h.assertValueEqual(result.getCount(),16,"Original batch output");
        h.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),"Source consumes flint-and-steel, no invented remainder");
        if(!frag)for(var plank:List.of(Items.BIRCH_PLANKS,Items.SPRUCE_PLANKS,Items.JUNGLE_PLANKS,Items.CRIMSON_PLANKS,Items.BAMBOO_PLANKS)){grid.set(4,new ItemStack(plank));h.assertTrue(recipe.matches(CraftingInput.of(3,3,grid),h.getLevel()),"Common plank tag accepted");}
        grid.set(4,new ItemStack(Items.DIRT));h.assertTrue(!recipe.matches(CraftingInput.of(3,3,grid),h.getLevel()),"Wrong middle ingredient rejected");h.succeed();
    }
    private static void cancelUse(GameTestHelper h) {
        var player=player(h,HandGrenade.FRAGGRENADE,1);var stack=player.getMainHandItem();var item=(GrenadeItem)stack.getItem();
        Consumer<EntityJoinLevelEvent> cancel=e->{if(e.getEntity() instanceof GrenadeProjectile p && p.getOwner()==player)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(cancel);
        try {item.use(h.getLevel(),player,InteractionHand.MAIN_HAND);player.releaseUsingItem();h.assertValueEqual(stack.getCount(),1,"Cancelled spawn never consumes last grenade");}
        finally {NeoForge.EVENT_BUS.unregister(cancel);}
        Consumer<LivingEntityUseItemEvent.Start> start=e->{if(e.getEntity()==player)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(start);
        try {item.use(h.getLevel(),player,InteractionHand.MAIN_HAND);h.assertTrue(!player.isUsingItem(),"Cancelled use never starts");}
        finally {NeoForge.EVENT_BUS.unregister(start);}
        Consumer<LivingEntityUseItemEvent.Stop> stop=e->{if(e.getEntity()==player)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(stop);
        try {item.use(h.getLevel(),player,InteractionHand.MAIN_HAND);player.releaseUsingItem();h.assertValueEqual(stack.getCount(),1,"Cancelled stop does not throw");}
        finally {NeoForge.EVENT_BUS.unregister(stop);}
        item.use(h.getLevel(),player,InteractionHand.MAIN_HAND);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));player.releaseUsingItem();
        h.assertValueEqual(stack.getCount(),1,"Replacing active item prevents stale throw");player.discard();h.succeed();
    }
    private static void cancelImpact(GameTestHelper h) {
        for(String mode:List.of("impact","damage","blast")) {
            var victim=target(h,new Vec3(6,80,3));var p=projectile(h,HandGrenade.FRAGGRENADE,new Vec3(4,81,3));p.setDeltaMovement(4,0,0);
            int[] blasts={0};Consumer<ProjectileImpactEvent> impact=e->{if(e.getProjectile()==p && mode.equals("impact"))e.setCanceled(true);};
            Consumer<LivingIncomingDamageEvent> damage=e->{if(e.getSource().getDirectEntity()==p && mode.equals("damage"))e.setCanceled(true);};
            Consumer<ExplosionEvent.Start> blast=e->{if(e.getExplosion().getDirectSourceEntity()==p){blasts[0]++;if(mode.equals("blast"))e.setCanceled(true);}};
            NeoForge.EVENT_BUS.addListener(impact);NeoForge.EVENT_BUS.addListener(damage);NeoForge.EVENT_BUS.addListener(blast);
            try {p.tick();h.assertValueEqual(blasts[0],mode.equals("blast")?1:0,"Blast only follows successful living hit");h.assertTrue(p.isRemoved()!=mode.equals("impact"),"Cancelled impact continues flight");if(!mode.equals("blast"))near(h,victim.getHealth(),1000,"Cancelled impact/damage shields victim");}
            finally {NeoForge.EVENT_BUS.unregister(impact);NeoForge.EVENT_BUS.unregister(damage);NeoForge.EVENT_BUS.unregister(blast);victim.discard();p.discard();}
        }h.succeed();
    }
    private static void detonation(GameTestHelper h) {
        var p=projectile(h,HandGrenade.FRAGGRENADE,new Vec3(4,80,4));var victim=target(h,new Vec3(5,79,4));var block=new BlockPos(4,78,4);h.setBlock(block,Blocks.TNT);
        Consumer<ExplosionEvent.Detonate> mutate=e->{if(e.getExplosion().getDirectSourceEntity()==p){e.getAffectedEntities().clear();e.getAffectedBlocks().add(h.absolutePos(block));}};NeoForge.EVENT_BUS.addListener(mutate);
        try {p.explode();near(h,victim.getHealth(),1000,"Detonate can remove victims");h.assertTrue(h.getBlockState(block).is(Blocks.TNT),"Event-added blocks cannot bypass KEEP or prime TNT");}
        finally {NeoForge.EVENT_BUS.unregister(mutate);victim.discard();p.discard();h.setBlock(block,Blocks.AIR);}h.succeed();
    }
    private static void water(GameTestHelper h) {
        var block=new BlockPos(4,80,4);h.setBlock(block,Blocks.WATER);var p=projectile(h,HandGrenade.STIELGRANATE,new Vec3(4.5,80.4,4.5));p.setDeltaMovement(.1,0,0);
        try {p.tick();near(h,p.getDeltaMovement().x,.1*(double).85f,"Inherited water drag");h.assertValueEqual(p.bounces(),3,"Water never consumes bounce");}
        finally {p.discard();h.setBlock(block,Blocks.STONE);}
        p=projectile(h,HandGrenade.STIELGRANATE,new Vec3(4.5,81.02,4.5));p.setDeltaMovement(0,-.03,0);
        try {p.tick();h.assertTrue(p.getY()>h.absolutePos(block).getY()+1,"Source bounce offset cannot embed slow grenade in floor");}
        finally {p.discard();h.setBlock(block,Blocks.AIR);}h.succeed();
    }
    private static void invalidAndSync(GameTestHelper h) {
        for(String invalid:List.of("id","gravity_nan","gravity_zero","age","bounces")) {
            var p=projectile(h,HandGrenade.FRAGGRENADE,new Vec3(4,80,4));CompoundTag tag=NetherGameTests.save(h,p);
            switch(invalid){case "id"->tag.putString("grenade","missing");case "gravity_nan"->tag.putFloat("gravity",Float.NaN);case "gravity_zero"->tag.putFloat("gravity",0);case "age"->tag.putInt("age",200);case "bounces"->tag.putInt("bounces",3);}
            NetherGameTests.load(h,p,tag);h.assertTrue(p.isRemoved(),"Invalid save rejected: "+invalid);
        }
        var p=projectile(h,HandGrenade.FRAGGRENADE,new Vec3(4,80,4));p.configure(HandGrenade.FRAGGRENADE,1);
        var copy=projectile(h,HandGrenade.STIELGRANATE,Vec3.ZERO);var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer,new ClientboundSetEntityDataPacket(p.getId(),p.getEntityData().getNonDefaultValues()));var packet=ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer);copy.getEntityData().assignValues(packet.packedItems());h.assertValueEqual(copy.grenade(),p.grenade(),"Variant travels over actual entity-data packet");near(h,copy.gravity(),p.gravity(),"Charge gravity syncs to flight renderer");}
        finally {buffer.release();p.discard();copy.discard();}h.succeed();
    }
    private static void friendly(GameTestHelper h) {
        var owner=player(h,HandGrenade.STIELGRANATE,1);var teammate=player(h,HandGrenade.STIELGRANATE,1);
        var scoreboard=h.getLevel().getScoreboard();var team=scoreboard.addPlayerTeam("grenade_"+owner.getId());team.setAllowFriendlyFire(false);
        scoreboard.addPlayerToTeam(owner.getScoreboardName(),team);scoreboard.addPlayerToTeam(teammate.getScoreboardName(),team);
        var p=projectile(h,HandGrenade.STIELGRANATE,new Vec3(4,80,4));p.setOwner(owner);
        teammate.setPos(p.position());float health=teammate.getHealth();
        try {h.assertTrue(!owner.canHarmPlayer(teammate),"Fixture has real friendly-fire restriction");boolean accepted=RocketDamage.hurt(h.getLevel(),owner,ShotDamage.PLAYER.source(h.getLevel(),GrenadeProjectile.BLAST,p),teammate,10,1);h.assertTrue(!accepted,"Blast honors team protection");near(h,teammate.getHealth(),health,"No friendly damage");}
        finally {scoreboard.removePlayerTeam(team);p.discard();owner.discard();teammate.discard();}h.succeed();
    }
    private static void serverFlight(GameTestHelper h) {
        var p=projectile(h,HandGrenade.FRAGGRENADE,new Vec3(4,80,4));p.setDeltaMovement(.2,0,0);var start=p.position();h.getLevel().addFreshEntity(p);
        h.runAfterDelay(4,()->{try {h.assertTrue(p.age()>=3 && p.getX()>start.x && p.getY()<start.y,"Real server ticks advance gravity and movement");}finally{p.discard();}h.succeed();});
    }
}
