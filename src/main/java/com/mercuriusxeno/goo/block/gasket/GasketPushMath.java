package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import java.util.function.BiFunction;

/**
 * Pure math for gasket push: computes how much goo transfers from
 * a reservoir to a destination, separated for unit testing.
 */
public final class GasketPushMath {

    /**
     * Exponent for goo (viscous).
     */
    public static final double GOO_EXPONENT = 0.6;

    /**
     * Exponent for vanilla fluids like water (less viscous, faster).
     */
    public static final double WATER_EXPONENT = 0.75;

    private GasketPushMath() {
    }

    /**
     * Computes the per-tick transfer rate for goo fluids (exponent 0.6).
     *
     * @param remaining volume in mB still in the source
     * @return mB to transfer this tick (min 1 if remaining > 0, 0 if empty)
     */
    public static int taperRate(int remaining) {
        return taperRate(remaining, GOO_EXPONENT);
    }

    /**
     * Computes the per-tick transfer rate with a given exponent.
     * Formula: {@code ceil(remaining^exponent)}.
     *
     * @param remaining volume in mB still in the source
     * @param exponent  the power-law exponent
     * @return mB to transfer this tick (min 1 if remaining > 0, 0 if empty)
     */
    public static int taperRate(int remaining, double exponent) {
        if (remaining <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(Math.pow(remaining, exponent)));
    }

    /**
     * Returns the taper exponent for a fluid. Water gets 0.75, everything else 0.6.
     *
     * @param fluid the fluid
     * @return the exponent
     */
    public static double exponentFor(Fluid fluid) {
        return fluid.isSame(Fluids.WATER) ? WATER_EXPONENT : GOO_EXPONENT;
    }

    /**
     * Tapered variant of {@link #computePush}: caps each type's offer at
     * {@link #taperRate    (int)} before passing to the acceptor. Use this for
     * per-tick gasket pushes so transfer drains gradually instead of instantly.
     *
     * @param reservoir the current reservoir contents
     * @param acceptor  function (type, volume) -> amount accepted
     * @return push result with accepted and remaining contents
     */
    public static PushResult computeTaperedPush(GooContents reservoir,
                                                BiFunction<GooType, Integer, Integer> acceptor) {
        return computePush(reservoir,
                (type, vol) -> acceptor.apply(type, Math.min(taperRate(vol), vol)));
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
    public static PushResult computePush(GooContents reservoir, BiFunction<GooType, Integer, Integer> acceptor) {
        if (reservoir.isEmpty()) {
            return new PushResult(GooContents.EMPTY, GooContents.EMPTY);
        }
        return distributeEntries(reservoir, acceptor);
    }

    /**
     * Iterates each goo entry, splitting volume between accepted and remaining.
     *
     * @param reservoir the source goo contents to distribute from
     * @param acceptor  function (type, volume) -> amount accepted per entry
     * @return push result splitting volume into accepted and remaining
     */
    private static PushResult distributeEntries(GooContents reservoir, BiFunction<GooType, Integer, Integer> acceptor) {
        GooContents accepted = GooContents.EMPTY;
        GooContents remaining = GooContents.EMPTY;
        for (var entry : reservoir.getAll().entrySet()) {
            int took = clampedTake(acceptor, entry.getKey(), entry.getValue());
            accepted = addIfPositive(accepted, entry.getKey(), took);
            remaining = addIfPositive(remaining, entry.getKey(), entry.getValue() - took);
        }
        return new PushResult(accepted, remaining);
    }

    /**
     * Clamps the acceptor result to [0, volume].
     *
     * @param acceptor the acceptor function
     * @param type     the goo type
     * @param volume   the offered volume
     * @return the clamped accepted amount
     */
    private static int clampedTake(BiFunction<GooType, Integer, Integer> acceptor,
                                   GooType type, int volume) {
        return Math.max(0, Math.min(acceptor.apply(type, volume), volume));
    }

    /**
     * Adds the amount to the contents only if positive.
     *
     * @param contents the current contents
     * @param type     the goo type
     * @param amount   the amount to add
     * @return the updated contents
     */
    private static GooContents addIfPositive(GooContents contents, GooType type, int amount) {
        return amount > 0 ? contents.withAdded(type, amount) : contents;
    }

    /**
     * Result of a push operation: what was accepted by the destination
     * and what remains in the reservoir.
     *
     * @param accepted  goo contents successfully transferred
     * @param remaining goo contents left in the reservoir
     */
    public record PushResult(GooContents accepted, GooContents remaining) {
    }
}
