package techguns.core;

/** Implemented projectile families; unsupported factories must not silently become bullets. */
public enum ProjectileKind {
    BALLISTIC(DamageKind.PROJECTILE), GAUSS(DamageKind.PROJECTILE), BLASTER(DamageKind.ENERGY), LASER(DamageKind.ENERGY), ROCKET(DamageKind.EXPLOSION), GRENADE_40MM(DamageKind.EXPLOSION), NETHER_BLASTER(DamageKind.FIRE), ALIEN_BLASTER(DamageKind.FIRE), FLAME(DamageKind.FIRE), CHAINSAW(DamageKind.PHYSICAL);

    private final DamageKind damageKind;
    ProjectileKind(DamageKind damageKind) { this.damageKind = damageKind; }
    public DamageKind damageKind() { return damageKind; }
}
