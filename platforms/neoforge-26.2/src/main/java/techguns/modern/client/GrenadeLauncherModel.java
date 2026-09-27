package techguns.modern.client;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.*;
import org.joml.*;
import techguns.core.LauncherAnimation;
import techguns.modern.TGContent;

/** Original independent OBJ drum, rotated continuously about its original pivot. */
public record GrenadeLauncherModel(ItemModel body,ItemModel drum,ModelRenderProperties properties,Matrix4fc transform) implements ItemModel {
    private record State(ItemStackRenderState body,ItemStackRenderState drum,float degrees) {}
    private static final SpecialModelRenderer<State> RENDERER=new SpecialModelRenderer<>() {
        @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline) {
            pose.pushPose();pose.translate(.5,.5,.5); // outer layer already applied the native item center
            state.body.submit(pose,collector,light,overlay,outline);
            pose.translate(0,-.125,0);pose.mulPose(Axis.XP.rotationDegrees(state.degrees));pose.translate(0,.125,0);
            state.drum.submit(pose,collector,light,overlay,outline);pose.popPose();
        }
        @Override public State extractArgument(ItemStack stack) { return null; } // Arguments include the owner/context, supplied by update.
        @Override public void getExtents(Consumer<Vector3fc> output) {} // Exact body/drum extents are supplied per layer below.
    };
    @Override public void update(ItemStackRenderState output,ItemStack item,ItemModelResolver resolver,ItemDisplayContext context,ClientLevel level,ItemOwner owner,int seed) {
        boolean held=switch(context) {case FIRST_PERSON_LEFT_HAND,FIRST_PERSON_RIGHT_HAND,THIRD_PERSON_LEFT_HAND,THIRD_PERSON_RIGHT_HAND->true;default->false;};
        float degrees=held && level!=null && item.getOrDefault(TGContent.RELOAD_TICKS.get(),0)==0
                ? LauncherAnimation.drumDegrees(item.getOrDefault(TGContent.LAUNCHER_SHOT_TIME.get(),-1L),level.getGameTime(),Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)):0;
        var bodyState=new ItemStackRenderState();var drumState=new ItemStackRenderState();
        body.update(bodyState,item,resolver,ItemDisplayContext.NONE,level,owner,seed);
        drum.update(drumState,item,resolver,ItemDisplayContext.NONE,level,owner,seed);
        List<Vector3fc> extents=new ArrayList<>();
        bodyState.visitExtents(v->extents.add(new Vector3f(v).add(.5f,.5f,.5f)));
        drumState.visitExtents(v->extents.add(new Vector3f(v).add(0,.125f,0).rotateX((float)java.lang.Math.toRadians(degrees)).add(.5f,.375f,.5f)));
        var layer=output.newLayer();layer.setExtents(()->extents.toArray(Vector3fc[]::new));layer.setLocalTransform(transform);
        properties.applyToLayer(layer,context);layer.setupSpecialModel(RENDERER,new State(bodyState,drumState,degrees));
        output.appendModelIdentityElement(this);output.appendModelIdentityElement(degrees);output.appendModelIdentityElement(item.hasFoil());
        if(degrees!=0 || item.hasFoil())output.setAnimated();
    }
    public record Unbaked() implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> CODEC=MapCodec.unit(new Unbaked());
        @Override public MapCodec<Unbaked> type() { return CODEC; }
        @Override public void resolveDependencies(ResolvableModel.Resolver resolver) {
            for(String name:List.of("grenadelauncher","grenadelauncher_body","grenadelauncher_drum"))resolver.markDependency(TGContent.id("item/"+name));
        }
        @Override public ItemModel bake(BakingContext context,Matrix4fc transformation) {
            var baker=context.blockModelBaker();var root=baker.getModel(TGContent.id("item/grenadelauncher"));
            ItemModel body=new CuboidItemModelWrapper.Unbaked(TGContent.id("item/grenadelauncher_body"),Optional.empty(),List.of()).bake(context,new Matrix4f());
            ItemModel drum=new CuboidItemModelWrapper.Unbaked(TGContent.id("item/grenadelauncher_drum"),Optional.empty(),List.of()).bake(context,new Matrix4f());
            return new GrenadeLauncherModel(body,drum,ModelRenderProperties.fromResolvedModel(baker,root,root.getTopTextureSlots()),transformation);
        }
    }
}
