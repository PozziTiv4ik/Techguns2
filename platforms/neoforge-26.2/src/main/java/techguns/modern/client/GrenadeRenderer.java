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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import techguns.modern.*;

/** Both original meshes, including the pinless flying FragGrenade and source 20-tick tumble. */
public final class GrenadeRenderer extends EntityRenderer<GrenadeProjectile,GrenadeRenderer.State> {
    private final ItemModelResolver models;
    public GrenadeRenderer(EntityRendererProvider.Context context) { super(context); models=context.getItemModelResolver(); }
    public static final class State extends EntityRenderState {
        final ItemStackRenderState item=new ItemStackRenderState();
        float yaw,pitch,spin;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(GrenadeProjectile entity,State state,float partial) {
        super.extractRenderState(entity,state,partial);
        var stack=TGContent.GRENADES.get(entity.grenade().id()).toStack();
        stack.set(DataComponents.ITEM_MODEL,TGContent.id(entity.grenade().id()+"_projectile"));
        models.updateForNonLiving(state.item,stack,ItemDisplayContext.NONE,entity);
        state.yaw=Mth.rotLerp(partial,entity.yRotO,entity.getYRot());
        state.pitch=Mth.rotLerp(partial,entity.xRotO,entity.getXRot());
        state.spin=entity.spinAge(partial)*-18;
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(state.yaw-90));
        pose.mulPose(Axis.ZP.rotationDegrees(state.pitch+state.spin));
        pose.scale(.8f,.8f,.8f);
        pose.mulPose(Axis.XP.rotationDegrees(-180)); pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate(-.03125,.83,-.0625);
        state.item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);
        pose.popPose(); super.submit(state,pose,collector,camera);
    }
}
