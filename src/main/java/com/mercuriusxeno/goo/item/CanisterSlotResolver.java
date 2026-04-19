package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.CanisterSlotLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Pure-static slot resolution for canister grids.
 * Maps world-space hit locations to 3x3 canister slot indices,
 * with fallback and constraint logic for insertion.
 */
public final class CanisterSlotResolver {

    /** Pixels per block: converts block-space [0..1] to pixel-space [0..16]. */
    static final double PIXELS_PER_BLOCK = 16.0;
    /** Sentinel value: no empty slot found. */

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
     * Resolves the best empty slot for canister insertion, enforcing
     * placement constraints at every step. Fallback order:
     * 1) direct-hit slot (if empty and allowed),
     * 2) adjacent slot by cursor lean (if empty and allowed),
     * 3) first empty allowed slot in the grid.
     *
     * @param hitLocation the contact point from BlockHitResult.getLocation()
     * @param pos         the canister block position
     * @param face        the face that was hit
     * @param be          the canister block entity to check occupancy
     * @return constrained empty slot index 0-8, or -1 if none available
     */
    public static int resolveAndConstrain(
            Vec3 hitLocation, BlockPos pos, Direction face, CanisterBlockEntity be) {
        float px = (float) ((hitLocation.x - pos.getX()) * PIXELS_PER_BLOCK);
        float pz = (float) ((hitLocation.z - pos.getZ()) * PIXELS_PER_BLOCK);
        int slot = CanisterSlotLayout.placementSlot(face, px, pz);
        Level level = be.getLevel();

        int resolved = resolveDirectOrAdjacent(be, level, pos, slot, px, pz);
        if (resolved != NO_SLOT) { return resolved; }

        return findFirstAllowedEmpty(be, level, pos);
    }

    /**
     * Tries the direct-hit slot, then the adjacent lean slot.
     *
     * @param be    the canister block entity
     * @param level the current level
     * @param pos   the canister block position
     * @param slot  the direct-hit slot index
     * @param px    pixel X coordinate on the block face
     * @param pz    pixel Z coordinate on the block face
     * @return the resolved slot index, or NO_SLOT if neither is available
     */
    private static int resolveDirectOrAdjacent(CanisterBlockEntity be, Level level,
            BlockPos pos, int slot, float px, float pz) {
        if (slot < 0) { return NO_SLOT; }
        if (isEmptyAndAllowed(be, level, pos, slot)) { return slot; }
        int adjacent = CanisterSlotLayout.adjacentByCursorLean(slot, px, pz);
        if (isEmptyAndAllowed(be, level, pos, adjacent)) { return adjacent; }
        return NO_SLOT;
    }

    /**
     * Returns true if the slot is empty and passes placement constraints.
     *
     * @param be    the canister block entity
     * @param level the current level
     * @param pos   the canister block position
     * @param slot  the slot index to check
     * @return true if the slot is empty and allowed
     */
    private static boolean isEmptyAndAllowed(CanisterBlockEntity be, Level level,
            BlockPos pos, int slot) {
        return be.getCanister(slot).isEmpty()
                && CanisterPlacementValidator.isSlotAllowed(level, pos, slot);
    }

    /**
     * Finds the first empty slot that passes placement constraints.
     *
     * @param be    the canister block entity
     * @param level the current level
     * @param pos   the canister block position
     * @return the first allowed empty slot, or -1 if none
     */
    private static int findFirstAllowedEmpty(CanisterBlockEntity be, Level level, BlockPos pos) {
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (be.getCanister(i).isEmpty()
                    && CanisterPlacementValidator.isSlotAllowed(level, pos, i)) {
                return i;
            }
        }
        return NO_SLOT;
    }
}
