package com.mercuriusxeno.goo.block;

import net.minecraft.core.Direction;

/**
 * Pure layout constants and hit detection for the 3x3 canister grid.
 * Extracted from CanisterBlock to enable unit testing without Minecraft
 * class-init (Block.box triggers bootstrap).
 *
 * <p>Layout: 1px gap + 4px canister + 1px gap, repeated 3 times = 16px per axis.
 * Each canister is 4x12x4 px (body 10px + 2x1px gaskets).</p>
 */
public final class CanisterSlotLayout {

    private CanisterSlotLayout() {}

    /** Number of slots in the 3x3 grid. */
    public static final int SLOT_COUNT = 9;

    /** Hit detection threshold in pixels: max distance from slot center. */
    public static final float HIT_THRESHOLD = 4.0f;

    /** Grid column boundary: pixels below this are column 0. */
    private static final float COL_BOUNDARY_LOW = 16.0f / 3.0f;

    /** Grid column boundary: pixels at or above this are column 2. */
    private static final float COL_BOUNDARY_HIGH = 2.0f * 16.0f / 3.0f;

    /**
     * Slot center positions in pixel coordinates (x, z).
     * Index matches slot number: 0=NW, 1=N, 2=NE, 3=W, 4=Center, 5=E, 6=SW, 7=S, 8=SE.
     */
    public static final float[][] SLOT_CENTERS = {
        { 3,  3}, // slot 0 (NW)
        { 8,  3}, // slot 1 (N)
        {13,  3}, // slot 2 (NE)
        { 3,  8}, // slot 3 (W)
        { 8,  8}, // slot 4 (Center)
        {13,  8}, // slot 5 (E)
        { 3, 13}, // slot 6 (SW)
        { 8, 13}, // slot 7 (S)
        {13, 13}, // slot 8 (SE)
    };

    /**
     * Finds the nearest slot center to the given pixel coordinates.
     * Returns -1 if the closest center is beyond the hit threshold.
     */
    public static int nearestSlot(float px, float pz) {
        int best = -1;
        float bestDist = HIT_THRESHOLD * HIT_THRESHOLD;
        for (int i = 0; i < SLOT_COUNT; i++) {
            float dx = px - SLOT_CENTERS[i][0];
            float dz = pz - SLOT_CENTERS[i][1];
            float dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    /**
     * Resolves the target slot for canister placement based on the clicked block face
     * and pixel-space hit coordinates. Side faces lock one axis to the nearest row or
     * column; top/bottom faces use both axes to pick the grid cell.
     *
     * @param face the block face that was clicked
     * @param px   block-local X coordinate in pixel space (0-16)
     * @param pz   block-local Z coordinate in pixel space (0-16)
     * @return slot index 0-8
     */
    public static int placementSlot(Direction face, float px, float pz) {
        int row = pixelToRow(pz);
        int col = pixelToColumn(px);

        return switch (face) {
            case NORTH -> clampGrid(row - 1) * 3 + col;
            case SOUTH -> clampGrid(row + 1) * 3 + col;
            case WEST  -> row * 3 + clampGrid(col - 1);
            case EAST  -> row * 3 + clampGrid(col + 1);
            default    -> row * 3 + col;          // UP/DOWN: full grid
        };
    }

    /**
     * Given an occupied slot, picks the adjacent slot in the direction the
     * cursor leans from the slot center. Works for any face - the lean
     * direction is determined purely by cursor offset from slot center.
     * Used as a fallback when {@link #placementSlot} returns an occupied slot.
     *
     * @param occupiedSlot the slot that was hit
     * @param px           block-local X in pixel space
     * @param pz           block-local Z in pixel space
     * @return adjacent slot index 0-8
     */
    public static int adjacentByCursorLean(int occupiedSlot, float px, float pz) {
        float cx = SLOT_CENTERS[occupiedSlot][0];
        float cz = SLOT_CENTERS[occupiedSlot][1];
        float dx = px - cx;
        float dz = pz - cz;
        int row = occupiedSlot / 3;
        int col = occupiedSlot % 3;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return row * 3 + clampGrid(dx > 0 ? col + 1 : col - 1);
        }
        return clampGrid(dz > 0 ? row + 1 : row - 1) * 3 + col;
    }

    /** Clamps a grid index to the valid range [0, 2]. */
    private static int clampGrid(int index) {
        return Math.max(0, Math.min(2, index));
    }

    /** Maps a pixel coordinate to a grid column index (0, 1, or 2). */
    private static int pixelToColumn(float px) {
        if (px < COL_BOUNDARY_LOW)  return 0;
        if (px >= COL_BOUNDARY_HIGH) return 2;
        return 1;
    }

    /** Maps a pixel coordinate to a grid row index (0, 1, or 2). */
    private static int pixelToRow(float pz) {
        if (pz < COL_BOUNDARY_LOW)  return 0;
        if (pz >= COL_BOUNDARY_HIGH) return 2;
        return 1;
    }
}
