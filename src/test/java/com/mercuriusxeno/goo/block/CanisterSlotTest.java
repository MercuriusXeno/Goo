package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterSlotLayout;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.function.IntPredicate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the canister 3x3 grid hit detection.
 * Uses CanisterSlotLayout directly to avoid Minecraft class-init poisoning.
 */
class CanisterSlotTest {

    /**
     * Slot centers in pixel coordinates (from CanisterSlotLayout).
     */
    private static final float[][] CENTERS = CanisterSlotLayout.SLOT_CENTERS;

    @Test
    void exactCenter_returnsCorrectSlot() {
        for (int i = 0; i < 9; i++) {
            assertEquals(i, CanisterSlotLayout.nearestSlot(CENTERS[i][0], CENTERS[i][1]),
                    "Exact center of slot " + i);
        }
    }

    @Test
    void withinThreshold_returnsSlot() {
        // 1px offset from center of slot 4 (8,8) in each direction
        assertEquals(4, CanisterSlotLayout.nearestSlot(9, 8));
        assertEquals(4, CanisterSlotLayout.nearestSlot(7, 8));
        assertEquals(4, CanisterSlotLayout.nearestSlot(8, 9));
        assertEquals(4, CanisterSlotLayout.nearestSlot(8, 7));
    }

    @Test
    void cornerOfSlot_withinThreshold() {
        // Slot 0 corner: (1,1) is sqrt(8) ~ 2.83 from center (3,3), within threshold of 4
        assertEquals(0, CanisterSlotLayout.nearestSlot(1, 1));
    }

    @Test
    void midpointBetweenSlots_returnsNearest() {
        // Midpoint between slot 0 (3,3) and slot 1 (8,3): x=5.5
        // Distance to slot 0: 2.5, distance to slot 1: 2.5 -> either is valid
        int result = CanisterSlotLayout.nearestSlot(5.5f, 3);
        assertTrue(result == 0 || result == 1,
                "Midpoint should resolve to slot 0 or 1, got " + result);
    }

    @Test
    void beyondThreshold_returnsNegativeOne() {
        // Far outside the grid
        assertEquals(-1, CanisterSlotLayout.nearestSlot(-5, -5));
        assertEquals(-1, CanisterSlotLayout.nearestSlot(20, 20));
    }

    @Test
    void edgeOfBlock_nearSlot_returnsSlot() {
        // (1, 3) is 2px from slot 0 center (3,3), well within threshold
        assertEquals(0, CanisterSlotLayout.nearestSlot(1, 3));
        // (15, 13) is 2px from slot 8 center (13,13), within threshold
        assertEquals(8, CanisterSlotLayout.nearestSlot(15, 13));
    }

    @Test
    void allNineSlots_distinctHits() {
        // Verify each slot center maps to a unique slot
        boolean[] seen = new boolean[9];
        for (int i = 0; i < 9; i++) {
            int slot = CanisterSlotLayout.nearestSlot(CENTERS[i][0], CENTERS[i][1]);
            assertFalse(seen[slot], "Slot " + slot + " mapped by multiple centers");
            seen[slot] = true;
        }
        for (int i = 0; i < 9; i++) {
            assertTrue(seen[i], "Slot " + i + " never mapped");
        }
    }

    // --- isValidSlot tests ---

    @Test
    void isValidSlot_validRange() {
        for (int i = 0; i < 9; i++) {
            assertTrue(CanisterBlock.isValidSlot(i), "Slot " + i + " should be valid");
        }
    }

    @Test
    void isValidSlot_outOfRange() {
        assertFalse(CanisterBlock.isValidSlot(-1));
        assertFalse(CanisterBlock.isValidSlot(9));
        assertFalse(CanisterBlock.isValidSlot(Integer.MIN_VALUE));
        assertFalse(CanisterBlock.isValidSlot(Integer.MAX_VALUE));
    }

    // --- findSlot tests ---

    @Test
    void findSlot_prefersHitSlot() {
        IntPredicate allMatch = i -> true;
        assertEquals(5, GooBlockInteraction.findSlot(5, 9, allMatch));
    }

    @Test
    void findSlot_fallsBackToFirstMatch() {
        IntPredicate onlySlot3 = i -> i == 3;
        assertEquals(3, GooBlockInteraction.findSlot(-1, 9, onlySlot3));
    }

    @Test
    void findSlot_hitSlotFailsFallsThrough() {
        IntPredicate onlySlot7 = i -> i == 7;
        assertEquals(7, GooBlockInteraction.findSlot(2, 9, onlySlot7));
    }

    @Test
    void findSlot_noMatch() {
        IntPredicate noneMatch = i -> false;
        assertEquals(-1, GooBlockInteraction.findSlot(-1, 9, noneMatch));
        assertEquals(-1, GooBlockInteraction.findSlot(4, 9, noneMatch));
    }

    @Test
    void findSlot_negativeHitSlot_skipsPreference() {
        IntPredicate onlySlot0 = i -> i == 0;
        assertEquals(0, GooBlockInteraction.findSlot(-1, 9, onlySlot0));
    }

    @Test
    void findSlot_respectsMaxSlots() {
        IntPredicate onlySlot7 = i -> i == 7;
        assertEquals(-1, GooBlockInteraction.findSlot(-1, 5, onlySlot7),
                "Slot 7 should not be found when maxSlots is 5");
        assertEquals(7, GooBlockInteraction.findSlot(-1, 8, onlySlot7),
                "Slot 7 should be found when maxSlots is 8");
    }

