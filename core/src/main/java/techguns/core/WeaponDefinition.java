package techguns.core;

public record WeaponDefinition(WeaponSpec stats, AmmoSpec ammo, ProjectileKind projectile, boolean automatic,
                               int extraPellets, double pelletSpread, double gravity,
                               double penetration, AimSpec aim, String fireSound, String reloadSound) {
    public WeaponDefinition {
        if (stats == null || ammo == null || projectile == null || extraPellets < 0 || extraPellets > 32
                || !Double.isFinite(pelletSpread) || pelletSpread < 0
                || !Double.isFinite(gravity) || gravity < 0 || !Double.isFinite(penetration) || penetration < 0
                || aim == null
                || fireSound == null || reloadSound == null)
            throw new IllegalArgumentException("Invalid weapon definition");
    }
    public String id() { return stats.id(); }
    public int projectileCount() { return extraPellets + 1; }
}
