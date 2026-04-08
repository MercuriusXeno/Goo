package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooContents;
import java.util.EnumMap;
import java.util.Map;

/**
 * Pure math and logic for the crucible, extracted so unit tests can
 * run without triggering Minecraft class initialization.
 */
public final class CrucibleMath {

    /** Base exponent for the extraction rate power-law curve. */
    static final double BASE_EXPONENT = 0.25;
    /** Additional exponent per rune ink matrix. */
    static final double EXPONENT_PER_MATRIX = 0.05;
    /** Maximum number of matrices that affect the exponent. */
    static final int MAX_MATRICES = 5;

    private CrucibleMath() {}

    /**
     * Computes extraction rate (mB/tick) from remaining pool volume and matrix count.
     * Formula: max(1, floor(remaining ^ (BASE_EXPONENT + matrices * EXPONENT_PER_MATRIX))).
     * Rate decelerates naturally as the pool drains (half-life feel).
     *
     * @param remaining the remaining volume in mB
     * @param matrices  the matrix upgrade count
     * @return the long value
     */
    static long extractionRate(long remaining, int matrices) {
        if (remaining <= 0) { return 1L; }
        double exponent = computeExponent(matrices);
        return Math.max(1L, (long) Math.floor(Math.pow(remaining, exponent)));
    }

    /** Computes the effective exponent from matrix count, clamped to [0, 5].
     *
     * @param matrices the matrix upgrade count
     * @return the computed exponent
     */
    static double computeExponent(int matrices) {
        int clamped = Math.max(0, Math.min(matrices, MAX_MATRICES));
        return BASE_EXPONENT + clamped * EXPONENT_PER_MATRIX;
    }

    /** Moves a value toward a target by at most step, without overshooting.
     *
     * @param current the current value
     * @param target  the target position or property
     * @param step    the step
     * @return the float value
     */
    static float moveToward(float current, float target, float step) {
        if (current < target) { return Math.min(current + step, target); }
        return Math.max(current - step, target);
    }

    /**
     * Distributes a total extraction budget proportionally across all goo types
     * in the pool. Each type gets floor(rate * typeVolume / totalVolume), with
     * a minimum of 1 mB (clamped to available volume). Remainder from rounding
     * is given to the largest type.
     *
     * @param contents the goo contents
     * @param rate     the extraction rate in mB/tick
     * @return the computed drain shares
     */
    static Map<GooType, Long> computeDrainShares(GooContents contents, long rate) {
        Map<GooType, Long> shares = new EnumMap<>(GooType.class);
        long totalVolume = contents.totalVolume();
        long allocated = allocateProportional(shares, contents, rate, totalVolume);
        distributeRemainder(shares, contents, rate, allocated);
        return shares;
    }

    /** Allocates each type's share proportionally, floored to at least 1 mB.
     *
     * @param shares      output map for per-type shares
     * @param contents    the goo contents
     * @param rate        the extraction rate
     * @param totalVolume the total goo volume
     * @return the sum of all allocated shares
     */
    private static long allocateProportional(Map<GooType, Long> shares,
            GooContents contents, long rate, long totalVolume) {
        long allocated = 0;
        for (Map.Entry<GooType, Long> entry : contents.getAll().entrySet()) {
            long available = entry.getValue();
            long share = Math.min(Math.max(1L, rate * available / totalVolume), available);
            shares.put(entry.getKey(), share);
            allocated += share;
        }
        return allocated;
    }

    /**
     * Assigns unallocated budget (from rounding) to the largest type,
     * capped at that type's available volume.
     *
     * @param shares    the per-type drain shares
     * @param contents  the goo contents
     * @param rate      the extraction rate in mB/tick
     * @param allocated the total allocated so far
     */
    private static void distributeRemainder(Map<GooType, Long> shares,
            GooContents contents, long rate, long allocated) {
        long remainder = rate - allocated;
        if (remainder <= 0) { return; }
        GooType largest = contents.largestType();
        if (largest == null) { return; }
        long available = contents.getVolume(largest);
        long current = shares.getOrDefault(largest, 0L);
        shares.put(largest, Math.min(current + remainder, available));
    }
}
