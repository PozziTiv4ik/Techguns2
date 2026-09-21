package techguns.modern.machine.drill;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import techguns.core.OreDrillRules;
import techguns.modern.machine.multiblock.MachineFormation;
import techguns.modern.world.OreClusterContent;

public final class OreDrillStructure {
    public record Shape(Direction direction,OreDrillRules.Size size) {}
    public static BlockPos position(BlockPos origin,Direction direction,OreDrillRules.Cell c) {
        Direction s1=direction.getAxis()==Direction.Axis.Y?Direction.NORTH:Direction.DOWN;
        Direction s2=direction.getAxis()==Direction.Axis.X?Direction.NORTH:Direction.EAST;
        return origin.relative(direction,c.axial()).relative(s1,c.side1()).relative(s2,c.side2());
    }
    public static boolean cluster(Level level,BlockPos pos) { return level.hasChunkAt(pos) && OreClusterContent.BLOCKS.values().stream().anyMatch(b->level.getBlockState(pos).is(b.get())); }
    private static boolean is(Level level,BlockPos pos,String kind) {
        if(!level.hasChunkAt(pos)) return false; var s=level.getBlockState(pos);
        return s.is(OreDrillContent.BLOCKS.get(kind).get()) && !s.getValue(MachineFormation.FORMED);
    }
    public static Shape detect(Level level,BlockPos origin) {
        Direction direction=null;
        for(var dir:Direction.values()) if(is(level,origin.relative(dir),"rod") || is(level,origin.relative(dir),"engine")) { if(direction!=null) return null; direction=dir; }
        if(direction==null) return null;
        int engines=0,rods=0,radius=0;
        while(engines<16 && is(level,origin.relative(direction,engines+1),"engine")) engines++;
        while(rods<16 && is(level,origin.relative(direction,engines+rods+1),"rod")) rods++;
        if(!cluster(level,origin.relative(direction,engines+rods+1))) return null;
        if(engines>0) for(var side:Direction.values()) if(side.getAxis()!=direction.getAxis()) {
            int count=0; while(count<3 && is(level,origin.relative(direction).relative(side,count+1),"engine")) count++; radius=Math.max(radius,count);
        }
        if(!OreDrillRules.valid(engines,rods,radius)) return null;
        var size=new OreDrillRules.Size(engines,rods,radius);
        for(var cell:OreDrillRules.air(size)) { var pos=position(origin,direction,cell); if(!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) return null; }
        return new Shape(direction,size);
    }
    public static List<MachineFormation.Part> parts(BlockPos origin,Direction direction,OreDrillRules.Size size,List<BlockPos> caps) {
        var parts=new ArrayList<MachineFormation.Part>();
        for(var c:OreDrillRules.parts(size)) parts.add(new MachineFormation.Part(position(origin,direction,c),OreDrillContent.BLOCKS.get(c.kind()).get(),0));
        for(var pos:caps) parts.add(new MachineFormation.Part(pos,OreDrillContent.BLOCKS.get("scaffold").get(),3));
        return List.copyOf(parts);
    }
    public static int connected(Level level,BlockPos start,int limit) {
        if(!cluster(level,start)) return 0; Block block=level.getBlockState(start).getBlock();
        var seen=new HashSet<BlockPos>(); var queue=new ArrayDeque<BlockPos>(); queue.add(start); int count=0;
        while(!queue.isEmpty()) { var p=queue.remove(); if(!seen.add(p) || !level.hasChunkAt(p) || !level.getBlockState(p).is(block)) continue;
            if(++count>=limit) return count; for(var d:Direction.values()) queue.add(p.relative(d));
        }
        return count;
    }
    /** Bounded iterative traversal, stopping at unavailable chunks rather than force-loading a world. */
    public static boolean rodPlacementAllowed(Level level,BlockPos pos) {
        BlockPos cluster=null;
        for(var dir:Direction.values()) if(cluster(level,pos.relative(dir))) { cluster=pos.relative(dir); break; }
        if(cluster==null) { for(var dir:Direction.values()) if(is(level,pos.relative(dir),"rod")) return true; return false; }
        var block=level.getBlockState(cluster).getBlock(); var seen=new HashSet<BlockPos>(); var queue=new ArrayDeque<BlockPos>(); queue.add(cluster);
        while(!queue.isEmpty()) {
            var p=queue.remove(); if(!seen.add(p)) continue;
            if(!level.hasChunkAt(p) || seen.size()>65536) return false;
            var state=level.getBlockState(p);
            if(state.is(OreDrillContent.BLOCKS.get("rod").get())) return false;
            if(state.is(block)) for(var dir:Direction.values()) queue.add(p.relative(dir));
        }
        return true;
    }
    private OreDrillStructure() {}
}
