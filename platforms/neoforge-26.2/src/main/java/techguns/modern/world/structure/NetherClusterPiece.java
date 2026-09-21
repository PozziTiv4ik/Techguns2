package techguns.modern.world.structure;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import techguns.core.StructureRules;
import techguns.modern.TGContent;
import techguns.modern.world.OreClusterContent;

/** All 27 original cells plus a depth-two foundation with independently weighted centre cells. */
public final class NetherClusterPiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("nether_ore_cluster_small");
    public static final String ROCK_MARKER="techguns:cluster_or_netherrack", AIR_MARKER="techguns:cluster_or_air";
    public static final BlockPos PIVOT=new BlockPos(1,0,1);
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private final int turns;
    private final long mixtureSeed;
    public NetherClusterPiece(StructureTemplateManager manager,BlockPos origin,int turns,long mixtureSeed) {
        super(LocationContent.CLUSTER_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin);
        this.turns=turns; this.mixtureSeed=mixtureSeed; expandFoundation();
    }
    public NetherClusterPiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.CLUSTER_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0)));
        turns=tag.getIntOr("Turns",0); mixtureSeed=tag.getLongOr("MixtureSeed",0); expandFoundation();
    }
    public long mixtureSeed() { return mixtureSeed; }
    private static StructurePlaceSettings settings(int turns) { return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true); }
    private void expandFoundation() { boundingBox.encapsulate(new BlockPos(boundingBox.minX(),Math.max(1,templatePosition.getY()-2),boundingBox.minZ())); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); tag.putLong("MixtureSeed",mixtureSeed);
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        NetherFoundation.place(template,templatePosition,placeSettings,List.of(Blocks.MAGMA_BLOCK,Blocks.STRUCTURE_BLOCK),level,clip,2,
                (cell,pos)->cell.state().is(Blocks.STRUCTURE_BLOCK)?mixture(cell.nbt().getStringOr("metadata",""),pos):cell.state());
        super.postProcess(level,structures,generator,random,clip,chunk,reference); expandFoundation();
    }
    private BlockState mixture(String marker,BlockPos pos) {
        Block other=switch(marker) { case ROCK_MARKER->Blocks.NETHERRACK; case AIR_MARKER->Blocks.AIR; default->throw new IllegalArgumentException("Unknown cluster marker: "+marker); };
        // Absolute position also gives separate rolls for the two foundation layers, independent of clipping.
        int roll=RandomSource.create(mixtureSeed ^ Mth.getSeed(pos)).nextInt(101);
        return (StructureRules.clusterMixture(roll)?OreClusterContent.netherCrystal():other).defaultBlockState();
    }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) {
        if(clip.isInside(pos)) level.setBlock(pos,mixture(marker,pos),2);
    }
}
