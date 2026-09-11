package techguns.modern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import techguns.modern.GunItem;
import techguns.modern.TGContent;
import techguns.modern.npc.CyberDemon;

public final class CyberDemonRenderer extends HumanoidMobRenderer<CyberDemon, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE = TGContent.id("textures/entity/cyberdemon.png");
    public CyberDemonRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(CyberDemonMesh.create().bakeRoot()), .5f);
        layers.removeIf(layer -> layer instanceof ItemInHandLayer);
        addLayer(new ItemInHandLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>>(this) {
            @Override protected void submitArmWithItem(HumanoidRenderState state, ItemStackRenderState item, ItemStack stack,
                                                       HumanoidArm arm, PoseStack pose, SubmitNodeCollector collector, int light) {
                if (!(stack.getItem() instanceof GunItem)) { super.submitArmWithItem(state, item, stack, arm, pose, collector, light); return; }
                if (item.isEmpty()) return;
                pose.pushPose();
                getParentModel().translateToHand(state, arm, pose);
                pose.mulPose(Axis.XP.rotationDegrees(-90)); pose.mulPose(Axis.YP.rotationDegrees(180));
                int side = arm == HumanoidArm.LEFT ? -1 : 1;
                pose.translate(side * (1.0 / 16 + .16), .125 + .72, -.625 + .1);
                pose.scale(1.5f, 1.5f, 1.5f);
                item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
                pose.popPose();
            }
        });
    }
    @Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
    @Override public Identifier getTextureLocation(HumanoidRenderState state) { return TEXTURE; }
    @Override protected HumanoidModel.ArmPose getArmPose(CyberDemon entity, HumanoidArm arm) {
        // The source explicitly disables both the aimed bow animation and the weapon arm pose.
        return HumanoidModel.ArmPose.EMPTY;
    }
}
