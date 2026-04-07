package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Shared vertex emitters for canister rendering. Used by both
 * HubBlockEntityRenderer and CanisterBlockEntityRenderer.
 */
public final class CanisterGeometry {
    // -- Gasket UV regions: choral_gasket.png, 16x16 --

    /** Gasket cap U/V extent: 4px / 16px. */
    private static final float GC_UV = 0.25f;

    /** Gasket bottom V end: 8px / 16px. */
    private static final float GC_BOTTOM_V = 0.5f;

    /** Normal direction for negative-facing surfaces. */
    private static final int NORMAL_NEG = -1;

    private CanisterGeometry() {}

    /**
     * Gasket top face: UV region [0,0]-[0.25,0.25].
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x0 the minimum X bound
     * @param y the Y coordinate
     * @param z0 the minimum Z bound
     * @param x1 the maximum X bound
     * @param z1 the maximum Z bound
     */
    public static void gasketTop(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float y, float z0, float x1, float z1) {
        GooRenderUtil.vertex(pose, c, light, x0, y, z0, 0, 0, 0, 1, 0);
        GooRenderUtil.vertex(pose, c, light, x0, y, z1, 0, GC_UV, 0, 1, 0);
        GooRenderUtil.vertex(pose, c, light, x1, y, z1, GC_UV, GC_UV, 0, 1, 0);
        GooRenderUtil.vertex(pose, c, light, x1, y, z0, GC_UV, 0, 0, 1, 0);
    }

    /**
     * Gasket bottom face: UV region [0,0.25]-[0.25,0.5].
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x0 the minimum X bound
     * @param y the Y coordinate
     * @param z0 the minimum Z bound
     * @param x1 the maximum X bound
     * @param z1 the maximum Z bound
     */
    public static void gasketBottom(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float y, float z0, float x1, float z1) {
        GooRenderUtil.vertex(pose, c, light, x1, y, z0, GC_UV, GC_UV, 0, NORMAL_NEG, 0);
        GooRenderUtil.vertex(pose, c, light, x1, y, z1, GC_UV, GC_BOTTOM_V, 0, NORMAL_NEG, 0);
        GooRenderUtil.vertex(pose, c, light, x0, y, z1, 0, GC_BOTTOM_V, 0, NORMAL_NEG, 0);
        GooRenderUtil.vertex(pose, c, light, x0, y, z0, 0, GC_UV, 0, NORMAL_NEG, 0);
    }

    /**
     * North face (-Z) with explicit UV bounds.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x0 the minimum X bound
     * @param y0 the minimum Y bound
     * @param z the Z coordinate
     * @param x1 the maximum X bound
     * @param y1 the maximum Y bound
     * @param u0 the minimum U texture coordinate
     * @param u1 the maximum U texture coordinate
     * @param v0 the minimum V texture coordinate
     * @param v1 the maximum V texture coordinate
     */
    public static void faceNorth(PoseStack.Pose pose, VertexConsumer c, int light,
            float x0, float y0, float z, float x1, float y1,
            float u0, float u1, float v0, float v1) {
        GooRenderUtil.vertex(pose, c, light, x1, y1, z, u0, v0, 0, 0, NORMAL_NEG);
        GooRenderUtil.vertex(pose, c, light, x1, y0, z, u0, v1, 0, 0, NORMAL_NEG);
        GooRenderUtil.vertex(pose, c, light, x0, y0, z, u1, v1, 0, 0, NORMAL_NEG);
        GooRenderUtil.vertex(pose, c, light, x0, y1, z, u1, v0, 0, 0, NORMAL_NEG);
    }

