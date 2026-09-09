package techguns.modern.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import techguns.modern.TGContent;
import techguns.modern.machine.reaction.ReactionChamberMenu;
import techguns.modern.machine.reaction.ReactionChamberRecipe;

public final class ReactionChamberScreen extends AbstractContainerScreen<ReactionChamberMenu> {
    private static final Identifier TEXTURE=TGContent.id("textures/gui/reaction_chamber_gui.png");
    private Button redstone,security;
    public ReactionChamberScreen(ReactionChamberMenu menu,Inventory inventory,Component title) { super(menu,inventory,title,176,188); }
    private void send(int id) { if(minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id); }
    private void button(String label,int id,int x,int y,int w,int h) { addRenderableWidget(Button.builder(Component.literal(label),b -> send(id)).bounds(leftPos+x,topPos+y,w,h).build()); }
    @Override public void init() {
        super.init(); inventoryLabelY=73; titleLabelX=35;
        button("+",0,91,36,10,10); button("−",1,101,36,10,10);
        button("+",4,35,59,13,12); button("−",5,49,59,13,12);
        addRenderableWidget(Button.builder(Component.literal("×"),b -> send(6)).bounds(leftPos+18,topPos+5,12,10)
                .tooltip(Tooltip.create(Component.translatable("gui.techguns.chem.dump_input"))).build());
        redstone=addRenderableWidget(Button.builder(Component.empty(),b -> send(2)).bounds(leftPos+7,topPos+168,80,17).build());
        security=addRenderableWidget(Button.builder(Component.empty(),b -> send(3)).bounds(leftPos+89,topPos+168,80,17).build()); updateButtons();
    }
    private void updateButtons() {
        redstone.setMessage(Component.translatable("gui.techguns.machine."+switch(menu.value(9)) { case 1 -> "high"; case 2 -> "low"; default -> "ignore"; }));
        security.setMessage(Component.translatable("gui.techguns.machine."+(menu.value(10)==0 ? "public" : "private")));
    }
    @Override protected void containerTick() { super.containerTick(); updateButtons(); }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partialTick) {
        super.extractBackground(g,mouseX,mouseY,partialTick);
        g.blit(RenderPipelines.GUI_TEXTURED,TEXTURE,leftPos,topPos,0,0,176,166,256,256);
        g.fill(leftPos,topPos+166,leftPos+176,topPos+188,0xFFC6C6C6);
        g.fill(leftPos+6,topPos+17,leftPos+14,topPos+70,0xFF24282C);
        g.fill(leftPos+6,topPos+70-(int)((long)menu.value(0)*53/1000000),leftPos+14,topPos+70,0xFFE4A237);
        var fluid=menu.fluid();
        if(!fluid.isEmpty()) {
            var model=minecraft.getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
            int height=Math.clamp(fluid.getAmount()*50/10000,0,50);
            int color=model.fluidTintSource()==null ? -1 : model.fluidTintSource().colorAsStack(fluid);
            for(int offset=0;offset<height;offset+=16) g.blitSprite(RenderPipelines.GUI_TEXTURED,model.stillMaterial().sprite(),leftPos+19,topPos+67-height+offset,10,Math.min(16,height-offset),color);
        }
        g.blit(RenderPipelines.GUI_TEXTURED,TEXTURE,leftPos+18,topPos+17,176,32,12,52,256,256);
        g.blit(RenderPipelines.GUI_TEXTURED,TEXTURE,leftPos+19,topPos+17+50-menu.value(3)*5,177,32,7,1,256,256);
        int height=menu.value(4)*4;
        if(height>0) g.blit(RenderPipelines.GUI_TEXTURED,TEXTURE,leftPos+114,topPos+56-height,menu.value(4)==menu.value(5) ? 190 : 198,40-height,5,height,256,256);
        if(menu.value(13)!=0) {
            g.blit(RenderPipelines.GUI_TEXTURED,TEXTURE,leftPos+112,topPos+55-menu.value(5)*4,178,22,5,3,256,256);
            int complete=menu.value(6)*100/Math.max(1,menu.value(7)), elapsed=menu.value(1)*100/Math.max(1,menu.value(2));
            if(complete>0) g.blit(RenderPipelines.GUI_TEXTURED,TEXTURE,leftPos+67,topPos+61,0,167,complete,4,256,256);
            if(elapsed>0) g.blit(RenderPipelines.GUI_TEXTURED,TEXTURE,leftPos+67,topPos+69,0,175,elapsed,4,256,256);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int mouseX,int mouseY) {
        super.extractLabels(g,mouseX,mouseY); g.text(font,Component.literal(menu.value(3)*10+"%"),34,48,0xFF404040,false);
        g.text(font,Component.literal(Integer.toString(menu.value(4))),121,18,0xFF404040,false);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float partialTick) {
        super.extractRenderState(g,x,y,partialTick); int mx=x-leftPos,my=y-topPos;
        List<Component> tip=null;
        if(mx>=6 && mx<15 && my>=17 && my<70) tip=List.of(Component.translatable("gui.techguns.machine.energy",menu.value(0),1000000),Component.translatable("gui.techguns.reaction.power",menu.value(8)));
        else if(mx>=18 && mx<30 && my>=17 && my<69) tip=List.of(menu.fluid().isEmpty() ? Component.translatable("gui.techguns.chem.empty") : menu.fluid().getHoverName(),
                Component.translatable("gui.techguns.chem.amount",menu.fluid().getAmount(),10000),Component.translatable("gui.techguns.reaction.level",menu.value(3)*1000));
        else if(mx>=110 && mx<132 && my>=15 && my<59) tip=List.of(Component.translatable("gui.techguns.reaction.intensity",menu.value(4),menu.value(5)));
        else if(mx>=67 && mx<168 && my>=61 && my<74) tip=List.of(Component.translatable("gui.techguns.reaction.progress",menu.value(6),menu.value(7)),
                Component.translatable("gui.techguns.reaction.risk",Component.translatable("gui.techguns.reaction.risk."+ReactionChamberRecipe.RISKS.get(Math.clamp(menu.value(12),0,2)))));
        if(tip!=null) g.setTooltipForNextFrame(tip.stream().map(Component::getVisualOrderText).toList(),x,y);
    }
}
