package techguns.modern.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import techguns.modern.TGContent;
import techguns.modern.machine.drill.*;

public final class OreDrillScreen extends AbstractContainerScreen<OreDrillMenu> {
    private static final Identifier TEXTURE=TGContent.id("textures/gui/ore_drill_gui.png");
    private Button redstone,security;
    public OreDrillScreen(OreDrillMenu menu,Inventory inv,Component title) { super(menu,inv,title,176,188); }
    private void send(int id) { if(minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id); }
    @Override public void init() {
        super.init(); inventoryLabelY=73; titleLabelX=35;
        redstone=addRenderableWidget(Button.builder(Component.empty(),b->send(2)).bounds(leftPos+7,topPos+168,80,17).build());
        security=addRenderableWidget(Button.builder(Component.empty(),b->send(3)).bounds(leftPos+89,topPos+168,80,17).build()); update();
    }
    private void update() {
        redstone.setMessage(Component.translatable("gui.techguns.machine."+switch(menu.value(6)) { case 1->"high"; case 2->"low"; default->"ignore"; }));
        security.setMessage(Component.translatable("gui.techguns.machine."+(menu.value(7)==0?"public":"private")));
    }
    @Override protected void containerTick() { super.containerTick(); update(); }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float partial) {
        super.extractBackground(g,x,y,partial);
        g.blit(RenderPipelines.GUI_TEXTURED,TEXTURE,leftPos,topPos,0,0,176,166,256,256); g.fill(leftPos,topPos+166,leftPos+176,topPos+188,0xFFC6C6C6);
        g.fill(leftPos+6,topPos+17,leftPos+13,topPos+67,0xFF24282C);
        g.fill(leftPos+6,topPos+67-(int)((long)menu.value(0)*50/OreDrillBlockEntity.CAPACITY),leftPos+13,topPos+67,0xFFE4A237);
        int done=(int)((long)menu.value(1)*38/Math.max(1,menu.value(2))); g.fill(leftPos+77,topPos+55-done,leftPos+95,topPos+55,0xAA71A65E);
        int fire=(int)((long)menu.value(4)*13/Math.max(1,menu.value(5))); if(fire>0) g.fill(leftPos+34,topPos+50-fire,leftPos+42,topPos+50,0xFFF4A035);
        for(int tank=0;tank<2;tank++) {
            var fluid=menu.fluid(tank); int px=leftPos+(tank==0?15:157);
            if(!fluid.isEmpty()) {
                var model=minecraft.getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState()); int height=(int)((long)fluid.getAmount()*50/(tank==0?16000:32000));
                int color=model.fluidTintSource()==null?-1:model.fluidTintSource().colorAsStack(fluid);
                for(int offset=0;offset<height;offset+=16) g.blitSprite(RenderPipelines.GUI_TEXTURED,model.stillMaterial().sprite(),px,topPos+67-height+offset,10,Math.min(16,height-offset),color);
            }
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float partial) {
        super.extractRenderState(g,x,y,partial); int mx=x-leftPos,my=y-topPos; List<Component> tip=null;
        if(mx>=6 && mx<14 && my>=17 && my<68) tip=List.of(Component.translatable("gui.techguns.machine.energy",menu.value(0),OreDrillBlockEntity.CAPACITY));
        else if(mx>=74 && mx<101 && my>=16 && my<56) tip=List.of(Component.translatable("gui.techguns.drill.rate",String.format(Locale.ROOT,"%.2f",menu.value(2)>0?72000.0/menu.value(2):0),menu.value(3)),Component.translatable("gui.techguns.drill.size",menu.value(9),menu.value(10)));
        else if(mx>=34 && mx<43 && my>=36 && my<51) tip=List.of(Component.translatable("gui.techguns.drill.fuel",menu.value(4),menu.value(5)));
        else for(int tank=0;tank<2;tank++) { int tx=tank==0?15:157; if(mx>=tx && mx<tx+10 && my>=17 && my<68) { var fluid=menu.fluid(tank); tip=List.of(fluid.isEmpty()?Component.translatable("gui.techguns.chem.empty"):fluid.getHoverName(),Component.translatable("gui.techguns.chem.amount",fluid.getAmount(),tank==0?16000:32000)); } }
        if(tip!=null) g.setTooltipForNextFrame(tip.stream().map(Component::getVisualOrderText).toList(),x,y);
    }
}
