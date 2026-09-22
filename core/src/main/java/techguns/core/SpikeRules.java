package techguns.core;

/** Original medium LAND ticket table; skipped unported structures still consume their tickets. */
public final class SpikeRules {
    public static int total(boolean sandy,boolean oil) { return 35+(sandy?20+(oil?15:0):0); }
    public static boolean selected(int roll,boolean sandy,boolean oil) {
        if(roll<0 || roll>=total(sandy,oil)) throw new IllegalArgumentException("Medium location roll outside total");
        int first=sandy?40:20; return roll>=first && roll<first+10;
    }
    public static int type(int roll) { return inclusive(roll,new int[]{5,10,10,10,5,2,3}); }
    public static int inclusive(int roll,int[] weights) {
        if(roll<0) throw new IllegalArgumentException("Negative inclusive roll");
        int sum=0; for(int i=0;i<weights.length;i++) { sum+=weights[i]; if(roll<=sum) return i; }
        throw new IllegalArgumentException("Inclusive roll outside total");
    }
    public static int surface(int[] heights) {
        if(heights.length!=9) throw new IllegalArgumentException("Nine surface samples required");
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE,sum=0;
        for(int h:heights) { if(h==Integer.MIN_VALUE) return Integer.MIN_VALUE; min=Math.min(min,h); max=Math.max(max,h); sum+=h; }
        return max-min>3?Integer.MIN_VALUE:Math.floorDiv(sum,9)-1;
    }
    private SpikeRules() {}
}
