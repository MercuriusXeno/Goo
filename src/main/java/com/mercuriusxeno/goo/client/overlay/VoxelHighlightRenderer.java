package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.LineContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Renders goo-colored translucent fill and wireframe edges tracing a block's
 * voxel outline shape. Stairs, slabs, fences, etc. highlight their actual
 * geometry instead of a flat face quad.
 */
final class VoxelHighlightRenderer {
    /** Face highlight alpha (translucent enough to see texture beneath). */
    private static final int FACE_ALPHA = 0x1A;

    /** Wireframe outline alpha for block face edges. */
    private static final int WIRE_ALPHA = 200;

    /** Offset from the block face to prevent z-fighting. */
    private static final double FACE_OFFSET = 0.005;

    private VoxelHighlightRenderer() {}

    /**
     * Renders goo-colored translucent fill and wireframe edges tracing the
     * block's voxel outline shape.
     *
     * @param poseStack the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera the render camera
     * @param pos the block position
     * @param face the block face direction
     * @param type the goo type
     */
    static void renderBlockFace(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, BlockPos pos, Direction face, GooType type) {
        Minecraft mc = Minecraft.getInstance();
        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        if (shape.isEmpty()) { return; }
        Vec3 offset = cameraOffset(pos, camera);
        int highlightRgb = GooColors.highlight(type);
        int edgeRgb = GooColors.edge(type);
        emitFillBoxes(poseStack, bufferSource, shape, offset.x, offset.y, offset.z, highlightRgb);
        emitWireframeEdges(poseStack, bufferSource, mc, shape, offset.x, offset.y, offset.z, edgeRgb);
    }

    /**
     * Renders goo-colored translucent fill and wireframe edges tracing the
     * block's full voxel shape. Used for chain marker highlighting where
     * no specific face is targeted.
     *
     * @param poseStack    the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera       the render camera
     * @param pos          the block position
     * @param type         the goo type
     */
    static void renderBlockShape(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, BlockPos pos, GooType type) {
        Minecraft mc = Minecraft.getInstance();
        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        if (shape.isEmpty()) { return; }
        Vec3 offset = cameraOffset(pos, camera);
        int highlightRgb = GooColors.highlight(type);
        int edgeRgb = GooColors.edge(type);
        emitFillBoxes(poseStack, bufferSource, shape, offset.x, offset.y, offset.z, highlightRgb);
        emitWireframeEdges(poseStack, bufferSource, mc, shape, offset.x, offset.y, offset.z, edgeRgb);
    }

    /**
     * Renders a full 1x1x1 cube highlight at the given position.
     * Used for water blocks whose VoxelShape is empty.
     *
     * @param poseStack    the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera       the render camera
     * @param pos          the block position
     * @param type         the goo type
     */
    static void renderFullCube(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, BlockPos pos, GooType type) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 offset = cameraOffset(pos, camera);
        int rgb = GooColors.highlight(type);
        int fillColor = colorWithAlpha(rgb, FACE_ALPHA);
        CuboidBounds box = new CuboidBounds(
                offsetMin(offset.x, 0), offsetMax(offset.x, 1),
                offsetMin(offset.z, 0), offsetMax(offset.z, 1),
                offsetMin(offset.y, 0), offsetMax(offset.y, 1));
        FlatQuadContext ctx = new FlatQuadContext(poseStack.last(),
                bufferSource.getBuffer(RenderTypes.debugQuads()));
        ctx.emitBox(fillColor, box);
        bufferSource.endLastBatch();

