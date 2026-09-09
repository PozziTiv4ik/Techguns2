package techguns.modern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import techguns.modern.RocketProjectile;
import techguns.modern.TGContent;

/** Original 11-part ModelRocket, oriented along flight with RenderRocketProjectile's 0.9 scale. */
public final class RocketRenderer extends EntityRenderer<RocketProjectile, RocketRenderer.State> {
    private final ItemModelResolver models;
    public RocketRenderer(EntityRendererProvider.Context context) { super(context); models = context.getItemModelResolver(); }
    public static final class State extends EntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float yaw, pitch;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(RocketProjectile entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        var stack = TGContent.AMMO.get(entity.variant().ammo()).toStack();
        stack.set(DataComponents.ITEM_MODEL, TGContent.id("rocket_projectile_" + entity.variant().id()));
        models.updateForNonLiving(state.item, stack, ItemDisplayContext.NONE, entity);
        state.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.pitch = Mth.rotLerp(partialTick, entity.xRotO, entity.getXRot());
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(state.yaw - 90));
        pose.mulPose(Axis.ZP.rotationDegrees(state.pitch));
        pose.scale(.9f, .9f, .9f);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
