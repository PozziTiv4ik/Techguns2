package techguns.modern.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Matrix4fc;
import techguns.modern.LaserBeam;
import techguns.modern.TGContent;

/** Original crossed, rotating additive strips and textures, submitted through the 26.2 renderer. */
public final class LaserBeamRenderer extends EntityRenderer<LaserBeam, LaserBeamRenderer.State> {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(TGContent.id("pipeline/laser"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexShader("core/entity").withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE").withShaderDefine("NO_OVERLAY").withShaderDefine("NO_CARDINAL_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexBinding(0, DefaultVertexFormat.ENTITY).withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false).build();
    private static final RenderType BODY = type("laser3"), START = type("laser3_start");

    private static RenderType type(String name) {
        return RenderType.create("techguns_" + name,
                RenderSetup.builder(PIPELINE).withTexture("Sampler0", TGContent.id("textures/fx/" + name + ".png")).createRenderSetup());
    }
    public static void pipelines(RegisterRenderPipelinesEvent event) { event.registerPipeline(PIPELINE); }
    public LaserBeamRenderer(EntityRendererProvider.Context context) { super(context); }
    public static final class State extends EntityRenderState {
        Vec3 end = Vec3.ZERO;
        float progress;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(LaserBeam beam, State state, float partialTick) {
        super.extractRenderState(beam, state, partialTick);
        state.end = beam.endOffset();
        state.progress = Math.clamp((beam.age() + partialTick) / beam.weapon().stats().projectileLifetime(), 0, 1);
    }
    @Override protected AABB getBoundingBoxForCulling(LaserBeam beam) { return beam.beamBounds(); }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        double length = state.end.length();
        if (length < .00001) return;
        // Capture immutable values: render states can be reused before deferred geometry is consumed.
        float progress = state.progress, brightness = (float) Math.sin(Math.sqrt(progress) * Math.PI);
        double width = 3 * brightness * 2 * .0125;
        Vec3 direction = state.end.scale(1 / length);
        Vec3 right = Math.abs(direction.y) > .999 ? new Vec3(1, 0, 0) : new Vec3(direction.z, 0, -direction.x).normalize();
        Vec3 up = direction.cross(right).normalize();
        double angle = Math.toRadians(45 + progress * 180);
        Vec3 first = right.scale(Math.cos(angle) * width).add(up.scale(Math.sin(angle) * width));
        Vec3 second = right.scale(-Math.sin(angle) * width).add(up.scale(Math.cos(angle) * width));
        double startLength = Math.min(1, length);
        Vec3 startEnd = direction.scale(startLength), end = state.end;
        collector.submitCustomGeometry(poses, START, (pose, buffer) -> {
            strip(buffer, pose.pose(), Vec3.ZERO, startEnd, first, 0, 1, brightness);
            strip(buffer, pose.pose(), Vec3.ZERO, startEnd, second, 0, 1, brightness);
        });
        if (length > startLength) collector.submitCustomGeometry(poses, BODY, (pose, buffer) -> {
            float endU = (float) (length / 3 * 2) + progress;
            strip(buffer, pose.pose(), startEnd, end, first, progress, endU, brightness);
            strip(buffer, pose.pose(), startEnd, end, second, progress, endU, brightness);
        });
    }
    private static void strip(VertexConsumer buffer, Matrix4fc pose, Vec3 from, Vec3 to, Vec3 side, float u0, float u1, float alpha) {
        vertex(buffer, pose, to.subtract(side), u1, 0, alpha);
        vertex(buffer, pose, from.subtract(side), u0, 0, alpha);
        vertex(buffer, pose, from.add(side), u0, 1, alpha);
        vertex(buffer, pose, to.add(side), u1, 1, alpha);
    }
    private static void vertex(VertexConsumer buffer, Matrix4fc pose, Vec3 point, float u, float v, float alpha) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(1f, 1f, 1f, alpha)
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0, 1, 0);
    }
}
