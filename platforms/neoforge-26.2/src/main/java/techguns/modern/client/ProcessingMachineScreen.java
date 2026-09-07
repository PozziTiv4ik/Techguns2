package techguns.modern.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import techguns.modern.TGContent;
import techguns.modern.machine.ProcessingMachineMenu;

public abstract class ProcessingMachineScreen<M extends ProcessingMachineMenu> extends AbstractContainerScreen<M> {
    private final Identifier texture;
    private Button redstone, security;
    protected ProcessingMachineScreen(M menu, Inventory inventory, Component title, String machine) {
        super(menu, inventory, title, 176, 188);
        texture = TGContent.id("textures/gui/" + machine + ".png");
    }
    protected abstract void addMachineControls();
    protected void updateMachineControls() {}
    protected abstract void extractMachineLabels(GuiGraphicsExtractor graphics);
    @Override public void init() {
        super.init();
        inventoryLabelY = 73;
        addMachineControls();
        redstone = addRenderableWidget(Button.builder(Component.empty(), button -> send(2)).bounds(leftPos + 7, topPos + 168, 80, 17).build());
        security = addRenderableWidget(Button.builder(Component.empty(), button -> send(3)).bounds(leftPos + 89, topPos + 168, 80, 17).build());
        updateButtons();
    }
    protected final void send(int id) { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    private void updateButtons() {
        String mode = switch (menu.value(4)) { case 1 -> "high"; case 2 -> "low"; default -> "ignore"; };
        redstone.setMessage(Component.translatable("gui.techguns.machine." + mode));
        security.setMessage(Component.translatable("gui.techguns.machine." + (menu.value(5) == 0 ? "public" : "private")));
        redstone.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.techguns.machine.redstone", redstone.getMessage())));
        security.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.techguns.machine.security", security.getMessage())));
        updateMachineControls();
    }
    @Override protected void containerTick() { super.containerTick(); updateButtons(); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (mouseX >= leftPos + 6 && mouseX < leftPos + 14 && mouseY >= topPos + 17 && mouseY < topPos + 70)
            graphics.setTooltipForNextFrame(java.util.List.of(
                    Component.translatable("gui.techguns.machine.energy", menu.value(0)).getVisualOrderText(),
                    Component.translatable("gui.techguns.machine.energy_rate", menu.value(7)).getVisualOrderText()), mouseX, mouseY);
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos, topPos, 0, 0, 176, 166, 256, 256);
        graphics.fill(leftPos, topPos + 166, leftPos + 176, topPos + 188, 0xFFC6C6C6);
        int energyHeight = Math.clamp(menu.value(0) * 53 / 20000, 0, 53);
        graphics.fill(leftPos + 6, topPos + 17, leftPos + 14, topPos + 70, 0xFF24282C);
        graphics.fill(leftPos + 6, topPos + 70 - energyHeight, leftPos + 14, topPos + 70, 0xFFE4A237);
        if (menu.value(2) > 0) {
            int progress = Math.clamp(menu.value(1) * 21 / menu.value(2), 0, 21);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos + 119, topPos + 36, 176, 0, 19, progress + 1, 256, 256);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        extractMachineLabels(graphics);
    }
    protected final void modeLabel(GuiGraphicsExtractor graphics, Component label) {
        int line = 0;
        for (var text : font.split(label, 78)) {
            if (line == 3) break;
            graphics.text(font, text, 18, 19 + 9 * line++, 0xFF404040);
        }
    }
}
