package techguns.modern.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.*;

public final class IndustrialLampBlock extends Block {
    public static final MapCodec<IndustrialLampBlock> CODEC=RecordCodecBuilder.mapCodec(i->i.group(com.mojang.serialization.Codec.BOOL.fieldOf("lantern").forGetter(b->b.lantern),propertiesCodec()).apply(i,IndustrialLampBlock::new));
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    public static final Map<Direction,BooleanProperty> CONNECTIONS=Arrays.stream(Direction.values()).collect(java.util.stream.Collectors.toMap(d->d,d->BooleanProperty.create(d.getName())));
    private final boolean lantern;
    public IndustrialLampBlock(boolean lantern,Properties p) { super(p); this.lantern=lantern; var s=stateDefinition.any().setValue(FACING,Direction.DOWN); for(var prop:CONNECTIONS.values()) s=s.setValue(prop,false); registerDefaultState(s); }
    public boolean lantern() { return lantern; }
    @Override public MapCodec<IndustrialLampBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(FACING); CONNECTIONS.values().forEach(b::add); }
    public static boolean canAttach(BlockGetter level,BlockPos pos,Direction facing) {
        var support=pos.relative(facing); var s=level.getBlockState(support);
        // Source checks the support's TOP for either vertical attachment, and uses
        // facing (not opposite) for horizontal BlockFaceShape.
        return facing.getAxis().isVertical()?LegacyBlockSupport.torchTop(level,support)
                :!LegacyBlockSupport.pistonException(s) && LegacyBlockSupport.solid(level,support,facing);
    }
    private BlockState connected(BlockState s,BlockGetter level,BlockPos pos) {
        for(var d:Direction.values()) s=s.setValue(CONNECTIONS.get(d),lantern && canAttach(level,pos,d)); return s;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return connected(defaultBlockState().setValue(FACING,c.getClickedFace().getOpposite()),c.getLevel(),c.getClickedPos()); }
    @Override protected boolean canSurvive(BlockState s,LevelReader l,BlockPos p) { return canAttach(l,p,s.getValue(FACING)); }
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved) {
        if(!old.is(this) && !l.isClientSide()) {
            if(!canSurvive(s,l,p)) l.destroyBlock(p,true);
            else { var connected=connected(s,l,p); if(connected!=s) l.setBlock(p,connected,UPDATE_CLIENTS|UPDATE_KNOWN_SHAPE); }
        }
    }
    @Override protected BlockState updateShape(BlockState s,LevelReader l,ScheduledTickAccess t,BlockPos p,Direction d,BlockPos n,BlockState ns,RandomSource r) {
        return canSurvive(s,l,p)?connected(s,l,p):Blocks.AIR.defaultBlockState();
    }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) {
        if(lantern) return Block.box(4,2,4,12,14,12);
        return switch(s.getValue(FACING)) {
            case DOWN->Block.box(4,0,4,12,3,12); case UP->Block.box(4,13,4,12,16,12);
            case NORTH->Block.box(4,4,0,12,12,3); case SOUTH->Block.box(4,4,13,12,12,16);
            case WEST->Block.box(0,4,4,3,12,12); case EAST->Block.box(13,4,4,16,12,12);
        };
    }
    @Override protected VoxelShape getBlockSupportShape(BlockState s,BlockGetter l,BlockPos p) { return Shapes.empty(); }
    @Override protected boolean isPathfindable(BlockState s,PathComputationType t) { return false; }
    @Override protected BlockState rotate(BlockState s,Rotation r) { return transform(s,r::rotate); }
    @Override protected BlockState mirror(BlockState s,Mirror m) { return transform(s,m::mirror); }
    private static BlockState transform(BlockState s,java.util.function.UnaryOperator<Direction> f) {
        var out=s.setValue(FACING,f.apply(s.getValue(FACING)));
        for(var d:Direction.values()) out=out.setValue(CONNECTIONS.get(f.apply(d)),s.getValue(CONNECTIONS.get(d))); return out;
    }
}
