package techguns.modern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import techguns.modern.*;

/** RenderGrenade40mmProjectile keeps the original half-scale OBJ and flight orientation without tumbling. */
public final class Grenade40mmRenderer extends EntityRenderer<Grenade40mmProjectile,Grenade40mmRenderer.State> {
    private final ItemModelResolver models;
    public Grenade40mmRenderer(EntityRendererProvider.Context context) {super(context);models=context.getItemModelResolver();}
    public static final class State extends EntityRenderState {
        final ItemStackRenderState item=new ItemStackRenderState();float yaw,pitch;
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(Grenade40mmProjectile entity,State state,float partial) {
        super.extractRenderState(entity,state,partial);
        var stack=TGContent.AMMO.get("40mmgrenade").toStack();stack.set(DataComponents.ITEM_MODEL,TGContent.id("grenade40mm"));
        models.updateForNonLiving(state.item,stack,ItemDisplayContext.NONE,entity);state.yaw=entity.getYRot();state.pitch=entity.getXRot();
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(state.yaw-90));pose.mulPose(Axis.ZP.rotationDegrees(state.pitch));
        pose.mulPose(Axis.XP.rotationDegrees(180));pose.scale(.5f,.5f,.5f);
        state.item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);pose.popPose();super.submit(state,pose,collector,camera);
    }
}
