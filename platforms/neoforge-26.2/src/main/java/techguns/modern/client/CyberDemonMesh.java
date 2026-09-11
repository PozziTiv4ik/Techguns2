package techguns.modern.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from original ModelCyberDemon; Techguns Mod License. */
public final class CyberDemonMesh {
    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition part_bipedHead = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_bipedBody = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_bipedRightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0f, 2.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_bipedLeftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0f, 2.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_bipedRightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.9f, 12.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_bipedLeftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offsetAndRotation(1.9f, 12.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_bipedHeadwear = part_bipedHead.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0f, 0f, 0f));
        PartDefinition part_h2 = part_bipedHead.addOrReplaceChild("h2", CubeListBuilder.create().texOffs(48, 0).addBox(-3.0f, -5.0f, -5.0f, 6.0f, 5.0f, 2.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        PartDefinition part_h3 = part_bipedHead.addOrReplaceChild("h3", CubeListBuilder.create().texOffs(52, 7).addBox(4.0f, -7.0f, -2.0f, 2.0f, 4.0f, 4.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        PartDefinition part_head = part_bipedHead.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0f, -8.0f, -3.0f, 8.0f, 8.0f, 7.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        PartDefinition part_h4 = part_bipedHead.addOrReplaceChild("h4", CubeListBuilder.create().texOffs(30, 8).addBox(-1.0f, 0.0f, -5.0f, 2.0f, 2.0f, 5.0f), PartPose.offsetAndRotation(-7.5f, -5.5f, -1.0f, -0.4363323f, 0.0f, 0.0f));
        PartDefinition part_h5 = part_bipedHead.addOrReplaceChild("h5", CubeListBuilder.create().texOffs(52, 7).addBox(-6.0f, -7.0f, -2.0f, 2.0f, 4.0f, 4.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        PartDefinition part_h6 = part_bipedHead.addOrReplaceChild("h6", CubeListBuilder.create().texOffs(24, 0).addBox(0.0f, -1.5f, -3.0f, 4.0f, 3.0f, 3.0f), PartPose.offsetAndRotation(6.0f, -5.0f, 1.5f, 0.0f, 0.6981317f, 0.0f));
        PartDefinition part_h7 = part_bipedHead.addOrReplaceChild("h7", CubeListBuilder.create().texOffs(24, 0).addBox(-4.0f, -1.5f, -3.0f, 4.0f, 3.0f, 3.0f), PartPose.offsetAndRotation(-6.0f, -5.0f, 1.5f, 0.0f, -0.6981317f, 0.0f));
        PartDefinition part_h8 = part_bipedHead.addOrReplaceChild("h8", CubeListBuilder.create().texOffs(30, 8).addBox(-1.0f, 0.0f, -5.0f, 2.0f, 2.0f, 5.0f), PartPose.offsetAndRotation(7.5f, -5.5f, -1.0f, -0.4363323f, 0.0f, 0.0f));
        PartDefinition part_b2 = part_bipedBody.addOrReplaceChild("b2", CubeListBuilder.create().texOffs(0, 28).addBox(-4.5f, 7.0f, -2.5f, 9.0f, 5.0f, 5.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        PartDefinition part_body = part_bipedBody.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 15).addBox(-6.0f, 0.0f, -3.0f, 12.0f, 7.0f, 6.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        PartDefinition part_rightArm = part_bipedRightArm.addOrReplaceChild("rightArm", CubeListBuilder.create().texOffs(44, 16).addBox(-6.0f, -2.0f, -2.5f, 5.0f, 7.0f, 5.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        PartDefinition part_ra2 = part_bipedRightArm.addOrReplaceChild("ra2", CubeListBuilder.create().texOffs(48, 28).addBox(-5.5f, 3.0f, 0.5f, 4.0f, 8.0f, 4.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.5205006f, 0.0f, 0.0f));
        PartDefinition part_la2 = part_bipedLeftArm.addOrReplaceChild("la2", CubeListBuilder.create().texOffs(48, 28).addBox(1.5f, 3.0f, 0.5f, 4.0f, 8.0f, 4.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.5205006f, 0.0f, 0.0f));
        PartDefinition part_leftArm = part_bipedLeftArm.addOrReplaceChild("leftArm", CubeListBuilder.create().texOffs(44, 16).addBox(1.0f, -2.0f, -2.5f, 5.0f, 7.0f, 5.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        PartDefinition part_rightLeg = part_bipedRightLeg.addOrReplaceChild("rightLeg", CubeListBuilder.create().texOffs(0, 38).addBox(-3.6f, -1.0f, -2.5f, 5.0f, 7.0f, 5.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.5235988f, 0.0f, 0.0f));
        PartDefinition part_rl2 = part_bipedRightLeg.addOrReplaceChild("rl2", CubeListBuilder.create().texOffs(20, 39).addBox(-3.1f, 3.0f, 1.5f, 4.0f, 7.0f, 4.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.5235988f, 0.0f, 0.0f));
        PartDefinition part_rl3 = part_bipedRightLeg.addOrReplaceChild("rl3", CubeListBuilder.create().texOffs(0, 50).addBox(-3.6f, 9.0f, -4.0f, 5.0f, 3.0f, 5.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        PartDefinition part_leftLeg = part_bipedLeftLeg.addOrReplaceChild("leftLeg", CubeListBuilder.create().texOffs(0, 38).addBox(-1.4f, -1.0f, -2.5f, 5.0f, 7.0f, 5.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.5235988f, 0.0f, 0.0f));
        PartDefinition part_ll2 = part_bipedLeftLeg.addOrReplaceChild("ll2", CubeListBuilder.create().texOffs(20, 39).addBox(-0.9f, 3.0f, 1.5f, 4.0f, 7.0f, 4.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, -0.5235988f, 0.0f, 0.0f));
        PartDefinition part_ll3 = part_bipedLeftLeg.addOrReplaceChild("ll3", CubeListBuilder.create().texOffs(0, 50).addBox(-1.4f, 9.0f, -4.0f, 5.0f, 3.0f, 5.0f), PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }
    private CyberDemonMesh() {}
}
