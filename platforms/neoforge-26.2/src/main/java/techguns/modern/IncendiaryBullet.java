package techguns.modern;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import techguns.core.*;

/** Original incendiary factory does not forward gravity and keeps flying in water. */
public final class IncendiaryBullet extends BurningProjectile {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, TGContent.id("incendiary"));
    public IncendiaryBullet(EntityType<? extends IncendiaryBullet> type, Level level) { super(type, level, Weapons.definition("revolver")); }
    @Override public ResourceKey<DamageType> damageType() { return DAMAGE_TYPE; }
    @Override protected boolean supports(WeaponDefinition gun) { return IncendiaryAmmo.supported(gun); }
    @Override protected float damageAt(double distance) { return IncendiaryAmmo.damageAt(weapon().stats(), distance); }
    @Override protected boolean ignites(double roll) { return IncendiaryAmmo.ignites(weapon().stats(), roll); }
    @Override protected int burnSeconds() { return IncendiaryAmmo.BURN_SECONDS; }
    @Override public boolean fireTrail() { return weapon().ammo().item().equals("shotgunrounds"); }
}
