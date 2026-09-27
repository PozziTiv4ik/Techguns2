package techguns.core.castle;

import java.util.Random;
import techguns.core.castle.CastleSegments.SegmentType;

/** Active PresetCastle branches; all six source families have one variant. */
public final class CastlePreset {
    public static final CastlePreset INSTANCE=new CastlePreset();
    public int getSizeXZ() { return 5; }
    public int getSizeY() { return 5; }
    public float getSpawnDensity() { return .1f; }
    public void init(CastleMaze path) {
        path.startHeightLevel=1; path.chanceStraight=.8f; path.chanceRamp=.5f;
        path.chanceRoom=.25f; path.chanceFork=.4f; path.chanceUp=.65f;
        path.useFoundations=true; path.usePillars=true; path.useRoof=true;
    }
    public record Segment(int family,SegmentType type) {
        public void placeSegment(CastleWorld world,int x,int y,int z,int rotation) { world.segment(family,type,x,y,z,rotation); }
    }
    public Segment getSegment(SegmentType type,int y,int min,int max,boolean above,boolean below,int seed) {
        new Random(seed).nextInt(1); // Source variant selection is independent of the shared world RNG.
        return new Segment(y==-1?5:y==min?0:y<=min+1?1:y<max-2?2:y<max-1?3:4,type);
    }
    private CastlePreset() {}
}
