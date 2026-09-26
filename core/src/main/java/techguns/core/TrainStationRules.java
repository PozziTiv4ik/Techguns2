package techguns.core;

/** MultiMBlock's inclusive weighted roll; its first choice receives the extra zero ticket. */
public final class TrainStationRules {
    public static int bound(int... weights) {
        if(weights.length==0) throw new IllegalArgumentException("Empty source variants");
        int total=1;
        for(int weight:weights) { if(weight<1) throw new IllegalArgumentException("Positive source weights required"); total=Math.addExact(total,weight); }
        return total;
    }
    public static int choice(int roll,int... weights) {
        if(roll<0 || roll>=bound(weights)) throw new IllegalArgumentException("Invalid MultiMBlock roll");
        int sum=0;
        for(int i=0;i<weights.length;i++) { sum+=weights[i]; if(roll<=sum) return i; }
        throw new IllegalArgumentException("No source variant for roll");
    }
    private TrainStationRules() {}
}
