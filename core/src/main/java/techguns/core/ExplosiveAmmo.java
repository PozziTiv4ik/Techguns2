package techguns.core;

/** Generated from the active GenericProjectileExplosive factory and AmmoTypes. */
public final class ExplosiveAmmo {
    public static final float DAMAGE_MULTIPLIER = 1.15f;
    public static final float EXPLOSION_POWER = 1.5f;
    public static final float DIRECT_KNOCKBACK = 3.0f;
    public static boolean supported(WeaponDefinition gun) {
        return gun.projectile() == ProjectileKind.BALLISTIC && gun.ammo().item().equals("as50magazine");
    }
    public static AmmoSpec ammo(WeaponDefinition gun) {
        if (!supported(gun)) throw new IllegalArgumentException("Weapon has no explosive variant");
        return new AmmoSpec("as50magazine_explosive", "as50magazineempty", "sniperrounds_explosive", 2, false);
    }
    public static float damageAt(WeaponSpec gun, double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid distance");
        float max = gun.damage() * DAMAGE_MULTIPLIER, min = gun.minimumDamage() * DAMAGE_MULTIPLIER;
        if (gun.dropEnd() == 0 || distance <= gun.dropStart()) return max;
        if (distance > gun.dropEnd()) return min;
        float factor = 1f - (float)((distance - gun.dropStart()) / (gun.dropEnd() - gun.dropStart()));
        return min + (max - min) * factor;
    }
    /** Vanilla 1.12 floors the result, including its one-damage fully occluded boundary. */
    public static float blastDamage(double distance, double exposure) {
        if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(exposure) || exposure < 0 || exposure > 1)
            throw new IllegalArgumentException("Invalid blast geometry");
        double radius = EXPLOSION_POWER * 2;
        if (distance > radius) return 0;
        double impact = (1 - distance / radius) * exposure;
        return (int)((impact * impact + impact) / 2 * 7 * radius + 1);
    }
    private ExplosiveAmmo() {}
}
