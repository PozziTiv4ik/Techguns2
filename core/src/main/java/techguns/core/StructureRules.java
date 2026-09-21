package techguns.core;

import java.util.function.IntPredicate;

/** WorldGenTGStructureSpawn, BlockUtils.getCaveHeight and WorldgenStructure.rotatePoint. */
public final class StructureRules {
    public static final int SMALL=16, MEDIUM=32, BIG=64, MIN_Y=20, MAX_Y=100, AIR_HEIGHT=10;
    public static boolean smallSite(int x,int z,int small,int medium,int big) {
        if(small<1 || medium<1 || big<1) throw new IllegalArgumentException("Positive grid sizes required");
        return x%small==0 && z%small==0 && !(x%big==0 && z%big==0) && !(x%medium==0 && z%medium==0);
    }
    public static int smallNetherTotal(boolean oreClusters) { return oreClusters?50:40; }
    public static boolean altarSelected(int roll,boolean oreClusters) {
        return smallNetherCandidate(roll,oreClusters)==0;
    }
    public static int smallNetherCandidate(int roll,boolean oreClusters) {
        if(roll<0 || roll>=smallNetherTotal(oreClusters)) throw new IllegalArgumentException("Invalid structure roll");
        return roll/10; // Altar, soul platform, loot, acid hole, conditional ore cluster; no reweighting.
    }
    /** MultiMBlock rolls nextInt(totalWeight+1), then uses <= cumulative weight: [1,1] means 2/3 acid. */
    public static boolean acidMixture(int roll) {
        if(roll<0 || roll>2) throw new IllegalArgumentException("Invalid original mixture roll");
        return roll<=1;
    }
    /** Original MultiMBlock [50,50]: rolls 0..50 select the cluster, 51..100 its alternative. */
    public static boolean clusterMixture(int roll) {
        if(roll<0 || roll>100) throw new IllegalArgumentException("Invalid original cluster mixture roll");
        return roll<=50;
    }
    public static int airFloor(IntPredicate air) {
        int count=0;
        for(int y=MAX_Y;y>MIN_Y;y--) {
            if(air.test(y)) count++;
            else { if(count>=AIR_HEIGHT) return y; count=0; }
        }
        return -1;
    }
    public static int caveHeight(int... corners) {
        if(corners.length!=4) throw new IllegalArgumentException("Four corners required");
        int min=MAX_Y,max=MIN_Y,sum=0;
        for(int y:corners) { if(y<MIN_Y || y>MAX_Y) return -1; min=Math.min(min,y); max=Math.max(max,y); sum+=y; }
        return max-min>AIR_HEIGHT?-1:(int)Math.round(sum/4.0);
    }
    public static int[] rotate(int x,int z,int turns,int centerX,int centerZ) {
        if(turns<0 || turns>3) throw new IllegalArgumentException("Quarter turn out of range");
        int a=x-centerX,b=z-centerZ;
        for(int i=0;i<turns;i++) { int previous=a; a=b; b=-previous; }
        return new int[]{a+centerX,b+centerZ};
    }
    /** Source samples corners using size=11, while real template cells end at 10. Keep its one-block shift. */
    public static int[] altarOriginShift(int turns) {
        return originShift(turns,11,11);
    }
    public static int[] originShift(int turns,int sizeX,int sizeZ) {
        int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        for(int x:new int[]{0,sizeX}) for(int z:new int[]{0,sizeZ}) { var p=rotate(x,z,turns,sizeX/2,sizeZ/2); minX=Math.min(minX,p[0]); minZ=Math.min(minZ,p[1]); }
        return new int[]{minX,minZ};
    }
    private StructureRules() {}
}
