package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.npc.*;
import techguns.modern.network.*;

final class FlamethrowerGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for (String mode : List.of("empty", "partial", "creative", "removed", "swap", "full_inventory")) r.register("flame_reload_"+mode, () -> h -> reload(h,mode));
        for (boolean empty : List.of(false,true)) r.register("flame_craft_"+(empty?"empty":"full"), () -> h -> recipe(h,empty));
        r.register("flame_100_shots_cooldown_and_creative", () -> FlamethrowerGameTests::fire);
        r.register("flame_cancelled_spawn_and_permission_snapshot", () -> FlamethrowerGameTests::spawnPolicy);
        for (boolean off : List.of(false,true)) r.register("flame_muzzle_"+(off?"offhand":"mainhand"), () -> h -> muzzle(h,off));
        r.register("flame_start_continuous_pause_and_no_idle_sound", () -> FlamethrowerGameTests::sounds);
        r.register("flame_flight_chunk_save_gravity_and_lifetime", () -> FlamethrowerGameTests::flight);
        r.register("flame_invalid_saved_state", () -> FlamethrowerGameTests::invalid);
        for (String mode : List.of("plain", "armor", "witch", "resistance", "immune", "npc")) r.register("flame_damage_"+mode, () -> h -> damage(h,mode));
        r.register("flame_typed_player_armor", () -> FlamethrowerGameTests::playerArmor);
        r.register("flame_falloff_and_repeated_hits", () -> FlamethrowerGameTests::falloff);
        for (String mode : List.of("impact", "damage", "fire_only")) r.register("flame_protection_"+mode, () -> h -> protection(h,mode));
        for (var face : Direction.values()) r.register("flame_block_face_"+face.getName(), () -> h -> face(h,face));
        r.register("flame_safe_failed_roll_and_occupied_block", () -> FlamethrowerGameTests::fireRules);
        r.register("flame_real_block_impact_and_cancellation", () -> FlamethrowerGameTests::blockImpact);
        r.register("flame_water_removal_after_collision", () -> FlamethrowerGameTests::water);
        r.register("flame_rain_and_roof", () -> FlamethrowerGameTests::rain);
        r.register("flame_npc_equipment_damage_and_no_block_fire", () -> FlamethrowerGameTests::npc);
        r.register("flame_zero_npc_damage_no_ignition", () -> FlamethrowerGameTests::zero);
        r.register("flame_owner_excluded_after_leaving_muzzle", () -> FlamethrowerGameTests::owner);
        r.register("flame_wall_precedes_entity_hit", () -> FlamethrowerGameTests::wall);
        r.register("flame_native_server_ticks", () -> FlamethrowerGameTests::nativeFlight);
    }
    private static void near(GameTestHelper h,double a,double b,String text) { h.assertTrue(Math.abs(a-b)<.001,text+": "+a+" vs "+b); }
    private static Player player(GameTestHelper h,int rounds) {
        var p=WeaponGameTests.player(h); p.setPos(h.absoluteVec(new Vec3(2,70,2))); p.getInventory().clearContent();
        var stack=TGContent.GUNS.get("flamethrower").toStack(); stack.set(TGContent.ROUNDS.get(),rounds); p.setItemInHand(InteractionHand.MAIN_HAND,stack); p.setData(SafeMode.SAFE,true); return p;
    }
    private static int count(Player p,String id) { int n=0;for(var s:p.getInventory())if(s.is(TGContent.AMMO.get(id).get()))n+=s.getCount();return n; }
    private static FlameProjectile shot(GameTestHelper h,Vec3 absolute,boolean unsafe) {
        var s=new FlameProjectile(TGContent.FLAME.get(),h.getLevel()); s.configure(Weapons.definition("flamethrower"),unsafe); s.setPos(absolute); s.setOwner(WeaponGameTests.player(h)); return s;
    }
    private static LivingEntity target(GameTestHelper h,EntityType<? extends Mob> type) {
        var v=h.spawnWithNoFreeWill(type,new Vec3(6,70,4)); v.setNoGravity(true); v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);v.setHealth(1000);return v;
    }
    private static FlameProjectile aimed(GameTestHelper h,LivingEntity v) { var s=shot(h,v.position().add(-1,.5,0),false);s.setDeltaMovement(2,0,0);return s; }
    private static void ready(Player p) { p.getCooldowns().tick();p.getCooldowns().tick(); }
    private static void reload(GameTestHelper h,String mode) {
        var p=player(h,mode.equals("empty")?0:63);var s=p.getMainHandItem();int old=GunItem.rounds(s);boolean creative=mode.equals("creative");p.getAbilities().instabuild=creative;
        if(mode.equals("full_inventory"))for(int i=1;i<p.getInventory().getContainerSize();i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        p.getInventory().setItem(1,creative?ItemStack.EMPTY:TGContent.AMMO.get("fueltank").toStack(2));
        var drops=new ArrayList<ItemEntity>();Consumer<EntityJoinLevelEvent> collect=e->{if(e.getEntity() instanceof ItemEntity item && item.getItem().is(TGContent.AMMO.get("fueltankempty").get()) && item.distanceTo(p)<3)drops.add(item);};NeoForge.EVENT_BUS.addListener(collect);
        try {
            h.assertTrue(GunNetwork.handle(p,new GunActionPayload(true)),"R starts actual reload");
            for(int i=0;i<44;i++)p.tick();h.assertValueEqual(GunItem.rounds(s),old,"Tank is not loaded early");h.assertValueEqual(count(p,"fueltank"),creative?0:2,"No early consumption");
            h.assertTrue(!GunItem.fire(h.getLevel(),p,s),"Reload blocks shooting");
            if(mode.equals("removed"))p.getInventory().setItem(1,ItemStack.EMPTY);
            if(mode.equals("swap"))p.setItemInHand(InteractionHand.MAIN_HAND,s.copy());p.tick();
            boolean cancelled=mode.equals("removed")||mode.equals("swap");
            h.assertValueEqual(GunItem.rounds(s),cancelled?old:100,"Atomic tank replacement");
            h.assertValueEqual(count(p,"fueltank"),mode.equals("removed")||creative?0:cancelled?2:1,"One full tank, no partial refund");
            h.assertValueEqual(count(p,"fueltankempty")+drops.stream().mapToInt(e->e.getItem().getCount()).sum(),creative||cancelled?0:1,"One empty tank, dropped if inventory full");
            h.assertTrue(!ReloadSessions.active(p),"Session ends");
        } finally {NeoForge.EVENT_BUS.unregister(collect);ReloadSessions.cancel(p);drops.forEach(Entity::discard);p.discard();}h.succeed();
    }
    private static void recipe(GameTestHelper h,boolean empty) {
        var input=CraftingInput.of(3,2,List.of(TGContent.MATERIALS.get("pumpmechanism").toStack(),TGContent.MATERIALS.get("ironreceiver").toStack(),TGContent.MATERIALS.get("plasticstock").toStack(),new ItemStack(Items.FLINT_AND_STEEL),TGContent.AMMO.get(empty?"fueltankempty":"fueltank").toStack(),ItemStack.EMPTY));
        var recipe=h.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,TGContent.id("flamethrower"+(empty?"_alt":"")))).orElseThrow().value();
        h.assertTrue(recipe instanceof ShapedRecipe,"Source shaped recipe");var shaped=(ShapedRecipe)recipe;h.assertTrue(shaped.matches(input,h.getLevel()),"Native ingredients match");var result=shaped.assemble(input);
        h.assertTrue(result.is(TGContent.GUNS.get("flamethrower").get()),"Real usable gun");h.assertValueEqual(GunItem.rounds(result),empty?0:100,"Source damage metadata determines initial fuel");
        result.set(TGContent.FLAME_RECOIL_TIME.get(),123L);var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);var saved=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,result).getOrThrow()).getOrThrow();
        h.assertValueEqual(GunItem.rounds(saved),GunItem.rounds(result),"Fuel persists");h.assertTrue(!saved.has(TGContent.FLAME_RECOIL_TIME.get()),"Recoil never persists");
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{ItemStack.STREAM_CODEC.encode(buf,result);h.assertTrue(ItemStack.matches(result,ItemStack.STREAM_CODEC.decode(buf)),"Fuel and recoil synchronize");}finally{buf.release();}h.succeed();
    }
    private static void fire(GameTestHelper h) {
        var p=player(h,100);var s=p.getMainHandItem();var shots=new ArrayList<FlameProjectile>();Consumer<EntityJoinLevelEvent> capture=e->{if(e.getEntity() instanceof FlameProjectile f && f.getOwner()==p)shots.add(f);};NeoForge.EVENT_BUS.addListener(capture);
        try {
            for(int i=0;i<100;i++) {h.assertTrue(GunNetwork.handle(p,new GunActionPayload(false)),"Each fueled request accepted");h.assertTrue(!GunItem.fire(h.getLevel(),p,s),"Same tick spam rejected");p.getCooldowns().tick();h.assertTrue(!GunItem.fire(h.getLevel(),p,s),"One tick still cooling");p.getCooldowns().tick();}
            h.assertValueEqual(shots.size(),100,"Exactly 100 flame entities");h.assertValueEqual(GunItem.rounds(s),0,"Exactly one charge per shot");h.assertTrue(!GunItem.fire(h.getLevel(),p,s),"Empty tank cannot shoot");
            s.set(TGContent.ROUNDS.get(),1);p.getAbilities().instabuild=true;h.assertTrue(GunItem.fire(h.getLevel(),p,s),"Creative shot accepted");h.assertValueEqual(GunItem.rounds(s),0,"GenericGun consumes loaded fuel in Creative");
        } finally {NeoForge.EVENT_BUS.unregister(capture);shots.forEach(Entity::discard);p.discard();}h.succeed();
    }
    private static void spawnPolicy(GameTestHelper h) {
        var p=player(h,2);var stack=p.getMainHandItem();Consumer<EntityJoinLevelEvent> cancel=e->{if(e.getEntity() instanceof FlameProjectile f && f.getOwner()==p)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(cancel);
        try {h.assertTrue(!GunItem.fire(h.getLevel(),p,stack),"Spawn veto rejects shot");h.assertValueEqual(GunItem.rounds(stack),2,"No lost fuel");h.assertTrue(!stack.has(TGContent.FLAME_RECOIL_TIME.get())&&!p.getCooldowns().isOnCooldown(stack),"No recoil or cooldown for rejected shot");}finally{NeoForge.EVENT_BUS.unregister(cancel);}
        var shots=new ArrayList<FlameProjectile>();Consumer<EntityJoinLevelEvent> capture=e->{if(e.getEntity() instanceof FlameProjectile f && f.getOwner()==p)shots.add(f);};NeoForge.EVENT_BUS.addListener(capture);boolean old=SafeMode.OP_ONLY.get();
        try {p.setData(SafeMode.SAFE,false);SafeMode.OP_ONLY.set(true);h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"Permission blocks terrain, not weapon");h.assertTrue(!shots.getLast().damagesBlocks(),"OP-only forces safe shot");ready(p);SafeMode.OP_ONLY.set(false);h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"Unsafe shot accepted");p.setData(SafeMode.SAFE,true);h.assertTrue(shots.getLast().damagesBlocks(),"Policy captured at launch");}
        finally{SafeMode.OP_ONLY.set(old);NeoForge.EVENT_BUS.unregister(capture);shots.forEach(Entity::discard);p.discard();}h.succeed();
    }
    private static void muzzle(GameTestHelper h,boolean off) {
        var p=player(h,3);var stack=p.getMainHandItem();p.setYRot(0);p.setYHeadRot(0);p.setXRot(0);if(off){p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,stack);}
        var shots=new ArrayList<FlameProjectile>();Consumer<EntityJoinLevelEvent> capture=e->{if(e.getEntity() instanceof FlameProjectile f&&f.getOwner()==p)shots.add(f);};NeoForge.EVENT_BUS.addListener(capture);
        try{h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"Hand fires");var s=shots.getFirst();Vec3 muzzle=s.position().subtract(s.getDeltaMovement().scale(.35/.5));double side=off?.16:-.16;
            near(h,muzzle.x,p.getEyePosition().x+Math.cos(Math.toRadians(s.getYRot()))*side,"Inherited side offset before forward shift");near(h,muzzle.y,p.getEyePosition().y-.1,"Inherited height");near(h,muzzle.z,p.getEyePosition().z+Math.sin(Math.toRadians(s.getYRot()))*side,"Inherited yaw offset");h.assertTrue(s.getDeltaMovement().length()>.7&&s.getDeltaMovement().length()<.8,"Original 1.5 speed factor");}
        finally{NeoForge.EVENT_BUS.unregister(capture);shots.forEach(Entity::discard);p.discard();}h.succeed();
    }
    private static void sounds(GameTestHelper h) {
        var p=player(h,8);var stack=p.getMainHandItem();var sounds=new ArrayList<String>();var shots=new ArrayList<FlameProjectile>();
        Consumer<PlayLevelSoundEvent.AtPosition> listen=e->{if(e.getPosition().distanceTo(FlameFiring.soundPosition(p))<.01&&e.getSound()!=null)sounds.add(e.getSound().value().location().getPath());};
        Consumer<EntityJoinLevelEvent> capture=e->{if(e.getEntity() instanceof FlameProjectile f&&f.getOwner()==p)shots.add(f);};NeoForge.EVENT_BUS.addListener(listen);NeoForge.EVENT_BUS.addListener(capture);
        h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"First sound");long first=stack.get(TGContent.FLAME_RECOIL_TIME.get());
        h.runAfterDelay(2,()->{ready(p);h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"Continuous sample");h.assertValueEqual(stack.get(TGContent.FLAME_RECOIL_TIME.get()),first,"Ten tick sway is not reset on each shot");});
        h.runAfterDelay(12,()->{try{h.assertValueEqual(sounds,List.of("guns.flamethrowerstart","guns.flamethrowerfire"),"No independent idle loop");ready(p);h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"Restart at ten silent ticks");h.assertValueEqual(sounds.getLast(),"guns.flamethrowerstart","Original restart threshold");ready(p);p.getInventory().setItem(1,TGContent.AMMO.get("fueltank").toStack());h.assertTrue(ReloadSessions.begin(p),"Reload starts");h.assertValueEqual(sounds.getLast(),"guns.flamethrowerreload","Original reload sound");h.assertTrue(!GunItem.fire(h.getLevel(),p,stack),"Reload emits no shot");}
            finally{NeoForge.EVENT_BUS.unregister(listen);NeoForge.EVENT_BUS.unregister(capture);ReloadSessions.cancel(p);shots.forEach(Entity::discard);p.discard();}h.succeed();});
    }
    private static void flight(GameTestHelper h) {
        var start=h.absoluteVec(new Vec3(4,80,4));start=new Vec3(Math.floor(start.x/16)*16+15.8,start.y,start.z);h.getLevel().getChunkAt(BlockPos.containing(start.add(2,0,0)));
        var owner=target(h,EntityTypes.PIG);var s=shot(h,start,true);s.setOwner(owner);s.npcDamage(.5f);s.setDeltaMovement(.5,.2,.1);s.tick();near(h,s.getX(),start.x+.5,"Cross chunk movement");near(h,s.getDeltaMovement().y,.2*(double).99f-.01,"Drag then gravity");
        var copy=shot(h,start,false);NetherGameTests.load(h,copy,NetherGameTests.save(h,s));h.assertTrue(copy.damagesBlocks(),"Unsafe launch survives save");h.assertValueEqual(copy.shotDamage(),s.shotDamage(),"NPC scale persists");h.assertValueEqual(copy.age(),1,"Age persists");
        h.assertValueEqual(copy.getOwner(),owner,"Owner UUID resolves on load");
        try{for(int i=1;i<16;i++){h.assertTrue(!s.isRemoved()&&!copy.isRemoved(),"Alive before final movement");s.tick();copy.tick();near(h,s.position().distanceTo(copy.position()),0,"Saved flight resumes exactly");}h.assertTrue(s.isRemoved()&&copy.isRemoved(),"Expires after 16 movements");}finally{s.discard();copy.discard();owner.discard();}h.succeed();
    }
    private static void invalid(GameTestHelper h) {
        var s=shot(h,h.absoluteVec(new Vec3(4,80,4)),false);var tag=NetherGameTests.save(h,s);
        for(String key:List.of("weapon","age","expired","origin","nan","scale")){var bad=tag.copy();switch(key){case "weapon"->bad.putString("weapon","revolver");case "age"->bad.putInt("age",-1);case "expired"->bad.putInt("age",16);case "origin"->bad.putDouble("origin_x",2);case "nan"->{bad.putDouble("origin_x",Double.NaN);bad.putDouble("origin_y",0);bad.putDouble("origin_z",0);}case "scale"->bad.putFloat("damage_scale",2);}
            var restored=shot(h,s.position(),false);NetherGameTests.load(h,restored,bad);h.assertTrue(restored.isRemoved(),"Malformed saved "+key+" rejected");restored.discard();}s.discard();h.succeed();
    }
    private static void damage(GameTestHelper h,String mode) {
        var v=target(h,mode.equals("witch")?EntityTypes.WITCH:mode.equals("immune")?EntityTypes.BLAZE:mode.equals("npc")?NpcContent.SUPER_MUTANT.get():EntityTypes.PIG);
        if(mode.equals("armor")||mode.equals("immune"))v.getAttribute(Attributes.ARMOR).setBaseValue(10);if(mode.equals("resistance"))v.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,200));
        float ordinary=(float)v.getAttributeValue(Attributes.ARMOR),physical=ordinary,fire=ArmorMath.defaultArmor(DamageKind.FIRE,ordinary,v.fireImmune()),toughness=(float)v.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if(v instanceof NpcTypedArmor typed){physical=typed.armorAgainst(DamageKind.PHYSICAL);fire=typed.armorAgainst(DamageKind.FIRE);}
        var s=aimed(h,v);try{s.tick();near(h,1000-v.getHealth(),ArmorMath.afterArmor(5,fire,toughness,0)*(mode.equals("witch")?.15f:1)+ArmorMath.afterArmor(.01f,physical,toughness,0),"FIRE and preliminary PHYSICAL are distinct");h.assertValueEqual(v.isOnFire(),!v.fireImmune(),"Only successful native ignition, no fire resistance immunity to main hit");if(!v.fireImmune())h.assertTrue(v.getRemainingFireTicks()>=59&&v.getRemainingFireTicks()<=60,"Three seconds ignition");h.assertTrue(s.isRemoved(),"No piercing");}finally{s.discard();v.discard();}h.succeed();
    }
    private static void playerArmor(GameTestHelper h) {var p=ArmorGameTests.player(h);ArmorGameTests.equipAll(p);var s=shot(h,p.position().add(2,1,0),false);float before=p.getHealth();try{p.hurtServer(h.getLevel(),s.shotDamage().source(h.getLevel(),FlameProjectile.DAMAGE_TYPE,s),10);near(h,before-p.getHealth(),4.6,"T2 FIRE absorption");}finally{s.discard();}h.succeed();}
    private static void falloff(GameTestHelper h) {
        for(double distance:new double[]{0,4,10,16,24}){var v=target(h,EntityTypes.PIG);var s=aimed(h,v);var tag=NetherGameTests.save(h,s);tag.putDouble("origin_x",s.getX()-distance);tag.putDouble("origin_y",s.getY());tag.putDouble("origin_z",s.getZ());NetherGameTests.load(h,s,tag);try{s.tick();near(h,1000-v.getHealth(),Weapons.definition("flamethrower").stats().damageAt(distance)+.01,"Displacement at start of impact tick survives save");}finally{s.discard();v.discard();}}
        var v=target(h,EntityTypes.PIG);try{for(int i=0;i<5;i++)aimed(h,v).tick();near(h,1000-v.getHealth(),25.01,"Five main hits bypass cooldown; one preliminary hit");}finally{v.discard();}h.succeed();
    }
    private static void protection(GameTestHelper h,String mode) {
        var v=target(h,EntityTypes.PIG);var s=aimed(h,v);Consumer<ProjectileImpactEvent> impact=e->{if(e.getProjectile()==s)e.setCanceled(true);};Consumer<LivingIncomingDamageEvent> damage=e->{if(e.getSource().getDirectEntity()==s&&(!mode.equals("fire_only")||e.getSource().is(FlameProjectile.DAMAGE_TYPE)))e.setCanceled(true);};if(mode.equals("impact"))NeoForge.EVENT_BUS.addListener(impact);else NeoForge.EVENT_BUS.addListener(damage);
        try{s.tick();near(h,1000-v.getHealth(),mode.equals("fire_only")?.01:0,"Protection event respected");h.assertTrue(!v.isOnFire(),"Cancelled main damage never ignites");h.assertValueEqual(s.isRemoved(),!mode.equals("impact"),"Impact veto continues flight");}finally{NeoForge.EVENT_BUS.unregister(mode.equals("impact")?impact:damage);s.discard();v.discard();}h.succeed();
    }
    private static void seed(FlameProjectile s,boolean fire) {for(long n=0;n<100000;n++)if(FlameRules.ignites(RandomSource.create(n).nextDouble())==fire){s.getRandom().setSeed(n);return;}throw new IllegalStateException("No deterministic seed");}
    private static void face(GameTestHelper h,Direction face) {
        var p=h.absolutePos(new BlockPos(4,4,4));var dest=p.relative(face);h.getLevel().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(dest.below(),Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(dest,Blocks.AIR.defaultBlockState());var s=shot(h,Vec3.atCenterOf(p).add(2,2,2),true);seed(s,true);
        try{h.assertTrue(s.igniteBlock(new BlockHitResult(Vec3.atCenterOf(p),face,p,false)),"Air on hit face ignites");h.assertTrue(h.getLevel().getBlockState(dest).is(Blocks.FIRE),"Correct cell receives fire");}finally{s.discard();h.getLevel().setBlockAndUpdate(dest,Blocks.AIR.defaultBlockState());}h.succeed();
    }
    private static void fireRules(GameTestHelper h) {
        var p=h.absolutePos(new BlockPos(4,3,4));h.getLevel().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());var hit=new BlockHitResult(Vec3.atCenterOf(p),Direction.UP,p,false);var s=shot(h,Vec3.atCenterOf(p).add(2,2,2),false);
        try{seed(s,true);h.assertTrue(!s.igniteBlock(hit),"Safe policy forbids fire");s.configure(s.weapon(),true);seed(s,false);h.assertTrue(!s.igniteBlock(hit),"Failed 50 percent roll");h.getLevel().setBlockAndUpdate(p.above(),Blocks.TORCH.defaultBlockState());seed(s,true);h.assertTrue(!s.igniteBlock(hit),"Replaceable non-air survives");h.assertTrue(h.getLevel().getBlockState(p.above()).is(Blocks.TORCH),"Block remains intact");}finally{s.discard();}h.succeed();
    }
    private static void blockImpact(GameTestHelper h) {
        var wall=h.absolutePos(new BlockPos(5,3,4));var dest=wall.west();h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(dest.below(),Blocks.STONE.defaultBlockState());
        for(boolean cancel:List.of(true,false)){h.getLevel().setBlockAndUpdate(dest,Blocks.AIR.defaultBlockState());var s=shot(h,Vec3.atCenterOf(dest),true);s.setDeltaMovement(1,0,0);seed(s,true);Consumer<ProjectileImpactEvent> listener=e->{if(e.getProjectile()==s&&cancel)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(listener);
            try{s.tick();h.assertValueEqual(h.getLevel().getBlockState(dest).is(Blocks.FIRE),!cancel,"Actual block clip and veto control fire");h.assertValueEqual(s.isRemoved(),!cancel,"Single impact lifetime");}finally{NeoForge.EVENT_BUS.unregister(listener);s.discard();}}
        h.getLevel().setBlockAndUpdate(dest,Blocks.AIR.defaultBlockState());h.succeed();
    }
    private static void water(GameTestHelper h) {
        h.setBlock(4,2,4,Blocks.WATER);var s=shot(h,h.absoluteVec(new Vec3(4.5,2.2,4.5)),false);s.setDeltaMovement(.1,0,0);s.tick();h.assertTrue(s.isInWater()&&s.isRemoved(),"Water extinguishes flame");near(h,s.getDeltaMovement().x,.1*(double).85f,"Water drag still applied");near(h,s.getDeltaMovement().y,-.01,"Gravity still applied after water removal");
        var v=target(h,EntityTypes.PIG);var hit=aimed(h,v);var water=hit.blockPosition();h.getLevel().setBlockAndUpdate(water,Blocks.WATER.defaultBlockState());try{hit.tick();h.assertTrue(hit.isInWater(),"Hit starts wet");near(h,1000-v.getHealth(),5.01,"Collision happens before wet removal");}finally{h.getLevel().setBlockAndUpdate(water,Blocks.AIR.defaultBlockState());hit.discard();v.discard();}h.succeed();
    }
    private static void rain(GameTestHelper h) {
        var level=h.getLevel();float rain=level.getRainLevel(1);var pos=h.absolutePos(new BlockPos(4,80,4));var roof=pos.above(3);var old=level.getBlockState(roof);
        try{level.setRainLevel(1);h.assertTrue(level.isRainingAt(pos),"Open-sky rain fixture");var wet=shot(h,Vec3.atCenterOf(pos),false);wet.setDeltaMovement(.1,0,0);wet.tick();h.assertTrue(wet.isRemoved(),"Rain extinguishes flame");level.setBlockAndUpdate(roof,Blocks.STONE.defaultBlockState());h.assertTrue(!level.isRainingAt(pos),"Roof excludes rain");var dry=shot(h,Vec3.atCenterOf(pos),false);dry.tick();h.assertTrue(!dry.isRemoved(),"Covered projectile remains alive");dry.discard();}finally{level.setRainLevel(rain);level.setBlockAndUpdate(roof,old);}h.succeed();
    }
    private static void npc(GameTestHelper h) {
        var mob=h.spawnWithNoFreeWill(NpcContent.BANDIT.get(),new Vec3(3,70,4));mob.setNoGravity(true);var v=target(h,EntityTypes.PIG);var stack=TGContent.GUNS.get("flamethrower").toStack();mob.setItemSlot(EquipmentSlot.MAINHAND,stack);var shots=new ArrayList<FlameProjectile>();var sounds=new ArrayList<String>();
        Consumer<EntityJoinLevelEvent> capture=e->{if(e.getEntity() instanceof FlameProjectile f&&f.getOwner()==mob)shots.add(f);};Consumer<PlayLevelSoundEvent.AtPosition> listen=e->{if(e.getPosition().distanceTo(FlameFiring.soundPosition(mob))<.01&&e.getSound()!=null)sounds.add(e.getSound().value().location().getPath());};NeoForge.EVENT_BUS.addListener(capture);NeoForge.EVENT_BUS.addListener(listen);
        try{h.assertTrue(NpcCombat.fire(mob,v),"Equipped NPC shoots without player fuel");h.assertValueEqual(shots.size(),1,"Actual flame family");h.assertValueEqual(GunItem.rounds(stack),0,"NPC does not consume player magazine");var s=shots.getFirst();h.assertTrue(s.shotDamage().npc()&&!s.damagesBlocks(),"NPC scaling and safe terrain policy");h.assertValueEqual(sounds,List.of("guns.flamethrowerfire"),"Source NPC uses fire sample even for first shot");
            Vec3 muzzle=s.position().subtract(s.getDeltaMovement().scale(.35/.5));near(h,muzzle.y,mob.getEyePosition().y-.1,"Forward shift applied exactly once");s.setPos(v.position().add(-1,.5,0));s.setDeltaMovement(2,0,0);float amount=s.shotDamage().againstEntity(5);s.tick();near(h,1000-v.getHealth(),amount+(amount>0?.01:0),"One NPC difficulty factor");}
        finally{NeoForge.EVENT_BUS.unregister(capture);NeoForge.EVENT_BUS.unregister(listen);shots.forEach(Entity::discard);mob.discard();v.discard();}h.succeed();
    }
    private static void zero(GameTestHelper h) {var v=target(h,EntityTypes.PIG);var s=aimed(h,v);s.npcDamage(0);try{s.tick();near(h,v.getHealth(),1000,"Zero scale suppresses both hits");h.assertTrue(!v.isOnFire(),"No burn without accepted damage");}finally{s.discard();v.discard();}h.succeed();}
    private static void owner(GameTestHelper h) {
        var v=target(h,EntityTypes.PIG);var s=shot(h,v.position().add(-3,.5,0),false);s.setOwner(v);s.setDeltaMovement(-.1,0,0);s.tick();
        s.setPos(v.position().add(-1,.5,0));s.setDeltaMovement(2,0,0);
        try{s.tick();near(h,v.getHealth(),1000,"Source shooter stays excluded after leaving owner bounds");h.assertTrue(!v.isOnFire()&&!s.isRemoved(),"No owner collision or ignition");}finally{s.discard();v.discard();}h.succeed();
    }
    private static void wall(GameTestHelper h) {
        var v=target(h,EntityTypes.PIG);var block=BlockPos.containing(v.position().add(-1,.5,0));var old=h.getLevel().getBlockState(block);h.getLevel().setBlockAndUpdate(block,Blocks.STONE.defaultBlockState());
        var s=shot(h,v.position().add(-2,.5,0),false);s.setDeltaMovement(3,0,0);
        try{s.tick();near(h,v.getHealth(),1000,"Block clip prevents damage behind wall");h.assertTrue(s.isRemoved()&&!v.isOnFire(),"Wall consumes flame without target ignition");}finally{s.discard();v.discard();h.getLevel().setBlockAndUpdate(block,old);}h.succeed();
    }
    private static void nativeFlight(GameTestHelper h) {var s=shot(h,h.absoluteVec(new Vec3(4,80,4)),false);s.setDeltaMovement(.25,0,0);double x=s.getX();h.getLevel().addFreshEntity(s);h.runAfterDelay(4,()->{h.assertTrue(s.age()>=3&&s.getX()>x+.5,"Real world ticks move flame");h.assertTrue(s.getDeltaMovement().y<0,"Real gravity");});h.runAfterDelay(18,()->{h.assertTrue(s.isRemoved(),"Real TTL stops the stream");h.succeed();});}
}
