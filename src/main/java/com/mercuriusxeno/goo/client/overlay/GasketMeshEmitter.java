package com.mercuriusxeno.goo.client.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Emits filled box quads and diagonal warning stripe geometry for gasket
 * overlay rendering. All methods are stateless and operate on raw vertex data.
 */
final class GasketMeshEmitter {
    /** Inset for stripe z-fighting prevention. */
    private static final double STRIPE_INSET = 0.001;

    /** Number of stripe bands across a face. */
    private static final int STRIPE_COUNT = 5;

    /** Fraction of each stripe band that is filled (rest is gap). */
    private static final double STRIPE_FILL = 0.4;

    /** Width of each stripe band as a fraction of the face (1/STRIPE_COUNT). */
    private static final double BAND_WIDTH = 1.0 / STRIPE_COUNT;

    /** Diagonal stripe shift for warning pattern. */
    private static final double STRIPE_DIAGONAL_SHIFT = 0.3;

    /** Stripe corner array index: near-side start of band. */
    private static final int CORNER_NEAR_START = 0;

    /** Stripe corner array index: near-side end of band. */
    private static final int CORNER_NEAR_END = 1;

    /** Stripe corner array index: far-side end (diagonally shifted). */
    private static final int CORNER_FAR_END = 2;

    /** Stripe corner array index: far-side start (diagonally shifted). */
    private static final int CORNER_FAR_START = 3;

    /** Vertex float offset for Y component. */
    private static final int VERT_Y = 1;

    /** Vertex float offset for Z component. */
    private static final int VERT_Z = 2;

    /** Index into box float array for max X. */
    private static final int BOX_X1 = 3;

    /** Index into box float array for max Y. */
    private static final int BOX_Y1 = 4;

    /** Index into box float array for max Z. */
    private static final int BOX_Z1 = 5;

    private GasketMeshEmitter() {}

    /**
     * Converts AABB min/max to camera-offset float array [x0,y0,z0,x1,y1,z1].
     *
     * @param bounds the bounding box
     * @param ofs camera offset
     * @return six floats: min xyz then max xyz
     */
    private static float[] boundsToFloats(AABB bounds, Vec3 ofs) {
        return new float[] {
                (float) (bounds.minX + ofs.x), (float) (bounds.minY + ofs.y),
                (float) (bounds.minZ + ofs.z), (float) (bounds.maxX + ofs.x),
                (float) (bounds.maxY + ofs.y), (float) (bounds.maxZ + ofs.z)
        };
    }

    /**
     * Renders six filled faces of an AABB as translucent quads.
     *
     * @param poseStack the pose stack for rendering
     * @param consumer the vertex consumer
     * @param bounds the axis-aligned bounding box
     * @param ofs camera offset
     * @param color the ARGB color value
     */
    static void renderFilledBox(PoseStack poseStack, VertexConsumer consumer,
            AABB bounds, Vec3 ofs, int color) {
        float[] f = boundsToFloats(bounds, ofs);
        PoseStack.Pose pose = poseStack.last();
        emitYFaces(pose, consumer, f[0], f[VERT_Y], f[VERT_Z], f[BOX_X1], f[BOX_Y1], f[BOX_Z1], color);
        emitZFaces(pose, consumer, f[0], f[VERT_Y], f[VERT_Z], f[BOX_X1], f[BOX_Y1], f[BOX_Z1], color);
        emitXFaces(pose, consumer, f[0], f[VERT_Y], f[VERT_Z], f[BOX_X1], f[BOX_Y1], f[BOX_Z1], color);
    }

    /**
     * Emits the bottom (Y-) and top (Y+) face quads.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param x0 minimum X
     * @param y0 minimum Y
     * @param z0 minimum Z
     * @param x1 maximum X
     * @param y1 maximum Y
     * @param z1 maximum Z
     * @param color the ARGB color value
     */
    private static void emitYFaces(PoseStack.Pose pose, VertexConsumer consumer,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        consumer.addVertex(pose, x0, y0, z0).setColor(color);
        consumer.addVertex(pose, x1, y0, z0).setColor(color);
        consumer.addVertex(pose, x1, y0, z1).setColor(color);
        consumer.addVertex(pose, x0, y0, z1).setColor(color);

        consumer.addVertex(pose, x0, y1, z1).setColor(color);
        consumer.addVertex(pose, x1, y1, z1).setColor(color);
        consumer.addVertex(pose, x1, y1, z0).setColor(color);
        consumer.addVertex(pose, x0, y1, z0).setColor(color);
    }

