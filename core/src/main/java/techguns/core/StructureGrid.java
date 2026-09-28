package techguns.core;

/** WorldGenTGStructureSpawn's ordered modulo tests, including non-divisible intervals and negative chunks. */
public record StructureGrid(int small,int medium,int big) {
    public static final int MAX_INTERVAL=100000;
    public static final StructureGrid DEFAULT=new StructureGrid(16,32,64);
    public enum Size {
        SMALL(16,4),MEDIUM(32,8),BIG(64,16);
        private final int defaultInterval,minimum;
        Size(int defaultInterval,int minimum) {this.defaultInterval=defaultInterval;this.minimum=minimum;}
        public int defaultInterval() {return defaultInterval;}
        public int minimum() {return minimum;}
    }
    public StructureGrid {
        validate(small,Size.SMALL);validate(medium,Size.MEDIUM);validate(big,Size.BIG);
    }
    private static void validate(int value,Size size) {
        if(value<size.minimum()||value>MAX_INTERVAL) throw new IllegalArgumentException("Invalid "+size+" structure interval: "+value);
    }
    public int interval(Size size) {return switch(size) {case SMALL->small;case MEDIUM->medium;case BIG->big;};}
    private static boolean at(int x,int z,int interval) {return x%interval==0&&z%interval==0;}
    public boolean accepts(Size size,int x,int z) {
        if(at(x,z,big)) return size==Size.BIG;
        if(at(x,z,medium)) return size==Size.MEDIUM;
        return size==Size.SMALL&&at(x,z,small);
    }
    /** Same zero-offset sectors as native random_spread, without its 4096 codec limit. */
    public static int sectorOrigin(int coordinate,int interval) {
        if(interval<1||interval>MAX_INTERVAL) throw new IllegalArgumentException("Invalid interval");
        return Math.toIntExact((long)Math.floorDiv(coordinate,interval)*interval);
    }
}
