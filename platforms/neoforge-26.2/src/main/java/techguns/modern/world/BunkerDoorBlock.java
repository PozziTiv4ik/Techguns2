package techguns.modern.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import techguns.modern.TGContent;

public final class BunkerDoorBlock extends DoorBlock {
    public static final MapCodec<BunkerDoorBlock> CODEC=simpleCodec(BunkerDoorBlock::new);
    // Keep iron-door redstone sounds and AI/wind behavior, with original metal footsteps.
    private static final BlockSetType TYPE=new BlockSetType("techguns:bunkerdoor",false,false,false,BlockSetType.PressurePlateSensitivity.EVERYTHING,
            SoundType.METAL,SoundEvents.IRON_DOOR_CLOSE,SoundEvents.IRON_DOOR_OPEN,SoundEvents.IRON_TRAPDOOR_CLOSE,SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,SoundEvents.STONE_BUTTON_CLICK_OFF,SoundEvents.STONE_BUTTON_CLICK_ON);
    public BunkerDoorBlock(Properties p) { super(TYPE,p); }
    @Override public MapCodec<BunkerDoorBlock> codec() { return CODEC; }
    @Override protected BlockState updateShape(BlockState s,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction direction,BlockPos neighbour,BlockState other,RandomSource random) {
        boolean otherHalf=direction.getAxis()==Direction.Axis.Y && (s.getValue(HALF)==DoubleBlockHalf.LOWER)==(direction==Direction.UP);
        if(otherHalf && !other.is(this)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(s,level,ticks,pos,direction,neighbour,other,random);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        if(c.getClickedFace()!=Direction.UP) return null;
        var s=super.getStateForPlacement(c); if(s==null) return null;
        var l=c.getLevel(); var pos=c.getClickedPos(); var f=c.getHorizontalDirection(); var right=pos.relative(f.getClockWise()); var left=pos.relative(f.getCounterClockWise());
        int ls=(LegacyBlockSupport.normalCube(l.getBlockState(left))?1:0)+(LegacyBlockSupport.normalCube(l.getBlockState(left.above()))?1:0);
        int rs=(LegacyBlockSupport.normalCube(l.getBlockState(right))?1:0)+(LegacyBlockSupport.normalCube(l.getBlockState(right.above()))?1:0);
        boolean ld=l.getBlockState(left).is(this)||l.getBlockState(left.above()).is(this), rd=l.getBlockState(right).is(this)||l.getBlockState(right.above()).is(this);
        double x=c.getClickLocation().x-pos.getX(), z=c.getClickLocation().z-pos.getZ();
        boolean hinge=f.getStepX()<0 && z<.5 || f.getStepX()>0 && z>.5 || f.getStepZ()<0 && x>.5 || f.getStepZ()>0 && x<.5;
        if((!ld || rd) && rs<=ls) { if(rd && !ld || rs<ls) hinge=false; } else hinge=true;
        return s.setValue(HINGE,hinge?DoorHingeSide.RIGHT:DoorHingeSide.LEFT);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        var lower=state.getValue(HALF)==DoubleBlockHalf.LOWER?pos:pos.below(); var s=level.getBlockState(lower);
        if(!s.is(this)) return InteractionResult.PASS;
        if(!level.isClientSide()) {
            boolean open=!s.getValue(OPEN); var f=s.getValue(FACING); var other=lower.relative(s.getValue(HINGE)==DoorHingeSide.LEFT?f.getClockWise():f.getCounterClockWise());
            var partner=level.getBlockState(other);
            if(partner.is(this) && partner.getValue(OPEN)==s.getValue(OPEN)) level.setBlock(other,partner.setValue(OPEN,open),10);
            level.setBlock(lower,s.setValue(OPEN,open),10);
            level.playSound(null,pos,FortificationContent.DOOR_SOUND.get(),SoundSource.BLOCKS,1,1);
            level.gameEvent(player,open?GameEvent.BLOCK_OPEN:GameEvent.BLOCK_CLOSE,pos);
        }
        return InteractionResult.SUCCESS;
    }
}
