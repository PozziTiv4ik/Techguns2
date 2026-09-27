package techguns.modern.client;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.*;
import org.joml.*;
import techguns.core.FlameRules;
import techguns.modern.TGContent;

/** Source first-person sine sway, adapted to the modern +X item mesh. */
public record FlamethrowerModel(ItemModel body, ModelRenderProperties properties, Matrix4fc transform) implements ItemModel {
    private record State(ItemStackRenderState body, Matrix4f pose) {}
    private static final SpecialModelRenderer<State> RENDERER = new SpecialModelRenderer<>() {
        @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
            pose.pushPose(); pose.mulPose(state.pose); state.body.submit(pose, collector, light, overlay, outline); pose.popPose();
        }
        @Override public State extractArgument(ItemStack stack) { return null; }
        @Override public void getExtents(Consumer<Vector3fc> out) {}
    };
    @Override public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed) {
        boolean first = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        float sway = first && level != null && item.getOrDefault(TGContent.RELOAD_TICKS.get(), 0) == 0
                ? FlameRules.recoil(item.getOrDefault(TGContent.FLAME_RECOIL_TIME.get(), -1L), level.getGameTime(), Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) : 0;
        float mirror = left ? -1 : 1, shift = sway * FlameRules.RECOIL_TRANSLATION;
        // Inverse of the mesh's +90-degree display yaw: source view axes remain unchanged.
        var axis = new Vector3f(-.25f * mirror, -.75f * mirror, 1).normalize();
        var pose = new Matrix4f().translation(.5f - .5f * shift, .5f + .75f * shift, .5f + mirror * shift)
                .rotate(new Quaternionf().rotationAxis((float)java.lang.Math.toRadians(sway * FlameRules.RECOIL_DEGREES), axis));
        var state = new ItemStackRenderState(); body.update(state, item, resolver, ItemDisplayContext.NONE, level, owner, seed);
        List<Vector3fc> extents = new ArrayList<>(); state.visitExtents(v -> extents.add(pose.transformPosition(new Vector3f(v))));
        var layer = output.newLayer(); layer.setExtents(() -> extents.toArray(Vector3fc[]::new)); layer.setLocalTransform(transform);
        properties.applyToLayer(layer, context); layer.setupSpecialModel(RENDERER, new State(state, pose));
        output.appendModelIdentityElement(this); output.appendModelIdentityElement(sway); output.appendModelIdentityElement(left); output.appendModelIdentityElement(item.hasFoil());
        if (sway != 0 || item.hasFoil()) output.setAnimated();
    }
    public record Unbaked() implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());
        @Override public MapCodec<Unbaked> type() { return CODEC; }
        @Override public void resolveDependencies(ResolvableModel.Resolver resolver) { resolver.markDependency(TGContent.id("item/flamethrower")); }
        @Override public ItemModel bake(BakingContext context, Matrix4fc transformation) {
            var baker = context.blockModelBaker(); var root = baker.getModel(TGContent.id("item/flamethrower"));
            var body = new CuboidItemModelWrapper.Unbaked(TGContent.id("item/flamethrower"), Optional.empty(), List.of()).bake(context, new Matrix4f());
            return new FlamethrowerModel(body, ModelRenderProperties.fromResolvedModel(baker, root, root.getTopTextureSlots()), transformation);
        }
    }
}
