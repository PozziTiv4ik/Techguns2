package techguns.core.castle;

import java.util.Random;
import techguns.core.castle.CastleSegments.SegmentType;

/** Shared source world RNG; callbacks build a plan, never touch live chunks. */
public abstract class CastleWorld {
    public final Random rand;
    protected CastleWorld(long seed) { rand=new Random(seed); }
    public abstract void segment(int family,SegmentType type,int x,int y,int z,int rotation);
    public abstract void post(int x,int y,int z);
}
