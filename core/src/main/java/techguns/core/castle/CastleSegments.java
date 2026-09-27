// Generated from the attributed 1.12.2 source by tools/legacy_castle.py.
package techguns.core.castle;

import java.util.*;
import techguns.core.castle.CastleSegments.SegmentType;

public class CastleSegments {

    public static HashMap<SegmentType, CastleSegments> templateSegments = new java.util.LinkedHashMap<>();

    static {
        boolean t = true;
        boolean f = false;
        addSegment(SegmentType.STRAIGHT, 0, 0, 1, new boolean[]{f,f,f, t, f, f, f, t}, 2);
        addSegment(SegmentType.CURVE, 0, 1, 1, new boolean[]{f,f,f, t, f, t, f, f}, 4);
        addSegment(SegmentType.FORK, 0, 2, 1, new boolean[]{f,f,f, t, f, t, f, t}, 4);
        addSegment(SegmentType.CROSS, 0, 3, 1, new boolean[]{f,t,f, t, f, t, f, t}, 1);
        addSegment(SegmentType.END, 0, 4, 1, new boolean[]{f,f,f, t, f, f, f, f}, 4);

        addSegment(SegmentType.RAMP, 1, 0, 2, false);
        addSegment(SegmentType.ENTRANCE, 1, 1, 1, false);

        addSegment(SegmentType.ROOM_WALL, 2, 0, 1, new boolean[]{f,f,f, t, t, t, t, t}, 4);
        addSegment(SegmentType.ROOM_CORNER, 2, 1, 1, new boolean[]{f,f,f, t, t, t, f, f}, 4);
        addSegment(SegmentType.ROOM_INNER, 2, 2, 1,new boolean[]{t,t,t, t, t, t, t, t}, 1);
        addSegment(SegmentType.ROOM_DOOR, 2, 3, 1, new boolean[]{f,t,f, t, t, t, t, t}, 4);
        addSegment(SegmentType.ROOM_DOOR_CORNER1, 2, 4, 1, new boolean[]{f,t,f, t, t, t, f, f}, 4);
        addSegment(SegmentType.ROOM_DOOR_CORNER2, 2, 5 ,1, new boolean[]{f,f,f, t, t, t, f, t}, 4);
        addSegment(SegmentType.ROOM_DOOR_CORNER_DOUBLE, 2, 6, 1, new boolean[]{f,t,f, t, t, t, f, t}, 4);

        addSegment(SegmentType.FOUNDATION, 3, 0, 1, false);
        addSegment(SegmentType.PILLARS, 3, 1, 1, false);
    }

    private static void addSegment(SegmentType type, int row, int col) {
        templateSegments.put(type, new CastleSegments(type, row, col));
    }

    private static void addSegment(SegmentType type, int row, int col, int sizeY) {
        templateSegments.put(type, new CastleSegments(type, row, col, sizeY));
    }

    private static void addSegment(SegmentType type, int row, int col, int sizeY, boolean[] pattern, int rotations) {
        templateSegments.put(type, new CastleSegments(type, row, col, sizeY).setPattern(pattern, rotations));
    }

    private static void addSegment(SegmentType type, int row, int col, int sizeY, boolean match) {
        templateSegments.put(type, new CastleSegments(type, row, col, sizeY).setMatch(match));
    }

    public int col;
    public int row;

    public int sizeY = 1;

    SegmentType type;

    public int rotations = 1;

    public boolean match = true;

    public boolean[] pattern = new boolean[8];

    public CastleSegments(SegmentType type, int row, int col) {
        this.col = col;
        this.row = row;
        this.type = type;
    }

    public CastleSegments(SegmentType type, int row, int col, int sizeY) {
        this(type, row, col);
        this.sizeY = sizeY;
    }

    public CastleSegments setPattern(boolean[] pattern, int rotations) {
        this.pattern = pattern;
        this.rotations = rotations;
        return this;
    }

    public CastleSegments setMatch (boolean match) {
        this.match = match;
        return this;
    }

    public enum SegmentType {
        STRAIGHT, CURVE, FORK, CROSS, END,
        RAMP, ENTRANCE,
        ROOM_WALL, ROOM_CORNER, ROOM_INNER, ROOM_DOOR, ROOM_DOOR_CORNER1, ROOM_DOOR_CORNER2, ROOM_DOOR_CORNER_DOUBLE,
        FOUNDATION, PILLARS;

    }

}
