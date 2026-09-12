package techguns.core;

/** Source MachineOperationChance arithmetic and GenericArmor's inverse-damage salvage calculation. */
public final class GrinderRules {
    public static final int CAPACITY = 20000, DURATION = 100, BASE_POWER = 5, MAX_BATCH = 8;
    public static int power(int batch) { if (batch < 1 || batch > MAX_BATCH) throw new IllegalArgumentException("Invalid batch"); return BASE_POWER * batch; }
    public static int rolledCount(int base, double factor, int batch, double roll) {
        if (base < 1 || batch < 1 || batch > MAX_BATCH || !Double.isFinite(factor) || factor < 0 || factor > 64
                || !Double.isFinite(roll) || roll < 0 || roll >= 1) throw new IllegalArgumentException("Invalid chance output");
        double amount = base * factor * batch;
        int fixed = Math.max(0, (int)Math.ceil(amount) - 1);
        return fixed + (roll <= amount - fixed ? 1 : 0);
    }
    public static int maximumCount(int base, double factor, int batch) { return Math.max(1, (int)Math.ceil(base * factor * batch)); }
    public static int[] armorSalvage(ArmorSpec armor, int damage) {
        int inverse = armor.durability() - Math.clamp(damage, 0, armor.durability() - 1);
        // Do not clamp inverse to maxDamage - 1: healthy armor really yields an extra part in the original.
        int parts = (int)Math.ceil(armor.repairParts() * (inverse / (float)(armor.durability() - 1)));
        int metal = (int)Math.ceil(parts * (float)armor.repairMetalRatio());
        return new int[]{metal, parts - metal};
    }
    public static float rollerAngle(float progress) { return (45 + 1080 * Math.clamp(progress, 0, 1)) % 360; }
    public static double itemHeight(float progress, boolean gun) { return .6 - Math.clamp(progress, 0, 1) * .4 + (gun ? .15 : 0); }
    private GrinderRules() {}
}