        int wireColor = colorWithAlpha(GooColors.edge(type), WIRE_ALPHA);
        float lineWidth = mc.getWindow().getAppropriateLineWidth();
        LineContext lineCtx = new LineContext(poseStack.last(),
                bufferSource.getBuffer(RenderTypes.lines()));
        emitCubeEdges(lineCtx, offset.x, offset.y, offset.z, wireColor, lineWidth);
        bufferSource.endLastBatch();
    }

    /** Emits the 12 edges of a unit cube at the given camera-relative offset.
     *
     * @param ctx       the line rendering context
     * @param ox        camera-relative X offset
     * @param oy        camera-relative Y offset
     * @param oz        camera-relative Z offset
     * @param color     the ARGB color
     * @param lineWidth the line width
     */
    private static void emitCubeEdges(LineContext ctx, double ox, double oy, double oz,
                                       int color, float lineWidth) {
        float x0 = (float) ox;
        float y0 = (float) oy;
        float z0 = (float) oz;
        float x1 = (float) (ox + 1);
        float y1 = (float) (oy + 1);
        float z1 = (float) (oz + 1);
        // Bottom face edges
        ctx.emitEdge(x0, y0, z0, x1, y0, z0, color, lineWidth);
        ctx.emitEdge(x1, y0, z0, x1, y0, z1, color, lineWidth);
        ctx.emitEdge(x1, y0, z1, x0, y0, z1, color, lineWidth);
        ctx.emitEdge(x0, y0, z1, x0, y0, z0, color, lineWidth);
        // Top face edges
        ctx.emitEdge(x0, y1, z0, x1, y1, z0, color, lineWidth);
        ctx.emitEdge(x1, y1, z0, x1, y1, z1, color, lineWidth);
        ctx.emitEdge(x1, y1, z1, x0, y1, z1, color, lineWidth);
        ctx.emitEdge(x0, y1, z1, x0, y1, z0, color, lineWidth);
        // Vertical edges
        ctx.emitEdge(x0, y0, z0, x0, y1, z0, color, lineWidth);
        ctx.emitEdge(x1, y0, z0, x1, y1, z0, color, lineWidth);
        ctx.emitEdge(x1, y0, z1, x1, y1, z1, color, lineWidth);
        ctx.emitEdge(x0, y0, z1, x0, y1, z1, color, lineWidth);
    }

    /**
     * Computes the camera-relative offset for a block position.
     *
     * @param pos    the block position
     * @param camera the render camera
     * @return the camera-relative offset vector
     */
    private static Vec3 cameraOffset(BlockPos pos, Camera camera) {
        return new Vec3(
                pos.getX() - camera.position().x,
                pos.getY() - camera.position().y,
                pos.getZ() - camera.position().z);
    }

    /**
     * Emits a single translucent fill at the shape's bounding AABB.
     * Iterating each sub-box (via {@code forAllBoxes}) on a composite
     * shape draws shared internal faces twice with translucent overlap,
     * which reads as visible "seams" between segments. The bounding
     * box gives a single unified fill; the wireframe pass below still
     * traces the actual outline so cutaways stay visible.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param shape        the block's voxel shape
     * @param ox           camera-relative X offset
     * @param oy           camera-relative Y offset
     * @param oz           camera-relative Z offset
     * @param rgb          the RGB color value
     */
    private static void emitFillBoxes(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            VoxelShape shape, double ox, double oy, double oz, int rgb) {
        int fillColor = colorWithAlpha(rgb, FACE_ALPHA);
        FlatQuadContext ctx = new FlatQuadContext(poseStack.last(),
            bufferSource.getBuffer(RenderTypes.debugQuads()));
        AABB bounds = shape.bounds();
        ctx.emitBox(fillColor, new CuboidBounds(
            offsetMin(ox, bounds.minX), offsetMax(ox, bounds.maxX),
            offsetMin(oz, bounds.minZ), offsetMax(oz, bounds.maxZ),
            offsetMin(oy, bounds.minY), offsetMax(oy, bounds.maxY)));
        bufferSource.endLastBatch();
    }

    /**
     * Creates an ARGB color from an RGB value and alpha channel.
     *
     * @param rgb   the RGB color
     * @param alpha the alpha value (0-255)
     * @return the ARGB color
     */
    private static int colorWithAlpha(int rgb, int alpha) {
        return ARGB.color(alpha, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
    }

    /**
     * Computes a camera-relative coordinate with inward face offset for the minimum bound.
     *
     * @param camOffset camera-relative offset for this axis
     * @param coord     shape-local coordinate
     * @return the offset float coordinate
     */
    private static float offsetMin(double camOffset, double coord) {
        return (float) (camOffset + coord - FACE_OFFSET);
    }

    /**
     * Computes a camera-relative coordinate with outward face offset for the maximum bound.
     *
     * @param camOffset camera-relative offset for this axis
     * @param coord     shape-local coordinate
     * @return the offset float coordinate
     */
    private static float offsetMax(double camOffset, double coord) {
        return (float) (camOffset + coord + FACE_OFFSET);
    }

    /**
     * Emits wireframe edges along the shape outline.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param mc           the Minecraft instance
     * @param shape        the block's voxel shape
     * @param ox           camera-relative X offset
     * @param oy           camera-relative Y offset
     * @param oz           camera-relative Z offset
     * @param rgb          the RGB color value
     */
    private static void emitWireframeEdges(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Minecraft mc, VoxelShape shape,
            double ox, double oy, double oz, int rgb) {
        int wireColor = colorWithAlpha(rgb, WIRE_ALPHA);
        float lineWidth = mc.getWindow().getAppropriateLineWidth();
        LineContext ctx = new LineContext(poseStack.last(), bufferSource.getBuffer(RenderTypes.lines()));
        shape.forAllEdges((x0, y0, z0, x1, y1, z1) ->
            ctx.emitEdge(
                (float) (ox + x0), (float) (oy + y0), (float) (oz + z0),
                (float) (ox + x1), (float) (oy + y1), (float) (oz + z1),
                wireColor, lineWidth));
        bufferSource.endLastBatch();
    }

}
