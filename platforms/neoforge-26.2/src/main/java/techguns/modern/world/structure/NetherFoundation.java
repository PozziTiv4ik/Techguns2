package techguns.modern.world.structure;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Shared placeFoundationNether rules: y=0 cells, sixteen layers, two consecutive solid cells. */
final class NetherFoundation {
    static void place(StructureTemplate template,BlockPos origin,StructurePlaceSettings placement,Collection<Block> materials,WorldGenLevel level,BoundingBox clip) {
        var settings=placement.copy().setBoundingBox(null);
        for(var material:materials) for(var cell:template.filterBlocks(origin,settings,material)) {
            if(cell.pos().getY()!=origin.getY() || !clip.isInside(cell.pos())) continue;
            int solid=0;
            for(int depth=1;depth<=16;depth++) {
                var p=cell.pos().below(depth); if(p.getY()<1 || !clip.isInside(p)) break;
                if(level.getBlockState(p).canBeReplaced()) { level.setBlock(p,cell.state(),2); solid=0; }
                else if(++solid>=2) break;
            }
        }
    }
    private NetherFoundation() {}
}
