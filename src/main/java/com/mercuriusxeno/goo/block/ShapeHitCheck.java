package com.mercuriusxeno.goo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Utility for testing whether a hit result's contact point falls within
 * a VoxelShape's bounds. Used by gasket hit detection across all
 * gasket-bearing machines (canisters, vats, crucibles).
 */
public final class ShapeHitCheck {

    /** Pixels per block dimension (16), used for hit-to-pixel coordinate conversion. */
    public static final double PIXELS_PER_BLOCK = 16.0;

    private ShapeHitCheck() {}

    /** Returns true if the hit contact point lies within the given shape's AABB.
     *
     * @param hit   the ray trace hit result
     * @param pos   the block position
     * @param shape the VoxelShape to rotate
     * @return true if the condition is met
     */
    public static boolean hitInsideShape(BlockHitResult hit, BlockPos pos, VoxelShape shape) {
        double localX = hit.getLocation().x - pos.getX();
        double localY = hit.getLocation().y - pos.getY();
        double localZ = hit.getLocation().z - pos.getZ();
        var bounds = shape.bounds();
        return isWithinHorizontal(localX, localZ, bounds)
            && isWithinVertical(localY, bounds);
    }

    /**
     * Returns true if the X and Z coordinates fall within the AABB's horizontal range.
     *
     * @param x local X coordinate
     * @param z local Z coordinate
     * @param bounds the axis-aligned bounding box
     * @return true if within horizontal bounds
     */
    private static boolean isWithinHorizontal(double x, double z,
            net.minecraft.world.phys.AABB bounds) {
        return x >= bounds.minX && x <= bounds.maxX
            && z >= bounds.minZ && z <= bounds.maxZ;
    }

    /**
     * Returns true if the Y coordinate falls within the AABB's vertical range.
     *
     * @param y local Y coordinate
     * @param bounds the axis-aligned bounding box
     * @return true if within vertical bounds
     */
    private static boolean isWithinVertical(double y, net.minecraft.world.phys.AABB bounds) {
        return y >= bounds.minY && y <= bounds.maxY;
    }
}
