package techguns.modern.test;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.armor.*;
import techguns.modern.crafting.AmmoChangeRecipe;
import techguns.modern.npc.*;

final class IncendiaryGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for (var gun : Weapons.ALL) if (IncendiaryAmmo.supported(gun)) {
            for (var variant : BallisticVariant.values()) r.register("incendiary_switch_" + gun.id() + "_" + variant.id(), () -> h -> switching(h, gun, variant));
            r.register("incendiary_reload_and_fire_" + gun.id(), () -> h -> reloadAndFire(h, gun));
            r.register("incendiary_damage_" + gun.id(), () -> h -> damage(h, gun));
        }
        for (var family : IncendiaryAmmo.FAMILIES) if (!family.empty().isEmpty()) r.register("incendiary_magazine_craft_" + family.normal(), () -> h -> magazine(h, family));
        r.register("incendiary_lmg_packed_recipe", () -> IncendiaryGameTests::packedLmg);
        for (String mode : List.of("armor", "witch", "resistance", "immune", "npc")) r.register("incendiary_target_" + mode, () -> h -> armor(h, mode));
        r.register("incendiary_player_typed_fire_armor", () -> IncendiaryGameTests::playerArmor);
        r.register("incendiary_eight_pellets_and_one_impulse", () -> IncendiaryGameTests::pellets);
        for (String mode : List.of("impact", "damage", "fire_only")) r.register("incendiary_cancel_" + mode, () -> h -> cancel(h, mode));
        for (String mode : List.of("ammo_removed", "variant", "stack")) r.register("incendiary_reload_cancel_" + mode, () -> h -> reloadCancel(h, mode));
        r.register("incendiary_reload_full_inventory", () -> IncendiaryGameTests::fullInventory);
        r.register("incendiary_creative_reload", () -> IncendiaryGameTests::creative);
        r.register("incendiary_npc_equipped_variant", () -> IncendiaryGameTests::npc);
        r.register("incendiary_zero_npc_damage", () -> IncendiaryGameTests::zeroDamage);
        for (String id : List.of("revolver", "combatshotgun", "as50")) r.register("incendiary_flight_save_" + id, () -> h -> flight(h, id));
        r.register("incendiary_water_drag", () -> IncendiaryGameTests::water);
        r.register("incendiary_saved_falloff_before_impact", () -> IncendiaryGameTests::falloff);
        r.register("incendiary_expiry_and_invalid_saves", () -> IncendiaryGameTests::invalidSaves);
        r.register("incendiary_invalid_items_and_components", () -> IncendiaryGameTests::invalidItems);
        r.register("incendiary_gilding_preserves_variant", () -> IncendiaryGameTests::gilding);
        for (var direction : Direction.values()) r.register("incendiary_block_face_" + direction.getName(), () -> h -> fireFace(h, direction));
        r.register("incendiary_block_probability_safe_and_occupied", () -> IncendiaryGameTests::fireRules);
        r.register("incendiary_real_block_impact_and_cancel", () -> IncendiaryGameTests::blockImpact);
        r.register("incendiary_safe_permission_at_launch", () -> IncendiaryGameTests::permission);
    }
    private static void near(GameTestHelper h, double a, double b, String text) { h.assertTrue(Math.abs(a-b)<.001, text + ": " + a + " vs " + b); }
    private static ItemStack ammo(String id, int count) { return TGContent.AMMO.get(id).toStack(count); }
    private static ItemStack gun(String id, int rounds) {
        var stack = TGContent.GUNS.get(id).toStack(); stack.set(TGContent.ROUNDS.get(), rounds);
        stack.set(TGContent.BALLISTIC_VARIANT.get(), BallisticVariant.INCENDIARY); return stack;
    }
    private static Player player(GameTestHelper h, ItemStack stack) {
        var p = WeaponGameTests.player(h); p.getInventory().clearContent(); p.setItemInHand(InteractionHand.MAIN_HAND, stack); p.setData(SafeMode.SAFE, true); return p;
    }
    private static int count(Player p, String id) {
        if (id.isEmpty()) return 0;
        int total=0; for (var stack : p.getInventory()) if (stack.is(TGContent.AMMO.get(id).get())) total+=stack.getCount(); return total;
    }
    private static CraftingRecipe recipe(GameTestHelper h, CraftingInput input) {
        return h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow().value();
    }
    private static ItemStack craft(GameTestHelper h, List<ItemStack> ingredients) {
        return recipe(h, CraftingInput.of(3,3,ingredients)).assemble(CraftingInput.of(3,3,ingredients));
    }
    private static List<ItemStack> grid(ItemStack... items) {
        var grid = new ArrayList<ItemStack>(Collections.nCopies(9, ItemStack.EMPTY));
        for(int i=0;i<items.length;i++) grid.set(i,items[i]); return grid;
    }
    private static void switching(GameTestHelper h, WeaponDefinition gun, BallisticVariant variant) {
        var inputGun = gun(gun.id(), gun.stats().capacity()/2);
        inputGun.set(TGContent.BALLISTIC_VARIANT.get(), variant == BallisticVariant.DEFAULT ? BallisticVariant.INCENDIARY : BallisticVariant.DEFAULT);
        inputGun.set(DataComponents.CUSTOM_NAME, Component.literal("Old magazine"));
        var data = new CompoundTag(); data.putInt("supply",42); inputGun.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        inputGun.set(TGContent.AIMING.get(),true); inputGun.set(TGContent.RELOAD_TICKS.get(),12);
        var original = inputGun.copy(); var ammunition = ammo(IncendiaryAmmo.ammo(gun,variant).item(),1);
        var input = CraftingInput.of(2,1,List.of(inputGun,ammunition)); var recipe = recipe(h,input); var result = recipe.assemble(input);
        h.assertTrue(recipe instanceof AmmoChangeRecipe,"Original ammo-change serializer");
        h.assertValueEqual(BallisticAmmo.variant(result),variant,"Correct selected ammunition");
        h.assertValueEqual(GunItem.rounds(result),gun.ammo().individual()?1:gun.stats().capacity(),"One shell versus full bundle/magazine");
        h.assertValueEqual(result.get(DataComponents.CUSTOM_NAME),original.get(DataComponents.CUSTOM_NAME),"Name retained");
        h.assertValueEqual(result.get(DataComponents.CUSTOM_DATA),original.get(DataComponents.CUSTOM_DATA),"Custom data retained");
        h.assertTrue(!result.has(TGContent.AIMING.get()) && !result.has(TGContent.RELOAD_TICKS.get()),"Transient actions removed");
        h.assertTrue(ItemStack.matches(inputGun,original),"Preview leaves input unchanged");
        h.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),"Old rounds consumed without duplicated magazine");
        var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        h.assertTrue(ItemStack.matches(result,ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,result).getOrThrow()).getOrThrow()),"Persistent item state");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer,result); h.assertTrue(ItemStack.matches(result,ItemStack.STREAM_CODEC.decode(buffer)),"Item packet retains ammunition");
            AmmoChangeRecipe.STREAM_CODEC.encode(buffer,(AmmoChangeRecipe)recipe);
            h.assertTrue(ItemStack.matches(result,AmmoChangeRecipe.STREAM_CODEC.decode(buffer).assemble(input)),"Recipe packet retains behavior");
        } finally { buffer.release(); }
        h.succeed();
    }
    private static void reloadAndFire(GameTestHelper h, WeaponDefinition definition) {
        var stack=gun(definition.id(),definition.stats().capacity()-1); var p=player(h,stack); var selected=BallisticAmmo.ammo(stack);
        p.getInventory().setItem(1,ammo(definition.ammo().item(),3)); p.getInventory().setItem(2,ammo(selected.item(),2));
        h.assertTrue(ReloadSessions.begin(p),"R starts with selected ammo");
        for(int i=1;i<definition.stats().reloadTicks();i++) ReloadSessions.tick(p);
        h.assertValueEqual(count(p,selected.item()),2,"No ammo consumed before final tick");
        ReloadSessions.tick(p); h.assertValueEqual(GunItem.rounds(stack),definition.stats().capacity(),"Reload fills capacity");
        h.assertValueEqual(count(p,selected.item()),1,"Exactly one selected item consumed");
        h.assertValueEqual(count(p,definition.ammo().item()),3,"Normal ammunition untouched");
        if(selected.magazine()) {
            h.assertValueEqual(count(p,selected.emptyItem()),1,"Original empty magazine returned");
            h.assertValueEqual(count(p,selected.looseItem()),(definition.stats().capacity()-1)*selected.bundlesPerMagazine()/definition.stats().capacity(),"Only whole incendiary remainder bundles");
        }
        h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"Server fires the selected variant");
        h.assertValueEqual(GunItem.rounds(stack),definition.stats().capacity()-1,"One shot consumed including shotgun pellets");
        h.assertTrue(!GunItem.fire(h.getLevel(),p,stack),"Cooldown rejects repeated request");
        var shots=h.getLevel().getEntitiesOfClass(IncendiaryBullet.class,p.getBoundingBox().inflate(3),e->e.getOwner()==p);
        h.assertValueEqual(shots.size(),definition.projectileCount(),"Original pellet count");
        for(var shot:shots) {
            h.assertValueEqual(shot.weapon(),definition,"Source weapon propagated"); h.assertTrue(!shot.damagesBlocks(),"Safe B flag reaches every pellet");
            h.assertValueEqual(shot.fireTrail(),definition.ammo().individual(),"Only shotgun factory enables fire trail"); shot.discard();
        }
        h.assertTrue(h.getLevel().getEntitiesOfClass(Bullet.class,p.getBoundingBox().inflate(3),e->e.getOwner()==p).isEmpty(),"No extra ordinary projectile");
        h.succeed();
    }
    private static LivingEntity target(GameTestHelper h, EntityType<? extends Mob> type) {
        var t=h.spawnWithNoFreeWill(type,new Vec3(6,70,4)); t.setNoGravity(true);
        t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); t.setHealth(1000); return t;
    }
    private static IncendiaryBullet shot(GameTestHelper h, WeaponDefinition gun, Vec3 position, boolean unsafe) {
        var shot=new IncendiaryBullet(TGContent.INCENDIARY_BULLET.get(),h.getLevel()); shot.configure(gun,unsafe);
        shot.setPos(position); shot.setOwner(WeaponGameTests.player(h)); h.getLevel().addFreshEntity(shot); return shot;
    }
    private static IncendiaryBullet aimed(GameTestHelper h, WeaponDefinition gun, LivingEntity t) {
        var shot=shot(h,gun,t.position().add(-1,.5,0),false); shot.setDeltaMovement(2,0,0); return shot;
    }
    private static void damage(GameTestHelper h, WeaponDefinition gun) {
        var t=target(h,EntityTypes.PIG); var s=aimed(h,gun,t);
        try {
            s.tick(); near(h,1000-t.getHealth(),gun.stats().damage()*1.1f+.01f,"Modified base damage plus source physical impulse");
            h.assertTrue(t.getRemainingFireTicks()>=59 && t.getRemainingFireTicks()<=60,"Successful hit ignites for three seconds");
            h.assertTrue(s.isRemoved(),"Impact consumes projectile");
        } finally { s.discard(); t.discard(); }
        h.succeed();
    }
    private static void magazine(GameTestHelper h, IncendiaryAmmo.Family family) {
        var inputs=new ArrayList<ItemStack>(); inputs.add(ammo(family.empty(),1));
        for(int i=0;i<family.bundles();i++) inputs.add(ammo(family.loose(),1));
        var result=craft(h,grid(inputs.toArray(ItemStack[]::new)));
        h.assertTrue(result.is(TGContent.AMMO.get(family.item()).get()),"Source empty magazine and loose incendiary rounds");
        h.assertValueEqual(result.getCount(),1,"Exactly one full magazine"); h.succeed();
    }
    private static void packedLmg(GameTestHelper h) {
        var out=craft(h,grid(ammo("lmgmagazineempty",1),ammo("rifleroundsstack_incendiary",1),ammo("rifleroundsstack_incendiary",1)));
        h.assertTrue(out.is(TGContent.AMMO.get("lmgmagazine_incendiary").get()),"Two original four-bundle stacks fill LMG"); h.succeed();
    }
    private static void armor(GameTestHelper h, String mode) {
        var t=target(h,mode.equals("witch")?EntityTypes.WITCH:mode.equals("immune")?EntityTypes.BLAZE:mode.equals("npc")?NpcContent.SUPER_MUTANT.get():EntityTypes.PIG);
        if(mode.equals("armor")) t.getAttribute(Attributes.ARMOR).setBaseValue(10);
        if(mode.equals("resistance")) t.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,200));
        var s=aimed(h,Weapons.definition("ak47"),t);
        float armor=mode.equals("armor")?10:0, fireArmor=armor*.5f;
        if(t instanceof NpcTypedArmor typed) { armor=typed.armorAgainst(DamageKind.PHYSICAL); fireArmor=typed.armorAgainst(DamageKind.FIRE); }
        float toughness=mode.equals("npc")?1:0;
        near(h,t.getAttributeValue(Attributes.ARMOR_TOUGHNESS),toughness,"Source SuperMutant toughness is one");
        float amount=ArmorMath.afterArmor(9*1.1f,fireArmor,toughness,.5f)*(mode.equals("witch")?.15f:1)+ArmorMath.afterArmor(.01f,armor,toughness,0);
        try {
            s.tick(); near(h,1000-t.getHealth(),amount,"FIRE armor, magic and ordinary physical impulse stay separate");
            h.assertValueEqual(t.isOnFire(),!t.fireImmune(),"Only native fire immunity suppresses ignition");
        } finally { s.discard(); t.discard(); } h.succeed();
    }
    private static void playerArmor(GameTestHelper h) {
        var p=ArmorGameTests.player(h); ArmorGameTests.equipAll(p); var shot=shot(h,Weapons.definition("revolver"),p.position().add(2,1,0),false);
        try {
            float before=p.getHealth(); p.hurtServer(h.getLevel(),shot.shotDamage().source(h.getLevel(),IncendiaryBullet.DAMAGE_TYPE,shot),10);
            near(h,before-p.getHealth(),4.6,"Full T2 uses source FIRE absorption, not PROJECTILE armor");
        } finally { shot.discard(); } h.succeed();
    }
    private static void pellets(GameTestHelper h) {
        var t=target(h,EntityTypes.PIG);
        try {
            for(int i=0;i<8;i++) aimed(h,Weapons.definition("sawedoff"),t).tick();
            near(h,1000-t.getHealth(),8*4*1.1f+.01f,"Every pellet bypasses main damage cooldown; preliminary hit only once");
        } finally { t.discard(); } h.succeed();
    }
    private static void cancel(GameTestHelper h,String mode) {
        var t=target(h,EntityTypes.PIG); var s=aimed(h,Weapons.definition("revolver"),t);
        Consumer<ProjectileImpactEvent> impact=e->{if(e.getProjectile()==s)e.setCanceled(true);};
        Consumer<LivingIncomingDamageEvent> damage=e->{if(e.getSource().getDirectEntity()==s && (!mode.equals("fire_only")||e.getSource().is(IncendiaryBullet.DAMAGE_TYPE)))e.setCanceled(true);};
        if(mode.equals("impact")) NeoForge.EVENT_BUS.addListener(impact); else NeoForge.EVENT_BUS.addListener(damage);
        try {
            s.tick(); near(h,1000-t.getHealth(),mode.equals("fire_only")?.01:0,"Cancelled damage respects source preliminary hit");
            h.assertTrue(!t.isOnFire(),"Vetoed hit never ignites"); h.assertValueEqual(s.isRemoved(),!mode.equals("impact"),"Impact veto lets projectile continue");
        } finally { NeoForge.EVENT_BUS.unregister(mode.equals("impact")?impact:damage); s.discard(); t.discard(); } h.succeed();
    }
    private static void reloadCancel(GameTestHelper h,String mode) {
        var stack=gun("thompson",0); var p=player(h,stack); p.getInventory().setItem(1,ammo("smgmagazine_incendiary",1)); p.getInventory().setItem(2,ammo("smgmagazine",1));
        h.assertTrue(ReloadSessions.begin(p),"Reload started");
        if(mode.equals("ammo_removed")) p.getInventory().setItem(1,ItemStack.EMPTY);
        if(mode.equals("variant")) stack.set(TGContent.BALLISTIC_VARIANT.get(),BallisticVariant.DEFAULT);
        if(mode.equals("stack")) p.setItemInHand(InteractionHand.MAIN_HAND,stack.copy());
        for(int i=0;i<40;i++) ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack),0,"Cancelled/unavailable ammunition cannot reload");
        h.assertValueEqual(count(p,"smgmagazine"),1,"No fallback to wrong variant");
        h.assertValueEqual(count(p,"smgmagazine_incendiary"),mode.equals("ammo_removed")?0:1,"No input loss after cancellation");
        h.assertTrue(!ReloadSessions.active(p) && !stack.has(TGContent.RELOAD_TICKS.get()),"Session cleared"); h.succeed();
    }
    private static void fullInventory(GameTestHelper h) {
        var stack=gun("as50",9); var p=player(h,stack);
        for(int i=1;i<p.getInventory().getContainerSize();i++) p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        p.getInventory().setItem(1,ammo("as50magazine_incendiary",2)); GunItem.completeReload(p,stack);
        for(String id:List.of("as50magazineempty","sniperrounds_incendiary")) {
            int dropped=h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(3),e->e.getItem().is(TGContent.AMMO.get(id).get())).stream().mapToInt(e->e.getItem().getCount()).sum();
            h.assertValueEqual(dropped,1,"Full inventory drops exact remainder " + id);
        }
        h.assertValueEqual(GunItem.rounds(stack),10,"Full inventory still reloads"); h.succeed();
    }
    private static void creative(GameTestHelper h) {
        var stack=gun("combatshotgun",0); var p=player(h,stack); p.getAbilities().instabuild=true;
        h.assertTrue(ReloadSessions.begin(p),"Creative selected variant without ammo"); for(int i=0;i<50;i++)ReloadSessions.tick(p);
        h.assertValueEqual(GunItem.rounds(stack),8,"Creative reload fills all shells"); h.assertValueEqual(BallisticAmmo.variant(stack),BallisticVariant.INCENDIARY,"Variant retained"); h.succeed();
    }
    private static void npc(GameTestHelper h) {
        var mob=h.spawnWithNoFreeWill(NpcContent.BANDIT.get(),new Vec3(3,70,4)); var t=target(h,EntityTypes.PIG);
        mob.setItemSlot(EquipmentSlot.MAINHAND,gun("boltaction",0)); mob.setNoGravity(true);
        try {
            h.assertTrue(NpcCombat.fire(mob,t),"NPC uses equipped ammunition component");
            var shots=h.getLevel().getEntitiesOfClass(IncendiaryBullet.class,mob.getBoundingBox().inflate(3),e->e.getOwner()==mob);
            h.assertValueEqual(shots.size(),1,"NPC creates incendiary projectile");
            h.assertTrue(shots.getFirst().shotDamage().npc() && !shots.getFirst().damagesBlocks(),"NPC difficulty policy persists without terrain fire"); shots.forEach(Entity::discard);
        } finally { mob.discard(); t.discard(); } h.succeed();
    }
    private static void zeroDamage(GameTestHelper h) {
        var t=target(h,EntityTypes.PIG); var s=aimed(h,Weapons.definition("revolver"),t); s.npcDamage(0);
        try { s.tick(); near(h,t.getHealth(),1000,"Zero NPC scale suppresses main and preliminary damage"); h.assertTrue(!t.isOnFire(),"Zero damage cannot ignite"); }
        finally { s.discard(); t.discard(); } h.succeed();
    }
    private static void flight(GameTestHelper h,String id) {
        var s=shot(h,Weapons.definition(id),h.absoluteVec(new Vec3(4,80,4)),true); s.npcDamage(.5f); s.setDeltaMovement(.5,.2,.1); var start=s.position(); s.tick();
        near(h,s.getX(),start.x+.5,"Movement precedes drag"); near(h,s.getDeltaMovement().y,.2*(double).99f,"Factory forwards no gravity");
        var restored=new IncendiaryBullet(TGContent.INCENDIARY_BULLET.get(),h.getLevel()); NetherGameTests.load(h,restored,NetherGameTests.save(h,s));
        h.assertValueEqual(restored.weapon(),s.weapon(),"Weapon survives save"); h.assertValueEqual(restored.shotDamage(),s.shotDamage(),"NPC scale survives save");
        h.assertTrue(restored.damagesBlocks(),"Firing-time block policy survives save"); h.assertValueEqual(restored.age(),1,"Lifetime survives save");
        s.tick(); restored.tick(); h.assertValueEqual(restored.position(),s.position(),"Restored projectile follows same trajectory"); s.discard(); restored.discard(); h.succeed();
    }
    private static void water(GameTestHelper h) {
        h.setBlock(4,2,4,Blocks.WATER); var s=shot(h,Weapons.definition("revolver"),h.absoluteVec(new Vec3(4.5,2.2,4.5)),false); s.setDeltaMovement(.1,0,0); s.tick();
        h.assertTrue(s.isInWater(),"Water fixture"); near(h,s.getDeltaMovement().x,.1*(double).85f,"Original water drag"); s.discard(); h.succeed();
    }
    private static void falloff(GameTestHelper h) {
        var t=target(h,EntityTypes.PIG); var s=aimed(h,Weapons.definition("revolver"),t); var saved=NetherGameTests.save(h,s);
        saved.putDouble("origin_x",s.getX()-16); saved.putDouble("origin_y",s.getY()); saved.putDouble("origin_z",s.getZ());
        NetherGameTests.load(h,s,saved);
        try { s.tick(); near(h,1000-t.getHealth(),7.7+.01,"Saved origin and pre-impact distance, not advanced position"); }
        finally { s.discard(); t.discard(); } h.succeed();
    }
    private static void invalidSaves(GameTestHelper h) {
        var s=shot(h,Weapons.definition("revolver"),h.absoluteVec(new Vec3(4,80,4)),false); var saved=NetherGameTests.save(h,s);
        for(int i=0;i<s.weapon().stats().projectileLifetime()-1;i++)s.tick(); h.assertTrue(!s.isRemoved(),"Last movement remains"); s.tick(); h.assertTrue(s.isRemoved(),"Exact source lifetime");
        for(String key:List.of("weapon","age","origin")) {
            var broken=saved.copy(); if(key.equals("weapon"))broken.putString("weapon","handcannon"); if(key.equals("age"))broken.putInt("age",-1); if(key.equals("origin"))broken.putDouble("origin_x",1);
            var b=new IncendiaryBullet(TGContent.INCENDIARY_BULLET.get(),h.getLevel()); NetherGameTests.load(h,b,broken); h.assertTrue(b.isRemoved(),"Reject invalid saved "+key);
        } h.succeed();
    }
    private static void invalidItems(GameTestHelper h) {
        h.assertTrue(BallisticAmmo.CODEC.parse(JsonOps.INSTANCE,new JsonPrimitive("explosive")).error().isPresent(),"Unsupported component rejected");
        for(String id:List.of("handcannon","lasergun","rocketlauncher","chainsaw")) h.assertValueEqual(BallisticAmmo.variant(gun(id,1)),BallisticVariant.DEFAULT,"Unsupported weapon ignores injected ballistic mode");
        for(var wrong:List.of(ammo("pistolrounds_incendiary",1),ammo("smgmagazineempty",1),new ItemStack(Items.COAL))) {
            var input=CraftingInput.of(2,1,List.of(gun("thompson",5),wrong));
            h.assertTrue(h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).isEmpty(),"No wrong-family, empty-magazine or fake switch");
        }
        var p=player(h,gun("revolver",0)); p.getInventory().setItem(1,ammo("pistolrounds",64)); h.assertTrue(!GunItem.canReload(p,p.getMainHandItem()),"No fallback to ordinary rounds"); h.succeed();
    }
    private static void gilding(GameTestHelper h) {
        var stack=gun("revolver",3); var ingot=new ItemStack(Items.GOLD_INGOT);
        var out=craft(h,List.of(ingot,ingot,ingot,ingot,stack,ingot,ingot,ingot,ingot));
        h.assertTrue(out.is(TGContent.GUNS.get("goldenrevolver").get()),"Original gilding recipe");
        h.assertValueEqual(BallisticAmmo.variant(out),BallisticVariant.INCENDIARY,"Upgrade preserves selected ammunition"); h.assertValueEqual(GunItem.rounds(out),3,"Upgrade preserves round count"); h.succeed();
    }
    private static void seed(IncendiaryBullet shot, boolean ignite) {
        for(long seed=0;seed<100000;seed++) if(IncendiaryAmmo.ignites(shot.weapon().stats(),RandomSource.create(seed).nextDouble())==ignite) { shot.getRandom().setSeed(seed); return; }
        throw new IllegalStateException("No deterministic ignition seed");
    }
    private static void fireFace(GameTestHelper h,Direction face) {
        var p=h.absolutePos(new BlockPos(4,4,4)); var dest=p.relative(face); h.getLevel().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(dest.below(),Blocks.STONE.defaultBlockState()); h.getLevel().setBlockAndUpdate(dest,Blocks.AIR.defaultBlockState());
        var s=shot(h,Weapons.definition("revolver"),Vec3.atCenterOf(p).add(2,2,2),true); seed(s,true);
        try { h.assertTrue(s.igniteBlock(new BlockHitResult(Vec3.atCenterOf(p),face,p,false)),"Air cell on hit face receives fire"); h.assertTrue(h.getLevel().getBlockState(dest).is(Blocks.FIRE),"Fire occupies chosen face"); }
        finally { s.discard(); h.getLevel().setBlockAndUpdate(dest,Blocks.AIR.defaultBlockState()); } h.succeed();
    }
    private static void fireRules(GameTestHelper h) {
        var p=h.absolutePos(new BlockPos(4,3,4)); h.getLevel().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState()); var hit=new BlockHitResult(Vec3.atCenterOf(p),Direction.UP,p,false);
        var s=shot(h,Weapons.definition("revolver"),Vec3.atCenterOf(p).add(2,2,2),false);
        try {
            seed(s,true); h.assertTrue(!s.igniteBlock(hit),"Safe firing policy blocks ignition");
            s.configure(s.weapon(),true); seed(s,false); h.assertTrue(!s.igniteBlock(hit),"Failed source probability roll");
            h.getLevel().setBlockAndUpdate(p.above(),Blocks.TORCH.defaultBlockState()); seed(s,true); h.assertTrue(!s.igniteBlock(hit),"Replaceable but non-air block is retained");
            h.assertTrue(h.getLevel().getBlockState(p.above()).is(Blocks.TORCH),"No block replacement");
        } finally { s.discard(); } h.succeed();
    }
    private static void blockImpact(GameTestHelper h) {
        var wall=h.absolutePos(new BlockPos(5,3,4)); var dest=wall.west(); h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState()); h.getLevel().setBlockAndUpdate(dest.below(),Blocks.STONE.defaultBlockState());
        for(boolean cancel:new boolean[]{true,false}) {
            h.getLevel().setBlockAndUpdate(dest,Blocks.AIR.defaultBlockState());
            var s=shot(h,Weapons.definition("revolver"),Vec3.atCenterOf(dest),true); s.setDeltaMovement(1,0,0); seed(s,true);
            Consumer<ProjectileImpactEvent> listener=e->{if(e.getProjectile()==s && cancel)e.setCanceled(true);}; NeoForge.EVENT_BUS.addListener(listener);
            try { s.tick(); h.assertValueEqual(h.getLevel().getBlockState(dest).is(Blocks.FIRE),!cancel,"Impact cancellation controls block ignition"); h.assertValueEqual(s.isRemoved(),!cancel,"Cancelled impact continues"); }
            finally { NeoForge.EVENT_BUS.unregister(listener); s.discard(); }
        } h.getLevel().setBlockAndUpdate(dest,Blocks.AIR.defaultBlockState()); h.succeed();
    }
    private static void permission(GameTestHelper h) {
        boolean before=SafeMode.OP_ONLY.get(); var stack=gun("revolver",6); var p=player(h,stack); p.setData(SafeMode.SAFE,false);
        try {
            SafeMode.OP_ONLY.set(true); h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"Restricted player may still fire at entities");
            var shots=h.getLevel().getEntitiesOfClass(IncendiaryBullet.class,p.getBoundingBox().inflate(3),s->s.getOwner()==p);
            h.assertValueEqual(shots.size(),1,"One shot"); h.assertTrue(!shots.getFirst().damagesBlocks(),"Permission denial overrides stored unsafe flag"); shots.forEach(Entity::discard);
        } finally { SafeMode.OP_ONLY.set(before); } h.succeed();
    }
}
