package techguns.modern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import techguns.core.GrinderRules;
import techguns.modern.*;
import techguns.modern.machine.grinder.*;
import techguns.modern.machine.workbench.OwnedWorkbenchBlock;

/** Source roller pivots, opposite three-turn rotations and descending ground-item transform. */
public final class GrinderRenderer implements BlockEntityRenderer<GrinderBlockEntity, GrinderRenderer.State> {
    private final ItemModelResolver items;
    public GrinderRenderer(BlockEntityRendererProvider.Context context) { items = context.itemModelResolver(); }
    public static final class State extends BlockEntityRenderState {
        ItemStackRenderState roller = new ItemStackRenderState(), item = new ItemStackRenderState();
        Direction facing = Direction.NORTH;
        float progress;
        boolean gun;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(GrinderBlockEntity machine, State state, float partial,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(machine, state, partial, cameraPosition, breakProgress);
        state.facing = machine.getBlockState().getValue(OwnedWorkbenchBlock.FACING); state.progress = machine.displayProgress(partial);
        var stack = machine.displayItem(); state.gun = stack.getItem() instanceof GunItem;
        var roller = GrinderContent.ITEM.toStack(); roller.set(DataComponents.ITEM_MODEL, TGContent.id("grinder_roll"));
        state.roller = new ItemStackRenderState(); state.item = new ItemStackRenderState();
        items.updateForTopItem(state.roller, roller, ItemDisplayContext.NONE, machine.getLevel(), null, 0);
        items.updateForTopItem(state.item, stack, ItemDisplayContext.GROUND, machine.getLevel(), null, (int)machine.getBlockPos().asLong());
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose(); poses.translate(.5, 0, .5); poses.mulPose(Axis.YP.rotationDegrees(90f * state.facing.get2DDataValue()));
        for (int side : new int[]{-1, 1}) {
            poses.pushPose(); poses.translate(0, 7.5 / 16, side * 1.5 / 16);
            poses.mulPose(Axis.XP.rotationDegrees(-side * GrinderRules.rollerAngle(state.progress)));
            state.roller.submit(poses, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); poses.popPose();
        }
        if (!state.item.isEmpty()) {
            poses.pushPose(); poses.translate(0, GrinderRules.itemHeight(state.progress, state.gun), 0);
            if (state.gun) { poses.scale(.5f, .5f, .5f); poses.mulPose(Axis.YP.rotationDegrees(90)); }
            state.item.submit(poses, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); poses.popPose();
        }
        poses.popPose();
    }
}
