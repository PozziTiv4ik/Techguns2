package techguns.modern.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import techguns.modern.TGContent;
import techguns.modern.npc.ZombieSoldier;

/** Original 64x64 skin, ModelGenericNPC humanoid layout, held-item poses and armor layers. */
public final class ZombieSoldierRenderer extends HumanoidMobRenderer<ZombieSoldier,HumanoidRenderState,HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE = TGContent.id("textures/entity/zombie_soldier.png");
    public ZombieSoldierRenderer(EntityRendererProvider.Context context) {
        super(context,new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),.5f);
        addLayer(new HumanoidArmorLayer<>(this,ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR,context.getModelSet(),HumanoidModel::new),context.getEquipmentRenderer()));
    }
    @Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
    @Override public Identifier getTextureLocation(HumanoidRenderState state) { return TEXTURE; }
    @Override protected HumanoidModel.ArmPose getArmPose(ZombieSoldier entity, HumanoidArm arm) {
        if (arm == entity.getMainArm() && entity.armed()) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        return entity.getItemHeldByArm(arm).isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
    }
}
