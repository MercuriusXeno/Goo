package com.mercuriusxeno.goo.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GooBlobItem pure logic: tier naming and volume thresholds.
 * These methods are package-private or static, testable without Minecraft.
 */
class GooBlobItemTest {

    // ── computeTierName ──────────────────────────────────────────────────

    /** Zero volume is a Microblob. */
    @Test
    void zeroVolumeIsMicroblob() {
        assertEquals("Microblob", BlobTiers.computeTierName(0));
    }

    /** Volume just below 1000 is still a Microblob. */
    @Test
    void justBelowThousandIsMicroblob() {
        assertEquals("Microblob", BlobTiers.computeTierName(999));
    }

    /** Volume at exactly 1000 mB is a Blob. */
    @Test
    void exactlyThousandIsBlob() {
        assertEquals("Blob", BlobTiers.computeTierName(1_000));
    }

    /** Volume just below 10M is still a Blob. */
    @Test
    void justBelowTenMillionIsBlob() {
        assertEquals("Blob", BlobTiers.computeTierName(9_999_999));
    }

    /** Volume at exactly 10M is a Kiloblob. */
    @Test
    void exactlyTenMillionIsKiloblob() {
        assertEquals("Kiloblob", BlobTiers.computeTierName(10_000_000));
    }

    /** Volume just below 1B is still a Kiloblob. */
    @Test
    void justBelowBillionIsKiloblob() {
        assertEquals("Kiloblob", BlobTiers.computeTierName(999_999_999));
    }

    /** Volume at exactly 1B is a Megablob (max practical tier). */
    @Test
    void exactlyBillionIsMegablob() {
        assertEquals("Megablob", BlobTiers.computeTierName(1_000_000_000));
    }

    /** Integer.MAX_VALUE is a Megablob. */
    @Test
    void maxIntIsMegablob() {
        assertEquals("Megablob", BlobTiers.computeTierName(Integer.MAX_VALUE));
    }

    // ── THROW_COST ───────────────────────────────────────────────────────

    /** Throw cost is exactly 1000 mB (one blob). */
    @Test
    void throwCostIsOneBlob() {
        assertEquals(1000, BlobTiers.THROW_COST);
    }
}
