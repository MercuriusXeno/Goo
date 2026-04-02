package com.mercuriusxeno.goo.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BlobStacks pure utility: constants, volume math, and output rule.
 * These tests do not require Minecraft class init - they only test pure logic.
 */
class BlobStacksTest {

    // -- Constants --

    /** One blob is 1000 mB. */
    @Test
    void mbPerBlobIsOneThousand() {
        assertEquals(1000L, BlobStacks.MB_PER_BLOB);
    }

    /** Maximum blob stack volume is 64,000 mB. */
    @Test
    void maxBlobStackVolumeIs64K() {
        assertEquals(64_000L, BlobStacks.MAX_BLOB_STACK_VOLUME);
    }

    // -- wholeBlobs --

    /** Zero volume yields zero blobs. */
    @Test
    void wholeBlobs_zero() {
        assertEquals(0L, BlobStacks.wholeBlobs(0L));
    }

    /** Sub-blob volume yields zero blobs. */
    @Test
    void wholeBlobs_subBlob() {
        assertEquals(0L, BlobStacks.wholeBlobs(999L));
    }

    /** Exactly 1000 mB yields 1 blob. */
    @Test
    void wholeBlobs_exactlyOne() {
        assertEquals(1L, BlobStacks.wholeBlobs(1000L));
    }

    /** 64,000 mB yields 64 blobs. */
    @Test
    void wholeBlobs_fullStack() {
        assertEquals(64L, BlobStacks.wholeBlobs(64_000L));
    }

    /** 64,001 mB still yields 64 whole blobs. */
    @Test
    void wholeBlobs_overStack() {
        assertEquals(64L, BlobStacks.wholeBlobs(64_001L));
    }

    // -- remainder --

    /** Zero volume has zero remainder. */
    @Test
    void remainder_zero() {
        assertEquals(0L, BlobStacks.remainder(0L));
    }

    /** 1 mB remainder. */
    @Test
    void remainder_one() {
        assertEquals(1L, BlobStacks.remainder(1L));
    }

    /** 999 mB remainder. */
    @Test
    void remainder_subBlob() {
        assertEquals(999L, BlobStacks.remainder(999L));
    }

    /** Clean multiple has zero remainder. */
    @Test
    void remainder_cleanMultiple() {
        assertEquals(0L, BlobStacks.remainder(5000L));
    }

    /** 64,001 has 1 mB remainder. */
    @Test
    void remainder_overStack() {
        assertEquals(1L, BlobStacks.remainder(64_001L));
    }

    // -- isCleanBlobStack --

    /** Zero is not a clean blob stack. */
    @Test
    void isCleanBlobStack_zero() {
        assertFalse(BlobStacks.isCleanBlobStack(0L));
    }

    /** Sub-blob is not clean. */
    @Test
    void isCleanBlobStack_subBlob() {
        assertFalse(BlobStacks.isCleanBlobStack(999L));
    }

    /** Exactly 1000 mB is clean. */
    @Test
    void isCleanBlobStack_exactlyOne() {
        assertTrue(BlobStacks.isCleanBlobStack(1000L));
    }

    /** 64,000 mB is clean (max stack). */
    @Test
    void isCleanBlobStack_maxStack() {
        assertTrue(BlobStacks.isCleanBlobStack(64_000L));
    }

    /** 64,001 mB is not clean (exceeds max stack). */
    @Test
    void isCleanBlobStack_overMax() {
        assertFalse(BlobStacks.isCleanBlobStack(64_001L));
    }

    /** 65,000 mB is not clean (clean multiple but > 64K). */
    @Test
    void isCleanBlobStack_cleanButOverMax() {
        assertFalse(BlobStacks.isCleanBlobStack(65_000L));
    }

    /** 1500 mB is not clean (not evenly divisible). */
    @Test
    void isCleanBlobStack_notDivisible() {
        assertFalse(BlobStacks.isCleanBlobStack(1500L));
    }

    /** Negative is not clean. */
    @Test
    void isCleanBlobStack_negative() {
        assertFalse(BlobStacks.isCleanBlobStack(-1000L));
    }

    // -- computeExtractCount --

    /** Zero volume extracts nothing. */
    @Test
    void computeExtractCount_zeroVolume() {
        assertEquals(0, BlobStacks.computeExtractCount(0L, false));
        assertEquals(0, BlobStacks.computeExtractCount(0L, true));
    }

    /** Sub-blob volume extracts nothing. */
    @Test
    void computeExtractCount_subBlob() {
        assertEquals(0, BlobStacks.computeExtractCount(999L, false));
        assertEquals(0, BlobStacks.computeExtractCount(999L, true));
    }

    /** Exactly 1 blob: no-shift extracts 1, shift extracts 1. */
    @Test
    void computeExtractCount_oneBlob() {
        assertEquals(1, BlobStacks.computeExtractCount(1000L, false));
        assertEquals(1, BlobStacks.computeExtractCount(1000L, true));
    }

    /** Multiple blobs: no-shift extracts 1, shift extracts all up to 64. */
    @Test
    void computeExtractCount_multipleBlobs() {
        assertEquals(1, BlobStacks.computeExtractCount(10_000L, false));
        assertEquals(10, BlobStacks.computeExtractCount(10_000L, true));
    }

    /** Shift caps at 64 even with more available. */
    @Test
    void computeExtractCount_shiftCapsAt64() {
        assertEquals(64, BlobStacks.computeExtractCount(100_000L, true));
    }

    /** Volume with remainder: only whole blobs count. */
    @Test
    void computeExtractCount_volumeWithRemainder() {
        assertEquals(1, BlobStacks.computeExtractCount(1500L, false));
        assertEquals(1, BlobStacks.computeExtractCount(1500L, true));
    }
}
