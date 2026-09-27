package techguns.core.castle;

import java.util.*;
import java.util.function.IntBinaryOperator;

public final class CastleLayout {
    public record Site(int offsetX,int offsetZ,int sampledWidth,int sampledDepth,int surface) {}
    public record Result(List<CastleMaze.Node> nodes,List<Integer> attempts,CastlePos entrance,CastleFacing facing) {}
    public static boolean selected(int ticket) {
        if(ticket<0||ticket>1) throw new IllegalArgumentException("Two LAND tickets");
        return ticket==1;
    }
    public static Optional<Site> surface(int width,int depth,int direction,IntBinaryOperator heights) {
        if(width<32||width>47||depth<32||depth>47||direction<0||direction>3) throw new IllegalArgumentException("Castle size/direction");
        int cx=width/2,cz=depth/2,minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        for(int px:new int[]{0,width}) for(int pz:new int[]{0,depth}) {
            int a=px-cx,b=pz-cz;
            for(int r=0;r<direction;r++) { int old=a; a=b; b=-old; }
            minX=Math.min(minX,a+cx); minZ=Math.min(minZ,b+cz);
        }
        int sx=direction%2==0?width:depth,sz=direction%2==0?depth:width;
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE,sum=0,count=0;
        for(int x=0;x<=sx;x+=4) for(int z=0;z<=sz;z+=4) {
            int h=heights.applyAsInt(minX+x,minZ+z); if(h<0) return Optional.empty();
            min=Math.min(min,h); max=Math.max(max,h); sum+=h; count++;
        }
        return max-min>10?Optional.empty():Optional.of(new Site(minX,minZ,sx,sz,sum/count));
    }
    public static Result generate(CastleWorld world,int x,int y,int z,int width,int height,int depth) {
        if(width<32||width>47||depth<32||depth>47||height<24||height>39) throw new IllegalArgumentException("Castle dimensions");
        CastleMaze best=null; var attempts=new ArrayList<Integer>();
        for(int i=0;i<5;i++) {
            var path=new CastleMaze(width/5,height/5,depth/5,world.rand); CastlePreset.INSTANCE.init(path); path.generatePath();
            attempts.add(path.getNumSegments());
            if(best==null||path.getNumSegments()>best.getNumSegments()) best=path;
        }
        best.generateDungeon(world,x,y,z,CastlePreset.INSTANCE);
        best.generateNPCSpawners(world,x,y,z,CastlePreset.INSTANCE);
        return new Result(best.nodes(),List.copyOf(attempts),best.getStartPos(),best.getEntranceRotation());
    }
    private CastleLayout() {}
}
