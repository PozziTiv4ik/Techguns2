package techguns.modern.machine.fabricator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import techguns.modern.machine.ProcessingMachineBlockEntity;
import techguns.modern.machine.multiblock.MachineFormation;
import techguns.modern.machine.multiblock.LinkedMachinePartBlockEntity;

public final class FabricatorBlock extends BaseEntityBlock {
    public enum Part { HOUSING, GLASS, CONTROLLER }
    private final Part part;
    public static final MapCodec<FabricatorBlock> CODEC=RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.xmap(Part::valueOf,Part::name).fieldOf("part").forGetter(b -> b.part),propertiesCodec()).apply(i,FabricatorBlock::new));
    public FabricatorBlock(Part part,Properties properties) { super(properties); this.part=part; registerDefaultState(stateDefinition.any().setValue(MachineFormation.FACING,Direction.SOUTH).setValue(MachineFormation.FORMED,false)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(MachineFormation.FACING,MachineFormation.FORMED); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(MachineFormation.FACING,context.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) { return state.getValue(MachineFormation.FORMED) ? state : state.setValue(MachineFormation.FACING,rotation.rotate(state.getValue(MachineFormation.FACING))); }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) { return rotate(state,mirror.getRotation(state.getValue(MachineFormation.FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return part==Part.CONTROLLER ? new FabricatorBlockEntity(pos,state) : new FabricatorPartBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        if(level.isClientSide()) return null;
        return part==Part.CONTROLLER ? createTickerHelper(type,FabricatorContent.CONTROLLER_ENTITY.get(),ProcessingMachineBlockEntity::tick)
                : createTickerHelper(type,FabricatorContent.PART_ENTITY.get(),LinkedMachinePartBlockEntity::tick);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if(placer instanceof Player player && level.getBlockEntity(pos) instanceof FabricatorBlockEntity machine) machine.setOwner(player);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(level instanceof ServerLevel) {
            var entity=level.getBlockEntity(pos);
            FabricatorBlockEntity machine=entity instanceof FabricatorBlockEntity controller ? controller : entity instanceof FabricatorPartBlockEntity linked ? linked.master() : null;
            if(machine!=null && machine.stillValid(player)) {
                if(!machine.formed() && !machine.form(hit.getDirection(),player)) player.sendOverlayMessage(Component.translatable("gui.techguns.fabricator.unformed"));
                else player.openMenu(machine);
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return part==Part.GLASS && state.getValue(MachineFormation.FORMED) ? Block.box(0,0,0,16,.9f*16,16) : Shapes.block();
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return getCollisionShape(state,level,pos,context); }
}
