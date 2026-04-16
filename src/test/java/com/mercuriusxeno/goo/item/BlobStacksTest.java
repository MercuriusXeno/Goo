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
        assertEquals(1000, BlobStacks.MB_PER_BLOB);
    }

    /** Maximum blob stack volume is 64,000 mB. */
    @Test
    void maxBlobStackVolumeIs64K() {
        assertEquals(64_000, BlobStacks.MAX_BLOB_STACK_VOLUME);
    }

    // -- wholeBlobs --

    /** Zero volume yields zero blobs. */
    @Test
    void wholeBlobs_zero() {
        assertEquals(0, BlobStacks.wholeBlobs(0));
    }

    /** Sub-blob volume yields zero blobs. */
    @Test
    void wholeBlobs_subBlob() {
        assertEquals(0, BlobStacks.wholeBlobs(999));
    }

    /** Exactly 1000 mB yields 1 blob. */
    @Test
    void wholeBlobs_exactlyOne() {
        assertEquals(1, BlobStacks.wholeBlobs(1000));
    }

    /** 64,000 mB yields 64 blobs. */
    @Test
    void wholeBlobs_fullStack() {
        assertEquals(64, BlobStacks.wholeBlobs(64_000));
    }

    /** 64,001 mB still yields 64 whole blobs. */
    @Test
    void wholeBlobs_overStack() {
        assertEquals(64, BlobStacks.wholeBlobs(64_001));
    }

    // -- remainder --

    /** Zero volume has zero remainder. */
    @Test
    void remainder_zero() {
        assertEquals(0, BlobStacks.remainder(0));
    }

    /** 1 mB remainder. */
    @Test
    void remainder_one() {
        assertEquals(1, BlobStacks.remainder(1));
    }

    /** 999 mB remainder. */
    @Test
    void remainder_subBlob() {
        assertEquals(999, BlobStacks.remainder(999));
    }

    /** Clean multiple has zero remainder. */
    @Test
    void remainder_cleanMultiple() {
        assertEquals(0, BlobStacks.remainder(5000));
    }

    /** 64,001 has 1 mB remainder. */
    @Test
    void remainder_overStack() {
        assertEquals(1, BlobStacks.remainder(64_001));
    }

    // -- isCleanBlobStack --

    /** Zero is not a clean blob stack. */
    @Test
    void isCleanBlobStack_zero() {
        assertFalse(BlobStacks.isCleanBlobStack(0));
    }

    /** Sub-blob is not clean. */
    @Test
    void isCleanBlobStack_subBlob() {
        assertFalse(BlobStacks.isCleanBlobStack(999));
    }

    /** Exactly 1000 mB is clean. */
    @Test
    void isCleanBlobStack_exactlyOne() {
        assertTrue(BlobStacks.isCleanBlobStack(1000));
    }

    /** 64,000 mB is clean (max stack). */
    @Test
    void isCleanBlobStack_maxStack() {
        assertTrue(BlobStacks.isCleanBlobStack(64_000));
    }

    /** 64,001 mB is not clean (exceeds max stack). */
    @Test
    void isCleanBlobStack_overMax() {
        assertFalse(BlobStacks.isCleanBlobStack(64_001));
    }

    /** 65,000 mB is not clean (clean multiple but > 64K). */
    @Test
    void isCleanBlobStack_cleanButOverMax() {
        assertFalse(BlobStacks.isCleanBlobStack(65_000));
    }

    /** 1500 mB is not clean (not evenly divisible). */
    @Test
    void isCleanBlobStack_notDivisible() {
        assertFalse(BlobStacks.isCleanBlobStack(1500));
    }

    /** Negative is not clean. */
    @Test
    void isCleanBlobStack_negative() {
        assertFalse(BlobStacks.isCleanBlobStack(-1000));
    }

    // -- computeExtractCount --

    /** Zero volume extracts nothing. */
    @Test
    void computeExtractCount_zeroVolume() {
        assertEquals(0, BlobStacks.computeExtractCount(0, false));
        assertEquals(0, BlobStacks.computeExtractCount(0, true));
    }

    /** Sub-blob volume extracts nothing. */
    @Test
    void computeExtractCount_subBlob() {
        assertEquals(0, BlobStacks.computeExtractCount(999, false));
        assertEquals(0, BlobStacks.computeExtractCount(999, true));
    }

    /** Exactly 1 blob: no-shift extracts 1, shift extracts 1. */
    @Test
    void computeExtractCount_oneBlob() {
        assertEquals(1, BlobStacks.computeExtractCount(1000, false));
        assertEquals(1, BlobStacks.computeExtractCount(1000, true));
    }

    /** Multiple blobs: no-shift extracts 1, shift extracts all up to 64. */
    @Test
    void computeExtractCount_multipleBlobs() {
        assertEquals(1, BlobStacks.computeExtractCount(10_000, false));
        assertEquals(10, BlobStacks.computeExtractCount(10_000, true));
    }

    /** Shift caps at 64 even with more available. */
    @Test
    void computeExtractCount_shiftCapsAt64() {
        assertEquals(64, BlobStacks.computeExtractCount(100_000, true));
    }

    /** Volume with remainder: only whole blobs count. */
    @Test
    void computeExtractCount_volumeWithRemainder() {
        assertEquals(1, BlobStacks.computeExtractCount(1500, false));
        assertEquals(1, BlobStacks.computeExtractCount(1500, true));
    }

    // -- absorbedVolume (sink-into-omniblob math) --

    /** Absorbing into an empty omniblob yields the source volume. */
    @Test
    void absorbedVolume_emptyOmniblob() {
        assertEquals(1000, BlobStacks.absorbedVolume(1000, 0));
    }

    /** Absorbing into a non-empty omniblob sums the two volumes. */
    @Test
    void absorbedVolume_withExistingVolume() {
        assertEquals(5500, BlobStacks.absorbedVolume(500, 5000));
    }

    /** Absorbing a full blob stack worth (64,000 mB) into a partial omniblob. */
    @Test
    void absorbedVolume_maxBlobStackIntoPartial() {
        assertEquals(64_500, BlobStacks.absorbedVolume(64_000, 500));
    }

    /** Zero source leaves the omniblob unchanged. */
    @Test
    void absorbedVolume_zeroSource() {
        assertEquals(5000, BlobStacks.absorbedVolume(0, 5000));
    }
}
