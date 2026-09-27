package techguns.modern.world.structure;

import java.util.*;
import net.minecraft.core.*;
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
import techguns.core.TrainStationRules;
import techguns.modern.TGContent;
import techguns.modern.npc.spawner.NpcSpawnerContent;
import techguns.modern.world.*;

/** The 972-cell mine starts five below the surface; only its sole y=0 column clears above it. */
public final class SmallMinePiece extends TemplateStructurePiece {
    public static final Identifier TEMPLATE=TGContent.id("small_mine");
    public static final BlockPos PIVOT=new BlockPos(8,0,5);
    public static final List<String> TYPES=List.of("coal","common_metal","common_gem","rare_metal","shiny_metal","shiny_gem","uranium");
    private static final List<Rotation> ROTATIONS=List.of(Rotation.NONE,Rotation.COUNTERCLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.CLOCKWISE_90);
    private static final List<Block> LATE=List.of(Blocks.RAIL,Blocks.WALL_TORCH);
    private final int turns,clusterType,cover;
    private final long mixtureSeed;
    private volatile Set<BlockPos> decorationCells;
    public SmallMinePiece(StructureTemplateManager manager,BlockPos origin,int turns,int clusterType,int cover,long seed) {
        super(LocationContent.MINE_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(turns),origin);
        this.turns=turns; this.clusterType=clusterType; this.cover=cover; mixtureSeed=seed;
    }
    public SmallMinePiece(StructureTemplateManager manager,CompoundTag tag) {
        super(LocationContent.MINE_PIECE.get(),tag,manager,ignored->settings(tag.getIntOr("Turns",0)));
        turns=tag.getIntOr("Turns",0); clusterType=Math.clamp(tag.getIntOr("ClusterType",0),0,6);
        cover=Math.clamp(tag.getIntOr("Cover",0),0,3); mixtureSeed=tag.getLongOr("MixtureSeed",0);
    }
    public int clusterType() { return clusterType; }
    public int cover() { return cover; }
    public long mixtureSeed() { return mixtureSeed; }
    public boolean protectsDecoration(BlockPos pos) {
        if(!boundingBox.isInside(pos)) return false;
        var cached=decorationCells;
        if(cached==null) {
            var positions=new HashSet<BlockPos>(); var all=placeSettings.copy().setBoundingBox(null);
            for(var b:List.of(Blocks.AIR,Blocks.STONE,Blocks.OAK_LOG,Blocks.STRUCTURE_BLOCK,Blocks.RAIL,Blocks.COBBLESTONE_STAIRS,Blocks.WALL_TORCH,NpcSpawnerContent.BLOCK.get()))
                for(var c:template.filterBlocks(templatePosition,all,b)) {
                    positions.add(c.pos()); if(c.pos().getY()==templatePosition.getY()) for(int d=6;d<=9;d++) positions.add(c.pos().above(d));
                }
            decorationCells=cached=Set.copyOf(positions);
        }
        return cached.contains(pos);
    }
    private static StructurePlaceSettings settings(int turns) {
        return new StructurePlaceSettings().setRotation(ROTATIONS.get(turns)).setRotationPivot(PIVOT).setIgnoreEntities(true)
                .setKnownShape(true).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING).addProcessor(new BlockIgnoreProcessor(LATE));
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        super.addAdditionalSaveData(context,tag); tag.putInt("Turns",turns); tag.putInt("ClusterType",clusterType); tag.putInt("Cover",cover); tag.putLong("MixtureSeed",mixtureSeed);
    }
    private Block ore(RandomSource random) {
        return switch(clusterType) {
            case 0->Blocks.COAL_ORE;
            case 1->switch(TrainStationRules.choice(random.nextInt(4),1,1,1)) {
                case 0->Blocks.IRON_ORE; case 1->TGOreContent.ORES.get("ore_copper").get(); default->TGOreContent.ORES.get("ore_tin").get(); };
            case 2->random.nextInt(3)<=1?Blocks.REDSTONE_ORE:Blocks.LAPIS_ORE;
            case 3->TGOreContent.ORES.get("ore_lead").get();
            case 4->random.nextInt(3)<=1?Blocks.GOLD_ORE:TGOreContent.ORES.get("ore_titanium").get();
            case 5->switch(TrainStationRules.choice(random.nextInt(7),2,1,3)) {
                case 0->Blocks.DIAMOND_ORE; case 1->Blocks.EMERALD_ORE; default->Blocks.STONE; };
            default->random.nextInt(3)<=1?TGOreContent.ORES.get("ore_uranium").get():Blocks.STONE;
        };
    }
    public BlockState mixture(String marker,BlockPos pos) {
        var random=RandomSource.create(mixtureSeed^Mth.getSeed(pos));
        var cluster=OreClusterContent.BLOCKS.get("ore_cluster_"+TYPES.get(clusterType)).get();
        return switch(marker) {
            case "techguns:mine_cover"->List.of(Blocks.GRASS_BLOCK,Blocks.SNOW_BLOCK,Blocks.SAND,Blocks.NETHERRACK).get(cover).defaultBlockState();
            case "techguns:mine_patch"->(random.nextInt(6)<=4?List.of(Blocks.GRASS_BLOCK,Blocks.GRASS_BLOCK,Blocks.SAND,Blocks.NETHERRACK).get(cover):Blocks.AIR).defaultBlockState();
            case "techguns:mine_stone_ore"->(random.nextFloat()<.75f?Blocks.STONE:ore(random)).defaultBlockState();
            case "techguns:mine_cluster_ore"->(random.nextFloat()<.5f?ore(random):cluster).defaultBlockState();
            case "techguns:mine_cluster"->cluster.defaultBlockState();
            default->throw new IllegalArgumentException("Unknown SmallMine marker: "+marker);
        };
    }
    @Override protected void handleDataMarker(String marker,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox clip) {
        if(!clip.isInside(pos)) return; var state=mixture(marker,pos);
        level.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_ON_PLACE);
        if(state.is(Blocks.SAND)) level.scheduleTick(pos,Blocks.SAND,2);
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos reference) {
        var all=placeSettings.copy().setBoundingBox(null); var markers=template.filterBlocks(templatePosition,all,Blocks.STRUCTURE_BLOCK);
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_ON_PLACE;
        // The source has exactly one bottom cell, a mixed cluster at (12,0,5). No foundation.
        for(var cell:markers) if(cell.pos().getY()==templatePosition.getY()) for(int d=1;d<=4;d++) {
            var pos=cell.pos().above(5+d); if(clip.isInside(pos)) level.setBlock(pos,Blocks.AIR.defaultBlockState(),flags);
        }
        placeSettings.setBoundingBox(clip);
        if(template.placeInWorld(level,templatePosition,reference,placeSettings,random,flags)) {
            for(var cell:markers) handleDataMarker(cell.nbt().getStringOr("metadata",""),cell.pos(),level,random,clip);
            // Legacy pass one: attach rails/torches only after the solid and mixed supports exist.
            for(var block:LATE) for(var cell:template.filterBlocks(templatePosition,placeSettings,block))
                if(clip.isInside(cell.pos())) level.setBlock(cell.pos(),cell.state(),flags);
        }
    }
}
