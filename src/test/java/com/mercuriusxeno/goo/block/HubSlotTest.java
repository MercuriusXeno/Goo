package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.hub.HubBlock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link HubBlock#nearestSlot(double, double)}.
 */
class HubSlotTest {

    /**
     * Clicking directly on each slot center should return that slot.
     */
    @Test
    void exactSlotCentersReturnCorrectSlot() {
        double[][] centers = {
                {8.0, 2.0},  // slot 0 (N)
                {13.0, 3.0},  // slot 1 (NE)
                {14.0, 8.0},  // slot 2 (E)
                {13.0, 13.0},  // slot 3 (SE)
                {8.0, 14.0},  // slot 4 (S)
                {3.0, 13.0},  // slot 5 (SW)
                {2.0, 8.0},  // slot 6 (W)
                {3.0, 3.0},  // slot 7 (NW)
        };
        for (int i = 0; i < centers.length; i++) {
            assertEquals(i, HubBlock.nearestSlot(centers[i][0], centers[i][1]),
                    "Exact center should match slot " + i);
        }
    }

    /**
     * Block center (8,8) is 6px from nearest slot, beyond the 4px threshold.
     */
    @Test
    void blockCenterReturnsMinus1() {
        assertEquals(-1, HubBlock.nearestSlot(8.0, 8.0));
    }

    /**
     * A point slightly offset from slot 0 should still resolve to slot 0.
     */
    @Test
    void nearSlot0ResolvesToSlot0() {
        assertEquals(0, HubBlock.nearestSlot(8.5, 3.5));
    }

    /**
     * Points on opposite sides of the midpoint between slots 5 and 7 should resolve correctly.
     */
    @Test
    void westSideMidpointResolvesToNearer() {
        // Slot 6 (W) at (2,8), slot 7 (NW) at (3,3)
        assertEquals(7, HubBlock.nearestSlot(2.5, 4.0));
        assertEquals(6, HubBlock.nearestSlot(2.0, 7.0));
    }

    /**
     * Far corner of the block should be beyond the threshold.
     */
    @Test
    void farCornerReturnsMinus1() {
        assertEquals(-1, HubBlock.nearestSlot(0.0, 0.0));
    }
}
