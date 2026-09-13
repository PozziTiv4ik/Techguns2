package techguns.modern.client;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import techguns.modern.TGContent;
import techguns.modern.Techguns;
import techguns.modern.GunItem;
import techguns.modern.network.GunActionPayload;
import techguns.modern.network.AimPayload;
import techguns.core.WeaponHud;

@Mod(value = Techguns.MOD_ID, dist = Dist.CLIENT)
public final class TechgunsClient {
    private static final KeyMapping RELOAD = new KeyMapping("key.techguns.reload", GLFW.GLFW_KEY_R, KeyMapping.Category.GAMEPLAY);
    private static final KeyMapping SAFE_MODE = new KeyMapping("key.techguns.safemode", GLFW.GLFW_KEY_B, KeyMapping.Category.GAMEPLAY);
    private static boolean attackWasDown;
    private static boolean aimWasDown;
    private static boolean requestedAim;
    private static String lastWeapon = "";
    private static int lastSlot = -1;

    public TechgunsClient(IEventBus modBus) {
        modBus.addListener(TechgunsClient::renderers);
        modBus.addListener(TechgunsClient::keys);
        modBus.addListener(TechgunsClient::screens);
        modBus.addListener(FluidRendering::register);
        modBus.addListener(LaserBeamRenderer::pipelines);
        modBus.addListener(TechgunsClient::itemProperties);
        NeoForge.EVENT_BUS.addListener(TechgunsClient::tick);
        NeoForge.EVENT_BUS.addListener(TechgunsClient::interaction);
        NeoForge.EVENT_BUS.addListener(TechgunsClient::fov);
        NeoForge.EVENT_BUS.addListener(TechgunsClient::hud);
    }

    private static void keys(RegisterKeyMappingsEvent event) { event.register(RELOAD); event.register(SAFE_MODE); }
    private static void itemProperties(net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent event) {
        event.register(TGContent.id("rocket_loaded"), RocketLoadedProperty.CODEC);
    }
    private static void screens(RegisterMenuScreensEvent event) {
        event.register(techguns.modern.machine.TGMachineContent.AMMO_PRESS_MENU.get(), AmmoPressScreen::new);
        event.register(techguns.modern.machine.TGMachineContent.METAL_PRESS_MENU.get(), MetalPressScreen::new);
        event.register(techguns.modern.machine.TGMachineContent.BLAST_FURNACE_MENU.get(), BlastFurnaceScreen::new);
        event.register(techguns.modern.machine.TGMachineContent.CHEM_LAB_MENU.get(),ChemLabScreen::new);
        event.register(techguns.modern.machine.reaction.ReactionContent.MENU.get(),ReactionChamberScreen::new);
        event.register(techguns.modern.machine.fabricator.FabricatorContent.MENU.get(),FabricatorScreen::new);
        event.register(techguns.modern.machine.charging.ChargingStationContent.MENU.get(), ChargingStationScreen::new);
        event.register(techguns.modern.machine.repair.RepairBenchContent.MENU.get(), RepairBenchScreen::new);
        event.register(techguns.modern.machine.camo.CamoBenchContent.MENU.get(), CamoBenchScreen::new);
        event.register(techguns.modern.machine.grinder.GrinderContent.MENU.get(), GrinderScreen::new);
    }

    private static void tick(ClientTickEvent.Pre event) {
        Minecraft client = Minecraft.getInstance();
        boolean down = client.options.keyAttack.isDown();
        boolean playing = client.player != null && client.gui.screen() == null && !client.isPaused();
        while (SAFE_MODE.consumeClick()) if (playing) ClientPacketDistributor.sendToServer(techguns.modern.network.SafeModePayload.INSTANCE);
        boolean useDown = client.options.keyUse.isDown();
        if (!down) attackWasDown = false;
        if (!useDown) aimWasDown = false;
        if (playing && client.player.getMainHandItem().getItem() instanceof GunItem gun) {
            String id = gun.definition().id();
            int slot = client.player.getInventory().getSelectedSlot();
            if (!id.equals(lastWeapon) || slot != lastSlot) requestedAim = false;
            lastWeapon = id;
            lastSlot = slot;
            boolean aimBlocked = client.player.isUsingItem() || client.player.getMainHandItem().getOrDefault(TGContent.RELOAD_TICKS.get(), 0) > 0;
            if (aimBlocked) requestedAim = false;
            if (!aimBlocked && gun.definition().aim().supported() && !gun.definition().aim().toggle() && useDown != requestedAim) requestAim(useDown);
            if (down && attackWasDown && gun.definition().automatic()) ClientPacketDistributor.sendToServer(new GunActionPayload(false));
            while (RELOAD.consumeClick()) {
                requestedAim = false;
                ClientPacketDistributor.sendToServer(new GunActionPayload(true));
            }
        } else {
            while (RELOAD.consumeClick()) { /* Discard key presses from menus and other items. */ }
            if (requestedAim && client.getConnection() != null) requestAim(false);
            requestedAim = false;
            lastWeapon = "";
            lastSlot = -1;
            attackWasDown = down;
            aimWasDown = useDown;
        }
    }