    /**
     * Emits the north (Z-) and south (Z+) face quads.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param x0 minimum X
     * @param y0 minimum Y
     * @param z0 minimum Z
     * @param x1 maximum X
     * @param y1 maximum Y
     * @param z1 maximum Z
     * @param color the ARGB color value
     */
    private static void emitZFaces(PoseStack.Pose pose, VertexConsumer consumer,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        consumer.addVertex(pose, x0, y0, z0).setColor(color);
        consumer.addVertex(pose, x0, y1, z0).setColor(color);
        consumer.addVertex(pose, x1, y1, z0).setColor(color);
        consumer.addVertex(pose, x1, y0, z0).setColor(color);

        consumer.addVertex(pose, x1, y0, z1).setColor(color);
        consumer.addVertex(pose, x1, y1, z1).setColor(color);
        consumer.addVertex(pose, x0, y1, z1).setColor(color);
        consumer.addVertex(pose, x0, y0, z1).setColor(color);
    }

    /**
     * Emits the west (X-) and east (X+) face quads.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param x0 minimum X
     * @param y0 minimum Y
     * @param z0 minimum Z
     * @param x1 maximum X
     * @param y1 maximum Y
     * @param z1 maximum Z
     * @param color the ARGB color value
     */
    private static void emitXFaces(PoseStack.Pose pose, VertexConsumer consumer,
            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        consumer.addVertex(pose, x0, y0, z1).setColor(color);
        consumer.addVertex(pose, x0, y1, z1).setColor(color);
        consumer.addVertex(pose, x0, y1, z0).setColor(color);
        consumer.addVertex(pose, x0, y0, z0).setColor(color);

        consumer.addVertex(pose, x1, y0, z0).setColor(color);
        consumer.addVertex(pose, x1, y1, z0).setColor(color);
        consumer.addVertex(pose, x1, y1, z1).setColor(color);
        consumer.addVertex(pose, x1, y0, z1).setColor(color);
    }

    /**
     * Renders diagonal warning stripes across all six faces of the AABB to
     * indicate the gasket is already connected.
     *
     * @param poseStack the pose stack for rendering
     * @param consumer the vertex consumer
     * @param bounds the axis-aligned bounding box
     * @param ofs camera offset
     * @param color the ARGB color value
     */
    static void renderDiagonalStripes(PoseStack poseStack, VertexConsumer consumer,
            AABB bounds, Vec3 ofs, int color) {
        AABB inset = computeStripeInsetBounds(bounds, ofs);
        PoseStack.Pose pose = poseStack.last();
        renderHorizontalFaceStripes(pose, consumer, color, inset);
        renderVerticalZFaceStripes(pose, consumer, color, inset);
        renderVerticalXFaceStripes(pose, consumer, color, inset);
    }

    /**
     * Computes the inset bounds for stripe rendering, applying camera offset
     * and a small inset to prevent z-fighting with the filled box.
     *
     * @param bounds the gasket bounds
     * @param ofs the camera offset
     * @return the inset bounds in camera-relative coordinates
     */
    private static AABB computeStripeInsetBounds(AABB bounds, Vec3 ofs) {
        return new AABB(
                bounds.minX + ofs.x - STRIPE_INSET,
                bounds.minY + ofs.y - STRIPE_INSET,
                bounds.minZ + ofs.z - STRIPE_INSET,
                bounds.maxX + ofs.x + STRIPE_INSET,
                bounds.maxY + ofs.y + STRIPE_INSET,
                bounds.maxZ + ofs.z + STRIPE_INSET);
    }

    /**
     * Renders diagonal stripes on the top (Y+) and bottom (Y-) horizontal faces.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param b the inset bounds
     */
    private static void renderHorizontalFaceStripes(PoseStack.Pose pose, VertexConsumer consumer,
            int color, AABB b) {
        renderHorizontalStripe(pose, consumer, color, b.minX, b.maxY, b.minZ, b.maxX, b.maxZ, true);
        renderHorizontalStripe(pose, consumer, color, b.minX, b.minY, b.minZ, b.maxX, b.maxZ, false);
    }

