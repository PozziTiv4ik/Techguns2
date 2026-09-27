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

public class Bunker extends CampPart {

    private static final Identifier CHEST_LOOT = TGContent.id("chests/militarybase_bunker");

     MBlock groundBlock = new MBlock(CampPalette.legacy("TGBlocks", "CONCRETE", 1));
     MBlock stairsblock = new MBlock(CampPalette.legacy("Blocks", "STONE_STAIRS", 0));
     MBlock wallBlock = new MBlock(CampPalette.legacy("TGBlocks", "CONCRETE", 0));
     MBlock roofBlock = new MBlock(CampPalette.legacy("TGBlocks", "CONCRETE", 2));
     MBlock sandbags = new MBlock(CampPalette.legacy("TGBlocks", "SANDBAGS", 0));
     MBlock ladder = new MBlock(CampPalette.legacy("TGBlocks", "LADDER_0", 0));
     MBlock[] crateBlocks = CampPalette.crates;

     MBlock lantern = new MBlock(CampPalette.legacy("Blocks", "GLOWSTONE", 0));
     MBlock lamps = new MBlock(CampPalette.legacy("TGBlocks", "LAMP_0", 0));

     int type = 0;

    public Bunker(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int type) {
        super(minX, minY, minZ, maxX, maxY, maxZ);
        this.type = type;
    }

    @Override
    public void setBlocks(CampWorld world, int posX, int posY, int posZ, int sizeX,
            int sizeY, int sizeZ, int direction, BiomeColorType colorType, Random rnd) {

            MutableBlockPos p = new MutableBlockPos();

            if ((sizeX <= 4) && (sizeZ >= 5)) {
                if (direction == 0 || direction == 2) {
                    direction = (direction+1) % 4;
                }
            }else if ((sizeZ <= 4) && (sizeX >= 5)) {
                if (direction == 1 || direction == 3) {
                    direction = (direction+1) % 4;
                }
            }

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
                sizeZ-=2;
                break;
         }

        Direction facing = directionToFacing(direction);

