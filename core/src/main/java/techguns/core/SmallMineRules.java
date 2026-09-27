package techguns.core;

/** SmallMine's shared cluster roll and the original biome priority (cold before sand). */
public final class SmallMineRules {
    public static final int TYPE_BOUND=42;
    public static int type(int roll) { return TrainStationRules.choice(roll,10,10,10,5,2,2,2); }
    public static int cover(boolean cold,boolean snowy,boolean sandy,boolean beach,boolean mesa,boolean nether) {
        return cold || snowy?1:sandy || beach || mesa?2:nether?3:0;
    }
    private SmallMineRules() {}
}
