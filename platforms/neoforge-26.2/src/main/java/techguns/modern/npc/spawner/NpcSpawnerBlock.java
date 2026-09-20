package techguns.modern.npc.spawner;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** Original HOLE: small outline, no collision, no survival drops. Soldier preset awaits ArmySoldier. */
public final class NpcSpawnerBlock extends BaseEntityBlock {
    private static final MapCodec<NpcSpawnerBlock> CODEC=simpleCodec(NpcSpawnerBlock::new);
    private static final VoxelShape OUTLINE=Block.box(2,0,2,14,2,14);
    public NpcSpawnerBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<NpcSpawnerBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new NpcSpawnerBlockEntity(pos,state); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return OUTLINE; }
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return Shapes.empty(); }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!level.isClientSide() && level.getBlockEntity(pos) instanceof NpcSpawnerBlockEntity spawner) spawner.defaultHole();
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide()?null:createTickerHelper(type,NpcSpawnerContent.ENTITY.get(),NpcSpawnerBlockEntity::serverTick);
    }
}
