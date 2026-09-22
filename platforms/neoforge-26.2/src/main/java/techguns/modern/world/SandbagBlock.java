package techguns.modern.world;

import com.mojang.serialization.MapCodec;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.*;

public final class SandbagBlock extends Block {
    public static final MapCodec<SandbagBlock> CODEC=simpleCodec(SandbagBlock::new);
    public static final List<Direction> DIRECTIONS=List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST);
    public static final List<BooleanProperty> SIDES=DIRECTIONS.stream().map(d->BooleanProperty.create(d.getName())).toList();
    public static final List<BooleanProperty> CORNERS=List.of("corner_ne","corner_es","corner_sw","corner_wn").stream().map(BooleanProperty::create).toList();
    private static final VoxelShape CENTER=Block.box(4,0,4,12,16,12);
    private static final List<VoxelShape> ARMS=List.of(Block.box(4,0,0,12,16,4),Block.box(12,0,4,16,16,12),Block.box(4,0,12,12,16,16),Block.box(0,0,4,4,16,12));
    public SandbagBlock(Properties p) { super(p); var s=stateDefinition.any(); for(var prop:SIDES) s=s.setValue(prop,false); for(var prop:CORNERS) s=s.setValue(prop,false); registerDefaultState(s); }
    @Override public MapCodec<SandbagBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { SIDES.forEach(b::add); CORNERS.forEach(b::add); }
    private static boolean gate(BlockState s,Direction d) { return s.getBlock() instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(s,d); }
    /** Forge 1.12 queried the OTHER block's callback before its own face rule. */
    private static boolean connects(BlockGetter level,BlockPos from,Direction d) {
        var pos=from.relative(d); var s=level.getBlockState(pos); var own=level.getBlockState(from); var b=s.getBlock();
        if(b instanceof SandbagBlock && (own.getBlock() instanceof SandbagBlock || own.getBlock() instanceof FenceBlock)) return true;
        if(b instanceof FenceBlock || b instanceof WallBlock || b instanceof IronBarsBlock) {
            if(!LegacyBlockSupport.fenceException(own) && LegacyBlockSupport.solid(level,from,d)) return true;
            if(!(b instanceof IronBarsBlock) && gate(own,d)) return true;
            if(b instanceof FenceBlock && own.getBlock() instanceof FenceBlock
                    && s.is(net.minecraft.tags.BlockTags.WOODEN_FENCES)==own.is(net.minecraft.tags.BlockTags.WOODEN_FENCES)) return true;
            if(b instanceof WallBlock && own.getBlock() instanceof WallBlock || b instanceof IronBarsBlock && own.getBlock() instanceof IronBarsBlock) return true;
        }
        return !LegacyBlockSupport.fenceException(s) && LegacyBlockSupport.solid(level,pos,d.getOpposite()) || gate(s,d);
    }
    public BlockState connected(BlockState state,BlockGetter level,BlockPos pos) {
        // During getStateForPlacement the origin is still air. Other sandbags' callback
        // sees the new block only after placement, so direct sandbag links are explicit.
        boolean[] side=new boolean[4];
        for(int i=0;i<4;i++) {
            var d=DIRECTIONS.get(i); side[i]=level.getBlockState(pos.relative(d)).getBlock() instanceof SandbagBlock || connects(level,pos,d);
            state=state.setValue(SIDES.get(i),side[i]);
        }
        for(int i=0;i<4;i++) { int j=(i+1)%4;
            boolean corner=side[i] && side[j] && (connects(level,pos.relative(DIRECTIONS.get(i)),DIRECTIONS.get(j)) || connects(level,pos.relative(DIRECTIONS.get(j)),DIRECTIONS.get(i)));
            state=state.setValue(CORNERS.get(i),corner);
        }
        return state;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return connected(defaultBlockState(),c.getLevel(),c.getClickedPos()); }
    @Override protected BlockState updateShape(BlockState s,LevelReader l,ScheduledTickAccess t,BlockPos p,Direction d,BlockPos n,BlockState ns,RandomSource r) { return connected(s,l,p); }
    @Override protected VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) {
        s=connected(s,l,p); var shape=CENTER; for(int i=0;i<4;i++) if(s.getValue(SIDES.get(i))) shape=Shapes.or(shape,ARMS.get(i)); return shape;
    }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) {
        s=connected(s,l,p); return Block.box(s.getValue(SIDES.get(3))?0:4,0,s.getValue(SIDES.get(0))?0:4,s.getValue(SIDES.get(1))?16:12,16,s.getValue(SIDES.get(2))?16:12);
    }
    @Override protected VoxelShape getBlockSupportShape(BlockState s,BlockGetter l,BlockPos p) { return CENTER; }
    @Override protected boolean isPathfindable(BlockState s,PathComputationType t) { return false; }
    @Override protected boolean skipRendering(BlockState s,BlockState n,Direction d) { return false; }
    @Override protected BlockState rotate(BlockState s,Rotation r) { return transform(s,r::rotate); }
    @Override protected BlockState mirror(BlockState s,Mirror m) { return transform(s,m::mirror); }
    private static BlockState transform(BlockState s,java.util.function.UnaryOperator<Direction> transform) {
        var result=s;
        for(int i=0;i<4;i++) {
            int a=DIRECTIONS.indexOf(transform.apply(DIRECTIONS.get(i))), b=DIRECTIONS.indexOf(transform.apply(DIRECTIONS.get((i+1)%4)));
            result=result.setValue(SIDES.get(a),s.getValue(SIDES.get(i))).setValue(CORNERS.get((a+1)%4==b?a:b),s.getValue(CORNERS.get(i)));
        }
        return result;
    }
    /** Synchronize diagonal render properties that no longer have getActualState in 26.2. */
    public static void refreshAround(Level level,BlockPos changed) {
        refresh(level,changed);
        for(int x:new int[]{-1,1}) for(int z:new int[]{-1,1}) refresh(level,changed.offset(x,0,z));
    }
    private static void refresh(Level level,BlockPos pos) {
        if(!level.hasChunkAt(pos)) return;
        var old=level.getBlockState(pos);
        if(old.getBlock() instanceof SandbagBlock block) {
            var state=block.connected(old,level,pos);
            if(state!=old) level.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        }
    }
}
