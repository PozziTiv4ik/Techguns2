package techguns.core;

/** Generated from AlienBlasterProjectile, GenericProjectile and the registered Ghastling factory. */
public final class AlienBlasterRules {
    public static final int IGNITE_SECONDS = 3;
    public static final double BLOCK_IGNITE_CHANCE = 0.35;
    public static final float AIR_DRAG = 0.99f, WATER_DRAG = 0.85f;
    public static final float GHASTLING_DAMAGE = 6.0f;
    public static final double GHASTLING_SPEED = 1.5, GHASTLING_SPREAD = 0.05;
    public static final int GHASTLING_LIFETIME = 200;
    public static boolean ignites(double roll) { return roll >= 0 && roll <= BLOCK_IGNITE_CHANCE; }
    private AlienBlasterRules() {}
}
