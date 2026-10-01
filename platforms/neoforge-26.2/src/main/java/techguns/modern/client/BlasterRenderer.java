package techguns.modern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import techguns.modern.BlasterProjectile;

/** RenderBlasterProjectile's laser3 sprite and crossed .2 x .05 additive strips. */
public final class BlasterRenderer extends EntityRenderer<BlasterProjectile, BlasterRenderer.State> {
    public BlasterRenderer(EntityRendererProvider.Context context) { super(context); }
    public static final class State extends EntityRenderState {
        Vec3 direction = new Vec3(0, 0, 1);
        boolean visible;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(BlasterProjectile projectile, State state, float partialTick) {
        super.extractRenderState(projectile, state, partialTick);
        state.direction = projectile.getDeltaMovement().normalize();
        state.visible = projectile.tickCount >= 2 || partialTick > .25f;
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.visible || state.direction.lengthSqr() < .00001) return;
        Vec3 direction = state.direction;
        Vec3 right = Math.abs(direction.y) > .999 ? new Vec3(1, 0, 0) : new Vec3(direction.z, 0, -direction.x).normalize();
        Vec3 up = direction.cross(right).normalize();
        Vec3 first = right.add(up).normalize().scale(.025), second = right.subtract(up).normalize().scale(.025);
        Vec3 from = direction.scale(-.1), to = direction.scale(.1);
        // The old four one-sided quads form two strips; this pipeline draws both sides.
        collector.submitCustomGeometry(poses, LaserBeamRenderer.blasterBody(), (pose, buffer) -> {
            strip(buffer, pose.pose(), from, to, first);
            strip(buffer, pose.pose(), from, to, second);
        });
    }
    private static void strip(VertexConsumer buffer, Matrix4fc pose, Vec3 from, Vec3 to, Vec3 side) {
        vertex(buffer, pose, from.subtract(side), 0, 0); vertex(buffer, pose, to.subtract(side), 1, 0);
        vertex(buffer, pose, to.add(side), 1, 1); vertex(buffer, pose, from.add(side), 0, 1);
    }
    private static void vertex(VertexConsumer buffer, Matrix4fc pose, Vec3 point, float u, float v) {
        buffer.addVertex(pose, (float)point.x, (float)point.y, (float)point.z).setColor(1f, 1f, 1f, 1f)
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
    }
}
