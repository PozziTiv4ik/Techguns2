package techguns.core;

/** Implemented projectile families; unsupported factories must not silently become bullets. */
public enum ProjectileKind {
    BALLISTIC(DamageKind.PROJECTILE), LASER(DamageKind.ENERGY);

    private final DamageKind damageKind;
    ProjectileKind(DamageKind damageKind) { this.damageKind = damageKind; }
    public DamageKind damageKind() { return damageKind; }
}
