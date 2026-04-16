package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.ReactorBlock;
import com.mercuriusxeno.goo.block.ReactorBlockEntity;
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

/**
 * Renders the output canister floating in the reactor's front hollow.
 * The canister is rendered as a scaled-down item model facing outward.
 */
public class ReactorBlockEntityRenderer
        implements BlockEntityRenderer<ReactorBlockEntity, ReactorRenderState> {

    /** Block center on X/Z axes. */
    private static final float BLOCK_CENTER = 0.5f;

    /** Hollow center Y: midpoint of [1, 15] in model space. */
    private static final float HOLLOW_Y = 8f / 16f;

    /** Z offset from block center into the hollow (center of z=0..6). */
    private static final float HOLLOW_Z_OFFSET = 5f / 16f;

    /** Canister scale in the hollow. */
    private static final float CANISTER_SCALE = 0.375f;

    private final ItemModelResolver itemModelResolver;
    private final ItemStackRenderState itemRenderState = new ItemStackRenderState();

    /**
     * Stores the item model resolver for rendering the output canister.
     *
     * @param context the renderer provider context
     */
    public ReactorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public ReactorRenderState createRenderState() {
        return new ReactorRenderState();
    }

    /**
     * Snapshots the output canister and facing from the block entity.
     *
     * @param be            the block entity instance
     * @param state         the render state to populate
     * @param partialTick   the partial tick
     * @param cameraPos     the camera world position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(ReactorBlockEntity be, ReactorRenderState state,
            float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.facing = be.getBlockState().getValue(ReactorBlock.FACING);
        state.outputCanister = be.getOutputCanister();
    }

    /**
     * Submits the output canister model in the hollow if one is present.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param cameraState   the camera render state
     */
    @Override
    public void submit(ReactorRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (state.outputCanister.isEmpty()) { return; }
        if (!resolveItemModel(state)) { return; }
        poseStack.pushPose();
        translateToHollow(poseStack, state.facing);
        poseStack.scale(CANISTER_SCALE, CANISTER_SCALE, CANISTER_SCALE);
        itemRenderState.submit(poseStack, nodeCollector,
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /**
     * Resolves the output canister item into the reusable render state.
     *
     * @param state the render state
     * @return true if a model was resolved
     */
    private boolean resolveItemModel(ReactorRenderState state) {
        itemModelResolver.updateForTopItem(
                itemRenderState, state.outputCanister,
                ItemDisplayContext.FIXED, null, null, 0);
        return !itemRenderState.isEmpty();
    }

    /**
     * Translates to the hollow center by rotating around the block center.
     *
     * @param poseStack the pose stack
     * @param facing    the block facing direction
     */
    private static void translateToHollow(PoseStack poseStack, Direction facing) {
        poseStack.translate(BLOCK_CENTER, HOLLOW_Y, BLOCK_CENTER);
        poseStack.mulPose(Axis.YP.rotationDegrees(facing.toYRot()));
        poseStack.translate(0f, 0f, HOLLOW_Z_OFFSET);
    }
}
