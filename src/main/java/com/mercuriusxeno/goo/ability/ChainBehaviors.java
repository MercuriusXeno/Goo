package com.mercuriusxeno.goo.ability;

import org.jspecify.annotations.Nullable;

/**
 * Static helpers for working with a {@link ChainBehavior} that may be
 * either a concrete instance (legacy ChainProfile path) or a
 * {@link DataDrivenChainBehavior} wrapper (ability-driven path).
 *
 * <p>Client visuals and other consumers that need the concrete inner
 * behavior should go through {@link #findFirst} instead of using
 * {@code instanceof} directly, which only matches the legacy path and
 * fails silently when the user has selected an ability.</p>
 */
public final class ChainBehaviors {

    private ChainBehaviors() {
    }

    /**
     * Returns the first concrete behavior of the given type that this
     * chain marker exposes, looking through any
     * {@link DataDrivenChainBehavior} wrapper.
     *
     * @param <T>      the requested concrete behavior type
     * @param behavior the chain behavior to search (may be null)
     * @param type     the class to match against
     * @return the matching concrete behavior, or null if none
     */
    public static <T extends ChainBehavior> @Nullable T findFirst(
            @Nullable ChainBehavior behavior, Class<T> type) {
        if (behavior == null) {
            return null;
        }
        if (type.isInstance(behavior)) {
            return type.cast(behavior);
        }
        if (behavior instanceof DataDrivenChainBehavior wrapper) {
            return wrapper.findInner(type);
        }
        return null;
    }
}
