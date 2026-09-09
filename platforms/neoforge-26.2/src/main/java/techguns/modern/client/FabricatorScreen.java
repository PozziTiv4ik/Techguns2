package techguns.modern.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import techguns.modern.machine.fabricator.FabricatorMenu;
import techguns.modern.TGContent;

public final class FabricatorScreen extends ProcessingMachineScreen<FabricatorMenu> {
    public FabricatorScreen(FabricatorMenu menu,Inventory inventory,Component title) { super(menu,inventory,title,"fabricator"); }
    @Override protected void addMachineControls() {}
    @Override protected void extractMachineLabels(GuiGraphicsExtractor g) {
        g.text(font,Component.translatable("gui.techguns.fabricator."+(menu.value(3)==1 ? "ready" : "incomplete")),19,37,0xFF404040,false);
    }
    @Override protected void extractProgress(GuiGraphicsExtractor g) {
        if(menu.value(2)>0) g.blit(RenderPipelines.GUI_TEXTURED,texture,leftPos+21,topPos+53,0,167,Math.clamp(menu.value(1)*90/menu.value(2),0,90)+1,11,256,256);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        super.extractBackground(g,mouseX,mouseY,partialTick);
        int[] x={47,68,89}; String[] icon={"wires","powder","plate"};
        for(int i=0;i<3;i++) if(menu.getSlot(i+1).getItem().isEmpty())
            g.blit(RenderPipelines.GUI_TEXTURED,TGContent.id("textures/gui/emptyslot_"+icon[i]+".png"),leftPos+x[i],topPos+17,0,0,16,16,16,16);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        int[] x={47,68,89}; String[] name={"wire","powder","plate"};
        for(int i=0;i<3;i++) if(menu.getSlot(i+1).getItem().isEmpty() && mouseX>=leftPos+x[i] && mouseX<leftPos+x[i]+16 && mouseY>=topPos+17 && mouseY<topPos+33)
            g.setTooltipForNextFrame(java.util.List.of(Component.translatable("gui.techguns.fabricator."+name[i]).getVisualOrderText()),mouseX,mouseY);
    }
}
