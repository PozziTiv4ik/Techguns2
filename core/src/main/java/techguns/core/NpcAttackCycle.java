package techguns.core;

/** Original EntityAIRangedAttack timer, including retry after an obstructed scheduled shot. */
public final class NpcAttackCycle {
    private final NpcAttackSpec spec;
    private int remaining = -1, burstRemaining, seen;
    public NpcAttackCycle(NpcAttackSpec spec) { this.spec = spec; burstRemaining = spec.burst(); }
    public boolean tick(double distance, boolean visible) {
        seen = visible ? Math.min(20, seen + 1) : 0;
        if (--remaining == 0) {
            if (distance > spec.range() || !visible) return false;
            if (spec.burst() > 0) --burstRemaining;
            if (burstRemaining > 0) remaining = spec.shotDelay();
            else { burstRemaining = spec.burst(); remaining = spec.intervalAt(distance); }
            return true;
        }
        if (remaining < 0) remaining = spec.intervalAt(distance);
        return false;
    }
    public boolean pursue(double distance) { return distance > spec.range() || seen < 20; }
    public void resetTarget() { remaining = -1; seen = 0; }
}
