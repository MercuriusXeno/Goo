package com.mercuriusxeno.goo.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.EnumMap;
import java.util.Map;

/**
 * Builds and rotates per-facing VoxelShapes for the tap block.
 * All shapes start south-facing and are rotated CW around Y.
 */
final class TapShapeBuilder {

    /** 180-degree rotation (2 CW steps). */
    private static final int ROTATION_HALF = 2;
    /** 270-degree rotation (3 CW steps). */
    private static final int ROTATION_THREE_QUARTER = 3;
    /** Pixels per block for coordinate conversion. */
    private static final double PIXELS_PER_BLOCK = 16;

    // Array indices for the [x1, z1, x2, z2] rotation tuple.
    /** Index of min-X in the XZ rotation tuple. */
    private static final int XZ_X1 = 0;
    /** Index of min-Z in the XZ rotation tuple. */
    private static final int XZ_Z1 = 1;
    /** Index of max-X in the XZ rotation tuple. */
    private static final int XZ_X2 = 2;
    /** Index of max-Z in the XZ rotation tuple. */
    private static final int XZ_Z2 = 3;

    private TapShapeBuilder() { }

    /** Builds per-facing sub-shapes from a south-facing base.
     *
     * @param southBase the south-facing base shape
     * @return the new sub shapes
     */
    static Map<Direction, VoxelShape> buildSubShapes(VoxelShape southBase) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.SOUTH, southBase);
        map.put(Direction.NORTH, rotateShapeCw(southBase, ROTATION_HALF));
        map.put(Direction.EAST, rotateShapeCw(southBase, ROTATION_THREE_QUARTER));
        map.put(Direction.WEST, rotateShapeCw(southBase, 1));
        return map;
    }

    /**
     * Builds VoxelShapes for all four horizontal facings. South-facing base shape
     * is the union of body, spigot, and valve.
     *
     * @param southBody   the south-facing body shape
     * @param southSpigot the south-facing spigot shape
     * @param southValve  the south-facing valve shape
     * @return the new shapes
     */
    static Map<Direction, VoxelShape> buildShapes(
            VoxelShape southBody, VoxelShape southSpigot, VoxelShape southValve) {
        VoxelShape south = southShape(southBody, southSpigot, southValve);
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.SOUTH, south);
        map.put(Direction.NORTH, rotateShapeCw(south, ROTATION_HALF));
        map.put(Direction.EAST, rotateShapeCw(south, ROTATION_THREE_QUARTER));
        map.put(Direction.WEST, rotateShapeCw(south, 1));
        return map;
    }

    /** Builds per-facing shapes with canister slot included (for when a canister is inserted).
     *
     * @param southBody         the south-facing body shape
     * @param southSpigot       the south-facing spigot shape
     * @param southValve        the south-facing valve shape
     * @param southCanisterSlot the south-facing canister slot shape
     * @return the new shapes with canister
     */
    static Map<Direction, VoxelShape> buildShapesWithCanister(
            VoxelShape southBody, VoxelShape southSpigot,
            VoxelShape southValve, VoxelShape southCanisterSlot) {
        VoxelShape south = Shapes.or(
            southShape(southBody, southSpigot, southValve), southCanisterSlot);
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.SOUTH, south);
        map.put(Direction.NORTH, rotateShapeCw(south, ROTATION_HALF));
        map.put(Direction.EAST, rotateShapeCw(south, ROTATION_THREE_QUARTER));
        map.put(Direction.WEST, rotateShapeCw(south, 1));
        return map;
    }

    /** Builds the south-facing composite shape: body + spigot + valve.
     *
     * @param southBody   the south-facing body shape
     * @param southSpigot the south-facing spigot shape
     * @param southValve  the south-facing valve shape
     * @return the voxel shape
     */
    private static VoxelShape southShape(
            VoxelShape southBody, VoxelShape southSpigot, VoxelShape southValve) {
        return Shapes.or(southBody, southSpigot, southValve);
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
            result[0] = Shapes.or(result[0], rotateBox(x1, y1, z1, x2, y2, z2, steps)));
        return result[0];
    }

    /** Rotates a single AABB clockwise around Y by the given 90-degree steps and converts to pixel coords.
     *
     * @param x1    min X in unit coords
     * @param y1    min Y in unit coords
     * @param z1    min Z in unit coords
     * @param x2    max X in unit coords
     * @param y2    max Y in unit coords
     * @param z2    max Z in unit coords
     * @param steps number of 90-degree clockwise steps
     * @return the rotated box as a VoxelShape in pixel coords
     */
    private static VoxelShape rotateBox(
            double x1, double y1, double z1, double x2, double y2, double z2, int steps) {
        double[] xz = rotateXZ(x1, z1, x2, z2, steps);
        return Block.box(
            xz[XZ_X1] * PIXELS_PER_BLOCK, y1 * PIXELS_PER_BLOCK, xz[XZ_Z1] * PIXELS_PER_BLOCK,
            xz[XZ_X2] * PIXELS_PER_BLOCK, y2 * PIXELS_PER_BLOCK, xz[XZ_Z2] * PIXELS_PER_BLOCK);
    }

    /** Rotates XZ coordinates clockwise around Y by the given 90-degree steps.
     *
     * @param x1    min X in unit coords
     * @param z1    min Z in unit coords
     * @param x2    max X in unit coords
     * @param z2    max Z in unit coords
     * @param steps number of 90-degree clockwise steps
     * @return array of [rx1, rz1, rx2, rz2] after rotation
     */
    private static double[] rotateXZ(double x1, double z1, double x2, double z2, int steps) {
        double[] xz = { x1, z1, x2, z2 };
        for (int s = 0; s < steps; s++) {
            rotateCwOnce(xz);
        }
        return xz;
    }

    /** Applies one 90-degree clockwise rotation around Y to XZ coordinates in place.
     *
     * @param xz array of [x1, z1, x2, z2] to rotate in place
     */
    @SuppressWarnings("PMD.UseVarargs") // array mutated in place
    private static void rotateCwOnce(double[] xz) {
        double tmpX1 = 1.0 - xz[XZ_Z2];
        double tmpZ1 = xz[XZ_X1];
        double tmpX2 = 1.0 - xz[XZ_Z1];
        double tmpZ2 = xz[XZ_X2];
        xz[XZ_X1] = tmpX1;
        xz[XZ_Z1] = tmpZ1;
        xz[XZ_X2] = tmpX2;
        xz[XZ_Z2] = tmpZ2;
    }
}
