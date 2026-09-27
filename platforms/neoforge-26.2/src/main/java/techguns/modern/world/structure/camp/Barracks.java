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

public class Barracks extends CampPart {

    private static final Identifier CHEST_LOOT = TGContent.id("chests/militarybase_barracks");

    MBlock b_pillar = CampPalette.BARRACKS_WOOD_PILLAR;
    MBlock b_wall = CampPalette.BARRACKS_WOOD_WALL;
    MBlock b_floor = CampPalette.BARRACKS_WOOD_FLOOR;
    MBlock b_scaffold = CampPalette.BARRACKS_WOOD_SCAFFOLD;
    MBlock b_roof = CampPalette.BARRACKS_WOOD_ROOF;
    MBlock b_roofSlab = CampPalette.BARRACKS_WOOD_ROOFSLAB;
    MBlock b_stairs = CampPalette.WOOD_STAIRS_OAK;
    MBlock b_torch = CampPalette.TGLAMP;
    MBlock b_ground = CampPalette.GRAVEL;
    MBlock b_window = CampPalette.GLASS_PANE;

    public Barracks(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        super(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public void setBlocks(CampWorld world, int posX, int posY, int posZ, int sizeX,
            int sizeY, int sizeZ, int direction, BiomeColorType colorType, Random rnd) {

        MutableBlockPos p = new MutableBlockPos();

        Direction facing = directionToFacing(direction);

        switch (direction) {
        case 0:
            posX+=2;
            sizeX-=2;
            break;
        case 1:
            posZ+=2;
            sizeZ-=2;
            break;
        case 2:
            sizeX-=2;
            break;
        case 3:
        default:
            sizeZ-=2;
            break;
        }

        sizeX-= ((sizeX-1) % 4);
        sizeZ-= ((sizeZ-1) % 4);

        sizeY = 7;

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int y = 0; y < sizeY; y++) {
                    if (y ==0) {
                        world.setBlockState(p.set(posX+x, posY+y, posZ+z), b_ground.getState(), 2);
                    }else if (y== 1) {
                        if (x % 4 == 0 && z % 4 == 0) {
                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), b_pillar.getState(), 2);
                        }
                    }else if( y==2) {
                        if (x==0 || z==0 || x== sizeX-1 || z == sizeZ-1) {
                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), b_scaffold.getState(), 2);
                        }else {
                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), b_floor.getState(), 2);
                        }
                    }else if (y <= 4){
                        if (x==0 || z==0 || x== sizeX-1 || z == sizeZ-1) {
                            if (x % 4 == 0 && z % 4 == 0) {
                                world.setBlockState(p.set(posX+x, posY+y, posZ+z), b_scaffold.getState(), 2);
                            }else {
                                if (y==4 && (x % 4 ==2 || z % 4 == 2)) {
                                    world.setBlockState(p.set(posX+x, posY+y, posZ+z), b_window.getState(), 2);
                                }else{
                                    world.setBlockState(p.set(posX+x, posY+y, posZ+z), b_wall.getState(), 2);
                                }
                            }
                        }
                    }else if (y == 5) {
                        if (x==0 || z==0 || x== sizeX-1 || z == sizeZ-1) {
                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), b_scaffold.getState(), 2);
                        }
                    }else if (y == 6) {
                        if (x==0 || z==0 || x== sizeX-1 || z == sizeZ-1) {
                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), b_roofSlab.getState(), 2);
                        }else {
                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), b_roof.getState(), 2);
                        }
                    }
                }
            }
        }

        int xoffset, zoffset;
        int dx, dz;

        switch (direction) {
            case 0:
                xoffset = 0; zoffset=((sizeZ/8)*4)+2;
                dx = -1; dz =0;
                break;
            case 1:
                xoffset = ((sizeX/8)*4)+2; zoffset=0;
                dx = 0; dz = -1;
                break;
            case 2:
                xoffset = sizeX-1; zoffset=((sizeZ/8)*4)+2;
                dx = 1; dz=0;
                break;
            case 3:
            default:
                xoffset = ((sizeX/8)*4)+2; zoffset=sizeZ-1;
                dx = 0; dz=1;
                break;
        }

        world.setBlockToAir(p.set(posX+xoffset, posY+4, posZ+zoffset));
        world.setBlockToAir(p.set(posX+xoffset, posY+3, posZ+zoffset));
        CampPalette.placeDoor(world, p.set(posX+xoffset, posY+3, posZ+zoffset), facing.getOpposite(), Blocks.OAK_DOOR, false);

        world.setBlockState(p.set(posX+xoffset+dx, posY+5, posZ+zoffset+dz), CampPalette.getWithFacing(b_torch.getState(),facing.getOpposite()), 2);

        world.setBlockState(p.set(posX+xoffset+dx, posY+2, posZ+zoffset+dz), CampPalette.getWithFacing(b_stairs.getState(),facing.getOpposite()), 2);
        world.setBlockState(p.set(posX+xoffset+dx*2, posY+1, posZ+zoffset+dz*2), CampPalette.getWithFacing(b_stairs.getState(),facing.getOpposite()), 2);

        int xHalf = sizeX/2;
        int zHalf = sizeZ/2;

        for (int x = 1; x < sizeX-1; x++) {
            for (int z = 1; z < sizeZ-1; z++) {
                for (int y = 3; y < 6; y++) {
                    if (x==1 || z==1 || x== sizeX-2 || z == sizeZ-2) {
                        if (y==5 && (x % 4 ==2 || z % 4 == 2)) {
                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), CampPalette.getWithFacing(b_torch.getState(),Direction.UP), 2);
                        }
                        if (y == 3 && (x % 4 == 0 || z % 4 == 0) && x!= xoffset && z!= zoffset) {

                            Direction chestrot = Direction.EAST;
                            if (z % 4==0) {
                                if(x>xHalf) {
                                    chestrot =chestrot.getOpposite();
                                }
                            } else if(x%4==0) {
                                chestrot=Direction.SOUTH;
                                if(z>zHalf) {
                                    chestrot =chestrot.getOpposite();
                                }
                            }
                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), CampPalette.getWithFacing(Blocks.CHEST.defaultBlockState(),chestrot), 2);

                            world.loot(p, CHEST_LOOT, world.rand.nextLong());

                        }
                    }else{
                        if (x==2 || z == 2 || x== sizeX-3 || z== sizeZ-3) {

                            if (sizeX / 4 > 1 || sizeZ / 4 > 1) {
                                if (y==3 && (x % 4 ==2 && z % 4 == 2) && x!= xoffset && z!= zoffset) {
                                    if (x==2) {
                                        world.setBlockState(p.set(posX+x, posY+y, posZ+z), CampPalette.legacy("Blocks", "BED", 1), 3);
                                        world.setBlockState(p.set(posX+x-1, posY+y, posZ+z), CampPalette.legacy("Blocks", "BED", 1+8), 3);
                                    }else if (x==sizeX-3) {
                                        world.setBlockState(p.set(posX+x, posY+y, posZ+z), CampPalette.legacy("Blocks", "BED", 3), 3);
                                        world.setBlockState(p.set(posX+x+1, posY+y, posZ+z), CampPalette.legacy("Blocks", "BED", 3+8), 3);
                                    }else if (z==2) {
                                        world.setBlockState(p.set(posX+x, posY+y, posZ+z), CampPalette.legacy("Blocks", "BED", 2), 3);
                                        world.setBlockState(p.set(posX+x, posY+y, posZ+z-1), CampPalette.legacy("Blocks", "BED", 2+8), 3);
                                    }else if (z==sizeZ-3) {
                                        world.setBlockState(p.set(posX+x, posY+y, posZ+z), CampPalette.legacy("Blocks", "BED", 0), 3);
                                        world.setBlockState(p.set(posX+x, posY+y, posZ+z+1), CampPalette.legacy("Blocks", "BED", 0+8), 3);
                                    }
                                }
                            }

                        }

                        if (y == 5 && x % 8 == 0 && z % 8 == 0) {
                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), CampPalette.getWithFacing(b_torch.getState(),Direction.UP), 2);
                        }
                    }
                }
            }
        }
    }

    @Override public String key() { return "Barracks"; }
}
