package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GooValue;
import net.minecraft.core.BlockPos;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pure math functions for world effect calculations. Framework-free so
 * they can be unit tested without bootstrapping Minecraft.
 */
public final class EffectMath {

    /** Goo types counted toward rock majority (rock and crystal from quartz ancestry). */
    private static final Set<GooType> ROCK_FAMILY = EnumSet.of(GooType.ROCK, GooType.CRYSTAL);

    /** Base pulse interval in ticks (16 seconds). */
    private static final int BASE_PULSE_INTERVAL = 320;
    /** Maximum stacks that affect pulse interval. */
    private static final int MAX_PULSE_STACKS = 4;

    /** Ticks per second, used by duration formulas. */
    private static final int TICKS_PER_SECOND = 20;

    /** Majority threshold multiplier: rockTotal * 2 > total means >50%. */
    private static final int MAJORITY_MULTIPLIER = 2;
    /** Base freeze radius before stacking. */
    private static final int FREEZE_BASE_RADIUS = 2;
    /** Duration multiplier per unit of radius for frost fields. */
    private static final int FROST_DURATION_PER_RADIUS = 4;
    /** Nether conversion base radius. */
    private static final int NETHER_BASE_RADIUS = 1;
    /** Nether conversion radius added per stack. */
    private static final int NETHER_RADIUS_PER_STACK = 2;

    private EffectMath() {}

    /**
     * Iterates all block positions within a sphere and applies the action to each.
     *
     * @param center the center of the sphere
     * @param radius the sphere radius
     * @param action the action to apply to each position in the sphere
     */
    public static void forEachInSphere(BlockPos center, int radius, Consumer<BlockPos> action) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        action.accept(center.offset(dx, dy, dz));
                    }
                }
            }
        }
    }

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

    // ── Rock majority predicate ─────────────────────────────────────────────

    /**
     * Returns true if rock + crystal make up strictly more than half of
     * the block's total goo blobs. This lets mixed-composition blocks
     * like bricks or polished stone qualify while keeping metal-heavy
     * or organic blocks out.
     *
     * @param value the block's goo composition, or null if unknown
     * @return true if rock family is the majority
     */
    public static boolean isRockCompatible(GooValue value) {
        if (value == null || value.isEmpty()) { return false; }
        int rockTotal = 0;
        for (GooType type : ROCK_FAMILY) {
            rockTotal += value.get(type);
        }
        return rockTotal * MAJORITY_MULTIPLIER > value.totalBlobs();
    }

    // ── Chain effect range formulas ───────────────────────────────────────

    /**
     * Frost freeze radius. Formula: 2 + n.
     *
     * @param stackCount 1-based stack level
     * @return spherical freeze radius (3, 4, 5, 6)
     */
    public static int computeFreezeRadius(int stackCount) {
        return FREEZE_BASE_RADIUS + stackCount;
    }

    /**
     * Frost field duration in ticks. Formula: 4 * radius * 20.
     *
     * @param radius the freeze radius (from computeFreezeRadius)
     * @return duration in ticks (240, 320, 400, 480)
     */
    public static int computeFrostDuration(int radius) {
        return FROST_DURATION_PER_RADIUS * radius * TICKS_PER_SECOND;
    }

    /**
     * Nether conversion radius. Formula: 1 + 2n.
     *
     * @param stackCount 1-based stack level
     * @return spherical conversion radius (3, 5, 7, 9)
     */
    public static int computeNetherRadius(int stackCount) {
        return NETHER_BASE_RADIUS + NETHER_RADIUS_PER_STACK * stackCount;
    }

}
