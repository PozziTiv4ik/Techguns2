package techguns.modern.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import techguns.modern.machine.ChemLabMenu;

public final class ChemLabScreen extends ProcessingMachineScreen<ChemLabMenu> {
    private Button drain;
    public ChemLabScreen(ChemLabMenu menu,Inventory inventory,Component title) { super(menu,inventory,title,"chem_lab"); }
    @Override protected void addMachineControls() {
        inventoryLabelX=35;
        drain=addRenderableWidget(Button.builder(Component.empty(),b -> send(0)).bounds(leftPos+35,topPos+60,92,12).build());
        for (int tank=0;tank<2;tank++) {
            int selected=tank;
            addRenderableWidget(Button.builder(Component.literal("×"),b -> send(4+selected)).bounds(leftPos+(tank==0 ? 18 : 157),topPos+70,12,10)
                    .tooltip(Tooltip.create(Component.translatable("gui.techguns.chem.dump_"+(tank==0 ? "input" : "output")))).build());
        }
    }
    @Override protected void updateMachineControls() {
        drain.setMessage(Component.translatable("gui.techguns.chem.drain",Component.translatable("gui.techguns.chem."+(menu.value(3)==1 ? "input" : "output"))));
    }
    @Override protected void extractMachineLabels(GuiGraphicsExtractor graphics) {}
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        super.extractBackground(graphics,mouseX,mouseY,partialTick);
        for (int tank=0;tank<2;tank++) {
            var fluid=menu.fluid(tank); int x=leftPos+(tank==0 ? 18 : 157);
            if (!fluid.isEmpty()) {
                var model=minecraft.getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
                int height=Math.clamp(fluid.getAmount()*50/menu.capacity(tank),0,50);
                int color=model.fluidTintSource()==null ? -1 : model.fluidTintSource().colorAsStack(fluid);
                for (int offset=0;offset<height;offset+=16) graphics.blitSprite(RenderPipelines.GUI_TEXTURED,model.stillMaterial().sprite(),
                        x+1,topPos+17+50-height+offset,10,Math.min(16,height-offset),color);
            }
            graphics.blit(RenderPipelines.GUI_TEXTURED,texture,x,topPos+17,176,32,12,52,256,256);
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick) {
        super.extractRenderState(graphics,mouseX,mouseY,partialTick);
        for (int tank=0;tank<2;tank++) {
            int x=leftPos+(tank==0 ? 18 : 157);
            if (mouseX>=x && mouseX<x+12 && mouseY>=topPos+17 && mouseY<topPos+69) {
                var fluid=menu.fluid(tank);
                graphics.setTooltipForNextFrame(java.util.List.of(
                        (fluid.isEmpty() ? Component.translatable("gui.techguns.chem.empty") : fluid.getHoverName()).getVisualOrderText(),
                        Component.translatable("gui.techguns.chem.amount",fluid.getAmount(),menu.capacity(tank)).getVisualOrderText()),mouseX,mouseY);
            }
        }
    }
    @Override protected void extractProgress(GuiGraphicsExtractor g) {
        if (menu.value(2)<=0) return;
        float p=(float)menu.value(1)/menu.value(2);
        int n=(int)(Math.min(p*5,1)*25); if(n>0) g.blit(RenderPipelines.GUI_TEXTURED,texture,leftPos+81,topPos+19,178,5,8,n,256,256);
        if (p>=.2f) { n=(int)(Math.min((p-.2f)*5,1)*9); if(n>0) g.blit(RenderPipelines.GUI_TEXTURED,texture,leftPos+88,topPos+39,186,25,n,1,256,256); }
        if (p>=.4f) { n=(int)(Math.min((p-.4f)*5,1)*20); if(n>0) g.blit(RenderPipelines.GUI_TEXTURED,texture,leftPos+97,topPos+23+20-n,194,9+20-n,14,n,256,256); }
        if (p>=.6f) { n=(int)(Math.min((p-.6f)*5,1)*16); if(n>0) g.blit(RenderPipelines.GUI_TEXTURED,texture,leftPos+108,topPos+17,205,3,n,6,256,256); }
        if (p>=.8f) { n=(int)((p-.8f)*5*24); g.blit(RenderPipelines.GUI_TEXTURED,texture,leftPos+117,topPos+20,214,6,14,n+1,256,256); }
    }
}
