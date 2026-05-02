package com.mercuriusxeno.goo.client.hud;

import org.jspecify.annotations.Nullable;
import java.util.function.BiPredicate;

/**
 * Shared animation state machine for in-world machine HUD panels. Each
 * per-machine HUD renderer (canister, crucible, vat) holds one instance
 * parameterized by its target type {@code T} (the record carrying the
 * machine's position plus any per-render positional offsets).
 *
 * <p>The animator owns the emerge/retract pitch interpolation and the
 * tracked-target reference. Each frame, the renderer:
 * <ol>
 *   <li>Resolves a target from the current crosshair (or {@code null}).</li>
 *   <li>Calls {@link #tick(Object)} with that target.</li>
 *   <li>If {@link #tracked()} is non-null after the tick, looks up the
 *       machine's data and renders the panel using the latest target
 *       offsets and {@link #pitch()}.</li>
 * </ol>
 *
 * <p>The {@code sameTarget} predicate distinguishes "still aiming at the
 * same machine slot" (refresh offsets without resetting the emerge
 * animation) from "aiming at a new machine" (start a fresh emerge). For
 * machines without per-slot tracking, pass a position-only equality
 * check.
 *
 * @param <T> the renderer's target record type
 */
public final class HudAnimator<T> {

    /** Exponential smoothing time constant in seconds. Lower = snappier. */
    private static final float SMOOTH_TAU = 0.1f;

    /** Pitch threshold below which a retracting panel is considered flush. */
    private static final float RETRACT_THRESHOLD = 0.01f;

    /** Pitch value for a fully emerged panel. */
    private static final float PITCH_EMERGED = 1f;

    private final long[] lastFrameNanos = {0};
    private final BiPredicate<T, T> sameTarget;

    private @Nullable T tracked;
    private float currentPitch;
    private boolean retracting;

    /**
     * Creates a HUD animator with a custom "same target" predicate.
     *
     * @param sameTarget returns true when two non-null targets refer to the
     *                   same machine + slot. Determines whether a refresh
     *                   should preserve the emerge animation or restart it.
     */
    public HudAnimator(BiPredicate<T, T> sameTarget) {
        this.sameTarget = sameTarget;
    }

    /**
     * Drives the state machine once per frame. Call from the
     * {@code RenderLevelStageEvent.AfterOpaqueFeatures} hook with the
     * resolved target (or {@code null} if not aiming at this machine).
     *
     * @param target the current target, or {@code null}
     */
    public void tick(@Nullable T target) {
        float dt = InWorldHud.computeDeltaTime(lastFrameNanos);
        applyTransition(target);
        advancePitch(dt);
    }

    /** @return the currently-tracked target, or {@code null} if idle */
    public @Nullable T tracked() {
        return tracked;
    }

    /** @return the current pitch in [0, 1]: 0 = flush, 1 = fully emerged */
    public float pitch() {
        return currentPitch;
    }

    /** Resets all state to idle. */
    public void clear() {
        tracked = null;
        currentPitch = 0f;
        retracting = false;
    }

    /**
     * Applies the state transition for one frame: new target starts an
     * emerge, same target refreshes offsets, lost target begins retract.
     *
     * @param target the current target, or {@code null}
     */
    private void applyTransition(@Nullable T target) {
        if (target == null) {
            if (tracked != null && !retracting) {
                retracting = true;
            }
            return;
        }
        if (tracked != null && sameTarget.test(tracked, target)) {
            tracked = target;
            if (retracting) {
                retracting = false;
            }
        } else {
            tracked = target;
            currentPitch = 0f;
            retracting = false;
        }
    }

    /**
     * Advances the smoothed pitch toward its target value (0 retracting,
     * 1 emerged) and clears state once a retracting panel falls flush.
     *
     * @param dt seconds since the previous frame
     */
    private void advancePitch(float dt) {
        if (tracked == null) {
            return;
        }
        float targetPitch = retracting ? 0f : PITCH_EMERGED;
        currentPitch = InWorldHud.smoothToward(currentPitch, targetPitch, dt, SMOOTH_TAU);
        if (retracting && currentPitch < RETRACT_THRESHOLD) {
            clear();
        }
    }
}
