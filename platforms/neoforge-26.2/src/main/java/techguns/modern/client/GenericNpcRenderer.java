package techguns.modern.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import techguns.modern.TGContent;
import techguns.modern.npc.ArmedNpc;

/** Source ModelGenericNPC layout with a per-NPC skin; skeleton and mutant meshes use separate renderers. */
public final class GenericNpcRenderer<T extends ArmedNpc> extends HumanoidMobRenderer<T,HumanoidRenderState,HumanoidModel<HumanoidRenderState>> {
    private final Identifier texture;
    public GenericNpcRenderer(EntityRendererProvider.Context context) { this(context,TGContent.id("textures/entity/zombie_soldier.png")); }
    public GenericNpcRenderer(EntityRendererProvider.Context context,Identifier texture) {
        super(context,new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),.5f);
        this.texture=texture;
        addLayer(new HumanoidArmorLayer<>(this,ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR,context.getModelSet(),HumanoidModel::new),context.getEquipmentRenderer()));
    }
    @Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
    @Override public Identifier getTextureLocation(HumanoidRenderState state) { return texture; }
    @Override protected HumanoidModel.ArmPose getArmPose(T entity, HumanoidArm arm) {
        if (arm == entity.getMainArm() && entity.armed()) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        return entity.getItemHeldByArm(arm).isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
    }
}
