package techguns.modern.world.structure;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import techguns.core.castle.*;
import techguns.core.castle.CastleSegments.SegmentType;
import techguns.modern.TGContent;
import techguns.modern.npc.spawner.*;

/** One complete saved layout; a chunk is populated only once, including finite posts and loot seeds. */
public final class CastlePiece extends StructurePiece {
    private final CastlePlan plan;
    private final Set<Long> placedChunks=new HashSet<>();
    public CastlePiece(CastlePlan plan) {
        super(LocationContent.CASTLE_PIECE.get(),0,BoundingBox.encapsulatingPositions(plan.cells().keySet()).orElseThrow());
        this.plan=plan; setOrientation(null);
    }
    public CastlePiece(StructurePieceSerializationContext context,CompoundTag tag) {
        super(LocationContent.CASTLE_PIECE.get(),tag);
        if(tag.getIntOr("PlanVersion",0)!=1) throw new IllegalArgumentException("Unknown Castle plan version");
        var origin=pos(tag.getIntArray("Origin").orElseThrow());
        int[] size=tag.getIntArray("Size").orElseThrow(),packed=tag.getIntArray("Cells").orElseThrow();
        var palette=tag.getListOrEmpty("Palette");
        if(size.length!=3||Arrays.stream(size).anyMatch(n->n<5||n>47)||packed.length==0||packed.length%4!=0||packed.length>400000||palette.isEmpty()||palette.size()>512)
            throw new IllegalArgumentException("Invalid Castle plan size");
        var states=new ArrayList<BlockState>();
        for(int i=0;i<palette.size();i++) states.add(NbtUtils.readBlockState(context.registryAccess().lookupOrThrow(Registries.BLOCK),palette.getCompoundOrEmpty(i)));
        var cells=new LinkedHashMap<BlockPos,BlockState>();
        for(int i=0;i<packed.length;i+=4) {
            var p=new BlockPos(packed[i],packed[i+1],packed[i+2]); int index=packed[i+3];
            if(!boundingBox.isInside(p)||index<0||index>=states.size()||cells.put(p,states.get(index))!=null) throw new IllegalArgumentException("Invalid Castle cell");
        }
        var loot=new HashMap<BlockPos,Long>(); var lt=tag.getListOrEmpty("Loot");
        for(int i=0;i<lt.size();i++) { var t=lt.getCompoundOrEmpty(i); var p=pos(t.getIntArray("Pos").orElseThrow());
            if(!cells.containsKey(p)||!cells.get(p).is(Blocks.CHEST)) throw new IllegalArgumentException("Invalid Castle chest"); loot.put(p,t.getLongOr("Seed",0)); }
        var posts=new HashSet<BlockPos>(); int[] pt=tag.getIntArray("Posts").orElseThrow();
        if(pt.length%3!=0) throw new IllegalArgumentException("Invalid Castle posts");
        for(int i=0;i<pt.length;i+=3) { var p=new BlockPos(pt[i],pt[i+1],pt[i+2]);
            if(!cells.containsKey(p)||!cells.get(p).is(NpcSpawnerContent.BLOCK.get())) throw new IllegalArgumentException("Invalid Castle post"); posts.add(p); }
        var nodes=new ArrayList<CastleMaze.Node>(); int[] nt=tag.getIntArray("Nodes").orElseThrow();
        if(nt.length%9!=0||nt.length>6000) throw new IllegalArgumentException("Invalid Castle graph");
        for(int i=0;i<nt.length;i+=9) nodes.add(new CastleMaze.Node(nt[i],nt[i+1],nt[i+2],nt[i+3]!=0,nt[i+4]!=0,nt[i+5],nt[i+6],nt[i+7],nt[i+8]));
        var segments=new ArrayList<CastlePlan.Segment>(); int[] st=tag.getIntArray("Segments").orElseThrow();
        if(st.length%6!=0||st.length>6000) throw new IllegalArgumentException("Invalid Castle segments");
        for(int i=0;i<st.length;i+=6) {
            if(st[i]<0||st[i]>=6||st[i+1]<0||st[i+1]>=16||st[i+5]<0||st[i+5]>=4) throw new IllegalArgumentException("Invalid Castle segment");
            segments.add(new CastlePlan.Segment(st[i],SegmentType.values()[st[i+1]],new BlockPos(st[i+2],st[i+3],st[i+4]),st[i+5]));
        }
        var ep=pos(tag.getIntArray("Entrance").orElseThrow()); int facing=tag.getIntOr("Facing",-1);
        if(facing<0||facing>3) throw new IllegalArgumentException("Invalid entrance direction");
        var graph=new CastleLayout.Result(List.copyOf(nodes),Arrays.stream(tag.getIntArray("Attempts").orElseThrow()).boxed().toList(),new CastlePos(ep.getX(),ep.getY(),ep.getZ()),CastleFacing.getHorizontal(facing));
        plan=new CastlePlan(origin,size[0],size[1],size[2],tag.getLongOr("Seed",0),graph,cells,loot,posts,segments);
        for(long chunk:tag.getLongArray("PlacedChunks").orElse(new long[0])) placedChunks.add(chunk);
    }
    private static BlockPos pos(int[] v) { if(v.length!=3) throw new IllegalArgumentException("Expected position"); return new BlockPos(v[0],v[1],v[2]); }
    private static int[] xyz(BlockPos p) { return new int[]{p.getX(),p.getY(),p.getZ()}; }
    public CastlePlan plan() { return plan; }
    public boolean protectsDecoration(BlockPos p) { return plan.cells().containsKey(p); }
    @Override protected synchronized void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        tag.putInt("PlanVersion",1); tag.putIntArray("Origin",xyz(plan.origin())); tag.putIntArray("Size",new int[]{plan.width(),plan.height(),plan.depth()}); tag.putLong("Seed",plan.seed());
        var states=new ListTag(); var palette=new LinkedHashMap<BlockState,Integer>(); int[] cells=new int[plan.cells().size()*4]; int i=0;
        for(var e:plan.cells().entrySet()) { int index=palette.computeIfAbsent(e.getValue(),s->{states.add(NbtUtils.writeBlockState(s));return states.size()-1;});
            cells[i++]=e.getKey().getX(); cells[i++]=e.getKey().getY(); cells[i++]=e.getKey().getZ(); cells[i++]=index; }
        tag.put("Palette",states); tag.putIntArray("Cells",cells);
        var loot=new ListTag(); plan.loot().forEach((p,seed)->{var t=new CompoundTag();t.putIntArray("Pos",xyz(p));t.putLong("Seed",seed);loot.add(t);}); tag.put("Loot",loot);
        tag.putIntArray("Posts",plan.posts().stream().sorted().flatMapToInt(p->Arrays.stream(xyz(p))).toArray());
        tag.putIntArray("Nodes",plan.graph().nodes().stream().flatMapToInt(n->Arrays.stream(new int[]{n.x(),n.y(),n.z(),n.entrance()?1:0,n.ramp()?1:0,n.elevation(),n.rotation(),n.room(),n.pattern()})).toArray());
        tag.putIntArray("Segments",plan.segments().stream().flatMapToInt(s->Arrays.stream(new int[]{s.family(),s.type().ordinal(),s.origin().getX(),s.origin().getY(),s.origin().getZ(),s.rotation()})).toArray());
        var entrance=plan.graph().entrance(); tag.putIntArray("Entrance",new int[]{entrance.x(),entrance.y(),entrance.z()}); tag.putInt("Facing",plan.graph().facing().ordinal());
        tag.putIntArray("Attempts",plan.graph().attempts().stream().mapToInt(Integer::intValue).toArray());
        tag.putLongArray("PlacedChunks",placedChunks.stream().mapToLong(Long::longValue).sorted().toArray());
    }
    @Override public synchronized void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        if(placedChunks.contains(chunk.pack())) return;
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_ON_PLACE;
        var view=plan.view(level);
        for(var e:plan.cells().entrySet()) {
            var p=e.getKey(); if(!clip.isInside(p)) continue;
            var state=CastlePlan.connected(e.getValue(),view,p); level.setBlock(p,state,flags);
            if((state.getBlock() instanceof StairBlock||state.getBlock() instanceof IronBarsBlock)&&level.getChunk(p) instanceof net.minecraft.world.level.chunk.ProtoChunk pending)
                pending.markPosForPostProcessing(p);
        }
        for(var e:plan.loot().entrySet()) if(clip.isInside(e.getKey())&&level.getBlockEntity(e.getKey()) instanceof ChestBlockEntity chest)
            chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/castle")),e.getValue());
        for(var p:plan.posts()) if(clip.isInside(p)&&level.getBlockEntity(p) instanceof NpcSpawnerBlockEntity post)
            post.configure(2,2,200,2,0,List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("zombiesoldier"),1),new NpcSpawnerBlockEntity.Entry(TGContent.id("skeletonsoldier"),1)),ItemStack.EMPTY);
        placedChunks.add(chunk.pack());
    }
}