    // --- placementSlot tests ---

    @Test
    void topFace_resolvesFullGrid() {
        // Top/bottom face uses both axes: each grid cell maps to correct slot
        assertEquals(0, CanisterSlotLayout.placementSlot(Direction.UP, 2, 2));
        assertEquals(1, CanisterSlotLayout.placementSlot(Direction.UP, 8, 2));
        assertEquals(2, CanisterSlotLayout.placementSlot(Direction.UP, 14, 2));
        assertEquals(3, CanisterSlotLayout.placementSlot(Direction.UP, 2, 8));
        assertEquals(4, CanisterSlotLayout.placementSlot(Direction.UP, 8, 8));
        assertEquals(5, CanisterSlotLayout.placementSlot(Direction.UP, 14, 8));
        assertEquals(6, CanisterSlotLayout.placementSlot(Direction.UP, 2, 14));
        assertEquals(7, CanisterSlotLayout.placementSlot(Direction.UP, 8, 14));
        assertEquals(8, CanisterSlotLayout.placementSlot(Direction.UP, 14, 14));
    }

    @Test
    void bottomFace_resolvesFullGrid() {
        assertEquals(4, CanisterSlotLayout.placementSlot(Direction.DOWN, 8, 8));
        assertEquals(0, CanisterSlotLayout.placementSlot(Direction.DOWN, 1, 1));
        assertEquals(8, CanisterSlotLayout.placementSlot(Direction.DOWN, 15, 15));
    }

    @Test
    void northFace_offsetsRowMinusOne() {
        // North face (Z-): row offset -1 from hit position, column from X
        assertEquals(0, CanisterSlotLayout.placementSlot(Direction.NORTH, 2, 8));
        assertEquals(1, CanisterSlotLayout.placementSlot(Direction.NORTH, 8, 8));
        assertEquals(2, CanisterSlotLayout.placementSlot(Direction.NORTH, 14, 8));
        // Z=14 maps to row 2, offset -1 = row 1: slot = 1*3 + 1 = 4
        assertEquals(4, CanisterSlotLayout.placementSlot(Direction.NORTH, 8, 14));
    }

    @Test
    void southFace_offsetsRowPlusOne() {
        // South face (Z+): row offset +1 from hit position, column from X
        assertEquals(6, CanisterSlotLayout.placementSlot(Direction.SOUTH, 2, 8));
        assertEquals(7, CanisterSlotLayout.placementSlot(Direction.SOUTH, 8, 8));
        assertEquals(8, CanisterSlotLayout.placementSlot(Direction.SOUTH, 14, 8));
    }

    @Test
    void westFace_offsetsColMinusOne() {
        // West face (X-): col offset -1 from hit position, row from Z
        assertEquals(0, CanisterSlotLayout.placementSlot(Direction.WEST, 8, 2));
        assertEquals(3, CanisterSlotLayout.placementSlot(Direction.WEST, 8, 8));
        assertEquals(6, CanisterSlotLayout.placementSlot(Direction.WEST, 8, 14));
        // X=14 maps to col 2, offset -1 = col 1: slot = 1*3 + 1 = 4
        assertEquals(4, CanisterSlotLayout.placementSlot(Direction.WEST, 14, 8));
    }

    @Test
    void eastFace_offsetsColPlusOne() {
        // East face (X+): col offset +1 from hit position, row from Z
        assertEquals(2, CanisterSlotLayout.placementSlot(Direction.EAST, 8, 2));
        assertEquals(5, CanisterSlotLayout.placementSlot(Direction.EAST, 8, 8));
        assertEquals(8, CanisterSlotLayout.placementSlot(Direction.EAST, 8, 14));
    }

    @Test
    void adjacency_clampsAtEdge() {
        // North face at row 0: offset -1 clamps to 0
        assertEquals(1, CanisterSlotLayout.placementSlot(Direction.NORTH, 8, 2));
        // West face at col 0: offset -1 clamps to 0
        assertEquals(3, CanisterSlotLayout.placementSlot(Direction.WEST, 2, 8));
        // South face at row 2: offset +1 clamps to 2
        assertEquals(7, CanisterSlotLayout.placementSlot(Direction.SOUTH, 8, 14));
        // East face at col 2: offset +1 clamps to 2
        assertEquals(5, CanisterSlotLayout.placementSlot(Direction.EAST, 14, 8));
    }

    @Test
    void placementSlot_gridBoundaries() {
        // Boundary at 5.33px: just below is col/row 0, at boundary is col/row 1
        float boundary = 16.0f / 3.0f;
        assertEquals(0, CanisterSlotLayout.placementSlot(Direction.UP, boundary - 0.01f, boundary - 0.01f));
        assertEquals(4, CanisterSlotLayout.placementSlot(Direction.UP, boundary, boundary));
        // Boundary at 10.67px: just below is col/row 1, at boundary is col/row 2
        float upperBoundary = 2.0f * 16.0f / 3.0f;
        assertEquals(4, CanisterSlotLayout.placementSlot(Direction.UP, upperBoundary - 0.01f, upperBoundary - 0.01f));
        assertEquals(8, CanisterSlotLayout.placementSlot(Direction.UP, upperBoundary, upperBoundary));
    }
}
