package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Colored-cube geometry helpers for {@link ChainMarkerBER}.
 * Extracted to keep the parent BER under the PMD method-count threshold.
 */
final class ChainMarkerGeometry {

    /** Negative face direction for normal inversion. */
    private static final float NEG_FACE = -1f;
    /** Vertex stride: 5 floats per vertex (x, y, z, u, v). */
    private static final int VERTEX_STRIDE = 5;
    /** Offset to normal data after all 4 vertices. */
    private static final int NORMAL_OFFSET = 20;
    /** Quad vertex index for the second vertex. */
    private static final int QUAD_V2 = 2;
    /** Quad vertex index for the third vertex. */
    private static final int QUAD_V3 = 3;
    /** Quad vertex index for the fourth vertex. */
    private static final int QUAD_V4 = 4;

    private ChainMarkerGeometry() {
    }

    /**
     * Renders all 6 faces of an axis-aligned cube centered at the origin.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param min the minimum bound on all axes
     * @param max the maximum bound on all axes
     * @param uv the UV texture rectangle
     */
    static void renderCube(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float min, float max,
            GooRenderUtil.UvRect uv) {
        renderCubeYFaces(pose, c, light, color, min, max, uv);
        renderCubeXFaces(pose, c, light, color, min, max, uv);
        renderCubeZFaces(pose, c, light, color, min, max, uv);
    }

    /**
     * Emits the top (+Y) and bottom (-Y) faces of a colored cube.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param min the minimum bound
     * @param max the maximum bound
     * @param uv the UV texture rectangle
     */
    private static void renderCubeYFaces(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float min, float max, GooRenderUtil.UvRect uv) {
        float u0 = uv.u0();
        float u1 = uv.u1();
        float v0 = uv.v0();
        float v1 = uv.v1();
        coloredQuad(pose, c, light, color, min,max,min,u0,v0, min,max,max,u0,v1, max,max,max,u1,v1, max,max,min,u1,v0, 0f,1f,0f);
        coloredQuad(pose, c, light, color, max,min,min,u1,v0, max,min,max,u1,v1, min,min,max,u0,v1, min,min,min,u0,v0, 0f,NEG_FACE,0f);
    }

    /**
     * Emits the east (+X) and west (-X) faces of a colored cube.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param min the minimum bound
     * @param max the maximum bound
     * @param uv the UV texture rectangle
     */
    private static void renderCubeXFaces(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float min, float max, GooRenderUtil.UvRect uv) {
        float u0 = uv.u0();
        float u1 = uv.u1();
        float v0 = uv.v0();
        float v1 = uv.v1();
        coloredQuad(pose, c, light, color, max,max,max,u1,v0, max,min,max,u1,v1, max,min,min,u0,v1, max,max,min,u0,v0, 1f,0f,0f);
        coloredQuad(pose, c, light, color, min,max,min,u1,v0, min,min,min,u1,v1, min,min,max,u0,v1, min,max,max,u0,v0, NEG_FACE,0f,0f);
    }

    /**
     * Emits the south (+Z) and north (-Z) faces of a colored cube.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param min the minimum bound
     * @param max the maximum bound
     * @param uv the UV texture rectangle
     */
    private static void renderCubeZFaces(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float min, float max, GooRenderUtil.UvRect uv) {
        float u0 = uv.u0();
        float u1 = uv.u1();
        float v0 = uv.v0();
        float v1 = uv.v1();
        coloredQuad(pose, c, light, color, min,max,max,u1,v0, min,min,max,u1,v1, max,min,max,u0,v1, max,max,max,u0,v0, 0f,0f,1f);
        coloredQuad(pose, c, light, color, max,max,min,u0,v0, max,min,min,u0,v1, min,min,min,u1,v1, min,max,min,u1,v0, 0f,0f,NEG_FACE);
    }

    /**
     * Emits a colored quad with 4 explicit vertex positions, UVs, and a normal.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param v vertex data: 4 groups of (x, y, z, u, v), plus nx, ny, nz appended
     */
    private static void coloredQuad(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float... v) {
        emitQuadVertex(pose, c, light, color, v, 0);
        emitQuadVertex(pose, c, light, color, v, 1);
        emitQuadVertex(pose, c, light, color, v, QUAD_V2);
        emitQuadVertex(pose, c, light, color, v, QUAD_V3);
    }

    /**
     * Emits one vertex from packed quad data.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param v the packed vertex and normal data
     * @param idx the vertex index (0-3)
     */
    private static void emitQuadVertex(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float[] v, int idx) {
        int off = idx * VERTEX_STRIDE;
        GooRenderUtil.vertexColored(pose, c, light, color,
                v[off], v[off + 1], v[off + QUAD_V2], v[off + QUAD_V3], v[off + QUAD_V4],
                v[NORMAL_OFFSET], v[NORMAL_OFFSET + 1], v[NORMAL_OFFSET + QUAD_V2]);
    }
}
