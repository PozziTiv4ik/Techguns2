package techguns.core;

/** Generated from FlamethrowerProjectile, TGuns and the source sine recoil animation. */
public final class FlameRules {
    public static final int BURN_SECONDS = 3;
    public static final int LOOP_DELAY = 10;
    public static final int RECOIL_TICKS = 10;
    public static final int MUZZLE_TICKS = 10;
    public static final double IGNITION_CHANCE = 0.5;
    public static final float RECOIL_TRANSLATION = 0.025f;
    public static final float RECOIL_DEGREES = 2.5f;
    public static boolean ignites(double roll) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) throw new IllegalArgumentException("Invalid ignition roll");
        return roll <= IGNITION_CHANCE;
    }
    public static boolean startsSound(long last, long now) { return last < 0 || now < last || now - last >= LOOP_DELAY; }
    public static boolean startsRecoil(long last, long now) { return last < 0 || now < last || now - last >= RECOIL_TICKS; }
    public static float recoil(long start, long now, float partial) {
        double elapsed = now - start + partial;
        return start < 0 || elapsed < 0 || elapsed >= RECOIL_TICKS || !Float.isFinite(partial) ? 0
                : (float)Math.sin(2 * Math.PI * elapsed / RECOIL_TICKS);
    }
    private FlameRules() {}
}
