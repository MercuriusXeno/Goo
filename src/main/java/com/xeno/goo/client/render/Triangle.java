package com.xeno.goo.client.render;

import net.minecraft.util.math.vector.Vector3d;

public class Triangle
{
    // public final boolean isPointingUp;
    public Vector3d v1;
    public Vector3d v2;
    public Vector3d v3;
    public Triangle(Vector3d v1, Vector3d v2, Vector3d v3) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
    }
}
