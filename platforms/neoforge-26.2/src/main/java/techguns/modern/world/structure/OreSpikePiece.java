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
import techguns.core.SpikeRules;
import techguns.modern.TGContent;
import techguns.modern.world.*;

/** Original 88-cell scan, one shared cluster type, and clearing only above the 13 bottom cells. */
public final class OreSpikePiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("orecluster_spike");
    public static final BlockPos PIVOT=new BlockPos(4,0,4);
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private static final String[] TYPES={"coal","common_metal","common_gem","rare_metal","shiny_metal","shiny_gem","uranium"};
    private final int turns,clusterType;
    private final long mixtureSeed;
    public OreSpikePiece(StructureTemplateManager manager,BlockPos origin,int turns,int clusterType,long mixtureSeed) {
        super(LocationContent.SPIKE_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin);
        this.turns=turns; this.clusterType=clusterType; this.mixtureSeed=mixtureSeed;
    }
    public OreSpikePiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.SPIKE_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0)));
        turns=tag.getIntOr("Turns",0); clusterType=Math.clamp(tag.getIntOr("ClusterType",0),0,6); mixtureSeed=tag.getLongOr("MixtureSeed",0);
    }
    public int clusterType() { return clusterType; }
    public long mixtureSeed() { return mixtureSeed; }
    private static StructurePlaceSettings settings(int turns) {
        return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true).setKnownShape(true)
                .addProcessor(new BlockIgnoreProcessor(List.of(SlimyContent.TRAIL.get())));
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); tag.putInt("ClusterType",clusterType); tag.putLong("MixtureSeed",mixtureSeed);
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        // Derive every bottom column from the source template, even if its palette cell is a marker or spawner.
        var settings=placeSettings.copy().setBoundingBox(null);
        for(var block:List.of(Blocks.STONE,Blocks.STRUCTURE_BLOCK,SlimyContent.EGGS.get(),techguns.modern.npc.spawner.NpcSpawnerContent.BLOCK.get()))
            for(var cell:template.filterBlocks(templatePosition,settings,block)) if(cell.pos().getY()==templatePosition.getY())
                for(int dy=1;dy<=6;dy++) { var pos=cell.pos().above(dy); if(clip.isInside(pos)) level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE); }
        placeSettings.setBoundingBox(clip);
        if(template.placeInWorld(level,templatePosition,reference,placeSettings,random,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)) {
            for(var cell:template.filterBlocks(templatePosition,placeSettings,Blocks.STRUCTURE_BLOCK))
                handleDataMarker(cell.nbt().getStringOr("metadata",""),cell.pos(),level,random,clip);
            // Original pass 1 places all ten ladders AFTER ore/air rolls. Two can have an air
            // support; preserve the scan until an ordinary neighbor update, just as legacy flag 2 did.
            for(var cell:template.filterBlocks(templatePosition,placeSettings,SlimyContent.TRAIL.get()))
                if(clip.isInside(cell.pos())) level.setBlock(cell.pos(),cell.state(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        }
    }
    private Block ore(RandomSource random) {
        return switch(clusterType) {
            case 0->Blocks.COAL_ORE;
            case 1->switch(SpikeRules.inclusive(random.nextInt(4),new int[]{1,1,1})) { case 0->Blocks.IRON_ORE; case 1->TGOreContent.ORES.get("ore_copper").get(); default->TGOreContent.ORES.get("ore_tin").get(); };
            case 2->random.nextInt(3)<=1?Blocks.REDSTONE_ORE:Blocks.LAPIS_ORE;
            case 3->TGOreContent.ORES.get("ore_lead").get();
            case 4->random.nextInt(3)<=1?Blocks.GOLD_ORE:TGOreContent.ORES.get("ore_titanium").get();
            case 5->switch(SpikeRules.inclusive(random.nextInt(7),new int[]{2,1,3})) { case 0->Blocks.DIAMOND_ORE; case 1->Blocks.EMERALD_ORE; default->Blocks.STONE; };
            default->random.nextInt(3)<=1?TGOreContent.ORES.get("ore_uranium").get():Blocks.STONE;
        };
    }
    public BlockState mixture(String marker,BlockPos pos) {
        var random=RandomSource.create(mixtureSeed^Mth.getSeed(pos));
        var cluster=OreClusterContent.BLOCKS.get("ore_cluster_"+TYPES[clusterType]).get();
        return switch(marker) {
            case "techguns:spike_stone_air"->(random.nextInt(3)<=1?Blocks.STONE:Blocks.AIR).defaultBlockState();
            case "techguns:spike_stone_ore"->(random.nextFloat()<.5f?Blocks.STONE:ore(random)).defaultBlockState();
            case "techguns:spike_cluster_ore"->(random.nextFloat()<.5f?ore(random):cluster).defaultBlockState();
            case "techguns:spike_cluster"->cluster.defaultBlockState();
            default->throw new IllegalArgumentException("Unknown spike marker: "+marker);
        };
    }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) {
        if(clip.isInside(pos)) level.setBlock(pos,mixture(marker,pos),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
    }
}
