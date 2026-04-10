package com.mercuriusxeno.goo.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.EnumMap;
import java.util.Map;

/**
 * Static shape-rotation utilities for the plexer block.
 * Rotates the south-facing base VoxelShape into all four horizontal facings.
 */
final class PlexerShapeHelper {

    /** Pixels per block for coordinate conversion. */
    private static final double PIXELS_PER_BLOCK = 16;
    /** 180-degree rotation (2 CW steps). */
    private static final int ROTATION_HALF = 2;
    /** 270-degree rotation (3 CW steps). */
    private static final int ROTATION_THREE_QUARTER = 3;

    // -- Rotation coord array indices: {x1, z1, x2, z2} --
    /** Index of x2 in the rotation coord array. */
    private static final int COORD_X2 = 2;
    /** Index of z2 in the rotation coord array. */
    private static final int COORD_Z2 = 3;

    private PlexerShapeHelper() { }

    /** Builds VoxelShapes for all four horizontal facings from the south-facing base.
     *
     * @param southShape the composite south-facing shape
     * @return shapes keyed by direction
     */
    static Map<Direction, VoxelShape> buildShapes(VoxelShape southShape) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.SOUTH, southShape);
        map.put(Direction.WEST, rotateShapeCw(southShape, 1));
        map.put(Direction.NORTH, rotateShapeCw(southShape, ROTATION_HALF));
        map.put(Direction.EAST, rotateShapeCw(southShape, ROTATION_THREE_QUARTER));
        return map;
    }

    /**
     * Rotates a VoxelShape clockwise around the Y axis by the given number
     * of 90-degree steps. Decomposes into AABB parts and reassembles.
     *
     * @param shape the VoxelShape to rotate
     * @param steps number of 90-degree clockwise steps
     * @return the voxel shape
     */
    static VoxelShape rotateShapeCw(VoxelShape shape, int steps) {
        if (steps == 0) { return shape; }
        VoxelShape[] result = { Shapes.empty() };
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
            result[0] = Shapes.or(result[0],
                rotateBoxCw(x1, y1, z1, x2, y2, z2, steps)));
        return result[0];
    }

    /** Rotates a single AABB clockwise around Y by the given 90-degree steps and returns it as a VoxelShape.
     *
     * @param x1    min X of the AABB
     * @param y1    min Y of the AABB
     * @param z1    min Z of the AABB
     * @param x2    max X of the AABB
     * @param y2    max Y of the AABB
     * @param z2    max Z of the AABB
     * @param steps number of 90-degree clockwise steps
     * @return the rotated VoxelShape
     */
    static VoxelShape rotateBoxCw(double x1, double y1, double z1,
            double x2, double y2, double z2, int steps) {
        double[] coords = { x1, z1, x2, z2 };
        for (int s = 0; s < steps; s++) {
            rotateCoordsOnce(coords);
        }
        return Block.box(coords[0] * PIXELS_PER_BLOCK, y1 * PIXELS_PER_BLOCK,
            coords[1] * PIXELS_PER_BLOCK, coords[COORD_X2] * PIXELS_PER_BLOCK,
            y2 * PIXELS_PER_BLOCK, coords[COORD_Z2] * PIXELS_PER_BLOCK);
    }

    /** Applies one 90-degree clockwise rotation step to {x1, z1, x2, z2} in place.
     *
     * @param c the coordinate array {x1, z1, x2, z2}
     */
    static void rotateCoordsOnce(double... c) {
        double tmpX1 = 1.0 - c[COORD_Z2];
        double tmpZ1 = c[0];
        double tmpX2 = 1.0 - c[1];
        double tmpZ2 = c[COORD_X2];
        c[0] = tmpX1;
        c[1] = tmpZ1;
        c[COORD_X2] = tmpX2;
        c[COORD_Z2] = tmpZ2;
    }
}
