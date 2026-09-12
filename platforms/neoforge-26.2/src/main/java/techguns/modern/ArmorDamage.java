package techguns.modern;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import techguns.core.ArmorMath;
import techguns.core.DamageKind;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

/** Preserve Techguns armor categories while keeping Minecraft's other damage stages. */
public final class ArmorDamage {
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) techguns.modern.armor.TGArmorSystem.refresh(player);
        var npc = event.getEntity() instanceof techguns.modern.npc.ArmedNpc armed ? armed : null;
        techguns.core.WeaponDefinition weapon = null;
        if (event.getSource().getDirectEntity() instanceof Bullet bullet && event.getSource().is(Bullet.DAMAGE_TYPE)) {
            weapon = bullet.weapon();
        } else if (event.getSource().getDirectEntity() instanceof LaserBeam beam && event.getSource().is(LaserBeam.DAMAGE_TYPE)) {
            weapon = beam.weapon();
        } else if (event.getSource().getDirectEntity() instanceof RocketProjectile rocket && event.getSource().is(RocketDamage.TYPE)) {
            weapon = rocket.weapon();
        } else if (event.getSource().getDirectEntity() instanceof NetherBlasterProjectile blast && event.getSource().is(NetherBlasterProjectile.DAMAGE_TYPE)) {
            weapon = blast.weapon();
        }
        DamageKind kind = weapon != null ? weapon.projectile().damageKind() : sourceKind(event.getSource());
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player && techguns.modern.armor.TGArmorSystem.hasArmor(player)) {
            // Legacy TG radiation is magic, not unblockable. Its modern tag must not suppress typed armor;
            // the reduction callback still runs at the accepted armor stage, after attack cancellation.
            if (!event.getSource().is(DamageTypeTags.BYPASSES_ARMOR) || event.getSource().is(techguns.modern.radiation.RadiationSystem.DAMAGE)) {
                float rawPenetration=weapon==null?0:(float)weapon.penetration();
                event.addReductionModifier(DamageContainer.Reduction.ARMOR,(container,previousReduction) -> {
                    if(!techguns.modern.armor.TGArmorSystem.hasArmor(player)) return previousReduction;
                    float damage=container.getNewDamage();
                    return damage-techguns.modern.armor.TGArmorSystem.absorb(player,event.getSource(),damage,kind,rawPenetration);
                });
            }
            return;
        }
        if (npc == null && weapon == null && !event.getSource().is(techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE)) return;
        float penetration = weapon == null ? 0 : (float) weapon.penetration();
        float armor = npc != null ? npc.armorAgainst(kind)
                : ArmorMath.defaultArmor(kind, (float) event.getEntity().getAttributeValue(Attributes.ARMOR), event.getEntity().fireImmune());
        float toughness = (float) event.getEntity().getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if (npc != null && event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) {
            // This modern tag encodes zero ordinary radiation armor; legacy TG radiation still used NPC typed armor.
            // Preserve actual vanilla armor-bypassing sources (magic, burning, fall, etc.).
            if (event.getSource().is(techguns.modern.radiation.RadiationSystem.DAMAGE))
                event.setAmount(ArmorMath.afterArmor(event.getAmount(), armor, toughness, penetration));
            return;
        }
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, previousReduction) -> {
            float damage = container.getNewDamage();
            return damage - ArmorMath.afterArmor(damage, armor, toughness, penetration);
        });
    }
    private static DamageKind sourceKind(DamageSource source) {
        if (source.is(NetherBlasterProjectile.DAMAGE_TYPE)) return DamageKind.FIRE;
        if (source.is(techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE)) return DamageKind.POISON;
        if (source.is(techguns.modern.radiation.RadiationSystem.DAMAGE)) return DamageKind.RADIATION;
        if (source.is(techguns.modern.radiation.RadiationSystem.POISONING)) return DamageKind.UNRESISTABLE;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return DamageKind.EXPLOSION;
        if (source.is(net.neoforged.neoforge.common.Tags.DamageTypes.IS_MAGIC)) return DamageKind.ENERGY;
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.DRAGON_BREATH)) return DamageKind.FIRE;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return DamageKind.PROJECTILE;
        if (source.is(DamageTypes.WITHER)) return DamageKind.POISON;
        if (source.is(DamageTypes.LIGHTNING_BOLT)) return DamageKind.LIGHTNING;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.FALL) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.STARVE)) return DamageKind.UNRESISTABLE;
        return DamageKind.PHYSICAL;
    }
    private ArmorDamage() {}
}
