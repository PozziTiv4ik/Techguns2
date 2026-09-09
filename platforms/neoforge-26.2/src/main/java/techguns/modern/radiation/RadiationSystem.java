package techguns.modern.radiation;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.*;
import techguns.modern.TGContent;
import techguns.modern.Techguns;

/** Source radiation accumulation, resistance, symptoms and medicine. The original default is disabled. */
public final class RadiationSystem {
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue DISABLED=BUILDER.comment("Original Techguns default. Set false to enable radiation exposure, accumulated poisoning and radioactive inventory items.")
            .define("WIP_disableRadiationSystem",true);
    private static final ModConfigSpec SPEC=BUILDER.build();
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS=DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,Techguns.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>,AttachmentType<Integer>> DOSE=ATTACHMENTS.register("radiation",() -> AttachmentType.builder(() -> 0)
            .serialize(Codec.intRange(0,1000).fieldOf("level")).copyOnDeath().sync((holder,player) -> holder==player,ByteBufCodecs.VAR_INT).build());
    private static final DeferredRegister<Attribute> ATTRIBUTES=DeferredRegister.create(Registries.ATTRIBUTE,Techguns.MOD_ID);
    public static final DeferredHolder<Attribute,Attribute> RESISTANCE=ATTRIBUTES.register("radiation_resistance",() -> new RangedAttribute("attribute.techguns.radiation_resistance",0,0,Float.MAX_VALUE).setSyncable(true));
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,Techguns.MOD_ID);
    public static final DeferredHolder<MobEffect,MobEffect> EXPOSURE=EFFECTS.register("radiation",() -> new TimedEffect(false));
    public static final DeferredHolder<MobEffect,MobEffect> REGENERATION=EFFECTS.register("radregeneration",() -> new TimedEffect(true));
    public static final DeferredHolder<MobEffect,MobEffect> PROTECTION=EFFECTS.register("radresistance",() -> new MobEffect(MobEffectCategory.BENEFICIAL,0x99ac2d) {}
            .addAttributeModifier(RESISTANCE,TGContent.id("rad_resistance_effect"),1,AttributeModifier.Operation.ADD_VALUE));
    public static final ResourceKey<DamageType> DAMAGE=ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("radiation"));
    public static final ResourceKey<DamageType> POISONING=ResourceKey.create(Registries.DAMAGE_TYPE,TGContent.id("radiation_poisoning"));
    public static final DeferredHolder<SoundEvent,SoundEvent> GEIGER_LOW=sound("effects.geiger.low"), GEIGER_HIGH=sound("effects.geiger.high");
    private static DeferredHolder<SoundEvent,SoundEvent> sound(String name) { return TGContent.SOUNDS.register(name,() -> SoundEvent.createVariableRangeEvent(TGContent.id(name))); }
    public static void register(IEventBus bus,ModContainer container) {
        ATTACHMENTS.register(bus); ATTRIBUTES.register(bus); EFFECTS.register(bus);
        bus.addListener(RadiationSystem::attributes); NeoForge.EVENT_BUS.register(RadiationSystem.class);
        container.registerConfig(ModConfig.Type.SERVER,SPEC,"techguns-radiation-server.toml");
    }
    private static void attributes(EntityAttributeModificationEvent event) { for(var type:event.getTypes()) if(!event.has(type,RESISTANCE)) event.add(type,RESISTANCE); }
    public static int dose(Player player) { return player.getData(DOSE); }
    public static void add(Player player,int amount) { if(!DISABLED.get()) player.setData(DOSE,Math.clamp((long)dose(player)+amount,0,1000)); }
    private static DamageSource source(ServerLevel level,ResourceKey<DamageType> type) { return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type)); }
    private static final class TimedEffect extends MobEffect {
        private final boolean regeneration;
        TimedEffect(boolean regeneration) { super(regeneration ? MobEffectCategory.BENEFICIAL : MobEffectCategory.HARMFUL,regeneration ? 0xfa2000 : 0xf8ff00); this.regeneration=regeneration; }
        @Override public boolean shouldApplyEffectTickThisTick(int remaining,int amplifier) { return remaining%20==0; }
        @Override public boolean applyEffectTick(ServerLevel level,LivingEntity entity,int amplifier) {
            if(DISABLED.get()) return true;
            if(regeneration) { if(entity instanceof Player player) add(player,-(amplifier+1)); return true; }
            int amount=Math.clamp(amplifier+1-(int)entity.getAttributeValue(RESISTANCE),0,1000);
            if(entity instanceof Player player) {
                if(!player.isCreative() && !player.isSpectator()) {
                    add(player,amount);
                    if(amount>0 && player instanceof ServerPlayer server && server.connection!=null) server.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                            amplifier>=2 ? GEIGER_HIGH : GEIGER_LOW,SoundSource.PLAYERS,player.getX(),player.getY(),player.getZ(),1,1,level.getRandom().nextLong()));
                }
            } else if(entity instanceof Mob && amount>=2) entity.hurtServer(level,source(level,DAMAGE),amount*.5f);
            return true;
        }
    }
    public static void reactionFailure(ServerLevel level,BlockPos controller,int intensity) {
        if(DISABLED.get() || intensity<=0) return;
        int amplifier=(int)Math.ceil(intensity*.5)-1;
        Vec3 center=Vec3.atCenterOf(controller);
        applyZone(level, center, 6, 62, amplifier, 4, 0);
    }
    public static void applyZone(ServerLevel level, Vec3 center, double radius, int duration, int innerStrength, double innerRadius, int outerStrength) {
        if (DISABLED.get()) return;
        for(var entity:level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius))) {
            double distance=entity.position().distanceTo(center);
            if(distance>=radius) continue;
            int strength=(int)Math.round(techguns.core.ExplosionMath.band(distance, innerRadius, radius, innerStrength, outerStrength));
            entity.addEffect(new MobEffectInstance(EXPOSURE,duration,strength,true,true));
        }
    }
    public static int inventoryStrength(ItemStack item) {
        var id=BuiltInRegistries.ITEM.getKey(item.getItem());
        if(!id.getNamespace().equals(Techguns.MOD_ID)) return 0;
        return switch(id.getPath()) { case "yellowcake", "tacticalnukewarhead", "rocket_nuke" -> 1; case "enricheduranium" -> 3; case "antigravcore", "plasmagenerator" -> 4; default -> 0; };
    }
    public static void tick(Player player) {
        if(DISABLED.get() || !(player.level() instanceof ServerLevel level) || player.isCreative() || player.isSpectator()) return;
        if(level.getGameTime()%60==0) {
            int strength=0;
            for(int slot=0;slot<36;slot++) strength=Math.max(strength,inventoryStrength(player.getInventory().getItem(slot)));
            if(strength>0) player.addEffect(new MobEffectInstance(EXPOSURE,60,strength-1,false,false));
        }
        if(level.getGameTime()%20!=0) return;
        int dose=dose(player);
        if(dose>=500) player.addEffect(new MobEffectInstance(MobEffects.HUNGER,30,dose>=750 ? 1 : 0,false,false));
        if(dose>=750) { player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE,30,1,false,false)); player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,30,1,false,false)); }
        if(dose>=1000) { player.addEffect(new MobEffectInstance(MobEffects.NAUSEA,30,1,false,false)); player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,30,1,false,false)); player.hurtServer(level,source(level,POISONING),2); }
    }
    @SubscribeEvent public static void onTick(PlayerTickEvent.Post event) { tick(event.getEntity()); }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void onClone(PlayerEvent.Clone event) {
        event.getEntity().setData(DOSE,Math.max(0,dose(event.getOriginal())-(event.isWasDeath() && !DISABLED.get() ? 200 : 0)));
    }
    @SubscribeEvent public static void onRemove(MobEffectEvent.Remove event) {
        // 26.2 milk calls removeAllEffects; removal by commands remains available.
        if(event.getEffect().is(EXPOSURE.getKey()) && event.getEntity().isUsingItem() && event.getEntity().getUseItem().is(Items.MILK_BUCKET)) event.setCanceled(true);
    }
    private RadiationSystem() {}
}
