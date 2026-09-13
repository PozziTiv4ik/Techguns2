package techguns.core;

import java.util.List;

public final class BanditRules {
    private static final List<String> WEAPONS=List.of("pistol","ak47","sawedoff","thompson","revolver","boltaction");
    public static String weapon(int roll) {
        if(roll<0 || roll>=WEAPONS.size()) throw new IllegalArgumentException("Roll outside source bandit weapon table");
        return WEAPONS.get(roll);
    }
    public static boolean helmet(double roll) {
        if(!Double.isFinite(roll) || roll<0 || roll>=1) throw new IllegalArgumentException("Expected unit random draw");
        return roll<=.5;
    }
    private BanditRules() {}
}
