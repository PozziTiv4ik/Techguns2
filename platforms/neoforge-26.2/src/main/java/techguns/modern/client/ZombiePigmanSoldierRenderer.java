package techguns.modern.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import techguns.modern.npc.ZombiePigmanSoldier;

/** Current Minecraft pigman visuals, retaining GenericNPC's aimed-gun humanoid poses. */
public final class ZombiePigmanSoldierRenderer extends HumanoidMobRenderer<ZombiePigmanSoldier,HumanoidRenderState,HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE=Identifier.withDefaultNamespace("textures/entity/piglin/zombified_piglin.png");
    public ZombiePigmanSoldierRenderer(EntityRendererProvider.Context context) {
        super(context,new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIFIED_PIGLIN)),.5f);
        addLayer(new HumanoidArmorLayer<>(this,ArmorModelSet.bake(ModelLayers.ZOMBIFIED_PIGLIN_ARMOR,context.getModelSet(),HumanoidModel::new),context.getEquipmentRenderer()));
    }
    @Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
    @Override public Identifier getTextureLocation(HumanoidRenderState state) { return TEXTURE; }
    @Override protected HumanoidModel.ArmPose getArmPose(ZombiePigmanSoldier entity,HumanoidArm arm) {
        if (arm==entity.getMainArm() && entity.armed()) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        return entity.getItemHeldByArm(arm).isEmpty()?HumanoidModel.ArmPose.EMPTY:HumanoidModel.ArmPose.ITEM;
    }
}
