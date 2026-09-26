package techguns.modern.world.structure;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import techguns.core.TrainStationRules;
import techguns.modern.TGContent;

/** Source SmallTrainstation: sparse foundation and damage fixed independently of chunk order. */
public final class TrainStationPiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("small_trainstation");
    public static final BlockPos PIVOT=new BlockPos(5,0,6);
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private final int turns;
    private final long damageSeed;
    public TrainStationPiece(StructureTemplateManager manager,BlockPos origin,int turns,long seed) {
        super(LocationContent.TRAIN_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin); this.turns=turns; damageSeed=seed; expandBounds();
    }
    public TrainStationPiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.TRAIN_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0)));
        turns=tag.getIntOr("Turns",0); damageSeed=tag.getLongOr("DamageSeed",0); expandBounds();
    }
    public long damageSeed() { return damageSeed; }
    private static StructurePlaceSettings settings(int turns) {
        return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true).setKnownShape(true).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
    }
    private void expandBounds() {
        boundingBox.encapsulate(new BlockPos(boundingBox.minX(),templatePosition.getY()-1,boundingBox.minZ()));
        boundingBox.encapsulate(new BlockPos(boundingBox.maxX(),templatePosition.getY()+7,boundingBox.maxZ()));
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) { super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); tag.putLong("DamageSeed",damageSeed); }
    public BlockState damaged(CompoundTag marker,BlockPos pos,RegistryAccess registries) {
        var weightsTag=marker.getListOrEmpty("Weights"); var states=marker.getListOrEmpty("Variants");
        if(states.size()!=weightsTag.size()) throw new IllegalArgumentException("Train station variants/weights mismatch");
        int[] weights=new int[weightsTag.size()]; for(int i=0;i<weights.length;i++) weights[i]=weightsTag.getIntOr(i,0);
        int roll=RandomSource.create(damageSeed^Mth.getSeed(pos)).nextInt(TrainStationRules.bound(weights));
        return NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK),states.getCompoundOrEmpty(TrainStationRules.choice(roll,weights))).rotate(getRotation());
    }
    private static void put(WorldGenLevel level,BlockPos pos,BlockState state,int flags) {
        level.setBlock(pos,state,flags);
        // WorldGenRegion does not run FallingBlock.onPlace; retain real gravel physics after generation.
        if(state.is(Blocks.GRAVEL)) level.scheduleTick(pos,Blocks.GRAVEL,2);
    }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) { throw new IllegalArgumentException("Train station variants require their full marker NBT"); }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        var all=placeSettings.copy().setBoundingBox(null); var markers=template.filterBlocks(templatePosition,all,Blocks.STRUCTURE_BLOCK);
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_ON_PLACE;
        for(var block:List.of(Blocks.GRAVEL,Blocks.STONE,Blocks.STRUCTURE_BLOCK)) for(var cell:template.filterBlocks(templatePosition,all,block)) if(cell.pos().getY()==templatePosition.getY()) {
            for(int d=1;d<=7;d++) if(clip.isInside(cell.pos().above(d))) level.setBlock(cell.pos().above(d),Blocks.AIR.defaultBlockState(),flags);
            var below=cell.pos().below();
            if(clip.isInside(below) && level.getBlockState(below).canBeReplaced()) put(level,below,block==Blocks.STRUCTURE_BLOCK?damaged(cell.nbt(),below,level.registryAccess()):cell.state(),flags);
        }
        placeSettings.setBoundingBox(clip);
        if(template.placeInWorld(level,templatePosition,reference,placeSettings,random,flags)) {
            for(var cell:markers) if(clip.isInside(cell.pos())) put(level,cell.pos(),damaged(cell.nbt(),cell.pos(),level.registryAccess()),flags);
            for(var cell:template.filterBlocks(templatePosition,placeSettings,Blocks.GRAVEL)) if(clip.isInside(cell.pos())) level.scheduleTick(cell.pos(),Blocks.GRAVEL,2);
            // Resolve stair corners and fence/pane connections, including a placed neighbour across the clip rim.
            var connected=new ArrayList<StructureTemplate.StructureBlockInfo>(markers);
            for(var block:List.of(Blocks.STONE_BRICK_STAIRS,Blocks.OAK_FENCE)) connected.addAll(template.filterBlocks(templatePosition,all,block));
            for(var cell:connected) {
                var pos=cell.pos(); if(pos.getX()<clip.minX()-1 || pos.getX()>clip.maxX()+1 || pos.getZ()<clip.minZ()-1 || pos.getZ()>clip.maxZ()+1
                        || pos.getY()<clip.minY() || pos.getY()>clip.maxY() || !level.hasChunkAt(pos) || !level.ensureCanWrite(pos)) continue;
                var state=level.getBlockState(pos); if(!(state.getBlock() instanceof StairBlock) && !state.is(Blocks.OAK_FENCE) && !state.is(Blocks.GLASS_PANE)) continue;
                if(level.getChunk(pos) instanceof net.minecraft.world.level.chunk.ProtoChunk pending) pending.markPosForPostProcessing(pos);
                var updated=Block.updateFromNeighbourShapes(state,level,pos); if(updated!=state) level.setBlock(pos,updated,flags);
            }
        }
        expandBounds();
    }
}
