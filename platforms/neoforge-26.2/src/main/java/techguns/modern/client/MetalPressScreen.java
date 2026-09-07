package techguns.modern.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import techguns.modern.machine.MetalPressMenu;

public final class MetalPressScreen extends ProcessingMachineScreen<MetalPressMenu> {
    private Button autoSplit;
    public MetalPressScreen(MetalPressMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, "metal_press"); }
    @Override protected void addMachineControls() {
        autoSplit = addRenderableWidget(Button.builder(Component.empty(), button -> send(0)).bounds(leftPos + 18, topPos + 50, 78, 20).build());
    }
    @Override protected void updateMachineControls() {
        autoSplit.setMessage(Component.translatable("gui.techguns.machine." + (menu.value(3) == 0 ? "disabled" : "enabled")));
    }
    @Override protected void extractMachineLabels(GuiGraphicsExtractor graphics) { modeLabel(graphics, Component.translatable("gui.techguns.machine.autosplit")); }
}
