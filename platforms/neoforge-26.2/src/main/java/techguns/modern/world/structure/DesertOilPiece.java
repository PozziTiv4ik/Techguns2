package techguns.modern.world.structure;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import techguns.core.DesertOilRules;
import techguns.modern.TGContent;
import techguns.modern.world.*;

/** All 579 source cells. There are no y=0 cells, so the legacy cleanup does nothing; no foundation. */
public final class DesertOilPiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("desert_oil_cluster");
    public static final BlockPos PIVOT=new BlockPos(5,0,5);
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private final int turns;
    private final Identifier oil;
    private final long mixtureSeed;
    private final BlockState sourceOil;
    public DesertOilPiece(StructureTemplateManager manager,BlockPos origin,int turns,Identifier oil,long mixtureSeed) {
        super(LocationContent.DESERT_OIL_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin);
        this.turns=turns; this.oil=oil; this.mixtureSeed=mixtureSeed; sourceOil=resolve(oil);
    }
    public DesertOilPiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.DESERT_OIL_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0)));
        turns=tag.getIntOr("Turns",0); var id=Identifier.tryParse(tag.getStringOr("OilFluid","minecraft:empty"));
        oil=id==null?Identifier.parse("minecraft:empty"):id; mixtureSeed=tag.getLongOr("MixtureSeed",0); sourceOil=resolve(oil);
    }
    private static BlockState resolve(Identifier id) {
        var fluid=BuiltInRegistries.FLUID.getValue(id); return fluid==null?Blocks.AIR.defaultBlockState():fluid.defaultFluidState().createLegacyBlock();
    }
    public Identifier oil() { return oil; }
    public long mixtureSeed() { return mixtureSeed; }
    public boolean hasOil() { return !sourceOil.isAir(); }
    private static StructurePlaceSettings settings(int turns) {
        return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true)
                .setKnownShape(true).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); tag.putString("OilFluid",oil.toString()); tag.putLong("MixtureSeed",mixtureSeed);
    }
    public BlockState mixture(String marker,BlockPos pos) {
        var random=RandomSource.create(mixtureSeed^Mth.getSeed(pos)); var cluster=OreClusterContent.BLOCKS.get("ore_cluster_oil").get().defaultBlockState();
        return switch(marker) {
            case "techguns:desert_oil_rim" -> hasOil()?(DesertOilRules.rimOil(random.nextInt(3))?sourceOil:Blocks.SAND.defaultBlockState()):Blocks.MAGMA_BLOCK.defaultBlockState();
            case "techguns:desert_oil_cluster_or_oil" -> random.nextFloat()<.5f?(hasOil()?sourceOil:Blocks.SANDSTONE.defaultBlockState()):cluster;
            case "techguns:desert_oil_cluster" -> cluster;
            case "techguns:desert_oil_oil" -> hasOil()?sourceOil:Blocks.LAVA.defaultBlockState();
            default -> throw new IllegalArgumentException("Unknown desert oil marker: "+marker);
        };
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        var all=placeSettings.copy().setBoundingBox(null); placeSettings.setBoundingBox(clip);
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_ON_PLACE;
        if(template.placeInWorld(level,templatePosition,reference,placeSettings,random,flags)) {
            for(var cell:template.filterBlocks(templatePosition,placeSettings,Blocks.STRUCTURE_BLOCK)) handleDataMarker(cell.nbt().getStringOr("metadata",""),cell.pos(),level,random,clip);
            var sandbags=FortificationContent.SANDBAGS.get();
            for(var cell:template.filterBlocks(templatePosition,all,sandbags)) {
                var pos=cell.pos();
                if(pos.getX()<clip.minX()-1 || pos.getX()>clip.maxX()+1 || pos.getZ()<clip.minZ()-1 || pos.getZ()>clip.maxZ()+1
                        || pos.getY()<clip.minY() || pos.getY()>clip.maxY() || !level.hasChunkAt(pos) || !level.ensureCanWrite(pos)) continue;
                var state=level.getBlockState(pos); if(!state.is(sandbags)) continue;
                if(level.getChunk(pos) instanceof net.minecraft.world.level.chunk.ProtoChunk pending) pending.markPosForPostProcessing(pos);
                var connected=Block.updateFromNeighbourShapes(state,level,pos); if(connected!=state) level.setBlock(pos,connected,flags);
            }
        }
    }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) {
        if(!clip.isInside(pos)) return;
        var state=mixture(marker,pos); level.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_ON_PLACE);
        // WorldGenRegion does not invoke LiquidBlock.onPlace. Queue the provider's real fluid physics.
        var fluid=state.getFluidState(); if(!fluid.isEmpty()) level.scheduleTick(pos,fluid.getType(),fluid.getType().getTickDelay(level));
    }
}
