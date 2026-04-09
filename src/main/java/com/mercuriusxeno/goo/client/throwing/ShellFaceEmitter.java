package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Colored shell face emission for blob flight rendering.
 * Each axis has a dispatcher that picks CW/CCW winding by normal sign.
 */
final class ShellFaceEmitter {

    /** Normal direction for negative-facing surfaces. */
    private static final float NORMAL_NEG = -1f;

    private ShellFaceEmitter() {}

    /**
     * Emits six faces of an axis-aligned cube centered at the origin with explicit ARGB color.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param hw the half-width of the cube
     * @param uv the UV texture rectangle
     */
    static void emitColoredCubeFaces(PoseStack.Pose pose, VertexConsumer c, int light,
            int color, float hw, GooRenderUtil.UvRect uv) {
        shellFaceY(pose, c, light, color, -hw, hw,  hw, -hw, hw, uv,  1f);
        shellFaceY(pose, c, light, color, -hw, hw, -hw, -hw, hw, uv, NORMAL_NEG);
        shellFaceX(pose, c, light, color,  hw, -hw, hw, -hw, hw, uv,  1f);
        shellFaceX(pose, c, light, color, -hw, -hw, hw, -hw, hw, uv, NORMAL_NEG);
        shellFaceZ(pose, c, light, color, -hw, hw, -hw, hw,  hw, uv,  1f);
        shellFaceZ(pose, c, light, color, -hw, hw, -hw, hw, -hw, uv, NORMAL_NEG);
    }

    // ── Y-axis ─────────────────────────────────────────────────────────

    /**
     * Y-axis shell face with explicit ARGB color.
     * Winding direction is chosen based on the normal sign.
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param color the ARGB color int for tinting
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y  the fixed Y coordinate for this face
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param ny the Y-axis normal sign (+1 or -1)
     */
    private static void shellFaceY(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x0, float x1, float y, float z0, float z1, GooRenderUtil.UvRect uv, float ny) {
        if (ny > 0) {
            shellFaceYPositive(pose, c, light, color, x0, x1, y, z0, z1, uv, ny);
        } else {
            shellFaceYNegative(pose, c, light, color, x0, x1, y, z0, z1, uv, ny);
        }
    }