    /**
     * Renders diagonal stripes on the north (Z-) and south (Z+) faces.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param b the inset bounds
     */
    private static void renderVerticalZFaceStripes(PoseStack.Pose pose, VertexConsumer consumer,
            int color, AABB b) {
        renderVerticalZStripe(pose, consumer, color, b.minX, b.minY, b.minZ, b.maxX, b.maxY, false);
        renderVerticalZStripe(pose, consumer, color, b.minX, b.minY, b.maxZ, b.maxX, b.maxY, true);
    }

    /**
     * Renders diagonal stripes on the west (X-) and east (X+) faces.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param b the inset bounds
     */
    private static void renderVerticalXFaceStripes(PoseStack.Pose pose, VertexConsumer consumer,
            int color, AABB b) {
        renderVerticalXStripe(pose, consumer, color, b.minX, b.minY, b.minZ, b.maxY, b.maxZ, false);
        renderVerticalXStripe(pose, consumer, color, b.maxX, b.minY, b.minZ, b.maxY, b.maxZ, true);
    }

    /**
     * Renders diagonal stripe bands on a horizontal face at a fixed Y coordinate.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param x0 minimum X
     * @param fixedY the Y coordinate of the face
     * @param z0 minimum Z
     * @param x1 maximum X
     * @param z1 maximum Z
     * @param flip whether to reverse winding order
     */
    private static void renderHorizontalStripe(PoseStack.Pose pose, VertexConsumer consumer,
            int color, double x0, double fixedY, double z0, double x1, double z1, boolean flip) {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            float[] c = computeStripeCorners(i, x0, x1 - x0);
            emitHorizontalStripeQuad(pose, consumer, color, flip, c, (float) fixedY, (float) z0, (float) z1);
        }
    }

    /**
     * Emits a single horizontal-face stripe quad from computed corners.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param flip whether to reverse winding order
     * @param c the four stripe corner X positions
     * @param y the fixed Y coordinate
     * @param z0 the near Z
     * @param z1 the far Z
     */
    private static void emitHorizontalStripeQuad(PoseStack.Pose pose, VertexConsumer consumer,
            int color, boolean flip, float[] c, float y, float z0, float z1) {
        emitQuad(pose, consumer, color, flip,
                c[CORNER_NEAR_START], y, z0, c[CORNER_NEAR_END], y, z0,
                c[CORNER_FAR_END], y, z1, c[CORNER_FAR_START], y, z1);
    }

    /**
     * Renders diagonal stripe bands on a vertical face at a fixed Z coordinate.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param x0 minimum X
     * @param y0 minimum Y
     * @param fixedZ the Z coordinate of the face
     * @param x1 maximum X
     * @param y1 maximum Y
     * @param flip whether to reverse winding order
     */
    private static void renderVerticalZStripe(PoseStack.Pose pose, VertexConsumer consumer,
            int color, double x0, double y0, double fixedZ, double x1, double y1, boolean flip) {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            float[] c = computeStripeCorners(i, x0, x1 - x0);
            emitVerticalZStripeQuad(pose, consumer, color, flip, c, (float) y0, (float) y1, (float) fixedZ);
        }
    }

    /**
     * Emits a single Z-face stripe quad from computed corners.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param flip whether to reverse winding order
     * @param c the four stripe corner X positions
     * @param y0 the bottom Y
     * @param y1 the top Y
     * @param z the fixed Z coordinate
     */
    private static void emitVerticalZStripeQuad(PoseStack.Pose pose, VertexConsumer consumer,
            int color, boolean flip, float[] c, float y0, float y1, float z) {
        emitQuad(pose, consumer, color, flip,
                c[CORNER_NEAR_START], y0, z, c[CORNER_NEAR_END], y0, z,
                c[CORNER_FAR_END], y1, z, c[CORNER_FAR_START], y1, z);
    }

    /**
     * Renders diagonal stripe bands on X-facing vertical faces (stripes in ZY plane).
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param fixedX the fixed X coordinate
     * @param y0 the minimum Y bound
     * @param z0 the minimum Z bound
     * @param y1 the maximum Y bound
     * @param z1 the maximum Z bound
     * @param flip whether to reverse winding order
     */
    private static void renderVerticalXStripe(PoseStack.Pose pose, VertexConsumer consumer,
            int color, double fixedX, double y0, double z0, double y1, double z1, boolean flip) {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            float[] c = computeStripeCorners(i, z0, z1 - z0);
            emitVerticalXStripeQuad(pose, consumer, color, flip, c, (float) fixedX, (float) y0, (float) y1);
        }
    }

    /**
     * Emits a single X-face stripe quad from computed corners.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param flip whether to reverse winding order
     * @param c the four stripe corner Z positions
     * @param x the fixed X coordinate
     * @param y0 the bottom Y
     * @param y1 the top Y
     */
    private static void emitVerticalXStripeQuad(PoseStack.Pose pose, VertexConsumer consumer,
            int color, boolean flip, float[] c, float x, float y0, float y1) {
        emitQuad(pose, consumer, color, flip,
                x, y0, c[CORNER_NEAR_START], x, y0, c[CORNER_NEAR_END],
                x, y1, c[CORNER_FAR_END], x, y1, c[CORNER_FAR_START]);
    }

    /**
     * Computes the four diagonal stripe corner positions along the primary axis.
     * Returns [nearStart, nearEnd, farEnd, farStart] where near is the unshifted
     * edge and far is the diagonally shifted edge.
     *
     * @param stripeIndex the stripe band index
     * @param origin the axis origin coordinate
     * @param axisLen the axis length
     * @return four corner positions along the primary axis
     */
    private static float[] computeStripeCorners(int stripeIndex, double origin, double axisLen) {
        double t0 = stripeIndex * BAND_WIDTH;
        double t1 = t0 + BAND_WIDTH * STRIPE_FILL;
        return new float[] {
                (float) (origin + axisLen * t0),
                (float) (origin + axisLen * t1),
                (float) (origin + axisLen * Math.min(t1 + STRIPE_DIAGONAL_SHIFT, 1.0)),
                (float) (origin + axisLen * Math.min(t0 + STRIPE_DIAGONAL_SHIFT, 1.0))
        };
    }

    /**
     * Emits a single quad with four vertices. When flip is true, vertices are
     * emitted in reverse order (3,2,1,0) for correct face winding.
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param flip whether to reverse winding order
     * @param x0 vertex 0 X
     * @param y0 vertex 0 Y
     * @param z0 vertex 0 Z
     * @param x1 vertex 1 X
     * @param y1 vertex 1 Y
     * @param z1 vertex 1 Z
     * @param x2 vertex 2 X
     * @param y2 vertex 2 Y
     * @param z2 vertex 2 Z
     * @param x3 vertex 3 X
     * @param y3 vertex 3 Y
     * @param z3 vertex 3 Z
     */
    private static void emitQuad(PoseStack.Pose pose, VertexConsumer consumer, int color,
            boolean flip, float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3) {
        if (flip) {
            emitQuadVertices(pose, consumer, color, x3, y3, z3, x2, y2, z2, x1, y1, z1, x0, y0, z0);
        } else {
            emitQuadVertices(pose, consumer, color, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
        }
    }

    /**
     * Emits four vertices in the given order (no winding logic).
     *
     * @param pose the pose matrix entry
     * @param consumer the vertex consumer
     * @param color the ARGB color value
     * @param x0 vertex 0 X
     * @param y0 vertex 0 Y
     * @param z0 vertex 0 Z
     * @param x1 vertex 1 X
     * @param y1 vertex 1 Y
     * @param z1 vertex 1 Z
     * @param x2 vertex 2 X
     * @param y2 vertex 2 Y
     * @param z2 vertex 2 Z
     * @param x3 vertex 3 X
     * @param y3 vertex 3 Y
     * @param z3 vertex 3 Z
     */
    private static void emitQuadVertices(PoseStack.Pose pose, VertexConsumer consumer, int color,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3) {
        consumer.addVertex(pose, x0, y0, z0).setColor(color);
        consumer.addVertex(pose, x1, y1, z1).setColor(color);
        consumer.addVertex(pose, x2, y2, z2).setColor(color);
        consumer.addVertex(pose, x3, y3, z3).setColor(color);
    }
}
