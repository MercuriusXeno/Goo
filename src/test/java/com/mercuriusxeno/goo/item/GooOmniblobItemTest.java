package com.mercuriusxeno.goo.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for omniblob-related logic: extract count computation.
 * Uses BlobStacks.computeExtractCount which is pure and testable without Minecraft.
 */
class GooOmniblobItemTest {

    // -- computeExtractCount --

    /** Sub-blob volume extracts nothing. */
    @Test
    void extractCount_subBlob() {
        assertEquals(0, BlobStacks.computeExtractCount(999L, false));
    }

    /** Exactly 1 blob without shift extracts 1. */
    @Test
    void extractCount_oneBlob_noShift() {
        assertEquals(1, BlobStacks.computeExtractCount(1000L, false));
    }

    /** Multiple blobs without shift extracts 1. */
    @Test
    void extractCount_manyBlobs_noShift() {
        assertEquals(1, BlobStacks.computeExtractCount(50_000L, false));
    }

    /** With shift, extract all whole blobs up to 64. */
    @Test
    void extractCount_shift_underMax() {
        assertEquals(10, BlobStacks.computeExtractCount(10_000L, true));
    }

    /** With shift and exactly 64K, extract 64. */
    @Test
    void extractCount_shift_exactly64() {
        assertEquals(64, BlobStacks.computeExtractCount(64_000L, true));
    }

    /** With shift and over 64K, cap at 64. */
    @Test
    void extractCount_shift_over64() {
        assertEquals(64, BlobStacks.computeExtractCount(100_000L, true));
    }

    /** With shift and 64,500 mB, extract 64 (remainder ignored). */
    @Test
    void extractCount_shift_withRemainder() {
        assertEquals(64, BlobStacks.computeExtractCount(64_500L, true));
    }

    /** With shift and 1500 mB, extract 1 (only 1 whole blob). */
    @Test
    void extractCount_shift_oneAndHalf() {
        assertEquals(1, BlobStacks.computeExtractCount(1500L, true));
    }

    /** Zero volume extracts nothing regardless of shift. */
    @Test
    void extractCount_zero() {
        assertEquals(0, BlobStacks.computeExtractCount(0L, true));
    }
}
