package techguns.modern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import techguns.core.SkeletonSoldierRules;
import techguns.modern.npc.SkeletonSoldier;

public final class SkeletonSoldierRenderer extends HumanoidMobRenderer<SkeletonSoldier,HumanoidRenderState,HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE=Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png");
    public SkeletonSoldierRenderer(EntityRendererProvider.Context context) {
        super(context,model(SkeletonSoldierRules.BODY_INFLATION),.5f);
        addLayer(new HumanoidArmorLayer<>(this,new ArmorModelSet<>(model(1),model(1),model(.5f),model(1)),context.getEquipmentRenderer()));
        layers.removeIf(layer -> layer instanceof ItemInHandLayer);
        addLayer(new ItemInHandLayer<HumanoidRenderState,HumanoidModel<HumanoidRenderState>>(this) {
            @Override protected void submitArmWithItem(HumanoidRenderState state,ItemStackRenderState item,ItemStack stack,HumanoidArm arm,PoseStack pose,SubmitNodeCollector collector,int light) {
                if(item.isEmpty()) return;
                pose.pushPose(); getParentModel().translateToHand(state,arm,pose);
                pose.mulPose(Axis.XP.rotationDegrees(-90)); pose.mulPose(Axis.YP.rotationDegrees(180));
                boolean left=arm==HumanoidArm.LEFT;
                pose.translate((left?-1:1)/16f,.125f,-.625f);
                // Source LayerHeldItemTranslateGun applies these after the hand/item transform.
                pose.translate(SkeletonSoldierRules.heldX(left),SkeletonSoldierRules.HELD_Y,0);
                item.submit(pose,collector,light,OverlayTexture.NO_OVERLAY,state.outlineColor); pose.popPose();
            }
        });
    }
    private static HumanoidModel<HumanoidRenderState> model(float inflation) { return new HumanoidModel<>(SkeletonSoldierMesh.create(inflation).bakeRoot()); }
    @Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
    @Override public Identifier getTextureLocation(HumanoidRenderState state) { return TEXTURE; }
    @Override protected HumanoidModel.ArmPose getArmPose(SkeletonSoldier entity,HumanoidArm arm) {
        if(arm==entity.getMainArm() && entity.armed()) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        return entity.getItemHeldByArm(arm).isEmpty()?HumanoidModel.ArmPose.EMPTY:HumanoidModel.ArmPose.ITEM;
    }
}
