package techguns.modern.test;

import com.mojang.serialization.JsonOps;
import java.util.*;
import java.util.function.Consumer;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.*;
import techguns.modern.*;
import techguns.modern.armor.TGArmorItem;
import techguns.modern.radiation.RadiationSystem;
import techguns.modern.world.*;

final class MilitaryCrateGameTests {
    private record Reward(String id,int weight,int min,int max) {}
    // Independent fixtures transcribed from the six source tables, in source entry order.
    private static final Map<String,List<Reward>> POOLS=Map.of(
        "ammo",parse("pistolrounds,2,1,4;shotgunrounds,2,4,8;riflerounds,2,1,2;sniperrounds,1,1,2;smgmagazine,2,1,2;pistolmagazine,1,1,2;assaultriflemagazine,2,1,3;pistolrounds_incendiary,1,1,2;shotgunrounds_incendiary,1,3,6;riflerounds_incendiary,1,1,2;sniperrounds_incendiary,1,1,1;smgmagazine_incendiary,1,1,2;pistolmagazine_incendiary,1,1,2;assaultriflemagazine_incendiary,1,1,2"),
        "gun",parse("revolver,1,1,1;thompson,1,1,1;ak47,1,1,1;boltaction,1,1,1;m4,1,1,1;pistol,1,1,1;combatshotgun,1,1,1;mac10,1,1,1;aug,1,1,1;flamethrower,1,1,1"),
        "armor",parse("t1_combat_helmet,1,1,1;t1_combat_chestplate,1,1,1;t1_combat_leggings,1,1,1;t1_combat_boots,1,1,1;t2_combat_helmet,1,1,1;t2_combat_chestplate,1,1,1;t2_combat_leggings,1,1,1;t2_combat_boots,1,1,1;t2_commando_helmet,1,1,1;t2_commando_chestplate,1,1,1;t2_commando_leggings,1,1,1;t2_commando_boots,1,1,1"),
        "medical",parse("minecraft:apple,1,1,3;minecraft:bread,1,1,3;minecraft:cooked_beef,1,1,2;radaway,2,1,4;radpills,2,1,4"),
        "explosives",parse("rocket,2,1,3;40mmgrenade,2,1,4;stielgranate,2,1,3;fraggrenade,2,1,2;rocketlauncher,1,1,1;grenadelauncher,1,1,1"),
        "generic",parse("minecraft:iron_ingot,10,1,1;minecraft:redstone,10,1,2;minecraft:coal,10,1,2;minecraft:gunpowder,10,1,2;minecraft:gold_ingot,5,1,1;minecraft:diamond,1,1,1;minecraft:ender_pearl,1,1,1;heavycloth,10,1,3;mechanicalpartsiron,10,1,2;mechanicalpartsobsidiansteel,5,1,1;plasticsheet,5,1,1;rubberbar,5,1,1;ingotobsidiansteel,5,1,1"));
    private static List<Reward> parse(String text) {return Arrays.stream(text.split(";")).map(s->{var a=s.split(",");return new Reward(a[0].contains(":")?a[0]:"techguns:"+a[0],Integer.parseInt(a[1]),Integer.parseInt(a[2]),Integer.parseInt(a[3]));}).toList();}
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        for(var v:MilitaryCrates.ALL) {
            r.register("crate_place_geometry_support_save_"+v.metadata(),()->h->properties(h,v));
            for(String mode:List.of("hand","axe","silk","creative"))r.register("crate_native_mining_"+v.metadata()+"_"+mode,()->h->mine(h,v,mode));
            r.register("crate_nonplayer_drop_"+v.metadata(),()->h->nonplayer(h,v));
        }
        for(String name:POOLS.keySet())r.register("crate_native_loot_complete_"+name,()->h->loot(h,name));
        r.register("crate_fortune_is_luck_and_only_ammo_bonus",()->MilitaryCrateGameTests::fortune);
        for(String mode:List.of("early","late","replace","break","rules","snapshots"))r.register("crate_event_"+mode,()->h->protection(h,mode));
        r.register("crate_loot_weapons_and_medicine_are_usable",()->MilitaryCrateGameTests::useRewards);
        r.register("crate_player_drop_pickup_and_no_duplicate",()->MilitaryCrateGameTests::pickup);
        r.register("crate_player_caused_explosion_keeps_self_drop",()->MilitaryCrateGameTests::explosion);
    }
    private static MilitaryCrateBlock block(int meta){return MilitaryCrateContent.fromMetadata(meta);}
    private static BlockPos pos(GameTestHelper h){return h.absolutePos(new BlockPos(4,3,4));}
    private static Item item(String name){return BuiltInRegistries.ITEM.getValue(Identifier.parse(name));}
    private static void near(GameTestHelper h,double a,double b,String text){h.assertTrue(Math.abs(a-b)<.00001,text+": "+a+" vs "+b);}
    private static LootTable table(GameTestHelper h,String name){return h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("blocks/military_crate_"+name)));}
    private static LootParams params(GameTestHelper h,Player p,int fortune){return MilitaryCrateDrops.params(h.getLevel(),pos(h),p,fortune);}
    private static void properties(GameTestHelper h,MilitaryCrates.Variant v){
        var l=h.getLevel();var p=pos(h);var b=block(v.metadata());var s=b.defaultBlockState();l.setBlock(p,s,3);var bounds=s.getShape(l,p).bounds();
        h.assertValueEqual(s.getDestroySpeed(l,p),4f,"Source hardness");h.assertValueEqual(b.getExplosionResistance(),4f,"Source resistance");h.assertValueEqual(s.getSoundType(),SoundType.STONE,"Short source constructor uses stone sound");
        near(h,bounds.minX,.03125,"Inset X");near(h,bounds.minZ,.03125,"Inset Z");near(h,bounds.maxX,.96875,"Outer X");near(h,bounds.maxY,1,"Full-height collision");h.assertValueEqual(s.getCollisionShape(l,p).bounds(),bounds,"Collision follows source outline, not only visible mesh");
        h.assertTrue(!s.isSolidRender()&&!s.isCollisionShapeFullBlock(l,p)&&!s.isRedstoneConductor(l,p),"Not an opaque/full/conducting cube");h.assertValueEqual(s.getLightDampening(),0,"No opaque light blocking");
        for(var d:Direction.values()){h.assertValueEqual(s.isFaceSturdy(l,p,d,SupportType.CENTER),d.getAxis()==Direction.Axis.Y,"Only vertical center support");h.assertTrue(!s.isFaceSturdy(l,p,d),"No invented full support face");}
        h.assertTrue(!s.hasBlockEntity()&&s.getMenuProvider(l,p)==null,"Loot crate is not storage");
        var player=WeaponGameTests.player(h);h.assertTrue(player.hasCorrectToolForDrops(s),"Harvestable by hand");h.assertTrue(new ItemStack(Items.IRON_AXE).getDestroySpeed(s)>1,"Source wood tool behavior");
        h.assertValueEqual(s.instrument(),NoteBlockInstrument.BASS,"Wood note sound independent of block sound");
        var ops=l.registryAccess().createSerializationContext(NbtOps.INSTANCE);h.assertValueEqual(BlockState.CODEC.parse(ops,BlockState.CODEC.encodeStart(ops,s).getOrThrow()).getOrThrow(),s,"Registry state preserves variant");
        for(var rotation:Rotation.values())h.assertValueEqual(s.rotate(rotation),s,"No invented facing");for(var mirror:Mirror.values())h.assertValueEqual(s.mirror(mirror),s,"Mirroring does not recolor crate");
        var save=ItemStack.CODEC.encodeStart(ops,new ItemStack(b,3)).getOrThrow();h.assertTrue(ItemStack.matches(new ItemStack(b,3),ItemStack.CODEC.parse(ops,save).getOrThrow()),"Stack variant persists");
        for(var face:Direction.values()){
            l.setBlock(p,Blocks.STONE.defaultBlockState(),3);var target=p.relative(face);l.setBlock(target,Blocks.AIR.defaultBlockState(),3);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(b,2));
            var hit=new BlockHitResult(Vec3.atCenterOf(p).add(face.getUnitVec3().scale(.5)),face,p,false);h.assertTrue(player.getMainHandItem().getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Place on every face");h.assertValueEqual(l.getBlockState(target),s,"Exact selected variant placed");h.assertValueEqual(player.getMainHandItem().getCount(),1,"One item spent");l.setBlock(p,Blocks.AIR.defaultBlockState(),3);h.assertTrue(l.getBlockState(target).is(b),"Crate needs no support");l.setBlock(target,Blocks.AIR.defaultBlockState(),3);
        }player.discard();h.succeed();
    }
    private static final class Miner implements AutoCloseable {
        final ServerPlayer player;final ServerPlayerGameMode mode;final EmbeddedChannel channel;
        Miner(GameTestHelper h,boolean creative){var type=creative?GameType.CREATIVE:GameType.SURVIVAL;player=(ServerPlayer)h.makeMockServerPlayer(type);var cookie=CommonListenerCookie.createInitial(player.getGameProfile(),false);var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);player.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,player,cookie);player.setPos(Vec3.atCenterOf(pos(h)).add(0,0,3));mode=new ServerPlayerGameMode(player);mode.changeGameModeForPlayer(type);}
        @Override public void close(){channel.finishAndReleaseAll();player.discard();}
    }
    private static ItemStack tool(GameTestHelper h,String mode){var s=mode.equals("hand")?ItemStack.EMPTY:new ItemStack(Items.IRON_AXE);if(mode.equals("silk"))s.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);return s;}
    private static Consumer<EntityJoinLevelEvent> capture(GameTestHelper h,List<ItemEntity> out){var area=new AABB(pos(h)).inflate(2);return e->{if(e.getLevel()==h.getLevel()&&e.getEntity() instanceof ItemEntity i&&area.contains(i.position()))out.add(i);};}
    private static String pool(MilitaryCrates.Variant v){return v.loot().substring("blocks/military_crate_".length());}
    private static void checkReward(GameTestHelper h,String pool,ItemStack stack){var r=POOLS.get(pool).stream().filter(x->stack.is(item(x.id))).findFirst().orElseThrow(()->new IllegalStateException("Unexpected reward "+stack));h.assertTrue(stack.getCount()>=r.min&&stack.getCount()<=r.max,"Original count range");if(stack.getItem() instanceof GunItem gun)h.assertValueEqual(GunItem.rounds(stack),gun.definition().stats().capacity(),"Metadata-zero gun is fully loaded");if(stack.getItem() instanceof TGArmorItem)h.assertTrue(stack.isDamageableItem()&&stack.getDamageValue()==0,"Source armor starts intact");}
    private static void mine(GameTestHelper h,MilitaryCrates.Variant v,String type){
        var p=pos(h);var b=block(v.metadata());h.getLevel().setBlock(p,b.defaultBlockState(),3);var drops=new ArrayList<ItemEntity>();var listener=capture(h,drops);NeoForge.EVENT_BUS.addListener(listener);
        try(var miner=new Miner(h,type.equals("creative"))){miner.player.setItemInHand(InteractionHand.MAIN_HAND,tool(h,type));h.assertTrue(miner.mode.destroyBlock(p),"Actual ServerPlayerGameMode mining");h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Crate removed");h.assertValueEqual(drops.size(),type.equals("creative")?0:1,"One harvest only, creative drops nothing");if(!drops.isEmpty()){if(type.equals("silk"))h.assertTrue(drops.getFirst().getItem().is(b.asItem()),"Silk Touch returns exact variant");else checkReward(h,pool(v),drops.getFirst().getItem());}}
        finally{NeoForge.EVENT_BUS.unregister(listener);drops.forEach(Entity::discard);}h.succeed();
    }
    private static void nonplayer(GameTestHelper h,MilitaryCrates.Variant v){
        var p=pos(h);var b=block(v.metadata());var drops=new ArrayList<ItemEntity>();var listener=capture(h,drops);NeoForge.EVENT_BUS.addListener(listener);
        try{h.getLevel().setBlock(p,b.defaultBlockState(),3);h.assertTrue(h.getLevel().destroyBlock(p,true),"Actual destruction without a harvester");h.assertValueEqual(drops.size(),1,"One native self drop");h.assertTrue(drops.getFirst().getItem().is(b.asItem()),"Nonplayer does not roll player rewards");}finally{NeoForge.EVENT_BUS.unregister(listener);drops.forEach(Entity::discard);}h.succeed();
    }
    private static void loot(GameTestHelper h,String name){
        var t=table(h,name);var p=WeaponGameTests.player(h);var json=LootTable.DIRECT_CODEC.encodeStart(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),t).getOrThrow().getAsJsonObject();var entries=json.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");var expected=POOLS.get(name);h.assertValueEqual(entries.size(),expected.size(),"No source candidate lost");
        for(int i=0;i<entries.size();i++){var e=entries.get(i).getAsJsonObject();h.assertValueEqual(e.get("name").getAsString(),expected.get(i).id,"Original order and item");h.assertValueEqual(e.has("weight")?e.get("weight").getAsInt():1,expected.get(i).weight,"Exact weight, no redistribution");}
        var seen=new HashSet<String>();var endpoints=new HashSet<String>();for(long seed=1;seed<=2048;seed++){var result=t.getRandomItems(params(h,p,0),RandomSource.create(seed * 0x9e3779b97f4a7c15L));h.assertValueEqual(result.size(),1,"One source roll at zero Fortune");var stack=result.getFirst();checkReward(h,name,stack);String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();seen.add(id);endpoints.add(id+"="+stack.getCount());}
        h.assertValueEqual(seen,expected.stream().map(Reward::id).collect(java.util.stream.Collectors.toSet()),"Every source reward reachable");for(var reward:expected){h.assertTrue(endpoints.contains(reward.id+"="+reward.min),"Inclusive minimum reached");h.assertTrue(endpoints.contains(reward.id+"="+reward.max),"Inclusive maximum reached");}p.discard();h.succeed();
    }
    private static void fortune(GameTestHelper h){
        var p=WeaponGameTests.player(h);p.getAttribute(Attributes.LUCK).setBaseValue(100);var params=params(h,p,3);near(h,params.getLuck(),3,"Fortune replaces player luck");h.assertValueEqual(params.contextMap().getOrThrow(LootContextParams.THIS_ENTITY),p,"Player available to loot functions");
        var counts=new HashSet<Integer>();for(int seed=1;seed<=256;seed++){var one=table(h,"ammo").getRandomItems(params(h,p,1),RandomSource.create(seed * 0x9e3779b97f4a7c15L));h.assertValueEqual(one.size(),1,"Fortune I has floor(random[0,1)*1)=0 extra rolls");var three=table(h,"ammo").getRandomItems(params,RandomSource.create(seed * 0x9e3779b97f4a7c15L));counts.add(three.size());for(var stack:three)checkReward(h,"ammo",stack);for(String name:POOLS.keySet())if(!name.equals("ammo"))h.assertValueEqual(table(h,name).getRandomItems(params,RandomSource.create(seed * 0x9e3779b97f4a7c15L)).size(),1,"No invented bonus rolls in "+name);}
        h.assertValueEqual(counts,Set.of(1,2,3),"All three Fortune III roll counts reachable");
        var tool=new ItemStack(Items.IRON_AXE);tool.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE),3);var pos=pos(h);var results=new HashSet<Integer>();
        Consumer<BlockDropsEvent> observe=e->{if(e.getPos().equals(pos)&&e.getBreaker()==p){results.add(e.getDrops().size());e.setCanceled(true);}};NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,observe);
        try{for(int i=0;i<100;i++)Block.dropResources(block(0).defaultBlockState(),h.getLevel(),pos,null,p,tool);h.assertValueEqual(results,Set.of(1,2,3),"Actual enchanted tool reaches HIGH handler; player luck 100 is ignored");}finally{NeoForge.EVENT_BUS.unregister(observe);p.discard();}h.succeed();
    }
    private static void protection(GameTestHelper h,String mode){
        var p=pos(h);var level=h.getLevel();level.setBlock(p,block(1).defaultBlockState(),3);var drops=new ArrayList<ItemEntity>();var capture=capture(h,drops);NeoForge.EVENT_BUS.addListener(capture);
        Consumer<BlockDropsEvent> change=e->{if(e.getPos().equals(p)){if(mode.equals("replace")){h.assertTrue(e.getDrops().stream().noneMatch(i->i.getItem().is(block(1).asItem())),"HIGH replacement runs before normal handlers");e.getDrops().clear();e.getDrops().add(new ItemEntity(level,p.getX()+.5,p.getY()+.5,p.getZ()+.5,new ItemStack(Items.DIAMOND)));}else e.setCanceled(true);}};
        Consumer<BreakBlockEvent> block=e->{if(e.getPos().equals(p))e.setCanceled(true);};boolean rules=level.getGameRules().get(GameRules.BLOCK_DROPS),snapshots=level.restoringBlockSnapshots;
        if(mode.equals("early")||mode.equals("late")||mode.equals("replace"))NeoForge.EVENT_BUS.addListener(mode.equals("early")?EventPriority.HIGHEST:EventPriority.NORMAL,change);if(mode.equals("break"))NeoForge.EVENT_BUS.addListener(block);
        try(var miner=new Miner(h,false)){if(mode.equals("rules"))level.getGameRules().set(GameRules.BLOCK_DROPS,false,level.getServer());if(mode.equals("snapshots"))level.restoringBlockSnapshots=true;boolean destroyed=miner.mode.destroyBlock(p);h.assertValueEqual(destroyed,!mode.equals("break"),"Mining event cancellation retains block");h.assertValueEqual(drops.size(),mode.equals("replace")?1:0,"Cancelled/suppressed harvest spawns no reward");if(mode.equals("replace"))h.assertTrue(drops.getFirst().getItem().is(Items.DIAMOND),"Later listener owns final drops");}
        finally{NeoForge.EVENT_BUS.unregister(change);NeoForge.EVENT_BUS.unregister(block);NeoForge.EVENT_BUS.unregister(capture);drops.forEach(Entity::discard);level.getGameRules().set(GameRules.BLOCK_DROPS,rules,level.getServer());level.restoringBlockSnapshots=snapshots;}h.succeed();
    }
    private static ItemStack reward(GameTestHelper h,String pool,String item){var p=WeaponGameTests.player(h);try{for(long seed=1;seed<10000;seed++)for(var s:table(h,pool).getRandomItems(params(h,p,0),RandomSource.create(seed * 0x9e3779b97f4a7c15L)))if(s.is(item(item)))return s;throw new IllegalStateException("Source reward unreachable: "+item);}finally{p.discard();}}
    private static void useRewards(GameTestHelper h){
        var p=WeaponGameTests.player(h);p.setPos(h.absoluteVec(new Vec3(4,80,4)));var projectiles=new ArrayList<Entity>();Consumer<EntityJoinLevelEvent> collect=e->{if(e.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile s&&s.getOwner()==p)projectiles.add(s);};NeoForge.EVENT_BUS.addListener(collect);
        try{for(String name:List.of("gun","explosives"))for(var r:POOLS.get(name)){var stack=reward(h,name,r.id);if(!(stack.getItem() instanceof GunItem gun))continue;p.setItemInHand(InteractionHand.MAIN_HAND,stack);h.assertTrue(GunItem.fire(h.getLevel(),p,stack),"Actual loot gun fires immediately: "+r.id);h.assertValueEqual(GunItem.rounds(stack),gun.definition().stats().capacity()-1,"Loaded loot consumes one round");for(int i=0;i<100;i++)p.getCooldowns().tick();}h.assertValueEqual(projectiles.size(),12+7,"Twelve gun rewards, including eight shotgun pellets");}
        finally{NeoForge.EVENT_BUS.unregister(collect);projectiles.forEach(Entity::discard);p.discard();}
        boolean disabled=RadiationSystem.DISABLED.get();try{RadiationSystem.DISABLED.set(false);var patient=WeaponGameTests.player(h);RadiationSystem.add(patient,700);for(String id:List.of("techguns:radpills","techguns:radaway")){var medicine=reward(h,"medical",id);int count=medicine.getCount();medicine.getItem().finishUsingItem(medicine,h.getLevel(),patient);h.assertValueEqual(medicine.getCount(),count-1,"Native loot medicine consumes one item");h.assertTrue(patient.hasEffect(RadiationSystem.REGENERATION),"Actual medication effects");}h.assertTrue(patient.hasEffect(RadiationSystem.PROTECTION),"Loot pills provide protection");patient.discard();}finally{RadiationSystem.DISABLED.set(disabled);}h.succeed();
    }
    private static void pickup(GameTestHelper h){
        var pos=pos(h);h.getLevel().setBlock(pos,block(2).defaultBlockState(),3);var drops=new ArrayList<ItemEntity>();var collect=capture(h,drops);NeoForge.EVENT_BUS.addListener(collect);
        try(var miner=new Miner(h,false)){miner.player.getInventory().clearContent();h.assertTrue(miner.mode.destroyBlock(pos),"Real harvest");h.assertValueEqual(drops.size(),1,"One armor entity");var drop=drops.getFirst();var armor=drop.getItem().getItem();drop.setNoPickUpDelay();drop.playerTouch(miner.player);h.assertValueEqual(miner.player.getInventory().countItem(armor),1,"Real pickup into inventory");h.assertTrue(drop.isRemoved(),"Picked entity consumed");miner.mode.destroyBlock(pos);h.assertValueEqual(drops.size(),1,"Air cannot reroll loot");}finally{NeoForge.EVENT_BUS.unregister(collect);drops.forEach(Entity::discard);}h.succeed();
    }
    private static void explosion(GameTestHelper h){
        var level=h.getLevel();var at=h.absolutePos(new BlockPos(4,200,4));var crate=block(1);level.setBlock(at,crate.defaultBlockState(),3);var player=WeaponGameTests.player(h);var drops=new ArrayList<ItemEntity>();
        Consumer<EntityJoinLevelEvent> observe=e->{if(e.getLevel()==level&&e.getEntity() instanceof ItemEntity item&&new AABB(at).inflate(3).contains(item.position()))drops.add(item);};NeoForge.EVENT_BUS.addListener(observe);boolean decay=level.getGameRules().get(GameRules.TNT_EXPLOSION_DROP_DECAY);
        try{level.getGameRules().set(GameRules.TNT_EXPLOSION_DROP_DECAY,false,level.getServer());level.explode(player,at.getX()+.5,at.getY()+.5,at.getZ()+.5,6,Level.ExplosionInteraction.TNT);h.assertTrue(level.getBlockState(at).isAir(),"Actual explosion removes crate");h.assertValueEqual(drops.size(),1,"No-decay explosion fixture returns one stack");h.assertTrue(drops.getFirst().getItem().is(crate.asItem()),"Player-caused explosion is not a player harvest");}
        finally{level.getGameRules().set(GameRules.TNT_EXPLOSION_DROP_DECAY,decay,level.getServer());NeoForge.EVENT_BUS.unregister(observe);drops.forEach(Entity::discard);player.discard();}h.succeed();
    }
}
