package techguns.modern.world.structure;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import techguns.modern.TGContent;
import techguns.modern.world.*;

/** Source PoliceStation, including the entire seven-high clearing and three-deep replaceable foundation. */
public final class PoliceStationPiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("policestation");
    public static final BlockPos PIVOT=new BlockPos(6,0,6);
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private final int turns;
    public PoliceStationPiece(StructureTemplateManager manager,BlockPos origin,int turns) {
        super(LocationContent.POLICE_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin); this.turns=turns; expandFoundation();
    }
    public PoliceStationPiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.POLICE_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0))); turns=tag.getIntOr("Turns",0); expandFoundation();
    }
    private static StructurePlaceSettings settings(int turns) {
        return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true).setKnownShape(true)
                .addProcessor(new BlockIgnoreProcessor(List.of(FortificationContent.LAMPS.get("lamp_white").get())));
    }
    private void expandFoundation() { boundingBox.encapsulate(new BlockPos(boundingBox.minX(),templatePosition.getY()-3,boundingBox.minZ())); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) { super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) { throw new IllegalArgumentException("PoliceStation has no markers: "+marker); }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        var all=placeSettings.copy().setBoundingBox(null); var concrete=BuildingContent.BLOCKS.get("concrete_brown").get();
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_ON_PLACE;
        for(var cell:template.filterBlocks(templatePosition,all,concrete)) if(cell.pos().getY()==templatePosition.getY()) {
            for(int d=1;d<=7;d++) if(clip.isInside(cell.pos().above(d))) level.setBlock(cell.pos().above(d),Blocks.AIR.defaultBlockState(),flags);
            // Unlike the Nether helper, the source checks every depth even after two solid blocks.
            for(int d=1;d<=3;d++) { var pos=cell.pos().below(d); if(clip.isInside(pos) && level.getBlockState(pos).canBeReplaced()) level.setBlock(pos,cell.state(),flags); }
        }
        placeSettings.setBoundingBox(clip);
        if(template.placeInWorld(level,templatePosition,reference,placeSettings,random,flags)) {
            for(var cell:template.filterBlocks(templatePosition,placeSettings,FortificationContent.LAMPS.get("lamp_white").get()))
                if(clip.isInside(cell.pos())) level.setBlock(cell.pos(),cell.state(),flags);
            for(var block:List.of(FortificationContent.SANDBAGS.get(),Blocks.GLASS_PANE,Blocks.IRON_BARS)) for(var cell:template.filterBlocks(templatePosition,all,block)) {
                var pos=cell.pos();
                if(pos.getX()<clip.minX()-1 || pos.getX()>clip.maxX()+1 || pos.getZ()<clip.minZ()-1 || pos.getZ()>clip.maxZ()+1
                        || pos.getY()<clip.minY() || pos.getY()>clip.maxY() || !level.hasChunkAt(pos) || !level.ensureCanWrite(pos)) continue;
                var state=level.getBlockState(pos); if(!state.is(block)) continue;
                level.getChunk(pos).markPosForPostProcessing(pos); var connected=Block.updateFromNeighbourShapes(state,level,pos);
                if(connected!=state) level.setBlock(pos,connected,flags);
            }
        }
        expandFoundation();
    }
}
