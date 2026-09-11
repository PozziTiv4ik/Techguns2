package techguns.core;

/** GenericGun.getAIAttack uses a distance-dependent interval and a separate burst delay. */
public record NpcAttackSpec(double range, int interval, int burst, int shotDelay, double forwardOffset) {
    public NpcAttackSpec {
        if (!Double.isFinite(range) || range <= 0 || interval < 3 || burst < 0 || burst > 0 && shotDelay <= 0
                || !Double.isFinite(forwardOffset) || forwardOffset < 0) throw new IllegalArgumentException("Invalid NPC attack parameters");
    }
    public int intervalAt(double distance) { return (int) Math.floor(distance / range * (interval - interval / 3) + interval / 3); }
}
