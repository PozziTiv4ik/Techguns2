package techguns.core;

/** Implemented projectile families; unsupported factories must not silently become bullets. */
public enum ProjectileKind {
    BALLISTIC(DamageKind.PROJECTILE), LASER(DamageKind.ENERGY), ROCKET(DamageKind.EXPLOSION), GRENADE_40MM(DamageKind.EXPLOSION), NETHER_BLASTER(DamageKind.FIRE), CHAINSAW(DamageKind.PHYSICAL);

    private final DamageKind damageKind;
    ProjectileKind(DamageKind damageKind) { this.damageKind = damageKind; }
    public DamageKind damageKind() { return damageKind; }
}
