package techguns.modern.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import techguns.modern.machine.AmmoPressMenu;

public final class AmmoPressScreen extends ProcessingMachineScreen<AmmoPressMenu> {
    private static final String[] PLANS = {"pistolrounds", "shotgunrounds", "riflerounds", "sniperrounds"};
    public AmmoPressScreen(AmmoPressMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, "ammo_press"); }
    @Override protected void addMachineControls() {
        addRenderableWidget(Button.builder(Component.literal("<"), button -> send(1)).bounds(leftPos + 20, topPos + 50, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> send(0)).bounds(leftPos + 40, topPos + 50, 20, 20).build());
    }
    @Override protected void extractMachineLabels(GuiGraphicsExtractor graphics) {
        modeLabel(graphics, Component.translatable("item.techguns." + PLANS[Math.clamp(menu.value(3), 0, 3)]));
    }
}
