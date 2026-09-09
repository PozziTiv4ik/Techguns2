package techguns.modern.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import techguns.modern.machine.ProcessingMachineBlock;
import techguns.modern.machine.charging.ChargingStationBlockEntity;

/** Original RenderChargingStation ground-item transform, without an extra rotation animation. */
public final class ChargingStationRenderer implements BlockEntityRenderer<ChargingStationBlockEntity, ChargingStationRenderer.State> {
    private final ItemModelResolver items;
    public ChargingStationRenderer(BlockEntityRendererProvider.Context context) { items = context.itemModelResolver(); }
    public static final class State extends BlockEntityRenderState {
        ItemStackRenderState item = new ItemStackRenderState();
        Direction facing = Direction.NORTH;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(ChargingStationBlockEntity machine, State state, float partialTicks,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(machine, state, partialTicks, cameraPosition, breakProgress);
        state.facing = machine.getBlockState().getValue(ProcessingMachineBlock.FACING);
        state.item = new ItemStackRenderState();
        items.updateForTopItem(state.item, machine.displayItem(), ItemDisplayContext.GROUND, machine.getLevel(), null, (int) machine.getBlockPos().asLong());
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item.isEmpty()) return;
        poses.pushPose();
        poses.translate(.5, .4, .5);
        poses.mulPose(Axis.YP.rotationDegrees(-90f * state.facing.get2DDataValue()));
        state.item.submit(poses, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poses.popPose();
    }
}
