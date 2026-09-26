package techguns.core;

/** Source small LAND table and the inclusive, four-block surface sampling used by its locations. */
public final class SmallOverworldRules {
    public static final int TOTAL=40;
    public static int candidate(int roll) {
        if(roll<0 || roll>=TOTAL) throw new IllegalArgumentException("Small Overworld ticket outside total");
        return roll/10; // FactoryHouseSmall, SmallTrainstation, SmallMine, GasStation.
    }
    public static int surface(int expectedSamples,int[] heights) {
        if(expectedSamples<1 || heights.length!=expectedSamples) throw new IllegalArgumentException("Incorrect source height sample count");
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE; long sum=0;
        for(int height:heights) { if(height==Integer.MIN_VALUE) return Integer.MIN_VALUE; min=Math.min(min,height); max=Math.max(max,height); sum+=height; }
        return (long)max-min>3?Integer.MIN_VALUE:(int)Math.floorDiv(sum,expectedSamples)-1;
    }
    private SmallOverworldRules() {}
}
