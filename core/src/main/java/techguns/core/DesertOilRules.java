package techguns.core;

/** DesertOilCluster's original conditional 15 tickets and 3-by-3 surface samples. */
public final class DesertOilRules {
    public static boolean selected(int roll,boolean sandy,boolean clusters,boolean oil) {
        if(roll<0 || roll>=BugNestLayout.total(sandy,clusters,oil)) throw new IllegalArgumentException("Medium ticket outside total");
        return sandy && clusters && oil && roll>=55;
    }
    public static int surface(int[] heights) {
        if(heights.length!=9) throw new IllegalArgumentException("Nine original surface samples required");
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE; long sum=0;
        for(int h:heights) { if(h==Integer.MIN_VALUE) return Integer.MIN_VALUE; min=Math.min(min,h); max=Math.max(max,h); sum+=h; }
        return (long)max-min>3?Integer.MIN_VALUE:(int)Math.floorDiv(sum,9)-1;
    }
    /** MultiMMBlock rolls 0..2 inclusively for [1,1]: two oil tickets, one sand ticket. */
    public static boolean rimOil(int roll) {
        if(roll<0 || roll>2) throw new IllegalArgumentException("Original inclusive rim roll outside total");
        return roll<=1;
    }
    private DesertOilRules() {}
}
