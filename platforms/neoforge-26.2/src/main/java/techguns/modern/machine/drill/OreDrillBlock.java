package techguns.modern.machine.drill;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import techguns.modern.machine.multiblock.*;

public final class OreDrillBlock extends BaseEntityBlock {
    public static final BooleanProperty END_CAP=BooleanProperty.create("end_cap");
    public static final MapCodec<OreDrillBlock> CODEC=RecordCodecBuilder.mapCodec(i->i.group(Codec.STRING.fieldOf("kind").forGetter(b->b.kind),propertiesCodec()).apply(i,OreDrillBlock::new));
    public final String kind;
    public OreDrillBlock(String kind,Properties p) { super(p); this.kind=kind; registerDefaultState(stateDefinition.any().setValue(MachineFormation.FACING,Direction.NORTH).setValue(MachineFormation.FORMED,false).setValue(END_CAP,false)); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(MachineFormation.FACING,MachineFormation.FORMED,END_CAP); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        var l=c.getLevel(); var p=c.getClickedPos(); String error=null;
        if(kind.equals("rod") && !OreDrillStructure.rodPlacementAllowed(l,p)) error="rod_placement";
        if(kind.equals("controller")) for(var dir:Direction.values()) if(l.getBlockState(p.relative(dir)).is(this)) { error="controller_placement"; break; }
        if(error!=null) { if(c.getPlayer()!=null) c.getPlayer().sendOverlayMessage(Component.translatable("gui.techguns.drill."+error)); return null; }
        return defaultBlockState().setValue(MachineFormation.FACING,c.getHorizontalDirection().getOpposite());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return kind.equals("controller")?new OreDrillBlockEntity(pos,state):new OreDrillPart(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type) {
        if(l.isClientSide()) return null;
        return kind.equals("controller")?createTickerHelper(type,OreDrillContent.CONTROLLER.get(),OreDrillBlockEntity::tick):createTickerHelper(type,OreDrillContent.PART.get(),LinkedMachinePartBlockEntity::tick);
    }
    @Override public void setPlacedBy(Level l,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) { super.setPlacedBy(l,pos,state,placer,stack); if(placer instanceof Player p && l.getBlockEntity(pos) instanceof OreDrillBlockEntity d) d.setOwner(p); }
    private static OreDrillBlockEntity machine(Level l,BlockPos pos) { return l.getBlockEntity(pos) instanceof OreDrillBlockEntity d?d:l.getBlockEntity(pos) instanceof OreDrillPart part?part.master():null; }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit) {
        var d=machine(l,pos);
        if(d!=null && d.stillValid(p)) { if(l.isClientSide()) return InteractionResult.SUCCESS; if(FluidUtil.interactWithFluidHandler(p,hand,pos,d.fluidAutomation(),null)) return InteractionResult.SUCCESS; }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level l,BlockPos pos,Player p,BlockHitResult hit) {
        if(l instanceof ServerLevel && !p.isSpectator()) {
            var d=machine(l,pos);
            if(d!=null && d.stillValid(p)) {
                if(!d.formed() && !d.form(p)) p.sendOverlayMessage(Component.translatable("gui.techguns.drill.unformed"));
                else p.openMenu(d);
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected BlockState rotate(BlockState state,Rotation r) { return state.getValue(MachineFormation.FORMED)?state:state.setValue(MachineFormation.FACING,r.rotate(state.getValue(MachineFormation.FACING))); }
    @Override protected BlockState mirror(BlockState s,Mirror m) { return rotate(s,m.getRotation(s.getValue(MachineFormation.FACING))); }
}
