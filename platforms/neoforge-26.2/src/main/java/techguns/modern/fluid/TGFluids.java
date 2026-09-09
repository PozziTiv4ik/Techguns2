package techguns.modern.fluid;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import techguns.modern.TGContent;
import techguns.modern.Techguns;

public final class TGFluids {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES,Techguns.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID,Techguns.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Techguns.MOD_ID);
    public static final Family ACID = new Family("creeper_acid","acid",100,true);
    public static final Family MILK = new Family("milk","milk",1000,false);
    public static final List<Family> ALL = List.of(ACID,MILK);

    public static final class Family {
        public final String id, texture;
        public final DeferredHolder<FluidType,FluidType> type;
        public final DeferredHolder<Fluid,FlowingFluid> still, flowing;
        public final DeferredBlock<LiquidBlock> block;
        public final DeferredItem<BucketItem> bucket;
        private Family(String id,String texture,int density,boolean acid) {
            this.id=id; this.texture=texture;
            type=TYPES.register(id, () -> new FluidType(FluidType.Properties.create().density(density).viscosity(1000)
                    .canConvertToSource(false).canExtinguish(true).supportsBoating(true)
                    .sound(SoundActions.BUCKET_FILL,SoundEvents.BUCKET_FILL).sound(SoundActions.BUCKET_EMPTY,SoundEvents.BUCKET_EMPTY)));
            still=FLUIDS.register(id, () -> new TGFlowingFluid.Source(properties()));
            flowing=FLUIDS.register("flowing_"+id, () -> new TGFlowingFluid.Flowing(properties()));
            block=BLOCKS.registerBlock("block_"+id,p -> new TGLiquidBlock(still.get(),p,acid),
                    p -> p.mapColor(acid ? MapColor.COLOR_LIGHT_GREEN : MapColor.SNOW).replaceable().noCollision()
                            .strength(0).pushReaction(PushReaction.DESTROY).noLootTable().liquid().sound(net.minecraft.world.level.block.SoundType.EMPTY));
            bucket=TGContent.ITEMS.registerItem(id+"_bucket",p -> new BucketItem(still.get(),p),
                    p -> p.stacksTo(1).craftRemainder(Items.BUCKET));
        }
        private BaseFlowingFluid.Properties properties() {
            return new BaseFlowingFluid.Properties(type,still,flowing).block(block).bucket(bucket).tickRate(5).explosionResistance(0);
        }
    }
    public static void register(IEventBus bus) {
        TYPES.register(bus); FLUIDS.register(bus); BLOCKS.register(bus);
        bus.addListener(TGFluids::capabilities);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        // Respect NeoForge's milk provider when another mod enables it; otherwise support the vanilla drinking bucket ourselves.
        if (!NeoForgeMod.MILK.isBound()) event.registerItem(Capabilities.Fluid.ITEM,
                (stack,access) -> new MilkBucketHandler(access),Items.MILK_BUCKET);
    }
    private TGFluids() {}
}
