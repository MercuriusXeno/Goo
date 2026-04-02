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
        assertEquals("Microblob", BlobTiers.computeTierName(0L));
    }

    /** Volume just below 1000 is still a Microblob. */
    @Test
    void justBelowThousandIsMicroblob() {
        assertEquals("Microblob", BlobTiers.computeTierName(999L));
    }

    /** Volume at exactly 1000 mB is a Blob. */
    @Test
    void exactlyThousandIsBlob() {
        assertEquals("Blob", BlobTiers.computeTierName(1_000L));
    }

    /** Volume just below 10M is still a Blob. */
    @Test
    void justBelowTenMillionIsBlob() {
        assertEquals("Blob", BlobTiers.computeTierName(9_999_999L));
    }

    /** Volume at exactly 10M is a Kiloblob. */
    @Test
    void exactlyTenMillionIsKiloblob() {
        assertEquals("Kiloblob", BlobTiers.computeTierName(10_000_000L));
    }

    /** Volume just below 1B is still a Kiloblob. */
    @Test
    void justBelowBillionIsKiloblob() {
        assertEquals("Kiloblob", BlobTiers.computeTierName(999_999_999L));
    }

    /** Volume at exactly 1B is a Megablob. */
    @Test
    void exactlyBillionIsMegablob() {
        assertEquals("Megablob", BlobTiers.computeTierName(1_000_000_000L));
    }

    /** Volume just below 1T is still a Megablob. */
    @Test
    void justBelowTrillionIsMegablob() {
        assertEquals("Megablob", BlobTiers.computeTierName(999_999_999_999L));
    }

    /** Volume at exactly 1T is a Gigablob. */
    @Test
    void exactlyTrillionIsGigablob() {
        assertEquals("Gigablob", BlobTiers.computeTierName(1_000_000_000_000L));
    }

    /** Volume just below 1Q is still a Gigablob. */
    @Test
    void justBelowQuadrillionIsGigablob() {
        assertEquals("Gigablob", BlobTiers.computeTierName(999_999_999_999_999L));
    }

    /** Volume at exactly 1Q is a Terrablob. */
    @Test
    void exactlyQuadrillionIsTerrablob() {
        assertEquals("Terrablob", BlobTiers.computeTierName(1_000_000_000_000_000L));
    }

    /** Long.MAX_VALUE is a Terrablob. */
    @Test
    void maxLongIsTerrablob() {
        assertEquals("Terrablob", BlobTiers.computeTierName(Long.MAX_VALUE));
    }

    // ── THROW_COST ───────────────────────────────────────────────────────

    /** Throw cost is exactly 1000 mB (one blob). */
    @Test
    void throwCostIsOneBlob() {
        assertEquals(1000L, BlobTiers.THROW_COST);
    }
}
