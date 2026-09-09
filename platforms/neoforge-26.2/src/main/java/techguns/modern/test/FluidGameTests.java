package techguns.modern.test;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import techguns.modern.fluid.TGFluids;

final class FluidGameTests {
    static void register(DeferredRegister<Consumer<GameTestHelper>> registry) {
        for (var family:TGFluids.ALL) {
            registry.register("fluid_bucket_"+family.id, () -> h -> bucket(h,family));
            registry.register("fluid_flow_"+family.id, () -> h -> flow(h,family));
            registry.register("fluid_no_infinite_"+family.id, () -> h -> noInfinite(h,family));
        }
        registry.register("fluid_acid_damage_and_milk", () -> FluidGameTests::damage);
        registry.register("fluid_density_and_side_spread", () -> FluidGameTests::density);
    }
    private static void bucket(GameTestHelper h,TGFluids.Family family) {
        var stack=family.bucket.toStack(); var contained=FluidUtil.getFirstStackContained(stack);
        h.assertTrue(contained.is(family.still.get()),"Bucket capability contains registered fluid");
        h.assertValueEqual(contained.getAmount(),1000,"Source bucket volume");
        BlockPos pos=h.absolutePos(new BlockPos(4,3,4));
        h.assertTrue(family.bucket.get().emptyContents(null,h.getLevel(),pos,null,stack),"Bucket pours into world");
        var state=h.getLevel().getBlockState(pos);
        h.assertTrue(state.is(family.block.get()) && state.getFluidState().isSource(),"World source uses original block ID");
        var pickup=family.block.get().pickupBlock(null,h.getLevel(),pos,state);
        h.assertTrue(pickup.is(family.bucket.get()) && h.getLevel().getFluidState(pos).isEmpty(),"Source pickup returns one filled bucket");
        h.getLevel().setBlock(pos,family.flowing.get().getFlowing(4,false).createLegacyBlock(),3);
        h.assertTrue(family.block.get().pickupBlock(null,h.getLevel(),pos,h.getLevel().getBlockState(pos)).isEmpty(),"Flow cannot produce a bucket");
        var player=WeaponGameTests.player(h); player.setItemInHand(InteractionHand.MAIN_HAND,family.bucket.toStack());
        var access=ItemAccess.forPlayerInteraction(player,InteractionHand.MAIN_HAND).oneByOne();
        var handler=access.getCapability(Capabilities.Fluid.ITEM); var resource=FluidResource.of(family.still.get());
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(handler.extract(resource,999,tx),0,"Buckets do not lose partial contents"); }
        try(Transaction tx=Transaction.openRoot()) { h.assertValueEqual(handler.extract(resource,1000,tx),1000,"Whole bucket transfer can be simulated"); }
        h.assertTrue(player.getMainHandItem().is(family.bucket.get()),"Simulation does not consume player's filled bucket");
        try(Transaction tx=Transaction.openRoot()) { handler.extract(resource,1000,tx); tx.commit(); }
        h.assertTrue(player.getMainHandItem().is(Items.BUCKET),"Committed transfer gives the empty bucket"); h.succeed();
    }
    private static void flow(GameTestHelper h,TGFluids.Family family) {
        BlockPos source=new BlockPos(4,5,4); h.setBlock(source,family.block.get());
        h.runAfterDelay(7, () -> {
            var fluid=h.getLevel().getFluidState(h.absolutePos(source.below()));
            h.assertTrue(fluid.getType().isSame(family.still.get()) && !fluid.isSource(),"Fluid flows down after its five-tick delay");
            h.assertValueEqual(family.type.get().getDensity(),family==TGFluids.ACID ? 100 : 1000,"Original density");
            h.assertValueEqual(family.type.get().getViscosity(),1000,"Original viscosity"); h.succeed();
        });
    }
    private static void noInfinite(GameTestHelper h,TGFluids.Family family) {
        for(int x=2;x<=6;x++) for(int z=2;z<=6;z++) h.setBlock(x,1,z,Blocks.STONE);
        // A closed three-cell trough forces flow into the middle rather than down the nearest outside edge.
        for(int x=2;x<=6;x++) { h.setBlock(x,2,3,Blocks.STONE); h.setBlock(x,2,5,Blocks.STONE); }
        h.setBlock(2,2,4,Blocks.STONE); h.setBlock(6,2,4,Blocks.STONE);
        h.setBlock(3,2,4,family.block.get()); h.setBlock(5,2,4,family.block.get());
        h.runAfterDelay(25, () -> {
            var middle=h.getLevel().getFluidState(h.absolutePos(new BlockPos(4,2,4)));
            h.assertTrue(!middle.isEmpty() && !middle.isSource(),"Two neighboring sources never create free acid or milk"); h.succeed();
        });
    }
    private static void damage(GameTestHelper h) {
        var player=WeaponGameTests.player(h); player.setHealth(20);
        player.getAttribute(Attributes.ARMOR).setBaseValue(20); player.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
        player.setDeltaMovement(Vec3.ZERO);
        var state=TGFluids.ACID.block.get().defaultBlockState(); var pos=h.absolutePos(new BlockPos(4,2,4));
        state.entityInside(h.getLevel(),pos,player,InsideBlockEffectApplier.NOOP,true);
        h.assertValueEqual(player.getHealth(),18f,"Original poison damage ignores ordinary armor");
        h.assertValueEqual(player.getDeltaMovement(),Vec3.ZERO,"Acid causes no knockback");
        for(int i=0;i<20;i++) state.entityInside(h.getLevel(),pos,player,InsideBlockEffectApplier.NOOP,true);
        h.assertValueEqual(player.getHealth(),18f,"Multiple contacts do not bypass hurt immunity");
        for(int i=0;i<11;i++) player.tick();
        state.entityInside(h.getLevel(),pos,player,InsideBlockEffectApplier.NOOP,true);
        h.assertValueEqual(player.getHealth(),16f,"Later contact damages again after immunity");
        var milk=TGFluids.MILK.block.get().defaultBlockState();
        for(int i=0;i<20;i++) { player.tick(); milk.entityInside(h.getLevel(),pos,player,InsideBlockEffectApplier.NOOP,true); }
        h.assertValueEqual(player.getHealth(),16f,"Milk is not a poison source"); h.succeed();
    }
    private static void density(GameTestHelper h) {
        h.setBlock(4,1,4,Blocks.STONE); h.setBlock(4,2,4,Blocks.WATER); h.setBlock(4,3,4,TGFluids.ACID.block.get());
        h.setBlock(9,1,4,Blocks.STONE); h.setBlock(9,2,4,TGFluids.ACID.block.get()); h.setBlock(9,3,4,TGFluids.MILK.block.get());
        h.runAfterDelay(7, () -> {
            h.assertBlockPresent(Blocks.WATER,new BlockPos(4,2,4));
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(5,3,4))).getType().isSame(TGFluids.ACID.still.get()),"Acid spreads sideways above denser water");
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(9,2,4))).getType().isSame(TGFluids.MILK.still.get()),"Denser milk can replace lighter acid"); h.succeed();
        });
    }
}
