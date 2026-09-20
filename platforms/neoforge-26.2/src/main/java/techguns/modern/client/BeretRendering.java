package techguns.modern.client;

import net.minecraft.client.model.*;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.*;
import techguns.modern.armor.ArmorContent;

/** Source head children keep their own inflation/rotation; the vanilla render state supplies the pose. */
public final class BeretRendering {
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private HumanoidModel<HumanoidRenderState> adult, baby;
            @Override public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layer, Model original) {
                if (!(original instanceof HumanoidModel<?>)) return original;
                if (layer == EquipmentClientInfo.LayerType.HUMANOID_BABY) {
                    if (baby == null) baby = new HumanoidModel<>(BeretMesh.create().apply(HumanoidModel.BABY_TRANSFORMER).bakeRoot());
                    return baby;
                }
                if (layer != EquipmentClientInfo.LayerType.HUMANOID) return original;
                if (adult == null) adult = new HumanoidModel<>(BeretMesh.create().bakeRoot());
                return adult;
            }
        }, ArmorContent.BERET.get());
    }
    private BeretRendering() {}
}
