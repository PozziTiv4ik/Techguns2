package techguns.modern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import techguns.modern.GunItem;
import techguns.modern.TGContent;
import techguns.modern.npc.SuperMutant;

public final class SuperMutantRenderer extends HumanoidMobRenderer<SuperMutant, HumanoidRenderState, SuperMutantRenderer.Model> {
    private static final Identifier TEXTURE = TGContent.id("textures/entity/supermutant_texture_1.png");
    public SuperMutantRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(SuperMutantMesh.create().bakeRoot()), .5f);
        layers.removeIf(layer -> layer instanceof ItemInHandLayer);
        addLayer(new ItemInHandLayer<HumanoidRenderState, Model>(this) {
            @Override protected void submitArmWithItem(HumanoidRenderState state, ItemStackRenderState item, ItemStack stack,
                                                       HumanoidArm arm, PoseStack pose, SubmitNodeCollector collector, int light) {
                if (!(stack.getItem() instanceof GunItem)) { super.submitArmWithItem(state, item, stack, arm, pose, collector, light); return; }
                if (item.isEmpty()) return;
                pose.pushPose();
                getParentModel().translateToHand(state, arm, pose);
                pose.mulPose(Axis.XP.rotationDegrees(-90)); pose.mulPose(Axis.YP.rotationDegrees(180));
                int side = arm == HumanoidArm.LEFT ? -1 : 1;
                pose.translate(side * (1.0 / 16 + .13), .125, -.625 - .18);
                item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
                pose.popPose();
            }
        });
    }
    @Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
    @Override public Identifier getTextureLocation(HumanoidRenderState state) { return TEXTURE; }
    @Override public Vec3 getRenderOffset(HumanoidRenderState state) { return super.getRenderOffset(state).add(0, .55, 0); }
    @Override protected HumanoidModel.ArmPose getArmPose(SuperMutant entity, HumanoidArm arm) {
        if (arm == entity.getMainArm() && entity.armed()) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        return entity.getItemHeldByArm(arm).isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
    }
    public static final class Model extends HumanoidModel<HumanoidRenderState> {
        Model(ModelPart root) { super(root); }
        @Override public void setupAnim(HumanoidRenderState state) {
            super.setupAnim(state);
            root.xScale = root.yScale = root.zScale = 1.35f;
        }
        @Override public void translateToHand(HumanoidRenderState state, HumanoidArm arm, PoseStack pose) {
            // Original ModelSuperMutant scales the body rendering, while LayerHeldItem uses unscaled postRenderArm.
            getArm(arm).translateAndRotate(pose);
        }
    }
}
