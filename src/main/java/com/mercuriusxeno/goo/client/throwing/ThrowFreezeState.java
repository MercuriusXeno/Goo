package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Client-only static holder for the post-throw aim-freeze window.
 *
 * <p>When the player throws a blob, the resolved aim target is captured here
 * for a short window of ticks. While frozen, {@code GooTargetHighlighter}
 * returns the frozen target instead of re-resolving, so rapid chain throws
 * land on the exact same spot even while the first blob is mid-flight.
 *
 * <p>Sneaking cancels the freeze immediately, matching the existing
 * "sneak bypasses aim assist" rule.
 *
 * <p>Pure state - no Minecraft hot-path imports - so it is unit-testable
 * in a plain JUnit context.
 */
public final class ThrowFreezeState {

    /** Duration of the freeze window in client ticks. */
    public static final int FREEZE_TICKS = 10;

    private static int ticksRemaining;
    private static @Nullable TargetResult frozenTarget;

    private ThrowFreezeState() {}

    /**
     * Arms the freeze with the target the player was aiming at when the
     * throw was sent.
     *
     * @param target the captured aim target (not {@link TargetResult.None})
     */
    public static void arm(TargetResult target) {
        ticksRemaining = FREEZE_TICKS;
        frozenTarget = target;
    }

    /**
     * Decrements the remaining tick count. When it reaches zero, clears
     * the frozen target.
     */
    public static void tick() {
        if (ticksRemaining <= 0) { return; }
        ticksRemaining--;
        if (ticksRemaining == 0) {
            frozenTarget = null;
        }
    }

    /** Full reset: zero ticks, no target. */
    public static void clear() {
        ticksRemaining = 0;
        frozenTarget = null;
    }

    /**
     * Whether the freeze is currently active AND has a target to hand back.
     * An {@link TargetResult.EntityTarget} whose entity has died since
     * arming auto-clears the state and returns false.
     *
     * @return true if a frozen target is in force this tick
     */
    public static boolean isFrozen() {
        return getFrozenTarget() != null;
    }

    /**
     * Returns the frozen target, or null if the freeze has expired or the
     * captured entity target is no longer alive. Auto-clears on a dead
     * entity so callers do not need to guard separately.
     *
     * @return the frozen target, or null
     */
    public static @Nullable TargetResult getFrozenTarget() {
        if (ticksRemaining <= 0 || frozenTarget == null) { return null; }
        if (frozenTarget instanceof TargetResult.EntityTarget et
                && !et.entity().isAlive()) {
            clear();
            return null;
        }
        return frozenTarget;
    }

    /**
     * Returns true if the freeze is active and its captured target is a
     * block-like target whose position equals the given pos. Used by the
     * chain marker BER to persist the highlighted visual during the freeze
     * window even when the cone-based aim assist momentarily drifts.
     *
     * @param pos the chain marker block position
     * @return true if the freeze is pinned on this block position
     */
    public static boolean isFrozenOnChainMarker(BlockPos pos) {
        TargetResult target = getFrozenTarget();
        if (target instanceof TargetResult.ChainMarkerTarget cmt) {
            return cmt.pos().equals(pos);
        }
        if (target instanceof TargetResult.BlockTarget bt) {
            return bt.pos().equals(pos);
        }
        return false;
    }
}
