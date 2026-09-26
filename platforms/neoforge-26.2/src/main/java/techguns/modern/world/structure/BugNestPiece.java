package techguns.modern.world.structure;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.TagValueInput;
import techguns.core.BugNestLayout;
import techguns.core.BugNestLayout.*;
import techguns.modern.TGContent;
import techguns.modern.npc.spawner.*;
import techguns.modern.world.SlimyContent;

/** Persist the resolved plan, including air and attachments, rather than rerolling on each chunk. */
public final class BugNestPiece extends StructurePiece {
    public static final Identifier ID=TGContent.id("alienbug_nest");
    private final Plan plan;
    private final long layoutSeed,decorSeed;
    public BugNestPiece(Plan plan,long layoutSeed,long decorSeed) {
        super(LocationContent.BUGNEST_PIECE.get(),0,BoundingBox.encapsulatingPositions(plan.cells().keySet().stream().map(BugNestPiece::pos).toList()).orElseThrow());
        this.plan=plan; this.layoutSeed=layoutSeed; this.decorSeed=decorSeed; setOrientation(null);
    }
    public BugNestPiece(CompoundTag tag) {
        super(LocationContent.BUGNEST_PIECE.get(),tag);
        if(tag.getIntOr("PlanVersion",0)!=1) throw new IllegalArgumentException("Unsupported AlienBugNest plan version");
        layoutSeed=tag.getLongOr("LayoutSeed",0); decorSeed=tag.getLongOr("DecorSeed",0);
        int[] packed=tag.getIntArray("Cells").orElseThrow(),rs=tag.getIntArray("Rooms").orElseThrow(),es=tag.getIntArray("Links").orElseThrow();
        if(packed.length==0 || packed.length%4!=0 || packed.length>200000 || rs.length%5!=0 || rs.length>60 || es.length%2!=0 || es.length>40)
            throw new IllegalArgumentException("Invalid AlienBugNest saved plan size");
        var cells=new LinkedHashMap<Pos,Integer>(); var rooms=new ArrayList<Room>(); var links=new ArrayList<Link>();
        for(int i=0;i<packed.length;i+=4) {
            var p=new Pos(packed[i],packed[i+1],packed[i+2]); int value=packed[i+3];
            if(value<0 || value>8 || !boundingBox.isInside(pos(p)) || cells.put(p,value)!=null) throw new IllegalArgumentException("Invalid nest cell");
        }
        for(int i=0;i<rs.length;i+=5) rooms.add(new Room(new Pos(rs[i],rs[i+1],rs[i+2]),rs[i+3],rs[i+4]));
        for(int i=0;i<es.length;i+=2) { if(es[i]<0 || es[i]>=rooms.size() || es[i+1]<0 || es[i+1]>=rooms.size()) throw new IllegalArgumentException("Invalid nest edge"); links.add(new Link(es[i],es[i+1])); }
        plan=new Plan(rooms,links,cells);
    }
    public Plan plan() { return plan; }
    public long layoutSeed() { return layoutSeed; }
    public long decorSeed() { return decorSeed; }
    public static BlockPos pos(Pos p) { return new BlockPos(p.x(),p.y(),p.z()); }
    public static BlockState state(int material) {
        return switch(material) {
            case BugNestLayout.AIR->Blocks.AIR.defaultBlockState();
            case BugNestLayout.SAND->SlimyContent.SAND.get().defaultBlockState();
            case BugNestLayout.EGGS->SlimyContent.EGGS.get().defaultBlockState();
            case BugNestLayout.SANDSTONE->Blocks.SANDSTONE.defaultBlockState();
            case BugNestLayout.SPAWNER->NpcSpawnerContent.BLOCK.get().defaultBlockState();
            case BugNestLayout.NORTH,BugNestLayout.EAST,BugNestLayout.SOUTH,BugNestLayout.WEST->SlimyContent.TRAIL.get().defaultBlockState()
                    .setValue(LadderBlock.FACING,switch(material) { case BugNestLayout.NORTH->Direction.NORTH; case BugNestLayout.EAST->Direction.EAST; case BugNestLayout.SOUTH->Direction.SOUTH; default->Direction.WEST; });
            default->throw new IllegalArgumentException("Unknown nest material");
        };
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        tag.putInt("PlanVersion",1); tag.putLong("LayoutSeed",layoutSeed); tag.putLong("DecorSeed",decorSeed);
        int[] cells=new int[plan.cells().size()*4]; int i=0;
        for(var e:plan.cells().entrySet()) { cells[i++]=e.getKey().x(); cells[i++]=e.getKey().y(); cells[i++]=e.getKey().z(); cells[i++]=e.getValue(); }
        tag.putIntArray("Cells",cells); int[] rooms=new int[plan.rooms().size()*5]; i=0;
        for(var r:plan.rooms()) { rooms[i++]=r.center().x(); rooms[i++]=r.center().y(); rooms[i++]=r.center().z(); rooms[i++]=r.radius(); rooms[i++]=r.type(); }
        tag.putIntArray("Rooms",rooms); int[] links=new int[plan.links().size()*2]; i=0;
        for(var e:plan.links()) { links[i++]=e.from(); links[i++]=e.to(); } tag.putIntArray("Links",links);
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        // No terrain queries or RNG here: each chunk writes only its immutable part of the plan.
        for(var cell:plan.cells().entrySet()) {
            var p=pos(cell.getKey()); if(!clip.isInside(p)) continue;
            level.setBlock(p,state(cell.getValue()),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            if(cell.getValue()==BugNestLayout.SPAWNER && level.getBlockEntity(p) instanceof NpcSpawnerBlockEntity spawner) {
                spawner.configure(7,3,150,1,0,List.of(new NpcSpawnerBlockEntity.Entry(TGContent.id("alienbug"),1)),ItemStack.EMPTY);
                // Legacy MBlockTGSpawner changes the interval but leaves the initial tile delay at 200.
                var data=spawner.saveWithoutMetadata(level.registryAccess()); data.putInt("delay",200);
                spawner.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),data));
            }
        }
    }
}
