package com.xeno.goo.library;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Quaternion;

public class AngleHelper
{
    public static Quaternion rotateToMatchLookVector(float p, float y)
    {
        // pitch and yaw are inverted for some reason?
        return eulerDegreesToQuaternion(MathHelper.wrapDegrees(360 - y), MathHelper.wrapDegrees(360 - p), 90f);
    }
    public static Quaternion eulerDegreesToQuaternion(float p, float y, float r)
    {
        return eulerRadiansToQuaternion(rad(p), rad(y), rad(r));
    }

    public static double rad(double i) {
        return i * Math.PI / 180d;
    }

    public static Quaternion eulerRadiansToQuaternion(double p, double y, double r) {
        double cp = Math.cos(p / 2d);
        double sp = Math.sin(p / 2d);
        double cy = Math.cos(y / 2d);
        double sy = Math.sin(y / 2d);
        double cr = Math.cos(r / 2d);
        double sr = Math.sin(r / 2d);

        return new Quaternion(
                (float)(sr * cp * cy - cr * sp * sy),
                (float)(cr * sp * cy + sr * cp * sy),
                (float)(cr * cp * sy - sr * sp * cy),
                (float)(cr * cp * cy + sr * sp * sy));
    }
}
