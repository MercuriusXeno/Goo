package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

/**
 * Threading context for vertex emission. Created once per render call,
 * carries the pose/consumer/light triple so every downstream method
 * drops 3 parameters.
 */
record RenderCtx(PoseStack.Pose pose, VertexConsumer c, int light) {

    /** Full white opaque color. */
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    /** Normal sign for negative-facing surfaces. */
    private static final float NORMAL_NEG = -1f;

    // ── Single vertex ──────────────────────────────────────────────────

    /**
     * Emits a single vertex with explicit color.
     *
     * @param color the ARGB color
     * @param x     the X position
     * @param y     the Y position
     * @param z     the Z position
     * @param u     the U texture coordinate
     * @param v     the V texture coordinate
     */
    void vertexColored(int color, float x, float y, float z, float u, float v,
                       float nx, float ny, float nz) {
        c.addVertex(pose, x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    /**
     * Emits a single white-opaque vertex.
     *
     * @param x  the X position
     * @param y  the Y position
     * @param z  the Z position
     * @param u  the U texture coordinate
     * @param v  the V texture coordinate
     */
    void vertex(float x, float y, float z, float u, float v,
                float nx, float ny, float nz) {
        vertexColored(OPAQUE_WHITE, x, y, z, u, v, nx, ny, nz);
    }

    // ── Direction-dispatched face emission ──────────────────────────────

    /**
     * Emits a quad face of a cuboid for the given direction.
     *
     * @param box the axis-aligned bounds
     * @param uv  the texture coordinate rectangle
     * @param dir the face direction (normal)
     */
    void emitFace(CuboidBounds box, GooRenderUtil.UvRect uv, Direction dir) {
        switch (dir) {
            case UP    -> emitFaceY(box, uv, 1f);
            case DOWN  -> emitFaceY(box, uv, NORMAL_NEG);
            case NORTH -> emitFaceZ(box, uv, NORMAL_NEG);
            case SOUTH -> emitFaceZ(box, uv, 1f);
            case WEST  -> emitFaceX(box, uv, NORMAL_NEG);
            case EAST  -> emitFaceX(box, uv, 1f);
        }
    }

    /**
     * Emits all 4 horizontal side faces of a cuboid.
     *
     * @param box the axis-aligned bounds
     * @param uv  the texture coordinate rectangle
     */
    void emitSides(CuboidBounds box, GooRenderUtil.UvRect uv) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            emitFace(box, uv, dir);
        }
    }

    /**
     * Emits all 6 faces of a cuboid.
     *
     * @param box the axis-aligned bounds
     * @param uv  the texture coordinate rectangle
     */
    void emitBox(CuboidBounds box, GooRenderUtil.UvRect uv) {
        for (Direction dir : Direction.values()) {
            emitFace(box, uv, dir);
        }
    }

    // ── Liquid surfaces (colored, upward/downward normal) ──────────────

    /**
     * Emits an upward-facing liquid surface quad with explicit color.
     *
     * @param color the ARGB color
     * @param box   the horizontal bounds (uses x0, x1, z0, z1, yTop)
     * @param uv    the texture coordinate rectangle
     */
    void liquidSurface(int color, CuboidBounds box, GooRenderUtil.UvRect uv) {
        float y = box.yTop();
        vertexColored(color, box.x0(), y, box.z0(), uv.u0(), uv.v0(), 0f, 1f, 0f);
        vertexColored(color, box.x0(), y, box.z1(), uv.u0(), uv.v1(), 0f, 1f, 0f);
        vertexColored(color, box.x1(), y, box.z1(), uv.u1(), uv.v1(), 0f, 1f, 0f);
        vertexColored(color, box.x1(), y, box.z0(), uv.u1(), uv.v0(), 0f, 1f, 0f);
    }

