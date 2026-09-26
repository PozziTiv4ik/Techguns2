package techguns.core;

import java.util.*;
import java.util.function.ToIntFunction;

/** Procedural AlienBugNest/BlockUtils geometry from 1.12.2, resolved before chunk placement. */
public final class BugNestLayout {
    public static final int AIR=0,SAND=1,EGGS=2,SANDSTONE=3,SPAWNER=4,NORTH=5,EAST=6,SOUTH=7,WEST=8,TERRAIN=9;
    public record Pos(int x,int y,int z) { public Pos add(int x,int y,int z) { return new Pos(this.x+x,this.y+y,this.z+z); } }
    public record Room(Pos center,int radius,int type) {}
    public record Link(int from,int to) {}
    public record Plan(List<Room> rooms,List<Link> links,Map<Pos,Integer> cells) {
        public Plan { rooms=List.copyOf(rooms); links=List.copyOf(links); cells=Collections.unmodifiableMap(new LinkedHashMap<>(cells)); }
    }
    public static int total(boolean sandy,boolean clusters,boolean oil) { return 20+(sandy?20:0)+(clusters?15+(sandy && oil?15:0):0); }
    public static boolean selected(int roll,boolean sandy,boolean clusters,boolean oil) {
        if(roll<0 || roll>=total(sandy,clusters,oil)) throw new IllegalArgumentException("Medium ticket outside total");
        return sandy && roll<20;
    }
    public static int surface(int[] heights) {
        if(heights.length!=4) throw new IllegalArgumentException("Four source corner heights required");
        for(int h:heights) if(h==Integer.MIN_VALUE || Math.abs((long)h-heights[0])>=256) return Integer.MIN_VALUE;
        var sorted=heights.clone(); Arrays.sort(sorted); return Math.floorDiv(sorted[1]+sorted[2],2);
    }
    public static int wall(int roll,boolean tunnel) {
        if(roll<0 || roll>=(tunnel?14:20)) throw new IllegalArgumentException("Original inclusive palette roll outside total");
        return tunnel?(roll<=12?SAND:SANDSTONE):(roll<=16?SAND:roll==17?EGGS:SANDSTONE);
    }
    private final List<Room> rooms=new ArrayList<>();
    private final List<Link> links=new ArrayList<>();
    private final Map<Pos,Integer> cells=new LinkedHashMap<>();
    private final ToIntFunction<Pos> terrain;
    private final Random decor;
    private BugNestLayout(ToIntFunction<Pos> terrain,long decorSeed) { this.terrain=terrain; decor=new Random(decorSeed); }
    public static Plan create(Pos origin,int width,int depth,long layoutSeed,long decorSeed,ToIntFunction<Pos> terrain) {
        if(width<16 || width>31 || depth<16 || depth>31) throw new IllegalArgumentException("Nest dimensions must be 16..31");
        var nest=new BugNestLayout(terrain,decorSeed); nest.graph(origin,width,depth,new Random(layoutSeed));
        nest.shells(0,new HashSet<>()); nest.connections(0,new HashSet<>()); nest.interiors(0,new HashSet<>());
        return new Plan(nest.rooms,nest.links,nest.cells);
    }
    private int room(int x,int y,int z,int radius,int type) { rooms.add(new Room(new Pos(x,y,z),radius,type)); return rooms.size()-1; }
    private void graph(Pos origin,int width,int depth,Random random) {
        int x=origin.x+width/2,y=origin.y,z=origin.z+depth/2;
        int mainY=y-(10+random.nextInt(7));
        room(x+random.nextInt(3),mainY,z+random.nextInt(3),5,0);
        links.add(new Link(0,room(x,y,z,2,2)));
        float angle=(float)(Math.PI*2/5); int prev=0;
        for(int i=0;i<5;i++) {
            int radius=Math.max(3,5-random.nextInt(i+1)); double distance=5+radius+5.0+random.nextDouble()*8;
            double px=x+distance*Math.cos(angle*i),pz=z+distance*Math.sin(angle*i);
            int sy=mainY+random.nextInt(15)-10,seg=room((int)px,sy,(int)pz,radius,1);
            if(prev==0) links.add(new Link(0,seg));
            else if(random.nextBoolean()) { links.add(new Link(prev,seg)); if(random.nextBoolean()) links.add(new Link(0,seg)); }
            else links.add(new Link(0,seg));
            if(random.nextBoolean()) {
                radius=Math.max(3,5-random.nextInt(i+1)); distance=5+radius+5.0+random.nextDouble()*8;
                double a=angle*i+Math.PI*.5*(random.nextDouble()-.5);
                px+=distance*Math.cos(a); pz+=distance*Math.sin(a); sy=mainY+random.nextInt(15)-10;
                links.add(new Link(seg,room((int)px,sy,(int)pz,radius,1)));
            }
            prev=seg;
        }
    }
    private List<Integer> neighbors(int index) { return links.stream().filter(e->e.from==index).map(Link::to).toList(); }
    private int get(Pos pos) { var cell=cells.get(pos); return cell==null?terrain.applyAsInt(pos):cell; }
    private void put(Pos pos,int value) { cells.put(pos,value); }
    private static boolean solid(int material) { return material==SAND || material==EGGS || material==SANDSTONE || material==TERRAIN; }
    private void shells(int index,Set<Integer> done) {
        var room=rooms.get(index); if(room.type==2 || !done.add(index)) return;
        sphere(room.center,room.radius,true); for(int child:neighbors(index)) shells(child,done);
    }
    private void sphere(Pos center,int radius,boolean shell) {
        for(int x=-radius;x<=radius;x++) for(int y=-radius;y<=radius;y++) for(int z=-radius;z<=radius;z++) {
            int d=x*x+y*y+z*z;
            if(d<radius*radius) put(center.add(x,y,z),!shell || d<(radius-1)*(radius-1)?AIR:wall(decor.nextInt(20),false));
        }
    }
    private record Vec(double x,double y,double z) {
        Vec subtract(Vec p) { return new Vec(x-p.x,y-p.y,z-p.z); }
        double length() { return Math.sqrt(x*x+y*y+z*z); }
        Vec cross(Vec p) { return new Vec(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x); }
    }
    private static Vec vec(Pos p) { return new Vec(p.x,p.y,p.z); }
    private static Vec direction(Pos from,Pos to) { var v=vec(to).subtract(vec(from)); double len=v.length(); return len<1e-4?new Vec(0,0,0):new Vec(v.x/len,v.y/len,v.z/len); }
    private static Pos along(Pos center,Vec dir,double distance,int ox,int oz) { return new Pos((int)(center.x+ox+dir.x*distance),(int)(center.y+dir.y*distance),(int)(center.z+oz+dir.z*distance)); }
    private void connections(int index,Set<Integer> done) {
        if(!done.add(index)) return; var room=rooms.get(index);
        for(int child:neighbors(index)) {
            var next=rooms.get(child); var dir=direction(room.center,next.center);
            double end=vec(next.center).subtract(vec(room.center)).length()-next.radius+(next.type==2?5:0);
            var a=along(room.center,dir,room.radius,0,0); var b=along(room.center,dir,end,0,0);
            int radius=Math.min(room.radius,Math.min(2+decor.nextInt(2),next.radius)); cylinder(a,b,radius);
            if(next.type==2) {
                // Source createSlimeLadder crosses the direction with itself: distance is zero.
                // Keep its whole half-open box and original attachment priority.
                int r=radius-1;
                for(int x=Math.min(a.x,b.x)-r;x<Math.max(a.x,b.x)+r;x++)
                    for(int y=Math.min(a.y,b.y)-r;y<Math.max(a.y,b.y)+r;y++)
                        for(int z=Math.min(a.z,b.z)-r;z<Math.max(a.z,b.z)+r;z++) ladder(new Pos(x,y,z),false);
            }
            connections(child,done);
        }
    }
    private void cylinder(Pos a,Pos b,int radius) {
        var v=vec(a).subtract(vec(b)); double len=v.length();
        for(int x=Math.min(a.x,b.x)-radius;x<Math.max(a.x,b.x)+radius;x++)
            for(int y=Math.min(a.y,b.y)-radius;y<Math.max(a.y,b.y)+radius;y++)
                for(int z=Math.min(a.z,b.z)-radius;z<Math.max(a.z,b.z)+radius;z++) {
                    var p=new Pos(x,y,z); double distance=v.cross(vec(a).subtract(vec(p))).length()/len;
                    if(distance<radius) put(p,distance<radius-1?AIR:wall(decor.nextInt(14),true));
                }
    }
    private void ladder(Pos p,boolean airOnly) {
        if(airOnly && get(p)!=AIR) return;
        // Vanilla 1.12 getStateForPlacement first tries NORTH, then horizontal SOUTH/WEST/NORTH/EAST.
        if(solid(get(p.add(0,0,1)))) put(p,NORTH);
        else if(solid(get(p.add(0,0,-1)))) put(p,SOUTH);
        else if(solid(get(p.add(1,0,0)))) put(p,WEST);
        else if(solid(get(p.add(-1,0,0)))) put(p,EAST);
    }
    private void interiors(int index,Set<Integer> done) {
        var room=rooms.get(index); if(room.type==2 || !done.add(index)) return;
        sphere(room.center,room.radius-1,false); if(room.type==1) eggs(room);
        for(int child:neighbors(index)) {
            var next=rooms.get(child);
            if(room.type==0 && next.type==2) {
                double angle=decor.nextDouble()*2*Math.PI,radius=1+decor.nextDouble()*.66;
                int ox=(int)(radius*Math.cos(angle)),oz=(int)(radius*Math.sin(angle)); var dir=direction(room.center,next.center);
                line(along(room.center,dir,-(room.radius+1),ox,oz),along(room.center,dir,(room.radius-1)*2,ox,oz));
            }
            interiors(child,done);
        }
    }
    private void eggs(Room room) {
        int r=room.radius-2,ri=r>1?r-1:r,spawnerX=decor.nextInt(2*ri)-ri,spawnerZ=decor.nextInt(2*ri)-ri;
        for(int x=-r;x<r;x++) for(int z=-r;z<r;z++) {
            if(x==spawnerX && z==spawnerZ) put(room.center.add(x,-r,z),SPAWNER);
            else {
                float f1=(float)(r-Math.abs(x))/r,f2=(float)(r-Math.abs(z))/r;
                if(decor.nextDouble()<(f1+f2)/2f) for(int y=0;y<room.radius;y++) {
                    var p=room.center.add(x,-(room.radius-1)+y,z); if(get(p)==AIR) put(p,EGGS);
                    if(decor.nextDouble()>(f1+f2)/3f) break;
                }
            }
        }
    }
    private void pillar(Pos p) { put(p,SAND); ladder(p.add(0,0,-1),true); ladder(p.add(1,0,0),true); ladder(p.add(0,0,1),true); ladder(p.add(-1,0,0),true); }
    private void line(Pos from,Pos to) {
        int[] p={from.x,from.y,from.z},end={to.x,to.y,to.z},a=new int[3],sign=new int[3];
        for(int i=0;i<3;i++) { a[i]=Math.abs(end[i]-p[i])*2; sign[i]=Integer.signum(end[i]-p[i]); }
        int dominant=a[0]>=Math.max(a[1],a[2])?0:a[1]>=a[2]?1:2;
        int first=dominant==0?1:0,second=dominant==2?1:2,d1=a[first]-a[dominant]/2,d2=a[second]-a[dominant]/2;
        while(true) {
            pillar(new Pos(p[0],p[1],p[2])); if(p[dominant]==end[dominant]) return;
            if(d1>=0) { p[first]+=sign[first]; if(dominant==1) pillar(new Pos(p[0],p[1],p[2])); d1-=a[dominant]; }
            if(d2>=0) { p[second]+=sign[second]; if(dominant==1) pillar(new Pos(p[0],p[1],p[2])); d2-=a[dominant]; }
            p[dominant]+=sign[dominant]; d1+=a[first]; d2+=a[second];
        }
    }
}