         for (int y = -1; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    if (y == -1) {

                        world.setBlockState(p.set(posX+x, posY+y, posZ+z), groundBlock.getState(), 2);
                    }else if (y == 0) {
                        if (x== 0 || x == sizeX-1 || z==0 || z == sizeZ-1) {

                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), wallBlock.getState(), 2);
                        } else {
                            world.setBlockToAir(p.set(posX+x, posY+y, posZ+z));
                        }
                    }else if (y == 1) {
                        if (type == 2 || type == 3) {
                            if (x== 0 || x == sizeX-1 || z==0 || z == sizeZ-1) {

                                world.setBlockState(p.set(posX+x, posY+y, posZ+z), wallBlock.getState(), 2);
                            }
                        }else {
                            if (((x== 0 || x == sizeX-1) && (z==0 || z == sizeZ-1 || z % 3 == 0)) ||
                                    ((x== 0 || x == sizeX-1 || x % 3 == 0) && (z==0 || z == sizeZ-1 ))) {

                                world.setBlockState(p.set(posX+x, posY+y, posZ+z), wallBlock.getState(), 2);
                            }
                        }
                    }else if (y == 2) {
                        if (x== 0 || x == sizeX-1 || z==0 || z == sizeZ-1) {

                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), wallBlock.getState(), 2);
                        }else {

                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), roofBlock.getState(), 2);
                        }
                    }else if (y == 3 && (type == 1 || type == 3)) {
                        if (x== 0 || x == sizeX-1 || z==0 || z == sizeZ-1) {

                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), sandbags.getState(), 2);
                        }
                    }

                }
            }
        }

        int xoffset, zoffset;
        int dx, dz;

        switch (direction) {
            case 0:
                xoffset = 0; zoffset=sizeZ/2;
                dx = -1; dz =0;
                break;
            case 1:
                xoffset = sizeX/2; zoffset=0;
                dx = 0; dz = -1;
                break;
            case 2:
                xoffset = sizeX-1; zoffset=sizeZ/2;
                dx = 1; dz=0;
                break;
            case 3:
            default:
                xoffset = sizeX/2; zoffset=sizeZ-1;
                dx = 0; dz=1;
                break;
        }

        world.setBlockToAir(p.set(posX+xoffset, posY+1, posZ+zoffset));
        world.setBlockToAir(p.set(posX+xoffset, posY+0, posZ+zoffset));

        CampPalette.placeDoor(world, p.set(posX+xoffset, posY+0, posZ+zoffset), facing, FortificationContent.DOOR.get(), true);

        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? 1 : 0), posY+0, posZ+zoffset+((dz == 0) ? 1 : 0)), wallBlock.getState(), 2);
        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? 1 : 0), posY+1, posZ+zoffset+((dz == 0) ? 1 : 0)), wallBlock.getState(), 2);
        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? -1 : 0), posY+0, posZ+zoffset+((dz == 0) ? -1 : 0)), wallBlock.getState(), 2);
        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? -1 : 0), posY+1, posZ+zoffset+((dz == 0) ? -1 : 0)), wallBlock.getState(), 2);

        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? 1 : dx), posY-1, posZ+zoffset+((dz == 0) ? 1 : dz)), wallBlock.getState(), 2);
        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? -1 : dx), posY-1, posZ+zoffset+((dz == 0) ? -1 : dz)), wallBlock.getState(), 2);
        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? 1 : dx*2), posY-1, posZ+zoffset+((dz == 0) ? 1 : dz*2)), wallBlock.getState(), 2);
        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? -1 : dx*2), posY-1, posZ+zoffset+((dz == 0) ? -1 : dz*2)), wallBlock.getState(), 2);
        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? 1 : dx), posY+0, posZ+zoffset+((dz == 0) ? 1 : dz)), wallBlock.getState(), 2);
        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? -1 : dx), posY+0, posZ+zoffset+((dz == 0) ? -1 : dz)), wallBlock.getState(), 2);
        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? 1 : dx*2), posY+0, posZ+zoffset+((dz == 0) ? 1 : dz*2)), wallBlock.getState(), 2);
        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? -1 : dx*2), posY+0, posZ+zoffset+((dz == 0) ? -1 : dz*2)), wallBlock.getState(), 2);

        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? 1 : dx), posY+2, posZ+zoffset+((dz == 0) ? 1 : dz)), CampPalette.getWithFacing(lamps.getState(), facing.getOpposite()), 2);
        world.setBlockState(p.set(posX+xoffset+((dx == 0) ? -1 : dx), posY+2, posZ+zoffset+((dz == 0) ? -1 : dz)),CampPalette.getWithFacing(lamps.getState(), facing.getOpposite()), 2);

        world.setBlockState(p.set(posX+xoffset, posY-1, posZ+zoffset), groundBlock.getState(), 2);
        world.setBlockState(p.set(posX+xoffset+dx, posY-1, posZ+zoffset+dz), groundBlock.getState(), 2);
        world.setBlockToAir(p.set(posX+xoffset+dx, posY+0, posZ+zoffset+dz));
        world.setBlockToAir(p.set(posX+xoffset+dx, posY+1, posZ+zoffset+dz));

        world.setBlockState(p.set(posX+xoffset+dx*2, posY+0, posZ+zoffset+dz*2), CampPalette.getWithFacing(stairsblock.getState(), facing), 2);
        world.setBlockToAir(p.set(posX+xoffset+dx*2, posY+1, posZ+zoffset+dz*2));

        if (type == 2 || type == 3) {

            world.setBlockState(p.set(posX+xoffset-dx, posY+1, posZ+zoffset-dz), CampPalette.getWithFacing(lamps.getState(), Direction.UP), 2);

                setCrateBlocks(world, p, posX+2, posY, posZ+2, sizeX-4, 2, sizeZ-4);

        }else {

            world.setBlockState(p.set(posX+(sizeX/2), posY+1, posZ+(sizeZ/2)), CampPalette.getWithFacing(lamps.getState(), Direction.UP), 2);

        }

        if (type == 1 || type == 3) {
            int meta = 0;
            Direction ladderFacing=Direction.NORTH;
            switch (direction) {
                case 0:
                    xoffset = sizeX-2;
                    zoffset = sizeZ-2;
                    dx = 1; dz=0;
                    meta = 2;
                    ladderFacing=Direction.WEST;
                    break;
                case 1:
                    zoffset = sizeZ-2;
                    xoffset = 1;
                    dx = 0; dz = 1;
                    meta = 0;
                    ladderFacing=Direction.NORTH;
                    break;
                case 2:
                    xoffset = 1;
                    zoffset = 1;
                    dx = -1; dz = 0;
                    meta = 3;
                    ladderFacing=Direction.EAST;
                    break;
                case 3:
                    xoffset = sizeX-2;
                    zoffset = 1;
                    dx = 0; dz = -1;
                    meta = 1;
                    ladderFacing=Direction.SOUTH;
                    break;
            }

            world.setBlockToAir(p.set(posX+xoffset, posY+2, posZ+zoffset));
            world.setBlockState(p.set(posX+xoffset+dx, posY+1, posZ+zoffset+dz), wallBlock.getState(), 2);

            world.setBlockState(p.set(posX+xoffset, posY+0, posZ+zoffset), CampPalette.getWithFacing(ladder.getState(),ladderFacing), 2);
            world.setBlockState(p.set(posX+xoffset, posY+1, posZ+zoffset), CampPalette.getWithFacing(ladder.getState(),ladderFacing), 2);
            world.setBlockState(p.set(posX+xoffset, posY+2, posZ+zoffset), CampPalette.getWithFacing(ladder.getState(),ladderFacing), 2);
        }

    }

    private void setCrateBlocks(CampWorld world, MutableBlockPos p, int posX, int posY, int posZ, int sizeX,
            int sizeY, int sizeZ) {
        Random r = world.rand;
        for (int x = 0; x < sizeX; x++) {
            for (int z=0; z <sizeZ; z++) {

                float chestroll = r.nextFloat();
                Block chest=null;

                if (chestroll <=0.1f) {
                    chest = Blocks.CHEST;
                }

                if (chest==null){

                    for (int y = 0; y < sizeY; y++) {
                        if (!world.isAirBlock(p.set(posX+x, posY+y-1, posZ+z)) && r.nextFloat() > 0.5) {
                            int index = r.nextInt(crateBlocks.length);
                            world.setBlockState(p.set(posX+x, posY+y, posZ+z), crateBlocks[index].getState(), 2);
                        }
                    }
                } else {

                    int meta = r.nextInt(4);

                    world.setBlockState(p.set(posX+x, posY, posZ+z), CampPalette.getRotatedHorizontal(chest.defaultBlockState(),meta));

                    world.loot(p, CHEST_LOOT, world.rand.nextLong());

                }
            }
        }
    }

    @Override public String key() { return "Bunker" + ":" + type; }
}
