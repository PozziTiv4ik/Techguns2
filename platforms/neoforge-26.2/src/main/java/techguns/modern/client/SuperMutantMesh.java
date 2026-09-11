package techguns.modern.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from original ModelSuperMutant by pWn3d; Techguns Mod License. */
public final class SuperMutantMesh {
    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition part_bipedLeftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(5.0f, 2.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_bipedRightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-1.9f, 11.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_bipedBody = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_bipedRightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-5.0f, 2.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_bipedLeftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(1.9f, 11.0f, -0.0f, 0f, 0f, 0f));
        PartDefinition part_bipedHead = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_rightBoot = part_bipedRightLeg.addOrReplaceChild("rightBoot", CubeListBuilder.create().texOffs(0, 33).addBox(-2.0f, 0.0f, -2.0f, 5.0f, 6.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-0.6f, 6.0f, -0.7f, 0f, 0f, 0f));
        PartDefinition part_leftBoot = part_bipedLeftLeg.addOrReplaceChild("leftBoot", CubeListBuilder.create().texOffs(0, 33).mirror().addBox(-2.0f, 0.0f, -2.0f, 5.0f, 6.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-0.4f, 6.0f, -0.7f, 0f, 0f, 0f));
        PartDefinition part_bipedHeadwear = part_bipedHead.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, new CubeDeformation(0.5f)), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_chestarmorfront = part_bipedBody.addOrReplaceChild("chestarmorfront", CubeListBuilder.create().texOffs(21, 34).addBox(-4.0f, 0.0f, -2.0f, 6.0f, 7.0f, 1.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(1.0f, 1.0f, -1.0f, 0f, 0f, 0f));
        PartDefinition part_chestarmorback = part_bipedBody.addOrReplaceChild("chestarmorback", CubeListBuilder.create().texOffs(21, 44).addBox(-4.0f, 0.0f, -2.0f, 6.0f, 7.0f, 1.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(1.0f, 1.0f, 4.0f, 0f, 0f, 0f));
        PartDefinition part_belt = part_bipedBody.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 53).addBox(0.0f, 0.0f, 0.0f, 9.0f, 4.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-4.5f, 9.0f, -2.5f, 0f, 0f, 0f));
        PartDefinition part_leftarm_upper = part_bipedLeftArm.addOrReplaceChild("leftarm_upper", CubeListBuilder.create().texOffs(40, 33).mirror().addBox(-1.0f, -2.0f, -2.0f, 5.0f, 7.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-0.1f, -0.2f, -0.5f, 0f, 0f, 0f));
        PartDefinition part_rightarmUpper = part_bipedRightArm.addOrReplaceChild("rightarmUpper", CubeListBuilder.create().texOffs(40, 33).addBox(-3.0f, -2.0f, -2.0f, 5.0f, 7.0f, 5.0f, new CubeDeformation(0.0f)), PartPose.offsetAndRotation(-0.9f, -0.2f, -0.5f, 0f, 0f, 0f));
        return LayerDefinition.create(mesh, 64, 64);
    }
    private SuperMutantMesh() {}
}
