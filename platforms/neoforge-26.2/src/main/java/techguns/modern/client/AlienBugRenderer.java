package techguns.modern.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import techguns.modern.TGContent;
import techguns.modern.npc.AlienBug;

/** Original RenderLiving silhouette and animation, without vanilla spider eye/death layers. */
public final class AlienBugRenderer extends MobRenderer<AlienBug,AlienBugRenderer.State,AlienBugRenderer.Model> {
    public AlienBugRenderer(EntityRendererProvider.Context context) { super(context,new Model(AlienBugMesh.create().bakeRoot()),.8f); }
    public static final class State extends LivingEntityRenderState { public int attackTimer; }
    @Override public State createRenderState() { return new State(); }
    @Override public Identifier getTextureLocation(State state) { return TGContent.id("textures/entity/alienbug.png"); }
    @Override public void extractRenderState(AlienBug entity,State state,float partialTick) { super.extractRenderState(entity,state,partialTick); state.attackTimer=entity.attackTimer(); }
    public static final class Model extends EntityModel<State> {
        private final ModelPart head,lower,upper,a,b,c,d;
        public Model(ModelPart root) {
            super(root); head=root.getChild("h1"); lower=head.getChild("JAW_L"); upper=head.getChild("JAW_U");
            a=root.getChild("L1_A"); b=root.getChild("L1_A_1"); c=root.getChild("L1_A_2"); d=root.getChild("L1_A_3");
        }
        @Override public void setupAnim(State state) {
            super.setupAnim(state); head.yRot=state.yRot*(float)Math.PI/180; head.xRot=state.xRot*(float)Math.PI/180;
            float p=state.walkAnimationPos,s=state.walkAnimationSpeed;
            float f9=(float)(-Math.cos(p*.6662f*2)*.4f)*s, f10=(float)(-Math.cos(p*.6662f*2+(float)Math.PI)*.4f)*s;
            float f11=(float)(-Math.cos(p*.6662f*3)*.4f)*s, f12=(float)(-Math.cos(p*.6662f*4+(float)Math.PI)*.4f)*s;
            a.yRot+=f9*1.25f; d.yRot-=f9*1.25f; c.yRot+=f10*1.25f; b.yRot-=f10*1.25f;
            a.xRot-=f9; d.xRot+=f9; c.xRot-=f10; b.xRot+=f10;
            a.zRot-=f11*.75f; d.zRot+=f11*.75f; c.zRot-=f12*.75f; b.zRot+=f12*.75f;
            lower.xRot=.4553564018453205f-(float)Math.cos(state.ageInTicks*.2f)*.1f;
            upper.xRot=-.18203784098300857f+(float)Math.cos(state.ageInTicks*.1f)*.1f;
            if(state.attackTimer>5 && state.attackTimer<=10) {
                float f=(state.attackTimer-5)/5f, bite=.5f*(1-(float)Math.cos(f*(float)Math.PI));
                upper.xRot+=bite*.3f; lower.xRot-=bite*.4f;
            }
        }
    }
}
