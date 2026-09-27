package techguns.modern;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import techguns.core.*;

/** Flamethrower factory forwards gravity; wet removal follows collision, as in GenericProjectile. */
public final class FlameProjectile extends BurningProjectile {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("flame"));
    public FlameProjectile(EntityType<? extends FlameProjectile> type, Level level) { super(type, level, Weapons.definition("flamethrower")); }
    @Override public ResourceKey<DamageType> damageType() { return DAMAGE_TYPE; }
    @Override protected boolean supports(WeaponDefinition gun) { return gun.projectile() == ProjectileKind.FLAME; }
    @Override protected float damageAt(double distance) { return weapon().stats().damageAt(distance); }
    @Override protected boolean ignites(double roll) { return FlameRules.ignites(roll); }
    @Override protected int burnSeconds() { return FlameRules.BURN_SECONDS; }
    @Override public boolean fireTrail() { return true; }
    @Override protected double flightGravity() { return weapon().gravity(); }
    @Override protected boolean extinguishesWhenWet() { return true; }
}
