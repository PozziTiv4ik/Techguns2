package techguns.modern.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from original ModelSkeletonSoldier; Techguns license and attribution retained. */
public final class SkeletonSoldierMesh {
    public static LayerDefinition create(float inflation) {
        var deformation=new CubeDeformation(inflation);
        var mesh=HumanoidModel.createMesh(deformation,0); var root=mesh.getRoot();
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-1.0f, -2.0f, -1.0f, 2.0f, 12.0f, 2.0f, deformation), PartPose.offset(-5.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0f, -2.0f, -1.0f, 2.0f, 12.0f, 2.0f, deformation), PartPose.offset(5.0f, 2.0f, 0.0f));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 12.0f, 2.0f, deformation), PartPose.offset(-2.0f, 12.0f, 0.0f));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-1.0f, 0.0f, -1.0f, 2.0f, 12.0f, 2.0f, deformation), PartPose.offset(2.0f, 12.0f, 0.0f));
        return LayerDefinition.create(mesh,64,32);
    }
    private SkeletonSoldierMesh() {}
}
