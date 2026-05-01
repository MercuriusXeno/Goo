package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;

/**
 * Vertex context for flat-colored quads (no UV, light, overlay, or normal).
 * Used by debug/overlay render types where vertices carry only position + color.
 *
 * @param pose the current pose matrix entry
 * @param c    the vertex consumer for geometry emission
 */
public record FlatQuadContext(PoseStack.Pose pose, VertexConsumer c) {

    /** Lookup table indexed by Direction.ordinal() for face dispatch. */
    private static final FaceEmitter[] FACE_EMITTERS = {
        FlatQuadContext::faceDown,   // DOWN = 0
        FlatQuadContext::faceUp,     // UP = 1
        FlatQuadContext::faceNorth,  // NORTH = 2
        FlatQuadContext::faceSouth,  // SOUTH = 3
        FlatQuadContext::faceWest,   // WEST = 4
        FlatQuadContext::faceEast    // EAST = 5
    };

    /** Functional interface for per-face quad emission. */
    @FunctionalInterface
    private interface FaceEmitter {
        /** Emits one face quad.
         *
         * @param ctx   the quad context
         * @param color the ARGB color
         * @param box   the bounds
         */
        void emit(FlatQuadContext ctx, int color, CuboidBounds box);
    }

    /**
     * Emits a single flat-colored vertex.
     *
     * @param x     the X position
     * @param y     the Y position
     * @param z     the Z position
     * @param color the ARGB color
     */
    public void vertex(float x, float y, float z, int color) {
        c.addVertex(pose, x, y, z).setColor(color);
    }

    /**
     * Emits a single face quad on the specified side of the box.
     *
     * @param color the ARGB color
     * @param box   the axis-aligned bounds
     * @param dir   the face direction to emit
     */
    public void emitFace(int color, CuboidBounds box, Direction dir) {
        FACE_EMITTERS[dir.ordinal()].emit(this, color, box);
    }

    /**
     * Emits all 6 faces of a cuboid as flat-colored quads.
     *
     * @param color the ARGB color
     * @param box   the axis-aligned bounds
     */
    public void emitBox(int color, CuboidBounds box) {
        faceUp(color, box);
        faceDown(color, box);
        faceNorth(color, box);
        faceSouth(color, box);
        faceWest(color, box);
        faceEast(color, box);
    }

    /** +Y face. */
    private void faceUp(int color, CuboidBounds box) {
        float y = box.yTop();
        vertex(box.x0(), y, box.z0(), color);
        vertex(box.x0(), y, box.z1(), color);
        vertex(box.x1(), y, box.z1(), color);
        vertex(box.x1(), y, box.z0(), color);
    }

    /** -Y face. */
    private void faceDown(int color, CuboidBounds box) {
        float y = box.yBot();
        vertex(box.x0(), y, box.z1(), color);
        vertex(box.x0(), y, box.z0(), color);
        vertex(box.x1(), y, box.z0(), color);
        vertex(box.x1(), y, box.z1(), color);
    }

    /** -Z face. */
    private void faceNorth(int color, CuboidBounds box) {
        float z = box.z0();
        vertex(box.x0(), box.yBot(), z, color);
        vertex(box.x0(), box.yTop(), z, color);
        vertex(box.x1(), box.yTop(), z, color);
        vertex(box.x1(), box.yBot(), z, color);
    }

    /** +Z face. */
    private void faceSouth(int color, CuboidBounds box) {
        float z = box.z1();
        vertex(box.x1(), box.yBot(), z, color);
        vertex(box.x1(), box.yTop(), z, color);
        vertex(box.x0(), box.yTop(), z, color);
        vertex(box.x0(), box.yBot(), z, color);
    }

    /** -X face. */
    private void faceWest(int color, CuboidBounds box) {
        float x = box.x0();
        vertex(x, box.yBot(), box.z1(), color);
        vertex(x, box.yTop(), box.z1(), color);
        vertex(x, box.yTop(), box.z0(), color);
        vertex(x, box.yBot(), box.z0(), color);
    }

    /** +X face. */
    private void faceEast(int color, CuboidBounds box) {
        float x = box.x1();
        vertex(x, box.yBot(), box.z0(), color);
        vertex(x, box.yTop(), box.z0(), color);
        vertex(x, box.yTop(), box.z1(), color);
        vertex(x, box.yBot(), box.z1(), color);
    }
}
