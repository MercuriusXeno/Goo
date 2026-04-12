package com.mercuriusxeno.goo.client.machine;

import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Client-side singleton tracking which machine gasket is awaiting a link.
 * Persists across item cycling and look-away. Cleared on: link completion,
 * explicit cancel, or dimension change.
 *
 * <p>Machine HUD renderers query this to show "From: Awaiting Link..."
 * or "To: Awaiting Link..." rows even when the tuner is not held.</p>
 */
public final class TunerAwaitState {

    /** Default slot value when no slot is selected. */
    /** Ellipsis cycle interval in milliseconds. */
    private static final long ELLIPSIS_INTERVAL_MS = 500;
    /** Number of ellipsis phases (., .., ...). */
    private static final int ELLIPSIS_PHASES = 3;
    /** Ellipsis phase 0: single dot. */
    private static final String ELLIPSIS_1 = ".";
    /** Ellipsis phase 1: two dots. */
    private static final String ELLIPSIS_2 = "..";
    /** Ellipsis phase 2: three dots. */
    private static final String ELLIPSIS_3 = "...";
    /** Await text prefix for receiver role. */
    private static final String PREFIX_FROM = "From";
    /** Await text prefix for transmitter role. */
    private static final String PREFIX_TO = "To";
    /** Await text infix. */
    private static final String INFIX_AWAITING = ": Awaiting Link";

    /** Position of the machine block awaiting a link, or null if idle. */
    private static @Nullable BlockPos awaitingPos;

    /** Slot index of the awaiting gasket (-1 for vat/crucible). */
    private static int awaitingSlot = NO_SLOT;

    /** Role of the awaiting gasket. */
    private static @Nullable GasketRole awaitingRole;

    /** Client tick when the await started, for ellipsis animation. */
    private static long startTick;

    private TunerAwaitState() {}

    /**
     * Sets the awaiting state for a specific machine gasket.
     *
     * @param pos the machine's block position
     * @param slot the slot index (-1 for vat/crucible)
     * @param role the role of the awaiting gasket
     */
    public static void set(BlockPos pos, int slot, GasketRole role) {
        awaitingPos = pos;
        awaitingSlot = slot;
        awaitingRole = role;
        startTick = System.currentTimeMillis();
    }

    /** Clears the awaiting state. */
    public static void clear() {
        awaitingPos = null;
        awaitingSlot = NO_SLOT;
        awaitingRole = null;
    }

    /**
     * Returns the awaiting info for a specific machine, or null if no
     * await is active for that machine and slot.
     *
     * @param pos the machine's block position
     * @param slot the slot index (-1 for vat/crucible)
     * @return the await info, or null
     */
    public static @Nullable AwaitInfo getForMachine(BlockPos pos, int slot) {
        if (awaitingPos == null || awaitingRole == null) { return null; }
        if (!awaitingPos.equals(pos) || awaitingSlot != slot) { return null; }
        return new AwaitInfo(awaitingRole, formatEllipsis());
    }

    /**
     * Returns cycling ellipsis text: ".", "..", "..." rotating every 500ms.
     *
     * @return the formatted string
     */
    public static String formatEllipsis() {
        long elapsed = System.currentTimeMillis() - startTick;
        int phase = (int) (elapsed / ELLIPSIS_INTERVAL_MS % ELLIPSIS_PHASES);
        return switch (phase) {
            case 0 -> ELLIPSIS_1;
            case 1 -> ELLIPSIS_2;
            default -> ELLIPSIS_3;
        };
    }

    /**
     * Returns true if any await is currently active.
     *
     * @return true if active
     */
    public static boolean isActive() {
        return awaitingPos != null;
    }

    /**
     * Returns the currently awaiting position, or null.
     *
     * @return the awaitingPos
     */
    public static @Nullable BlockPos getAwaitingPos() {
        return awaitingPos;
    }

    /**
     * Returns the currently awaiting slot.
     *
     * @return the awaitingSlot
     */
    public static int getAwaitingSlot() {
        return awaitingSlot;
    }

    /**
     * Returns the currently awaiting role, or null.
     *
     * @return the awaitingRole
     */
    public static @Nullable GasketRole getAwaitingRole() {
        return awaitingRole;
    }

    /**
     * Await information for a specific machine gasket.
     *
     * @param role the role of the awaiting gasket
     * @param ellipsis the current ellipsis animation text
     */
    public record AwaitInfo(GasketRole role, String ellipsis) {

        /**
         * Formats the await text: "From: Awaiting Link..." or "To: Awaiting Link...".
         *
         * @return the formatted string
         */
        public String formatText() {
            String prefix = role == GasketRole.RECEIVER ? PREFIX_FROM : PREFIX_TO;
            return prefix + INFIX_AWAITING + ellipsis;
        }
    }
}
