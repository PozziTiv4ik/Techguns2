package techguns.modern.world.structure.camp;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.FluidState;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.*;
import techguns.modern.world.structure.camp.CampPart.BiomeColorType;

/** Source algorithms execute against this private snapshot, never against a live neighbouring chunk. */
public final class CampWorld implements BlockGetter {
    public interface Terrain {
        BlockState state(BlockPos pos);
        int top(int x,int z);
    }
    public record Cell(BlockState state,int copyY) { public Cell(BlockState state) { this(state,Integer.MIN_VALUE); } }
    public record Loot(Identifier table,long seed) {}
    public record Post(String mob,int quota,int height) {}
    public record Component(String kind,int x,int y,int z,int width,int height,int depth,int direction) {}
    public record Plan(BlockPos origin,int width,int depth,int minY,int maxY,BiomeColorType color,
                       Map<BlockPos,Cell> cells,Set<Long> clearColumns,Map<BlockPos,Loot> loot,Map<BlockPos,Post> posts,List<Component> components) {
        public Plan {
            origin=origin.immutable(); cells=Collections.unmodifiableMap(new LinkedHashMap<>(cells)); clearColumns=Set.copyOf(clearColumns);
            loot=Map.copyOf(loot); posts=Map.copyOf(posts); components=List.copyOf(components);
        }
    }
    public final Random rand;
    private final Terrain terrain;
    private final BlockPos origin;
    private final int width,depth,minY,maxY;
    private final BiomeColorType color;
    private final Map<BlockPos,Cell> cells=new LinkedHashMap<>();
    private final Map<Long,Integer> changedTop=new HashMap<>();
    private final Map<BlockPos,Loot> loot=new HashMap<>();
    private final Map<BlockPos,Post> posts=new HashMap<>();
    private final Set<Long> clearColumns=new HashSet<>();
    private final List<Component> components=new ArrayList<>();
    public CampWorld(BlockPos origin,int width,int depth,int minY,int maxY,BiomeColorType color,long decorationSeed,Terrain terrain) {
        this.origin=origin.immutable(); this.width=width; this.depth=depth; this.minY=minY; this.maxY=maxY; this.color=color; this.terrain=terrain; rand=new Random(decorationSeed);
        if(width<5||depth<5||width>79||depth>79||minY>=maxY) throw new IllegalArgumentException("Invalid camp dimensions");
    }
    public static long column(int x,int z) { return ((long)x<<32)^(z&0xffffffffL); }
    public static int columnX(long value) { return (int)(value>>32); }
    public static int columnZ(long value) { return (int)value; }
    public BiomeColorType color() { return color; }
    @Override public int getMinY() { return minY; }
    @Override public int getHeight() { return maxY-minY; }
    public int getActualHeight() { return maxY; }
    private void check(int x,int z) {
        if(x<origin.getX()-3||z<origin.getZ()-3||x>origin.getX()+width+2||z>origin.getZ()+depth+2) throw new IllegalArgumentException("Camp read/write outside its planned margin: "+x+","+z);
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        check(pos.getX(),pos.getZ()); var cell=cells.get(pos); return cell==null?terrain.state(pos):cell.state();
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).isAir(); }
    public void setBlockState(BlockPos pos,BlockState state,int flags) { setBlockState(pos,state); }
    public void setBlockState(BlockPos pos,BlockState state) {
        check(pos.getX(),pos.getZ()); if(pos.getY()<minY||pos.getY()>=maxY) throw new IllegalArgumentException("Camp outside build height");
        pos=pos.immutable(); cells.put(pos,new Cell(state)); loot.remove(pos); posts.remove(pos);
        changedTop.merge(column(pos.getX(),pos.getZ()),pos.getY()+1,Math::max);
    }
    public void setBlockToAir(BlockPos pos) { setBlockState(pos,Blocks.AIR.defaultBlockState()); }
    private int top(int x,int z) { check(x,z); return Math.min(maxY-1,Math.max(terrain.top(x,z),changedTop.getOrDefault(column(x,z),minY))); }
    public int topAll(int x,int z) {
        for(int y=top(x,z);y>=minY;y--) if(!getBlockState(new BlockPos(x,y,z)).isAir()) return y+1; return minY;
    }
    public int getHeight(int x,int z) {
        for(int y=top(x,z);y>=minY;y--) if(getBlockState(new BlockPos(x,y,z)).getLightDampening()>0) return y+1; return minY;
    }
    public int solidHeight(int x,int z) {
        for(int y=top(x,z);y>=minY;y--) {
            var s=getBlockState(new BlockPos(x,y,z));
            // Source Material.CLOTH/IRON/WOOD blocks movement even for these partial shapes.
            boolean authored=s.getBlock() instanceof CamouflageNetBlock || s.getBlock() instanceof SandbagBlock || s.getBlock() instanceof IndustrialLampBlock || s.getBlock() instanceof MetalLadderBlock;
            if((s.blocksMotion()||authored||!s.getFluidState().isEmpty())&&!s.is(net.minecraft.tags.BlockTags.LEAVES)) return y+1;
        }
        return minY;
    }
    public void clearColumn(int x,int z) { check(x,z); clearColumns.add(column(x,z)); }
    public void raiseGround(int x,int y,int z,int newY) {
        var source=new BlockPos(x,y,z); var state=getBlockState(source); boolean ground=CampTerrain.ground(state);
        var cell=cells.get(source); int copy=ground?(cell==null?y:cell.copyY()):Integer.MIN_VALUE;
        if(!ground) state=Blocks.DIRT.defaultBlockState();
        for(int i=y+1;i<=newY;i++) {
            var p=new BlockPos(x,i,z); setBlockState(p,state); cells.put(p,new Cell(state,copy));
        }
    }
    public void loot(BlockPos pos,Identifier table,long seed) { loot.put(pos.immutable(),new Loot(table,seed)); }
    public void post(BlockPos pos,String mob,int quota,int height) {
        setBlockState(pos,NpcSpawnerContent.SOLDIER_BLOCK.get().defaultBlockState());
        posts.put(pos.immutable(),new Post(mob,quota,height));
    }
    public void component(String kind,int x,int y,int z,int sx,int sy,int sz,int direction) { components.add(new Component(kind,x,y,z,sx,sy,sz,direction)); }
    public Plan freeze() {
        // Connections are resolved from the complete plan, so a neighbour's generation order is irrelevant.
        for(var entry:cells.entrySet()) {
            var s=connected(entry.getValue().state(),this,entry.getKey());
            entry.setValue(new Cell(s,entry.getValue().copyY()));
        }
        // Native chest halves are explicit states; pair compatible authored facings without rerolling either loot seed.
        for(var entry:cells.entrySet()) {
            var s=entry.getValue().state(); if(!s.is(Blocks.CHEST)||s.getValue(ChestBlock.TYPE)!=ChestType.SINGLE) continue;
            var facing=s.getValue(ChestBlock.FACING);
            for(var d:List.of(facing.getClockWise(),facing.getCounterClockWise())) {
                var q=entry.getKey().relative(d); var other=cells.get(q);
                if(other==null||!other.state().is(Blocks.CHEST)||other.state().getValue(ChestBlock.TYPE)!=ChestType.SINGLE||other.state().getValue(ChestBlock.FACING)!=facing) continue;
                var type=d==facing.getClockWise()?ChestType.LEFT:ChestType.RIGHT;
                entry.setValue(new Cell(s.setValue(ChestBlock.TYPE,type)));
                cells.put(q,new Cell(other.state().setValue(ChestBlock.TYPE,type.getOpposite()))); break;
            }
        }
        return new Plan(origin,width,depth,minY,maxY,color,cells,clearColumns,loot,posts,components);
    }
    public static BlockState connected(BlockState s,BlockGetter view,BlockPos p) {
        if(s.getBlock() instanceof FenceBlock || s.getBlock() instanceof IronBarsBlock) for(var d:Direction.Plane.HORIZONTAL) {
            var q=p.relative(d); var n=view.getBlockState(q); boolean solid=n.isFaceSturdy(view,q,d.getOpposite());
            boolean connect=s.getBlock() instanceof FenceBlock fence?fence.connectsTo(n,solid,d.getOpposite()):((IronBarsBlock)s.getBlock()).attachsTo(n,solid);
            s=s.setValue(switch(d) { case NORTH->BlockStateProperties.NORTH; case SOUTH->BlockStateProperties.SOUTH; case EAST->BlockStateProperties.EAST; default->BlockStateProperties.WEST; },connect);
        }
        if(s.getBlock() instanceof SandbagBlock b) s=b.connected(s,view,p);
        if(s.getBlock() instanceof CamouflageNetBlock b) s=b.connected(s,view,p);
        if(s.getBlock() instanceof IndustrialLampBlock b) for(var d:Direction.values()) s=s.setValue(IndustrialLampBlock.CONNECTIONS.get(d),b.lantern()&&IndustrialLampBlock.canAttach(view,p,d));
        return s;
    }
    /** Authored neighbours always come from the complete plan; untouched terrain uses the native surface/carvers. */
    public static BlockGetter placementView(Plan plan,BlockGetter terrain) { return new BlockGetter() {
        private final Map<Long,Integer> groundHeights=new HashMap<>();
        public BlockState getBlockState(BlockPos p) {
            var c=plan.cells().get(p); if(c!=null) return c.state();
            var s=terrain.getBlockState(p); long column=column(p.getX(),p.getZ());
            // A neighbouring chunk may still contain a tree that its own clearing pass will remove.
            if(!s.isAir()&&!CampTerrain.ground(s)&&plan.clearColumns().contains(column)) {
                int ground=groundHeights.computeIfAbsent(column,ignored->{
                    for(int y=plan.maxY()-1;y>=plan.minY();y--) if(CampTerrain.ground(terrain.getBlockState(new BlockPos(p.getX(),y,p.getZ())))) return y;
                    return plan.minY()-1;
                });
                if(p.getY()>ground) return Blocks.AIR.defaultBlockState();
            }
            return s;
        }
        public FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
        public BlockEntity getBlockEntity(BlockPos p) { return null; }
        public int getMinY() { return plan.minY(); }
        public int getHeight() { return plan.maxY()-plan.minY(); }
    }; }
    public static Plan create(BlockPos origin,int width,int depth,int minY,int maxY,BiomeColorType color,long layoutSeed,long decorationSeed,Terrain terrain) {
        var world=new CampWorld(origin,width,depth,minY,maxY,color,decorationSeed,terrain);
        CampTerrain.removeJunkInArea(world,origin.getX()-1,origin.getZ()-1,width+2,depth+2);
        var random=new Random(layoutSeed); var camp=new CampLayout(4,random); camp.init(origin.getX(),origin.getY(),origin.getZ(),width,depth); camp.setBlocks(world,random);
        return world.freeze();
    }
}
