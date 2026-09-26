package techguns.core;

/** Original medium Nether table and NetherOreClusterCastle's inclusive block mixture. */
public final class NetherCastleRules {
    public static int total(boolean clusters) { return clusters?1020:20; }
    public static int candidate(int roll,boolean clusters) {
        if(roll<0 || roll>=total(clusters)) throw new IllegalArgumentException("Medium Nether roll outside total");
        return roll<10?0:roll<20?1:2; // AltarMedium, GhastSpawner, optional OreClusterCastle.
    }
    public static boolean cluster(int roll) {
        if(roll<0 || roll>100) throw new IllegalArgumentException("Castle mixture roll outside total");
        return roll<=40;
    }
    private NetherCastleRules() {}
}
