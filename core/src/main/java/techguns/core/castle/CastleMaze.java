// Generated from the attributed 1.12.2 source by tools/legacy_castle.py.
package techguns.core.castle;

import java.util.*;
import techguns.core.castle.CastleSegments.SegmentType;

public class CastleMaze {

    Random rand;

    PathSegment[][][] dungeonVolume;
    List<PathSegment> dungeonList;

    private int nextRoomID = 1;

    int sX;
    int sY;
    int sZ;

    public int startSegmentCount = 1;
    public int startHeightLevel = 0;

    public float chanceStraight = 0.5f;
    public float chanceRamp = 0.25f;
    public float chanceRoom = 0.25f;
    public float chanceFork = 0.2f;
    public float chanceUp = 0.5f;

    public int minRoomArea = 4;
    public int maxRoomArea = 16;
    public int minRoomWidth = 2;
    public int maxRoomWidth = 5;

    public boolean usePillars = true;
    public boolean useFoundations = true;
    public boolean useRoof = false;
    public boolean useBottomLayer = false;

    public int entranceRampLength = 0;
    private int rampPlaced = 0;

    private int numSegments = 0;

    protected CastleFacing startFacing = null;
    protected CastlePos startPos = null;

    public CastleMaze(int sX, int sY, int sZ, Random rand) {
        this.sX =sX;
        this.sY = sY;
        this.sZ = sZ;
        this.rand = rand;
        dungeonVolume = new PathSegment[sX][sY][sZ];
        dungeonList = new ArrayList<PathSegment>();

    }

    public int getNumSegments() {
        return numSegments;
    }

    public void generatePath() {
        if (this.useBottomLayer) {
            this.sY -= 1;
        }

        int startX;
        int startZ;

        int startY = (sY+startHeightLevel)%sY;
        switch (this.rand.nextInt(4)) {
            case 0:
                startX = 0;
                startZ = sZ/2;
                startFacing = CastleFacing.getFacingFromVector(1, 0, 0);
                break;
            case 1:
                startX = sX-1;
                startZ = sZ/2;
                startFacing = CastleFacing.getFacingFromVector(-1, 0, 0);
                break;
            case 2:
                startX = sX/2;
                startZ = 0;
                startFacing = CastleFacing.getFacingFromVector(0, 0, 1);
                break;
            case 3:
            default:
                startX = sX/2;
                startZ = sZ-1;
                startFacing = CastleFacing.getFacingFromVector(0, 0, -1);
                break;

        }
        int startDir = startFacing.getHorizontalIndex();

        this.entranceRampLength = Math.min(this.entranceRampLength, startY);

        this.startPos = new CastlePos(startX,startY,startZ);

        generateSegment(startX, startY, startZ, startDir, null);

    }

    public CastleFacing getEntranceRotation() {
        return this.startFacing;
    }

    public CastlePos getStartPos() {
        return this.startPos;
    }

