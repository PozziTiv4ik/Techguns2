package techguns.core.castle;

/** Original EnumFacing horizontal indices: south, west, north, east. */
public enum CastleFacing {
    SOUTH(0,1), WEST(-1,0), NORTH(0,-1), EAST(1,0);
    private final CastlePos vector;
    CastleFacing(int x,int z) { vector=new CastlePos(x,0,z); }
    public CastlePos getDirectionVec() { return vector; }
    public int getHorizontalIndex() { return ordinal(); }
    public static CastleFacing getHorizontal(int i) { return values()[Math.floorMod(i,4)]; }
    public CastleFacing rotateY() { return getHorizontal(ordinal()+1); }
    public CastleFacing rotateYCCW() { return getHorizontal(ordinal()+3); }
    public CastleFacing getOpposite() { return getHorizontal(ordinal()+2); }
    public static CastleFacing getFacingFromVector(int x,int y,int z) {
        for(var f:values()) if(f.vector.equals(new CastlePos(x,y,z))) return f;
        throw new IllegalArgumentException("Expected horizontal unit vector");
    }
}
