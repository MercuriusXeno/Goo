package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.item.GasketRole;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Client-side singleton tracking which machine gasket is awaiting a link.
 * Persists across item cycling and look-away. Cleared on: link completion,
 * explicit cancel, or dimension change.
 *
 * <p>Machine HUD renderers query this to show "From: Awaiting Link..."
 * or "To: Awaiting Link..." rows even when the tuner is not held.</p>
 */
public final class TunerAwaitState {

    private TunerAwaitState() {}

    /** Position of the machine block awaiting a link, or null if idle. */
    private static @Nullable BlockPos awaitingPos = null;

    /** Slot index of the awaiting gasket (-1 for vat/crucible). */
    private static int awaitingSlot = -1;

    /** Role of the awaiting gasket. */
    private static @Nullable GasketRole awaitingRole = null;

    /** Client tick when the await started, for ellipsis animation. */
    private static long startTick = 0;

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
        awaitingSlot = -1;
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
        if (awaitingPos == null || awaitingRole == null) return null;
        if (!awaitingPos.equals(pos) || awaitingSlot != slot) return null;
        return new AwaitInfo(awaitingRole, formatEllipsis());
    }

    /**
     * Returns cycling ellipsis text: ".", "..", "..." rotating every 500ms.
     */
    public static String formatEllipsis() {
        long elapsed = System.currentTimeMillis() - startTick;
        int phase = (int) ((elapsed / 500) % 3);
        return switch (phase) {
            case 0 -> ".";
            case 1 -> "..";
            default -> "...";
        };
    }

    /** Returns true if any await is currently active. */
    public static boolean isActive() {
        return awaitingPos != null;
    }

    /** Returns the currently awaiting position, or null. */
    public static @Nullable BlockPos getAwaitingPos() {
        return awaitingPos;
    }

    /** Returns the currently awaiting slot. */
    public static int getAwaitingSlot() {
        return awaitingSlot;
    }

    /** Returns the currently awaiting role, or null. */
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

        /** Formats the await text: "From: Awaiting Link..." or "To: Awaiting Link...". */
        public String formatText() {
            String prefix = role == GasketRole.RECEIVER ? "From" : "To";
            return prefix + ": Awaiting Link" + ellipsis;
        }
    }
}
