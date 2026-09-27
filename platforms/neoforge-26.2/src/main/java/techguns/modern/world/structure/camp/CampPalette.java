package techguns.modern.world.structure.camp;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import techguns.core.BuildingBlocks;
import techguns.modern.world.*;

/** Explicit flattening of the source palettes; unsupported metadata fails at planning time. */
public final class CampPalette {
    public record MBlock(BlockState state) { public BlockState getState() { return state; } }
    public enum Camo { WOOD,DESERT,SNOW }
    public static BlockState canopy(Camo camo) { return CamouflageNetContent.BLOCKS.get("camonet_top_"+camo.name().toLowerCase(Locale.ROOT)).get().defaultBlockState(); }
    public static BlockState lantern() { return FortificationContent.LAMPS.get("lantern_yellow").get().defaultBlockState(); }
    public static boolean isLamp(BlockState state) { return state.getBlock() instanceof IndustrialLampBlock; }
    private static BlockState building(String family,int metadata) {
        var v=BuildingBlocks.ALL.stream().filter(b->b.family().equals(family) && b.index()==metadata).findFirst().orElseThrow();
        return BuildingContent.BLOCKS.get(v.id()).get().defaultBlockState();
    }
    public static BlockState legacy(String owner,String name,int meta) {
        if(owner.equals("TGBlocks")) return switch(name) {
            case "CONCRETE"->building("concrete",meta);
            case "METAL_PANEL"->building("metalpanel",meta);
            case "LADDER_0"->building("ladder0",meta);
            case "MILITARY_CRATE"->MilitaryCrateContent.fromMetadata(meta).defaultBlockState();
            case "NEONLIGHT_BLOCK"->NeonContent.fromMetadata(meta).defaultBlockState();
            case "SANDBAGS"->FortificationContent.SANDBAGS.get().defaultBlockState();
            case "LAMP_0"->FortificationContent.LAMPS.get("lamp_yellow").get().defaultBlockState();
            case "CAMONET"->CamouflageNetContent.BLOCKS.get("camonet_wood").get().defaultBlockState();
            case "CAMONET_TOP"->canopy(Camo.WOOD);
            default->throw new IllegalArgumentException(owner+"."+name+":"+meta);
        };
        return switch(name) {
            case "GRAVEL"->Blocks.GRAVEL.defaultBlockState();
            case "GLOWSTONE"->Blocks.GLOWSTONE.defaultBlockState();
            case "IRON_BARS"->Blocks.IRON_BARS.defaultBlockState();
            case "GLASS_PANE"->Blocks.GLASS_PANE.defaultBlockState();
            case "LAVA"->Blocks.LAVA.defaultBlockState();
            case "LOG"->Blocks.OAK_LOG.defaultBlockState();
            case "PLANKS"->(switch(meta) { case 0->Blocks.OAK_PLANKS; case 1->Blocks.SPRUCE_PLANKS; case 5->Blocks.DARK_OAK_PLANKS; default->throw new IllegalArgumentException("planks "+meta); }).defaultBlockState();
            case "WOODEN_SLAB"->Blocks.SPRUCE_SLAB.defaultBlockState();
            case "OAK_STAIRS"->Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST);
            case "STONE_STAIRS"->Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST);
            case "BED"->Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING,horizontal(meta&3)).setValue(BedBlock.PART,(meta&8)==0?BedPart.FOOT:BedPart.HEAD);
            case "LADDER"->Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,switch(meta) { case 2->Direction.NORTH; case 3->Direction.SOUTH; case 4->Direction.WEST; case 5->Direction.EAST; default->throw new IllegalArgumentException("ladder "+meta); });
            case "OAK_FENCE_GATE"->Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,horizontal(meta));
            default->throw new IllegalArgumentException(owner+"."+name+":"+meta);
        };
    }
    private static Direction horizontal(int meta) { return switch(meta) { case 0->Direction.SOUTH; case 1->Direction.WEST; case 2->Direction.NORTH; case 3->Direction.EAST; default->throw new IllegalArgumentException("direction "+meta); }; }
    public static BlockState getWithFacing(BlockState state,Direction facing) {
        if(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return state.setValue(BlockStateProperties.HORIZONTAL_FACING,facing);
        if(state.hasProperty(BlockStateProperties.FACING)) return state.setValue(BlockStateProperties.FACING,facing);
        return state;
    }
    public static BlockState getRotatedHorizontal(BlockState state,int turns) {
        for(int i=0;i<turns;i++) state=state.rotate(Rotation.COUNTERCLOCKWISE_90); return state;
    }
    public static void placeDoor(CampWorld world,BlockPos pos,Direction facing,Block door,boolean right) {
        var clockwise=pos.relative(facing.getClockWise()); var counter=pos.relative(facing.getCounterClockWise());
        int a=(world.getBlockState(counter).canOcclude()?1:0)+(world.getBlockState(counter.above()).canOcclude()?1:0);
        int b=(world.getBlockState(clockwise).canOcclude()?1:0)+(world.getBlockState(clockwise.above()).canOcclude()?1:0);
        boolean leftDoor=world.getBlockState(counter).is(door)||world.getBlockState(counter.above()).is(door);
        boolean rightDoor=world.getBlockState(clockwise).is(door)||world.getBlockState(clockwise.above()).is(door);
        if((!leftDoor||rightDoor)&&b<=a) { if(rightDoor&&!leftDoor||b<a) right=false; } else right=true;
        var state=door.defaultBlockState().setValue(DoorBlock.FACING,facing).setValue(DoorBlock.HINGE,right?DoorHingeSide.RIGHT:DoorHingeSide.LEFT);
        world.setBlockState(pos,state.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER));
        world.setBlockState(pos.above(),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
    }
    private static MBlock tg(String name,int meta) { return new MBlock(legacy("TGBlocks",name,meta)); }
    private static MBlock mc(String name,int meta) { return new MBlock(legacy("Blocks",name,meta)); }
    public static final MBlock[] crates=java.util.stream.IntStream.range(0,9).mapToObj(i->tg("MILITARY_CRATE",i)).toArray(MBlock[]::new);
    public static final MBlock MILBASE_FENCE=mc("IRON_BARS",0),MILBASE_ROADBLOCK=mc("GRAVEL",0),MILBASE_ROADBLOCK_SECONDARY=tg("CONCRETE",2);
    public static final MBlock BARRACKS_WOOD_PILLAR=mc("LOG",0),BARRACKS_WOOD_WALL=mc("PLANKS",0),BARRACKS_WOOD_FLOOR=mc("PLANKS",0),
            BARRACKS_WOOD_SCAFFOLD=mc("PLANKS",5),BARRACKS_WOOD_ROOF=mc("PLANKS",1),BARRACKS_WOOD_ROOFSLAB=mc("WOODEN_SLAB",1),
            WOOD_STAIRS_OAK=mc("OAK_STAIRS",0),TGLAMP=tg("LAMP_0",0),GRAVEL=mc("GRAVEL",0),GLASS_PANE=mc("GLASS_PANE",0);
    public static final MBlock TANKS_BASE=tg("METAL_PANEL",7),TANKS_WALL=tg("METAL_PANEL",0),TANKS_BORDER=tg("METAL_PANEL",3),
            HELIPAD_GLOWBLOCK=tg("NEONLIGHT_BLOCK",4),HELIPAD_WIREFRAME=tg("METAL_PANEL",6),HELIPAD_CONCRETE=tg("CONCRETE",3),
            HELIPAD_HAZARDBLOCK=new MBlock(Blocks.CONCRETE.pick(DyeColor.YELLOW).defaultBlockState());
    private CampPalette() {}
}