    /**
     * South face (+Z) with explicit UV bounds.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x0 the minimum X bound
     * @param y0 the minimum Y bound
     * @param z the Z coordinate
     * @param x1 the maximum X bound
     * @param y1 the maximum Y bound
     * @param u0 the minimum U texture coordinate
     * @param u1 the maximum U texture coordinate
     * @param v0 the minimum V texture coordinate
     * @param v1 the maximum V texture coordinate
     */
    public static void faceSouth(PoseStack.Pose pose, VertexConsumer c, int light,
            float x0, float y0, float z, float x1, float y1,
            float u0, float u1, float v0, float v1) {
        GooRenderUtil.vertex(pose, c, light, x0, y1, z, u0, v0, 0, 0, 1);
        GooRenderUtil.vertex(pose, c, light, x0, y0, z, u0, v1, 0, 0, 1);
        GooRenderUtil.vertex(pose, c, light, x1, y0, z, u1, v1, 0, 0, 1);
        GooRenderUtil.vertex(pose, c, light, x1, y1, z, u1, v0, 0, 0, 1);
    }

    /**
     * West face (-X) with explicit UV bounds.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x the X coordinate
     * @param y0 the minimum Y bound
     * @param z0 the minimum Z bound
     * @param y1 the maximum Y bound
     * @param z1 the maximum Z bound
     * @param u0 the minimum U texture coordinate
     * @param u1 the maximum U texture coordinate
     * @param v0 the minimum V texture coordinate
     * @param v1 the maximum V texture coordinate
     */
    public static void faceWest(PoseStack.Pose pose, VertexConsumer c, int light,
            float x, float y0, float z0, float y1, float z1,
            float u0, float u1, float v0, float v1) {
        GooRenderUtil.vertex(pose, c, light, x, y1, z0, u0, v0, NORMAL_NEG, 0, 0);
        GooRenderUtil.vertex(pose, c, light, x, y0, z0, u0, v1, NORMAL_NEG, 0, 0);
        GooRenderUtil.vertex(pose, c, light, x, y0, z1, u1, v1, NORMAL_NEG, 0, 0);
        GooRenderUtil.vertex(pose, c, light, x, y1, z1, u1, v0, NORMAL_NEG, 0, 0);
    }

    /**
     * East face (+X) with explicit UV bounds.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param x the X coordinate
     * @param y0 the minimum Y bound
     * @param z0 the minimum Z bound
     * @param y1 the maximum Y bound
     * @param z1 the maximum Z bound
     * @param u0 the minimum U texture coordinate
     * @param u1 the maximum U texture coordinate
     * @param v0 the minimum V texture coordinate
     * @param v1 the maximum V texture coordinate
     */
    public static void faceEast(PoseStack.Pose pose, VertexConsumer c, int light,
            float x, float y0, float z0, float y1, float z1,
            float u0, float u1, float v0, float v1) {
        GooRenderUtil.vertex(pose, c, light, x, y1, z1, u0, v0, 1, 0, 0);
        GooRenderUtil.vertex(pose, c, light, x, y0, z1, u0, v1, 1, 0, 0);
        GooRenderUtil.vertex(pose, c, light, x, y0, z0, u1, v1, 1, 0, 0);
        GooRenderUtil.vertex(pose, c, light, x, y1, z0, u1, v0, 1, 0, 0);
    }

    /**
     * Renders a complete gasket box (all 6 faces) with gasket UVs.
     *
     * @param pose  the pose matrix entry
     * @param c     the vertex consumer
     * @param light the packed light value
     * @param x0    the minimum X bound
     * @param y0    the minimum Y bound
     * @param z0    the minimum Z bound
     * @param x1    the maximum X bound
     * @param y1    the maximum Y bound
     * @param z1    the maximum Z bound
     * @param gsU0  gasket side U start
     * @param gsU1  gasket side U end
     * @param gsV1  gasket side V end
     */
    public static void gasketBox(PoseStack.Pose pose, VertexConsumer c, int light,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float gsU0, float gsU1, float gsV1) {
        gasketTop(pose, c, light, x0, y1, z0, x1, z1);
        gasketBottom(pose, c, light, x0, y0, z0, x1, z1);
        faceNorth(pose, c, light, x0, y0, z0, x1, y1, gsU0, gsU1, 0, gsV1);
        faceSouth(pose, c, light, x0, y0, z1, x1, y1, gsU0, gsU1, 0, gsV1);
        faceWest(pose, c, light, x0, y0, z0, y1, z1, gsU0, gsU1, 0, gsV1);
        faceEast(pose, c, light, x1, y0, z0, y1, z1, gsU0, gsU1, 0, gsV1);
    }

}
