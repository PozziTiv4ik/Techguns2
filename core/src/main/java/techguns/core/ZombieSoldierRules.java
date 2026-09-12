package techguns.core;

/** ZombieSoldier.addRandomArmor: four weapons and four independent armor draws. */
public final class ZombieSoldierRules {
    public static String weapon(int roll) {
        return switch (roll) {
            case 0 -> "techguns:revolver";
            case 1 -> "techguns:thompson";
            case 2 -> "minecraft:iron_shovel";
            case 3 -> "minecraft:stone_shovel";
            default -> throw new IllegalArgumentException("Expected source nextInt(4) roll");
        };
    }
    public static boolean armor(double roll) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) throw new IllegalArgumentException("Expected unit random draw");
        return roll <= .5;
    }
    private ZombieSoldierRules() {}
}
