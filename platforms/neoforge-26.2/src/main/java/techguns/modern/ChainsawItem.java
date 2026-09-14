package techguns.modern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.ItemAbilities;
import techguns.core.ChainsawRules;
import techguns.core.WeaponDefinition;

/** Native melee and block breaking; RMB attacks use the server's ordinary gun validation. */
public final class ChainsawItem extends GunItem {
    private static final java.util.Map<Player,Long> LAST_SOUND = new java.util.WeakHashMap<>();
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("chainsaw"));
    public ChainsawItem(Properties properties, WeaponDefinition gun) { super(properties,gun); }
    @Override public boolean doesSneakBypassUse(ItemStack stack,net.minecraft.world.level.LevelReader level,BlockPos pos,Player player) { return true; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<net.minecraft.network.chat.Component> out, net.minecraft.world.item.TooltipFlag flag) {
        out.accept(net.minecraft.network.chat.Component.translatable("tooltip.techguns.chainsaw.controls"));
        out.accept(net.minecraft.network.chat.Component.translatable("tooltip.techguns.chainsaw.head",ChainsawRules.harvestLevel(head(stack)),ChainsawRules.digSpeed(rounds(stack),head(stack),true)));
    }
    static void playChain(net.minecraft.server.level.ServerLevel level,Player player) {
        long now=level.getGameTime(),last=LAST_SOUND.getOrDefault(player,Long.MIN_VALUE/2);
        if(now-last<10) return;
        LAST_SOUND.put(player,now);
        level.playSound(null,player.getX(),player.getY(),player.getZ(),now-last>12?TGContent.CHAINSAW_START.get():TGContent.SOUND_EVENTS.get("guns.chainsawloop").get(),SoundSource.PLAYERS,1,1);
    }
    public static int head(ItemStack stack) { return ChainsawRules.head(stack.getOrDefault(TGContent.MINING_HEAD.get(),0)); }
    public static boolean effective(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_AXE) || !(state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_HOE));
    }
    @Override public float getDestroySpeed(ItemStack stack, BlockState state) {
        return ChainsawRules.digSpeed(rounds(stack),head(stack),effective(state));
    }
    @Override public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        String tier = switch(head(stack)) { case 1 -> "obsidian"; case 2 -> "carbon"; default -> "default"; };
        return rounds(stack)>0 && effective(state) && !state.is(TagKey.create(Registries.BLOCK,TGContent.id("incorrect_for_chainsaw_"+tier)));
    }
    @Override public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (rounds(stack)<=0) return false;
        if (!level.isClientSide()) {
            // The source charges one unit even for a block outside the axe tool class.
            stack.set(TGContent.ROUNDS.get(),Math.max(0,rounds(stack)-1));
            level.playSound(null,pos,TGContent.CHAINSAW_HIT.get(),SoundSource.PLAYERS,.65f,1);
        }
        return true;
    }
    @Override public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_ID,ChainsawRules.meleeModifier(rounds(stack)),AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_ID,-2.4000000953674316,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND).build();
    }
    @Override public void hurtEnemy(ItemStack stack, LivingEntity victim, LivingEntity attacker) {
        if (!attacker.level().isClientSide()) stack.set(TGContent.ROUNDS.get(),ChainsawRules.afterUse(rounds(stack),attacker instanceof Player p && p.getAbilities().instabuild));
    }
    @Override public DamageSource getItemDamageSource(LivingEntity attacker) {
        return new MeleeDamage(attacker,rounds(attacker.getMainHandItem())>0?(float)definition().penetration():0);
    }
    public static final class MeleeDamage extends DamageSource {
        private final float penetration;
        private MeleeDamage(LivingEntity attacker,float penetration) {
            super(attacker.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE),attacker);
            this.penetration=penetration;
        }
        public float penetration() { return penetration; }
    }
    @Override public boolean canPerformAction(ItemInstance stack, ItemAbility ability) {
        return ability == ItemAbilities.SWORD_SWEEP && stack instanceof ItemStack item && rounds(item)>0;
    }
}