    /**
     * Emits a downward-facing liquid surface quad with explicit color.
     *
     * @param color the ARGB color
     * @param box   the horizontal bounds (uses x0, x1, z0, z1, yTop)
     * @param uv    the texture coordinate rectangle
     */
    void liquidSurfaceDown(int color, CuboidBounds box, GooRenderUtil.UvRect uv) {
        float y = box.yTop();
        vertexColored(color, box.x1(), y, box.z0(), uv.u1(), uv.v0(), 0f, NORMAL_NEG, 0f);
        vertexColored(color, box.x1(), y, box.z1(), uv.u1(), uv.v1(), 0f, NORMAL_NEG, 0f);
        vertexColored(color, box.x0(), y, box.z1(), uv.u0(), uv.v1(), 0f, NORMAL_NEG, 0f);
        vertexColored(color, box.x0(), y, box.z0(), uv.u0(), uv.v0(), 0f, NORMAL_NEG, 0f);
    }

    // ── Internal face emitters by axis ─────────────────────────────────

    /** Y-axis face with winding based on normal sign. */
    private void emitFaceY(CuboidBounds box, GooRenderUtil.UvRect uv, float ny) {
        float y = ny > 0 ? box.yTop() : box.yBot();
        if (ny > 0) {
            vertex(box.x0(), y, box.z0(), uv.u0(), uv.v0(), 0f, ny, 0f);
            vertex(box.x0(), y, box.z1(), uv.u0(), uv.v1(), 0f, ny, 0f);
            vertex(box.x1(), y, box.z1(), uv.u1(), uv.v1(), 0f, ny, 0f);
            vertex(box.x1(), y, box.z0(), uv.u1(), uv.v0(), 0f, ny, 0f);
        } else {
            vertex(box.x1(), y, box.z0(), uv.u1(), uv.v0(), 0f, ny, 0f);
            vertex(box.x1(), y, box.z1(), uv.u1(), uv.v1(), 0f, ny, 0f);
            vertex(box.x0(), y, box.z1(), uv.u0(), uv.v1(), 0f, ny, 0f);
            vertex(box.x0(), y, box.z0(), uv.u0(), uv.v0(), 0f, ny, 0f);
        }
    }

    /** X-axis face with winding based on normal sign. */
    private void emitFaceX(CuboidBounds box, GooRenderUtil.UvRect uv, float nx) {
        float x = nx > 0 ? box.x1() : box.x0();
        if (nx > 0) {
            vertex(x, box.yTop(), box.z1(), uv.u1(), uv.v0(), nx, 0f, 0f);
            vertex(x, box.yBot(), box.z1(), uv.u1(), uv.v1(), nx, 0f, 0f);
            vertex(x, box.yBot(), box.z0(), uv.u0(), uv.v1(), nx, 0f, 0f);
            vertex(x, box.yTop(), box.z0(), uv.u0(), uv.v0(), nx, 0f, 0f);
        } else {
            vertex(x, box.yTop(), box.z0(), uv.u1(), uv.v0(), nx, 0f, 0f);
            vertex(x, box.yBot(), box.z0(), uv.u1(), uv.v1(), nx, 0f, 0f);
            vertex(x, box.yBot(), box.z1(), uv.u0(), uv.v1(), nx, 0f, 0f);
            vertex(x, box.yTop(), box.z1(), uv.u0(), uv.v0(), nx, 0f, 0f);
        }
    }

    /** Z-axis face with winding based on normal sign. */
    private void emitFaceZ(CuboidBounds box, GooRenderUtil.UvRect uv, float nz) {
        float z = nz > 0 ? box.z1() : box.z0();
        if (nz > 0) {
            vertex(box.x0(), box.yTop(), z, uv.u1(), uv.v0(), 0f, 0f, nz);
            vertex(box.x0(), box.yBot(), z, uv.u1(), uv.v1(), 0f, 0f, nz);
            vertex(box.x1(), box.yBot(), z, uv.u0(), uv.v1(), 0f, 0f, nz);
            vertex(box.x1(), box.yTop(), z, uv.u0(), uv.v0(), 0f, 0f, nz);
        } else {
            vertex(box.x1(), box.yTop(), z, uv.u0(), uv.v0(), 0f, 0f, nz);
            vertex(box.x1(), box.yBot(), z, uv.u0(), uv.v1(), 0f, 0f, nz);
            vertex(box.x0(), box.yBot(), z, uv.u1(), uv.v1(), 0f, 0f, nz);
            vertex(box.x0(), box.yTop(), z, uv.u1(), uv.v0(), 0f, 0f, nz);
        }
    }
}
