package techguns.core;

public record WeaponDefinition(WeaponSpec stats, AmmoSpec ammo, boolean automatic,
                               int extraPellets, double pelletSpread, double gravity,
                               double penetration, float zoom, String fireSound, String reloadSound) {
    public WeaponDefinition {
        if (stats == null || ammo == null || extraPellets < 0 || extraPellets > 32
                || !Double.isFinite(pelletSpread) || pelletSpread < 0
                || !Double.isFinite(gravity) || gravity < 0 || !Double.isFinite(penetration) || penetration < 0
                || !Float.isFinite(zoom) || zoom <= 0 || zoom > 1
                || fireSound == null || reloadSound == null)
            throw new IllegalArgumentException("Invalid weapon definition");
    }
    public String id() { return stats.id(); }
    public int projectileCount() { return extraPellets + 1; }
}
