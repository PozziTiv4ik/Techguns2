package techguns.modern.machine.reaction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

public final class ReactionChamberBlock extends BaseEntityBlock {
    public enum Part { HOUSING, GLASS, CONTROLLER }
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FORMED=techguns.modern.machine.multiblock.MachineFormation.FORMED;
    public static final MapCodec<ReactionChamberBlock> CODEC=RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.xmap(Part::valueOf,Part::name).fieldOf("part").forGetter(b -> b.part),propertiesCodec()).apply(i,ReactionChamberBlock::new));
    private final Part part;
    public ReactionChamberBlock(Part part,Properties properties) { super(properties); this.part=part; registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH).setValue(FORMED,false)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,FORMED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING,context.getHorizontalDirection()); }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.getValue(FORMED) ? state : state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) { return rotate(state,mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return part==Part.CONTROLLER ? new ReactionChamberBlockEntity(pos,state) : new ReactionPartBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        if(level.isClientSide()) return null;
        return part==Part.CONTROLLER ? createTickerHelper(type,ReactionContent.CONTROLLER_ENTITY.get(),ReactionChamberBlockEntity::tick)
                : createTickerHelper(type,ReactionContent.PART_ENTITY.get(),ReactionPartBlockEntity::tick);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack item) {
        super.setPlacedBy(level,pos,state,placer,item);
        if(placer instanceof Player player && level.getBlockEntity(pos) instanceof ReactionChamberBlockEntity master) master.setOwner(player);
    }
    private static ReactionChamberBlockEntity master(Level level,BlockPos pos) {
        var entity=level.getBlockEntity(pos);
        return entity instanceof ReactionChamberBlockEntity master ? master : entity instanceof ReactionPartBlockEntity slave ? slave.master() : null;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(level instanceof ServerLevel) {
            var master=master(level,pos);
            if(master!=null && master.stillValid(player)) {
                if(!master.formed() && !master.form(hit.getDirection(),player)) player.sendOverlayMessage(Component.translatable("gui.techguns.reaction.unformed"));
                else player.openMenu(master);
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        var master=master(level,pos);
        if(master!=null && master.stillValid(player)) {
            if(level.isClientSide()) return InteractionResult.SUCCESS;
            if(FluidUtil.interactWithFluidHandler(player,hand,pos,master.tank(),null)) return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        if(state.getValue(FORMED) && level.getBlockEntity(pos) instanceof ReactionPartBlockEntity slave && slave.controller()!=null)
            return ReactionStructure.shape(pos.subtract(slave.controller().relative(state.getValue(FACING))));
        return Shapes.block();
    }
}
