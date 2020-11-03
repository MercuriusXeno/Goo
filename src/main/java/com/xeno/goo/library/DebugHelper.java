package com.xeno.goo.library;

import com.xeno.goo.GooMod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;

public class DebugHelper {
    public static String vectorMessage(String msg, Vector3d motion) {
        return msg + ": " + "x: " + motion.x + " y: " + motion.y + "z: " + motion.z;
    }

    public static String blockPosMessage(String msg, BlockPos pos) {
        return msg + ": " + "x: " + pos.getX() + " y: " + pos.getY() + "z: " + pos.getZ();
    }
}
