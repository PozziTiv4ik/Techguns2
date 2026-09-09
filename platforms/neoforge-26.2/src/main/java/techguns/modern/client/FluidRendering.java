package techguns.modern.client;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import techguns.modern.TGContent;
import techguns.modern.fluid.TGFluids;

final class FluidRendering {
    static void register(RegisterFluidModelsEvent event) {
        for (var fluid:TGFluids.ALL) event.register(new FluidModel.Unbaked(
                new Material(TGContent.id("block/"+fluid.texture+"_still")),new Material(TGContent.id("block/"+fluid.texture+"_flow")),
                null,(net.neoforged.neoforge.client.fluid.FluidTintSource)null),fluid.still,fluid.flowing);
    }
    private FluidRendering() {}
}
