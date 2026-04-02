package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Shared vertex emitters for canister rendering. Used by both
 * HubBlockEntityRenderer and CanisterBlockEntityRenderer.
 */
public final class CanisterGeometry {

    private CanisterGeometry() {}

    // -- Gasket UV regions: choral_gasket.png, 16x16 --

    /** Gasket cap U/V extent: 4px / 16px. */
    private static final float GC_UV = 0.25f;

    /** Gasket top face: UV region [0,0]-[0.25,0.25]. */
    public static void gasketTop(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float y, float z0, float x1, float z1) {
        GooRenderUtil.vertex(pose, c, light, x0, y, z0, 0, 0, 0, 1, 0);
        GooRenderUtil.vertex(pose, c, light, x0, y, z1, 0, GC_UV, 0, 1, 0);
        GooRenderUtil.vertex(pose, c, light, x1, y, z1, GC_UV, GC_UV, 0, 1, 0);
        GooRenderUtil.vertex(pose, c, light, x1, y, z0, GC_UV, 0, 0, 1, 0);
    }

    /** Gasket bottom face: UV region [0,0.25]-[0.25,0.5]. */
    public static void gasketBottom(PoseStack.Pose pose, VertexConsumer c,
            int light, float x0, float y, float z0, float x1, float z1) {
        GooRenderUtil.vertex(pose, c, light, x1, y, z0, GC_UV, GC_UV, 0, -1, 0);
        GooRenderUtil.vertex(pose, c, light, x1, y, z1, GC_UV, 0.5f, 0, -1, 0);
        GooRenderUtil.vertex(pose, c, light, x0, y, z1, 0, 0.5f, 0, -1, 0);
        GooRenderUtil.vertex(pose, c, light, x0, y, z0, 0, GC_UV, 0, -1, 0);
    }

    /** North face (-Z) with explicit UV bounds. */
    public static void faceNorth(PoseStack.Pose pose, VertexConsumer c, int light,
            float x0, float y0, float z, float x1, float y1,
            float u0, float u1, float v0, float v1) {
        GooRenderUtil.vertex(pose, c, light, x1, y1, z, u0, v0, 0, 0, -1);
        GooRenderUtil.vertex(pose, c, light, x1, y0, z, u0, v1, 0, 0, -1);
        GooRenderUtil.vertex(pose, c, light, x0, y0, z, u1, v1, 0, 0, -1);
        GooRenderUtil.vertex(pose, c, light, x0, y1, z, u1, v0, 0, 0, -1);
    }

    /** South face (+Z) with explicit UV bounds. */
    public static void faceSouth(PoseStack.Pose pose, VertexConsumer c, int light,
            float x0, float y0, float z, float x1, float y1,
            float u0, float u1, float v0, float v1) {
        GooRenderUtil.vertex(pose, c, light, x0, y1, z, u0, v0, 0, 0, 1);
        GooRenderUtil.vertex(pose, c, light, x0, y0, z, u0, v1, 0, 0, 1);
        GooRenderUtil.vertex(pose, c, light, x1, y0, z, u1, v1, 0, 0, 1);
        GooRenderUtil.vertex(pose, c, light, x1, y1, z, u1, v0, 0, 0, 1);
    }

    /** West face (-X) with explicit UV bounds. */
    public static void faceWest(PoseStack.Pose pose, VertexConsumer c, int light,
            float x, float y0, float z0, float y1, float z1,
            float u0, float u1, float v0, float v1) {
        GooRenderUtil.vertex(pose, c, light, x, y1, z0, u0, v0, -1, 0, 0);
        GooRenderUtil.vertex(pose, c, light, x, y0, z0, u0, v1, -1, 0, 0);
        GooRenderUtil.vertex(pose, c, light, x, y0, z1, u1, v1, -1, 0, 0);
        GooRenderUtil.vertex(pose, c, light, x, y1, z1, u1, v0, -1, 0, 0);
    }

    /** East face (+X) with explicit UV bounds. */
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
     * @param gsU0 gasket side U start
     * @param gsU1 gasket side U end
     * @param gsV1 gasket side V end
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
