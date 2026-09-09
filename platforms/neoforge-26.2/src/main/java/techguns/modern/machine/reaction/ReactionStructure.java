package techguns.modern.machine.reaction;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ReactionStructure {
    public record Part(BlockPos pos,ReactionChamberBlock.Part kind,int connector) {}
    public static List<Part> parts(BlockPos controller,Direction inward) {
        if (inward.getAxis().isVertical()) throw new IllegalArgumentException("Horizontal chamber required");
        BlockPos center=controller.relative(inward);
        List<Part> parts=new ArrayList<>(36);
        for (int y=0;y<4;y++) for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
            BlockPos pos=center.offset(x,y,z);
            var kind=pos.equals(controller) ? ReactionChamberBlock.Part.CONTROLLER : y==1 || y==2 ? ReactionChamberBlock.Part.GLASS : ReactionChamberBlock.Part.HOUSING;
            int connector=kind==ReactionChamberBlock.Part.CONTROLLER ? 0 : y==3 && x==0 && z==0 ? 3 : y==0 && Math.abs(x)+Math.abs(z)==1 ? 2 : 1;
            parts.add(new Part(pos,kind,connector));
        }
        return List.copyOf(parts);
    }
    public static VoxelShape shape(BlockPos offset) {
        int x=offset.getX(), y=offset.getY(), z=offset.getZ();
        if (Math.abs(x)>1 || Math.abs(z)>1 || y<0 || y>3 || x==0 && z==0) return Shapes.block();
        if (y==0 && (x==0 || z==0)) return Shapes.block();
        double inset=y==0 ? .25 : .45;
        return Block.box(x<0 ? inset*16 : 0,0,z<0 ? inset*16 : 0,x>0 ? (1-inset)*16 : 16,y==3 ? .65*16 : 16,z>0 ? (1-inset)*16 : 16);
    }
    private ReactionStructure() {}
}
