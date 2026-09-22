package techguns.core;

/** The original five-ticket OreClusterMeteorBasis entry shares the medium LAND table with OreClusterSpike. */
public final class MeteorRules {
    public static boolean selected(int roll,boolean sandy,boolean oil) {
        int total=SpikeRules.total(sandy,oil);
        if(roll<0 || roll>=total) throw new IllegalArgumentException("Medium location roll outside total");
        int first=sandy?50:30;
        return roll>=first && roll<first+5;
    }
    public static int type(int roll) { return SpikeRules.inclusive(roll,new int[]{5,5,5,5,5,5,5,15}); }
    public static int typeOre(int roll,int[] weights) { return SpikeRules.inclusive(roll,weights); }
    public static int surface(int[] heights) {
        if(heights.length!=25) throw new IllegalArgumentException("17x17 area must sample 25 terrain columns");
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE,sum=0;
        for(int h:heights) { if(h==Integer.MIN_VALUE) return Integer.MIN_VALUE; min=Math.min(min,h); max=Math.max(max,h); sum+=h; }
        return max-min>3?Integer.MIN_VALUE:Math.floorDiv(sum,25)-1;
    }
    private MeteorRules() {}
}
