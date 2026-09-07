package techguns.modern.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import techguns.modern.TGContent;
import techguns.modern.machine.AmmoPressMenu;

public final class AmmoPressScreen extends AbstractContainerScreen<AmmoPressMenu> {
    private static final Identifier TEXTURE = TGContent.id("textures/gui/ammo_press.png");
    private static final String[] PLANS = {"pistolrounds", "shotgunrounds", "riflerounds", "sniperrounds"};
    private Button redstone, security;
    public AmmoPressScreen(AmmoPressMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, 176, 188); }
    @Override public void init() {
        super.init();
        inventoryLabelY = 73;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> send(1)).bounds(leftPos + 20, topPos + 50, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> send(0)).bounds(leftPos + 40, topPos + 50, 20, 20).build());
        redstone = addRenderableWidget(Button.builder(Component.empty(), button -> send(2)).bounds(leftPos + 7, topPos + 168, 80, 17).build());
        security = addRenderableWidget(Button.builder(Component.empty(), button -> send(3)).bounds(leftPos + 89, topPos + 168, 80, 17).build());
        updateButtons();
    }
    private void send(int id) { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    private void updateButtons() {
        String mode = switch (menu.value(4)) { case 1 -> "high"; case 2 -> "low"; default -> "ignore"; };
        redstone.setMessage(Component.translatable("gui.techguns.machine." + mode));
        security.setMessage(Component.translatable("gui.techguns.machine." + (menu.value(5) == 0 ? "public" : "private")));
        redstone.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.techguns.machine.redstone", redstone.getMessage())));
        security.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.techguns.machine.security", security.getMessage())));
    }
    @Override protected void containerTick() { super.containerTick(); updateButtons(); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (mouseX >= leftPos + 6 && mouseX < leftPos + 14 && mouseY >= topPos + 17 && mouseY < topPos + 70)
            graphics.setTooltipForNextFrame(Component.translatable("gui.techguns.machine.energy", menu.value(0)), mouseX, mouseY);
    }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, 176, 166, 256, 256);
        graphics.fill(leftPos, topPos + 166, leftPos + 176, topPos + 188, 0xFFC6C6C6);
        int energyHeight = Math.clamp(menu.value(0) * 53 / 20000, 0, 53);
        graphics.fill(leftPos + 6, topPos + 17, leftPos + 14, topPos + 70, 0xFF24282C);
        graphics.fill(leftPos + 6, topPos + 70 - energyHeight, leftPos + 14, topPos + 70, 0xFFE4A237);
        if (menu.value(2) > 0) {
            int progress = Math.clamp(menu.value(1) * 21 / menu.value(2), 0, 21);
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + 119, topPos + 36, 176, 0, 19, progress + 1, 256, 256);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component plan = Component.translatable("item.techguns." + PLANS[Math.clamp(menu.value(3), 0, 3)]);
        int line = 0;
        for (var text : font.split(plan, 78)) {
            if (line == 3) break;
            graphics.text(font, text, 18, 19 + 9 * line++, 0xFF404040);
        }
    }
}