    private static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft client = Minecraft.getInstance();
        if (event.isUseItem() && client.player != null && !client.player.isShiftKeyDown()
                && client.player.getMainHandItem().getItem() instanceof GunItem gun && gun.definition().aim().supported()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (event.getHand() == InteractionHand.MAIN_HAND && !aimWasDown) {
                requestAim(gun.definition().aim().toggle() ? !requestedAim : true);
                aimWasDown = true;
            }
        }
        if (event.isAttack() && client.player != null && client.player.getMainHandItem().getItem() instanceof GunItem) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (!attackWasDown) ClientPacketDistributor.sendToServer(new GunActionPayload(false));
            attackWasDown = true;
        }
    }

    private static void requestAim(boolean enabled) {
        requestedAim = enabled;
        ClientPacketDistributor.sendToServer(new AimPayload(enabled));
    }

    private static void fov(ComputeFovModifierEvent event) {
        var stack = event.getPlayer().getMainHandItem();
        if (stack.getItem() instanceof GunItem gun && stack.getOrDefault(TGContent.AIMING.get(), false)
                && !event.getPlayer().isUsingItem() && stack.getOrDefault(TGContent.RELOAD_TICKS.get(), 0) == 0) {
            event.setNewFovModifier(gun.definition().aim().applyFov(event.getNewFovModifier()));
        }
    }

    private static void hud(RenderGuiEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden() || client.gui.screen() != null) return;
        var stack = client.player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun)) return;
        int remaining = stack.getOrDefault(TGContent.RELOAD_TICKS.get(), 0);
        WeaponHud state = WeaponHud.of(gun.definition(), GunItem.rounds(stack), remaining);
        var gui = event.getGuiGraphics();
        boolean rocket = gun.definition().projectile() == techguns.core.ProjectileKind.ROCKET;
        int x = Math.max(6, gui.guiWidth() - 154), y = Math.max(6, gui.guiHeight() - (rocket ? 94 : 68));
        gui.fill(x - 5, y - 5, x + 144, y + (rocket ? 62 : 36), 0xA0181C20);
        gui.text(client.font, Component.literal(client.font.plainSubstrByWidth(stack.getHoverName().getString(), 138)), x, y, 0xFFE7E7E7);
        gui.text(client.font, Component.translatable("hud.techguns.ammo", state.rounds(), state.capacity()), x, y + 13, 0xFFE9A63B);
        if (state.reloading()) {
            gui.text(client.font, Component.translatable("hud.techguns.reloading"), x + 60, y + 13, 0xFFC5C5C5);
            gui.fill(x, y + 28, x + Math.round(138 * state.reloadProgress()), y + 31, 0xFFE9A63B);
        }
        if (rocket) {
            var variant = Component.translatable("hud.techguns.rocket." + techguns.modern.RocketAmmo.variant(stack).id());
            gui.text(client.font, Component.literal(client.font.plainSubstrByWidth(variant.getString(), 138)), x, y + 35, 0xFFC5C5C5);
            boolean safe = techguns.modern.SafeMode.enabled(client.player);
            gui.text(client.font, Component.translatable(safe ? "hud.techguns.safe" : "hud.techguns.unsafe"), x, y + 48, safe ? 0xFF97CE8B : 0xFFEE9466);
        }
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        // The initial round uses vanilla tracer particles; a mesh renderer is part of M5.
        event.registerEntityRenderer(TGContent.BULLET.get(), NoopRenderer::new);
        event.registerEntityRenderer(TGContent.NETHER_BLAST.get(), NoopRenderer::new);
        event.registerEntityRenderer(TGContent.LASER_BEAM.get(), LaserBeamRenderer::new);
        event.registerEntityRenderer(TGContent.ROCKET.get(), RocketRenderer::new);
        event.registerEntityRenderer(TGContent.RADIATION_ZONE.get(), NoopRenderer::new);
        event.registerEntityRenderer(techguns.modern.npc.NpcContent.SUPER_MUTANT.get(), SuperMutantRenderer::new);
        event.registerEntityRenderer(techguns.modern.npc.NpcContent.CYBER_DEMON.get(), CyberDemonRenderer::new);
        event.registerEntityRenderer(techguns.modern.npc.NpcContent.PIGMAN.get(),ZombiePigmanSoldierRenderer::new);
        event.registerEntityRenderer(techguns.modern.npc.NpcContent.ZOMBIE_SOLDIER.get(),GenericZombieRenderer::new);
        event.registerEntityRenderer(techguns.modern.npc.NpcContent.FARMER.get(),GenericZombieRenderer::new);
        event.registerEntityRenderer(techguns.modern.npc.NpcContent.MINER.get(),GenericZombieRenderer::new);
        event.registerEntityRenderer(techguns.modern.npc.NpcContent.SKELETON.get(),SkeletonSoldierRenderer::new);
        event.registerEntityRenderer(techguns.modern.npc.OverworldSpawns.SELECTOR.get(), NoopRenderer::new);
        event.registerEntityRenderer(techguns.modern.npc.NetherSpawns.SELECTOR.get(), NoopRenderer::new);
        event.registerBlockEntityRenderer(techguns.modern.machine.charging.ChargingStationContent.ENTITY.get(), ChargingStationRenderer::new);
        event.registerBlockEntityRenderer(techguns.modern.machine.grinder.GrinderContent.ENTITY.get(), GrinderRenderer::new);
    }
}
