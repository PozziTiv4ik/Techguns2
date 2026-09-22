package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.armor.*;
import techguns.modern.machine.camo.*;
import techguns.modern.machine.grinder.*;
import techguns.modern.machine.repair.*;
import techguns.modern.radiation.RadiationSystem;

final class CommandoArmorGameTests {
    private static final BlockPos POS=new BlockPos(4,2,4);
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var slot:ArmorSlot.values()) {
            var name=slot.name().toLowerCase(Locale.ROOT);
            r.register("commando_armor_crafting_"+name,()->h->crafting(h,slot));
            r.register("commando_armor_repair_"+name,()->h->repair(h,slot));
            for(int wear:new int[]{0,1,989}) r.register("grinder_commando_"+name+"_"+wear,()->h->grinder(h,slot,wear));
        }
        r.register("commando_armor_equipping_single_skin_and_tooltips",()->CommandoArmorGameTests::equip);
        r.register("commando_armor_movement_jump_fall_radiation_wear",()->CommandoArmorGameTests::bonuses);
        for(String kind:List.of("physical","bullet","fire","explosion","acid","radiation")) r.register("commando_armor_damage_"+kind,()->h->damage(h,kind));
        r.register("commando_armor_anvil_and_repair_shortage",()->CommandoArmorGameTests::anvil);
        for(boolean lava:new boolean[]{false,true}) for(boolean eyes:new boolean[]{false,true}) r.register("commando_water_mining_"+lava+"_"+eyes,()->h->waterMining(h,lava,eyes));
        r.register("commando_accuracy_partial_worn_and_inventory",()->CommandoArmorGameTests::accuracy);
        for(String gun:List.of("ak47","combatshotgun","lasergun","netherblaster","rocketlauncher","chainsaw")) r.register("commando_actual_shot_spread_"+gun,()->h->shotSpread(h,gun));
        r.register("commando_helmet_does_not_generate_oxygen",()->CommandoArmorGameTests::oxygen);
    }
    private static Player player(GameTestHelper h) { var p=ArmorGameTests.player(h); p.getInventory().clearContent(); return p; }
    private static ItemStack armor(ArmorSlot slot,int wear) { var s=ArmorContent.COMMANDO.get(slot).toStack(); s.setDamageValue(wear); return s; }
    private static void suit(Player p,int wear) { for(var slot:ArmorSlot.values()) p.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,wear)); p.tick(); }
    private static ItemStack rubber(int n) { return TGContent.MATERIALS.get("rubberbar").toStack(n); }
    private static ItemStack metal(int n) { return TGContent.MATERIALS.get("ingotobsidiansteel").toStack(n); }
    private static void near(GameTestHelper h,double a,double b,String message) { ArmorGameTests.near(h,a,b,message); }
    private static void crafting(GameTestHelper h,ArmorSlot slot) {
        String[] patterns={"ihi hgh".replace(" ",""),"h hhihiii","hihi ih h","i ih h"}; var items=new ArrayList<ItemStack>();
        for(char c:patterns[slot.ordinal()].toCharArray()) items.add(switch(c) { case 'i'->metal(1); case 'h'->rubber(1); case 'g'->new ItemStack(Items.GLASS_PANE); default->ItemStack.EMPTY; });
        var input=CraftingInput.of(3,items.size()/3,items); var result=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input);
        h.assertTrue(result.is(ArmorContent.COMMANDO.get(slot).get()) && result.getCount()==1,"Original rubber, obsidian steel and helmet pane recipe");
        var invalid=new ArrayList<>(items); for(int i=0;i<invalid.size();i++) if(invalid.get(i).is(rubber(1).getItem())) invalid.set(i,TGContent.MATERIALS.get("rawrubber").toStack());
        h.assertTrue(h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,CraftingInput.of(3,invalid.size()/3,invalid),h.getLevel()).isEmpty(),"Raw rubber cannot replace processed rubber"); h.succeed();
    }
    private static void equip(GameTestHelper h) {
        var p=player(h); p.setShiftKeyDown(true);
        for(var slot:ArmorSlot.values()) {
            var s=armor(slot,0); h.assertTrue(s.getMaxDamage()==990 && !s.isEnchantable(),"Source durability and no enchanting-table value"); p.setItemInHand(InteractionHand.MAIN_HAND,s); s.getItem().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
            h.assertTrue(p.getItemBySlot(EquipmentSlot.valueOf(slot.name())).is(ArmorContent.COMMANDO.get(slot).get()),"Sneak-use equips single-skin armor");
            s=p.getItemBySlot(EquipmentSlot.valueOf(slot.name()));
            h.assertValueEqual(s.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier(),TGContent.id("t2_commando"),"Original equipment texture");
            var lines=new ArrayList<Component>(); s.getItem().appendHoverText(s,Item.TooltipContext.EMPTY,TooltipDisplay.DEFAULT,lines::add,TooltipFlag.NORMAL);
            h.assertTrue(lines.stream().anyMatch(c->c.getString().contains("125")) && lines.stream().anyMatch(c->c.getString().contains("5%")),"Water mining and accuracy bonuses exposed");
        }
        p.setShiftKeyDown(false); h.setBlock(POS,CamoBenchContent.BLOCK.get()); var b=h.getBlockEntity(POS,CamoBenchBlockEntity.class); b.setOwner(p); p.containerMenu=new CamoBenchMenu(97,p.getInventory(),b); b.setItem(0,armor(ArmorSlot.HEAD,123));
        h.assertTrue(!p.containerMenu.clickMenuButton(p,1),"No invented Commando camouflage variants"); h.assertValueEqual(b.getItem(0).getDamageValue(),123,"Rejected recolor preserves wear"); h.succeed();
    }
    private static void bonuses(GameTestHelper h) {
        var p=player(h); suit(p,0); near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.14,"Four ten-percent bonuses"); near(h,p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),.4,"Source knockback sum"); near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),1,"Radiation buildup resistance");
        p.setSprinting(true); TGArmorSystem.refresh(p); near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.234,"Doubled armor speed composes with vanilla sprint");
        p.setDeltaMovement(Vec3.ZERO); p.jumpFromGround(); near(h,p.getDeltaMovement().y,.52,"Boot jump"); var fall=new LivingFallEvent(p,10,1); NeoForge.EVENT_BUS.post(fall); near(h,fall.getDistance(),7.2,"Boot fall distance adjustment");
        for(var slot:TGArmorSystem.SLOTS) p.getItemBySlot(slot).setDamageValue(989); TGArmorSystem.refresh(p);
        near(h,p.getAttributeValue(Attributes.MOVEMENT_SPEED),.13,"Worn gear loses speed immediately"); near(h,p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE),0,"Worn knockback"); near(h,p.getAttributeValue(RadiationSystem.RESISTANCE),1,"Source radiation attribute remains at wear limit");
        near(h,TGArmorSystem.oxygenGear(p),0,"Worn helmet loses oxygen eligibility"); near(h,TGArmorSystem.gunAccuracyMultiplier(p),1,"Worn precision removed"); h.succeed();
    }
    private static DamageSource source(GameTestHelper h,String kind) {
        if(kind.equals("physical")) return h.getLevel().damageSources().playerAttack(WeaponGameTests.player(h));
        if(kind.equals("explosion")) return h.getLevel().damageSources().explosion(null,null);
        if(kind.equals("bullet")) { var b=new Bullet(TGContent.BULLET.get(),h.getLevel()); b.configure(Weapons.definition("ak47")); return ShotDamage.PLAYER.source(h.getLevel(),ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("bullet")),b); }
        var key=switch(kind) { case "fire"->NetherBlasterProjectile.DAMAGE_TYPE; case "acid"->techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE; default->RadiationSystem.DAMAGE; };
        return new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key));
    }
    private static void damage(GameTestHelper h,String kind) {
        var p=player(h); suit(p,0); float amount=kind.equals("bullet")?9:10; float expected=switch(kind) { case "physical"->2.8f; case "bullet"->2.52f; case "fire","explosion"->3.6f; case "acid"->6; default->8; };
        p.hurtServer(h.getLevel(),source(h,kind),amount); near(h,1000-p.getHealth(),expected,"Source typed absorption with toughness");
        h.assertTrue(TGArmorSystem.SLOTS.stream().allMatch(slot->p.getItemBySlot(slot).getDamageValue()>0),"Real damage wears equipped armor"); h.succeed();
    }
    private static RepairBenchBlockEntity bench(GameTestHelper h,Player p) { h.setBlock(POS,RepairBenchContent.BLOCK.get()); var b=h.getBlockEntity(POS,RepairBenchBlockEntity.class); b.setOwner(p); p.containerMenu=new RepairBenchMenu(96,p.getInventory(),b); return b; }
    private static void repair(GameTestHelper h,ArmorSlot slot) {
        var p=player(h); var b=bench(h,p); var s=armor(slot,989); s.set(DataComponents.CUSTOM_NAME,Component.literal("Commando repair")); p.setItemSlot(EquipmentSlot.valueOf(slot.name()),s);
        int[][] costs={{1,1},{2,2},{1,2},{1,1}}; var cost=costs[slot.ordinal()]; b.setItem(0,metal(cost[0])); b.setItem(1,rubber(cost[1]));
        h.assertTrue(p.containerMenu.clickMenuButton(p,slot.ordinal()+1),"Real bench accepts source materials"); h.assertTrue(s.getDamageValue()==0 && b.isEmpty(),"Exact costs restore complete durability"); h.assertValueEqual(s.getHoverName().getString(),"Commando repair","Name retained"); h.succeed();
    }
    private static void anvil(GameTestHelper h) {
        var p=player(h); var s=armor(ArmorSlot.CHEST,989); var menu=new AnvilMenu(1,p.getInventory()); menu.getSlot(0).set(s); menu.getSlot(1).set(metal(1)); menu.createResult(); h.assertValueEqual(menu.getSlot(2).getItem().getDamageValue(),742,"One ingot restores floor(990/4)");
        menu.getSlot(1).set(rubber(1)); menu.createResult(); h.assertTrue(menu.getSlot(2).getItem().isEmpty(),"Rubber is bench material, not original anvil repair item");
        var book=new ItemStack(Items.ENCHANTED_BOOK); var mending=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING); var enchant=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY); enchant.set(mending,1); book.set(DataComponents.STORED_ENCHANTMENTS,enchant.toImmutable());
        menu.getSlot(1).set(book); menu.createResult(); h.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(mending,menu.getSlot(2).getItem()),1,"Native enchanted books supported");
        var b=bench(h,p); b.setItem(9,s); b.setItem(0,metal(2)); b.setItem(1,rubber(1)); h.assertTrue(!p.containerMenu.clickMenuButton(p,6),"Missing one rubber prevents partial repair"); h.assertTrue(s.getDamageValue()==989 && b.getItem(0).getCount()==2 && b.getItem(1).getCount()==1,"No partial consumption"); h.succeed();
    }
    private static void grinder(GameTestHelper h,ArmorSlot slot,int wear) {
        h.setBlock(POS,GrinderContent.BLOCK.get()); var b=h.getBlockEntity(POS,GrinderBlockEntity.class); b.setItem(0,armor(slot,wear)); try(var tx=Transaction.openRoot()) { b.energy().insert(500,tx); tx.commit(); }
        int[][] healthy={{2,1},{3,2},{2,2},{2,1}},used={{1,1},{2,2},{1,2},{1,1}}; int[] expected=wear==989?new int[]{1,0}:wear==0?healthy[slot.ordinal()]:used[slot.ordinal()];
        h.succeedWhen(()->{ h.assertTrue(!b.working() && b.getItem(0).isEmpty(),"Native powered recycling completes"); int metalCount=0,rubberCount=0;
            for(int i=2;i<11;i++) { var s=b.getItem(i); if(s.is(metal(1).getItem())) metalCount+=s.getCount(); else if(s.is(rubber(1).getItem())) rubberCount+=s.getCount(); else h.assertTrue(s.isEmpty(),"No unrelated salvage"); }
            h.assertValueEqual(metalCount,expected[0],"Obsidian steel salvage"); h.assertValueEqual(rubberCount,expected[1],"Rubber salvage"); h.assertValueEqual(b.energy().getAmountAsInt(),0,"Exactly 500 FE"); });
    }
    private static void immerse(GameTestHelper h,Player p,boolean lava,boolean eyes) {
        var center=h.absolutePos(new BlockPos(4,0,4)).atY(120); var fluid=lava?Blocks.LAVA:Blocks.WATER;
        for(int x=-2;x<=2;x++) for(int y=0;y<4;y++) for(int z=-2;z<=2;z++) h.getLevel().setBlock(center.offset(x,y,z),(y<(eyes?4:1)?fluid:Blocks.AIR).defaultBlockState(),2);
        p.setPos(Vec3.atBottomCenterOf(center)); p.setDeltaMovement(Vec3.ZERO); p.tick(); p.setPos(Vec3.atBottomCenterOf(center)); p.setDeltaMovement(Vec3.ZERO); p.tick(); p.setOnGround(true);
        h.assertTrue(p.isEyeInFluid(lava?FluidTags.LAVA:FluidTags.WATER)==eyes,"Fixture measures actual eye immersion");
    }
    private static void waterMining(GameTestHelper h,boolean lava,boolean eyes) {
        var p=player(h); p.setInvulnerable(true); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE)); immerse(h,p,lava,eyes);
        var pos=h.absolutePos(POS); float base=p.getDestroySpeed(Blocks.STONE.defaultBlockState(),pos); int pieces=0;
        for(var slot:ArmorSlot.values()) { p.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,0)); near(h,p.getDestroySpeed(Blocks.STONE.defaultBlockState(),pos),base*(1+(eyes?1.25:0)*++pieces),"Each piece adds source bonus only with submerged eyes"); }
        p.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(989); near(h,p.getDestroySpeed(Blocks.STONE.defaultBlockState(),pos),base*(eyes?4.75:1),"Worn piece immediately stops contributing");
        p.setItemSlot(EquipmentSlot.HEAD,ArmorContent.T1_MINER.get(ArmorSlot.HEAD).toStack()); near(h,p.getDestroySpeed(Blocks.STONE.defaultBlockState(),pos),base*1.05*(eyes?4.75:1),"Ordinary and water mining multiply, rather than add"); h.succeed();
    }
    private static void accuracy(GameTestHelper h) {
        var p=player(h); p.getInventory().add(armor(ArmorSlot.CHEST,0)); p.setItemSlot(EquipmentSlot.OFFHAND,armor(ArmorSlot.HEAD,0)); near(h,TGArmorSystem.gunAccuracyMultiplier(p),1,"Inventory/offhand do not contribute");
        int n=0; for(var slot:ArmorSlot.values()) { p.setItemSlot(EquipmentSlot.valueOf(slot.name()),armor(slot,0)); near(h,TGArmorSystem.gunAccuracyMultiplier(p),1-.05*++n,"Five percent per equipped part"); }
        p.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(988); near(h,TGArmorSystem.gunAccuracyMultiplier(p),.8,"Last active durability point"); p.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(989); near(h,TGArmorSystem.gunAccuracyMultiplier(p),.85,"Wear acts immediately without tick");
        p.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(0); near(h,TGArmorSystem.gunAccuracyMultiplier(p),.8,"Repair restores precision"); h.succeed();
    }
    private static void shotSpread(GameTestHelper h,String id) {
        var p=player(h); suit(p,0); p.setPos(h.absoluteVec(new Vec3(4,100,4))); p.setYRot(0); p.setXRot(0); var stack=TGContent.GUNS.get(id).toStack(); p.setItemInHand(InteractionHand.MAIN_HAND,stack); var gun=Weapons.definition(id);
        var shots=new ArrayList<Projectile>(); Consumer<EntityJoinLevelEvent> observe=e->{ if(e.getEntity() instanceof Projectile shot && shot.getOwner()==p) shots.add(shot); }; NeoForge.EVENT_BUS.addListener(observe);
        try {
            for(boolean aiming:new boolean[]{false,true}) {
                if(aiming && !gun.aim().supported()) continue; if(aiming) h.assertTrue(AimSessions.set(p,true),"Original aiming enabled");
                float multiplier=.8f; if(aiming) multiplier*=gun.aim().accuracyMultiplier();
                for(int n=0;n<32;n++) {
                    shots.clear(); stack.set(TGContent.ROUNDS.get(),gun.stats().capacity()); p.getCooldowns().removeCooldown(p.getCooldowns().getCooldownGroup(stack));
                    h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"Actual equipped player fires "+id); h.assertValueEqual(shots.size(),gun.projectileCount(),"All projectiles created");
                    for(int i=0;i<shots.size();i++) { var shot=shots.get(i); double spread=i==0?gun.stats().spread():gun.pelletSpread(),limit=spread*multiplier*40+.00001;
                        if(shot instanceof RocketProjectile) {
                            // Rockets orient their model to the noisy velocity after shooting. The muzzle
                            // position retains the pre-Gaussian yaw and verifies the real fire path.
                            double yaw=Math.toDegrees(Math.atan2(-(shot.getZ()-p.getZ()),-(shot.getX()-p.getX())));
                            h.assertTrue(Math.abs(yaw)<=limit,"Rocket muzzle uses armor-adjusted source yaw before Gaussian flight noise");
                        } else h.assertTrue(Math.abs(shot.getYRot())<=limit && Math.abs(shot.getXRot())<=limit,"Source angular spread includes armor and aim; Gaussian velocity noise remains unchanged"); shot.discard(); }
                } AimSessions.cancel(p);
            }
        } finally { NeoForge.EVENT_BUS.unregister(observe); AimSessions.cancel(p); shots.forEach(Entity::discard); } h.succeed();
    }
    private static void oxygen(GameTestHelper h) {
        var p=player(h); p.setInvulnerable(true); p.setItemSlot(EquipmentSlot.HEAD,armor(ArmorSlot.HEAD,0)); immerse(h,p,false,true); near(h,TGArmorSystem.oxygenGear(p),1,"Original helmet eligibility");
        p.setAirSupply(280); for(int i=0;i<5;i++) p.tick(); h.assertTrue(p.getAirSupply()<280,"Helmet alone does not replenish air without original SCUBA supply");
        p.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(989); near(h,TGArmorSystem.oxygenGear(p),0,"Worn helmet cannot enable supply"); h.succeed();
    }
    private CommandoArmorGameTests() {}
}
