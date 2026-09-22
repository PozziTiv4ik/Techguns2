package techguns.modern.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import techguns.modern.*;

public final class SlimyContent {
    private static final DeferredRegister.Blocks REGISTRY=DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final DeferredBlock<Block> EGGS=REGISTRY.registerBlock("bugnest_eggs",Block::new,
            p->p.mapColor(MapColor.COLOR_GREEN).strength(4,4).sound(SoundType.SLIME_BLOCK).requiresCorrectToolForDrops());
    public static final DeferredBlock<Trail> TRAIL=REGISTRY.registerBlock("slimyladder",Trail::new,
            p->p.forceSolidOff().strength(0).sound(SoundType.SLIME_BLOCK).noOcclusion().pushReaction(PushReaction.DESTROY));
    static { TGContent.ITEMS.registerSimpleBlockItem(EGGS); TGContent.ITEMS.registerSimpleBlockItem(TRAIL); }
    public static final class Trail extends LadderBlock {
        public static final MapCodec<LadderBlock> CODEC=simpleCodec(Trail::new);
        public Trail(Properties properties) { super(properties); registerDefaultState(defaultBlockState().setValue(FACING,Direction.SOUTH)); }
        @Override public MapCodec<LadderBlock> codec() { return CODEC; }
        @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) {
            var facing=state.getValue(FACING); var support=pos.relative(facing.getOpposite()); var block=level.getBlockState(support);
            var b=block.getBlock();
            boolean excluded=b instanceof LeavesBlock || b instanceof ShulkerBoxBlock || b instanceof TrapDoorBlock
                    || b instanceof AbstractCauldronBlock || b instanceof StainedGlassBlock || block.is(Blocks.BEACON)
                    || block.is(Blocks.GLASS) || block.is(Blocks.GLOWSTONE) || block.is(Blocks.ICE) || block.is(Blocks.SEA_LANTERN)
                    || block.is(Blocks.PISTON) || block.is(Blocks.STICKY_PISTON) || block.is(Blocks.PISTON_HEAD);
            return !excluded && block.isFaceSturdy(level,support,facing) && !block.isSignalSource();
        }
    }
    public static void register(IEventBus bus) { REGISTRY.register(bus); }
    private SlimyContent() {}
}
