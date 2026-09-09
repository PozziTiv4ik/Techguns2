package techguns.modern.fluid;

import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Extraction from the vanilla drinkable milk bucket preserves its normal empty-bucket remainder. */
final class MilkBucketHandler extends ItemAccessResourceHandler<FluidResource> {
    MilkBucketHandler(ItemAccess access) { super(access,1); }
    @Override protected FluidResource getResourceFrom(ItemResource item,int slot) {
        return item.is(Items.MILK_BUCKET) ? FluidResource.of(TGFluids.MILK.still.get()) : FluidResource.EMPTY;
    }
    @Override protected int getAmountFrom(ItemResource item,int slot) { return item.is(Items.MILK_BUCKET) ? 1000 : 0; }
    @Override protected int getCapacity(int slot,FluidResource resource) { return 1000; }
    @Override protected ItemResource update(ItemResource item,int slot,FluidResource resource,int amount) {
        return amount==0 ? ItemResource.of(Items.BUCKET) : ItemResource.EMPTY;
    }
}
