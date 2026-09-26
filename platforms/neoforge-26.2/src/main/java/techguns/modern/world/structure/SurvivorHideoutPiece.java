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

/** Source SurvivorHideout with one saved panel roll and biome canopy across every native chunk. */
public final class SurvivorHideoutPiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("survivor_hideout");
    public static final BlockPos PIVOT=new BlockPos(5,0,9);
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private static final List<String> PANELS=List.of("metalpanel_container_red","metalpanel_container_green","metalpanel_container_blue","metalpanel_container_orange");
    private static final List<String> CANOPIES=List.of("camonet_top_wood","camonet_top_desert","camonet_top_snow");
    private final int turns,panel,canopy;
    public SurvivorHideoutPiece(StructureTemplateManager manager,BlockPos origin,int turns,int panel,int canopy) {
        super(LocationContent.SURVIVOR_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin);
        this.turns=turns; this.panel=valid(panel,4); this.canopy=valid(canopy,3); expandFoundation();
    }
    public SurvivorHideoutPiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.SURVIVOR_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0)));
        turns=tag.getIntOr("Turns",0); panel=valid(tag.getIntOr("Panel",0),4); canopy=valid(tag.getIntOr("Canopy",0),3); expandFoundation();
    }
    private static int valid(int value,int count) { if(value<0 || value>=count) throw new IllegalArgumentException("Invalid hideout variant: "+value); return value; }
    public int panel() { return panel; }
    public int canopy() { return canopy; }
    public Block panelBlock() { return BuildingContent.BLOCKS.get(PANELS.get(panel)).get(); }
    public Block canopyBlock() { return CamouflageNetContent.BLOCKS.get(CANOPIES.get(canopy)).get(); }
    private static StructurePlaceSettings settings(int turns) {
        return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true).setKnownShape(true)
                .addProcessor(new BlockIgnoreProcessor(List.of(FortificationContent.LAMPS.get("lantern_yellow").get())));
    }
    private void expandFoundation() { boundingBox.encapsulate(new BlockPos(boundingBox.minX(),templatePosition.getY()-3,boundingBox.minZ())); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); tag.putInt("Panel",panel); tag.putInt("Canopy",canopy);
    }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) { throw new IllegalArgumentException("SurvivorHideout has no markers: "+marker); }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        var all=placeSettings.copy().setBoundingBox(null);
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_ON_PLACE;
        for(var cell:template.filterBlocks(templatePosition,all,Blocks.COARSE_DIRT)) if(cell.pos().getY()==templatePosition.getY()) {
            for(int d=1;d<=7;d++) if(clip.isInside(cell.pos().above(d))) level.setBlock(cell.pos().above(d),Blocks.AIR.defaultBlockState(),flags);
            for(int d=1;d<=3;d++) { var pos=cell.pos().below(d); if(clip.isInside(pos) && level.getBlockState(pos).canBeReplaced()) level.setBlock(pos,cell.state(),flags); }
        }
        placeSettings.setBoundingBox(clip);
        if(template.placeInWorld(level,templatePosition,reference,placeSettings,random,flags)) {
            var basePanel=BuildingContent.BLOCKS.get(PANELS.getFirst()).get(); var baseCanopy=CamouflageNetContent.BLOCKS.get(CANOPIES.getFirst()).get();
            for(var cell:template.filterBlocks(templatePosition,placeSettings,basePanel)) if(clip.isInside(cell.pos())) level.setBlock(cell.pos(),panelBlock().defaultBlockState(),flags);
            for(var cell:template.filterBlocks(templatePosition,placeSettings,baseCanopy)) if(clip.isInside(cell.pos())) level.setBlock(cell.pos(),canopyBlock().defaultBlockState(),flags);
            var lantern=FortificationContent.LAMPS.get("lantern_yellow").get();
            for(var cell:template.filterBlocks(templatePosition,placeSettings,lantern)) if(clip.isInside(cell.pos())) level.setBlock(cell.pos(),cell.state(),flags);
            // Source connection shapes are live. Refresh placed neighbours across a chunk rim too;
            // native postprocessing handles the rim outside the current WorldGenRegion's write area.
            for(var block:List.of(Blocks.OAK_FENCE,FortificationContent.SANDBAGS.get(),baseCanopy,lantern)) for(var cell:template.filterBlocks(templatePosition,all,block)) {
                var pos=cell.pos();
                if(pos.getX()<clip.minX()-1 || pos.getX()>clip.maxX()+1 || pos.getZ()<clip.minZ()-1 || pos.getZ()>clip.maxZ()+1
                        || pos.getY()<clip.minY() || pos.getY()>clip.maxY() || !level.hasChunkAt(pos) || !level.ensureCanWrite(pos)) continue;
                var state=level.getBlockState(pos); if(!state.is(block==baseCanopy?canopyBlock():block)) continue;
                if(level.getChunk(pos) instanceof net.minecraft.world.level.chunk.ProtoChunk pending) pending.markPosForPostProcessing(pos);
                var connected=Block.updateFromNeighbourShapes(state,level,pos);
                if(connected!=state) level.setBlock(pos,connected,flags);
            }
        }
        expandFoundation();
    }
}
