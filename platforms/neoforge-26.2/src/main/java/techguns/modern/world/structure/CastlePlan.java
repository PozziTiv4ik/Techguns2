package techguns.modern.world.structure;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.FluidState;
import techguns.core.castle.*;
import techguns.core.castle.CastleSegments.SegmentType;
import techguns.modern.npc.spawner.NpcSpawnerContent;
import techguns.modern.world.structure.camp.CampWorld;

/** Immutable complete dungeon, including authored air. Random choices precede chunk placement. */
public record CastlePlan(BlockPos origin,int width,int height,int depth,long seed,CastleLayout.Result graph,
                         Map<BlockPos,BlockState> cells,Map<BlockPos,Long> loot,Set<BlockPos> posts,List<Segment> segments) {
    public static final List<String> FAMILIES=List.of("ncdung1","nclower1","ncmid1","ncupper1","nctop1","ncroof1");
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    public record Segment(int family,SegmentType type,BlockPos origin,int rotation) {}
    private record Cell(int x,int y,int z,BlockState state) {}
    private static final class Templates {
        static final Map<String,List<Cell>> ALL=load();
        private static Map<String,List<Cell>> load() {
            try(var stream=CastlePlan.class.getResourceAsStream("/data/techguns/castle/templates.json")) {
                if(stream==null) throw new IOException("Missing Castle scans");
                var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                var result=new HashMap<String,List<Cell>>();
                for(String family:FAMILIES) for(var entry:root.getAsJsonObject(family).getAsJsonObject("segments").entrySet()) {
                    var data=entry.getValue().getAsJsonObject(); var palette=new ArrayList<BlockState>();
                    for(var state:data.getAsJsonArray("palette")) palette.add(BlockState.CODEC.parse(JsonOps.INSTANCE,state).getOrThrow());
                    var cells=new ArrayList<Cell>();
                    for(var value:data.getAsJsonArray("cells")) { var a=value.getAsJsonArray(); cells.add(new Cell(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),palette.get(a.get(3).getAsInt()))); }
                    result.put(family+"/"+entry.getKey(),List.copyOf(cells));
                }
                if(result.size()!=96) throw new IOException("Incomplete Castle scans");
                return Map.copyOf(result);
            } catch(IOException e) { throw new UncheckedIOException(e); }
        }
    }
    public CastlePlan {
        origin=origin.immutable(); cells=Collections.unmodifiableMap(new LinkedHashMap<>(cells));
        loot=Map.copyOf(loot); posts=Set.copyOf(posts); segments=List.copyOf(segments);
    }
    public static CastlePlan create(BlockPos origin,int width,int height,int depth,long seed) {
        var builder=new Builder(seed);
        var graph=CastleLayout.generate(builder,origin.getX(),origin.getY(),origin.getZ(),width,height,depth);
        return builder.freeze(origin,width,height,depth,seed,graph);
    }
    /** Allows every authored segment/rotation to be exercised independently of random selection. */
    public static CastlePlan template(BlockPos origin,int family,SegmentType type,int rotation,long seed) {
        var builder=new Builder(seed); builder.segment(family,type,origin.getX(),origin.getY(),origin.getZ(),rotation);
        return builder.freeze(origin,5,type==SegmentType.RAMP?10:5,5,seed,new CastleLayout.Result(List.of(),List.of(),new CastlePos(0,0,0),CastleFacing.EAST));
    }
    private static final class Builder extends CastleWorld {
        final Map<BlockPos,BlockState> cells=new LinkedHashMap<>();
        final Map<BlockPos,Long> loot=new HashMap<>();
        final Set<BlockPos> posts=new HashSet<>();
        final List<Segment> segments=new ArrayList<>();
        Builder(long seed) { super(seed); }
        @Override public void segment(int family,SegmentType type,int x,int y,int z,int rotation) {
            segments.add(new Segment(family,type,new BlockPos(x,y,z),rotation));
            for(var cell:Templates.ALL.get(FAMILIES.get(family)+"/"+type)) {
                int dx=cell.x(),dz=cell.z();
                for(int r=0;r<rotation;r++) { int old=dx; dx=dz; dz=4-old; }
                var p=new BlockPos(x+dx,y+cell.y(),z+dz);
                if(p.getY()<1) continue; // Original Structure.placeBlocks lower boundary.
                var state=cell.state().rotate(ROTATIONS.get(rotation));
                // Legacy MBlockSkullBlock writes the raw 0..3 value into a sixteen-step yaw.
                if(state.is(Blocks.SKELETON_SKULL)) state=state.setValue(SkullBlock.ROTATION,rotation);
                cells.put(p,state); loot.remove(p); posts.remove(p);
                if(state.is(Blocks.CHEST)) loot.put(p,rand.nextLong());
            }
        }
        @Override public void post(int x,int y,int z) {
            if(y<1) return;
            var p=new BlockPos(x,y,z); cells.put(p,NpcSpawnerContent.BLOCK.get().defaultBlockState()); loot.remove(p); posts.add(p);
        }
        CastlePlan freeze(BlockPos origin,int width,int height,int depth,long seed,CastleLayout.Result graph) {
            // Native chest halves need explicit pairing; source stored this as an actual-state connection.
            for(var e:cells.entrySet()) {
                var s=e.getValue(); if(!s.is(Blocks.CHEST)||s.getValue(ChestBlock.TYPE)!=ChestType.SINGLE) continue;
                var facing=s.getValue(ChestBlock.FACING);
                for(var d:List.of(facing.getClockWise(),facing.getCounterClockWise())) {
                    var q=e.getKey().relative(d); var n=cells.get(q);
                    if(n==null||!n.is(Blocks.CHEST)||n.getValue(ChestBlock.TYPE)!=ChestType.SINGLE||n.getValue(ChestBlock.FACING)!=facing) continue;
                    var type=d==facing.getClockWise()?ChestType.LEFT:ChestType.RIGHT;
                    e.setValue(s.setValue(ChestBlock.TYPE,type)); cells.put(q,n.setValue(ChestBlock.TYPE,type.getOpposite())); break;
                }
            }
            return new CastlePlan(origin,width,height,depth,seed,graph,cells,loot,posts,segments);
        }
    }
    public BlockGetter view(BlockGetter terrain) {
        return new BlockGetter() {
            @Override public BlockState getBlockState(BlockPos p) { var state=cells.get(p); return state==null?terrain.getBlockState(p):state; }
            @Override public BlockEntity getBlockEntity(BlockPos p) { return null; }
            @Override public FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
            @Override public int getHeight() { return terrain.getHeight(); }
            @Override public int getMinY() { return terrain.getMinY(); }
        };
    }
    public static BlockState connected(BlockState state,BlockGetter view,BlockPos p) {
        state=CampWorld.connected(state,view,p);
        if(!(state.getBlock() instanceof StairBlock)) return state;
        // StairBlock's shape query is private. Same neighbour rule, applied to the full saved plan.
        var facing=state.getValue(StairBlock.FACING); var half=state.getValue(StairBlock.HALF);
        var behind=view.getBlockState(p.relative(facing));
        if(behind.getBlock() instanceof StairBlock&&behind.getValue(StairBlock.HALF)==half) {
            var d=behind.getValue(StairBlock.FACING);
            if(d.getAxis()!=facing.getAxis()&&canShape(state,view,p.relative(d.getOpposite())))
                return state.setValue(StairBlock.SHAPE,d==facing.getCounterClockWise()?StairsShape.OUTER_LEFT:StairsShape.OUTER_RIGHT);
        }
        var front=view.getBlockState(p.relative(facing.getOpposite()));
        if(front.getBlock() instanceof StairBlock&&front.getValue(StairBlock.HALF)==half) {
            var d=front.getValue(StairBlock.FACING);
            if(d.getAxis()!=facing.getAxis()&&canShape(state,view,p.relative(d)))
                return state.setValue(StairBlock.SHAPE,d==facing.getCounterClockWise()?StairsShape.INNER_LEFT:StairsShape.INNER_RIGHT);
        }
        return state.setValue(StairBlock.SHAPE,StairsShape.STRAIGHT);
    }
    private static boolean canShape(BlockState state,BlockGetter view,BlockPos p) {
        var other=view.getBlockState(p);
        return !(other.getBlock() instanceof StairBlock)||other.getValue(StairBlock.FACING)!=state.getValue(StairBlock.FACING)||other.getValue(StairBlock.HALF)!=state.getValue(StairBlock.HALF);
    }
}
