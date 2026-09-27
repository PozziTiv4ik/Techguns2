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

public class Tanks extends CampPart {
    protected int tankWidth;
    protected MBlock baseBlock = CampPalette.TANKS_BASE;
    protected MBlock wallBlock = CampPalette.TANKS_WALL;
    protected MBlock borderBlock = CampPalette.TANKS_BORDER;
    protected MBlock fillBlock = new MBlock(CampPalette.legacy("Blocks", "LAVA", 0));

    public Tanks(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        super(minX, minY, minZ, maxX, maxY, maxZ);
        tankWidth=4;
    }

    @Override
    public void setBlocks(CampWorld world, int posX, int posY, int posZ, int sizeX,
            int sizeY, int sizeZ, int direction, BiomeColorType colorType, Random rnd) {

        MutableBlockPos p = new MutableBlockPos();

        int count;
        int offset = 0;
        if (sizeX > sizeZ) {
            count = (int)(((float)sizeZ+1.0f)/((float)tankWidth+1.0f));
            for (int i = 0;  i < count; i++) {

                world.setBlockState(p.set(posX+1, posY+1, posZ+offset), baseBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-2, posY+1, posZ+offset), baseBlock.getState(),2);
                world.setBlockState(p.set(posX+1, posY+2, posZ+offset), baseBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-2, posY+2, posZ+offset), baseBlock.getState(),2);
                for (int ix=0;ix<sizeX-2;ix++){
                    world.setBlockState(p.set(posX+1+ix, posY+1, posZ+offset+1), baseBlock.getState(),2);
                    world.setBlockState(p.set(posX+1+ix, posY+1, posZ+offset+2), baseBlock.getState(),2);

                }
                world.setBlockState(p.set(posX+1, posY+1, posZ+offset+tankWidth-1), baseBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-2, posY+1, posZ+offset+tankWidth-1), baseBlock.getState(),2);
                world.setBlockState(p.set(posX+1, posY+2, posZ+offset+tankWidth-1), baseBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-2, posY+2, posZ+offset+tankWidth-1), baseBlock.getState(),2);

                for (int ix=0;ix<sizeX-2;ix++){
                    world.setBlockState(p.set(posX+1+ix, posY+2, posZ+offset+1), wallBlock.getState(),2);
                    world.setBlockState(p.set(posX+1+ix, posY+2, posZ+offset+2), wallBlock.getState(),2);
                }
                world.setBlockState(p.set(posX, posY+2, posZ+offset+1), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-1, posY+2, posZ+offset+1), borderBlock.getState(),2);
                world.setBlockState(p.set(posX, posY+2, posZ+offset+2), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-1, posY+2, posZ+offset+2), borderBlock.getState(),2);

                for (int ix=0;ix<sizeX-2;ix++){
                    world.setBlockState(p.set(posX+1+ix, posY+3, posZ+offset), wallBlock.getState(),2);
                    world.setBlockState(p.set(posX+1+ix, posY+3, posZ+offset+3), wallBlock.getState(),2);
                }
                world.setBlockState(p.set(posX, posY+3, posZ+offset), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-1, posY+3, posZ+offset), borderBlock.getState(),2);
                world.setBlockState(p.set(posX, posY+3, posZ+offset+3), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-1, posY+3, posZ+offset+3), borderBlock.getState(),2);

                world.setBlockState(p.set(posX, posY+3, posZ+offset+1), wallBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-1, posY+3, posZ+offset+1), wallBlock.getState(),2);
                world.setBlockState(p.set(posX, posY+3, posZ+offset+2), wallBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-1, posY+3, posZ+offset+2), wallBlock.getState(),2);

                for (int ix=0;ix<sizeX-2;ix++){
                    world.setBlockState(p.set(posX+1+ix, posY+4, posZ+offset), wallBlock.getState(),2);
                    world.setBlockState(p.set(posX+1+ix, posY+4, posZ+offset+3), wallBlock.getState(),2);
                }
                world.setBlockState(p.set(posX, posY+4, posZ+offset), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-1, posY+4, posZ+offset), borderBlock.getState(),2);
                world.setBlockState(p.set(posX, posY+4, posZ+offset+3), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-1, posY+4, posZ+offset+3), borderBlock.getState(),2);

                world.setBlockState(p.set(posX, posY+4, posZ+offset+1), wallBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-1, posY+4, posZ+offset+1), wallBlock.getState(),2);
                world.setBlockState(p.set(posX, posY+4, posZ+offset+2), wallBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-1, posY+4, posZ+offset+2), wallBlock.getState(),2);

                for (int ix=0;ix<sizeX-2;ix++){
                    world.setBlockState(p.set(posX+1+ix, posY+3, posZ+offset+1), fillBlock.getState(),2);
                    world.setBlockState(p.set(posX+1+ix, posY+3, posZ+offset+2), fillBlock.getState(),2);
                    world.setBlockState(p.set(posX+1+ix, posY+4, posZ+offset+1), fillBlock.getState(),2);
                    world.setBlockState(p.set(posX+1+ix, posY+4, posZ+offset+2), fillBlock.getState(),2);
                }

                for (int ix=0;ix<sizeX-2;ix++){
                    world.setBlockState(p.set(posX+1+ix, posY+5, posZ+offset+1), wallBlock.getState(),2);
                    world.setBlockState(p.set(posX+1+ix, posY+5, posZ+offset+2), wallBlock.getState(),2);
                }
                world.setBlockState(p.set(posX, posY+5, posZ+offset+1), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-1, posY+5, posZ+offset+1), borderBlock.getState(),2);
                world.setBlockState(p.set(posX, posY+5, posZ+offset+2), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+sizeX-1, posY+5, posZ+offset+2), borderBlock.getState(),2);

                offset+=(tankWidth+1);
            }

        }else {
            count = (int)(((float)sizeX+1.0f)/((float)tankWidth+1.0f));
            for (int i = 0;  i < count; i++) {

                world.setBlockState(p.set(posX+offset, posY+1, posZ+1), baseBlock.getState(),2);
                world.setBlockState(p.set(posX+offset, posY+1, posZ+sizeZ-2), baseBlock.getState(),2);
                world.setBlockState(p.set(posX+offset, posY+2, posZ+1), baseBlock.getState(),2);
                world.setBlockState(p.set(posX+offset, posY+2, posZ+sizeZ-2), baseBlock.getState(),2);
                for (int iz=0;iz<sizeZ-2;iz++){
                    world.setBlockState(p.set(posX+offset+1, posY+1, posZ+1+iz), baseBlock.getState(),2);
                    world.setBlockState(p.set(posX+offset+2, posY+1, posZ+1+iz), baseBlock.getState(),2);

                }
                world.setBlockState(p.set(posX+offset+tankWidth-1, posY+1, posZ+1), baseBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+tankWidth-1, posY+1, posZ+sizeZ-2), baseBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+tankWidth-1, posY+2, posZ+1), baseBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+tankWidth-1, posY+2, posZ+sizeZ-2), baseBlock.getState(),2);

                for (int iz=0;iz<sizeZ-2;iz++){
                    world.setBlockState(p.set(posX+offset+1, posY+2, posZ+iz+1), wallBlock.getState(),2);
                    world.setBlockState(p.set(posX+offset+2, posY+2, posZ+iz+1), wallBlock.getState(),2);
                }
                world.setBlockState(p.set(posX+offset+1, posY+2, posZ), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+1, posY+2, posZ+sizeZ-1), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+2, posY+2, posZ), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+2, posY+2, posZ+sizeZ-1), borderBlock.getState(),2);

                for (int iz=0;iz<sizeZ-2;iz++){
                    world.setBlockState(p.set(posX+offset, posY+3, posZ+1+iz), wallBlock.getState(),2);
                    world.setBlockState(p.set(posX+offset+3, posY+3, posZ+1+iz), wallBlock.getState(),2);
                }
                world.setBlockState(p.set(posX+offset, posY+3, posZ), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+offset, posY+3, posZ+sizeZ-1), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+3, posY+3, posZ), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+3, posY+3, posZ+sizeZ-1), borderBlock.getState(),2);

                world.setBlockState(p.set(posX+offset+1, posY+3, posZ), wallBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+1, posY+3, posZ+sizeZ-1), wallBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+2, posY+3, posZ), wallBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+2, posY+3, posZ+sizeZ-1), wallBlock.getState(),2);

                for (int iz=0;iz<sizeZ-2;iz++){
                    world.setBlockState(p.set(posX+offset, posY+4, posZ+1+iz), wallBlock.getState(),2);
                    world.setBlockState(p.set(posX+offset+3, posY+4, posZ+1+iz), wallBlock.getState(),2);
                }
                world.setBlockState(p.set(posX+offset, posY+4, posZ), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+offset, posY+4, posZ+sizeZ-1), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+3, posY+4, posZ), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+3, posY+4, posZ+sizeZ-1), borderBlock.getState(),2);

                world.setBlockState(p.set(posX+offset+1, posY+4, posZ), wallBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+1, posY+4, posZ+sizeZ-1), wallBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+2, posY+4, posZ), wallBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+2, posY+4, posZ+sizeZ-1), wallBlock.getState(),2);

                for (int iz=0;iz<sizeZ-2;iz++){
                    world.setBlockState(p.set(posX+offset+1, posY+3, posZ+1+iz), fillBlock.getState(),2);
                    world.setBlockState(p.set(posX+offset+2, posY+3, posZ+1+iz), fillBlock.getState(),2);
                    world.setBlockState(p.set(posX+offset+1, posY+4, posZ+1+iz), fillBlock.getState(),2);
                    world.setBlockState(p.set(posX+offset+2, posY+4, posZ+1+iz), fillBlock.getState(),2);
                }

                for (int iz=0;iz<sizeZ-2;iz++){
                    world.setBlockState(p.set(posX+offset+1, posY+5, posZ+1+iz), wallBlock.getState(),2);
                    world.setBlockState(p.set(posX+offset+2, posY+5, posZ+1+iz), wallBlock.getState(),2);
                }
                world.setBlockState(p.set(posX+offset+1, posY+5, posZ), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+1, posY+5, posZ+sizeZ-1), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+2, posY+5, posZ), borderBlock.getState(),2);
                world.setBlockState(p.set(posX+offset+2, posY+5, posZ+sizeZ-1), borderBlock.getState(),2);

                offset+=(tankWidth+1);
            }
        }

    }

    @Override public String key() { return "Tanks"; }
}
