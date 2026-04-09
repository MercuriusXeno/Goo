package com.mercuriusxeno.goo.client.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Reusable wireframe drawing primitives extracted from SlotOutlineRenderer.
 * Shared by slot outlines, gasket overlays, and target highlighters.
 */
final class WireframeRenderer {

    private WireframeRenderer() {}

    /**
     * Draws 12 edges of an axis-aligned cuboid using manual line rendering.
     * No VoxelShape involved: coordinates are camera-relative world space.
     *
     * @param poseStack the pose stack for rendering
     * @param consumer the vertex consumer
     * @param x0 the minimum X bound
     * @param y0 the minimum Y bound
     * @param z0 the minimum Z bound
     * @param x1 the maximum X bound
     * @param y1 the maximum Y bound
     * @param z1 the maximum Z bound
     * @param color the ARGB color value
     * @param lineWidth the line width in pixels
     */
    static void renderWireframeCuboid(
            PoseStack poseStack, VertexConsumer consumer,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            int color, float lineWidth) {
        // Bottom face
        emitEdge(poseStack, consumer, x0, y0, z0, x1, y0, z0, color, lineWidth);
        emitEdge(poseStack, consumer, x1, y0, z0, x1, y0, z1, color, lineWidth);
        emitEdge(poseStack, consumer, x1, y0, z1, x0, y0, z1, color, lineWidth);
        emitEdge(poseStack, consumer, x0, y0, z1, x0, y0, z0, color, lineWidth);
        // Top face
        emitEdge(poseStack, consumer, x0, y1, z0, x1, y1, z0, color, lineWidth);
        emitEdge(poseStack, consumer, x1, y1, z0, x1, y1, z1, color, lineWidth);
        emitEdge(poseStack, consumer, x1, y1, z1, x0, y1, z1, color, lineWidth);
        emitEdge(poseStack, consumer, x0, y1, z1, x0, y1, z0, color, lineWidth);
        // Vertical pillars
        emitEdge(poseStack, consumer, x0, y0, z0, x0, y1, z0, color, lineWidth);
        emitEdge(poseStack, consumer, x1, y0, z0, x1, y1, z0, color, lineWidth);
        emitEdge(poseStack, consumer, x1, y0, z1, x1, y1, z1, color, lineWidth);
        emitEdge(poseStack, consumer, x0, y0, z1, x0, y1, z1, color, lineWidth);
    }

    /**
     * Emits a single line segment (two vertices with direction-based normal and line width).
     *
     * @param poseStack the pose stack for rendering
     * @param consumer the vertex consumer
     * @param ax the start X coordinate
     * @param ay the start Y coordinate
     * @param az the start Z coordinate
     * @param bx the end X coordinate
     * @param by the end Y coordinate
     * @param bz the end Z coordinate
     * @param color the ARGB color value
     * @param lineWidth the line width in pixels
     */
    static void emitEdge(
            PoseStack poseStack, VertexConsumer consumer,
            double ax, double ay, double az,
            double bx, double by, double bz,
            int color, float lineWidth) {
        float dx = (float) (bx - ax);
        float dy = (float) (by - ay);
        float dz = (float) (bz - az);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0) {
            dx /= len;
            dy /= len;
            dz /= len;
        }

        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, (float) ax, (float) ay, (float) az)
                .setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
        consumer.addVertex(pose, (float) bx, (float) by, (float) bz)
                .setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
    }
}
