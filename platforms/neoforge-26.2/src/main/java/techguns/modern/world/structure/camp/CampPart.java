package techguns.modern.world.structure.camp;

import java.util.Random;
import net.minecraft.core.Direction;

/** Source size constraints and W/N/E/S convention, without a legacy World dependency. */
public abstract class CampPart {
    public final int minX,minY,minZ,maxX,maxY,maxZ;
    public boolean canSwapXZ;
    protected CampPart(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        this.minX=minX; this.minY=minY; this.minZ=minZ; this.maxX=maxX; this.maxY=maxY; this.maxZ=maxZ;
    }
    public CampPart setSwapXZ(boolean value) { canSwapXZ=value; return this; }
    public abstract String key();
    public abstract void setBlocks(CampWorld world,int x,int y,int z,int sx,int sy,int sz,int direction,BiomeColorType color,Random random);
    public static Direction directionToFacing(int direction) {
        return switch(direction) { case 0->Direction.WEST; case 2->Direction.EAST; case 3->Direction.SOUTH; default->Direction.NORTH; };
    }
    public enum BiomeColorType { WOODLAND,SNOW,DESERT,NETHER }
}
