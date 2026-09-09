package techguns.core;

/** Modifiers from RocketProjectile, RocketProjectileHV and RocketProjectileNuke factories. */
public enum RocketVariant {
    DEFAULT("default", "rocket", 1, 1, 1, 1),
    NUKE("nuke", "rocket_nuke", 5, 5, 1, 1),
    HIGH_VELOCITY("high_velocity", "rocket_high_velocity", 1, .75, 2, .75);

    private final String id, ammo;
    private final double damage, radius, velocity, lifetime;
    RocketVariant(String id, String ammo, double damage, double radius, double velocity, double lifetime) {
        this.id = id; this.ammo = ammo; this.damage = damage; this.radius = radius;
        this.velocity = velocity; this.lifetime = lifetime;
    }
    public String id() { return id; }
    public String ammo() { return ammo; }
    public float damage(WeaponSpec gun) { return (float) (gun.damage() * damage); }
    public float minimumDamage(WeaponSpec gun) { return (float) (gun.minimumDamage() * damage); }
    public double innerRadius(WeaponSpec gun) { return gun.dropStart() * radius; }
    public double outerRadius(WeaponSpec gun) { return gun.dropEnd() * radius; }
    public double speed(WeaponSpec gun) { return gun.projectileSpeed() * velocity; }
    public int lifetime(WeaponSpec gun) { return (int) Math.round(gun.projectileLifetime() * lifetime); }
    public float directDamage(WeaponSpec gun, double distance) {
        if (distance <= innerRadius(gun)) return damage(gun);
        if (distance >= outerRadius(gun)) return minimumDamage(gun);
        return (float) (damage(gun) + (minimumDamage(gun) - damage(gun))
                * (distance - innerRadius(gun)) / (outerRadius(gun) - innerRadius(gun)));
    }
    public float blastDamage(WeaponSpec gun, double distance) {
        return (float) ExplosionMath.band(distance, innerRadius(gun), outerRadius(gun), damage(gun), minimumDamage(gun));
    }
    public static RocketVariant fromId(String id) {
        for (var variant : values()) if (variant.id.equals(id)) return variant;
        throw new IllegalArgumentException("Unknown rocket variant: " + id);
    }
}
