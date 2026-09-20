package techguns.modern.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from original ModelBeret by pWn3d; Techguns Mod License. */
public final class BeretMesh {
    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        for (String part : new String[]{"body", "right_arm", "left_arm", "right_leg", "left_leg"})
            root.addOrReplaceChild(part, CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("berettop", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0f, -8.7f, -4.0f, 6.0f, 2.0f, 8.0f, new CubeDeformation(0.7f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0f, 0f, 0f));
        head.addOrReplaceChild("beretside", CubeListBuilder.create().texOffs(0, 11).addBox(-3.0f, 0.0f, 0.0f, 3.0f, 2.0f, 8.0f, new CubeDeformation(0.65f)), PartPose.offsetAndRotation(-2.7f, -8.5f, -4.0f, 0.0f, 0.0f, -0.6108652381980153f));
        return LayerDefinition.create(mesh, 32, 32);
    }
    private BeretMesh() {}
}
