package techguns.core;

import java.util.*;

/** Original OreDrillDefinition and OreDrillTileEntMaster math, independent of world coordinates. */
public final class OreDrillRules {
    public record Size(int engines,int rods,int radius) {
        public Size { if(!valid(engines,rods,radius)) throw new IllegalArgumentException("Invalid original drill dimensions"); }
        public int length() { return engines+rods; }
        public int miningRadius() { return engines==0?0:radius+1; }
        public int headSize() { return miningRadius()==0?0:miningRadius()<3?1:2; }
    }
    public record Cell(int axial,int side1,int side2,String kind) {}
    public record Rate(int ticks,int power,double perHour) {}
    public static boolean valid(int engines,int rods,int radius) {
        if(radius<0 || radius>3 || engines<0 || rods<1 || engines+rods>16) return false;
        return engines==0 ? rods==1 && radius==0 : rods>=radius*2+1 && rods>=engines && engines>radius;
    }
    public static List<Cell> parts(Size s) {
        var cells=new ArrayList<Cell>(); cells.add(new Cell(0,0,0,"controller"));
        for(int a=s.engines()+1;a<=s.length();a++) cells.add(new Cell(a,0,0,"rod"));
        if(s.engines()==0) return List.copyOf(cells);
        int r=s.radius(),outer=r+1;
        for(int a=1;a<=s.engines();a++) for(int x=-r;x<=r;x++) for(int z=-r;z<=r;z++) cells.add(new Cell(a,x,z,"engine"));
        for(int a=1;a<=s.length();a++) for(int x=-outer;x<=outer;x++) for(int z=-outer;z<=outer;z++) {
            int edges=(Math.abs(x)==outer?1:0)+(Math.abs(z)==outer?1:0);
            if(edges+((a==1 || a==s.length())?1:0)>=2) cells.add(new Cell(a,x,z,"frame"));
            else if(a>1 && a<s.length() && edges==1) cells.add(new Cell(a,x,z,"scaffold"));
        }
        return List.copyOf(cells);
    }
    public static List<Cell> air(Size s) {
        var cells=new ArrayList<Cell>();
        for(int a=s.engines()+1;a<s.length();a++) for(int x=-s.radius();x<=s.radius();x++) for(int z=-s.radius();z<=s.radius();z++)
            if(x!=0 || z!=0) cells.add(new Cell(a,x,z,"air"));
        return List.copyOf(cells);
    }
    /** The source does not require this final row; existing scaffold blocks are linked and hidden. */
    public static List<Cell> endCap(Size s) {
        var cells=new ArrayList<Cell>(); if(s.engines()==0) return cells;
        for(int x=-s.radius();x<=s.radius();x++) for(int z=-s.radius();z<=s.radius();z++) if(x!=0 || z!=0) cells.add(new Cell(s.length(),x,z,"scaffold"));
        return List.copyOf(cells);
    }
    public static Rate rate(Size s,int connected,int headLevel,int clusterLevel,double clusterOres,double clusterPower,double globalOres,double globalPower) {
        int effective=headLevel+s.miningRadius()-clusterLevel;
        if(effective<0) return new Rate(400,(int)(24*(float)globalPower),180);
        int count=Math.min(connected,s.length());
        // TGConfig read Float values, including the cluster multipliers stored in double fields.
        double perHour=(count*3+count*effective*.5)*(float)globalOres*(double)(float)clusterOres;
        return new Rate((int)(72000/perHour),(int)(8*perHour*(1+Math.max(s.miningRadius()-1,0)*.2)*(double)(float)clusterPower*(float)globalPower),perHour);
    }
    /** Four source slices per rod, alternating 45-degree rotation, logarithmic taper. */
    public static float headHalfWidth(int rods,int miningRadius,int slice) {
        float factor=switch(miningRadius) { case 0,1->.35f; case 2->.5f; case 3->.55f; default->.60f; };
        return Math.max(1,miningRadius)*(float)Math.log(rods*4-slice+1f)*(1f/(float)Math.log(rods*4+1f))*factor;
    }
    private OreDrillRules() {}
}
