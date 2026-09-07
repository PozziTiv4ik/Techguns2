package techguns.core;

/** Loader-independent weapon parameters. Distances are blocks, times are game ticks. */
public record WeaponSpec(String id, int capacity, int fireDelay, int reloadTicks,
                         float damage, float minimumDamage, double dropStart, double dropEnd,
                         double projectileSpeed, int projectileLifetime, double spread) {
    public WeaponSpec {
        if (id == null || !id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid weapon id");
        if (capacity < 1 || fireDelay < 1 || reloadTicks < 1 || projectileLifetime < 1)
            throw new IllegalArgumentException("Invalid weapon timing or capacity");
        if (!Float.isFinite(damage) || !Float.isFinite(minimumDamage) || damage <= 0
                || minimumDamage < 0 || minimumDamage > damage)
            throw new IllegalArgumentException("Invalid damage");
        if (!Double.isFinite(dropStart) || !Double.isFinite(dropEnd) || dropStart < 0 || dropEnd <= dropStart
                || !Double.isFinite(projectileSpeed) || projectileSpeed <= 0
                || !Double.isFinite(spread) || spread < 0)
            throw new IllegalArgumentException("Invalid projectile parameters");
    }

    public float damageAt(double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid distance");
        double fraction = Math.clamp((distance - dropStart) / (dropEnd - dropStart), 0.0, 1.0);
        return (float) (damage + (minimumDamage - damage) * fraction);
    }

    public int clampRounds(int rounds) { return Math.clamp(rounds, 0, capacity); }
}
