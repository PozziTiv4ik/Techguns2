package techguns.core;

import java.util.List;

public final class SkeletonSoldierRules {
    private static final List<String> WEAPONS=List.of("revolver","thompson","handcannon");
    public static final float BODY_INFLATION=.0625f, HELD_X=-.06f, HELD_Y=-.06f;
    public static String weapon(int roll) {
        if(roll<0 || roll>=WEAPONS.size()) throw new IllegalArgumentException("Roll outside source skeleton weapon table");
        return WEAPONS.get(roll);
    }
    public static boolean scout(double roll) {
        if(!Double.isFinite(roll) || roll<0 || roll>=1) throw new IllegalArgumentException("Expected unit random draw");
        return roll<=.5;
    }
    /** This offset belongs to the held-item renderer, not to projectile origin. */
    public static float heldX(boolean leftHand) { return leftHand?-HELD_X:HELD_X; }
    private SkeletonSoldierRules() {}
}
