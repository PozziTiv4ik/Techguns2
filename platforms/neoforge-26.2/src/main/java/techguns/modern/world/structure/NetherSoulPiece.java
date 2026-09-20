package techguns.modern.world.structure;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import techguns.modern.TGContent;
import techguns.modern.world.NetherMetalContent;

public final class NetherSoulPiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("nether_soul_platform");
    public static final BlockPos PIVOT=new BlockPos(5,0,5);
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private final int turns;
    public NetherSoulPiece(StructureTemplateManager manager,BlockPos origin,int turns) {
        super(LocationContent.SOUL_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin); this.turns=turns; expandFoundation();
    }
    public NetherSoulPiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.SOUL_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0))); turns=tag.getIntOr("Turns",0); expandFoundation();
    }
    private static StructurePlaceSettings settings(int turns) {
        var skull=Blocks.SKELETON_SKULL.defaultBlockState().rotate(ROTATIONS.get((4-turns)%4));
        return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true)
                .addProcessor(new RuleProcessor(List.of(new ProcessorRule(new BlockMatchTest(Blocks.SKELETON_SKULL),AlwaysTrueTest.INSTANCE,skull))));
    }
    private void expandFoundation() { boundingBox.encapsulate(new BlockPos(boundingBox.minX(),Math.max(1,templatePosition.getY()-16),boundingBox.minZ())); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) { super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        NetherFoundation.place(template,templatePosition,placeSettings,List.of(NetherMetalContent.BLOCKS.get("nethermetal_plate_black").get()),level,clip);
        super.postProcess(level,structures,generator,random,clip,chunk,reference); expandFoundation();
    }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) {}
}
