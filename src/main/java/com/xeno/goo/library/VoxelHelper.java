package com.xeno.goo.library;

import net.minecraft.block.Block;
import net.minecraft.util.Direction;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;

public class VoxelHelper {
    // just uses "north" as the default
    public static VoxelShape cuboid(Vector3d start, Vector3d end) {
        return cuboidWithHorizontalRotation(Direction.NORTH, start, end);
    }

    // create a cuboid from a direction using two 3d vectors
    public static VoxelShape cuboidWithHorizontalRotation(Direction facing, Vector3d start, Vector3d end)
    {
        return cuboidWithHorizontalRotation(facing, start.x, start.y, start.z, end.x, end.y, end.z);
    }

    // returns a horizontally rotated variant of the cuboid dimensions given to it assuming NORTH is the normal variant.
    public static VoxelShape cuboidWithHorizontalRotation(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2)
    {
        switch (facing)
        {
            case NORTH:
                return makeCuboidShape(x1, y1, z1, x2, y2, z2);
            case EAST:
                return makeCuboidShape(16 - z2, y1, x1, 16 - z1, y2, x2);
            case SOUTH:
                return makeCuboidShape(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST:
                return makeCuboidShape(z1, y1, 16 - x2, z2, y2, 16 - x1);
        }
        return makeCuboidShape(x1, y1, z1, x2, y2, z2);
    }

    // returns a horizontally rotated variant of the vector3f dimensions given to it assuming NORTH is the normal variant.
    public static Vector3f vector3fWithHorizontalRotation(Vector3f vec, Direction facing, float scale)
    {
        switch (facing)
        {
            case NORTH:
                return vec;
            case EAST:
                return new Vector3f(scale - vec.getZ(), vec.getY(), vec.getX());
            case SOUTH:
                return new Vector3f(scale - vec.getX(), vec.getY(), scale - vec.getZ());
            case WEST:
                return new Vector3f(vec.getZ(), vec.getY(), scale - vec.getX());
        }
        return vec;
    }

    public static Vector3f vector3fWithHorizontalRotation16f(Vector3f vec, Direction facing)
    {
        return vector3fWithHorizontalRotation(vec, facing, 16f);
    }

    public static Vector3f vector3fWithHorizontalRotation1f(Vector3f vec, Direction facing)
    {
        return vector3fWithHorizontalRotation(vec, facing, 1f);
    }

    // just a relay to Block's static cuboid method so that the other helpers can return a Voxel and not just values.
    public static VoxelShape makeCuboidShape(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Block.makeCuboidShape(x1, y1, z1, x2, y2, z2);
    }

    public static VoxelShape mergeAll(VoxelShape... shapes)
    {
        VoxelShape combo = VoxelShapes.empty();
        for(int i = 0; i < shapes.length; i++) {
            combo = VoxelShapes.combine(combo, shapes[i], IBooleanFunction.OR);
        }
        return combo;
    }
}
