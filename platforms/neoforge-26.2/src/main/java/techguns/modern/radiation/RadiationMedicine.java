package techguns.modern.radiation;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import techguns.modern.TGContent;

public final class RadiationMedicine extends Item {
    private final boolean pills;
    public RadiationMedicine(Properties properties,boolean pills) { super(properties); this.pills=pills; }
    @Override public int getUseDuration(ItemStack stack,LivingEntity user) { return 32; }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return pills ? ItemUseAnimation.EAT : ItemUseAnimation.BOW; }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand) { player.startUsingItem(hand); return InteractionResult.CONSUME; }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity user) {
        if(user instanceof Player player && level instanceof ServerLevel server) {
            player.addEffect(new MobEffectInstance(RadiationSystem.REGENERATION,pills ? 500 : 400,pills ? 1 : 14,false,false));
            if(pills) player.addEffect(new MobEffectInstance(RadiationSystem.PROTECTION,3600,2,false,false));
            ItemStack container=pills ? new ItemStack(Items.GLASS_BOTTLE) : TGContent.MATERIALS.get("infusionbag").toStack();
            if(!player.getInventory().add(container)) player.drop(container,false);
            server.playSound(null,player.blockPosition(),SoundEvents.PLAYER_BREATH,SoundSource.PLAYERS,.5f,.9f+server.getRandom().nextFloat()*.1f);
            player.awardStat(Stats.ITEM_USED.get(this));
            if(player instanceof ServerPlayer real) CriteriaTriggers.CONSUME_ITEM.trigger(real,stack);
            // Original consumables shrink even for creative users.
            stack.shrink(1);
        }
        return stack;
    }
}