    /**
     * Positive-normal Y-axis shell face (top face, CCW winding from above).
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param color the ARGB color int for tinting
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y  the fixed Y coordinate for this face
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param ny the Y-axis normal sign (+1 or -1)
     */
    private static void shellFaceYPositive(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x0, float x1, float y, float z0, float z1, GooRenderUtil.UvRect uv, float ny) {
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z0, uv.u0(), uv.v0(), 0f, ny, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z1, uv.u0(), uv.v1(), 0f, ny, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z1, uv.u1(), uv.v1(), 0f, ny, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z0, uv.u1(), uv.v0(), 0f, ny, 0f);
    }

    /**
     * Negative-normal Y-axis shell face (bottom face, CW winding from above).
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param color the ARGB color int for tinting
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y  the fixed Y coordinate for this face
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param ny the Y-axis normal sign (+1 or -1)
     */
    private static void shellFaceYNegative(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x0, float x1, float y, float z0, float z1, GooRenderUtil.UvRect uv, float ny) {
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z0, uv.u1(), uv.v0(), 0f, ny, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z1, uv.u1(), uv.v1(), 0f, ny, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z1, uv.u0(), uv.v1(), 0f, ny, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z0, uv.u0(), uv.v0(), 0f, ny, 0f);
    }

    // ── X-axis ─────────────────────────────────────────────────────────

    /**
     * X-axis shell face with explicit ARGB color.
     * Winding direction is chosen based on the normal sign.
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param color the ARGB color int for tinting
     * @param x  the fixed X coordinate for this face
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param nx the X-axis normal sign (+1 or -1)
     */
    private static void shellFaceX(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x, float y0, float y1, float z0, float z1, GooRenderUtil.UvRect uv, float nx) {
        if (nx > 0) {
            shellFaceXPositive(pose, c, light, color, x, y0, y1, z0, z1, uv, nx);
        } else {
            shellFaceXNegative(pose, c, light, color, x, y0, y1, z0, z1, uv, nx);
        }
    }

    /**
     * Positive-normal X-axis shell face (east face).
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param color the ARGB color int for tinting
     * @param x  the fixed X coordinate for this face
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param nx the X-axis normal sign (+1 or -1)
     */
    private static void shellFaceXPositive(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x, float y0, float y1, float z0, float z1, GooRenderUtil.UvRect uv, float nx) {
        GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z1, uv.u1(), uv.v0(), nx, 0f, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z1, uv.u1(), uv.v1(), nx, 0f, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z0, uv.u0(), uv.v1(), nx, 0f, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z0, uv.u0(), uv.v0(), nx, 0f, 0f);
    }

    /**
     * Negative-normal X-axis shell face (west face).
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param color the ARGB color int for tinting
     * @param x  the fixed X coordinate for this face
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param nx the X-axis normal sign (+1 or -1)
     */
    private static void shellFaceXNegative(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x, float y0, float y1, float z0, float z1, GooRenderUtil.UvRect uv, float nx) {
        GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z0, uv.u1(), uv.v0(), nx, 0f, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z0, uv.u1(), uv.v1(), nx, 0f, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z1, uv.u0(), uv.v1(), nx, 0f, 0f);
        GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z1, uv.u0(), uv.v0(), nx, 0f, 0f);
    }

    // ── Z-axis ─────────────────────────────────────────────────────────

    /**
     * Z-axis shell face with explicit ARGB color.
     * Winding direction is chosen based on the normal sign.
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param color the ARGB color int for tinting
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z  the fixed Z coordinate for this face
     * @param uv the UV texture rectangle
     * @param nz the Z-axis normal sign (+1 or -1)
     */
    private static void shellFaceZ(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x0, float x1, float y0, float y1, float z, GooRenderUtil.UvRect uv, float nz) {
        if (nz > 0) {
            shellFaceZPositive(pose, c, light, color, x0, x1, y0, y1, z, uv, nz);
        } else {
            shellFaceZNegative(pose, c, light, color, x0, x1, y0, y1, z, uv, nz);
        }
    }

    /**
     * Positive-normal Z-axis shell face (south face).
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param color the ARGB color int for tinting
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z  the fixed Z coordinate for this face
     * @param uv the UV texture rectangle
     * @param nz the Z-axis normal sign (+1 or -1)
     */
    private static void shellFaceZPositive(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x0, float x1, float y0, float y1, float z, GooRenderUtil.UvRect uv, float nz) {
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y1, z, uv.u1(), uv.v0(), 0f, 0f, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y0, z, uv.u1(), uv.v1(), 0f, 0f, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y0, z, uv.u0(), uv.v1(), 0f, 0f, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y1, z, uv.u0(), uv.v0(), 0f, 0f, nz);
    }

    /**
     * Negative-normal Z-axis shell face (north face).
     * @param pose the current pose matrix entry
     * @param c    the vertex consumer for geometry emission
     * @param light the packed light level for shading
     * @param color the ARGB color int for tinting
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z  the fixed Z coordinate for this face
     * @param uv the UV texture rectangle
     * @param nz the Z-axis normal sign (+1 or -1)
     */
    private static void shellFaceZNegative(PoseStack.Pose pose, VertexConsumer c, int light, int color,
            float x0, float x1, float y0, float y1, float z, GooRenderUtil.UvRect uv, float nz) {
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y1, z, uv.u0(), uv.v0(), 0f, 0f, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, x1, y0, z, uv.u0(), uv.v1(), 0f, 0f, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y0, z, uv.u1(), uv.v1(), 0f, 0f, nz);
        GooRenderUtil.vertexColored(pose, c, light, color, x0, y1, z, uv.u1(), uv.v0(), 0f, 0f, nz);
    }
}
