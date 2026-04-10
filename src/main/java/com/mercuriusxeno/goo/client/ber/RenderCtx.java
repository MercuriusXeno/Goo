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
 *
 * @param pose  the current pose matrix entry
 * @param c     the vertex consumer for geometry emission
 * @param light the packed light level for shading
 */
public record RenderCtx(PoseStack.Pose pose, VertexConsumer c, int light) {

    /** Full white opaque color. */
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    /** Normal sign for negative-facing surfaces. */
    private static final float NORMAL_NEG = -1f;
    /** Gasket cap U/V extent: 4px / 16px. */
    private static final float GC_UV = 0.25f;
    /** Gasket bottom V end: 8px / 16px. */
    private static final float GC_BOTTOM_V = 0.5f;

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
     * @param nx    the X normal
     * @param ny    the Y normal
     * @param nz    the Z normal
     */
    public void vertexColored(int color, float x, float y, float z, float u, float v,
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
     * @param nx the X normal
     * @param ny the Y normal
     * @param nz the Z normal
     */
    public void vertex(float x, float y, float z, float u, float v,
                float nx, float ny, float nz) {
        vertexColored(OPAQUE_WHITE, x, y, z, u, v, nx, ny, nz);
    }

    // ── Direction-dispatched face emission (white) ─────────────────────

    /**
     * Emits a white quad face of a cuboid for the given direction.
     *
     * @param box the axis-aligned bounds
     * @param uv  the texture coordinate rectangle
     * @param dir the face direction (normal)
     */
    public void emitFace(CuboidBounds box, GooRenderUtil.UvRect uv, Direction dir) {
        emitFace(OPAQUE_WHITE, box, uv, dir);
    }

    /** Emits all 4 horizontal white side faces of a cuboid.
     *
     * @param box the axis-aligned bounds
     * @param uv  the texture coordinate rectangle
     */
    public void emitSides(CuboidBounds box, GooRenderUtil.UvRect uv) {
        emitSides(OPAQUE_WHITE, box, uv);
    }

    /** Emits all 6 white faces of a cuboid.
     *
     * @param box the axis-aligned bounds
     * @param uv  the texture coordinate rectangle
     */
    public void emitBox(CuboidBounds box, GooRenderUtil.UvRect uv) {
        emitBox(OPAQUE_WHITE, box, uv);
    }

    // ── Direction-dispatched face emission (colored) ───────────────────

    /**
     * Emits a colored quad face of a cuboid for the given direction.
     *
     * @param color the ARGB color
     * @param box   the axis-aligned bounds
     * @param uv    the texture coordinate rectangle
     * @param dir   the face direction (normal)
     */
    public void emitFace(int color, CuboidBounds box, GooRenderUtil.UvRect uv, Direction dir) {
        float sign = isPositiveFace(dir) ? 1f : NORMAL_NEG;
        switch (dir.getAxis()) {
            case X -> emitFaceX(color, box, uv, sign);
            case Y -> emitFaceY(color, box, uv, sign);
            case Z -> emitFaceZ(color, box, uv, sign);
        }
    }

    /** Returns true for directions whose normal points along the positive axis. */
    private static boolean isPositiveFace(Direction dir) {
        return dir == Direction.UP || dir == Direction.SOUTH || dir == Direction.EAST;
    }

    /** Emits all 4 horizontal colored side faces of a cuboid.
     *
     * @param color the ARGB color
     * @param box   the axis-aligned bounds
     * @param uv    the texture coordinate rectangle
     */
    public void emitSides(int color, CuboidBounds box, GooRenderUtil.UvRect uv) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            emitFace(color, box, uv, dir);
        }
    }

    /** Emits all 6 colored faces of a cuboid.
     *
     * @param color the ARGB color
     * @param box   the axis-aligned bounds
     * @param uv    the texture coordinate rectangle
     */
    public void emitBox(int color, CuboidBounds box, GooRenderUtil.UvRect uv) {
        for (Direction dir : Direction.values()) {
            emitFace(color, box, uv, dir);
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
    public void liquidSurface(int color, CuboidBounds box, GooRenderUtil.UvRect uv) {
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
    public void liquidSurfaceDown(int color, CuboidBounds box, GooRenderUtil.UvRect uv) {
        float y = box.yTop();
        vertexColored(color, box.x1(), y, box.z0(), uv.u1(), uv.v0(), 0f, NORMAL_NEG, 0f);
        vertexColored(color, box.x1(), y, box.z1(), uv.u1(), uv.v1(), 0f, NORMAL_NEG, 0f);
        vertexColored(color, box.x0(), y, box.z1(), uv.u0(), uv.v1(), 0f, NORMAL_NEG, 0f);
        vertexColored(color, box.x0(), y, box.z0(), uv.u0(), uv.v0(), 0f, NORMAL_NEG, 0f);
    }

    // ── Gasket box ─────────────────────────────────────────────────────

    /**
     * Emits a complete gasket box (top cap, bottom cap, 4 side faces)
     * using the standard gasket UV layout.
     *
     * @param box  the axis-aligned bounds
     * @param gsU0 gasket side U start
     * @param gsU1 gasket side U end
     * @param gsV1 gasket side V end
     */
    public void gasketBox(CuboidBounds box, float gsU0, float gsU1, float gsV1) {
        gasketTopCap(box);
        gasketBottomCap(box);
        GooRenderUtil.UvRect sideUv = new GooRenderUtil.UvRect(gsU0, 0, gsU1, gsV1);
        emitSides(box, sideUv);
    }

    /** Emits the upward-facing top cap quad of a gasket box. */
    private void gasketTopCap(CuboidBounds box) {
        float y1 = box.yTop();
        vertex(box.x0(), y1, box.z0(), 0, 0, 0f, 1f, 0f);
        vertex(box.x0(), y1, box.z1(), 0, GC_UV, 0f, 1f, 0f);
        vertex(box.x1(), y1, box.z1(), GC_UV, GC_UV, 0f, 1f, 0f);
        vertex(box.x1(), y1, box.z0(), GC_UV, 0, 0f, 1f, 0f);
    }

    /** Emits the downward-facing bottom cap quad of a gasket box. */
    private void gasketBottomCap(CuboidBounds box) {
        float y0 = box.yBot();
        vertex(box.x1(), y0, box.z0(), GC_UV, GC_UV, 0f, NORMAL_NEG, 0f);
        vertex(box.x1(), y0, box.z1(), GC_UV, GC_BOTTOM_V, 0f, NORMAL_NEG, 0f);
        vertex(box.x0(), y0, box.z1(), 0, GC_BOTTOM_V, 0f, NORMAL_NEG, 0f);
        vertex(box.x0(), y0, box.z0(), 0, GC_UV, 0f, NORMAL_NEG, 0f);
    }

    // ── Internal face emitters by axis ─────────────────────────────────

    /** Y-axis face with winding based on normal sign. */
    private void emitFaceY(int color, CuboidBounds box, GooRenderUtil.UvRect uv, float ny) {
        float y = ny > 0 ? box.yTop() : box.yBot();
        if (ny > 0) {
            vertexColored(color, box.x0(), y, box.z0(), uv.u0(), uv.v0(), 0f, ny, 0f);
            vertexColored(color, box.x0(), y, box.z1(), uv.u0(), uv.v1(), 0f, ny, 0f);
            vertexColored(color, box.x1(), y, box.z1(), uv.u1(), uv.v1(), 0f, ny, 0f);
            vertexColored(color, box.x1(), y, box.z0(), uv.u1(), uv.v0(), 0f, ny, 0f);
        } else {
            vertexColored(color, box.x1(), y, box.z0(), uv.u1(), uv.v0(), 0f, ny, 0f);
            vertexColored(color, box.x1(), y, box.z1(), uv.u1(), uv.v1(), 0f, ny, 0f);
            vertexColored(color, box.x0(), y, box.z1(), uv.u0(), uv.v1(), 0f, ny, 0f);
            vertexColored(color, box.x0(), y, box.z0(), uv.u0(), uv.v0(), 0f, ny, 0f);
        }
    }

    /** X-axis face with winding based on normal sign. */
    private void emitFaceX(int color, CuboidBounds box, GooRenderUtil.UvRect uv, float nx) {
        float x = nx > 0 ? box.x1() : box.x0();
        if (nx > 0) {
            vertexColored(color, x, box.yTop(), box.z1(), uv.u1(), uv.v0(), nx, 0f, 0f);
            vertexColored(color, x, box.yBot(), box.z1(), uv.u1(), uv.v1(), nx, 0f, 0f);
            vertexColored(color, x, box.yBot(), box.z0(), uv.u0(), uv.v1(), nx, 0f, 0f);
            vertexColored(color, x, box.yTop(), box.z0(), uv.u0(), uv.v0(), nx, 0f, 0f);
        } else {
            vertexColored(color, x, box.yTop(), box.z0(), uv.u1(), uv.v0(), nx, 0f, 0f);
            vertexColored(color, x, box.yBot(), box.z0(), uv.u1(), uv.v1(), nx, 0f, 0f);
            vertexColored(color, x, box.yBot(), box.z1(), uv.u0(), uv.v1(), nx, 0f, 0f);
            vertexColored(color, x, box.yTop(), box.z1(), uv.u0(), uv.v0(), nx, 0f, 0f);
        }
    }

    /** Z-axis face with winding based on normal sign. */
    private void emitFaceZ(int color, CuboidBounds box, GooRenderUtil.UvRect uv, float nz) {
        float z = nz > 0 ? box.z1() : box.z0();
        if (nz > 0) {
            vertexColored(color, box.x0(), box.yTop(), z, uv.u1(), uv.v0(), 0f, 0f, nz);
            vertexColored(color, box.x0(), box.yBot(), z, uv.u1(), uv.v1(), 0f, 0f, nz);
            vertexColored(color, box.x1(), box.yBot(), z, uv.u0(), uv.v1(), 0f, 0f, nz);
            vertexColored(color, box.x1(), box.yTop(), z, uv.u0(), uv.v0(), 0f, 0f, nz);
        } else {
            vertexColored(color, box.x1(), box.yTop(), z, uv.u0(), uv.v0(), 0f, 0f, nz);
            vertexColored(color, box.x1(), box.yBot(), z, uv.u0(), uv.v1(), 0f, 0f, nz);
            vertexColored(color, box.x0(), box.yBot(), z, uv.u1(), uv.v1(), 0f, 0f, nz);
            vertexColored(color, box.x0(), box.yTop(), z, uv.u1(), uv.v0(), 0f, 0f, nz);
        }
    }
}