    public void generateSegment(int x, int y, int z, int dir, PathSegment prev) {
        CastleFacing facing = CastleFacing.getHorizontal(dir);
        PathSegment segment = new PathSegment(x,y,z);
        if (prev != null) {
            segment.setConnection(facing.getOpposite(), true);
            prev.setConnection(facing, true);
        }else {
            segment.isEntrance = true;
        }
        this.addSegment(segment);

        int maxRolls = 3;
        int roll = 0;

        int nextDir;
        boolean success = false;
        boolean canRollAgain = true;
        boolean fork = false;

        if (this.rampPlaced < this.entranceRampLength) {
            nextDir = dir;
            CastlePos nextPos = segment.getNextPos(nextDir);
            CastleFacing nextFacing = CastleFacing.getHorizontal(nextDir);
            CastlePos offset = nextFacing.getDirectionVec();
            int dy = -1;
            CastlePos elevPos = new CastlePos(x, y+dy,z);
            CastlePos elevNextPos = new CastlePos(x+offset.getX(), y+dy, z+offset.getZ());

            if (isWithinBounds(nextPos) && isWithinBounds(elevNextPos)) {

                PathSegment rampDummy = new PathSegment(elevPos.getX(), elevPos.getY(), elevPos.getZ());
                this.addSegment(rampDummy);
                segment.isRamp = true;
                rampDummy.isRamp = true;
                segment.elevation = -1;
                rampDummy.elevation = 1;
                rampDummy.rampRotation = nextFacing.getOpposite().getHorizontalIndex();

                generateSegment(elevNextPos.getX(), elevNextPos.getY(), elevNextPos.getZ(), nextDir, rampDummy);

                success = true;
            }
            this.rampPlaced++;
        }

        while (!success && roll++ < maxRolls) {
            if (segment.isEntrance) {
                nextDir = dir;
                canRollAgain = false;
            }else {
                nextDir = getRandomDir(dir);
            }
            CastleFacing nextFacing = CastleFacing.getHorizontal(nextDir);
            CastlePos nextPos = segment.getNextPos(nextDir);
            if (isWithinBounds(nextPos)) {
                if (dir == nextDir && !segment.isEntrance && !fork && rand.nextFloat() < chanceRamp) {
                    float f = rand.nextFloat();
                    int dy = f < chanceUp ? 1 : -1;
                    CastlePos elevPos = new CastlePos(x, y+dy,z);
                    if (isWithinBounds(elevPos) && !isOccupied(elevPos)) {
                        CastlePos offset = nextFacing.getDirectionVec();
                        CastlePos elevNextPos = new CastlePos(x+offset.getX(), y+dy, z+offset.getZ());
                        boolean cont = false;
                        if (!isOccupied(elevNextPos)) {
                            success = true;
                            cont = true;
                        }else {
                            PathSegment seg = this.get(elevNextPos);
                            if (seg != null && !seg.isRamp && !seg.isEntrance) {

                                seg.setConnection(nextFacing.getOpposite(), true);
                                success = true;
                                cont = false;
                            }
                        }
                        if (success) {
                            canRollAgain = false;
                            PathSegment rampDummy = new PathSegment(elevPos.getX(), elevPos.getY(), elevPos.getZ());
                            this.addSegment(rampDummy);
                            segment.isRamp = true;
                            rampDummy.isRamp = true;
                            if (dy == 1) {
                                segment.elevation = 1;
                                rampDummy.elevation = -1;
                                segment.rampRotation = nextDir;
                            }else {
                                segment.elevation = -1;
                                rampDummy.elevation = 1;
                                rampDummy.rampRotation = nextFacing.getOpposite().getHorizontalIndex();
                            }

                            if (cont) {
                                generateSegment(elevNextPos.getX(), elevNextPos.getY(), elevNextPos.getZ(), nextDir, rampDummy);
                            }
                        }
                    }

                } else if (!isOccupied(nextPos)) {
                    if (rand.nextFloat() < chanceRoom) {

                        int area = minRoomArea + rand.nextInt(maxRoomArea-minRoomArea);
                        boolean b = tryGrowRoom(nextPos,segment, nextFacing, minRoomWidth+rand.nextInt(maxRoomWidth-minRoomWidth), minRoomWidth+rand.nextInt(maxRoomWidth-minRoomWidth), area);
                        if (b) {
                            success = true;
                        }else {

                            generateSegment(nextPos.getX(), nextPos.getY(), nextPos.getZ(), nextDir, segment);
                            success = true;
                        }

                    }else {
                        generateSegment(nextPos.getX(), nextPos.getY(), nextPos.getZ(), nextDir, segment);
                        success = true;
                    }
                }else {
                    PathSegment seg = this.get(nextPos);
                    if (seg != null && !seg.isRamp && !seg.isEntrance) {
                        segment.setConnection(nextFacing, true);
                        seg.setConnection(CastleFacing.getHorizontal(nextDir).getOpposite(), true);
                        success = true;
                    }
                }
            }
            if (success && canRollAgain && rand.nextFloat() < chanceFork) {
                success = false;
                fork = true;
            }
        }
    }

