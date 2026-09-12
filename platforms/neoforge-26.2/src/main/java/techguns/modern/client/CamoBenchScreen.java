package techguns.modern.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import techguns.modern.TGContent;
import techguns.modern.machine.camo.*;

public final class CamoBenchScreen extends AbstractContainerScreen<CamoBenchMenu> {
    private static final Identifier TEXTURE = TGContent.id("textures/gui/camo_bench.png");
    private final Button[] controls = new Button[10];
    private Button security;
    public CamoBenchScreen(CamoBenchMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, 176, 188); }
    @Override public void init() {
        super.init();
        for (int i = 0; i < controls.length; i++) {
            int button = i + 1, target = i / 2; boolean back = i % 2 != 0;
            int x = target == 0 ? (back ? 14 : 26) : 102 + (target - 1) * 18;
            int y = target == 0 ? 59 : 59 + (back ? 10 : 0), size = target == 0 ? 12 : 10;
            controls[i] = addRenderableWidget(Button.builder(Component.literal(target == 0 ? (back ? "<" : ">") : (back ? "-" : "+")), b -> send(button))
                    .bounds(leftPos + x, topPos + y, size, size).build());
        }
        security = addRenderableWidget(Button.builder(Component.empty(), b -> send(0)).bounds(leftPos + 8, topPos + 168, 160, 17).build());
        updateButtons();
    }
    private void send(int id) { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    private void updateButtons() {
        security.setMessage(Component.translatable("gui.techguns.machine." + (menu.value(0) == 0 ? "public" : "private")));
        security.active = menu.value(1) != 0;
        security.setTooltip(Tooltip.create(Component.translatable("gui.techguns.machine.security", security.getMessage())));
        for (int i = 0; i < controls.length; i++) {
            int target = i / 2; var stack = menu.target(target); int count = CamoCycling.count(stack);
            controls[i].active = count > 1;
            var tooltip = Component.empty().append(Component.translatable("gui.techguns.camo.target_" + target)).append(": ")
                    .append(Component.translatable("gui.techguns.camo." + (i % 2 == 0 ? "forward" : "back"))).append("\n");
            tooltip.append(count == 0 ? Component.translatable("gui.techguns.camo.unsupported")
                    : Component.translatable("gui.techguns.camo.variant", CamoCycling.index(stack) + 1, count, CamoCycling.variantName(stack)));
            controls[i].setTooltip(Tooltip.create(tooltip));
        }
    }
    @Override protected void containerTick() { super.containerTick(); updateButtons(); }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, 176, 166, 256, 256);
        // Face/back/hand equipment is not ported yet; leave no non-functional inventory slots in this screen.
        graphics.fill(leftPos + 40, topPos + 15, leftPos + 97, topPos + 52, 0xFFC6C6C6);
        graphics.fill(leftPos, topPos + 166, leftPos + 176, topPos + 188, 0xFFC6C6C6);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos - 45, topPos + 6, 178, 0, 45, 84, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos - 18, topPos + 93, 7, 83, 18, 18, 256, 256);
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, leftPos - 44, topPos + 7, leftPos - 2, topPos + 88, 30, .0625f, mouseX, mouseY, minecraft.player);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFF404040);
        var stack = menu.target(0); int count = CamoCycling.count(stack);
        if (count > 0) graphics.text(font, (CamoCycling.index(stack) + 1) + "/" + count, 10, 40, 0xFF404040);
        graphics.text(font, font.substrByWidth(CamoCycling.variantName(stack), 88).getString(), 10, 50, 0xFF404040);
        for (int i = 1; i <= 4; i++) graphics.text(font, Integer.toString(CamoCycling.index(menu.target(i)) + 1), 104 + (i - 1) * 18, 38, 0xFF404040);
    }
}
