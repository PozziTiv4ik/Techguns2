package techguns.modern.client;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import techguns.modern.TGContent;
import techguns.modern.Techguns;

@Mod(value = Techguns.MOD_ID, dist = Dist.CLIENT)
public final class TechgunsClient {
    public TechgunsClient(IEventBus modBus) { modBus.addListener(TechgunsClient::renderers); }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        // The initial round uses vanilla tracer particles; a mesh renderer is part of M5.
        event.registerEntityRenderer(TGContent.BULLET.get(), NoopRenderer::new);
    }
}
