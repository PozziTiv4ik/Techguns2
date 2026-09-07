package techguns.modern.client;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import techguns.modern.TGContent;
import techguns.modern.Techguns;
import techguns.modern.GunItem;
import techguns.modern.network.GunActionPayload;

@Mod(value = Techguns.MOD_ID, dist = Dist.CLIENT)
public final class TechgunsClient {
    private static final KeyMapping RELOAD = new KeyMapping("key.techguns.reload", GLFW.GLFW_KEY_R, KeyMapping.Category.GAMEPLAY);
    private static boolean attackWasDown;

    public TechgunsClient(IEventBus modBus) {
        modBus.addListener(TechgunsClient::renderers);
        modBus.addListener(TechgunsClient::keys);
        NeoForge.EVENT_BUS.addListener(TechgunsClient::tick);
        NeoForge.EVENT_BUS.addListener(TechgunsClient::interaction);
    }

    private static void keys(RegisterKeyMappingsEvent event) { event.register(RELOAD); }

    private static void tick(ClientTickEvent.Pre event) {
        Minecraft client = Minecraft.getInstance();
        boolean down = client.options.keyAttack.isDown();
        boolean playing = client.player != null && client.gui.screen() == null && !client.isPaused();
        if (!down) attackWasDown = false;
        if (playing && client.player.getMainHandItem().getItem() instanceof GunItem gun) {
            if (down && attackWasDown && gun.definition().automatic()) ClientPacketDistributor.sendToServer(new GunActionPayload(false));
            while (RELOAD.consumeClick()) ClientPacketDistributor.sendToServer(new GunActionPayload(true));
        } else {
            while (RELOAD.consumeClick()) { /* Discard key presses from menus and other items. */ }
            attackWasDown = down;
        }
    }

    private static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft client = Minecraft.getInstance();
        if (event.isAttack() && client.player != null && client.player.getMainHandItem().getItem() instanceof GunItem) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (!attackWasDown) ClientPacketDistributor.sendToServer(new GunActionPayload(false));
            attackWasDown = true;
        }
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        // The initial round uses vanilla tracer particles; a mesh renderer is part of M5.
        event.registerEntityRenderer(TGContent.BULLET.get(), NoopRenderer::new);
    }
}
