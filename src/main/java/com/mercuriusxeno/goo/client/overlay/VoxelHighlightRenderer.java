package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.GooType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Renders goo-colored translucent fill and wireframe edges tracing a block's
 * voxel outline shape. Stairs, slabs, fences, etc. highlight their actual
 * geometry instead of a flat face quad.
 */
final class VoxelHighlightRenderer {
    /** Face highlight alpha (translucent enough to see texture beneath). */
    private static final int FACE_ALPHA = 80;

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
        int rgb = type.getColor();
        emitFillBoxes(poseStack, bufferSource, shape, offset.x, offset.y, offset.z, rgb);
        emitWireframeEdges(poseStack, bufferSource, mc, shape, offset.x, offset.y, offset.z, rgb);
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
     * Emits translucent fill quads for every AABB in the voxel shape.
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
        VertexConsumer quad = bufferSource.getBuffer(RenderTypes.debugQuads());
        PoseStack.Pose pose = poseStack.last();
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            emitOffsetBox(pose, quad, ox, oy, oz, x0, y0, z0, x1, y1, z1, fillColor);
        });
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
     * Computes camera-relative face-offset coords and emits a box.
     *
     * @param pose     the pose matrix entry
     * @param consumer the vertex consumer
     * @param ox       camera-relative X offset
     * @param oy       camera-relative Y offset
     * @param oz       camera-relative Z offset
     * @param x0       shape-local min X
     * @param y0       shape-local min Y
     * @param z0       shape-local min Z
     * @param x1       shape-local max X
     * @param y1       shape-local max Y
     * @param z1       shape-local max Z
     * @param color    the ARGB color
     */
    private static void emitOffsetBox(
            PoseStack.Pose pose, VertexConsumer consumer,
            double ox, double oy, double oz,
            double x0, double y0, double z0,
            double x1, double y1, double z1, int color) {
        emitBox(pose, consumer,
                offsetMin(ox, x0), offsetMin(oy, y0), offsetMin(oz, z0),
                offsetMax(ox, x1), offsetMax(oy, y1), offsetMax(oz, z1),
                color);
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
        VertexConsumer line = bufferSource.getBuffer(RenderTypes.lines());
        shape.forAllEdges((x0, y0, z0, x1, y1, z1) ->
            emitEdgeOffset(poseStack, line, ox, oy, oz,
                    x0, y0, z0, x1, y1, z1, wireColor, lineWidth));
        bufferSource.endLastBatch();
    }

    /**
     * Emits a single wireframe edge with camera-relative offsets applied.
     *
     * @param poseStack the pose stack
     * @param line      the vertex consumer
     * @param ox        camera-relative X offset
     * @param oy        camera-relative Y offset
     * @param oz        camera-relative Z offset
     * @param x0        edge start X
     * @param y0        edge start Y
     * @param z0        edge start Z
     * @param x1        edge end X
     * @param y1        edge end Y
     * @param z1        edge end Z
     * @param color     the ARGB color
     * @param width     the line width
     */
    private static void emitEdgeOffset(
            PoseStack poseStack, VertexConsumer line,
            double ox, double oy, double oz,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            int color, float width) {
        WireframeRenderer.emitEdge(poseStack, line,
                ox + x0, oy + y0, oz + z0,
                ox + x1, oy + y1, oz + z1,
                color, width);
    }

    /**
     * Emits six quads (one per face) for an axis-aligned box.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param x0 the minimum X bound
     * @param y0 the minimum Y bound
     * @param z0 the minimum Z bound
     * @param x1 the maximum X bound
     * @param y1 the maximum Y bound
     * @param z1 the maximum Z bound
     * @param color the ARGB color value
     */
    private static void emitBox(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        emitFaceUp(pose, c, x0, y1, z0, x1, z1, color);
        emitFaceDown(pose, c, x0, y0, z0, x1, z1, color);
        emitFaceNorth(pose, c, x0, y0, z0, x1, y1, color);
        emitFaceSouth(pose, c, x0, y0, z1, x1, y1, color);
        emitFaceWest(pose, c, x0, y0, z0, y1, z1, color);
        emitFaceEast(pose, c, x1, y0, z0, y1, z1, color);
    }

    /**
     * Emits the +Y face quad.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param x0    min X
     * @param y1    max Y
     * @param z0    min Z
     * @param x1    max X
     * @param z1    max Z
     * @param color the ARGB color
     */
    private static void emitFaceUp(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y1, float z0, float x1, float z1, int color) {
        c.addVertex(pose, x0, y1, z0).setColor(color);
        c.addVertex(pose, x0, y1, z1).setColor(color);
        c.addVertex(pose, x1, y1, z1).setColor(color);
        c.addVertex(pose, x1, y1, z0).setColor(color);
    }

    /**
     * Emits the -Y face quad.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param x0    min X
     * @param y0    min Y
     * @param z0    min Z
     * @param x1    max X
     * @param z1    max Z
     * @param color the ARGB color
     */
    private static void emitFaceDown(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z0, float x1, float z1, int color) {
        c.addVertex(pose, x0, y0, z1).setColor(color);
        c.addVertex(pose, x0, y0, z0).setColor(color);
        c.addVertex(pose, x1, y0, z0).setColor(color);
        c.addVertex(pose, x1, y0, z1).setColor(color);
    }

    /**
     * Emits the -Z face quad.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param x0    min X
     * @param y0    min Y
     * @param z0    min Z (fixed Z)
     * @param x1    max X
     * @param y1    max Y
     * @param color the ARGB color
     */
    private static void emitFaceNorth(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z0, float x1, float y1, int color) {
        c.addVertex(pose, x0, y0, z0).setColor(color);
        c.addVertex(pose, x0, y1, z0).setColor(color);
        c.addVertex(pose, x1, y1, z0).setColor(color);
        c.addVertex(pose, x1, y0, z0).setColor(color);
    }

    /**
     * Emits the +Z face quad.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param x0    min X
     * @param y0    min Y
     * @param z1    max Z (fixed Z)
     * @param x1    max X
     * @param y1    max Y
     * @param color the ARGB color
     */
    private static void emitFaceSouth(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z1, float x1, float y1, int color) {
        c.addVertex(pose, x1, y0, z1).setColor(color);
        c.addVertex(pose, x1, y1, z1).setColor(color);
        c.addVertex(pose, x0, y1, z1).setColor(color);
        c.addVertex(pose, x0, y0, z1).setColor(color);
    }

    /**
     * Emits the -X face quad.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param x0    min X (fixed X)
     * @param y0    min Y
     * @param z0    min Z
     * @param y1    max Y
     * @param z1    max Z
     * @param color the ARGB color
     */
    private static void emitFaceWest(PoseStack.Pose pose, VertexConsumer c,
            float x0, float y0, float z0, float y1, float z1, int color) {
        c.addVertex(pose, x0, y0, z1).setColor(color);
        c.addVertex(pose, x0, y1, z1).setColor(color);
        c.addVertex(pose, x0, y1, z0).setColor(color);
        c.addVertex(pose, x0, y0, z0).setColor(color);
    }

    /**
     * Emits the +X face quad.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param x1    max X (fixed X)
     * @param y0    min Y
     * @param z0    min Z
     * @param y1    max Y
     * @param z1    max Z
     * @param color the ARGB color
     */
    private static void emitFaceEast(PoseStack.Pose pose, VertexConsumer c,
            float x1, float y0, float z0, float y1, float z1, int color) {
        c.addVertex(pose, x1, y0, z0).setColor(color);
        c.addVertex(pose, x1, y1, z0).setColor(color);
        c.addVertex(pose, x1, y1, z1).setColor(color);
        c.addVertex(pose, x1, y0, z1).setColor(color);
    }
}
