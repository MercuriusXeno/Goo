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

    private ShapeHitCheck() {}

    /** Pixels per block dimension (16), used for hit-to-pixel coordinate conversion. */
    public static final double PIXELS_PER_BLOCK = 16.0;

    /** Returns true if the hit contact point lies within the given shape's AABB. */
    public static boolean hitInsideShape(BlockHitResult hit, BlockPos pos, VoxelShape shape) {
        double localX = hit.getLocation().x - pos.getX();
        double localY = hit.getLocation().y - pos.getY();
        double localZ = hit.getLocation().z - pos.getZ();
        var bounds = shape.bounds();
        return localX >= bounds.minX && localX <= bounds.maxX
            && localY >= bounds.minY && localY <= bounds.maxY
            && localZ >= bounds.minZ && localZ <= bounds.maxZ;
    }
}
