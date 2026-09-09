package techguns.modern.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import techguns.modern.machine.charging.ChargingStationMenu;

public final class ChargingStationScreen extends ProcessingMachineScreen<ChargingStationMenu> {
    public ChargingStationScreen(ChargingStationMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, "charging_station"); }
    @Override protected void addMachineControls() {}
    @Override protected void extractMachineLabels(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.techguns.charging.state_" + menu.value(3)), 19, 42, 0xFF404040);
        if (menu.value(3) == 1) graphics.text(font, Component.translatable("gui.techguns.charging.batch", menu.value(6)), 19, 56, 0xFF404040);
    }
    @Override protected void extractProgress(GuiGraphicsExtractor graphics) {
        if (menu.value(2) > 0) {
            int width = Math.clamp(menu.value(1) * 26 / menu.value(2), 0, 26);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos + 39, topPos + 19, 0, 167, width + 1, 10, 256, 256);
        }
    }
}
