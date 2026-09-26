package techguns.core;

/** Original four small LAND candidates; the mine remains present with ore clusters disabled. */
public final class GasStationRules {
    public static final int TOTAL=SmallOverworldRules.TOTAL;
    public static boolean selected(int roll) {
        return SmallOverworldRules.candidate(roll)==3;
    }
    public static int surface(int[] heights) {
        return SmallOverworldRules.surface(12,heights);
    }
    private GasStationRules() {}
}
