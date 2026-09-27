package techguns.core;

import java.util.Optional;
import java.util.function.IntBinaryOperator;

/** MilitaryBaseStructure retries the same origin, shrinking both axes without rerolling. */
public final class MilitaryCampRules {
    public record Site(int width,int depth,int height) {}
    public static boolean selected(int zeroBasedRoll) {
        if(zeroBasedRoll<0||zeroBasedRoll>=2) throw new IllegalArgumentException("Two big LAND tickets");
        return zeroBasedRoll==0;
    }
    public static Optional<Site> surface(int width,int depth,IntBinaryOperator heights) {
        if(width<32||width>79||depth<32||depth>79) throw new IllegalArgumentException("Source dimensions 32..79");
        while(true) {
            int min=255,max=0,sum=0,count=0; boolean valid=true;
            outer: for(int x=0;x<=width;x+=8) for(int z=0;z<=depth;z+=8) {
                int h=heights.applyAsInt(x,z); if(h<0) { valid=false; break outer; }
                min=Math.min(min,h); max=Math.max(max,h); sum+=h; count++;
            }
            if(valid&&max-min<=5&&sum/count>0) return Optional.of(new Site(width,depth,sum/count));
            if(width<32||depth<32) return Optional.empty();
            width-=16; depth-=16;
        }
    }
    private MilitaryCampRules() {}
}
