package techguns.modern.client;

import net.minecraft.client.model.monster.ghast.GhastModel;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.GhastRenderState;
import net.minecraft.resources.Identifier;
import techguns.modern.npc.Ghastling;

/** Source-sized body, nine seeded tentacles, vanilla textures and synchronized charge expression. */
public final class GhastlingRenderer extends MobRenderer<Ghastling,GhastRenderState,GhastModel> {
    private static final Identifier NORMAL=Identifier.withDefaultNamespace("textures/entity/ghast/ghast.png");
    private static final Identifier ATTACK=Identifier.withDefaultNamespace("textures/entity/ghast/ghast_shooting.png");
    public GhastlingRenderer(EntityRendererProvider.Context context) { super(context,new GhastModel(GhastlingMesh.create().bakeRoot()),.25f); }
    @Override public GhastRenderState createRenderState() { return new GhastRenderState(); }
    @Override public Identifier getTextureLocation(GhastRenderState state) { return state.isCharging?ATTACK:NORMAL; }
    @Override public void extractRenderState(Ghastling entity,GhastRenderState state,float partialTick) {
        super.extractRenderState(entity,state,partialTick); state.isCharging=entity.isAttacking();
    }
}
