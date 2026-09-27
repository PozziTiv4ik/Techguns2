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

public class Tent extends CampPart {

    int type;
    MBlock roofBlock = new MBlock(CampPalette.legacy("TGBlocks", "CAMONET_TOP", 0));
    MBlock wallBlock = new MBlock(CampPalette.legacy("TGBlocks", "CAMONET", 0));
    MBlock groundBlock = new MBlock(CampPalette.legacy("Blocks", "GRAVEL", 0));

    BlockState roofState = roofBlock.getState();
    BlockState wallState = wallBlock.getState();
    BlockState groundState = groundBlock.getState();

    public Tent(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int type) {
        super(minX, minY, minZ, maxX, maxY, maxZ);
        this.type = type;
    }

    @Override
    public void setBlocks(CampWorld world, int posX, int posY, int posZ, int sizeX,
            int sizeY, int sizeZ, int direction, BiomeColorType colorType, Random rnd) {

        if (type == 0) {

            posX++;
            posZ++;
            sizeX-=2;
            sizeZ-=2;

        }

        int camoMeta;
        switch (colorType) {
        case DESERT:
            camoMeta = 2;
            break;
        case SNOW:
            camoMeta = 1;
            break;
        case WOODLAND:
        default:
            camoMeta = 0;
            break;
        }

        MutableBlockPos p = new MutableBlockPos();

        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeZ; j++) {
                for (int k = 0; k < sizeY; k++) {
                    p.set(posX+i,posY+k,posZ+j);
                    if (k==0) {
                        world.setBlockState(p, groundState, 2);
                    }else if (k<sizeY-1) {
                        if (j == 0 || j == sizeZ-1 || i == 0 || i == sizeX-1) {
                            world.setBlockState(p, wallState, 2);
                        }
                    }else {
                        world.setBlockState(p, roofState, 2);
                    }
                }
            }
        }

        if (type == 0 || type == 1) {

            switch (direction) {
                case 0:
                    world.setBlockToAir(p.set(posX, posY+1, posZ+(sizeZ/2)));
                    world.setBlockToAir(p.set(posX, posY+2, posZ+(sizeZ/2)));
                    if (type==0) world.setBlockState(p.set(posX-1, posY, posZ+(sizeZ/2)), groundState, 2);
                    break;
                case 1:
                    world.setBlockToAir(p.set(posX+(sizeX/2), posY+1, posZ));
                    world.setBlockToAir(p.set(posX+(sizeX/2), posY+2, posZ));
                    if (type==0) world.setBlockState(p.set(posX+(sizeX/2), posY, posZ-1), groundState, 2);
                    break;
                case 2:
                    world.setBlockToAir(p.set(posX+sizeX-1, posY+1, posZ+(sizeZ/2)));
                    world.setBlockToAir(p.set(posX+sizeX-1, posY+2, posZ+(sizeZ/2)));
                    if (type==0) world.setBlockState(p.set(posX+sizeX, posY, posZ+(sizeZ/2)), groundState, 2);
                    break;
                case 3:
                    world.setBlockToAir(p.set(posX+(sizeX/2), posY+1, posZ+sizeZ-1));
                    world.setBlockToAir(p.set(posX+(sizeX/2), posY+2, posZ+sizeZ-1));
                    if (type==0) world.setBlockState(p.set(posX+(sizeX/2), posY, posZ+sizeZ), groundState, 2);
                    break;
            }
        }

        if (type == 2) {

            for (int y = 1; y < sizeY-1; y++) {
                if (direction == 0 || direction == 2) {
                    for (int z = 1; z < sizeZ-1; z++) {
                        if (z % 3 != 0) {
                            int x = (direction==0) ? 0 : sizeX-1;
                            world.setBlockToAir(p.set(posX+x, posY+y, posZ+z));
                        }
                    }
                }else {
                    for (int x = 1; x < sizeX-1; x++) {
                        if (x % 3 != 1) {
                            int z = (direction==1) ? 0 : sizeZ-1;
                            world.setBlockToAir(p.set(posX+x, posY+y, posZ+z));
                        }
                    }
                }
            }
        }
    }

    @Override public String key() { return "Tent" + ":" + type; }
}
