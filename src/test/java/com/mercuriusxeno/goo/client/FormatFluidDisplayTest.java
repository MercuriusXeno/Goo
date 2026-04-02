package com.mercuriusxeno.goo.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GooTooltipHandler.formatFluidDisplay(): tiered unit formatting
 * for microblob volumes. Pure static method, no Minecraft dependency.
 */
class FormatFluidDisplayTest {

    // ── mB tier (< 1000) ────────────────────────────────────────────────

    /** Zero displays as .000 (sub-blob fraction). */
    @Test
    void zeroDisplaysAsFraction() {
        assertEquals(".000", GooTooltipHandler.formatFluidDisplay(0L));
    }

    /** Small value displays as sub-blob fraction. */
    @Test
    void smallValueDisplaysAsFraction() {
        assertEquals(".500", GooTooltipHandler.formatFluidDisplay(500L));
    }

    /** 999 is still sub-blob tier. */
    @Test
    void justBelowThousandIsFraction() {
        assertEquals(".999", GooTooltipHandler.formatFluidDisplay(999L));
    }

    /** Single microblob displays with leading zeros. */
    @Test
    void singleMicroblobDisplaysWithLeadingZeros() {
        assertEquals(".001", GooTooltipHandler.formatFluidDisplay(1L));
    }

    /** 100 mB displays as .100. */
    @Test
    void hundredMicroblobsDisplay() {
        assertEquals(".100", GooTooltipHandler.formatFluidDisplay(100L));
    }

    // ── Blob tier (1000 .. 999,999) ──────────────────────────────────────

    /** Exactly 1000 mB displays as 1. */
    @Test
    void exactlyOneBlob() {
        assertEquals("1", GooTooltipHandler.formatFluidDisplay(1_000L));
    }

    /** 1500 mB displays as 1.5. */
    @Test
    void onePointFiveBlobs() {
        assertEquals("1.5", GooTooltipHandler.formatFluidDisplay(1_500L));
    }

    /** 999,999 mB is the top of blob tier. */
    @Test
    void topOfBlobTier() {
        assertEquals("999.9", GooTooltipHandler.formatFluidDisplay(999_999L));
    }

    /** Even blob count omits decimal. */
    @Test
    void evenBlobCountNoDecimal() {
        assertEquals("5", GooTooltipHandler.formatFluidDisplay(5_000L));
    }

    // ── K tier (1e6 .. 999,999,999) ───────────────────────────────────────

    /** Exactly 1M mB displays as 1 K. */
    @Test
    void exactlyOneKiloblob() {
        assertEquals("1 K", GooTooltipHandler.formatFluidDisplay(1_000_000L));
    }

    /** 2.5 K. */
    @Test
    void twoPointFiveKiloblobs() {
        assertEquals("2.5 K", GooTooltipHandler.formatFluidDisplay(2_500_000L));
    }

    // ── M tier (1e9 .. 999,999,999,999) ───────────────────────────────────

    /** Exactly 1B mB displays as 1 M. */
    @Test
    void exactlyOneMegablob() {
        assertEquals("1 M", GooTooltipHandler.formatFluidDisplay(1_000_000_000L));
    }

    // ── G tier (1e12 .. 999,999,999,999,999) ──────────────────────────────

    /** Exactly 1T mB displays as 1 G. */
    @Test
    void exactlyOneGigablob() {
        assertEquals("1 G", GooTooltipHandler.formatFluidDisplay(1_000_000_000_000L));
    }

    // ── T tier (>= 1e15) ──────────────────────────────────────────────────

    /** Exactly 1Q mB displays as 1 T. */
    @Test
    void exactlyOneTerrablob() {
        assertEquals("1 T", GooTooltipHandler.formatFluidDisplay(1_000_000_000_000_000L));
    }

    /** Large T value formats correctly. */
    @Test
    void largeTerrablobValue() {
        assertEquals("5.5 T", GooTooltipHandler.formatFluidDisplay(5_500_000_000_000_000L));
    }

    // ── Decimal precision ─────────────────────────────────────────────────

    /** 1050 mB is 1.050 blobs: trailing zero trimmed to "1.05". */
    @Test
    void smallRemainderShowsSignificantDigits() {
        assertEquals("1.05", GooTooltipHandler.formatFluidDisplay(1_050L));
    }

    /** Remainder exactly at a tenth shows one decimal. */
    @Test
    void exactTenthShowsDecimal() {
        assertEquals("1.3", GooTooltipHandler.formatFluidDisplay(1_300L));
    }
}
