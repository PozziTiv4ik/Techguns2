import java.util.*;
import techguns.core.BugNestLayout;

/** Minimal block-write world for executing the unmodified legacy generator in a headless JVM.
 * It intentionally does not emulate neighbor updates, entities or Minecraft world generation.
 */
public class BugNestOracle {
    public static void main(String[] args) {
        int runs=0;
        for(int sign:new int[]{-1,1}) for(int seed=0;seed<16;seed++) for(boolean flat:new boolean[]{false,true}) {
            int x=sign*512,z=sign*6784,y=64,width=16+seed,depth=31-seed; long decor=seed*7907L-19;
            var world=new World(decor,flat?y:Integer.MAX_VALUE);
            new AlienBugNest(x,y,z,width,0,depth,new Random(seed)).setBlocks(world);
            var modern=BugNestLayout.create(new BugNestLayout.Pos(x,y,z),width,depth,seed,decor,p->p.y()>world.surface?0:9);
            if(!world.cells.equals(modern.cells())) {
                var positions=new HashSet<>(world.cells.keySet()); positions.addAll(modern.cells().keySet());
                var differences=positions.stream().filter(p->!Objects.equals(world.cells.get(p),modern.cells().get(p))).limit(8)
                        .map(p->p+": source="+world.cells.get(p)+", port="+modern.cells().get(p)).toList();
                throw new AssertionError("Legacy geometry mismatch seed="+seed+", sign="+sign+", flat="+flat+": "+differences);
            }
            runs++;
        }
        System.out.println("Exact legacy block-write comparison passed for "+runs+" layouts.");
    }
}
class World {
    final Random rand; final int surface; final Map<BugNestLayout.Pos,Integer> cells=new LinkedHashMap<>();
    World(long seed,int surface) { rand=new Random(seed); this.surface=surface; }
    static BugNestLayout.Pos key(BlockPos p) { return new BugNestLayout.Pos(p.x,p.y,p.z); }
    int get(BlockPos p) { return cells.getOrDefault(key(p),p.y>surface?0:9); }
    boolean isAirBlock(BlockPos p) { return get(p)==0; }
    void setBlockState(BlockPos p,int material) { cells.put(key(p),material); }
    void setBlockState(BlockPos p,int material,int flags) { setBlockState(p,material); }
}
class BlockPos {
    int x,y,z;
    BlockPos(int x,int y,int z) { this.x=x; this.y=y; this.z=z; }
}
class MutableBlockPos extends BlockPos {
    MutableBlockPos() { super(0,0,0); } MutableBlockPos(BlockPos p) { super(p.x,p.y,p.z); }
    MutableBlockPos setPos(int x,int y,int z) { this.x=x;this.y=y;this.z=z;return this; }
    MutableBlockPos move(EnumFacing f) { x+=f.x;z+=f.z;return this; }
}
enum EnumFacing { NORTH(0,-1),EAST(1,0),SOUTH(0,1),WEST(-1,0); final int x,z; EnumFacing(int x,int z) {this.x=x;this.z=z;} }
class Block {
    final int material; Block(int material) {this.material=material;}
    private boolean solid(World w,BlockPos p,int x,int z) { int m=w.get(new BlockPos(p.x+x,p.y,p.z+z)); return m==1 || m==2 || m==3 || m==9; }
    boolean canPlaceBlockAt(World w,BlockPos p) { return solid(w,p,0,1) || solid(w,p,0,-1) || solid(w,p,1,0) || solid(w,p,-1,0); }
    int getStateForPlacement(World w,BlockPos p,EnumFacing f,int x,int y,int z,int meta,Object entity) {
        if(solid(w,p,0,1)) return 5; if(solid(w,p,0,-1)) return 7; if(solid(w,p,1,0)) return 8; return 6;
    }
}
class Blocks { static final Block AIR=new Block(0),SANDSTONE=new Block(3); }
class TGBlocks { static final Block SAND_HARD=new Block(1),SLIMY_BLOCK=new Block(2),SLIMY_LADDER=new Block(5); }
enum EnumTGSandHardTypes { BUGNEST_SAND }
enum EnumTGSlimyType { BUGNEST_EGGS }
enum EnumMonsterSpawnerType { HOLE }
enum EnumLootType { TIER0 }
enum BiomeColorType { WOODLAND }
class AlienBug {}
class MBlock {
    final Block block;
    MBlock(Block block,int meta) { this.block=block; }
    void setBlock(World w,MutableBlockPos p,int turn) { setBlock(w,p,turn,null,null); }
    public void setBlock(World w,MutableBlockPos p,int turn,EnumLootType loot,BiomeColorType biome) { w.setBlockState(p,block.material); }
}
class MultiMBlock extends MBlock {
    final Block[] blocks; final int[] weights;
    MultiMBlock(Block[] blocks,int[] meta,int[] weights) { super(blocks[0],0);this.blocks=blocks;this.weights=weights; }
    @Override public void setBlock(World w,MutableBlockPos p,int turn,EnumLootType loot,BiomeColorType biome) {
        int roll=w.rand.nextInt(Arrays.stream(weights).sum()+1),sum=0;
        for(int i=0;i<weights.length;i++) { sum+=weights[i]; if(roll<=sum) {w.setBlockState(p,blocks[i].material);return;} }
        throw new AssertionError();
    }
}
class MBlockTGSpawner extends MBlock {
    MBlockTGSpawner(EnumMonsterSpawnerType type,int left,int max,int delay,int range) { super(new Block(4),0); }
    MBlockTGSpawner addMobType(Class<?> type,int weight) { return this; }
}
class MBlockRegister { static final MBlock AIR=new MBlock(Blocks.AIR,0); }
class Vec3d {
    final double x,y,z; Vec3d(double x,double y,double z) {this.x=x;this.y=y;this.z=z;}
    Vec3d subtract(Vec3d p) {return new Vec3d(x-p.x,y-p.y,z-p.z);}
    double squareDistanceTo(Vec3d p) {var d=subtract(p);return d.x*d.x+d.y*d.y+d.z*d.z;}
    double lengthVector() {return Math.sqrt(x*x+y*y+z*z);}
    Vec3d normalize() {double l=lengthVector();return l<1e-4?new Vec3d(0,0,0):new Vec3d(x/l,y/l,z/l);}
    Vec3d crossProduct(Vec3d p) {return new Vec3d(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
}
class MathUtil {
    static class Vec2 { double x,y;Vec2(double x,double y){this.x=x;this.y=y;} }
    static Vec2 polarOffsetXZ(double x,double z,double r,double a){return new Vec2(x+r*Math.cos(a),z+r*Math.sin(a));}
}
class Vec2 extends MathUtil.Vec2 { Vec2(double x,double y){super(x,y);} }
