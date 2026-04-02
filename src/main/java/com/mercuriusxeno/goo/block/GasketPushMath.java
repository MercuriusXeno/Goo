package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooContents;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Pure math for gasket push: computes how much goo transfers from
 * a reservoir to a destination, separated for unit testing.
 */
public final class GasketPushMath {

    private GasketPushMath() {}

    /**
     * Result of a push operation: what was accepted by the destination
     * and what remains in the reservoir.
     *
     * @param accepted  goo contents successfully transferred
     * @param remaining goo contents left in the reservoir
     */
    public record PushResult(GooContents accepted, GooContents remaining) {}

    /** Exponent for the tapering transfer rate formula. */
    private static final double TAPER_EXPONENT = 0.6;

    /**
     * Computes the per-tick transfer rate for a given remaining volume.
     * Uses {@code ceil(remaining^0.6)} so the rate tapers as the source drains.
     *
     * @param remaining volume in mB still in the source
     * @return mB to transfer this tick (min 1 if remaining > 0, 0 if empty)
     */
    public static long taperRate(long remaining) {
        if (remaining <= 0) return 0L;
        return Math.max(1L, (long) Math.ceil(Math.pow(remaining, TAPER_EXPONENT)));
    }

    /**
     * Tapered variant of {@link #computePush}: caps each type's offer at
     * {@link #taperRate(long)} before passing to the acceptor. Use this for
     * per-tick gasket pushes so transfer drains gradually instead of instantly.
     *
     * @param reservoir the current reservoir contents
     * @param acceptor  function (type, volume) -> amount accepted
     * @return push result with accepted and remaining contents
     */
    public static PushResult computeTaperedPush(GooContents reservoir,
            BiFunction<GooType, Long, Long> acceptor) {
        if (reservoir.isEmpty()) {
            return new PushResult(GooContents.EMPTY, GooContents.EMPTY);
        }

        GooContents accepted = GooContents.EMPTY;
        GooContents remaining = GooContents.EMPTY;

        for (Map.Entry<GooType, Long> entry : reservoir.getAll().entrySet()) {
            GooType type = entry.getKey();
            long volume = entry.getValue();
            long budget = taperRate(volume);
            long offer = Math.min(budget, volume);
            long took = acceptor.apply(type, offer);
            took = Math.max(0, Math.min(took, offer));

            if (took > 0) {
                accepted = accepted.withAdded(type, took);
            }
            long leftover = volume - took;
            if (leftover > 0) {
                remaining = remaining.withAdded(type, leftover);
            }
        }

        return new PushResult(accepted, remaining);
    }

    /**
     * Iterates each goo type in the reservoir, offering it to the acceptor.
     * The acceptor returns the amount actually accepted for each (type, volume) pair.
     * Produces a split of accepted vs remaining contents.
     *
     * @param reservoir the current reservoir contents
     * @param acceptor  function (type, volume) -> amount accepted
     * @return push result with accepted and remaining contents
     */
    public static PushResult computePush(GooContents reservoir,
            BiFunction<GooType, Long, Long> acceptor) {
        if (reservoir.isEmpty()) {
            return new PushResult(GooContents.EMPTY, GooContents.EMPTY);
        }

        GooContents accepted = GooContents.EMPTY;
        GooContents remaining = GooContents.EMPTY;

        for (Map.Entry<GooType, Long> entry : reservoir.getAll().entrySet()) {
            GooType type = entry.getKey();
            long volume = entry.getValue();
            long took = acceptor.apply(type, volume);
            took = Math.max(0, Math.min(took, volume));

            if (took > 0) {
                accepted = accepted.withAdded(type, took);
            }
            long leftover = volume - took;
            if (leftover > 0) {
                remaining = remaining.withAdded(type, leftover);
            }
        }

        return new PushResult(accepted, remaining);
    }
}
