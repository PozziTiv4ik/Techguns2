package techguns.modern.world.structure;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import techguns.modern.TGContent;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.structure.camp.*;
import techguns.modern.world.structure.camp.CampWorld.*;
import techguns.modern.world.structure.camp.CampPart.BiomeColorType;

/** Complete, saved camp plan. Placement never rerolls or writes outside the current chunk. */
public final class MilitaryCampPiece extends StructurePiece {
    public static final Identifier ID=TGContent.id("military_camp");
    private final Plan plan;
    private final long layoutSeed,decorationSeed;
    private final Set<Long> placedChunks=new HashSet<>();
    private volatile Map<Long,Integer> decorationFloors;
    public MilitaryCampPiece(Plan plan,long layoutSeed,long decorationSeed) {
        super(LocationContent.MILITARY_CAMP_PIECE.get(),0,new BoundingBox(plan.origin().getX()-1,plan.minY(),plan.origin().getZ()-1,
                plan.origin().getX()+plan.width(),plan.maxY()-1,plan.origin().getZ()+plan.depth()));
        this.plan=plan; this.layoutSeed=layoutSeed; this.decorationSeed=decorationSeed; setOrientation(null);
    }
    public MilitaryCampPiece(StructurePieceSerializationContext context,CompoundTag tag) {
        super(LocationContent.MILITARY_CAMP_PIECE.get(),tag);
        if(tag.getIntOr("PlanVersion",0)!=1) throw new IllegalArgumentException("Unknown MilitaryCamp plan version");
        layoutSeed=tag.getLongOr("LayoutSeed",0); decorationSeed=tag.getLongOr("DecorationSeed",0);
        int[] origin=tag.getIntArray("Origin").orElseThrow(),packed=tag.getIntArray("Cells").orElseThrow();
        var palette=tag.getListOrEmpty("Palette");
        if(origin.length!=3||packed.length==0||packed.length%5!=0||packed.length>2500000||palette.isEmpty()||palette.size()>4096) throw new IllegalArgumentException("Invalid camp plan size");
        var states=new ArrayList<BlockState>(); for(int i=0;i<palette.size();i++) states.add(NbtUtils.readBlockState(context.registryAccess().lookupOrThrow(Registries.BLOCK),palette.getCompoundOrEmpty(i)));
        var cells=new LinkedHashMap<BlockPos,Cell>();
        for(int i=0;i<packed.length;i+=5) {
            var p=new BlockPos(packed[i],packed[i+1],packed[i+2]); int index=packed[i+3],copy=packed[i+4];
            if(!boundingBox.isInside(p)||index<0||index>=states.size()||copy!=Integer.MIN_VALUE&&(copy<boundingBox.minY()||copy>boundingBox.maxY())||cells.put(p,new Cell(states.get(index),copy))!=null) throw new IllegalArgumentException("Invalid camp cell");
        }
        var clear=new HashSet<Long>(); for(long c:tag.getLongArray("ClearColumns").orElseThrow()) {
            if(!boundingBox.isInside(new BlockPos(CampWorld.columnX(c),boundingBox.minY(),CampWorld.columnZ(c)))) throw new IllegalArgumentException("Invalid clearing column"); clear.add(c);
        }
        var loot=new HashMap<BlockPos,Loot>(); var lt=tag.getListOrEmpty("Loot");
        for(int i=0;i<lt.size();i++) { var t=lt.getCompoundOrEmpty(i); var p=position(t); if(!cells.containsKey(p)||!cells.get(p).state().is(Blocks.CHEST)) throw new IllegalArgumentException("Invalid chest location"); loot.put(p,new Loot(Identifier.parse(t.getStringOr("Table","")),t.getLongOr("Seed",0))); }
        var posts=new HashMap<BlockPos,Post>(); var pt=tag.getListOrEmpty("Posts");
        for(int i=0;i<pt.size();i++) { var t=pt.getCompoundOrEmpty(i); var p=position(t); var mob=t.getStringOr("Mob",""); if(!cells.containsKey(p)||!cells.get(p).state().is(NpcSpawnerContent.SOLDIER_BLOCK.get())||!List.of("armysoldier","attackhelicopter").contains(mob)) throw new IllegalArgumentException("Invalid camp post"); posts.put(p,new Post(mob,t.getIntOr("Quota",0),t.getIntOr("Height",0))); }
        var components=new ArrayList<Component>(); var ct=tag.getListOrEmpty("Components");
        for(int i=0;i<ct.size();i++) { var t=ct.getCompoundOrEmpty(i); var p=position(t); components.add(new Component(t.getStringOr("Kind",""),p.getX(),p.getY(),p.getZ(),t.getIntOr("Width",0),t.getIntOr("Height",0),t.getIntOr("Depth",0),t.getIntOr("Direction",0))); }
        int width=tag.getIntOr("Width",0),depth=tag.getIntOr("Depth",0); if(width<5||width>79||depth<5||depth>79) throw new IllegalArgumentException("Invalid camp dimensions");
        plan=new Plan(new BlockPos(origin[0],origin[1],origin[2]),width,depth,boundingBox.minY(),boundingBox.maxY()+1,BiomeColorType.valueOf(tag.getStringOr("Color","")),cells,clear,loot,posts,components);
        for(long c:tag.getLongArray("PlacedChunks").orElse(new long[0])) placedChunks.add(c);
    }
    private static BlockPos position(CompoundTag tag) { var p=tag.getIntArray("Pos").orElseThrow(); if(p.length!=3) throw new IllegalArgumentException("Invalid camp position"); return new BlockPos(p[0],p[1],p[2]); }
    private static CompoundTag at(BlockPos p) { var tag=new CompoundTag(); tag.putIntArray("Pos",new int[]{p.getX(),p.getY(),p.getZ()}); return tag; }
    public Plan plan() { return plan; }
    public long layoutSeed() { return layoutSeed; }
    public long decorationSeed() { return decorationSeed; }
    public boolean protectsDecoration(BlockPos pos) {
        if(!boundingBox.isInside(pos)) return false;
        var floors=decorationFloors;
        if(floors==null) {
            var result=new HashMap<Long,Integer>();
            for(var p:plan.cells().keySet()) result.merge(CampWorld.column(p.getX(),p.getZ()),p.getY(),Math::min);
            decorationFloors=floors=Map.copyOf(result);
        }
        long c=CampWorld.column(pos.getX(),pos.getZ());
        return plan.cells().containsKey(pos)||plan.clearColumns().contains(c)&&pos.getY()>=floors.getOrDefault(c,Integer.MAX_VALUE);
    }
    public boolean protectsDecoration(BlockPos pos,WorldGenLevel level) {
        if(protectsDecoration(pos)) return true;
        long c=CampWorld.column(pos.getX(),pos.getZ());
        if(!boundingBox.isInside(pos)||!plan.clearColumns().contains(c)) return false;
        var chunk=level.getChunk(pos);
        for(var type:List.of(Heightmap.Types.OCEAN_FLOOR_WG,Heightmap.Types.OCEAN_FLOOR))
            if(chunk.hasPrimedHeightmap(type)) return pos.getY()>=chunk.getHeight(type,pos.getX()&15,pos.getZ()&15)+1;
        for(int y=level.getMaxY()-1;y>=level.getMinY();y--) if(level.getBlockState(new BlockPos(pos.getX(),y,pos.getZ())).blocksMotion()) return pos.getY()>y;
        return true;
    }
    @Override protected synchronized void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        tag.putInt("PlanVersion",1); tag.putLong("LayoutSeed",layoutSeed); tag.putLong("DecorationSeed",decorationSeed);
        tag.putIntArray("Origin",new int[]{plan.origin().getX(),plan.origin().getY(),plan.origin().getZ()}); tag.putInt("Width",plan.width()); tag.putInt("Depth",plan.depth()); tag.putString("Color",plan.color().name());
        var palette=new LinkedHashMap<BlockState,Integer>(); var states=new ListTag(); int[] packed=new int[plan.cells().size()*5]; int i=0;
        for(var e:plan.cells().entrySet()) {
            int index=palette.computeIfAbsent(e.getValue().state(),s->{ states.add(NbtUtils.writeBlockState(s)); return states.size()-1; });
            packed[i++]=e.getKey().getX(); packed[i++]=e.getKey().getY(); packed[i++]=e.getKey().getZ(); packed[i++]=index; packed[i++]=e.getValue().copyY();
        }
        tag.putIntArray("Cells",packed); tag.put("Palette",states); tag.putLongArray("ClearColumns",plan.clearColumns().stream().mapToLong(Long::longValue).sorted().toArray());
        var loot=new ListTag(); plan.loot().forEach((p,v)->{var t=at(p); t.putString("Table",v.table().toString()); t.putLong("Seed",v.seed()); loot.add(t);}); tag.put("Loot",loot);
        var posts=new ListTag(); plan.posts().forEach((p,v)->{var t=at(p); t.putString("Mob",v.mob()); t.putInt("Quota",v.quota()); t.putInt("Height",v.height()); posts.add(t);}); tag.put("Posts",posts);
        var components=new ListTag(); for(var c:plan.components()) { var t=at(new BlockPos(c.x(),c.y(),c.z())); t.putString("Kind",c.kind()); t.putInt("Width",c.width()); t.putInt("Height",c.height()); t.putInt("Depth",c.depth()); t.putInt("Direction",c.direction()); components.add(t); } tag.put("Components",components);
        tag.putLongArray("PlacedChunks",placedChunks.stream().mapToLong(Long::longValue).sorted().toArray());
    }
    @Override public synchronized void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        long key=chunk.pack(); if(placedChunks.contains(key)) return;
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_ON_PLACE;
        var copies=new HashMap<BlockPos,BlockState>();
        for(var e:plan.cells().entrySet()) if(clip.isInside(e.getKey())&&e.getValue().copyY()!=Integer.MIN_VALUE) {
            var p=e.getKey(); var source=new BlockPos(p.getX(),e.getValue().copyY(),p.getZ());
            copies.computeIfAbsent(source,q->{var s=level.getBlockState(q); return CampTerrain.ground(s)?s:Blocks.DIRT.defaultBlockState();});
        }
        for(long c:plan.clearColumns()) {
            int x=CampWorld.columnX(c),z=CampWorld.columnZ(c); if(x<clip.minX()||x>clip.maxX()||z<clip.minZ()||z>clip.maxZ()) continue;
            for(int y=Math.min(clip.maxY(),level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z));y>Math.max(0,clip.minY()-1);y--) {
                var p=new BlockPos(x,y,z); if(CampTerrain.ground(level.getBlockState(p))) break; level.setBlock(p,Blocks.AIR.defaultBlockState(),flags);
            }
        }
        var view=CampWorld.placementView(plan,level);
        for(var e:plan.cells().entrySet()) {
            var p=e.getKey(); if(!clip.isInside(p)) continue; var c=e.getValue();
            var state=c.copyY()==Integer.MIN_VALUE?c.state():copies.get(new BlockPos(p.getX(),c.copyY(),p.getZ()));
            level.setBlock(p,CampWorld.connected(state,view,p),flags);
            // Vanilla templates defer these shape updates until neighbouring terrain has finished.
            // Queue only this chunk's cells; the chunk system owns the final neighbour refresh.
            if((state.getBlock() instanceof FenceBlock||state.getBlock() instanceof IronBarsBlock
                    ||state.getBlock() instanceof techguns.modern.world.CamouflageNetBlock||state.getBlock() instanceof techguns.modern.world.SandbagBlock
                    ||state.getBlock() instanceof techguns.modern.world.IndustrialLampBlock)
                    &&level.getChunk(p) instanceof net.minecraft.world.level.chunk.ProtoChunk pending) pending.markPosForPostProcessing(p);
        }
        for(var e:plan.loot().entrySet()) if(clip.isInside(e.getKey())&&level.getBlockEntity(e.getKey()) instanceof ChestBlockEntity chest)
            chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,e.getValue().table()),e.getValue().seed());
        for(var e:plan.posts().entrySet()) if(clip.isInside(e.getKey())&&level.getBlockEntity(e.getKey()) instanceof NpcSpawnerBlockEntity spawner) {
            var post=e.getValue(); spawner.configure(post.quota(),1,200,0,post.height(),List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id(post.mob()),1)),ItemStack.EMPTY);
        }
        placedChunks.add(key);
    }
}
