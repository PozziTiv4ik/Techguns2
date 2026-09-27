package techguns.modern.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.*;
import techguns.core.MilitaryCrates;

/** Original crate outline/collision and vertical center support; this block has no inventory. */
public final class MilitaryCrateBlock extends Block {
    public static final MapCodec<MilitaryCrateBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(0,8).fieldOf("variant").forGetter(b -> b.variant.metadata()), propertiesCodec()).apply(i, MilitaryCrateBlock::new));
    private static final VoxelShape SHAPE = Block.box(.5,0,.5,15.5,16,15.5);
    private final MilitaryCrates.Variant variant;
    public MilitaryCrateBlock(int metadata, Properties properties) { super(properties); variant = MilitaryCrates.byMetadata(metadata); }
    public MilitaryCrates.Variant variant() { return variant; }
    @Override public MapCodec<MilitaryCrateBlock> codec() { return CODEC; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return SHAPE; }
    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return false; }
}
