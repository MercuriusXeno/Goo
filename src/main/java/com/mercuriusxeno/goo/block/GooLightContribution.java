package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;

/**
 * Computes block-light emission contributions from goo content.
 *
 * <p>Per type the curve is {@code light = round(peak * sqrt(t))} with
 * {@code t = min(1, fill / saturationFill)}. Sqrt makes a small amount
 * of an emissive type produce noticeable light, then plateau as fill
 * approaches the type's saturation point. A floor of 1 applies whenever
 * any emissive fluid is present, so the "even a tiny bit glows" rule
 * holds even when the rounded value would underflow to 0.
 */
public final class GooLightContribution {

    /** Vanilla block-light ceiling. */
    public static final int MAX_LIGHT = 15;

    /** Minimum light any present goo emits, regardless of fill or peak.
     * Every goo type is at least somewhat luminous; the floor ensures
     * even a small amount of any type produces visible light. */
    private static final int FLOOR_PRESENT = 4;

    private GooLightContribution() {}

    /**
     * Light contribution for a single (type, amount, capacity) triple.
     *
     * @param type     the goo type, may be null
     * @param amount   present amount in microblobs (mB)
     * @param capacity slot capacity in microblobs; non-positive yields 0
     * @return contribution in [{@link #FLOOR_PRESENT}, peakLight] when present, 0 if empty
     */
    public static int forSlot(GooType type, long amount, long capacity) {
        if (type == null || amount <= 0L || capacity <= 0L) {
            return 0;
        }
        float fill = (float) amount / (float) capacity;
        float t = Math.min(1f, fill / type.saturationFill());
        int contribution = Math.round(type.peakLight() * (float) Math.sqrt(t));
        return Math.max(FLOOR_PRESENT, contribution);
    }

    /**
     * Sums contributions and clamps to {@link #MAX_LIGHT}.
     *
     * @param contributions per-slot contributions
     * @return total clamped to vanilla block-light ceiling
     */
    public static int sumClamped(int... contributions) {
        int total = 0;
        for (int c : contributions) {
            total += c;
        }
        return Math.min(MAX_LIGHT, total);
    }

    /**
     * Adds an iterable's contributions and clamps. Convenience for
     * stream-style aggregation in BEs that walk N slots.
     *
     * @param running running total so far
     * @param add     contribution to add
     * @return new total clamped to {@link #MAX_LIGHT}
     */
    public static int addClamped(int running, int add) {
        return Math.min(MAX_LIGHT, running + add);
    }
}
