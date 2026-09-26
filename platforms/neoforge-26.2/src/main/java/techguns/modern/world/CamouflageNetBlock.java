package techguns.modern.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.*;
import techguns.core.CamouflageNets;

public final class CamouflageNetBlock extends Block {
    public static final MapCodec<CamouflageNetBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.fieldOf("canopy").forGetter(CamouflageNetBlock::canopy), propertiesCodec()).apply(i, CamouflageNetBlock::new));
    public static final List<Direction> DIRECTIONS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    public static final List<BooleanProperty> SIDES = DIRECTIONS.stream().map(d -> BooleanProperty.create(d.getName())).toList();
    private static final VoxelShape CENTER = box(7, 0, 7, 9, 16, 9);
    private static final List<VoxelShape> ARMS = List.of(box(7, 0, 0, 9, 16, 7), box(9, 0, 7, 16, 16, 9),
            box(7, 0, 9, 9, 16, 16), box(0, 0, 7, 7, 16, 9));
    private static final List<VoxelShape> TOP = CamouflageNets.CANOPY_BOXES.stream()
            .map(b -> box(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1())).toList();
    private final boolean canopy;

    public CamouflageNetBlock(boolean canopy, Properties properties) {
        super(properties);
        this.canopy = canopy;
        var state = stateDefinition.any();
        for (var side : SIDES) state = state.setValue(side, false);
        registerDefaultState(state);
    }
    public boolean canopy() { return canopy; }
    @Override public MapCodec<CamouflageNetBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { SIDES.forEach(builder::add); }

    public BlockState connected(BlockState state, BlockGetter level, BlockPos pos) {
        for (int i = 0; i < 4; i++) {
            var block = level.getBlockState(pos.relative(DIRECTIONS.get(i))).getBlock();
            state = state.setValue(SIDES.get(i), block instanceof CamouflageNetBlock other && other.canopy == canopy);
        }
        return state;
    }
    private int mask(BlockState state) {
        int mask = 0;
        for (int i = 0; i < 4; i++) if (state.getValue(SIDES.get(i))) mask |= 8 >> i;
        return mask;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connected(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this) && !level.isClientSide()) {
            var updated = connected(state, level, pos);
            if (updated != state) level.setBlock(pos, updated, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
        }
    }
    @Override protected BlockState updateShape(BlockState s, LevelReader l, ScheduledTickAccess ticks, BlockPos p,
                                               Direction d, BlockPos neighbor, BlockState ns, RandomSource random) {
        return connected(s, l, p);
    }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        s = connected(s, l, p);
        if (canopy) return TOP.get(mask(s));
        return box(s.getValue(SIDES.get(3)) ? 0 : 7, 0, s.getValue(SIDES.get(0)) ? 0 : 7,
                s.getValue(SIDES.get(1)) ? 16 : 9, 16, s.getValue(SIDES.get(2)) ? 16 : 9);
    }
    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        if (canopy) return getShape(s, l, p, c);
        s = connected(s, l, p);
        var shape = CENTER;
        for (int i = 0; i < 4; i++) if (s.getValue(SIDES.get(i))) shape = Shapes.or(shape, ARMS.get(i));
        return shape;
    }
    @Override protected VoxelShape getBlockSupportShape(BlockState s, BlockGetter l, BlockPos p) { return Shapes.empty(); }
    @Override protected boolean isPathfindable(BlockState s, PathComputationType type) { return false; }
    @Override protected BlockState rotate(BlockState s, Rotation rotation) { return transform(s, rotation::rotate); }
    @Override protected BlockState mirror(BlockState s, Mirror mirror) { return transform(s, mirror::mirror); }
    private static BlockState transform(BlockState state, java.util.function.UnaryOperator<Direction> transform) {
        var result = state;
        for (int i = 0; i < 4; i++) result = result.setValue(SIDES.get(DIRECTIONS.indexOf(transform.apply(DIRECTIONS.get(i)))), state.getValue(SIDES.get(i)));
        return result;
    }
}