    private boolean tryGrowRoom(CastlePos pos, PathSegment prev, CastleFacing dir, int maxWidth, int maxLength, int preferredArea) {

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        CastlePos vFront = dir.getDirectionVec();
        CastlePos vRight = dir.rotateY().getDirectionVec();
        CastlePos vLeft = dir.rotateYCCW().getDirectionVec();

        int l = 0;

        int bestl = 0;
        int bestw_l = 0;
        int bestw_r = 0;
        float bestValue = Float.MAX_VALUE;

        int maxw_l = Integer.MAX_VALUE;
        int maxw_r = Integer.MAX_VALUE;

        boolean stopL = false;
        while (l < maxLength && !stopL ) {
            CastlePos posF = new CastlePos(x + vFront.getX()*l, y, z+vFront.getZ()*l);
            if (!isWithinBounds(posF) || isOccupied(posF)) {
                stopL = true;

                break;
            }

            boolean stop = false;
            int w_l = 0;
            int w_r = 0;

            while (w_l < maxWidth && w_l < maxw_l && !stop) {
                CastlePos pos_ = new CastlePos(x + vLeft.getX() * (w_l+1) + vFront.getX()*l, y, z + vLeft.getZ() * (w_l+1) + vFront.getZ()*l);
                if (!isWithinBounds(pos_) || isOccupied(pos_)) {
                    stop = true;

                    maxw_l = w_l;
                }else {
                    w_l++;
                }
            }

            stop = false;
            while (w_r < maxWidth && w_r < maxw_r &&  !stop) {
                CastlePos pos_ = new CastlePos(x + vRight.getX() * (w_r+1) + vFront.getX()*l, y, z + vRight.getZ() * (w_r+1) + vFront.getZ()*l);
                if (!isWithinBounds(pos_) || isOccupied(pos_)) {
                    stop = true;

                    maxw_r = w_r;
                }else {
                    w_r++;
                }
            }

            int area = ( w_l+w_r+1) * (l+1);
            float area_ratio = (float)Math.max(area, preferredArea) / (float)Math.min(area, preferredArea);
            float aspect_ratio = (float)Math.max(( w_l+w_r+1), (l+1)) / (float)Math.min(( w_l+w_r+1), (l+1));
            float value = area_ratio + aspect_ratio;

            if (value < bestValue) {
                bestValue = value;
                bestl = l;
                bestw_l = w_l;
                bestw_r = w_r;
            }
            l++;

        }

        if (bestl < 2 || (bestw_l+bestw_r+1) < 2) {
            return false;
        }else {

            CastlePos p1 = new CastlePos (x + vLeft.getX() * bestw_l, y, z + vLeft.getZ() * bestw_l);
            CastlePos p2 = new CastlePos (x + vRight.getX() * bestw_r + vFront.getX()*bestl, y, z + vRight.getZ() * bestw_r + vFront.getZ()*bestl);

            CastlePos min = new CastlePos (Math.min(p1.getX(),p2.getX()), y, Math.min(p1.getZ(),p2.getZ()));
            CastlePos max = new CastlePos (Math.max(p1.getX(),p2.getX()), y, Math.max(p1.getZ(),p2.getZ()));

            if (!isWithinBounds(min) || !isWithinBounds(max)) {
                throw new IllegalStateException("Room exceeds dungeon bounds");

            }

            placeRoomSegments(min, max);
            prev.setConnection(dir, true);
            PathSegment segment = this.get(pos);
            if (segment != null) {
                segment.setConnection(dir.getOpposite(), true);
            }else {
                throw new IllegalStateException("Missing room entrance");

            }

            return true;
        }
    }

