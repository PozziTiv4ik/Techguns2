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
import techguns.core.AdvancedBulletRules;
import techguns.modern.AdvancedBulletProjectile;

/** Original bullet_blue texture, additive crossed strips and speed-dependent visibility delay. */
public final class AdvancedBulletRenderer extends EntityRenderer<AdvancedBulletProjectile, AdvancedBulletRenderer.State> {
    public AdvancedBulletRenderer(EntityRendererProvider.Context context) { super(context); }
    public static final class State extends EntityRenderState { Vec3 direction = Vec3.ZERO; boolean visible; }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(AdvancedBulletProjectile projectile, State state, float partialTick) {
        super.extractRenderState(projectile, state, partialTick);
        state.direction = projectile.getDeltaMovement().normalize();
        state.visible = projectile.tickCount + partialTick > AdvancedBulletRules.DELAY_FACTOR / projectile.renderSpeed();
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.visible || state.direction.lengthSqr() < .00001) return;
        Vec3 direction = state.direction;
        Vec3 right = Math.abs(direction.y) > .999 ? new Vec3(1, 0, 0) : new Vec3(direction.z, 0, -direction.x).normalize();
        Vec3 up = direction.cross(right).normalize();
        Vec3 first = right.add(up).normalize().scale(AdvancedBulletRules.HALF_WIDTH), second = right.subtract(up).normalize().scale(AdvancedBulletRules.HALF_WIDTH);
        Vec3 from = direction.scale(-AdvancedBulletRules.HALF_LENGTH), to = direction.scale(AdvancedBulletRules.HALF_LENGTH);
        collector.submitCustomGeometry(poses, LaserBeamRenderer.advancedBulletBody(), (pose, buffer) -> {
            strip(buffer, pose.pose(), from, to, first); strip(buffer, pose.pose(), from, to, second);
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
