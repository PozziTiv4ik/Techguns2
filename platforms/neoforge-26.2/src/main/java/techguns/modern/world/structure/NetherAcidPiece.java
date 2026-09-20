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
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import techguns.core.StructureRules;
import techguns.modern.TGContent;
import techguns.modern.fluid.TGFluids;

/** Scanned pool with nine fixed sources and twelve independently chosen rim cells. */
public final class NetherAcidPiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("nether_acid_hole");
    public static final String MIXTURE_MARKER="techguns:acid_or_netherrack";
    public static final BlockPos PIVOT=new BlockPos(4,0,4);
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private final int turns;
    private final long mixtureSeed;
    public NetherAcidPiece(StructureTemplateManager manager,BlockPos origin,int turns,long mixtureSeed) {
        super(LocationContent.ACID_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin);
        this.turns=turns; this.mixtureSeed=mixtureSeed; expandFoundation();
    }
    public NetherAcidPiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.ACID_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0)));
        turns=tag.getIntOr("Turns",0); mixtureSeed=tag.getLongOr("MixtureSeed",0); expandFoundation();
    }
    public long mixtureSeed() { return mixtureSeed; }
    private static StructurePlaceSettings settings(int turns) {
        return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true);
    }
    private void expandFoundation() { boundingBox.encapsulate(new BlockPos(boundingBox.minX(),Math.max(1,templatePosition.getY()-16),boundingBox.minZ())); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); tag.putLong("MixtureSeed",mixtureSeed);
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        NetherFoundation.place(template,templatePosition,placeSettings,List.of(Blocks.NETHERRACK),level,clip);
        super.postProcess(level,structures,generator,random,clip,chunk,reference);
        expandFoundation();
    }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) {
        if(!MIXTURE_MARKER.equals(marker)) throw new IllegalArgumentException("Unknown acid pool marker: "+marker);
        if(!clip.isInside(pos)) return;
        // Independent of chunk clipping, placement order and the population RNG supplied by Minecraft.
        int roll=RandomSource.create(mixtureSeed ^ Mth.getSeed(pos)).nextInt(3);
        var block=StructureRules.acidMixture(roll)?TGFluids.ACID.block.get():Blocks.NETHERRACK;
        level.setBlock(pos,block.defaultBlockState(),2);
    }
}
