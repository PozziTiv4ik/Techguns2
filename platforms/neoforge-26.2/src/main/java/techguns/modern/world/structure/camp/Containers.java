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

public class Containers extends CampPart{

    int containerWidth = 2;
    int containerHeight = 2;
    int containerMinLength = 2;
    BlockState containerBlock=null;

    public Containers(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int containerWidth, int containerHeight, int containerMinLength, MBlock containerBlock) {
        super(minX, minY, minZ, maxX, maxY, maxZ);
        this.containerWidth = containerWidth;
        this.containerHeight = containerHeight;
        this.containerMinLength = containerMinLength;
        this.containerBlock = containerBlock.getState();
    }

    public Containers(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, MBlock containerBlock) {
        super(minX, minY, minZ, maxX, maxY, maxZ);
        this.containerBlock = containerBlock.getState();
    }

    @Override
    public void setBlocks(CampWorld world, int posX, int posY, int posZ, int sizeX,
            int sizeY, int sizeZ, int direction, BiomeColorType colorType, Random rnd) {
        int count;
        int offset = 0;

        MutableBlockPos pos = new MutableBlockPos();
        if (sizeX > sizeZ) {
            count = (int)(((float)sizeZ+1.0f)/((float)containerWidth+1.0f));
            for (int i = 0;  i < count; i++) {
                CampTerrain.fillBlocks(world, pos.set(posX, posY+1, posZ+offset), sizeX, containerHeight, containerWidth, containerBlock);
                offset+=(containerWidth+1);
            }

        }else {
            count = (int)(((float)sizeX+1.0f)/((float)containerWidth+1.0f));
            for (int i = 0;  i < count; i++) {
                CampTerrain.fillBlocks(world, pos.set(posX+offset, posY+1, posZ), containerWidth, containerHeight, sizeZ, containerBlock);
                offset+=(containerWidth+1);
            }
        }

    }

    @Override public String key() { return "Containers"; }
}
