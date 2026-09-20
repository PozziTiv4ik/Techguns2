package techguns.core;

import java.util.List;

/** TGSpawnerTileEnt differs from the ordinary NPC selector: standard strict weighted choice. */
public final class SpawnerRules {
    public static final int DEFAULT_DELAY=200, DEFAULT_REMAINING=5, DEFAULT_ACTIVE=3, DEFAULT_RANGE=2, HOME_RADIUS=10;
    public static boolean hasRoom(int active,int maximum,int remaining) { return active<Math.min(maximum,remaining); }
    public static int choose(List<Integer> weights,int roll) {
        int total=0;
        for(int weight:weights) { if(weight<=0 || weight>10000) throw new IllegalArgumentException("Invalid spawner weight"); total=Math.addExact(total,weight); }
        if(roll<0 || roll>=total) throw new IllegalArgumentException("Roll outside spawner pool");
        for(int i=0;i<weights.size();i++) { roll-=weights.get(i); if(roll<0) return i; }
        throw new IllegalStateException("Unreachable spawner roll");
    }
    public static double offset(double first,double second,double range) { return (first-second)*range+.5; }
    private SpawnerRules() {}
}
