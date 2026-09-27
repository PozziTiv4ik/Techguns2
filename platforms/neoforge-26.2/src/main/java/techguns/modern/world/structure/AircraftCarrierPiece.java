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
import techguns.modern.TGContent;
import techguns.modern.npc.spawner.*;

/** Saved supplies and loot seeds; spent posts and opened chests are never recreated. */
public final class AircraftCarrierPiece extends StructurePiece {
    private final AircraftCarrierPlan plan;
    private final Set<Long> placedChunks=new HashSet<>();
    public AircraftCarrierPiece(AircraftCarrierPlan plan) {
        super(LocationContent.AIRCRAFT_CARRIER_PIECE.get(),0,BoundingBox.encapsulatingPositions(plan.cells().keySet()).orElseThrow());
        this.plan=plan;setOrientation(null);
    }
    public AircraftCarrierPiece(StructurePieceSerializationContext context,CompoundTag tag) {
        super(LocationContent.AIRCRAFT_CARRIER_PIECE.get(),tag);
        if(tag.getIntOr("PlanVersion",0)!=1) throw new IllegalArgumentException("Unknown carrier plan version");
        var origin=pos(tag.getIntArray("Origin").orElseThrow());int turns=tag.getIntOr("Turns",-1);
        var palette=tag.getListOrEmpty("Palette");int[] packed=tag.getIntArray("Cells").orElseThrow();
        if(turns<0||turns>3||packed.length!=3929*4||palette.isEmpty()||palette.size()>128) throw new IllegalArgumentException("Invalid carrier plan size");
        var states=new ArrayList<BlockState>();
        for(int i=0;i<palette.size();i++) states.add(NbtUtils.readBlockState(context.registryAccess().lookupOrThrow(Registries.BLOCK),palette.getCompoundOrEmpty(i)));
        var cells=new LinkedHashMap<BlockPos,BlockState>();
        for(int i=0;i<packed.length;i+=4) {
            var p=new BlockPos(packed[i],packed[i+1],packed[i+2]);int index=packed[i+3];
            if(!boundingBox.isInside(p)||index<0||index>=states.size()||cells.put(p,states.get(index))!=null) throw new IllegalArgumentException("Invalid carrier cell");
        }
        var loot=new HashMap<BlockPos,Long>();var lt=tag.getListOrEmpty("Loot");
        for(int i=0;i<lt.size();i++) {var t=lt.getCompoundOrEmpty(i);var p=pos(t.getIntArray("Pos").orElseThrow());
            if(!cells.containsKey(p)||!cells.get(p).is(Blocks.CHEST)||loot.put(p,t.getLongOr("Seed",0))!=null) throw new IllegalArgumentException("Invalid carrier chest");}
        plan=new AircraftCarrierPlan(origin,turns,tag.getLongOr("Seed",0),cells,loot);
        for(long chunk:tag.getLongArray("PlacedChunks").orElse(new long[0])) placedChunks.add(chunk);
    }
    private static BlockPos pos(int[] v) {if(v.length!=3) throw new IllegalArgumentException("Expected position");return new BlockPos(v[0],v[1],v[2]);}
    private static int[] xyz(BlockPos p) {return new int[]{p.getX(),p.getY(),p.getZ()};}
    public AircraftCarrierPlan plan() {return plan;}
    @Override protected synchronized void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        tag.putInt("PlanVersion",1);tag.putIntArray("Origin",xyz(plan.origin()));tag.putInt("Turns",plan.turns());tag.putLong("Seed",plan.seed());
        var palette=new LinkedHashMap<BlockState,Integer>();var states=new ListTag();int[] cells=new int[plan.cells().size()*4];int i=0;
        for(var e:plan.cells().entrySet()) {int index=palette.computeIfAbsent(e.getValue(),s->{states.add(NbtUtils.writeBlockState(s));return states.size()-1;});
            cells[i++]=e.getKey().getX();cells[i++]=e.getKey().getY();cells[i++]=e.getKey().getZ();cells[i++]=index;}
        tag.put("Palette",states);tag.putIntArray("Cells",cells);
        var loot=new ListTag();plan.loot().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{var t=new CompoundTag();t.putIntArray("Pos",xyz(e.getKey()));t.putLong("Seed",e.getValue());loot.add(t);});tag.put("Loot",loot);
        tag.putLongArray("PlacedChunks",placedChunks.stream().mapToLong(Long::longValue).sorted().toArray());
    }
    @Override public synchronized void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        if(placedChunks.contains(chunk.pack())) return;
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_ON_PLACE;
        var view=PlannedBlocks.view(plan.cells(),level);
        for(var e:plan.cells().entrySet()) {
            var p=e.getKey();if(!clip.isInside(p)) continue;
            var state=PlannedBlocks.connected(e.getValue(),view,p);level.setBlock(p,state,flags);
            if((state.getBlock() instanceof StairBlock||state.getBlock() instanceof IronBarsBlock)&&level.getChunk(p) instanceof net.minecraft.world.level.chunk.ProtoChunk pending)
                pending.markPosForPostProcessing(p);
            if(level.getBlockEntity(p) instanceof NpcSpawnerBlockEntity post) {
                boolean helicopter=state.is(NpcSpawnerContent.SOLDIER_BLOCK.get());
                post.configure(helicopter?1:6,helicopter?1:2,helicopter?200:150,helicopter?0:2,0,
                    helicopter?List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("attackhelicopter"),1)):
                        List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("armysoldier"),1),new NpcSpawnerBlockEntity.Entry(TGContent.id("commando"),1)),ItemStack.EMPTY);
            }
        }
        for(var e:plan.loot().entrySet()) if(clip.isInside(e.getKey())&&level.getBlockEntity(e.getKey()) instanceof ChestBlockEntity chest)
            chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,TGContent.id("chests/aircraftcarrier")),e.getValue());
        placedChunks.add(chunk.pack());
    }
}
