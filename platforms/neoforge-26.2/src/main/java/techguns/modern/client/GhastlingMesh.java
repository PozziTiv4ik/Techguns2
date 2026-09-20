package techguns.modern.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from original ModelGhastling; -0.6 model translation is folded into every pivot. Techguns Mod License. */
public final class GhastlingMesh {
    public static LayerDefinition create() {
        var mesh=new MeshDefinition(); var root=mesh.getRoot();
        root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-8.0f, -8.0f, -8.0f, 16.0f, 16.0f, 16.0f),PartPose.offset(0.0f,-1.6f,0.0f));
        root.addOrReplaceChild("tentacle0",CubeListBuilder.create().texOffs(0,0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f),PartPose.offset(-3.75f,5.4f,-5.0f));
        root.addOrReplaceChild("tentacle1",CubeListBuilder.create().texOffs(0,0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 13.0f, 2.0f),PartPose.offset(1.25f,5.4f,-5.0f));
        root.addOrReplaceChild("tentacle2",CubeListBuilder.create().texOffs(0,0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 9.0f, 2.0f),PartPose.offset(6.25f,5.4f,-5.0f));
        root.addOrReplaceChild("tentacle3",CubeListBuilder.create().texOffs(0,0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 11.0f, 2.0f),PartPose.offset(-6.25f,5.4f,0.0f));
        root.addOrReplaceChild("tentacle4",CubeListBuilder.create().texOffs(0,0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 11.0f, 2.0f),PartPose.offset(-1.25f,5.4f,0.0f));
        root.addOrReplaceChild("tentacle5",CubeListBuilder.create().texOffs(0,0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 10.0f, 2.0f),PartPose.offset(3.75f,5.4f,0.0f));
        root.addOrReplaceChild("tentacle6",CubeListBuilder.create().texOffs(0,0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 12.0f, 2.0f),PartPose.offset(-3.75f,5.4f,5.0f));
        root.addOrReplaceChild("tentacle7",CubeListBuilder.create().texOffs(0,0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 9.0f, 2.0f),PartPose.offset(1.25f,5.4f,5.0f));
        root.addOrReplaceChild("tentacle8",CubeListBuilder.create().texOffs(0,0).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 12.0f, 2.0f),PartPose.offset(6.25f,5.4f,5.0f));
        return LayerDefinition.create(mesh,64,32);
    }
    private GhastlingMesh() {}
}
