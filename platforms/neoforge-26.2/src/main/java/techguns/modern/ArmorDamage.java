package techguns.modern;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import techguns.core.ArmorMath;

/** Preserve Techguns armor categories while keeping Minecraft's other damage stages. */
public final class ArmorDamage {
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (event.getSource().is(techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE)) {
            // Default armor contributes zero against legacy poison. Specialized TG protection is a separate port stage.
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container,previousReduction) -> 0);
            return;
        }
        techguns.core.WeaponDefinition weapon;
        if (event.getSource().getDirectEntity() instanceof Bullet bullet && event.getSource().is(Bullet.DAMAGE_TYPE)) {
            weapon = bullet.weapon();
        } else if (event.getSource().getDirectEntity() instanceof LaserBeam beam && event.getSource().is(LaserBeam.DAMAGE_TYPE)) {
            weapon = beam.weapon();
        } else return;
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, previousReduction) -> {
            float damage = container.getNewDamage();
            float armor = ArmorMath.defaultArmor(weapon.projectile().damageKind(), (float) event.getEntity().getAttributeValue(Attributes.ARMOR), false);
            float toughness = (float) event.getEntity().getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            return damage - ArmorMath.afterArmor(damage, armor, toughness, (float) weapon.penetration());
        });
    }
    private ArmorDamage() {}
}
