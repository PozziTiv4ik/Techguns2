package techguns.modern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import org.jspecify.annotations.Nullable;
import techguns.core.OreDrillRules;
import techguns.modern.TGContent;
import techguns.modern.machine.drill.*;

/** Original logarithmic stack of rotating cubes. Rendering uses snapshots, never live inventory during submit. */
public final class OreDrillRenderer implements BlockEntityRenderer<OreDrillBlockEntity,OreDrillRenderer.State> {
    private final ItemModelResolver items;
    public OreDrillRenderer(BlockEntityRendererProvider.Context c) { items=c.itemModelResolver(); }
    public static final class State extends BlockEntityRenderState {
        ItemStackRenderState slice=new ItemStackRenderState();
        boolean formed,head,turning;
        Direction direction=Direction.DOWN;
        int engines,rods,radius; float angle;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(OreDrillBlockEntity d,State s,float partial,Vec3 camera,ModelFeatureRenderer.@Nullable CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(d,s,partial,camera,overlay);
        s.formed=d.formed(); s.head=d.headLevel()>0; s.turning=d.turning(); s.direction=d.drillDirection();
        s.engines=d.size().engines(); s.rods=d.size().rods(); s.radius=d.size().miningRadius(); s.angle=(d.getLevel().getGameTime()%360)*10f+partial/36f;
        var stack=new ItemStack(OreDrillContent.BLOCKS.get("controller").get());
        stack.set(DataComponents.ITEM_MODEL,TGContent.id("oredrill_slice_"+switch(d.headLevel()) { case 2->"obsidiansteel"; case 3->"carbon"; default->"steel"; }));
        s.slice=new ItemStackRenderState(); items.updateForTopItem(s.slice,stack,ItemDisplayContext.NONE,d.getLevel(),null,0);
    }
    @Override public AABB getRenderBoundingBox(OreDrillBlockEntity d) {
        return new AABB(d.getBlockPos()).minmax(new AABB(d.getBlockPos().relative(d.drillDirection(),d.size().length()+1))).inflate(d.size().radius()+2);
    }
    @Override public void submit(State s,PoseStack p,SubmitNodeCollector collector,CameraRenderState camera) {
        if(!s.formed) return;
        int x=s.direction.getStepX(),y=s.direction.getStepY(),z=s.direction.getStepZ(),offset=s.engines+1;
        p.pushPose(); p.translate(.5-x*offset,1.25-y*offset,.5-z*offset);
        if(y<0) p.mulPose(Axis.ZP.rotationDegrees(180));
        else if(y>0) p.translate(0,-1.5,0);
        else if(x!=0) { p.mulPose(Axis.ZP.rotationDegrees(-90*x)); p.translate(x*.75,-.75,0); }
        else { p.mulPose(Axis.XP.rotationDegrees(90*z)); p.translate(0,-.75,z*.75); }
        p.translate(0,2*offset,0); if(s.head && s.turning) p.mulPose(Axis.YP.rotationDegrees(s.angle));
        if(!s.head) p.translate(0,-.2,0);
        for(int i=0;i<s.rods*(s.head?4:1);i++) {
            p.translate(0,s.head?.25:1,0); p.pushPose();
            if(!s.head || i%2==1) p.mulPose(Axis.YP.rotationDegrees(45));
            float width=s.head?OreDrillRules.headHalfWidth(s.rods,s.radius,i):.2f;
            p.scale(width*2,s.head?.5f:1,width*2);
            s.slice.submit(p,collector,s.lightCoords,OverlayTexture.NO_OVERLAY,0); p.popPose();
        }
        p.popPose();
    }
}
