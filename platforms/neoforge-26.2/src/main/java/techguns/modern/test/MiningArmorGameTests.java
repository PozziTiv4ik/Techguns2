package techguns.modern.test;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import techguns.core.ArmorSlot;
import techguns.modern.armor.*;

final class MiningArmorGameTests {
    private static final BlockPos POS=new BlockPos(4,2,4);
    static void register(DeferredRegister<Consumer<GameTestHelper>> r) {
        r.register("miner_mining_partial_and_worn_sets",() -> MiningArmorGameTests::partial);
        r.register("miner_mining_inventory_is_not_equipment",() -> MiningArmorGameTests::inventory);
        r.register("miner_mining_haste_fatigue_and_air",() -> MiningArmorGameTests::effects);
        r.register("miner_mining_event_priority_and_cancellation",() -> MiningArmorGameTests::events);
        r.register("miner_mining_server_breaks_stone_faster",() -> MiningArmorGameTests::destroy);
    }
    private static Player player(GameTestHelper h) {
        var p=ArmorGameTests.player(h); p.getInventory().clearContent(); p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE)); p.setOnGround(true); return p;
    }
    private static float speed(GameTestHelper h,Player p) { return p.getDestroySpeed(Blocks.STONE.defaultBlockState(),h.absolutePos(POS)); }
    private static void suit(Player p,int damage) {
        for(var slot:ArmorSlot.values()) { var stack=ArmorContent.T1_MINER.get(slot).toStack(); stack.setDamageValue(damage); p.setItemSlot(EquipmentSlot.valueOf(slot.name()),stack); }
    }
    private static void near(GameTestHelper h,double actual,double expected,String message) { ArmorGameTests.near(h,actual,expected,message); }
    private static void partial(GameTestHelper h) {
        var p=player(h); near(h,speed(h,p),6,"Bare iron pickaxe speed");
        int pieces=0;
        for(var slot:ArmorSlot.values()) {
            p.setItemSlot(EquipmentSlot.valueOf(slot.name()),ArmorContent.T1_MINER.get(slot).toStack());
            near(h,speed(h,p),6*(1+.05*++pieces),"Each equipped part adds five percent");
        }
        p.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(823); near(h,speed(h,p),7.2,"Bonus remains at damage 823");
        p.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(824); near(h,speed(h,p),6.9,"Bonus removed immediately at damage 824 without waiting for a player tick");
        suit(p,824); near(h,speed(h,p),6,"Fully worn gear has no mining bonus");
        p.getItemBySlot(EquipmentSlot.HEAD).setDamageValue(0); near(h,speed(h,p),6.3,"Repair immediately restores the bonus"); h.succeed();
    }
    private static void inventory(GameTestHelper h) {
        var p=player(h); p.getInventory().add(ArmorContent.T1_MINER.get(ArmorSlot.CHEST).toStack());
        p.setItemSlot(EquipmentSlot.OFFHAND,ArmorContent.T1_MINER.get(ArmorSlot.HEAD).toStack());
        for(var slot:ArmorSlot.values()) p.setItemSlot(EquipmentSlot.valueOf(slot.name()),ArmorContent.ITEMS.get(slot).toStack());
        near(h,speed(h,p),6,"Inventory/offhand gear and T2 do not grant mining speed");
        p.setItemSlot(EquipmentSlot.FEET,ArmorContent.T1_MINER.get(ArmorSlot.FEET).toStack());
        near(h,speed(h,p),6.3,"Mixed suit counts only the one miner part");
        for(var slot:ArmorSlot.values()) p.setItemSlot(EquipmentSlot.valueOf(slot.name()),ArmorContent.HAZMAT.get(slot).toStack());
        near(h,speed(h,p),6,"Hazmat also has zero mining bonus"); h.succeed();
    }
    private static void effects(GameTestHelper h) {
        var p=player(h); suit(p,0); p.addEffect(new MobEffectInstance(MobEffects.HASTE,200,1));
        near(h,speed(h,p),6*1.4*1.2,"Haste II is multiplied before armor bonus");
        p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE,200,1));
        near(h,speed(h,p),6*1.4*.09*1.2,"Mining fatigue penalty is retained");
        p.setOnGround(false); near(h,speed(h,p),6*1.4*.09/5*1.2,"Airborne penalty remains");
        p.removeAllEffects(); p.setOnGround(true); p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        near(h,speed(h,p),1.2,"Wrong tool still gets speed bonus");
        h.assertTrue(!p.hasCorrectToolForDrops(Blocks.STONE.defaultBlockState(),h.getLevel(),h.absolutePos(POS)),"Armor does not grant harvest capability"); h.succeed();
    }
    private static void events(GameTestHelper h) {
        var p=player(h); suit(p,0);
        Consumer<PlayerEvent.BreakSpeed> earlier=e -> { if(e.getEntity()==p) e.setNewSpeed(10); };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,earlier);
        try { near(h,speed(h,p),12,"Armor multiplies current event speed, not the original value"); }
        finally { NeoForge.EVENT_BUS.unregister(earlier); }
        Consumer<PlayerEvent.BreakSpeed> cancel=e -> { if(e.getEntity()==p) { e.setNewSpeed(7); e.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,cancel);
        try {
            var event=new PlayerEvent.BreakSpeed(p,Blocks.STONE.defaultBlockState(),6,h.absolutePos(POS)); NeoForge.EVENT_BUS.post(event);
            h.assertTrue(event.isCanceled(),"Earlier cancellation survives"); near(h,event.getNewSpeed(),7,"Cancelled event is not modified by armor");
            h.assertTrue(speed(h,p)<=0,"Native hook rejects canceled mining");
        } finally { NeoForge.EVENT_BUS.unregister(cancel); }
        near(h,speed(h,p),7.2,"Normal behavior restored after cancellation listener is removed"); h.succeed();
    }
    private static void destroy(GameTestHelper h) {
        var level=h.getLevel(); var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"tg-mining-test"),false);
        var p=new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation()) {
            @Override public GameType gameMode() { return GameType.SURVIVAL; }
            @Override public boolean isClientAuthoritative() { return false; }
        };
        var connection=new Connection(PacketFlow.SERVERBOUND); var channel=new EmbeddedChannel(connection);
        p.connection=new ServerGamePacketListenerImpl(level.getServer(),connection,p,cookie); p.snapTo(h.absoluteVec(new Vec3(4,2,2))); p.setOnGround(true);
        var pos=h.absolutePos(POS); var mode=new ServerPlayerGameMode(p);
        try {
            mode.changeGameModeForPlayer(GameType.SURVIVAL); GameType.SURVIVAL.updatePlayerAbilities(p.getAbilities());
            h.assertTrue(!p.getAbilities().instabuild && !mode.isCreative(),"Fixture uses actual survival abilities");
            for(boolean equipped:new boolean[]{false,true}) {
                h.setBlock(POS,Blocks.STONE); p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
                if(equipped) suit(p,0);
                mode.handleBlockBreakAction(pos,Action.START_DESTROY_BLOCK,Direction.NORTH,level.getMaxY(),0);
                // An immediate STOP enters the server's delayed validation path; it must finish the remaining work.
                mode.handleBlockBreakAction(pos,Action.STOP_DESTROY_BLOCK,Direction.NORTH,level.getMaxY(),1);
                int ticks=0;
                while(!level.getBlockState(pos).isAir() && ticks<12) { mode.tick(); ticks++; }
                h.assertTrue(level.getBlockState(pos).isAir(),"Native survival block destruction succeeds");
                h.assertValueEqual(ticks,equipped?6:7,"Miner bonus shortens native server destruction by one tick");
                h.assertValueEqual(p.getMainHandItem().getDamageValue(),1,"Exactly one ordinary tool wear on successful mining");
            }
        } finally {
            channel.finishAndReleaseAll();
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(3)).forEach(Entity::discard);
        }
        h.succeed();
    }
    private MiningArmorGameTests() {}
}
