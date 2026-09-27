package techguns.modern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import techguns.core.HelicopterAttack;
import techguns.modern.TGContent;
import techguns.modern.npc.*;

/** Original three OBJ parts, Apache skin, two independent rotations and 100-tick death animation. */
public final class AttackHelicopterRenderer extends EntityRenderer<AttackHelicopter,AttackHelicopterRenderer.State> {
    private final ItemModelResolver models;
    public AttackHelicopterRenderer(EntityRendererProvider.Context context) { super(context); models=context.getItemModelResolver(); shadowRadius=5; }
    public static final class State extends EntityRenderState {
        final ItemStackRenderState[] parts={new ItemStackRenderState(),new ItemStackRenderState(),new ItemStackRenderState()};
        float yaw,pitch,rotor,death;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(AttackHelicopter entity,State state,float partial) {
        super.extractRenderState(entity,state,partial);
        for(int i=0;i<3;i++) { var stack=NpcContent.HELICOPTER_EGG.toStack(); stack.set(DataComponents.ITEM_MODEL,TGContent.id("helicopter"+i)); models.updateForNonLiving(state.parts[i],stack,ItemDisplayContext.NONE,entity); }
        state.yaw=Mth.rotLerp(partial,entity.yRotO,entity.getYRot()); state.pitch=Mth.rotLerp(partial,entity.xRotO,entity.getXRot());
        state.death=entity.deathTime==0?0:entity.deathTime+partial; state.rotor=(entity.level().getGameTime()%60+partial)*(state.death==0?24:12);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(-state.yaw-90)); pose.scale(2.5f,2.5f,2.5f);
        if(state.death>0) { pose.translate(0,HelicopterAttack.deathOffset(state.death),0); pose.mulPose(Axis.YP.rotationDegrees(HelicopterAttack.deathTurn(state.death))); }
        state.parts[0].submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);
        pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(state.rotor)); state.parts[1].submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor); pose.popPose();
        pose.pushPose(); if(state.death==0) { pose.translate(1.2,0,0); pose.mulPose(Axis.ZP.rotationDegrees(-state.pitch)); pose.translate(-1.2,0,0); }
        state.parts[2].submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor); pose.popPose(); pose.popPose(); super.submit(state,pose,collector,camera);
    }
}
