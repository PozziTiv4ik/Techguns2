package techguns.core;

/** Generated from ModelMinigun and GenericGun: full rotation per non-resetting recoil cycle. */
public final class MinigunAnimation {
    public static final int SPIN_TICKS = 5;
    public static final double PIVOT_Y = -0.0078125;
    public static final double PIVOT_Z = 0.06194478271980103;
    public static boolean startsSpin(long last, long now) { return last < 0 || now < last || now - last >= SPIN_TICKS; }
    public static float degrees(long start, long now, float partial) {
        double elapsed = (double) now - start + partial;
        // Mesh conversion reflects Y, reversing the source X rotation.
        return start < 0 || elapsed < 0 || elapsed >= SPIN_TICKS || !Float.isFinite(partial) ? 0
                : (float) (-360 * elapsed / SPIN_TICKS);
    }
    private MinigunAnimation() {}
}
