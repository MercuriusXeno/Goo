package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.ber.CuboidBounds;
import com.mercuriusxeno.goo.client.ber.LineContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Low-level outline and wireframe drawing routines for slot outline rendering.
 * Extracted from SlotOutlineRenderer to keep method counts under threshold.
 */
final class SlotOutlineDrawing {

    /** Translucent green color for placement preview wireframe. */
    private static final int PREVIEW_COLOR = ARGB.color(180, 100, 255, 100);

    /** Opaque black outline color for high-contrast secondary outline. */
    private static final int HC_SECONDARY_COLOR = -16_777_216;

    /** Line width for high-contrast secondary outline. */
    private static final float HC_SECONDARY_WIDTH = 7.0F;

    /** High-contrast primary outline color. */
    private static final int HC_PRIMARY_COLOR = -11_010_079;

    /** Black alpha for standard outline. */
    private static final int OUTLINE_BLACK_ALPHA = 102;

    private SlotOutlineDrawing() { }

    /**
     * Renders a custom outline shape using the same style as vanilla block outlines.
     * If a preview AABB is provided, also draws a wireframe cuboid for placement preview.
     *
     * @return true to suppress vanilla outline rendering
     *
     * @param renderState the block outline render state
     * @param bufferSource the buffer source for rendering
     * @param poseStack the pose stack for rendering
     * @param translucent whether the current pass is translucent
     * @param levelRenderState the level render state
     * @param shape the voxel shape to render
     * @param preview the placement preview bounds, or null
     */
    static boolean renderOutline(BlockOutlineRenderState renderState,
            MultiBufferSource.BufferSource bufferSource, PoseStack poseStack,
            boolean translucent, LevelRenderState levelRenderState,
            VoxelShape shape, @Nullable AABB preview) {
        if (renderState.isTranslucent() != translucent) { return true; }
        renderOutlineContent(renderState, bufferSource, poseStack, levelRenderState, shape, preview);
        return true;
    }

    /**
     * Draws selection outline and optional preview, then flushes the buffer.
     *
     * @param renderState the block outline render state
     * @param bufferSource the buffer source for rendering
     * @param poseStack the pose stack for rendering
     * @param levelRenderState the level render state
     * @param shape the voxel shape to render
     * @param preview the placement preview bounds, or null
     */
    private static void renderOutlineContent(BlockOutlineRenderState renderState,
            MultiBufferSource.BufferSource bufferSource, PoseStack poseStack,
            LevelRenderState levelRenderState, VoxelShape shape, @Nullable AABB preview) {
        Vec3 camPos = levelRenderState.cameraRenderState.pos;
        BlockPos pos = renderState.pos();
        renderSelectionOutline(renderState, bufferSource, poseStack, shape, pos, camPos);
        renderOptionalPreview(poseStack, bufferSource, preview, pos, camPos);
        bufferSource.endLastBatch();
    }

    /**
     * Renders the placement preview wireframe if a preview AABB is present.
     *
     * @param poseStack the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param preview the placement preview bounds, or null
     * @param pos the block position
     * @param camPos the camera world position
     */
    static void renderOptionalPreview(PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            @Nullable AABB preview, BlockPos pos, Vec3 camPos) {
        if (preview != null) {
            renderPreviewWireframe(poseStack, bufferSource, preview, pos, camPos);
        }
    }

    /**
     * Renders the standard selection outline (with high-contrast support).
     *
     * @param renderState the block outline render state
     * @param bufferSource the buffer source for rendering
     * @param poseStack the pose stack for rendering
     * @param shape the voxel shape to render
     * @param pos the block position
     * @param camPos the camera world position
     */
    static void renderSelectionOutline(BlockOutlineRenderState renderState,
            MultiBufferSource.BufferSource bufferSource, PoseStack poseStack,
            VoxelShape shape, BlockPos pos, Vec3 camPos) {
        if (renderState.highContrast()) {
            renderShapeOutline(poseStack, bufferSource, RenderTypes.secondaryBlockOutline(),
                    shape, pos, camPos, HC_SECONDARY_COLOR, HC_SECONDARY_WIDTH);
        }
        int color = renderState.highContrast() ? HC_PRIMARY_COLOR : ARGB.black(OUTLINE_BLACK_ALPHA);
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        renderShapeOutline(poseStack, bufferSource, RenderTypes.lines(), shape, pos, camPos, color, lineWidth);
    }

    /**
     * Renders a wireframe cuboid offset to camera-relative coordinates.
     *
     * @param poseStack the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param bounds the axis-aligned bounding box
     * @param pos the block position
     * @param camPos the camera world position
     * @param color the ARGB color value
     */
    private static void renderCameraRelativeWireframe(PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            AABB bounds, BlockPos pos, Vec3 camPos, int color) {
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        LineContext ctx = new LineContext(poseStack.last(), bufferSource.getBuffer(RenderTypes.lines()));
        AABB offset = bounds.move(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
        ctx.emitWireframe(new CuboidBounds(
            (float) offset.minX, (float) offset.maxX,
            (float) offset.minZ, (float) offset.maxZ,
            (float) offset.minY, (float) offset.maxY), color, lineWidth);
    }

    /**
     * Renders a wireframe placement preview for an empty slot.
     *
     * @param poseStack the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param bounds the axis-aligned bounding box
     * @param pos the block position
     * @param camPos the camera world position
     */
    static void renderPreviewWireframe(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            AABB bounds, BlockPos pos, Vec3 camPos) {
        renderCameraRelativeWireframe(poseStack, bufferSource, bounds, pos, camPos, PREVIEW_COLOR);
    }

    /**
     * Delegates to ShapeRenderer with camera-relative coordinates.
     *
     * @param poseStack the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param renderType the render type for lines
     * @param shape the voxel shape to render
     * @param pos the block position
     * @param camPos the camera world position
     * @param color the ARGB color value
     * @param lineWidth the line width in pixels
     */
    static void renderShapeOutline(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            RenderType renderType, VoxelShape shape, BlockPos pos, Vec3 camPos, int color, float lineWidth) {
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        ShapeRenderer.renderShape(poseStack, consumer, shape,
                pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z, color, lineWidth);
    }

}
