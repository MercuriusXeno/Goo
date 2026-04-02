package com.mercuriusxeno.goo.block;

import java.util.Set;

/**
 * Block entity that accepts canisters attached to its top face via copper
 * fittings. Canister placement validates the block below; blocks implementing
 * this interface are valid attachment targets.
 */
public interface ICanisterAttachable {

    /** Returns the max number of canisters that can attach to the top face. */
    int maxTopAttachments();

    /** Returns the current number of canisters attached to the top face. */
    int currentTopAttachments();

    /** Returns true if another canister can be attached to the top face. */
    default boolean canAttachOnTop() {
        return currentTopAttachments() < maxTopAttachments();
    }

    /**
     * Returns the set of slot indices (0-8 in a 3x3 grid) that are allowed
     * for canister placement on this block's top face. Implementations may
     * constrain which positions are valid based on facing, upgrades, etc.
     *
     * <p>Default returns all 9 slots (no constraint).</p>
     */
    default Set<Integer> allowedSlots() {
        return Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
    }
}
