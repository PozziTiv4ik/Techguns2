package techguns.core;

public final class PigmanRules {
    public static String weapon(int roll) {
        if (roll < 0 || roll >= 9) throw new IllegalArgumentException("Expected source nextInt(9) roll");
        return roll < 3 ? "thompson" : roll < 5 ? "revolver" : roll < 7 ? "ak47" : "pistol";
    }
    /** HEAD is unconditional in the source; each other piece has an independent <= .5 draw. */
    public static boolean armor(ArmorSlot slot, double roll) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) throw new IllegalArgumentException("Expected unit random draw");
        return slot == ArmorSlot.HEAD || roll <= .5;
    }
    private PigmanRules() {}
}
