package techguns.modern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.*;
import techguns.modern.GrenadeItem;
import techguns.modern.TGContent;

/** RenderGrenade's 25-degree charge tilt, inside the native hand's isolated pose stack. */
final class GrenadeHandRendering {
    static void register(RegisterClientExtensionsEvent event) {
        var extension=new IClientItemExtensions() {
            @Override public boolean applyForgeHandTransform(PoseStack pose,LocalPlayer player,HumanoidArm arm,
                    ItemStack stack,float partial,float equip,float swing) {
                if(!player.isUsingItem() || player.getUseItem()!=stack || !(stack.getItem() instanceof GrenadeItem item)) return false;
                int side=arm==HumanoidArm.RIGHT?1:-1;
                pose.translate(side*.56,-.52-equip*.6,-.72);
                float charge=Mth.clamp((player.getTicksUsingItem()+partial)/item.grenade().chargeTicks,0,1);
                pose.mulPose(Axis.XP.rotationDegrees(25*charge));
                return true;
            }
        };
        TGContent.GRENADES.values().forEach(item->event.registerItem(extension,item.get()));
    }
    private GrenadeHandRendering() {}
}
