package techguns.core;

import java.util.List;

/** Generated from original AmmoTypes and GenericProjectileIncendiary.Factory. */
public final class IncendiaryAmmo {
    public record Family(String normal, String item, String empty, String loose, int bundles) {}
    public static final float DAMAGE_MULTIPLIER = 1.1f;
    public static final int BURN_SECONDS = 3;
    public static final float IGNITION_DIVISOR = 40.0f;
    public static final List<Family> FAMILIES = List.of(
        new Family("pistolrounds", "pistolrounds_incendiary", "", "", 0),
        new Family("riflerounds", "riflerounds_incendiary", "", "", 0),
        new Family("shotgunrounds", "shotgunrounds_incendiary", "", "", 0),
        new Family("smgmagazine", "smgmagazine_incendiary", "smgmagazineempty", "pistolrounds_incendiary", 2),
        new Family("assaultriflemagazine", "assaultriflemagazine_incendiary", "assaultriflemagazineempty", "riflerounds_incendiary", 3),
        new Family("pistolmagazine", "pistolmagazine_incendiary", "pistolmagazineempty", "pistolrounds_incendiary", 3),
        new Family("lmgmagazine", "lmgmagazine_incendiary", "lmgmagazineempty", "riflerounds_incendiary", 8),
        new Family("as50magazine", "as50magazine_incendiary", "as50magazineempty", "sniperrounds_incendiary", 2)
    );
    public static boolean supported(WeaponDefinition gun) {
        return gun.projectile() == ProjectileKind.BALLISTIC && FAMILIES.stream().anyMatch(f -> f.normal().equals(gun.ammo().item()));
    }
    public static AmmoSpec ammo(WeaponDefinition gun, BallisticVariant variant) {
        if (variant == BallisticVariant.DEFAULT) return gun.ammo();
        if (!supported(gun)) throw new IllegalArgumentException("Weapon has no incendiary variant");
        var family = FAMILIES.stream().filter(f -> f.normal().equals(gun.ammo().item())).findFirst().orElseThrow();
        return new AmmoSpec(family.item(), family.empty(), family.loose(), family.bundles(), gun.ammo().individual());
    }
    public static float damageAt(WeaponSpec gun, double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid distance");
        float max = gun.damage() * DAMAGE_MULTIPLIER, min = gun.minimumDamage() * DAMAGE_MULTIPLIER;
        if (gun.dropEnd() == 0 || distance <= gun.dropStart()) return max;
        if (distance > gun.dropEnd()) return min;
        float factor = 1f - (float)((distance - gun.dropStart()) / (gun.dropEnd() - gun.dropStart()));
        return min + (max - min) * factor;
    }
    public static boolean ignites(WeaponSpec gun, double roll) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) throw new IllegalArgumentException("Invalid ignition roll");
        return roll <= gun.damage() * DAMAGE_MULTIPLIER / IGNITION_DIVISOR;
    }
    private IncendiaryAmmo() {}
}
