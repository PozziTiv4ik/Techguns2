package techguns.modern.armor;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import techguns.core.DamageKind;
import techguns.modern.TGContent;

/** Player-specific GenericArmor/Forge 2807 stages; NPC intrinsic protection is handled separately. */
public final class TGArmorSystem {
    public static final List<EquipmentSlot> SLOTS=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
    private static final Identifier SPEED=TGContent.id("armor_speed"), KNOCKBACK=TGContent.id("armor_knockback"), DISPLAY=TGContent.id("armor_display");
    public static boolean hasArmor(Player player) { return SLOTS.stream().anyMatch(slot -> player.getItemBySlot(slot).getItem() instanceof TGArmorItem); }
    public static void refresh(Player player) {
        double speed=0,knockback=0,display=0;
        for(var slot:SLOTS) {
            var stack=player.getItemBySlot(slot);
            if (stack.getItem() instanceof TGArmorItem item) {
                display+=item.spec().displayedArmor(stack.getDamageValue());
                if (item.spec().bonusesActive(stack.getDamageValue())) { speed+=item.spec().speed(); knockback+=item.spec().knockback(); }
            }
        }
        update(player.getAttribute(Attributes.MOVEMENT_SPEED),SPEED,speed*(player.isSprinting()?2:1),AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        update(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE),KNOCKBACK,knockback,AttributeModifier.Operation.ADD_VALUE);
        // Vanilla's HUD reads this attribute. The actual special-armor calculation explicitly excludes this display-only modifier.
        update(player.getAttribute(Attributes.ARMOR),DISPLAY,display,AttributeModifier.Operation.ADD_VALUE);
    }
    private static void update(AttributeInstance attribute,Identifier id,double amount,AttributeModifier.Operation operation) {
        var old=attribute.getModifier(id);
        if (old!=null && old.amount()==amount && old.operation()==operation) return;
        attribute.removeModifier(id);
        if (amount!=0) attribute.addTransientModifier(new AttributeModifier(id,amount,operation));
    }
    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post event) { refresh(event.getEntity()); }
    /** TGEventHandler.onBreakEvent runs at NORMAL after tool, effects and higher-priority modifiers. */
    @SubscribeEvent(priority=EventPriority.NORMAL,receiveCanceled=false)
    public static void mining(PlayerEvent.BreakSpeed event) {
        float bonus=0;
        for(var slot:SLOTS) {
            var stack=event.getEntity().getItemBySlot(slot);
            if(stack.getItem() instanceof TGArmorItem item && item.spec().bonusesActive(stack.getDamageValue())) bonus+=(float)item.spec().mining();
        }
        if(bonus!=0) event.setNewSpeed(event.getNewSpeed()*(1+bonus));
    }
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double jump=0;
        for(var slot:SLOTS) { var stack=player.getItemBySlot(slot); if(stack.getItem() instanceof TGArmorItem item && item.spec().bonusesActive(stack.getDamageValue())) jump+=item.spec().jump(); }
        player.setDeltaMovement(player.getDeltaMovement().add(0,jump,0));
    }
    @SubscribeEvent public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        float reduction=0,freeHeight=0;
        for (var slot:SLOTS) {
            var stack=player.getItemBySlot(slot);
            if (stack.getItem() instanceof TGArmorItem item && item.spec().bonusesActive(stack.getDamageValue())) {
                reduction+=(float)item.spec().fallReduction(); freeHeight+=(float)item.spec().freeFallHeight();
            }
        }
        if (reduction>0 || freeHeight>0) event.setDistance(Math.max(0,event.getDistance()-freeHeight)*Math.max(0,1-reduction));
    }
    public static float absorb(Player player,DamageSource source,float damage,DamageKind kind,float penetration) {
        var specialWear=new EnumMap<EquipmentSlot,ArmorHurtEvent.ArmorEntry>(EquipmentSlot.class);
        double ratio=0;
        float toughness=(float)player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        var display=player.getAttribute(Attributes.ARMOR).getModifier(DISPLAY);
        float vanillaArmor=(float)Math.max(0,player.getAttributeValue(Attributes.ARMOR)-(display==null?0:display.amount()));
        for(var slot:SLOTS) {
            var stack=player.getItemBySlot(slot);
            if(stack.getItem() instanceof TGArmorItem item) {
                double piece=item.spec().absorption(kind,penetration); ratio+=piece;
                if(damage>0 && piece>0) specialWear.put(slot,new ArmorHurtEvent.ArmorEntry(stack,(int)Math.max(1,damage*piece)));
            }
        }
        // Supported sets have total ratio <= .80. General high-priority/foreign special armor remains outside this port.
        float remaining=(float)(damage*(1-Math.min(1,ratio)));
        wear(player,source,specialWear,true);
        if (remaining>0 && (vanillaArmor>0 || toughness>0)) {
            var vanillaWear=new EnumMap<EquipmentSlot,ArmorHurtEvent.ArmorEntry>(EquipmentSlot.class);
            for(var slot:SLOTS) {
                var stack=player.getItemBySlot(slot); var equippable=stack.get(DataComponents.EQUIPPABLE);
                if(equippable!=null && equippable.slot()==slot && equippable.damageOnHurt())
                    vanillaWear.put(slot,new ArmorHurtEvent.ArmorEntry(stack,(int)Math.max(1,remaining/4)));
            }
            // Forge 2807 applies a second ordinary wear pass, even when toughness comes only from Techguns armor.
            wear(player,source,vanillaWear,false);
            remaining=CombatRules.getDamageAfterAbsorb(player,remaining,source,vanillaArmor,toughness);
        }
        refresh(player);
        return remaining;
    }
    private static void wear(Player player,DamageSource source,EnumMap<EquipmentSlot,ArmorHurtEvent.ArmorEntry> entries,boolean special) {
        if (entries.isEmpty() || !(player.level() instanceof ServerLevel level) || player.isCreative()) return;
        var event=NeoForge.EVENT_BUS.post(new ArmorHurtEvent(entries,player,source));
        if(event.isCanceled()) return;
        for(var entry:event.getArmorMap().entrySet()) {
            var value=entry.getValue(); var stack=value.armorItemStack;
            if(player.getItemBySlot(entry.getKey())!=stack || !Float.isFinite(value.newDamage)) continue;
            int damage=(int)Math.clamp(value.newDamage,0,Integer.MAX_VALUE);
            if(special && stack.getItem() instanceof TGArmorItem item) damage=item.spec().specialWearLimit(stack.getDamageValue(),damage);
            if(damage>0) stack.hurtAndBreak(damage,level,player,item -> player.onEquippedItemBroken(item,entry.getKey()));
        }
    }
    private TGArmorSystem() {}
}
