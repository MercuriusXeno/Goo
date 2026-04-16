package com.mercuriusxeno.goo.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OmniblobQuickCraft pure utility: charitable and greedy distribution math.
 * These tests do not require Minecraft class init - they only test pure logic.
 */
class OmniblobQuickCraftTest {

    // -- charitablePerSlot --

    /** Even split: 10,000 mB across 5 slots = 2,000 mB each. */
    @Test
    void charitable_evenSplit() {
        assertEquals(2000, OmniblobQuickCraft.charitablePerSlot(10_000, 5));
    }

    /** Uneven split floors: 7,777 mB across 3 slots = 2,592 mB each (1 mB remainder). */
    @Test
    void charitable_unevenFloors() {
        assertEquals(2592, OmniblobQuickCraft.charitablePerSlot(7_777, 3));
    }

    /** Single slot gets everything. */
    @Test
    void charitable_singleSlot() {
        assertEquals(5000, OmniblobQuickCraft.charitablePerSlot(5000, 1));
    }

    /** Zero slots returns zero (defensive). */
    @Test
    void charitable_zeroSlots() {
        assertEquals(0, OmniblobQuickCraft.charitablePerSlot(5000, 0));
    }

    /** Volume less than slot count: each slot gets zero. */
    @Test
    void charitable_volumeLessThanSlots() {
        assertEquals(0, OmniblobQuickCraft.charitablePerSlot(2, 5));
    }

    /** Large volume: 100,000 mB across 4 slots = 25,000 each. */
    @Test
    void charitable_largeVolume() {
        assertEquals(25_000, OmniblobQuickCraft.charitablePerSlot(100_000, 4));
    }

    // -- greedyPerSlot --

    /** Greedy always returns one blob (1,000 mB). */
    @Test
    void greedy_returnsOneBlob() {
        assertEquals(1000, OmniblobQuickCraft.greedyPerSlot());
    }

    /** Greedy matches BlobStacks constant. */
    @Test
    void greedy_matchesMbPerBlob() {
        assertEquals(BlobStacks.MB_PER_BLOB, OmniblobQuickCraft.greedyPerSlot());
    }
}
