package techguns.core;

/** PoliceStation's original ten medium LAND tickets and 4-by-4 surface samples. */
public final class PoliceStationRules {
    public static boolean selected(int roll,boolean sandy,boolean clusters,boolean oil) {
        if(roll<0 || roll>=BugNestLayout.total(sandy,clusters,oil)) throw new IllegalArgumentException("Medium ticket outside total");
        int first=sandy?20:0; return roll>=first && roll<first+10;
    }
    public static int surface(int[] heights) {
        if(heights.length!=16) throw new IllegalArgumentException("Sixteen source height samples required");
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE; long sum=0;
        for(int height:heights) { if(height==Integer.MIN_VALUE) return Integer.MIN_VALUE; min=Math.min(min,height); max=Math.max(max,height); sum+=height; }
        return (long)max-min>3?Integer.MIN_VALUE:(int)Math.floorDiv(sum,16)-1;
    }
    private PoliceStationRules() {}
}
