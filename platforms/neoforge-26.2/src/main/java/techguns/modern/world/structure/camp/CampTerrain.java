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

public final class CampTerrain {
public static final float[][] FILTER_GAUSSIAN_5x5 = new float[][] {
        {.00325f, .01375f, .0235f, .01375f, .00325f},
        {.01375f, .059f, .097f, .059f, .01375f},
        {.0235f, .097f, .159f, .097f, .0235f},
        {.01375f, .059f, .097f, .059f, .01375f},
        {.00325f, .01375f, .0235f, .01375f, .00325f}
    };
public static void flattenArea(CampWorld world, int posX, int posZ, int sizeX, int sizeZ, int maxHeightDiff) {
        int posY;
        int heightSum = 0;
        int count = (sizeX*sizeZ);
        int min = -1;
        int max = 0;
        ArrayList<Integer> heights = new ArrayList<Integer>();
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z <sizeZ; z++) {
                int y = getHeightValueNoTrees(world,posX+x, posZ+z)-1;

                heights.add(Integer.valueOf(y));
                heightSum+=y;
                if (y > max) max = y;
                if (y < min || min == -1) min = y;
            }
        }

        int span = max-min;

        float median=0;
        Collections.sort(heights);
        if (heights.size()>2){
            if (heights.size() % 2 ==0){
                median = (heights.get(((int)Math.floor(heights.size()/2))-1) + heights.get(((int)Math.ceil(heights.size()/2))-1)) /2;
            } else {
                median = heights.get((heights.size()/2)-1);
            }
        }

        float averageHeight = median;
        float f = averageHeight - ((float)maxHeightDiff/2.0f);

        MutableBlockPos p = new MutableBlockPos();
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z <sizeZ; z++) {
                int y =getHeightValueNoTrees(world, posX+x, posZ+z)-1;
                int newY;
                if (span <= maxHeightDiff) newY = y;
                else newY = Math.round(((((float)(y-min)/(float)span)*(float)maxHeightDiff) + f));

                adjustHeightAtPos(world, posX+x, posZ+z, y, p, newY);

            }
        }
    }
public static void apply2DHeightmapFilter(CampWorld world, int posX, int posZ, int sizeX, int sizeZ, float[][] filter) {
        int xoffset = -filter.length/2;
        int zoffset = -filter[0].length/2;
        MutableBlockPos p = new MutableBlockPos();
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z <sizeZ; z++) {
                int y = getHeightValueNoTrees(world,posX+x, posZ+z)-1;
                float newY = 0.0f;
                for (int i = 0; i < filter.length; i++) {
                    for (int j = 0; j < filter[i].length; j++){
                        newY += (getHeightValueNoTrees(world,posX+x+xoffset+i, posZ+z+zoffset+j)-1.0f) * filter[i][j];
                    }
                }
                adjustHeightAtPos(world, posX+x, posZ+z, y, p, Math.round(newY));
            }
        }
    }
public static void fillBlocks(CampWorld world, MutableBlockPos start, int sizeX, int sizeY, int sizeZ, BlockState block) {
        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();
        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeZ; j++) {
                for (int k = 0; k < sizeY; k++) {
                    world.setBlockState(start.set(x+i, y+k, z+j), block);
                }
            }
        }
    }
public static void fillBlocksHollow(CampWorld world, MutableBlockPos start, int sizeX, int sizeY, int sizeZ, BlockState block) {
        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();

        for (int i = 0; i < sizeX; i++) {
            for (int j = 0; j < sizeZ; j++) {
                for (int k = 0; k < sizeY; k++) {
                    if (j == 0 || j == sizeZ-1 || i == 0 || i == sizeX-1) {
                        world.setBlockState(start.set(x+i, y+k, z+j), block);
                    }
                }
            }
        }
    }

    public static BiomeColorType getBiomeType(CampWorld world,int x,int z) { return world.color(); }
    public static int getHeightValueNoTrees(CampWorld world,int x,int z) { return world.solidHeight(x,z); }
    public static void removeJunkInArea(CampWorld world,int x,int z,int sx,int sz) {
        for(int dx=0;dx<sx;dx++) for(int dz=0;dz<sz;dz++) {
            world.clearColumn(x+dx,z+dz);
            for(int y=world.topAll(x+dx,z+dz); y>0; y--) {
                var p=new BlockPos(x+dx,y,z+dz);
                if(ground(world.getBlockState(p))) break;
                world.setBlockToAir(p);
            }
        }
    }
    private static void adjustHeightAtPos(CampWorld world,int x,int z,int y,MutableBlockPos p,int newY) {
        if(newY>y) world.raiseGround(x,y,z,newY);
        else for(int i=y;i>newY;i--) world.setBlockToAir(p.set(x,i,z));
    }
    public static boolean ground(BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || s.is(net.minecraft.tags.BlockTags.DIRT)
            || s.is(net.minecraft.tags.BlockTags.SAND) || s.is(net.minecraft.tags.BlockTags.TERRACOTTA)
            || s.is(Blocks.CLAY) || s.is(Blocks.GRAVEL) || s.is(Blocks.BEDROCK) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
            || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.STONE_BRICKS)
            || techguns.core.BuildingBlocks.ALL.stream().filter(v->v.family().equals("concrete")).anyMatch(v->s.is(techguns.modern.world.BuildingContent.BLOCKS.get(v.id()).get()));
    }

}
