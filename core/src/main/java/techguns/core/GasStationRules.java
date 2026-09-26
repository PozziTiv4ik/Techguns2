package techguns.core;

/** Original four small LAND candidates; the mine remains present with ore clusters disabled. */
public final class GasStationRules {
    public static final int TOTAL=40;
    public static boolean selected(int roll) {
        if(roll<0 || roll>=TOTAL) throw new IllegalArgumentException("Small Overworld ticket outside total");
        return roll>=30;
    }
    public static int surface(int[] heights) {
        if(heights.length!=12) throw new IllegalArgumentException("Twelve source height samples required");
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE; long sum=0;
        for(int height:heights) { if(height==Integer.MIN_VALUE) return Integer.MIN_VALUE; min=Math.min(min,height); max=Math.max(max,height); sum+=height; }
        return (long)max-min>3?Integer.MIN_VALUE:(int)Math.floorDiv(sum,12)-1;
    }
    private GasStationRules() {}
}
