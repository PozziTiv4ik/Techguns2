package techguns.core;

/** Original ten LAND tickets, rectangular 4-block terrain samples and camouflage priority. */
public final class SurvivorHideoutRules {
    public static boolean selected(int roll,boolean sandy,boolean clusters,boolean oil) {
        if(roll<0 || roll>=BugNestLayout.total(sandy,clusters,oil)) throw new IllegalArgumentException("Medium ticket outside total");
        int first=sandy?30:10; return roll>=first && roll<first+10;
    }
    public static int surface(int[] heights) {
        if(heights.length!=15) throw new IllegalArgumentException("Fifteen source height samples required");
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE; long sum=0;
        for(int height:heights) { if(height==Integer.MIN_VALUE) return Integer.MIN_VALUE; min=Math.min(min,height); max=Math.max(max,height); sum+=height; }
        return (long)max-min>3?Integer.MIN_VALUE:(int)Math.floorDiv(sum,15)-1;
    }
    public static int canopy(boolean cold,boolean snowy,boolean sandy,boolean beach,boolean mesa) {
        return cold || snowy?2:sandy || beach || mesa?1:0;
    }
    private SurvivorHideoutRules() {}
}
