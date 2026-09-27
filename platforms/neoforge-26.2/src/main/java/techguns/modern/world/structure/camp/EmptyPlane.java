// Generated from the attributed 1.12.2 source by tools/legacy_military_camp.py.
// Edit the converter or native adapters; legacy is never part of the modern source set.
package techguns.modern.world.structure.camp;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import techguns.modern.TGContent;
import techguns.modern.world.FortificationContent;
import techguns.modern.world.structure.camp.CampPart.BiomeColorType;
import techguns.modern.world.structure.camp.CampPalette.MBlock;
import techguns.modern.world.structure.camp.CampPalette.Camo;

public class EmptyPlane extends CampPart {

    BlockState block;

    public EmptyPlane(BlockState block) {
        super (1, 1, 1, -1, -1, -1);
        this.block = block;
    }

    public EmptyPlane(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState block) {
        super(minX, minY, minZ, maxX, maxY, maxZ);
        this.block = block;
    }

    @Override
    public void setBlocks(CampWorld world, int posX, int posY, int posZ, int sizeX,
            int sizeY, int sizeZ, int direction, BiomeColorType colorType, Random rnd) {
        MutableBlockPos p = new MutableBlockPos();
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeZ; j++) {
                world.setBlockState(p.set(posX+i, posY, posZ+j), block);
            }
        }
    }

    @Override public String key() { return "EmptyPlane"; }
}
