package techguns.modern.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import techguns.core.GrinderRules;
import techguns.modern.TGContent;
import techguns.modern.machine.grinder.GrinderMenu;

public final class GrinderScreen extends AbstractContainerScreen<GrinderMenu> {
    private static final Identifier TEXTURE = TGContent.id("textures/gui/grinder.png");
    private Button security, redstone;
    public GrinderScreen(GrinderMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, 176, 188); }
    @Override public void init() {
        super.init();
        redstone = addRenderableWidget(Button.builder(Component.empty(), b -> send(1)).bounds(leftPos + 7, topPos + 168, 80, 17).build());
        security = addRenderableWidget(Button.builder(Component.empty(), b -> send(0)).bounds(leftPos + 89, topPos + 168, 80, 17).build());
        updateButtons();
    }
    private void send(int id) { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    private void updateButtons() {
        String mode = switch (menu.metric(4)) { case 1 -> "high"; case 2 -> "low"; default -> "ignore"; };
        redstone.setMessage(Component.translatable("gui.techguns.machine." + mode));
        security.setMessage(Component.translatable("gui.techguns.machine." + (menu.value(0) == 0 ? "public" : "private")));
        security.active = menu.value(1) != 0;
        security.setTooltip(Tooltip.create(Component.translatable("gui.techguns.machine.security", security.getMessage())));
        redstone.setTooltip(Tooltip.create(Component.translatable("gui.techguns.machine.redstone", redstone.getMessage())));
    }
    private Component status() { return Component.translatable("gui.techguns.grinder." + switch (menu.metric(3)) { case 1 -> "running"; case 2 -> "paused"; case 3 -> "blocked"; default -> "idle"; }); }
    @Override protected void containerTick() { super.containerTick(); updateButtons(); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        if (mouseX >= leftPos + 6 && mouseX < leftPos + 14 && mouseY >= topPos + 17 && mouseY < topPos + 70)
            graphics.setTooltipForNextFrame(java.util.List.of(
                    Component.translatable("gui.techguns.machine.energy", menu.metric(0), GrinderRules.CAPACITY).getVisualOrderText(),
                    Component.translatable("gui.techguns.machine.energy_rate", menu.metric(6)).getVisualOrderText()), mouseX, mouseY);
        if (mouseX >= leftPos + 31 && mouseX < leftPos + 75 && mouseY >= topPos + 39 && mouseY < topPos + 61)
            graphics.setTooltipForNextFrame(java.util.List.of(status().getVisualOrderText(),
                    Component.translatable("gui.techguns.grinder.batch", menu.metric(5)).getVisualOrderText()), mouseX, mouseY);
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractBackground(graphics, mouseX, mouseY, partial);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, 176, 166, 256, 256);
        graphics.fill(leftPos, topPos + 166, leftPos + 176, topPos + 188, 0xFFC6C6C6);
        int energy = Math.clamp(menu.metric(0) * 53 / GrinderRules.CAPACITY, 0, 53);
        graphics.fill(leftPos + 6, topPos + 17, leftPos + 14, topPos + 70, 0xFF24282C);
        graphics.fill(leftPos + 6, topPos + 70 - energy, leftPos + 14, topPos + 70, 0xFFE4A237);
        if (menu.metric(2) > 0) {
            int progress = Math.clamp(menu.metric(1) * 21 / menu.metric(2), 0, 20), remaining = 20 - progress;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + 31, topPos + 39, 0, 167, progress + 1, 22, 256, 256);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + 51 + remaining, topPos + 39, remaining, 167, progress + 1, 22, 256, 256);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFF404040);
        int line = 0;
        for (var text : font.split(status(), 42)) { if (line == 2) break; graphics.text(font, text, 36, 18 + 9 * line++, 0xFF404040); }
    }
}
