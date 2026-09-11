package techguns.core;

/** Implemented projectile families; unsupported factories must not silently become bullets. */
public enum ProjectileKind {
    BALLISTIC(DamageKind.PROJECTILE), LASER(DamageKind.ENERGY), ROCKET(DamageKind.EXPLOSION), NETHER_BLASTER(DamageKind.FIRE);

    private final DamageKind damageKind;
    ProjectileKind(DamageKind damageKind) { this.damageKind = damageKind; }
    public DamageKind damageKind() { return damageKind; }
}
