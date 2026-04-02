package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import org.jspecify.annotations.Nullable;

/**
 * Debounce + crossfade state machine for the dominant goo type in a crucible.
 * Stabilizes the displayed type so it doesn't flicker during small fluctuations,
 * and smoothly blends between types when a real change occurs.
 *
 * <p>Client-side only, not serialized. Tick once per game tick via {@link #tick}.
 */
public class DominantTypeFader {

    /** Ticks required before the shown type switches. */
    private static final int DEBOUNCE_TICKS = 20;
    /** Crossfade alpha increment per tick. 20 ticks = 1 second blend. */
    private static final float CROSSFADE_SPEED = 0.05f;

    /** Stabilized dominant type for rendering. */
    private @Nullable GooType shownType;
    /** Candidate type waiting to replace the shown type. */
    private @Nullable GooType pendingType;
    /** Ticks the pending type has been consistently dominant. */
    private int pendingTicks;
    /** Last game tick the debounce was advanced. */
    private long lastTick = -1;
    /** Outgoing type during a crossfade transition. */
    private @Nullable GooType outgoingType;
    /** Crossfade progress [0, 1]: 0 = fully outgoing, 1 = fully shown. */
    private float crossfadeAlpha = 1f;

    /**
     * Advances the state machine by one game tick.
     *
     * @param actual   the current dominant goo type (null if reservoir is empty)
     * @param gameTick the current game time (deduplicates multiple calls per tick)
     */
    public void tick(@Nullable GooType actual, long gameTick) {
        if (gameTick == lastTick) return;
        lastTick = gameTick;
        tickCrossfade();
        if (actual == null) {
            clear();
            return;
        }
        if (shownType == null) {
            shownType = actual;
            return;
        }
        if (actual == shownType) {
            resetPending();
            return;
        }
        advancePendingOrSwitch(actual);
    }

    /** Advances crossfade alpha toward 1, clearing outgoing type when complete. */
    private void tickCrossfade() {
        if (outgoingType == null) return;
        crossfadeAlpha += CROSSFADE_SPEED;
        if (crossfadeAlpha >= 1f) {
            crossfadeAlpha = 1f;
            outgoingType = null;
        }
    }

    /** Clears all state when the reservoir is empty. */
    private void clear() {
        shownType = null;
        outgoingType = null;
        crossfadeAlpha = 1f;
        resetPending();
    }

    /** Resets the pending candidate when the current shown type is confirmed. */
    private void resetPending() {
        pendingType = null;
        pendingTicks = 0;
    }

    /** Advances the pending debounce counter, switching when it expires. */
    private void advancePendingOrSwitch(GooType actual) {
        if (actual == pendingType) {
            pendingTicks++;
        } else {
            pendingType = actual;
            pendingTicks = 1;
        }
        if (pendingTicks >= DEBOUNCE_TICKS) {
            outgoingType = shownType;
            shownType = actual;
            crossfadeAlpha = 0f;
            resetPending();
        }
    }

    /** Returns the debounce-stabilized dominant goo type for rendering. */
    public @Nullable GooType getShownType() {
        return shownType;
    }

    /** Returns the outgoing type during a crossfade, or null if not crossfading. */
    public @Nullable GooType getOutgoingType() {
        return outgoingType;
    }

    /** Returns the crossfade alpha [0, 1]: 0 = fully outgoing, 1 = fully shown. */
    public float getCrossfadeAlpha() {
        return crossfadeAlpha;
    }
}
