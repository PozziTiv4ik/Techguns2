package techguns.modern;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import techguns.core.ArmorMath;

/** Replace the armor reduction stage for Techguns bullets, leaving vanilla's other damage stages intact. */
public final class ArmorDamage {
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (event.getSource().is(techguns.modern.fluid.TGLiquidBlock.ACID_DAMAGE)) {
            // Default armor contributes zero against legacy poison. Specialized TG protection is a separate port stage.
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container,previousReduction) -> 0);
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof Bullet bullet) || !event.getSource().is(Bullet.DAMAGE_TYPE)) return;
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, previousReduction) -> {
            float damage = container.getNewDamage();
            float armor = (float) event.getEntity().getAttributeValue(Attributes.ARMOR);
            float toughness = (float) event.getEntity().getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            return damage - ArmorMath.afterArmor(damage, armor, toughness, (float) bullet.weapon().penetration());
        });
    }
    private ArmorDamage() {}
}
