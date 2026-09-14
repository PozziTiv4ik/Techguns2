package techguns.core;

/** TGuns.chainsaw, Chainsaw and GenericGunMeleeCharge; upgrades affect mining only. */
public final class ChainsawRules {
    public static int head(int value) { return Math.clamp(value, 0, 2); }
    public static int harvestLevel(int head) { return 3 + head(head); }
    public static float digSpeed(int fuel, int head, boolean effective) { return fuel > 0 && effective ? 14 + 3 * head(head) : 1; }
    public static double meleeModifier(int fuel) { return fuel > 0 ? 12 : 2; }
    public static int afterUse(int fuel, boolean creative) { return creative ? Math.max(0,fuel) : Math.max(0,fuel-1); }
    public static boolean canUpgrade(int current, int target) { return current >= 0 && current < 2 && target == current + 1; }
    private ChainsawRules() {}
}
