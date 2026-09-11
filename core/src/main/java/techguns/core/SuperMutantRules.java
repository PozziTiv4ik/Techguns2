package techguns.core;

public final class SuperMutantRules {
    public static String weapon(int roll) {
        return switch (roll) { case 0 -> "rocketlauncher"; case 1 -> "ak47"; case 2 -> "combatshotgun"; case 3, 4 -> "lasergun"; default -> throw new IllegalArgumentException("Expected nextInt(5)"); };
    }
    public static float armor(DamageKind kind) {
        return switch (kind) {
            case PHYSICAL, PROJECTILE -> 7;
            case EXPLOSION, LIGHTNING, ENERGY, FIRE, ICE -> 10;
            case POISON, RADIATION -> 15;
            default -> 0;
        };
    }
    public static float damage(int difficulty) { return difficulty == 1 ? .6f : difficulty == 2 ? .8f : 1; }
    public static double accuracy(int difficulty) { return difficulty == 1 ? 1.3 : difficulty == 2 ? 1.15 : 1; }
    private SuperMutantRules() {}
}
