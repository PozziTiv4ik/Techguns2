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

/** Original 738-cell cage, vanilla Ghastling spawner, deferred factory chest and 13 supports. */
public final class NetherGhastPiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("nether_ghast_spawner");
    public static final BlockPos PIVOT=new BlockPos(5,0,5);
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private final int turns;
    public NetherGhastPiece(StructureTemplateManager manager,BlockPos origin,int turns) {
        super(LocationContent.GHAST_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin);
        this.turns=turns; expandFoundation();
    }
    public NetherGhastPiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.GHAST_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0)));
        turns=tag.getIntOr("Turns",0); expandFoundation();
    }
    private static StructurePlaceSettings settings(int turns) {
        // Legacy skull yaw was in tile NBT and stayed zero under every structure rotation.
        var skull=Blocks.SKELETON_SKULL.defaultBlockState().rotate(ROTATIONS.get((4-turns)%4));
        return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true).setKnownShape(true)
                .addProcessor(new RuleProcessor(List.of(new ProcessorRule(new BlockMatchTest(Blocks.SKELETON_SKULL),AlwaysTrueTest.INSTANCE,skull))));
    }
    private void expandFoundation() { boundingBox.encapsulate(new BlockPos(boundingBox.minX(),Math.max(1,templatePosition.getY()-16),boundingBox.minZ())); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) { super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) { throw new IllegalArgumentException("Ghast cage has no source markers: "+marker); }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        NetherFoundation.place(template,templatePosition,placeSettings,List.of(Blocks.NETHERRACK),level,clip);
        var all=placeSettings.copy().setBoundingBox(null); placeSettings.setBoundingBox(clip);
        if(template.placeInWorld(level,templatePosition,reference,placeSettings,random,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)) {
            for(var cell:template.filterBlocks(templatePosition,all,Blocks.IRON_BARS)) {
                var pos=cell.pos();
                if(pos.getX()<clip.minX()-1 || pos.getX()>clip.maxX()+1 || pos.getZ()<clip.minZ()-1 || pos.getZ()>clip.maxZ()+1
                        || pos.getY()<clip.minY() || pos.getY()>clip.maxY() || !level.hasChunkAt(pos) || !level.ensureCanWrite(pos)) continue;
                var state=level.getBlockState(pos); if(!state.is(Blocks.IRON_BARS)) continue;
                level.getChunk(pos).markPosForPostProcessing(pos);
                var connected=Block.updateFromNeighbourShapes(state,level,pos);
                if(connected!=state) level.setBlock(pos,connected,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            }
        }
        expandFoundation();
    }
}
