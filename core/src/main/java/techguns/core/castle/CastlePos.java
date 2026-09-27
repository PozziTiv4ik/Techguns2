package techguns.core.castle;

/** Integer coordinates used by the original path; no platform dependency. */
public record CastlePos(int x,int y,int z) {
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
}
