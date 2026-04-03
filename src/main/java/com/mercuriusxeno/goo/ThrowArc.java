package com.mercuriusxeno.goo;

import com.mercuriusxeno.goo.item.GooGloveItem;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Pure math for goo blob throw trajectories and glove hand positioning.
 * Shared between client (preview arc, flight rendering) and server (throw
 * origin). Every method is side-agnostic and testable without framework state.
 */
public final class ThrowArc {

    private ThrowArc() {}

    /** Minecraft standard entity gravity in blocks/tick². */
    public static final double GRAVITY = 0.08;

    /** Flat boost added to arc peak height, in blocks. */
    public static final double ARC_FLAT_BOOST = 1.0;

    /** Multiplier on the gravity-based arc component (1.15 = +15%). */
    public static final double ARC_GRAVITY_SCALE = 1.15;

    /** Blob travel speed in blocks per tick. */
    public static final double BLOCKS_PER_TICK = 1.5;

    /** Lateral offset from eye to the glove arm, in blocks at scale 1. */
    public static final double ARM_SIDE = 0.35;

    /** Downward offset from eye to hand height, in blocks at scale 1. */
    public static final double ARM_DOWN = 0.35;

    /**
     * Computes travel time in ticks for a given distance.
     *
     * @param distance world-space distance in blocks
     * @return travel ticks, always >= 1
     */
    public static double travelTicks(double distance) {
        return Math.max(1, Math.ceil(distance / BLOCKS_PER_TICK));
    }

    /**
     * Computes the base gravity peak height for a given travel time.
     *
     * @param travelTicks total flight time in ticks
     * @return peak height in blocks at t=0.5
     */
    public static double basePeak(double travelTicks) {
        return GRAVITY * travelTicks * travelTicks / 8.0;
    }

    /**
     * Computes the granny-arc boosted peak: 125% of gravity peak + 2 blocks.
     *
     * @param travelTicks total flight time in ticks
     * @return boosted peak height in blocks
     */
    public static double grannyPeak(double travelTicks) {
        return basePeak(travelTicks) * ARC_GRAVITY_SCALE + ARC_FLAT_BOOST;
    }

    /**
     * Interpolates a point on the parabolic arc with a given peak height.
     *
     * @param start       arc origin (hand position)
     * @param end         arc destination (target center)
     * @param t           normalized progress [0..1]
     * @param peak        peak height in blocks (at t=0.5)
     * @return world-space position on the arc
     */
    public static Vec3 arcPoint(Vec3 start, Vec3 end, double t, double peak) {
        double x = start.x + (end.x - start.x) * t;
        double y = start.y + (end.y - start.y) * t;
        double z = start.z + (end.z - start.z) * t;
        double arcY = 4.0 * peak * t * (1.0 - t);
        return new Vec3(x, y + arcY, z);
    }

    /**
     * Samples the arc into a polyline of evenly-spaced t values.
     *
     * @param start    arc origin
     * @param end      arc destination
     * @param peak     peak height in blocks
     * @param segments number of line segments (points = segments + 1)
     * @return sampled positions along the arc
     */
    public static Vec3[] sampleArc(Vec3 start, Vec3 end,
                                   double peak, int segments) {
        Vec3[] points = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            points[i] = arcPoint(start, end, t, peak);
        }
        return points;
    }

    /**
     * Pure hand-offset calculation. Takes basis vectors and returns the
     * world-space offset from the eye to the glove hand.
     *
     * @param rightX right-vector X (negate camera left)
     * @param rightY right-vector Y
     * @param rightZ right-vector Z
     * @param upX    up-vector X
     * @param upY    up-vector Y
     * @param upZ    up-vector Z
     * @param side   +1 for right arm, −1 for left arm
     * @param scale  player scale factor
     * @return offset vector to add to eye position
     */
    public static Vec3 handOffset(double rightX, double rightY, double rightZ,
                                  double upX, double upY, double upZ,
                                  float side, float scale) {
        double s = side * ARM_SIDE * scale;
        double d = ARM_DOWN * scale;
        return new Vec3(
                rightX * s - upX * d,
                rightY * s - upY * d,
                rightZ * s - upZ * d);
    }

    /**
     * Determines which side the glove is on (+1 right, −1 left).
     *
     * @param mainItem main-hand ItemStack
     * @param mainArm  the player's dominant arm
     * @return +1f for right arm, −1f for left arm
     */
    public static float gloveSide(ItemStack mainItem, HumanoidArm mainArm) {
        boolean inMainHand = mainItem.getItem() instanceof GooGloveItem;
        HumanoidArm arm = inMainHand ? mainArm : mainArm.getOpposite();
        return arm == HumanoidArm.RIGHT ? 1f : -1f;
    }
}
