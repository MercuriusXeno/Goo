package com.mercuriusxeno.goo.client.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Vertex context for line-segment rendering (wireframes, arcs, edges).
 * Vertices carry position + color + normal + lineWidth. No UV, overlay, or light.
 *
 * @param pose the current pose matrix entry
 * @param c    the vertex consumer for line geometry
 */
public record LineCtx(PoseStack.Pose pose, VertexConsumer c) {

    /**
     * Emits a single line segment (two vertices with direction-derived normal).
     *
     * @param ax        start X
     * @param ay        start Y
     * @param az        start Z
     * @param bx        end X
     * @param by        end Y
     * @param bz        end Z
     * @param color     the ARGB color
     * @param lineWidth the line width in pixels
     */
    public void emitEdge(float ax, float ay, float az,
                         float bx, float by, float bz,
                         int color, float lineWidth) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0) {
            dx /= len;
            dy /= len;
            dz /= len;
        }
        c.addVertex(pose, ax, ay, az)
            .setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
        c.addVertex(pose, bx, by, bz)
            .setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(lineWidth);
    }

    /**
     * Emits 12 edges of an axis-aligned cuboid wireframe.
     *
     * @param box       the cuboid bounds
     * @param color     the ARGB color
     * @param lineWidth the line width in pixels
     */
    public void emitWireframe(CuboidBounds box, int color, float lineWidth) {
        float x0 = box.x0();
        float x1 = box.x1();
        float y0 = box.yBot();
        float y1 = box.yTop();
        float z0 = box.z0();
        float z1 = box.z1();
        emitHorizontalRing(x0, x1, y0, z0, z1, color, lineWidth);
        emitHorizontalRing(x0, x1, y1, z0, z1, color, lineWidth);
        emitVerticalPillars(x0, x1, y0, y1, z0, z1, color, lineWidth);
    }

    /** Emits four edges forming a horizontal ring at the given Y level. */
    private void emitHorizontalRing(float x0, float x1, float y,
            float z0, float z1, int color, float lineWidth) {
        emitEdge(x0, y, z0, x1, y, z0, color, lineWidth);
        emitEdge(x1, y, z0, x1, y, z1, color, lineWidth);
        emitEdge(x1, y, z1, x0, y, z1, color, lineWidth);
        emitEdge(x0, y, z1, x0, y, z0, color, lineWidth);
    }

    /** Emits four vertical pillar edges connecting bottom and top Y levels. */
    private void emitVerticalPillars(float x0, float x1, float y0, float y1,
            float z0, float z1, int color, float lineWidth) {
        emitEdge(x0, y0, z0, x0, y1, z0, color, lineWidth);
        emitEdge(x1, y0, z0, x1, y1, z0, color, lineWidth);
        emitEdge(x1, y0, z1, x1, y1, z1, color, lineWidth);
        emitEdge(x0, y0, z1, x0, y1, z1, color, lineWidth);
    }
}