    private void placeRoomSegments(CastlePos min, CastlePos max) {
        int roomID = nextRoomID++;

        int y = min.getY();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                PathSegment segment = new PathSegment(x,y,z);
                segment.roomID = roomID;
                this.addSegment(segment);

                if (x > min.getX()) segment.setConnection(7, true);
                if (x < max.getX()) segment.setConnection(3, true);
                if (z > min.getZ()) segment.setConnection(1, true);
                if (z < max.getZ()) segment.setConnection(5, true);
                if (x != min.getX() && z != min.getZ()) segment.setConnection(0,  true);
                if (x != min.getX() && z != max.getZ()) segment.setConnection(6,  true);
                if (x != max.getX() && z != min.getZ()) segment.setConnection(2,  true);
                if (x != max.getX() && z != max.getZ()) segment.setConnection(4,  true);
            }
        }

        int length = (max.getZ()-min.getZ())+1;
        int numDoors = rand.nextInt(length);
        for (int i = 0; i < numDoors; i++) {
            int z = rand.nextInt(length);
            CastlePos pos_ = new CastlePos(min.getX(), y, min.getZ()+z);
            PathSegment segment = this.get(pos_);
            CastleFacing facing = CastleFacing.WEST;
            placeDoor(new CastlePos(pos_.getX(), y, pos_.getZ()), facing, segment);
        }

        numDoors = rand.nextInt(length);
        for (int i = 0; i < numDoors; i++) {
            int z = rand.nextInt(length);
            CastlePos pos_ = new CastlePos(max.getX(), y, min.getZ()+z);
            PathSegment segment = this.get(pos_);
            CastleFacing facing = CastleFacing.EAST;
            placeDoor(new CastlePos(pos_.getX(), y, pos_.getZ()), facing, segment);
        }

        length = (max.getX() - min.getX())+1;
        numDoors = rand.nextInt(length);
        for (int i = 0; i < numDoors; i++) {
            int x = rand.nextInt(length);
            CastlePos pos_ = new CastlePos(min.getX()+x, y, min.getZ());
            PathSegment segment = this.get(pos_);
            CastleFacing facing = CastleFacing.NORTH;
            placeDoor(new CastlePos(pos_.getX(), y, pos_.getZ()), facing, segment);
        }

        numDoors = rand.nextInt(length);
        for (int i = 0; i < numDoors; i++) {
            int x = rand.nextInt(length);
            CastlePos pos_ = new CastlePos(min.getX()+x, y, max.getZ());
            PathSegment segment = this.get(pos_);
            CastleFacing facing = CastleFacing.SOUTH;
            placeDoor(new CastlePos(pos_.getX(), y, pos_.getZ()), facing, segment);
        }
    }

    private void placeDoor(CastlePos pos, CastleFacing facing, PathSegment segment) {
        CastlePos offset = facing.getDirectionVec();
        CastlePos nextPos = new CastlePos(pos.getX()+offset.getX(), pos.getY(), pos.getZ()+offset.getZ());
        if (isWithinBounds(nextPos)) {
            if (!isOccupied(nextPos)) {
                generateSegment(nextPos.getX(), nextPos.getY(), nextPos.getZ(), facing.getHorizontalIndex(), segment);
            }else {
                PathSegment seg = this.get(nextPos);
                if (seg != null && !seg.isRamp &&!seg.isEntrance) {
                    segment.setConnection(facing, true);
                    seg.setConnection(facing.getOpposite(), true);
                }
            }
        }
    }

    private void addSegment(PathSegment seg) {
        if (seg.x >= 0 && seg.x < sX &&
            seg.y >= 0 && seg.y < sY &&
            seg.z >= 0 && seg.z < sZ) {
            dungeonVolume[seg.x][seg.y][seg.z] = seg;
            dungeonList.add(seg);
            this.numSegments ++;
        }
    }

    private boolean isOccupied(CastlePos pos) {
        return dungeonVolume[pos.getX()][pos.getY()][pos.getZ()] != null;
    }

    private int getRandomDir(int dir) {
        float f = rand.nextFloat();
        if (f < chanceStraight) {
            return dir;
        }else if (f < chanceStraight + (1.0f-chanceStraight)*0.5f) {
            return CastleFacing.getHorizontal(dir).rotateY().getHorizontalIndex();
        }else {
            return CastleFacing.getHorizontal(dir).rotateYCCW().getHorizontalIndex();
        }
    }

    private boolean isWithinBounds(CastlePos pos) {
        if (this.entranceRampLength > this.rampPlaced) {
            return pos.getX() >= 0 && pos.getX() < sX &&
                    pos.getY() >= 0 && pos.getY() < sY &&
                    pos.getZ() >= 0 && pos.getZ() < sZ;
        }else {
            return pos.getX() >= 0 && pos.getX() < sX &&
                    pos.getY() >= 0 && pos.getY() < sY-this.entranceRampLength &&
                    pos.getZ() >= 0 && pos.getZ() < sZ;
        }

    }

    private PathSegment get(CastlePos index) {
        return dungeonVolume[index.getX()][index.getY()][index.getZ()];
    }

    public class PathSegment {
        public boolean isEntrance;
        int x;
        int y;
        int z;

        boolean isRamp = false;
        int elevation = 0;
        int rampRotation = -1;

        int roomID = -1;

        boolean[] pattern = new boolean[8];

        public PathSegment(int x, int y, int z) {
            super();
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public CastlePos getNextPos(int dir) {
            CastlePos offset = CastleFacing.getHorizontal(dir).getDirectionVec();
            return new CastlePos(x+offset.getX(), y+offset.getY(), z+offset.getZ());
        }

        public CastlePos getNextPos(CastleFacing facing) {
            CastlePos offset = facing.getDirectionVec();
            return new CastlePos(x+offset.getX(), y+offset.getY(), z+offset.getZ());
        }

        public boolean connectionAt(int index) {
            return pattern[index];
        }

        public boolean connectionAt(CastleFacing facing) {

            int i = ((facing.getHorizontalIndex()+2) % 4) * 2 + 1;
            return pattern[i];
        }

        public void setConnection(int index, boolean connected) {
            this.pattern[index] = connected;
        }

        public void setConnection(CastleFacing facing, boolean connected) {
            int i = ((facing.getHorizontalIndex()+2) % 4) * 2 + 1;
            pattern[i] = connected;

        }

        public boolean match(boolean[] o, int rotations) {
            int i = rotations*2;

            return (pattern.length == 8 && o.length == 8) &&
                    pattern[0] == o[(i+0)%8] &&
                    pattern[1] == o[(i+1)%8] &&
                    pattern[2] == o[(i+2)%8] &&
                    pattern[3] == o[(i+3)%8] &&
                    pattern[4] == o[(i+4)%8] &&
                    pattern[5] == o[(i+5)%8] &&
                    pattern[6] == o[(i+6)%8] &&
                    pattern[7] == o[(i+7)%8];

        }

        public int getRampRotation() {
            return this.rampRotation;
        }

        public boolean allowSpawner() {
            return !(this.isEntrance || this.isRamp);
        }

    }

    public void generateDungeon(CastleWorld world, int posX, int posY, int posZ, CastlePreset preset) {

        if (this.useBottomLayer) {

            this.cloneBottomLayer();
        }

        for (int i = 0; i < sX; i++) {
            for (int j = 0; j < sY; j++) {
                for (int k = 0; k < sZ; k++) {
                    boolean above = false;
                    for (int y = j+1; y < sY; y++) {
                        if (this.dungeonVolume[i][y][k] != null) {
                            above = true;
                            break;
                        }
                    }
                    boolean below = false;
                    boolean directlyBelow = false;
                    for (int y = j-1; y >= 0; y--) {
                        if (this.dungeonVolume[i][y][k] != null) {
                            below = true;
                            if (y == j-1) directlyBelow = true;
                            break;
                        }
                    }

                    PathSegment segment = dungeonVolume[i][j][k];
                    if (segment != null) {

                        if (segment.isRamp) {
                            if (segment.elevation == 1) {

                                int px = posX+(i*preset.getSizeXZ());
                                int py = posY+(j*preset.getSizeY());
                                int pz = posZ+(k*preset.getSizeXZ());
                                int r = segment.getRampRotation();

                                if (r== 0 || r == 2) r = (r+2)%4;

                                preset.getSegment(SegmentType.RAMP, segment.y, 0, this.sY, above, below, world.rand.nextInt()).placeSegment(world, px, py, pz, (r+1) %4);
                            }else {

                                int r = dungeonVolume[i][j-1][k].getRampRotation();
                                if (r== 0 || r == 2) r = (r+2)%4;
                                if (useRoof && !above && segment.y+1 < this.sY) {
                                    int px = posX+(i*preset.getSizeXZ());
                                    int py = posY+((j+1)*preset.getSizeY());
                                    int pz = posZ+(k*preset.getSizeXZ());
                                    int seed = segment.roomID > 0 ? segment.roomID : world.rand.nextInt();
                                    preset.getSegment(SegmentType.END, -1, 0, this.sY, above, below, seed).placeSegment(world, px, py, pz, (r+1) %4);
                                }
                            }
                        }else {

                            boolean ok = false;
                            for (CastleSegments tempSeg : CastleSegments.templateSegments.values()) {
                                if (!tempSeg.match) continue;
                                boolean match = false;
                                int r = 0;
                                while (!match && r < tempSeg.rotations) {
                                    if (segment.match(tempSeg.pattern, r)) {
                                        match = true;
                                    }else {
                                        r++;
                                    }
                                }
                                if (match) {
                                    int px = posX+(i*preset.getSizeXZ());
                                    int py = posY+(j*preset.getSizeY());
                                    int pz = posZ+(k*preset.getSizeXZ());
                                    if (tempSeg.type == SegmentType.END && j == (sY+this.startHeightLevel)%sY) {
                                        preset.getSegment(SegmentType.ENTRANCE, segment.y, 0, this.sY, above, below, world.rand.nextInt()).placeSegment(world, px, py, pz, r);
                                    }else {
                                        int seed = segment.roomID > 0 ? segment.roomID : world.rand.nextInt();
                                        preset.getSegment(tempSeg.type, segment.y, 0, this.sY, above, below, seed).placeSegment(world, px, py, pz, r);
                                    }
                                    ok = true;

                                    if (useRoof && !above && segment.y >= startHeightLevel && segment.y+1 < this.sY) {
                                        py = posY+((j+1)*preset.getSizeY());
                                        int seed = segment.roomID > 0 ? segment.roomID : world.rand.nextInt();
                                        preset.getSegment(tempSeg.type, -1, 0, this.sY, above, below, seed).placeSegment(world, px, py, pz, r);
                                    }
                                    break;
                                }
                            }
                            if (!ok) {
                                throw new IllegalStateException("Unmatched dungeon connection pattern");
                            }
                        }
                    }else {
                        if (useFoundations && above && !below) {
                            int px = posX+(i*preset.getSizeXZ());
                            int py = posY+(j*preset.getSizeY());
                            int pz = posZ+(k*preset.getSizeXZ());
                            preset.getSegment(SegmentType.FOUNDATION, j, 0, this.sY, above, below, world.rand.nextInt()).placeSegment(world, px, py, pz, 0);
                        }else if (usePillars && above ) {
                            int px = posX+(i*preset.getSizeXZ());
                            int py = posY+(j*preset.getSizeY());
                            int pz = posZ+(k*preset.getSizeXZ());
                            preset.getSegment(SegmentType.PILLARS, j, 0, this.sY, above, below, world.rand.nextInt()).placeSegment(world, px, py, pz, 0);
                        }else if (useRoof && directlyBelow && !above) {

                        }
                    }
                }
            }
        }
    }

    private void cloneBottomLayer() {
        PathSegment[][][] dungeonVolume2 = new PathSegment[sX][sY+1][sZ];
        for (int i = 0; i < sX; i++) {
            for (int j = 0; j < sY; j++) {
                for (int k = 0; k < sZ; k++) {
                    if (dungeonVolume[i][j][k] != null) {
                        dungeonVolume2[i][j+1][k] = dungeonVolume[i][j][k];
                        dungeonVolume2[i][j+1][k].y++;
                        if (j == 0) {
                            PathSegment seg2 = new PathSegment(i, j, k);
                            PathSegment seg = dungeonVolume[i][j][k];
                            seg2.pattern = seg.pattern;
                            dungeonVolume2[i][j][k] = seg2;
                        }
                    }
                }
            }
        }
        this.dungeonVolume = dungeonVolume2;
        sY = sY+1;
        this.startPos = new CastlePos(startPos.getX(), startPos.getY()+1, startPos.getZ());
    }

    public void generateNPCSpawners(CastleWorld world, int posX, int posY, int posZ, CastlePreset preset) {
        for (PathSegment seg : getSpawnPositions(preset.getSpawnDensity()))
            world.post(posX+seg.x*5+2, posY+seg.y*5+1, posZ+seg.z*5+2);
    }

    private List<PathSegment> getSpawnPositions(float spawnDensity) {
        int maxTries = 5;
        int tries = 0;

        int numSpawns = (int)((float)this.getNumSegments() * spawnDensity);

        List<PathSegment> spawners = new ArrayList<PathSegment>();

        while (numSpawns > 0 && tries < maxTries) {
            int index = this.rand.nextInt(this.dungeonList.size());
            PathSegment seg = this.dungeonList.get(index);
            if (seg != null && !spawners.contains(seg) && seg.allowSpawner()) {
                spawners.add(seg);
                numSpawns--;
                tries = 0;
            }else {
                tries++;
            }
        }

        return spawners;
    }

    public record Node(int x,int y,int z,boolean entrance,boolean ramp,int elevation,int rotation,int room,int pattern) {}
    public List<Node> nodes() {
        var result=new ArrayList<Node>();
        for(var s:dungeonList) { int bits=0; for(int i=0;i<8;i++) if(s.pattern[i]) bits|=1<<i;
            result.add(new Node(s.x,s.y,s.z,s.isEntrance,s.isRamp,s.elevation,s.rampRotation,s.roomID,bits)); }
        return List.copyOf(result);
    }
}
