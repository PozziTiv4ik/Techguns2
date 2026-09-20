package techguns.modern.world.structure;

import java.util.*;
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
import techguns.modern.world.NetherMetalContent;
import techguns.modern.npc.spawner.NpcSpawnerContent;

public final class NetherAltarPiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("nether_altar_small");
    public static final BlockPos PIVOT=new BlockPos(5,0,5);
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private final int turns;
    public NetherAltarPiece(StructureTemplateManager manager,BlockPos origin,int turns) {
        super(LocationContent.ALTAR_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin); this.turns=turns; expandFoundation();
    }
    public NetherAltarPiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.ALTAR_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0))); turns=tag.getIntOr("Turns",0); expandFoundation();
    }
    private static StructurePlaceSettings settings(int turns) { return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true); }
    private void expandFoundation() { boundingBox.encapsulate(new BlockPos(boundingBox.minX(),Math.max(1,templatePosition.getY()-16),boundingBox.minZ())); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) { super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) {}
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        foundation(level,clip);
        super.postProcess(level,structures,generator,random,clip,chunk,reference);
        expandFoundation();
    }
    /** Fill replaceable cells only; stop after two consecutive solids, as placeFoundationNether does. */
    private void foundation(WorldGenLevel level,BoundingBox clip) {
        var materials=new ArrayList<Block>(); NetherMetalContent.BLOCKS.values().forEach(b->materials.add(b.get()));
        materials.addAll(List.of(Blocks.AIR,Blocks.NETHER_BRICK_STAIRS,Blocks.NETHER_BRICK_FENCE,NpcSpawnerContent.BLOCK.get()));
        NetherFoundation.place(template,templatePosition,placeSettings,materials,level,clip);
    }
}
