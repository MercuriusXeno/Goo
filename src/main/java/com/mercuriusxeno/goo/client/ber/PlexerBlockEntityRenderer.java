package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.PlexerBlock;
import com.mercuriusxeno.goo.block.PlexerBlockEntity;
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
 * Renders the reconstitution target item floating in the plexer cutaway.
 * The item is rendered as a scaled-down model facing outward from the opening.
 */
public class PlexerBlockEntityRenderer
        implements BlockEntityRenderer<PlexerBlockEntity, PlexerRenderState> {

    /** Block center on X/Z axes (rotation pivot). */
    private static final float BLOCK_CENTER = 0.5f;

    /** Cutaway center Y in model space: midpoint of [8,13]. */
    private static final float CUTAWAY_Y = 10.5f / 16f;

    /** Z offset from block center to cutaway center (13/16 - 8/16). */
    private static final float CUTAWAY_Z_OFFSET = 5f / 16f;

    /** Item scale - roughly 6px in a 16px space. */
    private static final float ITEM_SCALE = 0.375f;

    private final ItemModelResolver itemModelResolver;
    private final ItemStackRenderState itemRenderState = new ItemStackRenderState();

    /**
     * Stores the item model resolver for rendering target items.
     *
     * @param context the renderer provider context
     */
    public PlexerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public PlexerRenderState createRenderState() {
        return new PlexerRenderState();
    }

    /**
     * Snapshots the target item and facing from the block entity.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param partialTick the partial tick for interpolation
     * @param cameraPos the camera world position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(PlexerBlockEntity be, PlexerRenderState state,
            float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.targetItem = be.getTargetItem();
        state.facing = be.getBlockState().getValue(PlexerBlock.FACING);
    }

    /**
     * Submits the target item model in the cutaway if one is set.
     *
     * @param state the block state
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param cameraState the camera render state
     */
    @Override
    public void submit(PlexerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (state.targetItem.isEmpty()) { return; }
        if (!resolveItemModel(state)) { return; }
        poseStack.pushPose();
        translateToCutaway(poseStack, state.facing);
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        itemRenderState.submit(poseStack, nodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /**
     * Resolves the target item into the reusable render state.
     *
     * @param state the block state
     * @return true if the condition is met
     */
    private boolean resolveItemModel(PlexerRenderState state) {
        itemModelResolver.updateForTopItem(
            itemRenderState, state.targetItem, ItemDisplayContext.FIXED, null, null, 0);
        return !itemRenderState.isEmpty();
    }

    /**
     * Translates to the cutaway center by rotating around the block center.
     * Moves to pivot, rotates by facing, then offsets into the cutaway.
     *
     * @param poseStack the pose stack for rendering
     * @param facing the block facing direction
     */
    private static void translateToCutaway(PoseStack poseStack, Direction facing) {
        poseStack.translate(BLOCK_CENTER, CUTAWAY_Y, BLOCK_CENTER);
        float degrees = facing.toYRot();
        poseStack.mulPose(Axis.YP.rotationDegrees(degrees));
        poseStack.translate(0f, 0f, CUTAWAY_Z_OFFSET);
    }
}
