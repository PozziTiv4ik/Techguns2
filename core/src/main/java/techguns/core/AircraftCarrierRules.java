package techguns.core;

import java.util.Arrays;
import java.util.function.IntBinaryOperator;

/** Original unrotated water sampling and inclusive MultiMBlock supply rolls. */
public final class AircraftCarrierRules {
    public static int surface(IntBinaryOperator liquidHeight) {
        int[] h={liquidHeight.applyAsInt(0,0),liquidHeight.applyAsInt(54,0),liquidHeight.applyAsInt(0,21),liquidHeight.applyAsInt(54,21)};
        for(int v:h) if(v<0||Math.abs(v-h[0])>=2) return -1;
        Arrays.sort(h); return (h[1]+h[2])/2;
    }
    public static int[] rotated(int x,int z,int turns) {
        if(turns<0||turns>3) throw new IllegalArgumentException("Carrier direction");
        int dx=x-27,dz=z-10;
        for(int i=0;i<turns;i++) { int previous=dx;dx=dz;dz=-previous; }
        return new int[]{27+dx,10+dz};
    }
    /** Metadata 0 has two tickets. -1 means air in SUPPLY_CRATES_CHANCE. */
    public static int supply(int roll,boolean chance) {
        if(roll<0||roll>=(chance?19:10)) throw new IllegalArgumentException("Carrier supply roll");
        return roll>=10?-1:Math.max(0,roll-1);
    }
    private AircraftCarrierRules() {}
}
