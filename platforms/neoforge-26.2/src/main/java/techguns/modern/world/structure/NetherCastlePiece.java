package techguns.modern.world.structure;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import techguns.core.NetherCastleRules;
import techguns.modern.TGContent;
import techguns.modern.world.*;

/** 642 scanned cells, sixteen source foundation columns, four finite rifle-armed guards. */
public final class NetherCastlePiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("nether_ore_cluster_castle");
    public static final String MARKER="techguns:castle_cluster_or_air";
    public static final BlockPos PIVOT=new BlockPos(5,0,5);
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private final int turns;
    private final long mixtureSeed;
    public NetherCastlePiece(StructureTemplateManager manager,BlockPos origin,int turns,long mixtureSeed) {
        super(LocationContent.NETHER_CASTLE_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin);
        this.turns=turns; this.mixtureSeed=mixtureSeed; expandFoundation();
    }
    public NetherCastlePiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.NETHER_CASTLE_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0)));
        turns=tag.getIntOr("Turns",0); mixtureSeed=tag.getLongOr("MixtureSeed",0); expandFoundation();
    }
    public long mixtureSeed() { return mixtureSeed; }
    private static StructurePlaceSettings settings(int turns) { return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true).setKnownShape(true); }
    private void expandFoundation() { boundingBox.encapsulate(new BlockPos(boundingBox.minX(),Math.max(1,templatePosition.getY()-16),boundingBox.minZ())); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); tag.putLong("MixtureSeed",mixtureSeed);
    }
    public BlockState mixture(BlockPos pos) {
        int roll=RandomSource.create(mixtureSeed^Mth.getSeed(pos)).nextInt(101);
        return (NetherCastleRules.cluster(roll)?OreClusterContent.netherCrystal():Blocks.AIR).defaultBlockState();
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        NetherFoundation.place(template,templatePosition,placeSettings,List.of(NetherMetalContent.BLOCKS.get("nethermetal_plate_black").get()),level,clip);
        var all=placeSettings.copy().setBoundingBox(null); placeSettings.setBoundingBox(clip);
        if(template.placeInWorld(level,templatePosition,reference,placeSettings,random,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)) {
            for(var cell:template.filterBlocks(templatePosition,placeSettings,Blocks.STRUCTURE_BLOCK)) handleDataMarker(cell.nbt().getStringOr("metadata",""),cell.pos(),level,random,clip);
            // 1.12 fences resolved connection shapes live. Refresh the previously placed one-block
            // chunk rim too, so completing the next chunk also connects existing fence arms.
            for(var cell:template.filterBlocks(templatePosition,all,Blocks.NETHER_BRICK_FENCE)) {
                var pos=cell.pos();
                if(pos.getX()<clip.minX()-1 || pos.getX()>clip.maxX()+1 || pos.getZ()<clip.minZ()-1 || pos.getZ()>clip.maxZ()+1
                        || pos.getY()<clip.minY() || pos.getY()>clip.maxY() || !level.hasChunkAt(pos) || !level.ensureCanWrite(pos)) continue;
                var old=level.getBlockState(pos); if(!old.is(Blocks.NETHER_BRICK_FENCE)) continue;
                var connected=Block.updateFromNeighbourShapes(old,level,pos);
                if(connected!=old) level.setBlock(pos,connected,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            }
        }
        expandFoundation();
    }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) {
        if(!MARKER.equals(marker)) throw new IllegalArgumentException("Unknown castle marker: "+marker);
        if(clip.isInside(pos)) level.setBlock(pos,mixture(pos),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
    }
}
