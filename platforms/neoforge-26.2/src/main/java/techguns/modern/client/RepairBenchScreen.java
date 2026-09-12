package techguns.modern.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import techguns.modern.TGContent;
import techguns.modern.machine.repair.*;

public final class RepairBenchScreen extends AbstractContainerScreen<RepairBenchMenu> {
    private static final Identifier TEXTURE = TGContent.id("textures/gui/repair_bench.png");
    private final Button[] repairs = new Button[6];
    private Button security;
    public RepairBenchScreen(RepairBenchMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, 176, 188); }
    @Override public void init() {
        super.init();
        int[] positions = {90, 110, 130, 150, 65, 9};
        for (int index = 0; index < repairs.length; index++) {
            int button = index + 1;
            repairs[index] = addRenderableWidget(new Button(leftPos + positions[index], topPos + 39, 14, 14,
                    Component.translatable("gui.techguns.repair.target_" + button), b -> send(button), narration -> narration.get()) {
                @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
                    extractDefaultSprite(graphics);
                    graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, getX() + 1, getY() + 1, 176, 0, 12, 12, 256, 256);
                }
            });
        }
        security = addRenderableWidget(Button.builder(Component.empty(), b -> send(0)).bounds(leftPos + 8, topPos + 168, 160, 17).build());
        updateButtons();
    }
    private void send(int id) { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    private void updateButtons() {
        security.setMessage(Component.translatable("gui.techguns.machine." + (menu.value(0) == 0 ? "public" : "private")));
        security.active = menu.value(1) != 0;
        security.setTooltip(Tooltip.create(Component.translatable("gui.techguns.machine.security", security.getMessage())));
        for (int i = 0; i < repairs.length; i++) {
            var stack = menu.target(i + 1);
            var costs = RepairBenchBlockEntity.costs(stack);
            boolean ready = !costs.isEmpty();
            var tooltip = Component.empty().append(repairs[i].getMessage()).append("\n");
            if (stack.isEmpty()) tooltip.append(Component.translatable("gui.techguns.repair.empty"));
            else if (!RepairBenchBlockEntity.supports(stack)) tooltip.append(Component.translatable("gui.techguns.repair.unsupported"));
            else if (stack.getDamageValue() == 0) tooltip.append(Component.translatable("gui.techguns.repair.full"));
            else {
                tooltip.append(Component.translatable("gui.techguns.repair.required"));
                for (var cost : costs) {
                    boolean enough = menu.available(cost) >= cost.getCount(); ready &= enough;
                    tooltip.append("\n").append(Component.translatable("gui.techguns.repair.cost", cost.getCount(), cost.getHoverName()).withStyle(enough ? ChatFormatting.GRAY : ChatFormatting.RED));
                }
            }
            repairs[i].active = ready; repairs[i].setTooltip(Tooltip.create(tooltip));
        }
    }
    @Override protected void containerTick() { super.containerTick(); updateButtons(); }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, 176, 166, 256, 256);
        graphics.fill(leftPos, topPos + 166, leftPos + 176, topPos + 188, 0xFFC6C6C6);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) { graphics.text(font, title, titleLabelX, titleLabelY, 0xFF404040); }
}
