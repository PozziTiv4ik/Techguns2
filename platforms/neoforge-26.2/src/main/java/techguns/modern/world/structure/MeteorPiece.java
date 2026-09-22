package techguns.modern.world.structure;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import techguns.core.*;
import techguns.modern.TGContent;
import techguns.modern.world.*;

/** Original 2265 cells: clear thirty above all 289 bottom cells, then base, mixtures and lamps. */
public final class MeteorPiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("orecluster_meteor_basis");
    public static final BlockPos PIVOT=new BlockPos(8,0,8);
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private static final String[] TYPES={"coal","common_metal","common_gem","rare_metal","shiny_metal","shiny_gem","uranium","nether_crystal"};
    private final int turns,clusterType;
    private final long mixtureSeed;
    public MeteorPiece(StructureTemplateManager manager,BlockPos origin,int turns,int clusterType,long mixtureSeed) {
        super(LocationContent.METEOR_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin);
        this.turns=turns; this.clusterType=clusterType; this.mixtureSeed=mixtureSeed; expandClearing();
    }
    public MeteorPiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.METEOR_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0)));
        turns=tag.getIntOr("Turns",0); clusterType=Math.clamp(tag.getIntOr("ClusterType",0),0,7); mixtureSeed=tag.getLongOr("MixtureSeed",0); expandClearing();
    }
    public int clusterType() { return clusterType; }
    public long mixtureSeed() { return mixtureSeed; }
    private static StructurePlaceSettings settings(int turns) {
        return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true).setKnownShape(true)
                .addProcessor(new BlockIgnoreProcessor(List.of(FortificationContent.LAMPS.get("lamp_white").get())));
    }
    private void expandClearing() { boundingBox.encapsulate(new BlockPos(boundingBox.minX(),templatePosition.getY()+35,boundingBox.minZ())); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); tag.putInt("ClusterType",clusterType); tag.putLong("MixtureSeed",mixtureSeed);
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        var all=placeSettings.copy().setBoundingBox(null);
        // The bottom layer contains 288 stone cells and one explicit air cell.
        for(var block:List.of(Blocks.STONE,Blocks.AIR)) for(var cell:template.filterBlocks(templatePosition,all,block))
            if(cell.pos().getY()==templatePosition.getY()) for(int depth=1;depth<=30;depth++) {
                var p=cell.pos().above(5+depth);
                if(clip.isInside(p)) level.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            }
        placeSettings.setBoundingBox(clip);
        if(template.placeInWorld(level,templatePosition,reference,placeSettings,random,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)) {
            for(var cell:template.filterBlocks(templatePosition,placeSettings,Blocks.STRUCTURE_BLOCK))
                handleDataMarker(cell.nbt().getStringOr("metadata",""),cell.pos(),level,random,clip);
            // The source's second pass places lamps after their floor, ceiling and door supports.
            for(var cell:template.filterBlocks(templatePosition,placeSettings,FortificationContent.LAMPS.get("lamp_white").get()))
                if(clip.isInside(cell.pos())) level.setBlock(cell.pos(),cell.state(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_ON_PLACE);
            // Old getActualState resolved these live at render time. In 26.2 they are saved properties.
            // Also refresh the one-block rim of an already placed neighbouring chunk: a later
            // chunk may add the southern/eastern arm or a diagonal corner to that rim.
            for(var b:List.of(FortificationContent.SANDBAGS.get(),Blocks.GLASS_PANE))
                for(var cell:template.filterBlocks(templatePosition,all,b)) {
                    var pos=cell.pos();
                    if(pos.getX()<clip.minX()-1 || pos.getX()>clip.maxX()+1 || pos.getZ()<clip.minZ()-1 || pos.getZ()>clip.maxZ()+1
                            || pos.getY()<clip.minY() || pos.getY()>clip.maxY() || !level.hasChunkAt(pos) || !level.ensureCanWrite(pos)) continue;
                    var old=level.getBlockState(cell.pos());
                    if(!old.is(b)) continue; // Another chunk has not placed this template cell yet.
                    var connected=old.getBlock() instanceof SandbagBlock bags?bags.connected(old,level,cell.pos()):Block.updateFromNeighbourShapes(old,level,cell.pos());
                    if(connected!=old) level.setBlock(cell.pos(),connected,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
                }
        }
        expandClearing();
    }
    private Block ore(RandomSource random) {
        return switch(clusterType) {
            case 0->Blocks.COAL_ORE;
            case 1->switch(MeteorRules.typeOre(random.nextInt(4),new int[]{1,1,1})) {
                case 0->Blocks.IRON_ORE; case 1->TGOreContent.ORES.get("ore_copper").get(); default->TGOreContent.ORES.get("ore_tin").get(); };
            case 2->random.nextInt(3)<=1?Blocks.REDSTONE_ORE:Blocks.LAPIS_ORE;
            case 3->TGOreContent.ORES.get("ore_lead").get();
            case 4->random.nextInt(3)<=1?Blocks.GOLD_ORE:TGOreContent.ORES.get("ore_titanium").get();
            case 5->switch(MeteorRules.typeOre(random.nextInt(7),new int[]{2,1,3})) {
                case 0->Blocks.DIAMOND_ORE; case 1->Blocks.EMERALD_ORE; default->Blocks.STONE; };
            case 6->random.nextInt(3)<=1?TGOreContent.ORES.get("ore_uranium").get():Blocks.STONE;
            default->switch(MeteorRules.typeOre(random.nextInt(5),new int[]{2,1,1})) {
                case 0->Blocks.NETHERRACK; case 1->Blocks.NETHER_QUARTZ_ORE; default->Blocks.GLOWSTONE; };
        };
    }
    public BlockState mixture(String marker,BlockPos pos) {
        var random=RandomSource.create(mixtureSeed^Mth.getSeed(pos));
        var cluster=OreClusterContent.BLOCKS.get("ore_cluster_"+TYPES[clusterType]).get();
        return switch(marker) {
            case "techguns:meteor_magma_or_stone"->(random.nextInt(3)<=1?Blocks.MAGMA_BLOCK:Blocks.STONE).defaultBlockState();
            case "techguns:meteor_cluster_ore"->(random.nextFloat()<.5f?ore(random):cluster).defaultBlockState();
            case "techguns:meteor_cluster"->cluster.defaultBlockState();
            default->throw new IllegalArgumentException("Unknown meteor marker: "+marker);
        };
    }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) {
        if(clip.isInside(pos)) level.setBlock(pos,mixture(marker,pos),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
    }
}
