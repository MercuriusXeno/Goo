package com.mercuriusxeno.goo.block.canister;

import net.minecraft.core.Direction;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Pure layout constants and hit detection for the 3x3 canister grid.
 * Extracted from CanisterBlock to enable unit testing without Minecraft
 * class-init (Block.box triggers bootstrap).
 *
 * <p>Layout: 1px gap + 4px canister + 1px gap, repeated 3 times = 16px per axis.
 * Each canister is 4x12x4 px (body 10px + 2x1px gaskets).</p>
 */
public final class CanisterSlotLayout {

    /**
     * Number of slots in the 3x3 grid.
     */
    public static final int SLOT_COUNT = 9;
    /**
     * Hit detection threshold in pixels: max distance from slot center.
     */
    public static final float HIT_THRESHOLD = 4.0f;
    /**
     * Slot center positions in pixel coordinates (x, z).
     * Index matches slot number: 0=NW, 1=N, 2=NE, 3=W, 4=Center, 5=E, 6=SW, 7=S, 8=SE.
     */
    public static final float[][] SLOT_CENTERS = {
            {3, 3}, // slot 0 (NW)
            {8, 3}, // slot 1 (N)
            {13, 3}, // slot 2 (NE)
            {3, 8}, // slot 3 (W)
            {8, 8}, // slot 4 (Center)
            {13, 8}, // slot 5 (E)
            {3, 13}, // slot 6 (SW)
            {8, 13}, // slot 7 (S)
            {13, 13}, // slot 8 (SE)
    };
    /** Pixels per block; converts pixel-space coords to block-space. */
    private static final float BLOCK_PIXELS = 16f;
    /**
     * Slot centers in block-local coordinates ({@code SLOT_CENTERS / 16}).
     * Precomputed for renderers that consume block-coord positions.
     */
    public static final float[][] SLOT_CENTERS_BLOCK = buildBlockCenters();

    private static float[][] buildBlockCenters() {
        float[][] block = new float[SLOT_COUNT][];
        for (int i = 0; i < SLOT_COUNT; i++) {
            block[i] = new float[]{
                    SLOT_CENTERS[i][0] / BLOCK_PIXELS,
                    SLOT_CENTERS[i][1] / BLOCK_PIXELS
            };
        }
        return block;
    }
    /** Sentinel value: no matching slot found. */
    /**
     * Number of columns (and rows) in the grid.
     */
    private static final int GRID_SIZE = 3;
    /**
     * Maximum valid grid index (GRID_SIZE - 1).
     */
    private static final int MAX_GRID_INDEX = 2;
    /**
     * Grid column boundary: pixels below this are column 0.
     */
    private static final float COL_BOUNDARY_LOW = 16.0f / 3.0f;
    /**
     * Grid column boundary: pixels at or above this are column 2.
     */
    private static final float COL_BOUNDARY_HIGH = 2.0f * 16.0f / 3.0f;

    private CanisterSlotLayout() {
    }

    /**
     * Finds the nearest slot center to the given pixel coordinates.
     * Returns -1 if the closest center is beyond the hit threshold.
     *
     * @param px pixel-space X coordinate
     * @param pz pixel-space Z coordinate
     * @return slot index 0-8, or -1 if beyond threshold
     */
    public static int nearestSlot(float px, float pz) {
        int best = NO_SLOT;
        float bestDistSq = Float.MAX_VALUE;
        for (int i = 0; i < SLOT_COUNT; i++) {
            float distSq = squaredDistToSlot(i, px, pz);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = i;
            }
        }
        return withinThreshold(best, bestDistSq);
    }

    /**
     * Returns the slot index if the squared distance is within the hit threshold, else NO_SLOT.
     *
     * @param slot   the candidate slot index, or NO_SLOT
     * @param distSq the squared distance to the slot center
     * @return the slot index if within threshold, otherwise NO_SLOT (-1)
     */
    private static int withinThreshold(int slot, float distSq) {
        return distSq <= HIT_THRESHOLD * HIT_THRESHOLD ? slot : NO_SLOT;
    }

    /**
     * Returns the squared distance from a point to a slot's center.
     *
     * @param slot the slot index
     * @param px   pixel-space X coordinate
     * @param pz   pixel-space Z coordinate
     * @return squared distance in pixel space
     */
    private static float squaredDistToSlot(int slot, float px, float pz) {
        float dx = px - SLOT_CENTERS[slot][0];
        float dz = pz - SLOT_CENTERS[slot][1];
        return dx * dx + dz * dz;
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
        return resolveGridSlot(face, pixelToRow(pz), pixelToColumn(px));
    }

    /**
     * Resolves the grid slot for a face click given the base row and column.
     *
     * @param face the clicked face
     * @param row  the grid row (0-2)
     * @param col  the grid column (0-2)
     * @return slot index 0-8
     */
    private static int resolveGridSlot(Direction face, int row, int col) {
        return switch (face) {
            case NORTH -> clampGrid(row - 1) * GRID_SIZE + col;
            case SOUTH -> clampGrid(row + 1) * GRID_SIZE + col;
            case WEST -> row * GRID_SIZE + clampGrid(col - 1);
            case EAST -> row * GRID_SIZE + clampGrid(col + 1);
            default -> row * GRID_SIZE + col;
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
        float dx = px - SLOT_CENTERS[occupiedSlot][0];
        float dz = pz - SLOT_CENTERS[occupiedSlot][1];
        int row = occupiedSlot / GRID_SIZE;
        int col = occupiedSlot % GRID_SIZE;
        return leanToAdjacent(dx, dz, row, col);
    }

    /**
     * Picks the adjacent slot based on cursor lean direction from the occupied center.
     *
     * @param dx  horizontal offset from slot center
     * @param dz  vertical offset from slot center
     * @param row the grid row of the occupied slot
     * @param col the grid column of the occupied slot
     * @return adjacent slot index 0-8
     */
    private static int leanToAdjacent(float dx, float dz, int row, int col) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return row * GRID_SIZE + clampGrid(dx > 0 ? col + 1 : col - 1);
        }
        return clampGrid(dz > 0 ? row + 1 : row - 1) * GRID_SIZE + col;
    }

    /**
     * Clamps a grid index to the valid range [0, 2].
     *
     * @param index the grid index to clamp
     * @return the clamped index
     */
    private static int clampGrid(int index) {
        return Math.max(0, Math.min(MAX_GRID_INDEX, index));
    }

    /**
     * Maps a pixel coordinate to a grid column index (0, 1, or 2).
     *
     * @param px the pixel-space X coordinate
     * @return column index (0, 1, or 2)
     */
    private static int pixelToColumn(float px) {
        if (px < COL_BOUNDARY_LOW) {
            return 0;
        }
        if (px >= COL_BOUNDARY_HIGH) {
            return MAX_GRID_INDEX;
        }
        return 1;
    }

    /**
     * Maps a pixel coordinate to a grid row index (0, 1, or 2).
     *
     * @param pz the pixel-space Z coordinate
     * @return row index (0, 1, or 2)
     */
    private static int pixelToRow(float pz) {
        if (pz < COL_BOUNDARY_LOW) {
            return 0;
        }
        if (pz >= COL_BOUNDARY_HIGH) {
            return MAX_GRID_INDEX;
        }
        return 1;
    }
}
