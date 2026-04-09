package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.CanisterSlotLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.Set;

/**
 * Pure-static slot resolution for canister grids.
 * Maps world-space hit locations to 3x3 canister slot indices,
 * with fallback and constraint logic for insertion.
 */
public final class CanisterSlotResolver {

    /** Pixels per block: converts block-space [0..1] to pixel-space [0..16]. */
    static final double PIXELS_PER_BLOCK = 16.0;
    /** Sentinel value: no empty slot found. */
    static final int NO_SLOT = -1;

    private CanisterSlotResolver() {
        // static utility
    }

    /**
     * Determines which canister grid cell a hit point targets for placement.
     *
     * @param hitLocation the contact point from BlockHitResult.getLocation()
     * @param pos         the canister block position
     * @param face        the face that was hit
     * @return slot index 0-8
     */
    public static int projectToCanisterSlot(Vec3 hitLocation, BlockPos pos, Direction face) {
        float px = (float) ((hitLocation.x - pos.getX()) * PIXELS_PER_BLOCK);
        float pz = (float) ((hitLocation.z - pos.getZ()) * PIXELS_PER_BLOCK);
        return CanisterSlotLayout.placementSlot(face, px, pz);
    }

    /**
     * Resolves the best empty slot for canister insertion.
     *
     * @param hitLocation the contact point from BlockHitResult.getLocation()
     * @param pos         the canister block position
     * @param face        the face that was hit
     * @param be          the canister block entity to check occupancy
     * @return empty slot index 0-8, or -1 if all full
     */
    public static int resolveInsertionSlot(
            Vec3 hitLocation, BlockPos pos, Direction face, CanisterBlockEntity be) {
        float px = (float) ((hitLocation.x - pos.getX()) * PIXELS_PER_BLOCK);
        float pz = (float) ((hitLocation.z - pos.getZ()) * PIXELS_PER_BLOCK);
        int slot = CanisterSlotLayout.placementSlot(face, px, pz);

        if (slot >= 0 && be.getCanister(slot).isEmpty()) { return slot; }

        return fallbackSlot(slot, px, pz, be);
    }

    /**
     * Tries the adjacent slot by cursor lean, then falls back to the first empty.
     *
     * @param slot the targeted slot index
     * @param px   the x pixel coordinate within the block
     * @param pz   the z pixel coordinate within the block
     * @param be   the canister block entity
     * @return the fallback slot index, or -1 if all full
     */
    private static int fallbackSlot(int slot, float px, float pz, CanisterBlockEntity be) {
        if (slot >= 0) {
            int adjacent = CanisterSlotLayout.adjacentByCursorLean(slot, px, pz);
            if (be.getCanister(adjacent).isEmpty()) { return adjacent; }
        }
        return findFirstEmpty(be);
    }

    /**
     * Resolves the best empty slot, then constrains to allowed slots for
     * the attachment target below.
     *
     * @param hitLocation the contact point from the hit result
     * @param pos         the canister block position
     * @param face        the face that was hit
     * @param be          the canister block entity
     * @return constrained slot index, or -1 if none available
     */
    static int resolveAndConstrain(
            Vec3 hitLocation, BlockPos pos, Direction face, CanisterBlockEntity be) {
        int slot = resolveInsertionSlot(hitLocation, pos, face, be);
        net.minecraft.world.level.Level level = be.getLevel();
        if (slot >= 0 && CanisterPlacementValidator.isSlotAllowed(level, pos, slot)) { return slot; }

        Set<Integer> allowed = CanisterPlacementValidator.getAllowedSlots(level, pos);
        if (allowed == null) { return slot; }
        return findFirstAllowedEmpty(be, allowed);
    }

    /**
     * Finds the first empty slot in the block entity.
     *
     * @param be the canister block entity
     * @return the first empty slot index, or -1 if all full
     */
    private static int findFirstEmpty(CanisterBlockEntity be) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (be.getCanister(i).isEmpty()) { return i; }
        }
        return NO_SLOT;
    }

    /**
     * Finds the first empty slot that is in the allowed set.
     *
     * @param be      the canister block entity
     * @param allowed the set of allowed slot indices
     * @return the first allowed empty slot, or -1 if none
     */
    private static int findFirstAllowedEmpty(CanisterBlockEntity be, Set<Integer> allowed) {
        for (int slot : allowed) {
            if (be.getCanister(slot).isEmpty()) { return slot; }
        }
        return NO_SLOT;
    }
}
