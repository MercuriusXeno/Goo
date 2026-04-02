package com.mercuriusxeno.goo.effect;

/**
 * Pure math functions for world effect calculations. Framework-free so
 * they can be unit tested without bootstrapping Minecraft.
 */
public final class EffectMath {

    private EffectMath() {}

    /** Base pulse interval in ticks (16 seconds). */
    private static final int BASE_PULSE_INTERVAL = 320;
    /** Maximum stacks that affect pulse interval. */
    private static final int MAX_PULSE_STACKS = 4;

    /**
     * Can a stack be added given current count and cap?
     *
     * @param currentStacks current stack level
     * @param maxStacks     maximum allowed stacks
     * @return true if stacking is permitted
     */
    public static boolean canStack(int currentStacks, int maxStacks) {
        return currentStacks < maxStacks;
    }

    /**
     * Returns true if the fuse has not yet expired.
     *
     * @param remaining ticks left on the fuse
     * @return true if live
     */
    public static boolean isFuseLive(int remaining) {
        return remaining > 0;
    }

    /**
     * Computes the pulse interval in ticks for a given stack count.
     * Formula: 320 / 2^(min(stackCount, 4) - 1).
     *
     * @param stackCount current stack level (1-based)
     * @return interval in ticks
     */
    public static int computePulseInterval(int stackCount) {
        int clamped = Math.min(stackCount, MAX_PULSE_STACKS);
        return BASE_PULSE_INTERVAL >> (clamped - 1);
    }

    // ── Chain effect range formulas ───────────────────────────────────────

    /**
     * Blaze explosion radius. Formula: 1 + 2n.
     *
     * @param stackCount 1-based stack level
     * @return explosion radius (3, 5, 7, 9)
     */
    public static float computeExplosionRadius(int stackCount) {
        return 1.0f + 2.0f * stackCount;
    }

    /**
     * Frost freeze radius. Formula: 3 + 2n.
     *
     * @param stackCount 1-based stack level
     * @return spherical freeze radius (5, 7, 9)
     */
    public static int computeFreezeRadius(int stackCount) {
        return 3 + 2 * stackCount;
    }

    /**
     * Nether conversion radius. Formula: 1 + 2n.
     *
     * @param stackCount 1-based stack level
     * @return spherical conversion radius (3, 5, 7, 9)
     */
    public static int computeNetherRadius(int stackCount) {
        return 1 + 2 * stackCount;
    }

    /**
     * Rock implosion depth. Formula: n².
     *
     * @param stackCount 1-based stack level
     * @return column depth (1, 4, 9, 16, 25)
     */
    public static int computeImplosionDepth(int stackCount) {
        return stackCount * stackCount;
    }
}
