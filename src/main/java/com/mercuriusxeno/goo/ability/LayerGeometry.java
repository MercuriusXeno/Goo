package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Shared geometry for the progressive area pipeline: layer-center
 * resolution and footprint perpendicular offsetting. The legacy
 * {@code RockBehavior} and {@code BlazeBehavior} each carried private
 * copies of these helpers; consolidating here lets the per-axis
 * delegates ({@link LayerVisuals}, {@link LayerAudio}) compute their
 * coordinates without pulling in the legacy classes.
 */
public final class LayerGeometry {

    private LayerGeometry() {
    }

    /**
     * Resolves the block position at the center of the given layer.
     * Layer 0 lands on the block adjacent to the marker (the surface
     * the marker was attached to); subsequent layers step further into
     * the wall along {@code placedFace.getOpposite()}.
     *
     * @param origin     the marker block position
     * @param placedFace the face the marker was attached to
     * @param stepIndex  zero-based layer offset
     * @return the layer center block position
     */
    public static BlockPos layerCenter(BlockPos origin, Direction placedFace, int stepIndex) {
        return origin.relative(placedFace.getOpposite(), stepIndex + 1);
    }

    /**
     * Offsets a position in the two axes perpendicular to a blast axis.
     * Used to walk a flat 2D footprint anchored at a layer center.
     *
     * @param center    the layer center position
     * @param blastAxis the axis the blast travels along
     * @param a         first perpendicular offset
     * @param b         second perpendicular offset
     * @return the resulting block position
     */
    public static BlockPos offsetPerpendicular(BlockPos center, Direction.Axis blastAxis,
                                               int a, int b) {
        return switch (blastAxis) {
            case X -> center.offset(0, a, b);
            case Y -> center.offset(a, 0, b);
            case Z -> center.offset(a, b, 0);
        };
    }
}
