package techguns.modern.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import techguns.modern.machine.BlastFurnaceMenu;

public final class BlastFurnaceScreen extends ProcessingMachineScreen<BlastFurnaceMenu> {
    public BlastFurnaceScreen(BlastFurnaceMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, "blast_furnace"); }
    @Override protected void addMachineControls() {}
    @Override protected void extractMachineLabels(GuiGraphicsExtractor graphics) {}
    @Override protected void extractProgress(GuiGraphicsExtractor graphics) {
        if (menu.value(2) > 0) {
            int progress = Math.clamp(menu.value(1) * 90 / menu.value(2), 0, 90);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos + 19, topPos + 53, 0, 167, progress + 1, 11, 256, 256);
        }
    }
}
